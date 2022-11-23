package e.s.miniweb.core;

import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.webkit.SafeBrowsingResponse;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Objects;
import java.util.Stack;

import e.s.miniweb.ControllerBindings;
import e.s.miniweb.R;
import e.s.miniweb.core.hotReload.AssetLoader;
import e.s.miniweb.core.hotReload.EmulatorHostCall;
import e.s.miniweb.core.hotReload.HotReloadMonitor;
import e.s.miniweb.core.template.InternalRequest;
import e.s.miniweb.core.template.TemplateEngine;
import e.s.miniweb.core.template.TemplateResponse;
import e.s.miniweb.core.template.WebMethod;

public class AppWebRouter extends WebViewClient {
    private static final String TAG = "AppWebRouter";
    private final String HtmlMime = "text/html";
    private final TemplateEngine template;
    private final AssetLoader assets;
    private final MainActivity mainView;

    private static final Stack<String> historyStack = new Stack<>(); // static so it survives warm reload

    public boolean clearHistory;

    public AppWebRouter(AssetLoader assets, MainActivity main){
        template = new TemplateEngine(assets, this);
        this.assets = assets;
        this.mainView = main;

        // Prepare all the controllers for everything
        ControllerBindings.BindAllControllers();
    }

    /**
     * Core of the router. This provides app-generated data for a web request
     * rather than loading from a network request.
     */
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        // This method is called for every page and asset: navigation events and the
        // Assets loaded subsequently based on the page that is loaded.
        // Return null when the web view should get the page from the real internet.
        // If we return a proper WebResourceResponse, the web view will render that.
        // We could check for a https://*.my-site.com url for going outside etc.

        // Ask the routing & template system for a result
        PageResult pageString = getResultContent(request);

        if (pageString == null){ // the routing did not work. Pass up to system
            return super.shouldInterceptRequest(view, request);
        }

        // is this a raw data result (i.e. from a file)
        if (pageString.rawData != null) {
            return new WebResourceResponse(pageString.mimeType, null, pageString.rawData);
        }

        // is it junk?
        if (pageString.data == null) return null;

        // Ok, looks like a string. Encode and output
        InputStream data = new ByteArrayInputStream(pageString.data.getBytes());
        return new WebResourceResponse(pageString.mimeType, "utf-8", data);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        // This gets called only for page-to-page navigation requests, and not
        // for any in-page asset loading requests. This provides a useful hook
        // where we can clear the 'hot-reload' asset list.
        HotReloadMonitor.ClearReload();

        // We return value true if we want to do anything OTHER than navigation.
        // Like, if we wanted a link to show some kind of Android system screen.
        // If the intent is to handle the click by navigating to one of our own,
        // self-generated pages, we return false and use shouldInterceptRequest.
        return false;
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        Log.e(TAG, error.toString());
    }

    @Override
    public void onPageFinished(WebView view, String url)
    {
        super.onPageFinished(view, url);

        mainView.pageLoaded();
        if (clearHistory)
        {
            clearHistory = false;
            historyStack.clear();
        }

        // if the new page is different to the old, add it to the history stack
        if (historyStack.empty() || !Objects.equals(url, historyStack.peek())) historyStack.push(url);

        view.clearHistory(); // we don't use the real history
        view.clearCache(true); // clear any cached files. We don't need them
    }

    /** try to reload previous page. If not possible, returns false */
    public boolean goBack(WebView view) {
        if (view == null) return false;
        if (historyStack.size() < 2) {
            return false; // current page is top of stack
        }
        ClearHotLoad();
        historyStack.pop(); // remove current page
        view.loadUrl(historyStack.pop()); // load previous page (it will be put back on when loaded)
        return true;
    }

    public String getCurrentPage() {
        return historyStack.peek();
    }

    @Override
    public void onSafeBrowsingHit(WebView view, WebResourceRequest request, int threatType, SafeBrowsingResponse callback) {
        Log.i(TAG, "WebView tagged resource as a threat. Ignoring.");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            callback.proceed(false);
        }
    }

    /** Decode what kind of thing is being requested,
     * and pass to the appropriate handler
     */
    private PageResult getResultContent(WebResourceRequest request) {
        PageResult pageResult = new PageResult();
        String controller = "?";
        String method = "?";
        try {
            Uri url = request.getUrl();
            if (url == null){
                pageResult.data = errorPage(App.str(R.string.msg_err_bad_url)+": null", "?", "?");
                pageResult.mimeType = HtmlMime;
                pageResult.hotReloadCandidate = false;
                return pageResult;
            }
            // guess controller and method in case of crash
            controller = url.getHost();
            method = url.getPath();

            String scheme = request.getUrl().getScheme(); // can use to direct out
            if (scheme == null){
                pageResult.data = errorPage(App.str(R.string.msg_err_bad_url)+": "+request.getUrl().toString(), controller, method);
                pageResult.mimeType = HtmlMime;
                pageResult.hotReloadCandidate = false;
                return pageResult;

            } else if (Objects.equals(scheme, "app")) { // controller pages
                pageResult.data = getControllerResponse(request, false); // Run the controller & template engine
                pageResult.mimeType = HtmlMime;
                pageResult.hotReloadCandidate = true;

                if (request.getClass() != InternalRequest.class) EmulatorHostCall.pushLastPage(pageResult.data);

                return pageResult;

            } else if (scheme.equals("asset")) { // request for a file stored in the APK
                String path = url.getHost()+url.getPath();
                controller = "asset fetch";
                method = path;

                HotReloadMonitor.AddHotReloadAsset(path);
                pageResult.rawData = getRawAsset(path);
                pageResult.mimeType = guessMime(path);
                pageResult.hotReloadCandidate = false;
                return pageResult;


            } else if (scheme.equals("content")) { // request for an Android service's data
                // TODO: handle this
                return null;
            } else {
                pageResult.data = errorPage(App.str(R.string.msg_err_bad_url_type)+": "+scheme, controller, method);
                pageResult.mimeType = HtmlMime;
                pageResult.hotReloadCandidate = false;
                return pageResult;
            }

        } catch (Exception ex) {
            pageResult.data = errorPage(ex.getMessage(), controller, method);
            pageResult.mimeType = HtmlMime;
            pageResult.hotReloadCandidate = false;
            return pageResult;
        }
    }

    /**
     * Render a page through a controller method
     */
    public String getControllerResponse(WebResourceRequest request, boolean isPartialView) throws Exception {
        String pageString;
        Uri url = request.getUrl();
        String controller = url.getHost();
        String method = url.getPath();
        if (method == null) method = "";
        if (method.startsWith("/")) method = method.substring(1);
        if (method.equals("")) method = "index";
        String params = url.getQuery();

        // Check for 'expect hot reload' here
        String response = null;
        if (Objects.equals(controller, hotController) && method.equals(hotMethod) && Objects.equals(params, hotParams)) {
            // This is a hot reload. Don't actually run the template.
            Log.i(TAG, "Hot-reloading: " + url);
            response = HotReloadMonitor.RunHotReload(template);
        }

        // Either a normal call, or hot reload failed.
        if (response == null) {
            // Call the controller for a page render
            response = runTemplate(controller, method, params, request, isPartialView);
        }

        if (!isPartialView) ClearHotLoad();

        if (response == null) {// Output an error page
            pageString = errorPage(App.str(R.string.msg_err_404), controller, method);
        } else if (isPartialView) { // should not add document wrappers
            pageString = response;
        } else if (response.startsWith("<!doctype html>") || response.startsWith("<html")) { // already has document wrappers
            pageString = response;
        } else {
            // We got a body fragment. Wrap the response in a default document and deliver
            pageString = wrapPageStringWithHtmlHeaders(response);
        }

        return pageString;
    }

    /** This is where we call down to TemplateEngine for cold-calls */
    private String runTemplate(String controller, String method, String params, WebResourceRequest request, boolean isPartialView) throws Exception {
        String key = ControllerBinding.makeKey(controller, method);

        // Check the url is valid
        if (!ControllerBinding.hasMethod(key)) {
            HotReloadMonitor.ClearReload();
            Log.e(TAG, "Unknown web method! Controller="+controller+"; Method="+method);
            return null;
        }

        // Check permissions
        if (!ControllerBinding.isPermitted(key)) {
            // permissions check failed. Bail out with an error
            HotReloadMonitor.ClearReload();
            Log.e(TAG, "Permission check failed! Controller="+controller+"; Method="+method);
            return template.internalTemplate("not-permitted", null);
        }

        WebMethod controllerAction = ControllerBinding.getMethod(key);

        if (controllerAction == null) {
            Log.e(TAG, "Invalid web method -- no 'WebMethod' bound! Controller="+controller+"; Method="+method);
            return null;
        }

        // We have enough permission. Run the method and fill in the template
        TemplateResponse tmpl = template.Run(controllerAction, params, request);

        // extract response
        String finalOutput = tmpl.ResponseBody;
        tmpl.ResponseBody = null;

        // save hot-reload data
        HotReloadMonitor.AddHotReloadPage(tmpl);
        if (!isPartialView) {
            HotReloadMonitor.lastPageRendered = tmpl;
            HotReloadMonitor.lastPageRendered.Controller = controller;
            HotReloadMonitor.lastPageRendered.Method = method;
            HotReloadMonitor.lastPageRendered.Params = params;
        }

        return finalOutput;
    }

    private String wrapPageStringWithHtmlHeaders(String response) {
        StringBuilder sb = new StringBuilder();

        sb.append("<!doctype html><html><head><meta charset=\"UTF-8\">"); // document with header and char set.
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />"); // makes styling consistent
        sb.append("<link rel=\"stylesheet\" href=\"asset://");
        sb.append(App.str(R.string.path_styles));
        sb.append("default.css\" type=\"text/css\">"); // default styles for both light & dark

        // Add specific dark or light mode styles
        sb.append("<link rel=\"stylesheet\" href=\"asset://");
        sb.append(App.str(R.string.path_styles));
        if (mainView.inDarkMode()){
            sb.append("default-dark.css");
        } else {
            sb.append("default-light.css");
        }
        sb.append("\" type=\"text/css\">");

        sb.append("</head><body>");
        sb.append(response);
        sb.append("</body></html>");

        return sb.toString();
    }

    private String errorPage(String message, String controller, String method) {
        //noinspection unused
        ErrorModel model = new ErrorModel();
        model.Controller = controller;
        model.Message = message;
        model.Method = method;
        return template.internalTemplate("error.html", model);
    }

    private InputStream getRawAsset(String path) {
        try {
            return assets.open(path);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to load asset '"+path+"'; Error="+ex);
            return null;
        }
    }

    private String guessMime(String path) {
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".html")) return HtmlMime;
        if (path.endsWith(".js")) return "application/javascript";

        if (path.endsWith(".svg")) return "image/svg+xml";

        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".webp")) return "image/webp";
        if (path.endsWith(".jpg")) return "image/jpeg";
        if (path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".gif")) return "image/gif";

        return "application/octet-stream";
    }

    private String hotController;
    private String hotMethod;
    private String hotParams;
    public void ExpectHotReload(String controller, String method, String params) {
        hotController=controller;
        hotMethod=method;
        hotParams=params;
    }
    public void ClearHotLoad(){
        hotController=null;
        hotMethod=null;
        hotParams=null;
    }

    private static class ErrorModel {
        public String Message;
        public String Controller;
        public String Method;
    }

    private static class PageResult {
        public String data;
        public String mimeType;
        public InputStream rawData;
        public boolean hotReloadCandidate;
    }
}