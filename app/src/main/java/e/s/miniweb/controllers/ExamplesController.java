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
        ControllerBinding.BindMethod(controller, "templating-examples", this::templatingExamples);
        ControllerBinding.BindMethod(controller, "partial-views", this::partialViews);
        ControllerBinding.BindMethod(controller, "permission-visibility", this::permissionVisibility);
        ControllerBinding.BindMethod(controller, "not-permitted", this::notPermitted, "not-a-real-permission,also-not-real");
        ControllerBinding.BindMethod(controller, "is-permitted", this::isPermitted, "perm1, perm2");

        // partials
        ControllerBinding.BindMethod(controller, "url-view", this::urlPartialView);
        ControllerBinding.BindMethod(controller, "element-view", this::urlPartialElementView);
    }

    private TemplateResponse isPermitted(Map<String, String> params, WebResourceRequest request) {
        return Page("examples/permitted-ok", null);
    }

    // this controller-action will only run if the user has at least one of these permissions:
    private TemplateResponse notPermitted(Map<String, String> params, WebResourceRequest request) {
        return Page("examples/permitted-ok", null);
    }

    private TemplateResponse urlPartialElementView(Map<String, String> params, WebResourceRequest request) {
        return Page("examples/element-view", params);
    }

    private TemplateResponse urlPartialView(Map<String, String> params, WebResourceRequest request) {
        Object model = new Object() {
            public final String text = params.containsKey("text") ? params.get("text") : null;
            public final String text2 = params.containsKey("text2") ? params.get("text2") : null;
        };

        return Page("examples/a-sub-view", model);
    }


    private TemplateResponse partialViews(Map<String, String> params, WebResourceRequest request) {

        List<ExamplesController.ExampleObject> list = new ArrayList<>();
        list.add(new ExamplesController.ExampleObject("hello"));
        list.add(new ExamplesController.ExampleObject("this"));
        list.add(new ExamplesController.ExampleObject("is"));
        list.add(new ExamplesController.ExampleObject("a"));
        list.add(new ExamplesController.ExampleObject("list"));

        Object subViewObject = new Object(){
            public final String text = "This is 'text' from a child item on the parent model";
        };

        Object model = new Object() {
            public final String text = "This is 'text' from the parent model";
            public final List<ExamplesController.ExampleObject> listOfThings = list;
            public final Object subViewData = subViewObject;
        };

        return Page("examples/partial-views", model);
    }

    private TemplateResponse permissionVisibility(Map<String, String> params, WebResourceRequest request) {
        return Page("examples/permission-visibility", null);
    }

    /**
     * Demo page with loads of templating examples
     */
    private TemplateResponse templatingExamples(Map<String, String> params, WebResourceRequest request) {
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

        return Page("examples/templating-examples", model);
    }

    // Internal classes used for view models
    // you can use either complete public classes, nested classes, or inline anonymous types
    // to drive the template engine

    private static class ExampleObject {
        public final String exampleField;
        public final String text;

        public ExampleObject(String msg) {
            exampleField = msg;
            text = msg;
        }
    }
}
