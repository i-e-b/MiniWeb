package e.s.miniweb;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;

public class MainActivity extends Activity {
    private WebView view;
    private AppWebRouter client;
    private JsCallbackManager manager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setTitle("Loading..."); // temp message as soon as possible

        // setup the web view
        setContentView(R.layout.activity_main);
        view = (WebView) findViewById(R.id.main_web);

        // Do any heavy lifting out on a non-ui thread, so
        // the 'loading' message gets a chance to update
        new Thread(this::loadWebViewWithLocalClient).start();
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void loadWebViewWithLocalClient() {
        // We're off the ui thread, so can do any start-up processes here...
        /*try {
            Thread.sleep(5000); // fake loading
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/

        // hook the view to the app client and request the home page
        client = new AppWebRouter();
        manager = new JsCallbackManager(this);

        // Call out to the homepage
        runOnUiThread(()->{
            view.getSettings().setJavaScriptEnabled(true);
            view.setWebChromeClient(new BrowserEventListener(this));

            view.setWebViewClient(client);
            view.addJavascriptInterface(manager, "manager");
            // send the view to home page, with a special flag to say this is the first page since app start.
            view.loadUrl("app://home?init=true"); // we can play fast-and-loose with the url structure.
        });
    }

    // first render of the homepage
    public void HomepageLoaded() {
        runOnUiThread(() -> {
            // close the 'loading...' bar
            ActionBar titleBar = this.getActionBar();
            if (titleBar != null) this.getActionBar().hide();
        });
    }
}