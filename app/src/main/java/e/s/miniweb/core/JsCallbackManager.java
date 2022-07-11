package e.s.miniweb.core;

import android.webkit.JavascriptInterface;

// Available to on-page JavaScript as 'manager'.
// This handles page script-to-android-app functions.
public class JsCallbackManager {
    private final MainActivity mainActivity;

    public JsCallbackManager(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    @JavascriptInterface // this annotation MUST be added to any method you call from JavaScript
    public void homepageLoaded() {
        mainActivity.HomepageLoaded();
    }

    @JavascriptInterface
    public void showTitle(String message) {
        mainActivity.PopupTitle(message);
    }

    @JavascriptInterface
    public void clearHistory() {
        mainActivity.clearHistory();
    }
}
