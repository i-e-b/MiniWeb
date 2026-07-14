package e.s.miniweb.core;

import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.HashMap;
import java.util.Map;

/**
 * Manager for JavaScript calls.
 * <p>
 * The methods in this class annotated with @JavascriptInterface
 * will be available as functions in the global `manager` object
 * exposed to all page scripts.
 * <p>
 * You MUST add the @JavascriptInterface annotation to each method
 * otherwise they won't be exposed to JavaScript.
 */
public class JsCallbackManager extends CoreJsManager {
    private static final String TAG = "JsCallbackManager";
    private final Host host;

    public JsCallbackManager(MainActivity mainActivity, Host host) {
        super(mainActivity);
        this.host = host;
    }

    public static PageInteractions target = null;

    /**
     * Called from web pages to trigger controller actions
     */
    @JavascriptInterface // this annotation MUST be added to any method you call from JavaScript
    public void postMessage(String jsonString) {
        if (target != null) {
            try {
                var message = (JSONObject) new JSONTokener(jsonString).nextValue();
                target.HandlePageRequest(host, message);
            } catch (JSONException e) {
                Log.e(TAG, "Invalid JSON object in call to `PageRequest` -> `postMessage`", e);
            }
        } else Log.w(TAG, "Page called `PageRequest`, but there was no registered PageInteractions target.");
    }

}
