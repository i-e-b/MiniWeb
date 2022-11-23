package e.s.miniweb.core.hotReload;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import e.s.miniweb.R;
import e.s.miniweb.core.App;
import e.s.miniweb.core.template.TemplateEngine;
import e.s.miniweb.core.template.TemplateResponse;

public class HotReloadMonitor {
    private static final String TAG = "HotReloadMonitor";

    // These must all be static to enable warm-reload
    private static final Map<String, TemplateResponse> hotReloadAssets = new HashMap<>();
    public static TemplateResponse lastPageRendered = null;
    public static boolean TryLoadFromHost = false; // part of the hot-load system.

    //region Hot-reload
    /** If true, the last rendered page is available for hot-reload */
    public static Set<String> GetHotReloadPaths() {
        return hotReloadAssets.keySet();
    }


    /** Returns true if view data is available to do a hot reload */
    public static boolean CanReload() {
        return lastPageRendered != null && !hotReloadAssets.isEmpty();
    }

    /** Add the path of an asset to the list of paths that should trigger a hot-reload */
    public static void AddHotReloadAsset(String path) {
        if (path == null){
            Log.w(TAG, "AddHotReloadAsset was given an empty path");
            return;
        }
        if (hotReloadAssets.containsKey(path)) return;

        hotReloadAssets.put(path, new TemplateResponse());
    }

    /** Add the path of an template page to the list of paths that should trigger a hot-reload */
    public static void AddHotReloadPage(TemplateResponse tmpl) {
        if (tmpl == null){
            Log.w(TAG, "AddHotReloadPage was given an empty template");
            return;
        }

        String key = App.str(R.string.path_views) + tmpl.TemplatePath + ".html";
        // see also: e.s.miniweb.core.template.TemplateEngine#getDocTemplate

        if (hotReloadAssets.containsKey(key)) return;

        hotReloadAssets.put(key, tmpl);
    }

    /** If true, the last rendered page is available for hot-reload */
    public static boolean HasAssetChanged(String filePath, String changeDate) {
        if (!hotReloadAssets.containsKey(filePath)) {
            Log.w(TAG, "Unexpected hot reload query: "+filePath);
            return false; // not a path we recognise
        }
        if (Objects.equals(changeDate, "")){
            return false; // probably lost connection to emulator host
        }

        TemplateResponse target = hotReloadAssets.get(filePath);
        if (target == null){Log.e(TAG, "Null reference in hot reload query: "+filePath);return false;}


        if (target.LastPageChangeDate == null){ // haven't captured original date
            target.LastPageChangeDate = changeDate; // save for next go around
            return false;
        }
        if (target.LastPageChangeDate.equals(changeDate)) return false; // file not changed

        target.LastPageChangeDate = changeDate; // update the date for next time
        return true; // yes, this asset has changed
    }

    public static String GetHotController(){
        return (lastPageRendered == null) ? "" : lastPageRendered.Controller;
    }

    public static String GetHotMethod(){
        return (lastPageRendered == null) ? "" : lastPageRendered.Method;
    }

    public static String GetHotParams(){
        return (lastPageRendered == null) ? "" : lastPageRendered.Params;
    }

    /** Clear any previous hot-reload state */
    public static void ClearReload(){
        hotReloadAssets.clear();
        lastPageRendered = null;
    }

    /** Just do the render phase of `Run` */
    public static String RunHotReload(TemplateEngine template) {
        try {
            template.copyLinesToTemplate(App.str(R.string.path_views) + lastPageRendered.TemplatePath + ".html", lastPageRendered);
            return template.transformTemplate(lastPageRendered, null);
        } catch (Exception ex){
            Log.e(TAG, "Hot reload failed: "+ex);
            return null; // will cause a normal load
        }
    }
    //endregion
}
