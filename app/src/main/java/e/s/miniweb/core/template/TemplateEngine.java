package e.s.miniweb.core.template;

import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceRequest;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import e.s.miniweb.core.AppWebRouter;
import e.s.miniweb.core.Permissions;
import e.s.miniweb.core.hotReload.AssetLoader;
import e.s.miniweb.core.hotReload.EmulatorHostCall;

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
    public TemplateResponse Run(WebMethod controllerAction, String params, WebResourceRequest request) throws Exception {
        // 'Do' the page action.
        // This results in a template and the data to go with it
        TemplateResponse tmpl = getDocTemplate(request, params, controllerAction);

        if (tmpl.RedirectUrl != null) {
            tmpl.ResponseBody = redirectPage(tmpl);
            tmpl.RedirectUrl = null;
            return tmpl;
        }

        // do the render!
        tmpl.ResponseBody = transformTemplate(tmpl, null);
        EmulatorHostCall.pushLastPage(tmpl.ResponseBody);
        return tmpl;
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
     * Replace {{name}} with tmpl.Model.name.toString()
     * For {{for:name}}...{{end:name}}
     * if tmpl.Model.name is enumerable, repeat the contained section for each item. The template {{item:childField}} becomes available.
     * if tmpl.Model.name is null, or false, or "", then don't show the section
     * otherwise, show the section once.
     * Template holes `{{...}}` must not cross line in the doc -- both the open and close must be on the same line.
     * Any `{{for:...}}` or `{{end:...}}` must be on their own line with only whitespace around them.
     */
    public String transformTemplate(TemplateResponse tmpl, Object cursorItem) {
        StringBuilder test = new StringBuilder();
        for (String s: tmpl.TemplateLines){test.append(s);test.append("\r\n");}

        HNode node = HNode.parse(test.toString());

        lastForBlockWasHidden = false;
        StringBuilder pageOut = new StringBuilder();
        recurseTemplate(node, pageOut, cursorItem, tmpl.Model);

        return pageOut.toString();
/*
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
                        Log.w(TAG, "Could not interpret 'for' block. Check markup and data types." + errFileAndLine(tmpl.TemplatePath, templateLineIndex));
                        pageOut.append(templateLine); // write it out -- both so we can see errors, and so comments don't get broken.
                    }
                    continue;
                }

                // Is it a sub-view?
                if (!isQuoted && templateLine.contains("{{view:")) {
                    int templateLinesConsumed = recurseViewBlock(templateLineIndex, tmpl, cursorItem, pageOut);
                    if (templateLinesConsumed >= 0) { // 'view' should only be 1 line
                        templateLineIndex += templateLinesConsumed;
                    } else {
                        Log.w(TAG, "Could not interpret 'view' block. Check markup and data types." + errFileAndLine(tmpl.TemplatePath, templateLineIndex));
                        pageOut.append(templateLine); // write it out -- both so we can see errors, and so comments don't get broken.
                    }
                    continue;
                }

                // Is it a permission block?
                if (!isQuoted && templateLine.contains("{{needs:")) {
                    // permission control: read contents, outputting if permissions are met, then skip to end of block
                    int templateLinesConsumed = recurseNeedsBlock(templateLineIndex, tmpl, cursorItem, pageOut);
                    if (templateLinesConsumed > 0) {
                        templateLineIndex += templateLinesConsumed;
                    } else {
                        Log.w(TAG, "Could not interpret 'needs' block. Check markup and data types." + errFileAndLine(tmpl.TemplatePath, templateLineIndex));
                        pageOut.append("[ERROR]"); // unlike others, we HIDE failed permission blocks
                    }
                    continue;
                }

                // Trailing 'end'?
                if (!isQuoted && templateLine.contains("{{end:")) {
                    Log.w(TAG, "Unexpected 'end' tag. Either bad markup, or a block was not interpreted correctly. File=" + errFileAndLine(tmpl.TemplatePath, templateLineIndex));
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
            } catch (Exception ex) {
                Log.e(TAG, "Unexpected error in template transformer", ex);

                pageOut.append("\r\n[ERROR: ").append(ex.getMessage()).append("]\r\n");
            }
        }

        return pageOut.toString();*/
    }

    private static boolean lastForBlockWasHidden = false;
    /** Recurse through the HNode tree, rendering output and interpreting directives */
    private static void recurseTemplate(HNode node, StringBuilder out, Object item, Object model){
        // Template directives are never self-closing (i.e. <tag/>). They should not be empty (i.e. <tag></tag>)
        // Known directives:
        //
        //   <_for {path}>{content}</_for>         -- repeat contents for each non-null item in model
        //   <_else>{content}</_else>              -- show if previous _for did not display
        //   <_>{path}</_>                         -- insert value from model
        //   <_needs {perms...}>{content}</_needs> -- show content only if user has at least one of the permissions
        //   <_view {params...}></_view>           -- inject a sub-view into the page
        //
        // Paths
        //
        //   a           -->  model.a         (or model.get("a") )
        //   a.b.c       -->  model.a.b.c
        //   #           -->  item
        //   #.x.y       -->  item.x.y


        // Content or recursion?
        if (node.children.size() < 1) {
            // not a container. Slap in contents
            out.append(node.Src, node.srcStart, node.srcEnd + 1);
        } else {
            if (node.isUnderscored){
                // Special things
                Map<String,String>attrMap=new HashMap<>();
                String tag = decomposeTag(node.Src.substring(node.srcStart, node.contStart), attrMap);
                switch (tag){
                    case "_": // plain data lookup
                    {
                        String path = node.innerText();
                        if (Objects.equals(path, "#")) out.append(findField(item, ""));
                        else if (path.startsWith("#.")) out.append(findField(item, path.substring(2)));
                        else out.append(findField(model, path));
                        break;
                    }
                    case "_for": // loop block
                    {
                        lastForBlockWasHidden = true;
                        if (attrMap.isEmpty()) {
                            out.append("[ERROR]");
                            break;
                        }
                        String path = attrMap.keySet().iterator().next();
                        Object items;
                        if (path.startsWith("#.")) items = findFieldObject(item, path.substring(2));
                        else items = findFieldObject(model, path);
                        if (items instanceof Iterable) { // 'for' item is a list. Repeat contents
                            @SuppressWarnings("rawtypes") Iterable listItems = (Iterable) items;
                            for (Object subItem : listItems) {
                                lastForBlockWasHidden = false;
                                for (HNode child : node.children) recurseTemplate(child, out, subItem, model);
                            }
                        } else if (items instanceof Boolean) { // 'for' items is bool. Show if true
                            boolean b = (boolean) items;
                            if (b) {
                                lastForBlockWasHidden = false;
                                for (HNode child : node.children) recurseTemplate(child, out, items, model);
                            }
                        } else if (items != null) { // something else. Show if not null{
                            lastForBlockWasHidden = false;
                            for (HNode child : node.children) recurseTemplate(child, out, items, model);
                        }
                        break;
                    }
                    case "_else": // else block
                        if (lastForBlockWasHidden) {
                            for (HNode child : node.children) recurseTemplate(child, out, item, model);
                        }
                        break;
                    case "_needs": // needs block
                        out.append("[needs]");
                        break;
                    default:
                        out.append("[ERROR]");
                        Log.w(TAG, "Unknown tag: ");
                        break;
                }
            } else {
                // Normal HTML
                // Opening tag?
                if (node.srcStart < node.contStart) { // false for comments, scripts, etc
                    out.append(node.Src, node.srcStart, node.contStart);
                }
                // Recurse each child
                for (HNode child : node.children) recurseTemplate(child, out, item, model);
                // Closing tag
                if (node.contEnd < node.srcEnd) { // false for comments, scripts, etc
                    out.append(node.Src, node.contEnd + 1, node.srcEnd + 1);
                }
            }
        }
    }

    /** take "<_>", "<_for thing>" etc, and split into parts. Array is always at least 1 element, never null */
    private static String decomposeTag(String tag, Map<String, String> attributes) {
        // TODO: needs to cope with 3 styles:
        // <tag>
        // <tag word.dot word.dot>
        // <tag key="my value" key2="value 2">

        StringBuilder tagName = new StringBuilder();
        StringBuilder key = new StringBuilder();
        StringBuilder value = new StringBuilder();

        int i = 1;
        int end = tag.length()-1;

        // get tag
        for (; i < end; i++){
            char c = tag.charAt(i);
            if (c == ' ') break;
            tagName.append(c);
        }

        // get params
        boolean inQuote = false;
        boolean hasValue = false;
        for (; i < end; i++){
            char c = tag.charAt(i);
            if (c == '"') inQuote = !inQuote;
            else if (c == '=') hasValue = true;
            else if (!inQuote && c == ' ') {
                if (key.length() > 0) {
                    if (hasValue) attributes.put(key.toString(), value.toString());
                    else attributes.put(key.toString(), key.toString());
                }
                key.setLength(0);
                value.setLength(0);
            } else {
                if (inQuote) value.append(c);
                else key.append(c);
            }
        }

        if (key.length() > 0) {
            if (hasValue) attributes.put(key.toString(), value.toString());
            else attributes.put(key.toString(), key.toString());
        }

        return tagName.toString();
    }


    /**
     * Handle permission-gated blocks by calling back out through the template system and injecting results into string builder.
     * Returns number of extra lines consumed from the input template
     *
     * @param startIndex offset into template lines where we should start
     * @param cursorItem 'for' loop item, if any. Otherwise null
     * @param sb         output string builder
     * @param tmpl       template being read
     */
    private int recurseNeedsBlock(int startIndex, TemplateResponse tmpl, Object cursorItem, StringBuilder sb) {
        String startLine = tmpl.TemplateLines.get(startIndex).trim();
        if (!startLine.startsWith("{{") || !startLine.endsWith("}}")) { // invalid directive line
            return -1;
        }

        String[] bits = readDirective(startLine);
        if (bits == null) return -1; // bad '{{...:...}}' line
        if (!Objects.equals(bits[0], "needs")) return -1; // not the start of a 'needs' block

        Map<String, String> requiredPermissions = readParams(bits[1]);


        // scan lines until we find the next *matching* '{{end:needs}}'
        boolean found = false;
        int nestingLevel = 0;
        int endIndex;
        List<String> templateLines = tmpl.TemplateLines;
        int limit = templateLines.size();
        for (endIndex = startIndex + 1; endIndex < limit; endIndex++) {
            String templateLine = templateLines.get(endIndex);

            String[] endBits = readDirective(templateLine);
            if (endBits == null) continue; // bad '{{...:...}}' line
            if (Objects.equals(endBits[0], "needs")) { // a nested block
                nestingLevel++;
                continue;
            }
            if (!Objects.equals(endBits[0], "end")) continue; // not the end of a block
            String endType = endBits[1].trim();
            if (!endType.startsWith("needs")) continue; // not the end of a 'needs' block

            // Found an {{end:needs}}.
            // If we're nested, reduce count and continue.
            // Otherwise, this is our stop.
            if (nestingLevel > 0) {
                nestingLevel--;
                continue;
            }

            found = true;
            break;
        }
        int lineCount = endIndex - startIndex;
        if (!found || nestingLevel < 0) {
            Log.w(TAG, "Failed to find the end of 'needs' block." + errFileAndLine(tmpl.TemplatePath, startIndex));
            sb.append("[ERROR]"); // could not find end point
            return lineCount;
        }

        // If the programmer decorated the end (e.g. `{{end:needs perm1, perm2}}`)
        // Then check it's correct and error if not
        String endLine = templateLines.get(endIndex).trim();
        bits = readDirective(endLine);
        if (bits != null && !Objects.equals(bits[1], "needs")) {
            Map<String, String> endPermissions = readParams(bits[1].substring(6));
            if (!areSameKeys(endPermissions, requiredPermissions)) {
                Log.w(TAG, "End of 'needs' block did not match start." + errFileAndLine(tmpl.TemplatePath, endIndex));
                sb.append("[ERROR]");
                return lineCount;
            }
        }


        // If permissions met, re-run the template and inject
        if (Permissions.HasAnyPermissions(requiredPermissions.keySet().toArray(new String[0]))) {
            // copy out the lines between start and end of our block
            TemplateResponse subset = tmpl.cloneRange(startIndex + 1, endIndex - 1);
            // recurse on the template sub-set
            sb.append(transformTemplate(subset, cursorItem));
        }

        return lineCount;
    }

    /**
     * Handle sub-view blocks by calling back out through the template system and injecting results into string builder.
     * Returns number of extra lines consumed from the input template
     *
     * @param startIndex offset into template lines where we should start
     * @param cursorItem 'for' loop item, if any. Otherwise null
     * @param sb         output string builder
     * @param tmpl       template being read
     */
    private int recurseViewBlock(int startIndex, TemplateResponse tmpl, Object cursorItem, StringBuilder sb) {
        String startLine = tmpl.TemplateLines.get(startIndex).trim();
        if (!startLine.startsWith("{{") || !startLine.endsWith("}}")) { // invalid repeat line
            return -1;
        }

        String[] bits = readDirective(startLine);
        if (bits == null) return -1; // bad '{{...:...}}' line
        if (!Objects.equals(bits[0], "view")) return -1; // not the start of a 'view' block
        Map<String, String> params = readParams(bits[1]);

        if (params.containsKey("url")) { // should do a GET to this URL
            try {
                Uri target = Uri.parse(params.get("url"));
                if (params.containsKey("model")) { // inject model into params?
                    Object viewModel = getViewModelObjectByPath(startIndex, tmpl, cursorItem, params);
                    if (viewModel != null) {
                        target = mapObjectToUrlQuery(target, viewModel);
                    }
                }
                WebResourceRequest request = new InternalRequest(target);
                String response = router.getControllerResponse(request, true);
                sb.append(response);
                return 0;
            } catch (Exception ex) {
                Log.w(TAG, "URL view block failed:" + errFileLineAndError(tmpl.TemplatePath, startIndex, ex));
                sb.append("[ERROR]");
                return -1;
            }
        }

        // exit early if there is no path
        if (!params.containsKey("path")) {
            Log.w(TAG, "Invalid view block: no url or path. Check the mark-up, and ensure ',' separators." + errFileAndLine(tmpl.TemplatePath, startIndex));
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
        } catch (FileNotFoundException fex) {
            Log.w(TAG, "Failed to load view! TargetFile=" + viewPath + "; ParentFile=" + tmpl.TemplatePath + "; Line=" + startIndex);
            return -1;
        } catch (Exception ex) {
            Log.w(TAG, "Failed to load view block:" + errFileLineAndError(tmpl.TemplatePath, startIndex, ex));
            return -1;
        }
    }

    /**
     * handle repeater blocks by duplicating their contents and recursing the template process with child objects.
     * Returns number of extra lines consumed from the input template
     *
     * @param startIndex offset into template lines where we should start
     * @param cursorItem 'for' loop item, if any. Otherwise null
     * @param sb         output string builder
     * @param tmpl       template being read
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

    private String errFileAndLine(String file, int lineIdx) {
        return "; File=" + file + "; Line=" + (lineIdx + 1);
    }

    private String errFileLineAndError(String file, int lineIdx, Exception ex) {
        return "; File=" + file + "; Line=" + (lineIdx + 1) + "; Error=" + ex;
    }

    private boolean areSameKeys(Map<String, String> endPermissions, Map<String, String> requiredPermissions) {
        Set<String> left = endPermissions.keySet();
        Set<String> right = requiredPermissions.keySet();

        return left.equals(right);
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

    /**
     * Try to map the fields of an object into a url by appending query parameters
     */
    private Uri mapObjectToUrlQuery(Uri target, Object viewModel) {
        Uri.Builder builder = target.buildUpon();
        Map<String, String> modelAsMap = convertObjectToMap(viewModel);
        for (String key : modelAsMap.keySet()) {
            builder = builder.appendQueryParameter(key, modelAsMap.get(key));
        }
        target = builder.build();
        return target;
    }

    /**
     * extract public fields to a hash map
     */
    private Map<String, String> convertObjectToMap(Object obj) {
        Map<String, String> map = new HashMap<>();

        if (obj != null) {
            Field[] fields = obj.getClass().getFields();
            for (Field f : fields) {
                if (f == null) continue;
                Object fieldVal;
                try {
                    fieldVal = f.get(obj);
                } catch (IllegalAccessException e) {
                    continue;
                }
                if (fieldVal != null) {
                    map.put(f.getName(), fieldVal.toString());
                }
            }
        }

        return map;
    }

    /**
     * try to find an object given a `model.path.to.thing` or `item.path.to.thing`
     */
    private Object getViewModelObjectByPath(int lineIdx, TemplateResponse tmpl, Object cursorItem, Map<String, String> params) {
        if (params.containsKey("model")) {
            String modelPath = params.get("model");
            if (modelPath == null || modelPath.equals("")) {
                Log.w(TAG, "invalid view block: empty model." + errFileAndLine(tmpl.TemplatePath, lineIdx));
                return null;
            } else if (modelPath.startsWith("model")) { // looks like a page model reference
                if (modelPath.equals("model")) { // view should get the whole model
                    return tmpl.Model;
                } else { // need to find by path
                    return findFieldObject(tmpl.Model, stripFirstDotItem(modelPath));
                }
            } else if (modelPath.startsWith("item")) { // looks like a 'for' block item reference
                if (modelPath.equals("item")) { // view should get the whole iterated item
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

    /**
     * Remove start of string up to and including the first '.'
     */
    private String stripFirstDotItem(String path) {
        int index = path.indexOf('.');
        if (index < 0) return "";
        if (index + 1 >= path.length()) return "";
        return path.substring(index + 1);
    }

    /**
     * break a directive line into parts. Always returns 2 elements or null.
     */
    private String[] readDirective(String line) {
        try {
            line = line.trim();
            if (line.length() < 4) return null; // too short to be valid
            if (!line.startsWith("{{") || !line.endsWith("}}")) return null; // not a directive line
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
    private static Object searchForField(Object model, String name) throws NoSuchFieldException, IllegalAccessException {
        if (!name.contains(".")) { // simple case
            if (model instanceof Map) {
                try {
                    Map<?, ?> map = (Map<?, ?>) model;
                    return map.get(name);
                } catch (Exception ex) {
                    Log.w(TAG, "Failed to find '" + name + "' in map");
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
                    Log.w(TAG, "Failed to find '" + bit + "' in map");
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
    private static Object getIterableIndexed(Object src, int idx) {
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
    private static boolean looksLikeInt(String str) {
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
    private static Object findFieldObject(Object model, String name) {
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
    private static String findField(Object model, String name) {
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
    private TemplateResponse getDocTemplate(WebResourceRequest request, String params, WebMethod controllerMethod) throws Exception {

        // This is the call to the controller method:
        TemplateResponse resp = controllerMethod.RunControllerMethod(mapParams(params), request);

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

        InputStream is = null;
        try {
            try {
                is = assets.open(fileName);
            } catch (FileNotFoundException fex) {
                is = assets.open(fileName + ".html");
            }

            Reader rdr = new InputStreamReader(is);
            // Now read file lines into the template
            BufferedReader br = new BufferedReader(rdr);
            String readLine;
            try {
                // While the BufferedReader readLine is not null
                while ((readLine = br.readLine()) != null) {
                    resp.TemplateLines.add(readLine);
                }

                // Close the InputStream and BufferedReader
                is.close();
                rdr.close();
                br.close();
                is = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } finally {
            if (is != null) is.close();
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