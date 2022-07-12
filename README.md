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

Urls for the app are in the form `app://{controller}/{method}?param=values`

The app will start in `app://home/index`.

Templates should be in `assets/views`, and the file name should end in `.html`.
Each template is a fragment of HTML which will be wrapped in a \<body\> tag and
given a default style sheet.

The default style sheet is in `assets/styles/default.css`

## Templating

The view templates are mostly plain HTML, with the ability to inject values from
the controller method.

### Simple values


