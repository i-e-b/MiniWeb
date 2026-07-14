package e.s.miniweb.core;

import android.app.Application;
import android.content.res.Resources;
import android.os.Build;
import android.os.StrictMode;
import android.os.strictmode.Violation;
import android.util.Log;

import e.s.miniweb.BuildConfig;

@SuppressWarnings("ALL")
public class App extends Application {
    private static final String TAG = "AppCore";
    private static Resources resources;

    /** Run any very-early phase code */
    public App() {
        Log.i(TAG, "Core app coming up");

        // If built in debug mode, enable "strict" resource monitoring.
        if (BuildConfig.DEBUG) {
            try {
                // https://wh0.github.io/2020/08/12/closeguard.html
                Class.forName("dalvik.system.CloseGuard")
                        .getMethod("setEnabled", boolean.class)
                        .invoke(null, true);
            } catch (Exception e) {
                Log.e(TAG, "CloseGuard failed to start: "+e);
            }

            // Use a smarter filtered report if we can:
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                        .detectAll()
                        .penaltyListener(Runnable::run, this::logV)
                        .build());
            } else {
                StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                        .detectAll()
                        .penaltyLog()
                        .build());
            }
        }
    }

    /** get a string by resource id */
    public static String str(int id) {
        return resources.getString(id);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        resources = getResources();
    }

    private void logV(Violation v) {
        StackTraceElement[] stack = v.fillInStackTrace().getStackTrace();

        int frameCount = 0;
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement e : stack) {
            if (e.isNativeMethod()) continue;
            String className = e.getClassName();

            // exclude platform lines, and this reporter.
            if (className.startsWith("dalvik.system.")) continue;
            if (className.startsWith("java.lang.")) continue;
            if (className.startsWith("sun.nio.")) continue;
            if (className.startsWith("android.os.")) continue;
            if (className.startsWith("e.s.miniweb.core.App")) continue;

            frameCount++;
            sb.append(e.getClassName());
            sb.append('.');
            sb.append(e.getMethodName());
            sb.append("; ");
        }

        if (frameCount > 0) {
            Log.w(TAG, "Strict mode violation happened in your code. Please trace and fix.");
            Log.w(TAG, "Strict mode VIOLATION: " + v + "\r\n" + sb);
        } else {
            Log.i(TAG, "Android system leaked a resource.");
        }
    }
}
