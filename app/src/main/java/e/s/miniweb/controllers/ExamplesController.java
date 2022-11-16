package e.s.miniweb.controllers;

import android.webkit.WebResourceRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import e.s.miniweb.core.ControllerBase;
import e.s.miniweb.core.ControllerBinding;
import e.s.miniweb.core.template.TemplateResponse;
import e.s.miniweb.models.NestedObjectModel;
@SuppressWarnings("unused")

public class ExamplesController extends ControllerBase {
    public ExamplesController(){
        String controller = "examples";

        // pages
        ControllerBinding.BindMethod(controller, "model-paths", this::modelPaths);
        ControllerBinding.BindMethod(controller, "partial-views", this::partialViews);
        ControllerBinding.BindMethod(controller, "permission-visibility", this::permissionVisibility);

        // partials
        ControllerBinding.BindMethod(controller, "url-view", this::urlPartialView);
    }

    private TemplateResponse urlPartialView(Map<String, String> params, WebResourceRequest request) {
        Object model = null;
        if (params.containsKey("text")) {
            model = new Object() {
                public final String text = params.get("text");
            };
        }

        return Page("examples/a-sub-view", model);
    }


    private TemplateResponse partialViews(Map<String, String> params, WebResourceRequest request) {
        return Page("examples/partial-views", null);
    }

    private TemplateResponse permissionVisibility(Map<String, String> params, WebResourceRequest request) {
        return Page("examples/permission-visibility", null);
    }

    /**
     * Demo page with loads of templating examples
     */
    private TemplateResponse modelPaths(Map<String, String> params, WebResourceRequest request) {
        List<ExamplesController.ExampleObject> list = new ArrayList<>();
        list.add(new ExamplesController.ExampleObject("hello"));
        list.add(new ExamplesController.ExampleObject("this"));
        list.add(new ExamplesController.ExampleObject("is"));
        list.add(new ExamplesController.ExampleObject("a"));
        list.add(new ExamplesController.ExampleObject("list"));

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
            public final List<ExamplesController.ExampleObject> listThing = list;
            public final Map<String, NestedObjectModel> mapNest = sampleMapNest;
        };

        return Page("examples/modelPaths", model);
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
