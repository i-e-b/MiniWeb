package e.s.miniweb.core;

import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
    private static final String HOST_UP_MSG = "ANDROID_EMU_HOST_V1";
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
    public static String queryHostForString(String path){
        try {
            URL url = new URL(HOST_BASE + (path.startsWith("/") ? path.substring(1) : path));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setConnectTimeout(30); // short connection time-out
                conn.setReadTimeout(1000);

                InputStream is = conn.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String readLine;

                // While the BufferedReader readLine is not null
                while ((readLine = br.readLine()) != null) {
                    sb.append(readLine); // add it to the template
                }

                is.close();
                br.close();
                return sb.toString();
            } finally {
                conn.disconnect();
            }
        } catch (Exception e){
            Log.i(TAG, "failure in query host (string). Probably not connected.");
            return "";
        }
    }
    /**
     * Send a 'GET' request to the host with a given path.
     * Returns the server result, or empty
     * @param path path and query to send to host. Does NOT need leading '/'
     */
    public static InputStream queryHostForData(String path){
        try {
            // we read data to a buffer, then return a stream-reader for that,
            // so that we don't hit time-out issues.
            URL url = new URL(HOST_BASE + (path.startsWith("/") ? path.substring(1) : path));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                conn.setConnectTimeout(30); // short connection time-out
                conn.setReadTimeout(1000);

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
            Log.i(TAG, "failure in query host (data). Probably not connected.");
            return null;
        }
    }
}
