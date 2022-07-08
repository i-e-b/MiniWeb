package e.s.miniweb.template;

import android.webkit.WebResourceRequest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TemplateEngine {
    // composite name => call-back; Composite is controller | method
    private static final Map<String, WebMethod> responders = new HashMap<String, WebMethod>();
    private static final Set<Object> controllers = new HashSet<Object>();

    public TemplateEngine() {
        // Note: Is Java's reflection is so crap we can't pre-load class info in
        // a meaningful way? So we wait until we get a call, and cache from there?
    }

    public static void BindMethod(String controllerName, String methodName, WebMethod methodFunc) {
        String composite = controllerName+"|="+methodName;
        if (responders.containsKey(composite)){
            System.out.println("Reused method. Ignored.");
            return;
        }

        responders.put(composite, methodFunc);
    }

    public static void Use(Object o) {
        // just keep a reference for the gc
        controllers.add(o);
    }

    // todo: should give a plain string and do the template filling here
    public TemplateResponse Run(String controller, String method, String params, WebResourceRequest request) {
        String composite = controller+"|="+method;

        if ( ! responders.containsKey(composite)){
            System.out.println("Unknown web method!");
            return null;
        }

        WebMethod wm = responders.get(composite);
        return wm.gen(null, request);
    }
}