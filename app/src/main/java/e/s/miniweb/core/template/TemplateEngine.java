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
import java.util.Map;
import java.util.Objects;

import e.s.miniweb.R;
import e.s.miniweb.core.App;
import e.s.miniweb.core.AppWebRouter;
import e.s.miniweb.core.Permissions;
import e.s.miniweb.core.hotReload.AssetLoader;

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

            copyLinesToTemplate(App.str(R.string.path_internal) + name, tmpl);
            return transformTemplate(tmpl, null);
        } catch (Exception ex) {
            // don't call the error template. Something might be broken with the apk!
            StringBuilder sb = new StringBuilder();
            sb.append("<h1>");
            sb.append(App.str(R.string.msg_err_header));
            sb.append("</h1><pre>\r\n");
            StackTraceElement[] stack = ex.getStackTrace();
            for (StackTraceElement element : stack) {
                sb.append(element.toString());
            }
            sb.append("</pre>");
            return sb.toString();
        }
    }

    //region Render core

    /** This mess is for GC optimisation. It all gets inlined anyway. */
    static class PageOut {
        private final StringBuilder out;
        private final StringBuilder tag;
        private final StringBuilder key;
        private final StringBuilder value;
        public PageOut(){
            out = new StringBuilder();
            tag = new StringBuilder();
            key = new StringBuilder();
            value = new StringBuilder();
        }
        public final void add(String t) {out.append(t);}
        public final void error(){out.append(App.str(R.string.msg_err_inline));}
        public final void addRange(String t, int left, int right){out.append(t, left, right);}
        public final String output(){return out.toString();}

        public final void resetTag() {
            tag.setLength(0);
            key.setLength(0);
            value.setLength(0);
        }

        public void tagAdd(char c) {tag.append(c);}
        public String tagStr() {return tag.toString();}

        public void tagAddToMap(boolean hasValue, Map<String, String> attributes) {
            if (key.length() > 0) {
                if (hasValue) attributes.put(key.toString(), value.toString());
                else attributes.put(key.toString(), key.toString());
            }
        }
        public void valueAdd(char c) {value.append(c);}
        public void keyAdd(char c) {key.append(c);}
    }

    /**
     * Parse the template into a basic HTML tree.
     * Walk that tree, doing template replacements where needed.
     */
    public String transformTemplate(TemplateResponse tmpl, Object cursorItem) {
        StringBuilder test = new StringBuilder();
        for (String line: tmpl.TemplateLines){
            test.append(moustacheReplace(tmpl.Model, line));
            test.append("\r\n");
        }

        HNode node = HNode.parse(test.toString());

        lastBlockWasHidden = false;
        PageOut pageOut = new PageOut();
        recurseTemplate(node, pageOut, cursorItem, tmpl.Model);

        return pageOut.output();
    }

    /** Replace '{{ var name }}' templates */
    private String moustacheReplace(Object model, String line) {
        // Hopefully the most common: do nothing.
        if (!line.contains("{{") || !line.contains("}}")) return line;

        // scan through the line, replacing as we go
        StringBuilder sb = new StringBuilder();
        int left = 0;
        int end = line.length() - 1;
        while (left < end){
            int next = line.indexOf("{{", left);
            if (next < 0) break;

            int term = line.indexOf("}}", left);
            if (term < next) {
                sb.append(line.substring(left, term+2));
                left = term + 2;
                continue;
            }

            String key = line.substring(next+2, term);
            sb.append(line.substring(left, next));
            sb.append(findField(model, key));
            left = term + 2;
        }
        sb.append(line.substring(left));
        return sb.toString();
    }

    /** controls 'else' for 'for' and 'needs' */
    private static boolean lastBlockWasHidden = false;

    /** Recurse through the HNode tree, rendering output and interpreting directives */
    private void recurseTemplate(HNode node, PageOut page, Object item, Object model){
        // This method is the largest in the project.
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
        if (!node.isUnderscored && node.children.size() < 1) {
            // not a container. Slap in contents
            page.addRange(node.Src, node.srcStart, node.srcEnd + 1);
        } else {
            if (node.isUnderscored){
                // Special things
                Map<String, String> params = new HashMap<>();
                String tag = decomposeTag(page, node.Src.substring(node.srcStart, node.contStart), params);
                switch (tag){
                    case "_": // plain data lookup
                    {
                        String path = node.innerText();
                        if (Objects.equals(path, "#")) page.add(findField(item, ""));
                        else if (path.startsWith("#.")) page.add(findField(item, path.substring(2)));
                        else page.add(findField(model, path));
                        break;
                    }
                    case "_for":
                    {
                        lastBlockWasHidden = true;
                        if (params.isEmpty()) {
                            page.error();
                            break;
                        }
                        String path = params.keySet().iterator().next();
                        Object items;
                        if (path.startsWith("#.")) items = findFieldObject(item, path.substring(2));
                        else items = findFieldObject(model, path);
                        if (items instanceof Iterable) { // 'for' item is a list. Repeat contents
                            @SuppressWarnings("rawtypes") Iterable listItems = (Iterable) items;
                            for (Object subItem : listItems) {
                                lastBlockWasHidden = false;
                                for (HNode child : node.children) recurseTemplate(child, page, subItem, model);
                            }
                        } else if (items instanceof Boolean) { // 'for' items is bool. Show if true
                            boolean b = (boolean) items;
                            if (b) {
                                lastBlockWasHidden = false;
                                for (HNode child : node.children) recurseTemplate(child, page, items, model);
                            }
                        } else if (items != null) { // something else. Show if not null{
                            lastBlockWasHidden = false;
                            for (HNode child : node.children) recurseTemplate(child, page, items, model);
                        }
                        break;
                    }
                    case "_else":
                    {
                        if (lastBlockWasHidden) {
                            for (HNode child : node.children) recurseTemplate(child, page, item, model);
                        }
                        break;
                    }
                    case "_needs":
                    {
                        String[] required = params.keySet().toArray(new String[0]);
                        if (required.length > 0 && Permissions.HasAnyPermissions(required)) {
                            lastBlockWasHidden = false;
                            for (HNode child : node.children) recurseTemplate(child, page, item, model);
                        } else {
                            lastBlockWasHidden = true;
                        }
                        break;
                    }
                    case "_view":
                    {
                        injectViewBlock(params, model, item, page);
                        break;
                    }
                    default: {
                        page.error();
                        Log.w(TAG, "Unknown tag: '" + tag + "'");
                        break;
                    }
                }
            } else {// Normal HTML
                // Opening tag. False for comments, scripts, etc
                if (node.srcStart < node.contStart) page.addRange(node.Src, node.srcStart, node.contStart);
                // Recurse each child
                for (HNode child : node.children) recurseTemplate(child, page, item, model);
                // Closing tag. False for comments, scripts, etc
                if (node.contEnd < node.srcEnd) page.addRange(node.Src, node.contEnd + 1, node.srcEnd + 1);
            }
        }
    }

    /** take "<_>", "<_for thing>" etc, and split into parts. Array is always at least 1 element, never null */
    private static String decomposeTag(PageOut page, String tag, Map<String, String> attributes) {
        int i = 1;
        int end = tag.length()-1;
        page.resetTag();

        // get tag
        for (; i < end; i++){
            char c = tag.charAt(i);
            if (c == ' ') break;
            page.tagAdd(c);
        }
        String tagOut = page.tagStr();

        // get params
        boolean inQuote = false;
        boolean hasValue = false;
        for (; i < end; i++){
            char c = tag.charAt(i);
            if (c == '"') inQuote = !inQuote;
            else if (!inQuote && c == '=') hasValue = true;
            else if (!inQuote && c == ' ') {
                page.tagAddToMap(hasValue, attributes);
                hasValue = false;
                page.resetTag();
            } else {
                if (hasValue) page.valueAdd(c);
                else page.keyAdd(c);
            }
        }

        page.tagAddToMap(hasValue, attributes);
        return tagOut;
    }


    /**
     * Handle sub-view blocks by calling back out through the template system and injecting results into string builder.
     * Returns number of extra lines consumed from the input template
     *
     */
    private void injectViewBlock(Map<String, String> params, Object model, Object cursorItem, PageOut page) {
        if (params.containsKey("url")) { // should do a GET to this URL
            try {
                Uri target = Uri.parse(params.get("url"));
                if (params.containsKey("model")) { // inject model into params?
                    Object viewModel = getViewModelObjectByPath(model, cursorItem, params);
                    if (viewModel != null) {
                        target = mapObjectToUrlQuery(target, viewModel);
                    }
                }
                WebResourceRequest request = new InternalRequest(target);
                String response = router.getControllerResponse(request, true);
                page.add(response);
                return;
            } catch (Exception ex) {
                Log.w(TAG, "URL view block failed:" + ex);
                page.error();
                return;
            }
        }

        // exit early if there is no path
        if (!params.containsKey("path")) {
            Log.w(TAG, "Invalid view block: no url or path. Check page mark-up.");
            page.error();
            return;
        }

        // Try to find a model item. We will assume page's model if no prefix is given.
        // If we can't find anything, we will pass in `null`
        Object viewModel = getViewModelObjectByPath(model, cursorItem, params);

        // Try to load the view into a new template
        String viewPath = App.str(R.string.path_views) + params.get("path");
        try {
            TemplateResponse viewTmpl = new TemplateResponse();
            viewTmpl.TemplateLines = new ArrayList<>();
            viewTmpl.Model = viewModel;

            copyLinesToTemplate(viewPath, viewTmpl);
            page.add(transformTemplate(viewTmpl, null));
        } catch (FileNotFoundException fex) {
            Log.w(TAG, "Failed to load view! TargetFile=" + viewPath);
            page.error();
        } catch (Exception ex) {
            Log.w(TAG, "Failed to load view block:" + ex);
            page.error();
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

    /**
     * Try to map the fields of an object into a url by appending query parameters
     */
    private static Uri mapObjectToUrlQuery(Uri target, Object viewModel) {
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
    private static Map<String, String> convertObjectToMap(Object obj) {
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
    private static Object getViewModelObjectByPath(Object model, Object cursorItem, Map<String, String> params) {
        if (params.containsKey("model")) {
            String modelPath = params.get("model");
            if (modelPath == null || modelPath.equals("")) {
                Log.w(TAG, "invalid view block: empty model.");
                return null;
            } else if (modelPath.startsWith("model")) { // looks like a page model reference
                if (modelPath.equals("model")) { // view should get the whole model
                    return model;
                } else { // need to find by path
                    return findFieldObject(model, stripFirstDotItem(modelPath));
                }
            } else if (modelPath.startsWith("item")) { // looks like a 'for' block item reference
                if (modelPath.equals("item")) { // view should get the whole iterated item
                    return cursorItem;
                } else { // need to find by path
                    return findFieldObject(cursorItem, stripFirstDotItem(modelPath));
                }
            } else { // assume page model reference
                return findFieldObject(model, modelPath);
            }
        }
        return null;
    }

    /**
     * Remove start of string up to and including the first '.'
     */
    private static String stripFirstDotItem(String path) {
        int index = path.indexOf('.');
        if (index < 0) return "";
        if (index + 1 >= path.length()) return "";
        return path.substring(index + 1);
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

            if (name.equals("")) return model; // special case <_for> -> use the model directly

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

            if (name.equals("")) { // special case: <_></_> : output the model as a string
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
        copyLinesToTemplate(App.str(R.string.path_views) + resp.TemplatePath + ".html", resp);

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