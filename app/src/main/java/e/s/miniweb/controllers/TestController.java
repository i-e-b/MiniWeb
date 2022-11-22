package e.s.miniweb.controllers;

import android.webkit.WebResourceRequest;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import e.s.miniweb.JsCallbackManager;
import e.s.miniweb.core.ControllerBase;
import e.s.miniweb.core.ControllerBinding;
import e.s.miniweb.core.hotReload.EmulatorHostCall;
import e.s.miniweb.core.hotReload.HotReloadMonitor;
import e.s.miniweb.core.template.TemplateResponse;
import e.s.miniweb.models.FullNameModel;
@SuppressWarnings("unused")

/*
    This controller is used to give some various examples.
    Feel free to delete when building your app.

    There is no requirement to name controller classes
    the same as the url routing name, but it can be
    confusing if you don't.

    It's not required to `extends ControllerBase`,
    but it is helpful.
 */
public class TestController extends ControllerBase {
    /**
     * The constructor of your controller should bind all it's routes.
     * The constructor should be called in ControllerBindings::BindAllControllers()
     */
    public TestController() {
        String controller = "test";

        // pages
        ControllerBinding.BindMethod(controller, "testOne", this::testOne);
        ControllerBinding.BindMethod(controller, "testTwo", this::testTwo);
        ControllerBinding.BindMethod(controller, "bad-input", this::badInput);
        ControllerBinding.BindMethod(controller, "bad-method", this::badMethod);
        ControllerBinding.BindMethod(controller, "paramsAndForms", this::paramsAndForms);
        ControllerBinding.BindMethod(controller, "paramsAndForms2", this::paramsAndForms2);
        ControllerBinding.BindMethod(controller, "paramsAndForms3", this::paramsAndForms3);
        ControllerBinding.BindMethod(controller, "paramsAndForms4", this::paramsAndForms4);
        ControllerBinding.BindMethod(controller, "emoji", this::emoji);
        ControllerBinding.BindMethod(controller, "svg-embed", this::svgEmbed);
        ControllerBinding.BindMethod(controller, "emuHost", this::emulatorAndHostTests);
        ControllerBinding.BindMethod(controller, "increment", this::incrementPage);
        ControllerBinding.BindMethod(controller, "memInfo", this::memInfo);

        // partials
        ControllerBinding.BindMethod(controller, "badge-africa", this::badgeAfrica);
    }

    private String lastName = "";
    private String lastSurname = "";

    /**
     * In this method we handle both the GET and the (fake) POST. You
     * can split them by setting an 'action' on the html form tag.
     */
    private TemplateResponse paramsAndForms(Map<String, String> params, WebResourceRequest request) {
        if (params.containsKey("isPostback")) { // got form values

            // Note that because we are a 1 user site, we can use class fields, unlike traditional web servers.
            lastName = params.get("name");
            lastSurname = params.get("surname");

            return Redirect("app://test/paramsAndForms3");

        } else { // initial page display
            FullNameModel model = new FullNameModel();
            model.Name = params.containsKey("name") ? params.get("name") : "";
            model.Surname = params.containsKey("surname") ? params.get("surname") : "";

            return Page("test/paramsAndForms1", model);
        }
    }

    // this method only expects the post-back from a form
    private TemplateResponse paramsAndForms2(Map<String, String> data, WebResourceRequest request) {
        lastName = data.get("name");
        lastSurname = data.get("surname");
        return Redirect("app://test/paramsAndForms3");
    }

    // Shows the result from the form
    private TemplateResponse paramsAndForms3(Map<String, String> params, WebResourceRequest request) {
        Object model = new Object(){
            public final String Name = lastName;
            public final String Surname = lastSurname;
        };
        return Page("test/paramsAndForms3", model);
    }

    // this one gets the data from JavaScript
    private TemplateResponse paramsAndForms4(Map<String, String> parameters, WebResourceRequest request) {
        Object model = new Object(){
            public final String Name = JsCallbackManager.formData.get("demoForm");
        };
        return Page("test/paramsAndForms3", model);
    }


    private TemplateResponse memInfo(Map<String, String> params, WebResourceRequest request) {
        final Runtime runtime = Runtime.getRuntime();
        final long usedMemInMB=(runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
        final long maxHeapSizeInMB=runtime.maxMemory() / 1048576L;
        final long availHeapSizeInMB = maxHeapSizeInMB - usedMemInMB;

        Object model = new Object(){
            public final long usedMem = usedMemInMB;
            public final long maxHeapSize = maxHeapSizeInMB;
            public final long availHeapSize = availHeapSizeInMB;
        };
        return Page("test/memInfo", model);
    }

    private static int loadCount = 0;
    /**
     * Display a page that proves hot-load is working
     */
    private TemplateResponse incrementPage(Map<String, String> params, WebResourceRequest request) {

        loadCount++;

        Object model = new Object(){
            public final String LoadCount = ""+loadCount;
        };

        return Page("test/loadCountView", model);
    }

    /**
     * Display a page showing emulator-host stats
     */
    private TemplateResponse emulatorAndHostTests(Map<String, String> params, WebResourceRequest request) {
        Object model = new Object(){
            public final String IsConnected = EmulatorHostCall.hostIsAvailable() ? "connected" : "not available";
            public final String HotReloadRunning = HotReloadMonitor.TryLoadFromHost ? "running" : "off";
            public final String HostTime = EmulatorHostCall.queryHostForString("time");
            public final String SelfTime = getIsoDateNow();
            public final boolean hotReloadOn = HotReloadMonitor.TryLoadFromHost;
        };

        return Page("test/emuHost", model);
    }

    private static String getIsoDateNow(){
        Date date = new Date();
        SimpleDateFormat sdf;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault(Locale.Category.FORMAT));
        } else {
            sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        }
        return sdf.format(date);
    }

    /**
     * Display a reference page full of emoji supported by Android
     */
    private TemplateResponse emoji(Map<String, String> params, WebResourceRequest request) {
        return Page("test/emoji", null);
    }


    /** Show a globe, with a shield, and 'value' from params in that shield. */
    private TemplateResponse badgeAfrica(Map<String, String> params, WebResourceRequest request) {
        return Page("test/badge-africa", params);
    }

    /**
     * Display a page that loads an `img` tag with SVG source
     */
    private TemplateResponse svgEmbed(Map<String, String> params, WebResourceRequest request) {
        return Page("test/svg-embed", null);
    }

    /**
     * Demonstrate what happens when a web method crashes
     */
    private TemplateResponse badMethod(Map<String, String> parameter, WebResourceRequest request) throws Exception {
        throw new Exception("This is an example crash!");
    }

    private TemplateResponse badInput(Map<String, String> params, WebResourceRequest request) {
        return Page("test/badInput", new Object());
    }


    /**
     * Demo page. Delete as required
     * Demonstrates the 'get out of jail free card' that is the Android back button.
     */
    private TemplateResponse testOne(Map<String, String> parameters, WebResourceRequest request) {
        Object model = new Object() {
            public final String time = new Date().toString();
        };

        // There is no requirement to have views and controllers line up BUT it is a good idea.
        return Page("test/testOne", model);
    }

    /**
     * Demo page. Delete as required
     * Demonstrates how to do a redirect
     */
    private TemplateResponse testTwo(Map<String, String> parameters, WebResourceRequest request) {

        if (parameters.containsKey("first")) {
            // There is no requirement to have views and controllers line up BUT it is a good idea.
            return Page("test/testTwo", null);
        }

        return EndOfPath(); // redirect to home and clear 'back' history.
    }



}
