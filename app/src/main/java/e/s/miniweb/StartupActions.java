package e.s.miniweb;

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

    /**
     * Do actions before the homepage is loaded.
     *
     * This will pause on the 'Loading...' screen
     * until the method returns.
     */
    public static void beforeHomepage(){

        // Add 'demo' permissions. Remove for your app.
        Permissions.AddPermissions(new String[]{"perm1", "perm2"});
    }

    /**
     * Do actions after the homepage is loaded.
     *
     * This will be done in a background thread
     * and will not prevent the user from interacting
     * with the app, including changing page.
     */
    public static void afterHomepage(RouterControls controls){}
}
