package e.s.miniweb.core;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;

import e.s.miniweb.BuildConfig;

public class AppCore extends Application implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "AppCore";
    @SuppressLint("StaticFieldLeak") // this has whole-application lifecycle anyway.
    public static AppCore Current;

    public Activity CurrentActivity;

    /** Run any very-early phase code */
    public AppCore() {
        Log.i(TAG, "Core app coming up");

        // https://wh0.github.io/2020/08/12/closeguard.html
        if(BuildConfig.DEBUG) {
            try {
                Class.forName("dalvik.system.CloseGuard")
                        .getMethod("setEnabled", boolean.class)
                        .invoke(null, true);
            } catch (ReflectiveOperationException e) {
                Log.e(TAG, "CloseGuard failed to start: "+e);
            }
        }

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build());

        Current = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        registerActivityLifecycleCallbacks(this); // add hooks to get activity data
    }

    @Override
    public void onTerminate() { // this is only for the emulator, and won't be called on real devices.
        super.onTerminate();
        unregisterActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityPreCreated(Activity activity, Bundle savedInstanceState) {
        ActivityLifecycleCallbacks.super.onActivityPreCreated(activity, savedInstanceState);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        Log.i(TAG, "Activity created: "+activity);
    }

    @Override
    public void onActivityPostCreated(Activity activity, Bundle savedInstanceState) {
        ActivityLifecycleCallbacks.super.onActivityPostCreated(activity, savedInstanceState);
    }

    @Override
    public void onActivityPreStarted(Activity activity) {
        ActivityLifecycleCallbacks.super.onActivityPreStarted(activity);
    }

    @Override
    public void onActivityStarted(Activity activity) {
        Log.i(TAG, "Activity started: "+activity);
    }

    @Override
    public void onActivityPostStarted(Activity activity) {
        ActivityLifecycleCallbacks.super.onActivityPostStarted(activity);
    }

    @Override
    public void onActivityPreResumed(Activity activity) {
        ActivityLifecycleCallbacks.super.onActivityPreResumed(activity);
    }

    @Override
    public void onActivityResumed(Activity activity) {
        Log.i(TAG, "Activity resumed: "+activity);
    }

    @Override
    public void onActivityPostResumed(Activity activity) {
        ActivityLifecycleCallbacks.super.onActivityPostResumed(activity);
    }

    @Override
    public void onActivityPrePaused(Activity activity) {
        ActivityLifecycleCallbacks.super.onActivityPrePaused(activity);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        Log.i(TAG, "Activity paused: "+activity);
    }

    @Override
    public void onActivityPostPaused(Activity activity) {
        ActivityLifecycleCallbacks.super.onActivityPostPaused(activity);
    }

    @Override
    public void onActivityPreStopped(Activity activity) {
        ActivityLifecycleCallbacks.super.onActivityPreStopped(activity);
    }

    @Override
    public void onActivityStopped(Activity activity) {
        Log.i(TAG, "Activity stopped: "+activity);
    }

    @Override
    public void onActivityPostStopped(Activity activity) {
        ActivityLifecycleCallbacks.super.onActivityPostStopped(activity);
    }

    @Override
    public void onActivityPreSaveInstanceState(Activity activity, Bundle outState) {
        ActivityLifecycleCallbacks.super.onActivityPreSaveInstanceState(activity, outState);
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        Log.i(TAG, "Activity save state: "+activity);
    }

    @Override
    public void onActivityPostSaveInstanceState(Activity activity, Bundle outState) {
        ActivityLifecycleCallbacks.super.onActivityPostSaveInstanceState(activity, outState);
    }

    @Override
    public void onActivityPreDestroyed(Activity activity) {
        ActivityLifecycleCallbacks.super.onActivityPreDestroyed(activity);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        Log.i(TAG, "Activity destroyed: "+activity);
    }

    @Override
    public void onActivityPostDestroyed(Activity activity) {
        ActivityLifecycleCallbacks.super.onActivityPostDestroyed(activity);
    }
}
