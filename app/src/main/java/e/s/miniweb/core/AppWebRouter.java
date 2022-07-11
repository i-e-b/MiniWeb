package e.s.miniweb.core;

import android.content.res.AssetManager;
import android.net.Uri;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;

import e.s.miniweb.ControllerBindings;
import e.s.miniweb.template.TemplateEngine;

public class AppWebRouter extends WebViewClient {
    final private TemplateEngine template;
    private final AssetManager assets;
    private final WebView view;
    private String lastAppUrl;
    public boolean clearHistory;

    public AppWebRouter(AssetManager assets, WebView view){
        template = new TemplateEngine(assets);
        this.assets = assets;
        this.view = view;

        // Prepare all the controllers for everything
        ControllerBindings.BindAllControllers();
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        // We return value true if we want to do anything OTHER than navigation
        // like, if we wanted a link to show some kind of Android system screen.
        // If the intent is to handle the click by navigating to one of our own
        // self-generated pages, we return true and use 'shouldInterceptRequest'
        return false;
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        System.out.println(error.toString());
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
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        // Return null when the web view should get the page from the real internet.
        // If we return a proper WebResourceResponse, the web view will render that.
        // We could check for a https://*.mysite.com url for going outside etc.

        PageResult pageString = getResultContent(request);
        if (pageString.data == null) return null;

        InputStream data = new ByteArrayInputStream(pageString.data.getBytes());
        return new WebResourceResponse(pageString.mimeType, "utf-8", data);
    }

    private PageResult getResultContent(WebResourceRequest request) {
        PageResult pageResult = new PageResult();
        try {
            Uri url = request.getUrl();
            if (url == null){
                pageResult.data = errorPage("Invalid url: null", "?", "?");
                pageResult.mimeType = "text/html";
                return pageResult;
            }

            String scheme = request.getUrl().getScheme(); // can use to direct out
            if (scheme == null){
                pageResult.data = errorPage("Invalid url: "+request.getUrl().toString(), "?", "?");
                pageResult.mimeType = "text/html";
                return pageResult;

            } else if (Objects.equals(scheme, "app")) { // controller pages
                lastAppUrl = url.toString();
                pageResult.data = getControllerResponse(request);
                pageResult.mimeType = "text/html";
                return pageResult;

            } else if (scheme.equals("asset")) {
                String path = url.getHost()+url.getPath();
                pageResult.data = getRawAsset(path);
                pageResult.mimeType = guessMime(path);
                return pageResult;

            } else {
                pageResult.data = errorPage("Unknown url type: "+scheme, "?", "?");
                pageResult.mimeType = "text/html";
                return pageResult;
            }

        } catch (Exception ex) {
            pageResult.data = errorPage(ex.getMessage(), "?", "?");
            pageResult.mimeType = "text/html";
            return pageResult;
        }
    }

    private String guessMime(String path) {
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".js")) return "application/javascript";
        return "application/octet-stream";
    }

    private String getControllerResponse(WebResourceRequest request) {
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
            pageString = "<!doctype html><html>" +
                    "<head><link rel=\"stylesheet\" href=\"asset://styles/default.css\" type=\"text/css\"></head>" +
                    "<body>" +
                    response +
                    "<script type=\"text/javascript\">manager.homepageLoaded();</script>" +
                    "</body></html>";
        }
        return pageString;
    }

    private String errorPage(String message, String controller, String method) {
        return "<!doctype html><html><body>" +
                "<h1>Error</h1><h2>" + message + "</h2>" +
                "<p>A problem happened in the app. You can send a screenshot of this screen to the support team</p>" +
                "<p>Controller=" + controller+"; Method="+method+ ";</p>" +
                "<a href=\"app://home\">Back</a></body></html>";
    }

    private String getRawAsset(String path) {
        try {
            StringBuilder sb = new StringBuilder();

            InputStream is = assets.open(path);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));

            String readLine;

            try {
                while ((readLine = br.readLine()) != null) {
                    sb.append(readLine);
                }
                is.close();br.close();

            } catch (Exception e) {
                e.printStackTrace();
            }

            return sb.toString();
        } catch (Exception ex) {
            // todo: write a warning page, giving some details and a 'back'|'home' button
            System.out.println(ex.getLocalizedMessage());
            return null;
        }
    }

    // Return the last 'app' url that was requested, or null
    public String getLastRequest() {
        return lastAppUrl;
    }

    private class PageResult {
        public String data;
        public String mimeType;
    }
}