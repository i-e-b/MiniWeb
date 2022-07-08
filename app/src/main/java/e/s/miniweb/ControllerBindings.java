package e.s.miniweb;

import e.s.miniweb.controllers.Home;
import e.s.miniweb.template.TemplateEngine;

public class ControllerBindings {
    // You MUST add a line below once for each controller in the app.
    // Otherwise Java has no idea it exists. Oh for Assembly.GetTypes().
    public static void BindAllControllers(){
        TemplateEngine.Use(new Home());
    }

}
