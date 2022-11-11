package e.s.miniweb.core;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.File;

import e.s.miniweb.JsCallbackManager;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private WebView view;
    private AppWebRouter client;
    private JsCallbackManager manager;
    private long lastPress; // controls double-back-to-exit timing
    private boolean hasLoaded = false;

    /** Do start-up of the web-app
     * This is mostly hooking up the views, updating settings,
     * then calling the homepage */
    @SuppressLint("SetJavaScriptEnabled")
    public void loadWebViewWithLocalClient() {
        // We're off the ui thread, so can do any start-up processes here...

        // hook the view to the app client and request the home page
        client = new AppWebRouter(getAssets()); // <-- route definitions are in here
        manager = new JsCallbackManager(this); // <-- methods for js "manager.myFunc()" are in here

        // Activate the web-view with event handlers, and kick off the landing page.
        runOnUiThread(()->{
            // remove anti-script safety settings
            view.getSettings().setJavaScriptEnabled(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                view.getSettings().setSafeBrowsingEnabled(false);
            }
            view.getSettings().setBlockNetworkLoads(false);

            // bind handlers
            view.setWebChromeClient(new BrowserEventListener(this));
            view.setWebViewClient(client);
            view.addJavascriptInterface(manager, "manager");

            // Turn off caching
            view.getSettings().setAppCacheEnabled(false);
            view.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);

            // send the view to home page, with a special flag to say this is the first page since app start.
            view.loadUrl("app://home"); // we can play fast-and-loose with the url structure.
        });
    }

    /** Handle back button.
    * If we're on the home page, we let Android deal with it.
    * Otherwise we send it down to the web view
     * */
    @Override
    public void onBackPressed() {
        if (view.canGoBack()) { // have history. Go back
            view.goBack();
        } else { // No browser history...
            long pressTime = System.currentTimeMillis();

            if (Math.abs(pressTime - lastPress) > 2500){ // last press was more that 2.5 seconds ago. Ignore
                Toast.makeText(getApplicationContext(), "Press again to leave", Toast.LENGTH_SHORT).show();
                lastPress = pressTime;
            } else { // last press was within 2.5 seconds. Pass to activity (usually does a soft exit)
                super.onBackPressed();
            }
        }
    }

    /** Called when the homepage is loaded.
     * On the first render of the homepage, we hide 'Loading...' messages */
    public void HomepageLoaded() {
        if (!hasLoaded) {
            hasLoaded = true;
            hideTitle();
        }
    }

    /** Show a message as a small 'toast' alert (subtle) */
    public void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    private void hideTitle(){
        runOnUiThread(() -> {
            ActionBar titleBar = this.getActionBar();
            if (titleBar != null) {
                titleBar.hide();
            }
        });
    }

    private void setTitleColor(String color){
        if (color == null) return;
        try {
            ActionBar titleBar = this.getActionBar();
            ColorDrawable colorDrawable = new ColorDrawable(Color.parseColor(color)); // css-like color, e.g. "#0F9D58"
            titleBar.setBackgroundDrawable(colorDrawable);
        } catch(Exception ex) {
            Log.e(TAG, ex.toString());
        }
    }

    /** Show a message by displaying the Activity title (bold) */
    public void PopupTitle(String message, String color) {
        runOnUiThread(() -> {
            ActionBar titleBar = this.getActionBar();
            if (titleBar != null) {
                setTitleColor(color);
                titleBar.setTitle(message);
                titleBar.show();
            }
        });

        // close the message after a few seconds
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(this::hideTitle, 10000); // close after 10 seconds

    }

    /**
     * Kick-off the startup process. This sets some 'loading' indicators and
     * then starts the homepage on another thread.
     * */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // NOTE:
        // DO NOT add any more code to the onCreate method.
        // It is small and lightweight on purpose.
        // Do any start-up in the loadWebViewWithLocalClient() method.

        hasLoaded = false;

        this.setTitle("Loading..."); // temp message as soon as possible

        // setup the web view
        view = new WebView(this);
        this.setContentView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Do any heavy lifting out on a non-ui thread, so
        // the 'loading' message gets a chance to update
        new Thread(this::loadWebViewWithLocalClient).start();
    }

    /**
     * The target audience are on constrained devices, and
     * we generally favor small size over best speed.
     * When exiting, we try to clean up as much Android
     * cache junk as possible.
     */
    @Override
    protected void onStop() {
        super.onStop();
        cleanAllCacheFiles();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        cleanAllCacheFiles();
    }

    private void cleanAllCacheFiles() {
        try {
            Log.i(TAG, "Shutdown-cleanup starting");
            boolean ok1 = deleteDir(this.getExternalCacheDir());
            boolean ok2 = deleteDir(this.getCacheDir());
            boolean ok3 = deleteDir(this.getCodeCacheDir());
            Log.i(TAG, "Shutdown-cleanup complete: "+ok1+", "+ok2+", "+ok3);
        } catch (Exception e) {
            Log.e(TAG, "Failed to do shut-down clean-up "+ e);
        }
    }

    /** Recursively delete a directory */
    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children == null) return true;
            for (String child : children) {
                boolean success = deleteDir(new File(dir, child));
                if (!success) {
                    return false;
                }
            }
            return dir.delete();
        } else if(dir!= null && dir.isFile()) {
            return dir.delete();
        } else {
            return false; // not a dir or file?
        }
    }

    /** Ask that the 'back' history is cleared once the page is finished loading */
    public void clearHistory() {
        client.clearHistory = true; // view.clearHistory() doesn't work, so we need this convoluted mess.
    }
}