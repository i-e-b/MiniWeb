package e.s.miniweb;

import static android.content.res.Configuration.UI_MODE_NIGHT_YES;

import android.webkit.JavascriptInterface;

import e.s.miniweb.core.MainActivity;
import e.s.miniweb.core.hotReload.EmulatorHostCall;

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

    /**
     * example for extracting data from forms via JavaScript
     * @param formName storage key that uniquely identifies the data to be stored
     * @param values JSON string of values to be stored
     */
    @JavascriptInterface // this annotation MUST be added to any method you call from JavaScript
    public void storeForm(String formName, String values) {
        Statics.formData.put(formName, values);
    }


    /*
    The methods below are used to drive the UI, and should NOT be modified
     */

    //region Critical methods

    /**
     * Request data from emulation host, if running in an emulator
     * @param queryPath path & query string to send to host. Should not have leading '/'
     * @return data returned, if any, or null.
     */
    @JavascriptInterface
    public String queryHost(String queryPath){
        return EmulatorHostCall.queryHostForString(queryPath);
    }

    /**
     * Shows a message on the application title.
     * This is a large and prominent message.
     * Hidden after 10 seconds
     * @param message Message to display. Should be short.
     */
    @JavascriptInterface // this annotation MUST be added to any method you call from JavaScript
    public void showTitle(String message, String color) {
        mainActivity.PopupTitle(message, color);
    }

    /**
     * Removes the page history.
     * The 'back' button will not show pages that
     * were visited before this method was called.
     */
    @JavascriptInterface // this annotation MUST be added to any method you call from JavaScript
    public void clearHistory() {
        mainActivity.clearHistory();
    }

    /**
     * Returns `true` if the device is currently set to dark mode.
     * Return `false` if the device is in light mode, or does not support modes.
     */
    @JavascriptInterface // this annotation MUST be added to any method you call from JavaScript
    public boolean inDarkMode() {
        return mainActivity.inDarkMode();
    }

    //endregion

}
