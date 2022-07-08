package e.s.miniweb.core;

import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import e.s.miniweb.ControllerBindings;
import e.s.miniweb.template.TemplateEngine;
import e.s.miniweb.template.TemplateResponse;

public class AppWebRouter extends WebViewClient {
    final private TemplateEngine template;

    public AppWebRouter(){
        template = new TemplateEngine();

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
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        // Return null when the web view should get the page from the real internet.
        // If we return a proper WebResourceResponse, the web view will render that.
        // We could check for a https://*.mysite.com url for going outside etc.

        String isApp = request.getUrl().getScheme();
        String controller = request.getUrl().getHost();
        String method = request.getUrl().getPath().substring(1);
        String params = request.getUrl().getQuery();

        TemplateResponse response = template.Run(controller, method, params, request);
        System.out.println(response.TemplateName);

        // spit out a generic page for any request.
        String pageString = "<html>" +
                "<body><h1>Router</h1>" +
                "<p>This is a sample response from the router. With all sorts of stuff.</p>" +
                "<dl>" +
                "<dt>target type</dt><dd>"+isApp+"</dd>"+
                "<dt>controller name</dt><dd>"+controller+"</dd>"+
                "<dt>method name</dt><dd>"+method+"</dd>"+
                "<dt>parameters</dt><dd>"+params+"</dd>"+
                "</dl>" +
                "<ul>" +
                "<li><a href=\"app://first/method?p1=x&p2=y\">Test one</a></li>"+
                "<li><a href=\"app://second/otherMethod?p1=a&p2=b\">Test two</a></li>"+
                "<li><a href=\"app://home\">Home</a></li>"+
                "<li><a href=\"javascript:manager.showTitle('Bold message')\">Run Android code</a></li>"+
                "<li><a href=\"javascript:alert('hello')\">Show alert as toast message</a></li>"+
                // fill up some space
                "<li></li><li></li><li></li><li></li><li></li><li></li><li></li><li></li><li></li><li></li><li></li><li></li><li></li><li></li><li></li><li></li><li></li><li></li>" +
                "</ul>" +
                "<script type=\"text/javascript\">manager.homepageLoaded();</script>"+ // signal we're ready
                "</body></html>";

        /*try {
            Thread.sleep(5000); // fake loading
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/

        InputStream data = new ByteArrayInputStream(pageString.getBytes());
        return new WebResourceResponse("text/html", "utf-8", data);
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        System.out.println(error.toString());
    }
}