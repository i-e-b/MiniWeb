package e.s.miniweb.core;

import static android.content.res.Configuration.UI_MODE_NIGHT_YES;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
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

import java.util.Set;

import e.s.miniweb.JsCallbackManager;
import e.s.miniweb.StartupActions;
import e.s.miniweb.core.hotReload.AssetLoader;
import e.s.miniweb.core.hotReload.EmulatorHostCall;
import e.s.miniweb.core.hotReload.HotReloadMonitor;

public class MainActivity extends Activity implements RouterControls {
    private static final String TAG = "MainActivity";
    private WebView webView;
    private AppWebRouter webRouter;
    private JsCallbackManager manager;
    private AssetLoader loader;
    private long lastPress; // controls double-tap-back-to-exit timing
    private boolean hasLoaded = false;

    // Hot reload
    private static final int hotReloadInterval = 1000; // 1 second
    private Handler backgroundHandler;

    /** Do start-up of the web-app
     * This is mostly hooking up the views, updating settings,
     * then calling the homepage */
    @SuppressLint("SetJavaScriptEnabled")
    public void loadWebViewWithLocalClient() {
        // We're off the ui thread, so can do any start-up processes here...
        Looper.prepare();

        // This can be called in two situations:
        // 1. The app is doing a fresh start. Everything will be null
        // 2. The app is doing a warm start. All our `static` objects will already exist
        boolean isWarmReload = HotReloadMonitor.CanReload();

        // hook the view to the app client and request the home page
        if (loader == null) loader = new AssetLoader(getAssets());
        if (webRouter == null) webRouter = new AppWebRouter(loader, this); // <-- route definitions are in here
        if (manager == null) manager = new JsCallbackManager(this); // <-- methods for js "manager.myFunc()" are in here

        // Hot-reload loop (with self terminate if not connected)
        if (backgroundHandler == null) {
            backgroundHandler = new Handler(Looper.myLooper());
            startHotReloadRepeater();
        }

        if (!isWarmReload) StartupActions.beforeHomepage();

        // Activate the web-view with event handlers, and kick off the landing page.
        runOnUiThread(()->{
            // setup the web view
            webView = new WebView(this);
            this.setContentView(webView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // remove anti-script safety settings
            webView.getSettings().setJavaScriptEnabled(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                webView.getSettings().setSafeBrowsingEnabled(false);
            }
            webView.getSettings().setBlockNetworkLoads(false);

            // bind handlers
            webView.setWebChromeClient(new BrowserEventListener(this));
            webView.setWebViewClient(webRouter);
            webView.addJavascriptInterface(manager, "manager");

            // Turn off caching
            webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);

            // this is either the first start since the app launched,
            // OR we have been restarted due to a system event
            // (such as switching dark/light mode)

            hasLoaded = false;
            if (HotReloadMonitor.CanReload()){ // we can resume our previous screen
                doHotReload();
                hideTitle();
            } else { // fresh start. Go to home page.
                webView.loadUrl("app://home");
            }
        });

        if (!isWarmReload) StartupActions.afterHomepage(this);

        Looper.loop(); // leave this thread running to do background jobs.
    }

