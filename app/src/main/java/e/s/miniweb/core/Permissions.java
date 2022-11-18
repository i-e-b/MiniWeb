package e.s.miniweb.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Holder for current user permissions
 *
 * Your app should call whatever services are required to populate
 * and update this list as required.
 *
 * Permissions for the session are static, so if a user backgrounds
 * the app, the same permissions are held when the app is resumed.
 *
 * Permissions are strings, which are matched exactly when
 * gating access.
 *
 * Note that permissions should be quite fine-grained to work
 * well with view and control access. You might want to get
 * coarser 'roles' from log-in, and expand to permissions here.
 */
@SuppressWarnings("unused")
public class Permissions {
    private static final Set<String> permissions = new HashSet<>();

    /**
     * Remove all permissions from current session.
     * This should be done when a user logs in or out,
     * or when re-synchronising permissions from an
     * external source.
     *
     * Any content that does not require permissions
     * will still be accessible after this call.
     */
    public static void ClearPermissions(){
        permissions.clear();
    }

    /**
     * Add a single permission to the current session.
     * If the permission is already granted, this call will have no effect
     * @param permission the permission string
     */
    public static void AddPermission(String permission){
        if (permission == null || permission.isEmpty()) return;
        permissions.add(permission);
    }

    /**
     * Add multiple permissions to the current session.
     * If permissions are duplicated or already granted, the user will keep the single permission.
     * @param permissionSet a set of permissions to add.
     */
    public static void AddPermissions(String[] permissionSet){
        if (permissionSet == null) return;
        permissions.addAll(Arrays.asList(permissionSet));
    }

    /**
     * Returns true if the current session is granted the requested permission.
     * False if the session is not granted this permission.
     * If an invalid permission is passed, this returns false.
     * @param permission the permission to check
     * @return true if permission granted
     */
    public static boolean HasPermission(String permission){
        if (permission == null) return false;
        return permissions.contains(permission);
    }

    /**
     * Return true if ANY of the supplied permissions are granted.
     * Returns false only if NONE of the supplied permissions are granted
     * If an empty set is supplied, this returns true (no permission required)
     * @param permissionSet permissions to check
     * @return true if any granted
     */
    public static boolean HasAnyPermissions(String[] permissionSet){
        if (permissionSet == null) return true;
        for (String perm: permissionSet){
            if (permissions.contains(perm)) return true;
        }
        return false;
    }
}
