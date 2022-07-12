# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# We use reflected names for templating, so avoid any renaming.
#-dontobfuscate

-optimizations *
-optimizationpasses 5
-overloadaggressively

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
-keepclassmembers class e.s.miniweb.JsCallbackManager { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep names in controllers to stop reflection being broken.
-keep public class e.s.miniweb.core.MainActivity

-keepclassmembernames class e.s.miniweb.controllers.** { *; }
-keepnames class e.s.miniweb.controllers.** { *; }
-keep public class e.s.miniweb.controllers.** { *; }


-keepclassmembernames class e.s.miniweb.models.** { *; }
-keepnames class e.s.miniweb.models.** { *; }
-keep public class e.s.miniweb.models.** { *; }

-keepclassmembernames class **Model { *; }
-keepnames class **Model { *; }
-keep public class **Model { *; }
-keepattributes *Annotation*

-keepclassmembers class ** {
    public void on*(**);
}

# repack obfuscated classes into single package so it would be hard to find their originall package
-repackageclasses ''
-allowaccessmodification

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile