package e.s.miniweb.controllers;

import android.webkit.WebResourceRequest;

import java.util.Date;
import java.util.Map;

import e.s.miniweb.core.ControllerBase;
import e.s.miniweb.template.TemplateEngine;
import e.s.miniweb.template.TemplateResponse;

@SuppressWarnings("unused")
public class Home extends ControllerBase {
    /**
     * The constructor of your controller should bind all it's routes.
     * The constructor should be called in ControllerBindings::BindAllControllers()
     */
    public Home() {
        TemplateEngine.BindMethod("home", "index", this::index);
        TemplateEngine.BindMethod("home", "testOne", this::testOne);
        TemplateEngine.BindMethod("home", "testTwo", this::testTwo);
    }

    /**
     * "index" is the default route method if a url only gives a controller name.
     * "home/index" is the special landing page (feel free to use the url "app://home" anywhere)
     */
    public TemplateResponse index(Map<String, String> parameters, WebResourceRequest request) {
        Object model = new Object() {
            public final String an = "an";
            public final String multiple = "multiple";
            public final String s = "s";
            public final String line = "line";
            public final String controller = "Home";
        };

        return Page("home/index", model);
    }

    /**
     * Demo page. Delete as required
     * Demonstrates the 'get out of jail free card' that is the Android back button.
     */
    private TemplateResponse testOne(Map<String, String> parameters, WebResourceRequest request) {
        Object model = new Object() {
            public final String time = new Date().toString();
        };

        // There is no requirement to have views and controllers line up BUT it is a good idea.
        return Page("test/testOne", model);
    }

    /**
     * Demo page. Delete as required
     * Demonstrates how to do a redirect
     */
    private TemplateResponse testTwo(Map<String, String> parameters, WebResourceRequest request) {

        if (parameters.containsKey("first")) {
            // There is no requirement to have views and controllers line up BUT it is a good idea.
            return Page("test/testTwo", null);
        }

        return EndOfPath(); // redirect to home and clear 'back' history.
    }


}
