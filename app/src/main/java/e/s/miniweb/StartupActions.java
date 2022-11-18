package e.s.miniweb;

import android.util.Log;

import e.s.miniweb.core.Permissions;
import e.s.miniweb.core.RouterControls;

/**
 * These methods are called as the app is starting up.
 * Modify them to suit your needs
 *
 * These methods will NOT be called during a warm-start,
 * e.g. when the app returns from the background.
 */
@SuppressWarnings("unused")
public class StartupActions {
    private static final String TAG = "StartupActions";

    /**
     * Do actions before the homepage is loaded.
     *
     * This will pause on the 'Loading...' screen
     * until the method returns.
     */
    public static void beforeHomepage(){

        // Demo of a way to do 'late loading' of user account.
        // Feel free to modify or remove for your app.
        Permissions.ClearPermissions();
        Permissions.AddPermission("no-permissions-loaded");
    }

    /**
     * Do actions after the homepage is loaded.
     *
     * This will be done in a background thread
     * and will not prevent the user from interacting
     * with the app, including changing page.
     */
    public static void afterHomepage(RouterControls controls){


        // Demo of a way to do 'late loading' of user account.
        // Feel free to modify or remove for your app.
        Permissions.ClearPermissions();
        Permissions.AddPermissions(new String[]{"perm1", "perm2"});
        if (!controls.hotReloadCurrentPage()){
            Log.w(TAG, "Failed to hot-reload");
        }
    }
}
