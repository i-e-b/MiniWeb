package e.s.miniweb.controllers;

import android.webkit.WebResourceRequest;

import java.util.Map;

import e.s.miniweb.core.ControllerBase;
import e.s.miniweb.core.ControllerBinding;
import e.s.miniweb.core.template.TemplateResponse;

/*
    This controller provides the app://home path
    The app://home/index method is where your app will start.

    You could put log-in screens here etc.

    There is no requirement to name controller classes
    the same as the url routing name, but it can be
    confusing if you don't.

    It's not required to `extends ControllerBase`,
    but it is helpful.
 */

@SuppressWarnings("unused")
public class Home extends ControllerBase {
    /**
     * The constructor of your controller should bind all it's routes.
     * The constructor should be called in ControllerBindings::BindAllControllers()
     */
    public Home() {
        String controller = "home";
        ControllerBinding.BindMethod(controller, "index", this::index);
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
