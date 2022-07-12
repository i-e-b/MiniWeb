package e.s.miniweb.controllers;

import android.webkit.WebResourceRequest;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import e.s.miniweb.core.ControllerBase;
import e.s.miniweb.template.TemplateEngine;
import e.s.miniweb.template.TemplateResponse;
@SuppressWarnings("unused")

/*
    This controller is used to give some various examples.
    Feel free to delete when building your app.
 */
public class TestController extends ControllerBase {
    /**
     * The constructor of your controller should bind all it's routes.
     * The constructor should be called in ControllerBindings::BindAllControllers()
     */
    public TestController() {
        TemplateEngine.BindMethod("test", "testOne", this::testOne);
        TemplateEngine.BindMethod("test", "testTwo", this::testTwo);
        TemplateEngine.BindMethod("test", "model-paths", this::modelPaths);
        TemplateEngine.BindMethod("test", "bad-input", this::badInput);
    }

    private TemplateResponse badInput(Map<String, String> params, WebResourceRequest request) {
        return Page("test/badInput", new Object());
    }

    /**
     * Demo page with loads of templating examples
     */
    private TemplateResponse modelPaths(Map<String, String> params, WebResourceRequest request) {
        List<ExampleObject> list = new ArrayList<>();
        list.add(new ExampleObject("hello"));
        list.add(new ExampleObject("this"));
        list.add(new ExampleObject("is"));
        list.add(new ExampleObject("a"));
        list.add(new ExampleObject("list"));

        List<NestObject> nested = new ArrayList<>();
        nested.add(new NestObject("First child"));
        nested.add(new NestObject("Second child"));

        Map<String, NestObject> sampleMapNest = new HashMap<>();
        sampleMapNest.put("myKey", new NestObject("deep"));

        Object model = new Object() {
            public final String an = "an";
            public final String multiple = "multiple";
            public final String s = "s";
            public final String line = "line";
            public final String controller = "Home";
            public final boolean falseValue = false;
            public final boolean trueValue = true;
            public final Object nullValue = null;
            public final NestObject pathPoints = new NestObject("This value comes from a dotted path");
            public final List<NestObject> nestedRepeat = nested;
            public final List<ExampleObject> listThing = list;
            public final Map<String, NestObject> mapNest = sampleMapNest;
        };

        return Page("test/modelPaths", model);
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



    // Internal classes used for view models
    // you can use either complete public classes, nested classes, or inline anonymous types
    // to drive the template engine

    private static class ExampleObject {
        public String exampleField;

        public ExampleObject(String msg) {
            exampleField = msg;
        }
    }

    private static class NestObject {
        public List<String> children;
        public String name;

        public NestObject(String name){
            this.name = name;

            // shove in some sample data
            children = new ArrayList<>();
            children.add("one");
            children.add("two");
            children.add("three");
            children.add("four");
        }
    }
}
