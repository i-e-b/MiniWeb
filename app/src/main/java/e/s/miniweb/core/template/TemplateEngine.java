package e.s.miniweb.core.template;

import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceRequest;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import e.s.miniweb.core.AppWebRouter;
import e.s.miniweb.core.ControllerBinding;
import e.s.miniweb.core.hotReload.AssetLoader;
import e.s.miniweb.core.hotReload.HotReloadMonitor;

public class TemplateEngine {
    private static final String TAG = "TemplateEngine";
    private final AssetLoader assets;
    private final AppWebRouter router;

    public TemplateEngine(AssetLoader assets, AppWebRouter router) {
        // Note: Java's reflection is so crap we can't pre-load class info in
        // a meaningful way. So we wait until we get a call, and figure it out from there.
        this.assets = assets;
        this.router = router;
    }

    /**
     * Call out to the controller, get a template and model object.
     * Fill out template, then return the resulting document as a string.
     */
    public String Run(String controller, String method, String params, WebResourceRequest request, boolean isPartialView) throws Exception {
        String composite = controller + "|=" + method;

        if (!ControllerBinding.hasMethod(composite)) {
            System.out.println("Unknown web method!");
            return null;
        }

        WebMethod wm = ControllerBinding.getMethod(composite);
        if (wm == null) {
            System.out.println("Invalid web method!");
            return null;
        }

        // 'Do' the page action.
        // This results in a template and the data to go with it
        TemplateResponse tmpl = getDocTemplate(request, params, wm);

        if (tmpl.RedirectUrl != null) {
            return redirectPage(tmpl);
        }

        // ready to render the final page.
        // if we wanted to 'refresh', then `tmpl` is what we'd need

        HotReloadMonitor.AddHotReloadPage(tmpl);
        if (!isPartialView) {
            HotReloadMonitor.lastPageRendered = tmpl;
            HotReloadMonitor.lastPageRendered.Controller = controller;
            HotReloadMonitor.lastPageRendered.Method = method;
            HotReloadMonitor.lastPageRendered.Params = params;
        }

        // do the render!
        return transformTemplate(tmpl, null);
    }

