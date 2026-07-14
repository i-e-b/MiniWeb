using MiniWeb.Controllers;
using MiniWeb.Core;

namespace MiniWeb;

/// <summary>
/// Controller bindings for the app.
/// <p>
/// Controllers do page selection and app logic for a set of related pages.
/// Most of your app code should be in the controllers.
/// </p>
/// You MUST add each controller class here, or it will not be available in the app.
/// </summary>
public static class ControllerBindings
{
    /// <summary>
    /// Add bindings for each of the app's controllers.
    /// <p>
    /// You MUST add a line below once for each controller in the app.
    /// </p>
    /// </summary>
    public static void BindAllControllers(){
        ControllerBinding.Use(new Home());
        // Add more bindings here
    }

    /*
     * Note: this method-call based binding is used for compatibility with
     * the Java version of this host code.
     */
}