package e.s.miniweb.controllers;

import android.webkit.WebResourceRequest;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import e.s.miniweb.core.ControllerBase;
import e.s.miniweb.models.FullNameModel;
import e.s.miniweb.models.NestedObjectModel;
import e.s.miniweb.core.template.TemplateEngine;
import e.s.miniweb.core.template.TemplateResponse;
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
        String controller = "test";
        TemplateEngine.BindMethod(controller, "testOne", this::testOne);
        TemplateEngine.BindMethod(controller, "testTwo", this::testTwo);
        TemplateEngine.BindMethod(controller, "model-paths", this::modelPaths);
        TemplateEngine.BindMethod(controller, "bad-input", this::badInput);
        TemplateEngine.BindMethod(controller, "bad-method", this::badMethod);
        TemplateEngine.BindMethod(controller, "paramsAndForms", this::paramsAndForms);
        TemplateEngine.BindMethod(controller, "paramsAndForms2", this::paramsAndForms2);
        TemplateEngine.BindMethod(controller, "paramsAndForms3", this::paramsAndForms3);
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
     * Demo page with loads of templating examples
     */
    private TemplateResponse modelPaths(Map<String, String> params, WebResourceRequest request) {
        List<ExampleObject> list = new ArrayList<>();
        list.add(new ExampleObject("hello"));
        list.add(new ExampleObject("this"));
        list.add(new ExampleObject("is"));
        list.add(new ExampleObject("a"));
        list.add(new ExampleObject("list"));

        List<NestedObjectModel> nested = new ArrayList<>();
        nested.add(new NestedObjectModel("First child"));
        nested.add(new NestedObjectModel("Second child"));

        Map<String, NestedObjectModel> sampleMapNest = new HashMap<>();
        sampleMapNest.put("myKey", new NestedObjectModel("deep"));

        Object model = new Object() {
            public final String an = "an";
            public final String multiple = "multiple";
            public final String s = "s";
            public final String line = "line";
            public final String controller = "Home";
            public final boolean falseValue = false;
            public final boolean trueValue = true;
            public final Object nullValue = null;
            public final NestedObjectModel pathPoints = new NestedObjectModel("This value comes from a dotted path");
            public final List<NestedObjectModel> nestedRepeat = nested;
            public final List<ExampleObject> listThing = list;
            public final Map<String, NestedObjectModel> mapNest = sampleMapNest;
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
        public final String exampleField;

        public ExampleObject(String msg) {
            exampleField = msg;
        }
    }
}
