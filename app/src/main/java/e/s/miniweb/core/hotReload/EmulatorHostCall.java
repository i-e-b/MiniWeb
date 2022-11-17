package e.s.miniweb.core.hotReload;

import android.net.TrafficStats;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

import e.s.miniweb.R;
import e.s.miniweb.core.App;

/**
 * Utilities for connecting to a helper service when
 * this app is running on an emulator hosted by a PC
 */
public class EmulatorHostCall {
    /** Time allowed for checking if the host is connected. This should be reasonably short */
    private static final int WAKE_TIME_MS = 500;

    /** Time allowed for contacting the emulator host. This should be short */
    private static final int CONNECT_TIME_MS = 100;

    /** Time allowed for transferring data from emulator host */
    private static final int READ_TIME_MS = 2500;

    // This is where we expect to find the host tool running
    private static final String HOST_BASE = "http://10.0.2.2:1310/";

    // Status strings we expect from the host
    private static final String HOST_UP_MSG = "ANDROID_EMU_HOST_V1";
    private static final String TAG = "EmulatorHostCall";

    /**
     * Test if Emulator host is available.
     * This CAN NOT be run on the main UI thread.
     * @return true if host responded. False otherwise
     */
    public static boolean hostIsAvailable(){
        // This just shuts up a weird Android system warning
        TrafficStats.setThreadStatsTag(512);

        try {
            URL url = new URL(HOST_BASE + "host");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                // Set short time-out. The host server should respond in a few ms.
                conn.setConnectTimeout(WAKE_TIME_MS);
                conn.setReadTimeout(WAKE_TIME_MS);

                InputStream is = conn.getInputStream();

                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line = br.readLine();

                is.close();
                isr.close();
                br.close();

                return Objects.equals(line, HOST_UP_MSG);
            } finally {
                conn.disconnect();
            }
        } catch (android.os.NetworkOnMainThreadException nex) {
            Log.e(TAG, App.str(R.string.err_host_call_blocked));
            return false;
        } catch (Exception e){
            return false;
        }
    }

    /**
     * Send a 'GET' request to the host with a given path.
     * Returns the server result, or empty
     * @param path path and query to send to host. Does NOT need leading '/'
     */
    public static String queryHostForString(String path){
        if (!HotReloadMonitor.TryLoadFromHost) return "";
        // This just shuts up a weird Android system warning
        TrafficStats.setThreadStatsTag(512);

        try {
            URL url = new URL(HOST_BASE + (path.startsWith("/") ? path.substring(1) : path));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setConnectTimeout(CONNECT_TIME_MS); // short connection time-out
                conn.setReadTimeout(READ_TIME_MS);

                InputStream is = conn.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String readLine;

                // While the BufferedReader readLine is not null
                while ((readLine = br.readLine()) != null) {
                    sb.append(readLine); // add it to the template
                    sb.append("\r\n"); // add line break back in
                }

                is.close();
                br.close();
                return sb.toString();
            } finally {
                conn.disconnect();
            }
        } catch (Exception e){
            Log.i(TAG, App.str(R.string.err_host_str_query));
            return "";
        }
    }

    /**
     * Send a 'GET' request to the host with a given path.
     * Returns the server result, or empty
     * @param path path and query to send to host. Does NOT need leading '/'
     */
    public static InputStream queryHostForData(String path){
        if (!HotReloadMonitor.TryLoadFromHost) return null;
        // This just shuts up a weird Android system warning
        TrafficStats.setThreadStatsTag(512);

        try {
            // we read data to a buffer, then return a stream-reader for that,
            // so that we don't hit time-out issues.
            URL url = new URL(HOST_BASE + (path.startsWith("/") ? path.substring(1) : path));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setConnectTimeout(CONNECT_TIME_MS); // short connection time-out
                conn.setReadTimeout(READ_TIME_MS);

                InputStream is = conn.getInputStream();

                // Creating an object of ByteArrayOutputStream class
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                try {
                    int temp;
                    while ((temp = is.read(buffer)) != -1) {
                        byteArrayOutputStream.write(buffer, 0, temp);
                    }
                } catch (Exception e) {
                    Log.w(TAG, e.toString());
                }
                is.close();

                byte[] byteArray = byteArrayOutputStream.toByteArray();
                return new ByteArrayInputStream(byteArray);
            } finally {
                conn.disconnect();
            }
        } catch (Exception e){
            Log.i(TAG, App.str(R.string.err_host_data_query));
            return null;
        }
    }
}
