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

-keep public class e.s.miniweb.core.MainActivity

# Keep names in controllers to stop reflection being broken.
-keepclassmembernames class e.s.miniweb.controllers.** { *; }
-keepnames class e.s.miniweb.controllers.** { *; }
-keep class e.s.miniweb.controllers.** { *; }
-keepclassmembernames public class e.s.miniweb.controllers.** { *; }
-keepnames public class e.s.miniweb.controllers.** { *; }
-keep public class e.s.miniweb.controllers.** { *; }

# Keep all model names
-keepclassmembernames class e.s.miniweb.models.** { *; }
-keepnames class e.s.miniweb.models.** { *; }
-keep class e.s.miniweb.models.** { *; }
-keepclassmembernames public class e.s.miniweb.models.** { *; }
-keepnames public class e.s.miniweb.models.** { *; }
-keep public class e.s.miniweb.models.** { *; }

# Keep names of things called model
-keepclassmembernames class **Model { *; }
-keepnames class **Model { *; }
-keep class **Model { *; }
-keepclassmembernames public class **Model { *; }
-keepnames public class **Model { *; }
-keep public class **Model { *; }

# Keep names in anonymous types
-keepclassmembernames class this** { *; }
-keepnames class this** { *; }
-keepclassmembernames public class this** { *; }
-keepnames public class this** { *; }

-keepclassmembernames class *$* { *; }
-keepclassmembernames public class *$* { *; }

# keep all annotations
-keepattributes *Annotation*

-keepclassmembers class ** {
    public void on*(**);
}

# repack obfuscated classes into single package
-repackageclasses ''
-allowaccessmodification

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile