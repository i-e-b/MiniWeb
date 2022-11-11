package e.s.miniweb.core;

import android.content.res.AssetManager;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Objects;

import e.s.miniweb.ControllerBindings;
import e.s.miniweb.core.template.TemplateEngine;

public class AppWebRouter extends WebViewClient {
    private static final String TAG = "AppWebRouter";
    private final String HtmlMime = "text/html";
    final private TemplateEngine template;
    private final AssetManager assets;
    public boolean clearHistory;

    public AppWebRouter(AssetManager assets){
        template = new TemplateEngine(assets);
        this.assets = assets;

        // Prepare all the controllers for everything
        ControllerBindings.BindAllControllers();
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        // We return value true if we want to do anything OTHER than navigation
        // like, if we wanted a link to show some kind of Android system screen.
        // If the intent is to handle the click by navigating to one of our own
        // self-generated pages, we return false and use 'shouldInterceptRequest'
        return false;
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        Log.e(TAG, error.toString());
    }

    @Override
    public void onPageFinished(WebView view, String url)
    {
        if (clearHistory)
        {
            clearHistory = false;
            view.clearHistory();
        }
        super.onPageFinished(view, url);
        view.clearCache(true); // clear any cached files. We don't need them
    }

    /**
     * Core of the router. This provides app-generated data for a web request
     * rather than loading from a network request.
     */
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        // Return null when the web view should get the page from the real internet.
        // If we return a proper WebResourceResponse, the web view will render that.
        // We could check for a https://*.my-site.com url for going outside etc.

        // Ask the routing & template system for a result
        PageResult pageString = getResultContent(request);

        if (pageString == null){ // the routing did not work. Pass up to system
            return super.shouldInterceptRequest(view, request);
        }

        // is this a raw data result (i.e. from a file)
        if (pageString.rawData != null){
            return new WebResourceResponse(pageString.mimeType, null, pageString.rawData);
        }

        // is it junk?
        if (pageString.data == null) return null;

        // Ok, looks like a string. Encode and output
        InputStream data = new ByteArrayInputStream(pageString.data.getBytes());
        return new WebResourceResponse(pageString.mimeType, "utf-8", data);
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
                pageResult.data = errorPage("Invalid url: null", "?", "?");
                pageResult.mimeType = HtmlMime;
                return pageResult;
            }
            // guess controller and method in case of crash
            controller = url.getHost();
            method = url.getPath();

            String scheme = request.getUrl().getScheme(); // can use to direct out
            if (scheme == null){
                pageResult.data = errorPage("Invalid url: "+request.getUrl().toString(), controller, method);
                pageResult.mimeType = HtmlMime;
                return pageResult;

            } else if (Objects.equals(scheme, "app")) { // controller pages

                pageResult.data = getControllerResponse(request);
                pageResult.mimeType = HtmlMime;
                return pageResult;

            } else if (scheme.equals("asset")) { // request for a file stored in the APK
                String path = url.getHost()+url.getPath();
                controller = "asset fetch";
                method = path;

                pageResult.rawData = getRawAsset(path);
                pageResult.mimeType = guessMime(path);
                return pageResult;


            } else if (scheme.equals("content")) { // request for an Android service's data
                // TODO: handle this
                return null;
            } else {
                pageResult.data = errorPage("Unknown url type: "+scheme, controller, method);
                pageResult.mimeType = HtmlMime;
                return pageResult;
            }

        } catch (Exception ex) {
            pageResult.data = errorPage(ex.getMessage(), controller, method);
            pageResult.mimeType = HtmlMime;
            return pageResult;
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
        if (path.endsWith(".gif")) return "image/gif"; // pronounced "hiff"

        return "application/octet-stream";
    }

    private String getControllerResponse(WebResourceRequest request) throws Exception {
        String pageString;
        String controller = request.getUrl().getHost();
        String method = request.getUrl().getPath();
        if (method == null) method = "";
        if (method.startsWith("/")) method = method.substring(1);
        if (method.equals("")) method = "index";
        String params = request.getUrl().getQuery();

        String response = template.Run(controller, method, params, request);

        if (response == null) {
            // Output an error page
            pageString = errorPage("Missing page", controller, method);
        } else if (response.startsWith("<!doctype html>") || response.startsWith("<html")) {
            pageString = response; // template is for a complete doc. Dev is responsible for styles etc.
        } else {
            // We got a body fragment. Wrap the response in a default document and deliver
            pageString =
                    "<!doctype html><html><head><link rel=\"stylesheet\" href=\"asset://styles/default.css\" type=\"text/css\"></head>" +
                    "<body>" + response + "</body></html>";
        }

        return pageString;
    }

    private String errorPage(String message, String controller, String method) {
        //noinspection unused
        ErrorModel model = new ErrorModel();
        model.Controller = controller;
        model.Message = message;
        model.Method = method;
        return template.internalTemplate("error.html", model);
    }

    private static class ErrorModel {
        public String Message;
        public String Controller;
        public String Method;
    }

    private InputStream getRawAsset(String path) {
        try {
            return assets.open(path);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            return null;
        }
    }

    private static class PageResult {
        public String data;
        public String mimeType;
        public InputStream rawData;
    }
}