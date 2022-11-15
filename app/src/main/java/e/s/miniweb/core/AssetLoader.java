package e.s.miniweb.core;

import static e.s.miniweb.core.template.TemplateEngine.TryLoadFromHost;

import android.content.res.AssetManager;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;

public class AssetLoader {
    private static final String TAG = "AssetLoader";
    private final AssetManager apkLoader;

    public AssetLoader(AssetManager apkLoader){

        this.apkLoader = apkLoader;
    }

    /** Read a file either from the emulator host or from the APK */
    public Reader read(String fileName) throws IOException {
        if (TryLoadFromHost) {
            Log.i(TAG, "Try host load: assets/" + fileName);
            String result = EmulatorHostCall.queryHostForString("assets/" + fileName);
            if (!result.equals("")) {
                return new StringReader(result);
            }
        }

        // If the file didn't load, or hot-loading is off, load from APK
        InputStream is = apkLoader.open(fileName);
        return new InputStreamReader(is);
    }

    /** Read a file either from the emulator host or from the APK */
    public InputStream open(String path) throws IOException {
        if (TryLoadFromHost) {
            try {
                Log.i(TAG, "Try host load: assets/" + path);
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
