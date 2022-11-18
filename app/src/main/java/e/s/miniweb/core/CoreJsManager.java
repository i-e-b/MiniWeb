package e.s.miniweb.core;

import android.webkit.JavascriptInterface;

import e.s.miniweb.core.hotReload.EmulatorHostCall;

/**
 * JavaScript bridge functions critical to the app. Do not modify.
 */
public class CoreJsManager {

    /*****************************************************************************************************
     *****************************************************************************************************
     The methods below are used to drive the UI, and should NOT be modified
     *****************************************************************************************************
     *****************************************************************************************************/



    //region Critical methods

    private final MainActivity mainActivity;

    public CoreJsManager(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }
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
