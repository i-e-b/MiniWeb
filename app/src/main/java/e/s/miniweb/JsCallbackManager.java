package e.s.miniweb;

import android.webkit.JavascriptInterface;

// Available to on-page JavaScript as 'manager'.
// This handles page script-to-android-app functions.
public class JsCallbackManager {
    private final MainActivity mainActivity;

    public JsCallbackManager(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    @JavascriptInterface
    public void homepageLoaded() {
        mainActivity.HomepageLoaded();
    }
}
