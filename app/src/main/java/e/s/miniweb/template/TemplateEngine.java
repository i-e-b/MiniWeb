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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

        return transformTemplate(tmpl, null);
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
    private String transformTemplate(TemplateResponse tmpl, Object cursorItem) {
        StringBuilder sb = new StringBuilder();
        //tmpl.TemplateLines.get(0);

        List<String> templateLines = tmpl.TemplateLines;
        for (int lineIndex = 0, templateLinesSize = templateLines.size(); lineIndex < templateLinesSize; lineIndex++) {
            String line = templateLines.get(lineIndex);
            if (!line.contains("{{")) { // plain text line
                sb.append(line);
                continue;
            }

            // Any block control?
            if (line.contains("{{for:") || line.contains("{{end:")) {
                // block control: recurse contents as new sub-documents, then skip to end of block
                int size = recurseBlock(lineIndex, tmpl, sb);
                if (size > 0){
                    lineIndex += size;
                } else{
                    sb.append(line); // write it out -- both so we can see errors, and so comments don't get broken.
                }
                continue;
            }

            // For each template hole, find a field on the object that matches. If not found, we replace with a `[name]` placeholder.
            // Null values are replaced with empty string. We never leave `{{..}}` in place, otherwise we'd have an endless loop.
            while (line.contains("{{")) {
                line = singleReplace(line, tmpl, cursorItem);
            }

            // everything should be done now.
            sb.append(line);
        }
        // todo: templates
        return sb.toString();
    }

    private int recurseBlock(int startIndex, TemplateResponse tmpl, StringBuilder sb) {
        // get the target
        String startLine = tmpl.TemplateLines.get(startIndex).trim();
        if (! startLine.startsWith("{{") || ! startLine.endsWith("}}")) { // invalid repeat line
            return 0;
        }

        String[] bits = readDirective(startLine);
        if (bits == null) return 0; // bad '{{...:...}}' line
        if (!Objects.equals(bits[0], "for")) return 0; // not the start of a block

        String fieldName = bits[1];

        // scan until we find the next matching '{{end:...}}'
        boolean found = false;
        int endIndex;
        List<String> templateLines = tmpl.TemplateLines;
        int limit = templateLines.size();
        for (endIndex = startIndex + 1; endIndex < limit; endIndex++) {
            String templateLine = templateLines.get(endIndex);

            String[] endBits = readDirective(templateLine);
            if (endBits == null) continue; // bad '{{...:...}}' line
            if (!Objects.equals(endBits[0], "end")) continue; // not the end of a block
            if (!Objects.equals(endBits[1], fieldName)) continue; // not the end the field we started (probably nested)

            found = true;
            break;
        }
        if (!found) return 0; // could not find an end point
        int lineCount = endIndex - startIndex;

        TemplateResponse subset = tmpl.cloneRange(startIndex+1, endIndex - 1);

        // todo: handle `{{for:item:my_thing}}`
        // now we ignore the top and bottom lines, and run everything else through `` again
        // that might come back here if there are nested 'for' blocks
        Object items = findFieldObject(subset.Model, fieldName);
        if (items instanceof Iterable) {
            @SuppressWarnings("rawtypes") Iterable listItems = (Iterable) items;
            for (Object item : listItems) {
                sb.append(transformTemplate(subset, item));
            }
        } else if (items instanceof Boolean) {
            boolean b = (boolean) items;
            if (b) {
                sb.append(transformTemplate(subset, null));
            }
        } else if (items != null) {
            sb.append(transformTemplate(subset, items));
        }

        return lineCount;
    }

    private String[] readDirective(String line) {
        try {
            line = line.trim();
            if (line.length() < 4) return null;
            line = line.substring(2, line.length() - 2);
            String[] bits = line.split(":", 2);
            if (bits.length != 2) { // bad format
                return null;
            }
            return bits;
        } catch (Exception ex) {
            return null;
        }
    }

    // replace the FIRST template hole we find.
    private String singleReplace(String line, TemplateResponse tmpl, Object cursorItem) {
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

        if (name.startsWith("item:")){
            sb.append(findField(cursorItem, name.substring(5)));
        } else {
            // get directly from model
            sb.append(findField(tmpl.Model, name));
        }
        sb.append(rest);

        return sb.toString();
    }

    private String findField(Object model, String name) {
        try {
            if (model == null) return "";
            if (name == null) return "";
            Field field = model.getClass().getField(name);

            Object value = field.get(model);
            if (value == null) return "";
            return value.toString();
        } catch (NoSuchFieldException | IllegalAccessException ignore) {
            return "["+name+"]";
        }
    }

    private Object findFieldObject(Object model, String name) {
        try {
            if (model == null) return null;
            if (name == null) return null;

            Field field = model.getClass().getField(name);
            return field.get(model);
        } catch (NoSuchFieldException | IllegalAccessException ignore) {
            return null;
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