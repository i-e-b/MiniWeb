package e.s.miniweb;

import e.s.miniweb.controllers.*;
import e.s.miniweb.core.template.TemplateEngine;

public class ControllerBindings {
    // You MUST add a line below once for each controller in the app.
    // Otherwise Java has no idea it exists.
    public static void BindAllControllers(){
        TemplateEngine.Use(new Home());
        TemplateEngine.Use(new TestController());
    }

}
