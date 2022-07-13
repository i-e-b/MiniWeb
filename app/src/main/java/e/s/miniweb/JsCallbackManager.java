package e.s.miniweb;

import android.webkit.JavascriptInterface;

import e.s.miniweb.core.MainActivity;

// Available to on-page JavaScript as 'manager'.
// This handles page script-to-android-app functions.
public class JsCallbackManager {
    private final MainActivity mainActivity;

    public JsCallbackManager(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    /*
        Add your own methods here.
     */

    // example for extracting data from forms via JavaScript
    @JavascriptInterface // this annotation MUST be added to any method you call from JavaScript
    public void storeForm(String formName, String values) {
        Statics.formData.put(formName, values);
    }


    /*
    These methods are used to drive the UI, and should not be modified
     */

    //region Critical methods
    @JavascriptInterface // this annotation MUST be added to any method you call from JavaScript
    public void homepageLoaded() {
        mainActivity.HomepageLoaded();
    }

    @JavascriptInterface // this annotation MUST be added to any method you call from JavaScript
    public void showTitle(String message) {
        mainActivity.PopupTitle(message);
    }

    @JavascriptInterface // this annotation MUST be added to any method you call from JavaScript
    public void clearHistory() {
        mainActivity.clearHistory();
    }
    //endregion

}
