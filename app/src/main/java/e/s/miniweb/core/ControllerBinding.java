package e.s.miniweb.core;

import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import e.s.miniweb.core.template.WebMethod;

public class ControllerBinding {
    private static final String TAG = "ControllerBinding";
    private static final String GLUE = "=>";

    /**
     *  Bind a method name to a controller function.
     *  This makes it available on url "app://{controllerName}/{methodName}"
     *
     *  This binding has no permissions requirement, and will be available regardless
     *  of session permissions.
     *
     * @param controllerName name of the controller class that contains the method
     * @param methodName name of the method, as exposed in the url
     * @param methodFunc the `WebMethod` function that runs for requests to the url
     */
    public static void BindMethod(String controllerName, String methodName, WebMethod methodFunc) {
        String composite = makeKey(controllerName, methodName);
        if (responders.containsKey(composite)){
            Log.w(TAG, "Reused method, Ignored. c="+controllerName+"; m="+methodName);
            return;
        }

        responders.put(composite, methodFunc);
    }

    /**
     *  Bind a method name to a controller function.
     *  This makes it available on url "app://{controllerName}/{methodName}"
     *
     *  This binding includes requirements, and will only be available
     *  in sessions that have AT LEAST ONE of the listed permissions.
     *
     *  If the permission set is null or empty, the method will be open to all sessions
     *
     * @param controllerName name of the controller class that contains the method
     * @param methodName name of the method, as exposed in the url
     * @param methodFunc the `WebMethod` function that runs for requests to the url
     * @param permissionSet a comma-separated list of permissions. User will require one or more of these.
     */
    public static void BindMethod(String controllerName, String methodName, WebMethod methodFunc, String permissionSet) {
        String composite = makeKey(controllerName, methodName);
        if (responders.containsKey(composite)){
            Log.w(TAG, "Reused method, Ignored. c="+controllerName+"; m="+methodName);
            return;
        }

        if (permissionSet != null) {
            String[] perms = permissionSet.trim().split("\\s*,\\s*"); // comma separated, trimming whitespace
            if (perms.length > 0){
                permissions.put(composite, perms);
            }
        }

        responders.put(composite, methodFunc);
    }

    /** Reference a controller. The controller should call `BindMethod` in its constructor */
    public static void Use(Object o) {
        // just keep a reference for the gc
        controllers.add(o);
    }

    // TODO: move these to a core superclass

    //region internal bits

    public static String makeKey(String controllerName, String methodName){
        return controllerName + GLUE + methodName;
    }

    // composite name => call-back; Composite is controller | method
    private static final Map<String, WebMethod> responders = new HashMap<>();
    private static final Map<String, String[]> permissions = new HashMap<>();

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static final Set<Object> controllers = new HashSet<>();

    public static void ClearBindings() {
        responders.clear();
        controllers.clear();
        permissions.clear();
    }

    public static boolean isPermitted(String compositeKey){
        if (!permissions.containsKey(compositeKey)) return true; // no restrictions
        String[] set = permissions.get(compositeKey);
        if (set == null) {
            Log.e(TAG, "Permissions for '"+compositeKey+"' were included, but set was null");
            return false; // something went wrong. Deny permission just in case
        }
        return Permissions.HasAnyPermissions(set);
    }

    public static boolean hasMethod(String compositeKey) {
        return responders.containsKey(compositeKey);
    }

    public static WebMethod getMethod(String compositeKey) {
        return responders.get(compositeKey);
    }
    //endregion
}