    /**
     * Render one of the internal templates -- for errors, redirects, etc.
     * You should provide the full file name (like "redirect.html")
     */
    public String internalTemplate(String name, Object model) {
        try {
            TemplateResponse tmpl = new TemplateResponse();
            tmpl.TemplateLines = new ArrayList<>();
            tmpl.Model = model;

            copyLinesToTemplate("internal/" + name, tmpl);
            return transformTemplate(tmpl, null);
        } catch (Exception ex) {
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

    //region Render core

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

    /**
     * Replace {{name}} with tmpl.Model.name.toString()
     * For {{for:name}}...{{end:name}}
     * if tmpl.Model.name is enumerable, repeat the contained section for each item. The template {{item:childField}} becomes available.
     * if tmpl.Model.name is null, or false, or "", then don't show the section
     * otherwise, show the section once.
     * Template holes `{{...}}` must not cross line in the doc -- both the open and close must be on the same line.
     * Any `{{for:...}}` or `{{end:...}}` must be on their own line with only whitespace around them.
     */
    public String transformTemplate(TemplateResponse tmpl, Object cursorItem) {
        StringBuilder pageOut = new StringBuilder();

        List<String> templateLines = tmpl.TemplateLines;
        for (int templateLineIndex = 0, templateLinesSize = templateLines.size(); templateLineIndex < templateLinesSize; templateLineIndex++) {
            try {
                String templateLine = templateLines.get(templateLineIndex);
                if (!templateLine.contains("{{")) { // plain text line
                    pageOut.append(templateLine);
                    pageOut.append("\r\n");
                    continue;
                }

                // If the {{ or }} are quoted, ignore for special blocks (but not simple replacements)
                boolean isQuoted = templateLine.contains("'{{") || templateLine.contains("}}'");

                // It it part of a 'for' block?
                if (!isQuoted && templateLine.contains("{{for:")) {
                    // block control: recurse contents as new sub-documents, then skip to end of block
                    int templateLinesConsumed = recurseForBlock(templateLineIndex, tmpl, cursorItem, pageOut);
                    if (templateLinesConsumed > 0) {
                        templateLineIndex += templateLinesConsumed;
                    } else {
                        Log.w(TAG, "Could not interpret 'for' block. Check markup and data types. File=" + tmpl.TemplatePath + "; Line=" + templateLineIndex);
                        pageOut.append(templateLine); // write it out -- both so we can see errors, and so comments don't get broken.
                    }
                    continue;
                }

                // Is it a sub-view?
                if (!isQuoted && templateLine.contains("{{view:")) {
                    int templateLinesConsumed = injectViewBlock(templateLineIndex, tmpl, cursorItem, pageOut);
                    if (templateLinesConsumed >= 0) { // 'view' should only be 1 line
                        templateLineIndex += templateLinesConsumed;
                    } else {
                        Log.w(TAG, "Could not interpret 'view' block. Check markup and data types. File=" + tmpl.TemplatePath + "; Line=" + templateLineIndex);
                        pageOut.append(templateLine); // write it out -- both so we can see errors, and so comments don't get broken.
                    }
                    continue;
                }

                // Is it a permission block?
                if (!isQuoted && templateLine.contains("{{needs:")) {
                    pageOut.append("Hello. I see your permission request.");
                    continue;
                }

                // Trailing 'end'?
                if (!isQuoted && templateLine.contains("{{end:")) {
                    Log.w(TAG, "Unexpected 'end' tag. Either bad markup, or a block was not interpreted correctly. File=" + tmpl.TemplatePath + "; Line=" + templateLineIndex);
                    continue;
                }

                // For each template hole, find a field on the object that matches. If not found, we replace with a `[name]` placeholder.
                // Null values are replaced with empty string. We never leave `{{..}}` in place, otherwise we'd have an endless loop.
                while (templateLine.contains("{{")) {
                    templateLine = singleReplace(templateLine, tmpl, cursorItem);
                }

                // everything should be done now.
                pageOut.append(templateLine);
                pageOut.append("\r\n");
            } catch (Exception ex){
                Log.e(TAG, "Unexpected error in template transformer", ex);

                pageOut.append("\r\n[ERROR: ").append(ex.getMessage()).append("]\r\n");
            }
        }

        return pageOut.toString();
    }

    /** Handle sub-view blocks by calling back out through the template system and injecting results into string builder.
     * Returns number of extra lines consumed from the input template
     * @param startIndex offset into template lines where we should start
     * @param cursorItem 'for' loop item, if any. Otherwise null
     * @param sb output string builder
     * @param tmpl template being read
     * */
    private int injectViewBlock(int startIndex, TemplateResponse tmpl, Object cursorItem, StringBuilder sb) {
        String startLine = tmpl.TemplateLines.get(startIndex).trim();
        if (!startLine.startsWith("{{") || !startLine.endsWith("}}")) { // invalid repeat line
            return -1;
        }

        String[] bits = readDirective(startLine);
        if (bits == null) return -1; // bad '{{...:...}}' line
        if (!Objects.equals(bits[0], "view")) return -1; // not the start of a 'view' block
        Map<String,String> params = readParams(bits[1]);

        if (params.containsKey("url")) { // should do a GET to this URL
            try {
                Uri target = Uri.parse(params.get("url"));
                if (params.containsKey("model")){ // inject model into params?
                    // TODO: actually implement
                    target = target.buildUpon().appendQueryParameter("text","NOT YET IMPLEMENTED").build();
                }
                WebResourceRequest request = new InternalRequest(target);
                String response = router.getControllerResponse(request, true);
                sb.append(response);
                return 0;
            } catch (Exception ex) {
                Log.w(TAG, "URL view block failed: File=" + tmpl.TemplatePath + "; Line=" + startIndex + "; Error=" + ex);
                sb.append("[ERROR]");
                return -1;
            }
        }

        // exit early if there is no path
        if (!params.containsKey("path")){
            Log.w(TAG, "Invalid view block: no url or path. Check the mark-up, and ensure ',' separators. File=" + tmpl.TemplatePath + "; Line=" + startIndex);
            return -1;
        }

        // Try to find a model item. We will assume page's model if no prefix is given.
        // If we can't find anything, we will pass in `null`
        Object viewModel = getViewModelObjectByPath(startIndex, tmpl, cursorItem, params);

        // Try to load the view into a new template
        String viewPath = params.get("path");
        try {
            TemplateResponse viewTmpl = new TemplateResponse();
            viewTmpl.TemplateLines = new ArrayList<>();
            viewTmpl.Model = viewModel;

            copyLinesToTemplate(viewPath, viewTmpl);
            sb.append(transformTemplate(viewTmpl, null));
            return 0;
        } catch (FileNotFoundException fex){
            Log.w(TAG, "Failed to load view! TargetFile="+viewPath+"; ParentFile=" + tmpl.TemplatePath + "; Line=" + startIndex);
            return -1;
        } catch (Exception ex){
            Log.w(TAG, "Failed to load view block: File=" + tmpl.TemplatePath + "; Line=" + startIndex + "; Error=" + ex);
            return -1;
        }
    }

    /** try to find an object given a `model.path.to.thing` or `item.path.to.thing` */
    private Object getViewModelObjectByPath(int lineIdx, TemplateResponse tmpl, Object cursorItem, Map<String, String> params) {
        if (params.containsKey("model")){
            String modelPath = params.get("model");
            if (modelPath == null || modelPath.equals("")) {
                Log.w(TAG, "invalid view block: empty model. File=" + tmpl.TemplatePath + "; Line=" + lineIdx);
                return null;
            } else if (modelPath.startsWith("model")) { // looks like a page model reference
                if (modelPath.equals("model")){ // view should get the whole model
                    return tmpl.Model;
                } else { // need to find by path
                    return findFieldObject(tmpl.Model, stripFirstDotItem(modelPath));
                }
            } else if (modelPath.startsWith("item")){ // looks like a 'for' block item reference
                if (modelPath.equals("item")){ // view should get the whole iterated item
                    return cursorItem;
                } else { // need to find by path
                    return findFieldObject(cursorItem, stripFirstDotItem(modelPath));
                }
            } else { // assume page model reference
                return findFieldObject(tmpl.Model, modelPath);
            }
        }
        return null;
    }

    /** Remove start of string up to and including the first '.' */
    private String stripFirstDotItem(String path) {
        int index = path.indexOf('.');
        if (index < 0) return "";
        if (index+1 >= path.length()) return "";
        return path.substring(index+1);
    }

    /**
     * handle repeater blocks by duplicating their contents and recursing the template process with child objects.
     * Returns number of extra lines consumed from the input template
     * @param startIndex offset into template lines where we should start
     * @param cursorItem 'for' loop item, if any. Otherwise null
     * @param sb output string builder
     * @param tmpl template being read
     */
    private int recurseForBlock(int startIndex, TemplateResponse tmpl, Object cursorItem, StringBuilder sb) {
        // get the target
        String startLine = tmpl.TemplateLines.get(startIndex).trim();
        if (!startLine.startsWith("{{") || !startLine.endsWith("}}")) { // invalid repeat line
            return -1;
        }

        // read the 'for' directive
        String[] bits = readDirective(startLine);
        if (bits == null) return -1; // bad '{{...:...}}' line
        if (!Objects.equals(bits[0], "for")) return 0; // not the start of a 'for' block

        String fieldName = bits[1];

        // scan until we find the next *matching* '{{end:...}}'
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
        if (!found) return -1; // could not find an end point
        int lineCount = endIndex - startIndex;

        // copy out the lines between start and end
        TemplateResponse subset = tmpl.cloneRange(startIndex + 1, endIndex - 1);

        // handle `{{for:item:my_thing}}` -- this allows nested loops on outer loop item.
        Object target = subset.Model;
        if (fieldName.startsWith("item:")) {
            target = cursorItem;
            fieldName = fieldName.substring(5);
        }

        // now we ignore the top and bottom lines, and run everything else through `transformTemplate` again,
        // which might come back here if there are nested 'for' blocks
        Object items = findFieldObject(target, fieldName);
        if (items instanceof Iterable) { // 'for' item is a list. Repeat contents
            @SuppressWarnings("rawtypes") Iterable listItems = (Iterable) items;
            for (Object item : listItems) {
                sb.append(transformTemplate(subset, item));
            }
        } else if (items instanceof Boolean) { // 'for' items is bool. Show if true
            boolean b = (boolean) items;
            if (b) {
                sb.append(transformTemplate(subset, null));
            }
        } else if (items != null) { // something else. Show if not null
            sb.append(transformTemplate(subset, items));
        }

        return lineCount;
    }

    /**
     * break a directive line into parts. Always returns 2 elements or null.
     */
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

    /**
     * break directive params into a hash map.
     * directive params are comma separated, and the key/value are separated by '='.
     * (e.g. `param1=value1, param2=value2,param3=value3`)
     */
    private Map<String, String> readParams(String params) {
        Map<String, String> result = new HashMap<>();
        if (params == null) return result;
        String[] parts = params.split(",");
        for (String part : parts) {
            if (part == null || part.equals("")) continue;
            String[] sides = part.split("=", 2);

            if (sides.length == 1) result.put(sides[0].trim(), sides[0].trim());
            else result.put(sides[0].trim(), sides[1].trim());
        }
        return result;
    }

    /**
     * replace the FIRST template hole we find.
     */
    private String singleReplace(String line, TemplateResponse tmpl, Object cursorItem) {
        String left, right;

        // split "...before...{{name}}...after..."
        // into left="...before..."; right="name}}..after..."
        if (line.startsWith("{{")) {
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
        if (closeIndex < 0) {
            sb.append(right);
            return sb.toString();
        }

        String name = right.substring(0, closeIndex);
        String rest = right.substring(closeIndex + 2);

        if (name.startsWith("item:")) {
            sb.append(findField(cursorItem, name.substring(5)));
        } else {
            // get directly from model
            sb.append(findField(tmpl.Model, name));
        }
        sb.append(rest);

        return sb.toString();
    }

    /**
     * Hunts though object hierarchies for values.
     * The simplest and fastest path is to have a single field name.
     */
    private Object searchForField(Object model, String name) throws NoSuchFieldException, IllegalAccessException {
        if (!name.contains(".")) { // simple case
            if (model instanceof Map) {
                try {
                    Map<?, ?> map = (Map<?, ?>) model;
                    return map.get(name);
                } catch (Exception ex) {
                    Log.w(TAG, "Failed to find '"+name+"' in map");
                    return null;
                }
            } else {
                return model.getClass().getField(name).get(model);
            }
        }

        // We have a dotted path. Split the path up, and step through each element.
        Object src = model;
        String[] pathBits = name.split("[.]");

        // walk down the chain
        for (String bit : pathBits) {
            if (src == null) return null;

            if (looksLikeInt(bit)) { // index into iterable, or fail
                int idx = Integer.parseInt(bit);
                src = getIterableIndexed(src, idx);
            } else if (src instanceof Map) { // try to find a value by map
                try {
                    Map<?, ?> map = (Map<?, ?>) src;
                    if (map.containsKey(bit)) src = map.get(bit);
                } catch (Exception ex) {
                    Log.w(TAG, "Failed to find '"+bit+"' in map");
                    return null;
                }
            } else { // get field by name, or fail
                src = src.getClass().getField(bit).get(src);
            }
        }
        return src;
    }

    /**
     * try to read the value of an object at the given index. Returns null on failure
     */
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
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Returns true if the string contains only ascii numbers
     */
    private boolean looksLikeInt(String str) {
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /**
     * find a named field in the given object. The name can be a dotted path (e.g. "parent.child.item")
     * This is like `findField`, but returns an object instead of a string.
     */
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

    /**
     * find a named field in the given object. The name can be a dotted path (e.g. "parent.child.item")
     * This is like `findFieldObject`, but returns a string instead of an object.
     */
    private String findField(Object model, String name) {
        try {
            if (model == null) return "";
            if (name == null) return "";

            if (name.equals("")) { // special case: {{item:}} output the model as a string
                return model.toString();
            }

            Object value = searchForField(model, name);

            if (value == null) return "";
            return value.toString();
        } catch (Exception ex) {
            return "[" + name + "]";
        }
    }

    /**
     * Generate and populate a TemplateResponse for the given web request
     */
    private TemplateResponse getDocTemplate(WebResourceRequest request, String params, WebMethod wm) throws Exception {
        TemplateResponse resp = wm.generate(mapParams(params), request);
        if (resp == null) throw new Exception("Method gave a bad result");
        if (resp.RedirectUrl != null) return resp;

        resp.TemplateLines = new ArrayList<>();
        copyLinesToTemplate("views/" + resp.TemplatePath + ".html", resp);

        return resp;
    }

    /**
     * read template file into an array of lines
     */
    public void copyLinesToTemplate(String fileName, TemplateResponse resp) throws IOException {
        resp.TemplateLines.clear();

        Reader rdr;
        try {
            rdr = assets.read(fileName);
        } catch (FileNotFoundException fex){
            rdr = assets.read(fileName+".html");
        }

        // Now read file lines into the template
        BufferedReader br = new BufferedReader(rdr);
        String readLine;
        try {
            // While the BufferedReader readLine is not null
            while ((readLine = br.readLine()) != null) {
                resp.TemplateLines.add(readLine);
            }

            // Close the InputStream and BufferedReader
            if (rdr != null) rdr.close();
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * split url query parameters into a hash map
     */
    private Map<String, String> mapParams(String params) {
        Map<String, String> result = new HashMap<>();
        if (params == null) return result;
        String[] parts = params.split("([&]|[?])");
        for (String part : parts) {
            if (part == null || part.equals("")) continue;
            String[] sides = part.split("=", 2);

            if (sides.length == 1) result.put(sides[0], sides[0]);
            else result.put(sides[0], sides[1]);
        }
        return result;
    }
    //endregion
}