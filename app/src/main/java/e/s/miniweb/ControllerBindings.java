package e.s.miniweb;

import e.s.miniweb.controllers.*;
import e.s.miniweb.core.ControllerBinding;

/**
 * Controller bindings for the app.
 *
 * Controllers do page selection and app logic for a set of related pages.
 * Most of your app code should be in the controllers.
 *
 * You MUST add each controller class here, or it
 * will not be available in the app.
 */
public class ControllerBindings {

    /**
     * Add bindings for each of the app's controllers.
     *
     * You MUST add a line below once for each controller in the app.
     */
    public static void BindAllControllers(){
        ControllerBinding.Use(new Home());
        ControllerBinding.Use(new TestController());
        ControllerBinding.Use(new ExamplesController());
    }

}
