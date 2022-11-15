package e.s.miniweb.core.hotReload;

import android.content.res.AssetManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

/** Loads files from either the APK's assets, or the emulator host */
public class AssetLoader {
    private static final String TAG = "AssetLoader";
    private final AssetManager apkLoader;

    public AssetLoader(AssetManager apkLoader){

        this.apkLoader = apkLoader;
    }

    /** Read a file either from the emulator host or from the APK */
    public Reader read(String fileName) throws IOException {
        // If the file didn't load, or hot-loading is off, load from APK
        InputStream is = open(fileName);
        return new InputStreamReader(is);
    }

    /** Read a file either from the emulator host or from the APK */
    public InputStream open(String path) throws IOException {
        if (HotReloadMonitor.TryLoadFromHost) {
            try {
                InputStream is = EmulatorHostCall.queryHostForData("assets/" + path);
                if (is != null) return is;
            } catch (Exception e){
                Log.w(TAG, "Load from host failed. Trying APK; error="+e);
            }
        }

        // If the file didn't load, or hot-loading is off, load from APK
        return apkLoader.open(path);
    }
}