    /** Handle back button.
    * If we're on the home page, we let Android deal with it.
    * Otherwise we send it down to the web view
     * */
    @Override
    public void onBackPressed() {
        // todo: replace this override with  Activity.getOnBackInvokedDispatcher()

        // try to go back a page
        if (!webRouter.goBack(webView)) {
            // nothing to go back to. Maybe exit app?
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
    public void pageLoaded() {
        if (!hasLoaded) {
            hasLoaded = true;
            hideTitle();
        }
    }

    /** Show a message as a small 'toast' alert (subtle) */
    public void showToast(String message) {
        runOnUiThread(()-> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    private void hideTitle(){
        runOnUiThread(() -> {
            try {
                ActionBar titleBar = this.getActionBar();
                if (titleBar != null) {
                    titleBar.hide();
                }
            } catch (Exception ex){
                Log.e(TAG, "Failed to hide title: "+ex);
            }
        });
    }

    private void setTitleColor(String color){
        if (color == null) return;
        try {
            ColorDrawable colorDrawable = new ColorDrawable(Color.parseColor(color)); // css-like color, e.g. "#0F9D58"
            ActionBar titleBar = this.getActionBar();
            titleBar.setBackgroundDrawable(colorDrawable);
        } catch(Exception ex) {
            Log.e(TAG, ex.toString());
        }
    }

    /** Show a message by displaying the Activity title (bold) */
    public void PopupTitle(String message, String color) {
        runOnUiThread(() -> {
            try {
                ActionBar titleBar = this.getActionBar();
                if (titleBar != null) {
                    setTitleColor(color);
                    titleBar.setTitle(message);
                    titleBar.show();
                }
            } catch (Exception ex){
                Log.e(TAG, "Failed to show title: "+ex);
            }
        });

        // close the message after a few seconds
        if (backgroundHandler != null) {
            backgroundHandler.postDelayed(this::hideTitle, 10000); // close after 10 seconds
        } else {
            Log.w(TAG, "backgroundHandler was unexpectedly missing");
            final Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(this::hideTitle, 10000); // close after 10 seconds
        }
    }

    /**
     * Kick-off the startup process. This sets some 'loading' indicators and
     * then starts the homepage on another thread.
     * */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // NOTE:
        // DO NOT add any more code to the onCreate method.
        // It is small and lightweight on purpose.
        // Do any start-up in the loadWebViewWithLocalClient() method.

        hasLoaded = false;
        this.setTitle("Loading..."); // temp message as soon as possible

        // Do any heavy lifting out on a non-ui thread, so
        // the 'loading' message gets a chance to update
        new Thread(this::loadWebViewWithLocalClient).start();

        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "main activity is stopped");
        stopHotReloadRepeater();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "main activity is resuming. Hot reload: "+HotReloadMonitor.CanReload());
        startHotReloadRepeater();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        Log.i(TAG, "main activity is destroyed");
        stopHotReloadRepeater();
        backgroundHandler.getLooper().quit(); // allow the background thread to stop.
        ControllerBinding.ClearBindings();
    }


    /** if true, we will check for emulator host, and stop running if not found */
    public boolean doFirstEmuHostCheck = true;
    /** Background task that checks for changes to assets used by the current page
     * DO NOT call this directly. Use `hotReloadHandler.post(HotReloadAssetChecker);`
     * to run in the correct thread. */
    private final Runnable HotReloadAssetChecker = new Runnable() {

        @Override
        public void run() { // this should always run on our background thread.

            if (doFirstEmuHostCheck){
                doFirstEmuHostCheck = false;
                Log.i(TAG, "Checking for hot-reload service");

                if (EmulatorHostCall.hostIsAvailable()){
                    Log.i(TAG, "Hot-reload service connected!");
                    HotReloadMonitor.TryLoadFromHost = true;
                    backgroundHandler.postDelayed(HotReloadAssetChecker, hotReloadInterval); // tick again
                } else {
                    Log.i(TAG, "Hot-reload service not found. Deactivating");
                    HotReloadMonitor.TryLoadFromHost = false;
                    return; // DO NOT continue pumping `hotReloadHandler`
                }
            }

            if (!HotReloadMonitor.TryLoadFromHost) return;

            // Do the hot-reload checks
            try {
                // Get list of assets that have been loaded since last navigation event
                Set<String> tmplPaths = HotReloadMonitor.GetHotReloadPaths();
                if (tmplPaths.isEmpty()) {
                    return;
                }

                boolean doReload = false;
                
                // For each asset that was requested by the current page (including the page itself)
                for (String tmplPath : tmplPaths) {
                    // Ask the emulator host what the last modified date was
                    String path = "touched/" + tmplPath;
                    String modifiedDate = EmulatorHostCall.queryHostForString(path);

                    // If the date has changed, we should reload the *page* (not just the asset)
                    if (HotReloadMonitor.HasAssetChanged(tmplPath, modifiedDate)) {
                        doReload = true;
                        break;
                    }
                }

                if (doReload) {
                    doHotReload();
                }
            } catch (Exception ex){
                Log.w(TAG, "error in emulator host loop: "+ex);
            } finally {
                backgroundHandler.postDelayed(HotReloadAssetChecker, hotReloadInterval); // tick again if awake
            }
        }
    };

    /** Reload the current view from existing data */
    private void doHotReload() {
        HotReloadMonitor.TryLoadFromHost = true; // make sure hot-load is switched on

        String controller = HotReloadMonitor.GetHotController();
        String method = HotReloadMonitor.GetHotMethod(); // ???
        String params = HotReloadMonitor.GetHotParams();

        // Make sure we're only re-rendering, and not calling the controller again.
        webRouter.ExpectHotReload(controller, method, params);

        String url = "app://"+controller+"/"+method+ (params==null? "" : "?"+params);

        runOnUiThread(() -> {
            webView.loadUrl(url); // Ask the web view to request and render the current page
        });
    }

    void startHotReloadRepeater() {
        Log.i(TAG, "Starting hot-reload looper");
        doFirstEmuHostCheck = true;
        if (backgroundHandler != null) backgroundHandler.postDelayed(HotReloadAssetChecker, hotReloadInterval);
    }

    void stopHotReloadRepeater() {
        HotReloadMonitor.TryLoadFromHost = false;
        if (backgroundHandler != null) backgroundHandler.removeCallbacks(HotReloadAssetChecker);
        Log.i(TAG, "Stopping hot-reload looper");
    }

    /** Ask that the 'back' history is cleared once the page is finished loading */
    public void clearHistory() {
        webRouter.clearHistory = true; // view.clearHistory() doesn't work, so we need this convoluted mess.
    }

    /**
     * Returns `true` if the device is currently set to dark mode.
     * Return `false` if the device is in light mode, or does not support modes.
     */
    public boolean inDarkMode() {
        try {
            int uiMode = getResources().getConfiguration().uiMode;
            return ((uiMode & UI_MODE_NIGHT_YES) > 0);
        } catch (Exception ex){

            return false;
        }
    }

    @Override
    public String getCurrentUrl() {
        return webRouter.getCurrentPage();
    }

    @Override
    public void setCurrentUrl(String url, boolean clearHistory) {
        webRouter.clearHistory = clearHistory;
        webView.loadUrl(url);
    }

    @Override
    public boolean hotReloadCurrentPage() {
        if (!HotReloadMonitor.CanReload()) return false;
        doHotReload();
        return true;
    }

    @Override
    public void coldReloadCurrentPage() {
        HotReloadMonitor.ClearReload();
        webView.reload();
    }
}