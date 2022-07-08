package e.s.miniweb;

import android.webkit.WebChromeClient;
import android.webkit.WebView;

// This class lets you capture browser events, like progress or
// Window targeting.
public class BrowserEventListener extends WebChromeClient {
    private final MainActivity mainActivity;

    public BrowserEventListener(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        System.out.println("progress = " + newProgress);
    }
/*
    @Override
    public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
        mainActivity.showToast(message);
        System.out.println("alert = " + message);
        result.confirm(); // need to do this, or the page freezes
        return true; // means don't show the default alert
    }
 */
}
