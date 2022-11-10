# MiniWeb
A small framework for building small android apps

## What is it?

A simple set of drivers for the built-in Android WebView that provides
a web-like development environment contained in a single app.

The app uses as much Android system features as possible to result
in a small final APK. The app plus example pages comes to under 30KB.

## How it works

The app is based around a set of self registering "controllers". These are classes
that register a set of web methods. Each method will return the path of a view
template and an optional object used to complete that template.

Note that the web client and app server and tightly coupled 1:1, so you can
get away with directly calling 'server' code from the client in a way you
can't with normal web development.

Urls for the app are in the form `app://{controller}/{method}?param=values`

The app will start in `app://home/index`, which is the same as `app://home`.

Templates should be in `assets/views`, and the file name should end in `.html`.
Each template is a fragment of HTML which will be wrapped in a \<body\> tag and
given a default style sheet.

The default style sheet is in `assets/styles/default.css`

## Calling JavaScript

JavaScript is enabled on the web view, and should work as normal.

It is possible to call directly from JavaScript to the host app. An object called `manager` is
available to all scripts. This exposes any methods in the `JsCallbackManager` class which
are decorated with the `@JavascriptInterface` annotation.

There are some limitations around the kind of data that can be passed to `manager`.
It sometimes needs an intermediary call in the page to serialise an object to send back to the app.
(see the 'Forms and parameters' example)

## Templating

The view templates are mostly plain HTML, with the ability to inject values from
the controller method.
Template holes can be put anywhere in a template -- text, tags, scripts, etc.

### Simple values

Values are copied from the model object into 'holes' in the html, which start with `{{` and end with `}}`.
For example, with a model containing `public String greeting = "hello";` and a template with
`<p>{{greeting}} world</p>`, then the final result is `<p>hello world</p>`

Note: the field name used with a template hole is case sensitive, and must be a field on the model,
not a function or method.

### Complex values

You can reach further into an object hierarchy with a dotted path. See the 'templating' demo page for
examples. It's generally better to restructure your model instead.

### Looping and Conditional output

You can use a pair of <code>`{{for:`model_field`}}`</code> and <code>`{{end:`model_field`}}`</code> to
enclose a block of HTML.

The `{{for:...}}` and `{{end:...}}` MUST be on their own line in the template file, with no
other text on the same line (whitespace is ok). If any non-whitespace characters are on the 
same line, the block will not be processed.

* If the `model_field` is a boolean value, the block will be displayed once, and only if the value is `true`
* If the `model_field` is a list or array, the block will repeat for each item
* For any other value, the block will be displayed once, and only if the value is not `null`

Inside a loop, you can use <code>`{{item:`field_name`}}`</code> to read values from each item.

You can loop or use a conditional block with a child item. For example:
```
{{for:people}}
    <p>Dear {{item:name}}, we are writing regarding account {{item:accountNumber}}...</p>
{{end:people}}
```

### Nesting loops

You can put a `{{for:...}}` loop inside another, but it must be for a different field.

You can loop over an item in a loop using `{{for:item:...}}`
```
{{for:accounts}}
    <p>{{item:userName}} has these items</p>
    <ol>
    {{for:item:basketItems}}
        <li>{{item:name}} x {{item:quantity}}</li>
    {{end:item:basketItems}}
    </ol>
{{end:accounts}}
```

## Hot Reload

Hot reload is not working yet, but should be coming soon(ish)

## Other bits

Test cert jks password: deploy.jks