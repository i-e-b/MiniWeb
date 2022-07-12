package e.s.miniweb.controllers;

import android.webkit.WebResourceRequest;

import java.util.Map;

import e.s.miniweb.core.ControllerBase;
import e.s.miniweb.core.template.TemplateEngine;
import e.s.miniweb.core.template.TemplateResponse;

@SuppressWarnings("unused")
public class Home extends ControllerBase {
    /**
     * The constructor of your controller should bind all it's routes.
     * The constructor should be called in ControllerBindings::BindAllControllers()
     */
    public Home() {
        TemplateEngine.BindMethod("home", "index", this::index);
    }

    /**
     * "index" is the default route method if a url only gives a controller name.
     * "home/index" is the special landing page (feel free to use the url "app://home" anywhere)
     */
    public TemplateResponse index(Map<String, String> parameters, WebResourceRequest request) {
        // viewPath is required, but model is optional.
        return Page("home/index", null);
    }
}
