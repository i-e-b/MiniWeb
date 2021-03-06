package e.s.miniweb.core.template;

import android.content.res.AssetManager;
import android.webkit.WebResourceRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
    public String Run(String controller, String method, String params, WebResourceRequest request) throws Exception {
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

        if (tmpl.RedirectUrl != null){
            return redirectPage(tmpl);
        }

        return transformTemplate(tmpl, null);
    }

    /**
     * Render one of the internal templates -- for errors, redirects, etc.
     * You should provide the full file name (like "redirect.html")
     */
    public String internalTemplate(String name, Object model){
        try {
            TemplateResponse tmpl = new TemplateResponse();
            tmpl.TemplateLines = new ArrayList<>();
            tmpl.Model = model;

            readAsset("internal/"+name, tmpl);
            return transformTemplate(tmpl, null);
        } catch (Exception ex){
            // don't call the error template. Something might be broken with the apk!
            StringBuilder sb = new StringBuilder();
            sb.append("<h1>Internal error</h1><pre>\r\n");
            StackTraceElement[] stack = ex.getStackTrace();
            for (StackTraceElement element : stack) {
                sb.append(element.toString());
            }
            sb.append("</pre>");
            return sb.toString();
        }
    }

    /**
     * Render a page that causes a redirect
     */
    private String redirectPage(TemplateResponse tmpl) {
        return internalTemplate("redirect.html", new RedirectModel(tmpl));
    }

    private static class RedirectModel {
        public final String RedirectUrl;
        public final boolean ShouldClearHistory;

        public RedirectModel(TemplateResponse tmpl) {
            RedirectUrl = tmpl.RedirectUrl;
            ShouldClearHistory = tmpl.ShouldClearHistory;
        }
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

        List<String> templateLines = tmpl.TemplateLines;
        for (int lineIndex = 0, templateLinesSize = templateLines.size(); lineIndex < templateLinesSize; lineIndex++) {
            String line = templateLines.get(lineIndex);
            if (!line.contains("{{")) { // plain text line
                sb.append(line);
                sb.append("\r\n");
                continue;
            }

            // Any block control?
            if (line.contains("{{for:") || line.contains("{{end:")) {
                // block control: recurse contents as new sub-documents, then skip to end of block
                int size = recurseBlock(lineIndex, tmpl, cursorItem, sb);
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
            sb.append("\r\n");
        }

        return sb.toString();
    }

    private int recurseBlock(int startIndex, TemplateResponse tmpl, Object cursorItem, StringBuilder sb) {
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

        // handle `{{for:item:my_thing}}`
        Object target = subset.Model;
        if (fieldName.startsWith("item:")){
            target = cursorItem;
            fieldName = fieldName.substring(5);
        }

        // now we ignore the top and bottom lines, and run everything else through `` again
        // that might come back here if there are nested 'for' blocks
        Object items = findFieldObject(target, fieldName);
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
        if (closeIndex < 0){
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

            if (name.equals("")){ // special case: {{item:}} output the model as a string
                return model.toString();
            }

            Object value = searchForField(model, name);

            if (value == null) return "";
            return value.toString();
        } catch (Exception ex) {
            return "["+name+"]";
        }
    }

    /**
     * Hunts though object hierarchies for values.
     * The simplest and fastest path is to have a single field name.
     */
    private Object searchForField(Object model, String name) throws NoSuchFieldException, IllegalAccessException {
        if (!name.contains(".")) { // simple case
            return model.getClass().getField(name).get(model);
        }

        Object src = model;
        String[] pathBits = name.split("[.]");

        // walk down the chain
        for (String bit : pathBits) {
            if (src == null) return null;

            if (looksLikeInt(bit)) { // index into iterable, or fail
                int idx = Integer.parseInt(bit);
                src = getIterableIndexed(src, idx);
            } else if (src instanceof Map){
                src = ((Map<?, ?>) src).get(bit);
            } else { // get field by name, or fail
                src = src.getClass().getField(bit).get(src);
            }
        }
        return src;
    }

    @SuppressWarnings("rawtypes")
    private Object getIterableIndexed(Object src, int idx) {
        // coding in Java feels more old fashioned than C.
        try {
            if (src == null || idx < 0) return null;
            Iterable items = (Iterable) src;
            Iterator iterator = items.iterator();

            Object current = iterator.next();
            for (int counter = 0; counter < idx; counter++)
                current = iterator.next();

            return current;
        } catch (Exception ex){
            return null;
        }
    }

    private boolean looksLikeInt(String str){
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private Object findFieldObject(Object model, String name) {
        try {
            if (model == null) return null;
            if (name == null) return null;

            if (name.equals("")) return model; // special case {{for:}} -> use the model directly

            return searchForField(model, name);
        } catch (NoSuchFieldException | IllegalAccessException ignore) {
            return null;
        }
    }

    private TemplateResponse getDocTemplate(WebResourceRequest request, String params, WebMethod wm) throws Exception {
        TemplateResponse resp = wm.generate(mapParams(params), request);
        if (resp == null) throw new Exception("Method gave a bad result");
        if (resp.RedirectUrl != null) return resp;

        resp.TemplateLines = new ArrayList<>();
        readAsset("views/" + resp.TemplatePath + ".html", resp);

        return resp;
    }

    private void readAsset(String fileName, TemplateResponse resp) throws IOException {
        InputStream is = assets.open(fileName);
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