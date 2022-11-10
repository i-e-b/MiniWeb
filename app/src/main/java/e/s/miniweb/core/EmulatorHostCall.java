package e.s.miniweb.core;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

/**
 * Utilities for connecting to a helper service when
 * this app is running on an emulator hosted by a PC
 */
public class EmulatorHostCall {
    // This is where we expect to find the host tool running
    private static final String HOST_BASE = "http://10.0.2.2:1310/";

    // Status strings we expect from the host
    private static final String HOST_UP_MSG = "ANDROID_EMU_HOST";
    private static final String HOST_NOT_FOUND_MSG = "NOT_FOUND";
    private static final String HOST_ERROR_MSG = "HOST_ERROR";

    private static final String TAG = "EmulatorHostCall";

    /**
     * Test if Emulator host is available.
     * @return true if host responded. False otherwise
     */
    public static boolean hostIsAvailable(){
        try {
            URL url = new URL(HOST_BASE+"host");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                // Set short time-out. The host server should respond in a few ms.
                conn.setConnectTimeout(30);
                conn.setReadTimeout(30);

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                return Objects.equals(br.readLine(), HOST_UP_MSG);
            } finally {
                conn.disconnect();
            }
        } catch (Exception e){
            return false;
        }
    }

    /**
     * Send a 'GET' request to the host with a given path.
     * Returns the server result, or empty
     * @param path path and query to send to host. Does NOT need leading '/'
     */
    public static String queryHost(String path){
        try {
            URL url = new URL(HOST_BASE + (path.startsWith("/") ? path.substring(1) : path));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setConnectTimeout(30); // short connection time-out
                conn.setReadTimeout(1000);

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String readLine;

                // While the BufferedReader readLine is not null
                while ((readLine = br.readLine()) != null) {
                    sb.append(readLine); // add it to the template
                }

                return sb.toString();
            } finally {
                conn.disconnect();
            }
        } catch (Exception e){
            Log.e(TAG, e.toString());
            return "";
        }
    }
}
