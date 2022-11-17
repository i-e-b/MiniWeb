package e.s.miniweb.core;

import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import e.s.miniweb.core.template.WebMethod;

public class ControllerBinding {
    private static final String TAG = "ControllerBinding";

    // composite name => call-back; Composite is controller | method
    private static final Map<String, WebMethod> responders = new HashMap<>();
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private static final Set<Object> controllers = new HashSet<>();


    //region Controller binding
    public static void BindMethod(String controllerName, String methodName, WebMethod methodFunc) {
        String composite = controllerName+"|="+methodName;
        if (responders.containsKey(composite)){
            Log.w(TAG, "Reused method, Ignored. c="+controllerName+"; m="+methodName);
            return;
        }

        responders.put(composite, methodFunc);
    }

    public static void Use(Object o) {
        // just keep a reference for the gc
        controllers.add(o);
    }

    public static void ClearBindings() {
        responders.clear();
        controllers.clear();
    }

    public static boolean hasMethod(String compositeKey) {
        return responders.containsKey(compositeKey);
    }

    public static WebMethod getMethod(String compositeKey) {
        return responders.get(compositeKey);
    }
    //endregion
}
