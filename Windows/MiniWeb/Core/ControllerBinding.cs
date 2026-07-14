namespace MiniWeb.Core;

/// <summary>
/// Routing engine for controller methods
/// </summary>
public class ControllerBinding {
    private const string Glue = "=>";

    /**
     *  Bind a method name to a controller function.
     *  This makes it available on url "app://{controllerName}/{methodName}"
     * <p>
     *  This binding has no permissions requirement, and will be available regardless
     *  of session permissions.
     * </p>
     *
     * @param controllerName name of the controller class that contains the method
     * @param methodName name of the method, as exposed in the url
     * @param methodFunc the `WebMethod` function that runs for requests to the url
     */
    public void Method(ControllerBase src, string controllerName, string methodName, WebMethod methodFunc)
    {
        BindMethod(src, controllerName, methodName, methodFunc);
    }


    /**
     *  Bind a method name to a controller function.
     *  This makes it available on url "app://{controllerName}/{methodName}"
     * <p>
     *  This binding has no permissions requirement, and will be available regardless
     *  of session permissions.
     * </p>
     *
     * @param controllerName name of the controller class that contains the method
     * @param methodName name of the method, as exposed in the url
     * @param methodFunc the `WebMethod` function that runs for requests to the url
     */
    public static void BindMethod(ControllerBase src, string controllerName, string methodName, WebMethod methodFunc) {
        var composite = MakeKey(controllerName, methodName);
        if (Responders.TryAdd(composite, methodFunc))
        {
            Controllers.TryAdd(composite, src);
            return;
        }

        Console.WriteLine("Reused method, Ignored. c="+controllerName+"; m="+methodName);
    }

    /**
     *  Bind a method name to a controller function.
     *  This makes it available on url "app://{controllerName}/{methodName}"
     * <p>
     *  This binding includes requirements, and will only be available
     *  in sessions that have AT LEAST ONE of the listed permissions.
     * </p>
     *  If the permission set is null or empty, the method will be open to all sessions
     *
     * @param controllerName name of the controller class that contains the method
     * @param methodName name of the method, as exposed in the url
     * @param methodFunc the `WebMethod` function that runs for requests to the url
     * @param permissionSet a comma-separated list of permissions. User will require one or more of these.
     */
    public static void BindMethod(String controllerName, String methodName, WebMethod methodFunc, string? permissionSet) {
        var composite = MakeKey(controllerName, methodName);
        if (Responders.ContainsKey(composite)){
            Console.WriteLine("Reused method, Ignored. c="+controllerName+"; m="+methodName);
            return;
        }

        if (permissionSet != null) {
            var perms = permissionSet.Trim().Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries); // comma separated, trimming whitespace
            if (perms.Length > 0){
                Permissions[composite] = perms;
            }
        }

        Responders[composite] = methodFunc;
    }

    /** Reference a controller. The controller should call `BindMethod` in its constructor */
    public static void Use(ControllerBase o) {
        o.BindMethods(new ControllerBinding());
    }

    // TODO: move these to a core superclass

    #region internal bits

    /// <summary>
    /// Make a composite key for the controller and method names
    /// </summary>
    public static string MakeKey(string controllerName, string methodName){
        return controllerName + Glue + methodName;
    }

    /// <summary>
    /// Method for serving web requests
    /// </summary>
    public delegate TemplateResponse WebMethod(Host host, Dictionary<string, string> parameters, ResourceRequest request);

    // composite name => call-back; Composite is controller | method
    private static readonly Dictionary<string, WebMethod>      Responders  = new();
    // ReSharper disable once CollectionNeverQueried.Local
    private static readonly Dictionary<string, string[]>       Permissions = new();
    private static readonly Dictionary<string, ControllerBase> Controllers = new();

    /// <summary>
    /// Remove all bindings
    /// </summary>
    public static void ClearBindings() {
        Responders.Clear();
        Controllers.Clear();
        Permissions.Clear();
    }

    /// <summary>
    /// Check if the given controller method is registered
    /// </summary>
    /// <param name="compositeKey">Composite key from <see cref="MakeKey"/></param>
    public static bool HasMethod(string compositeKey) {
        return Responders.ContainsKey(compositeKey);
    }

    /// <summary>
    /// Get the controller method for the given key.
    /// Returns <c>null</c> if the key is not bound to a method.
    /// </summary>
    /// <param name="compositeKey">Composite key from <see cref="MakeKey"/></param>
    public static WebMethod? GetMethod(string compositeKey)
    {
        return Responders.GetValueOrDefault(compositeKey);
    }


    /// <summary>
    /// Get the controller instance for the given key.
    /// Returns <c>null</c> if the key is not bound to a controller.
    /// </summary>
    /// <param name="compositeKey">Composite key from <see cref="MakeKey"/></param>
    public static ControllerBase? GetController(string compositeKey)
    {
        return Controllers.GetValueOrDefault(compositeKey);
    }

    /// <summary>
    /// split url query parameters into a dictionary
    /// </summary>
    public static Dictionary<string, string> MapParams(string queryString) {
        Dictionary<string, string> result = new ();
        if (string.IsNullOrWhiteSpace(queryString)) return result;

        var parts = queryString.Split('&','?');
        foreach (var part in parts) {
            if (string.IsNullOrWhiteSpace(part)) continue;
            var sides = part.Split("=", 2);

            if (sides.Length == 1) result[sides[0]] = sides[0];
            else result[sides[0]] = sides[1];
        }
        return result;
    }
    #endregion
}