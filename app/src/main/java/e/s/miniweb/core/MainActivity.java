package e.s.miniweb.core;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Toast;

import e.s.miniweb.ControllerBindings;
import e.s.miniweb.controllers.Home;
import e.s.miniweb.template.TemplateEngine;

public class MainActivity extends Activity {
    private WebView view;
    private AppWebRouter client;
    private JsCallbackManager manager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setTitle("Loading..."); // temp message as soon as possible

        // setup the web view
        view = new WebView(this);
        this.setContentView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

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
            view.loadUrl("app://home/init?first=true"); // we can play fast-and-loose with the url structure.
        });
    }

    // first render of the homepage
    public void HomepageLoaded() {hideTitle();}

    // Show a message as a small 'toast' alert (subtle)
    public void showToast(String message) {
        Toast.makeText(getApplicationContext(),message,Toast.LENGTH_LONG).show();
    }

    private void hideTitle(){
        runOnUiThread(() -> {
            ActionBar titleBar = this.getActionBar();
            if (titleBar != null) {
                titleBar.hide();
            }
        });
    }

    // Show a message by displaying the Activity title (bold)
    public void PopupTitle(String message) {
        runOnUiThread(() -> {
            ActionBar titleBar = this.getActionBar();
            if (titleBar != null) {
                titleBar.setTitle(message);
                titleBar.show();
            }
        });

        // close the message after a few seconds
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(this::hideTitle, 10000); // close after 10 seconds

    }
}