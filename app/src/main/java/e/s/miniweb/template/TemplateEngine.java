package e.s.miniweb.template;

import android.content.res.AssetManager;
import android.webkit.WebResourceRequest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TemplateEngine {
    // composite name => call-back; Composite is controller | method
    private static final Map<String, WebMethod> responders = new HashMap<>();
    private static final Set<Object> controllers = new HashSet<>();
    private final AssetManager assets;

    public TemplateEngine(AssetManager assets) {
        // Note: Is Java's reflection is so crap we can't pre-load class info in
        // a meaningful way? So we wait until we get a call, and cache from there?
        this.assets = assets;
    }

    public static void BindMethod(String controllerName, String methodName, WebMethod methodFunc) {
        String composite = controllerName+"|="+methodName;
        if (responders.containsKey(composite)){
            System.out.println("Reused method. Ignored.");
            return;
        }

        responders.put(composite, methodFunc);
        System.out.println("Added controller. Now have "+controllers.size());
    }

    public static void Use(Object o) {
        // just keep a reference for the gc
        controllers.add(o);
    }

    // Call out to the controller, get a template and model object.
    // Fill out template, then return the resulting document as a string.
    public String Run(String controller, String method, String params, WebResourceRequest request) {
        String composite = controller+"|="+method;

        if ( ! responders.containsKey(composite)){
            System.out.println("Unknown web method!");
            return null;
        }

        WebMethod wm = responders.get(composite);
        if (wm == null){
            System.out.println("Invalid web method!");
            return null;
        }

        TemplateResponse tmpl = getDocTemplate(request, params, wm);

        if (tmpl == null){
            System.out.println("Invalid controller response!");
            return null;
        }
        if (tmpl.RedirectUrl != null){
            return redirectPage(tmpl);
        }

        return transformTemplate(tmpl);
    }

    private String redirectPage(TemplateResponse tmpl) {
        return "<!doctype html>" +
                "<html lang=\"en-US\">" +
                "    <head>" +
                "        <meta charset=\"UTF-8\">" +
                "        <meta http-equiv=\"refresh\" content=\"0; url="+tmpl.RedirectUrl+"\">" +
                "        <script type=\"text/javascript\">" +
                "            manager.clearHistory();" +
                "            window.location.href = \""+ tmpl.RedirectUrl+"\";" +
                "        </script>\n" +
                "        <title>Page Redirection</title>\n" +
                "    </head>\n" +
                "    <body>\n" +
                "        If you are not redirected automatically, <a href=\\\"\"+tmpl.RedirectUrl+\"\\\">follow this link</a>.\n" +
                "    </body>\n" +
                "</html>";
    }

    // Replace {{name}} with tmpl.Model.name.toString()
    // For {{for:name}}...{{end:name}}
    //     if tmpl.Model.name is enumerable, repeat the contained section for each item. The template {{item:childField}} becomes available.
    //     if tmpl.Model.name is null, or false, or "", then don't show the section
    //     otherwise, show the section once.
    // Template holes `{{...}}` must not cross line in the doc -- both the open and close must be on the same line.
    // Any `{{for:...}}` or `{{end:...}}` must be on their own line with only whitespace around them.
    private String transformTemplate(TemplateResponse tmpl) {
        StringBuilder sb = new StringBuilder();
        //tmpl.TemplateLines.get(0);

        for (String line : tmpl.TemplateLines) {
            if (!line.contains("{{")) { // plain text line
                sb.append(line);
                continue;
            }

            // Any block control?
            if (line.contains("{{for:")||line.contains("{{end:")){
                // todo: block control (should basically recurse contents)
                continue;
            }

            // For each template hole, find a field on the object that matches. If not found, we replace with a `[name]` placeholder.
            // Null values are replaced with empty string. We never leave `{{..}}` in place, otherwise we'd have an endless loop.
            while (line.contains("{{")) {
                line = singleReplace(line, tmpl);
            }

            // everything should be done now.
            sb.append(line);
        }
        // todo: templates
        return sb.toString();
    }

    // replace the FIRST template hole we find.
    private String singleReplace(String line, TemplateResponse tmpl) {
        String left, right;

        // split "...before...{{name}}...after..."
        // into left="...before..."; right="name}}..after..."
        if (line.startsWith("{{")){
            left = "";
            right = line.substring(2);
        } else {
            String[] bits = line.split("[{]{2}", 2);
            if (bits.length < 1) return "";
            if (bits.length == 1) return bits[0];

            left = bits[0];
            right = bits[1];
        }

        StringBuilder sb = new StringBuilder();
        sb.append(left);

        int closeIndex = right.indexOf("}}");
        if (closeIndex < 1) { // hole was "{{}}", which is invalid
            sb.append(right);
            return sb.toString();
        }

        String name = right.substring(0,closeIndex);
        String rest = right.substring(closeIndex+2);

        // test:
        sb.append(findField(tmpl.Model, name));
        sb.append(rest);

        return sb.toString();
    }

    private String findField(Object model, String name) {
        try {
            Field field = model.getClass().getField(name);

            Object value = field.get(model);
            if (value == null) return "";
            return value.toString();
        } catch (NoSuchFieldException | IllegalAccessException ignore) {
            return "["+name+"]";
        }
    }

    private TemplateResponse getDocTemplate(WebResourceRequest request, String params, WebMethod wm) {
        try {
            TemplateResponse resp = wm.generate(mapParams(params), request);
            if (resp.RedirectUrl != null) return resp;

            resp.TemplateLines = new ArrayList<>();

            InputStream is = assets.open("views/" + resp.TemplatePath + ".html");
            BufferedReader br = new BufferedReader(new InputStreamReader(is));

            String readLine;

            try {
                // While the BufferedReader readLine is not null
                while ((readLine = br.readLine()) != null) {
                    resp.TemplateLines.add(readLine); // add it to the template
                }

                // Close the InputStream and BufferedReader
                is.close();
                br.close();

            } catch (Exception e) {
                e.printStackTrace();
            }

            return resp;
        } catch (Exception ex) {
            // todo: write a warning page, giving some details and a 'back'|'home' button
            System.out.println(ex.getLocalizedMessage());
            return null;
        }
    }

    private Map<String, String> mapParams(String params) {
        Map<String, String> result = new HashMap<>();
        if (params == null) return result;
        String[] parts = params.split("([&]|[?])");
        for (String part : parts) {
            if (part == null || part.equals("")) continue;
            String[] sides = part.split("=",2);

            if (sides.length == 1) result.put(sides[0],sides[0]);
            else                   result.put(sides[0],sides[1]);
        }
        return result;
    }
}