package e.s.miniweb;

import android.webkit.JavascriptInterface;

import java.util.HashMap;
import java.util.Map;

import e.s.miniweb.core.CoreJsManager;
import e.s.miniweb.core.MainActivity;

/**
 * Manager for JavaScript calls.
 *
 * The methods in this class annotated with @JavascriptInterface
 * will be available as functions in the global `manager` object
 * exposed to all page scripts.
 *
 * You MUST add the @JavascriptInterface annotation to each method
 * otherwise they won't be exposed to JavaScript.
 */
public class JsCallbackManager extends CoreJsManager {
    public JsCallbackManager(MainActivity mainActivity) {super(mainActivity);}

    /*
        Add your own methods here.
     */


    /** supports the 'storeForm' method. Feel free to remove for your app */
    public static final Map<String, String> formData = new HashMap<>();

    /**
     * Example for extracting data from forms via JavaScript.
     * Look in "assets/views/test/paramsAndForms1.html" for its use.
     *
     * Feel free to remove or modify this for your app.
     *
     * @param formName storage key that uniquely identifies the data to be stored
     * @param values JSON string of values to be stored
     */
    @JavascriptInterface // this annotation MUST be added to any method you call from JavaScript
    public void storeForm(String formName, String values) {
        formData.put(formName, values);
    }

}
