# MiniWeb
A small framework for building small android apps

## What is it?

A simple set of drivers for the built-in Android WebView that provides
a web-like development environment contained in a single app.

The app uses as much Android system features as possible to result
in a small final APK. The app plus example pages comes to around 30KB
when built in release mode.

## Roadmap

Things to do:

- [x] Add partial views, including by taking a model sub-item
- [ ] Global permissions object, with toggle flags in templates (also maybe in controllers)
- [ ] View export / tooling -- ideally so pages can be edited with browser inspector etc.

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

## Editing HTML

If you use Android Studio, some basic HTML editing tools are available.
You might want to use a different one of the IntelliJ suite of tools for better support.

As well as 'hot reload' described below, you can open a HTML page in your browser for basic design
using `View` > `Open in Browser` in the Android Studio main menu:

![View, Open in Browser](https://github.com/i-e-b/MiniWeb/raw/main/_docs/view-in-browser-android-studio.png)


## Emulator Host and Hot Reload

MiniWeb supports "hot-reload" of pages and assets when running under an emulator
(i.e. during development). You need to run the Emulator Host tool (`TinyWebHook`)
or a similar compatible service.

The Emulator Host tool **must** be running when your app first starts, otherwise
the hot-reload system will be disabled.

If that is working correctly, changes to files in the `assets` folder will be
monitored, and if any files being used by the current page (including linked 
resources) will cause the page to refresh with the updated resources.

Note: the controller is not called during hot-reload. The template model used
to render the page is kept to allow redraw without calling the controller.
This means that any external effects of your page (like reading from, *or* writing
to, a database) will not be repeated for a hot-reload.

The Android Emulator connects to the host over IP address `10.0.2.2`.
The MiniWeb class at `e.s.miniweb.core.EmulatorHostCall` has a few methods to make
HTTP calls to the host over this address (with very short connection timeout, so
that failures don't pause the app significantly).

If there is a HTTP server listening on port `1310`, MiniWeb will try to communicate
with it using a very simple protocol.

### TinyWebHook

There is a `TinyWebHook` dotnet app that supports the MiniWeb Hot-Host Protocol.
`TinyWebHook` also supports a `/` path for test use.

This must be run with administrator access under Windows, or with http binding
access on other systems (`root` should be ok for development).

Call `TinyWebHook` with the path to your Android app's `assets` folder.
(e.g. `C:\gits\MiniWeb\app\src\main\assets`)

### Hot-Host Protocol

#### Version

`http://10.0.2.2:1310/host`

This should return `ANDROID_EMU_HOST_V1`; If this path
fails or returns a different value, Hot Reload will **not** be active.
This is agreed in code at `e.s.miniweb.core.EmulatorHostCall#HOST_UP_MSG` and `TinyWebHook.Program.HostUpMsg`

#### Time

`http://10.0.2.2:1310/time`

This should return the current server time as **UTC**
in the format `yyyy-MM-dd HH:mm:ss`.

#### Assets

`http://10.0.2.2:1310/assets/{path to asset}`

This should return the file contents for the asset requested. The path is allowed
to be any path into the assets folder.
The path may **not** contain `..` or `.` elements.

* If the host returns a 200 status, the content will be used instead of the content in the APK of the running Android app.
* If the host returns a 404 status, the file will be treated as removed, regardless if its presence in the APK.
* If the host returns any other status, the file will be loaded from the APK as normal.

#### Last touch

`http://10.0.2.2:1310/touched/{path to asset}`

This should return the modified date of the file requested.
The Android app will request and store this when a file is loaded through the host, and
will periodically request again while the page is being displayed.

* If the host returns 200, and the date has **not** changed, the Android app takes no action
* If the host returns 200, and the date **has** changed, the Android app will try to reload the current page with existing data on the updated template
* If the host returns any other code, the Android app takes no action.

## Other bits

Test cert jks password: `deploy.jks`
Do **not** use this certificate for your app, it is public and should only be used for testing.
