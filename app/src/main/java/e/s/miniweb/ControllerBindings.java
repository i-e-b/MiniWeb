package e.s.miniweb;

import e.s.miniweb.controllers.*;
import e.s.miniweb.core.ControllerBinding;

public class ControllerBindings {
    // You MUST add a line below once for each controller in the app.
    // Otherwise Java has no idea it exists.
    public static void BindAllControllers(){
        ControllerBinding.Use(new Home());
        ControllerBinding.Use(new TestController());
        ControllerBinding.Use(new ExamplesController());
    }

}
