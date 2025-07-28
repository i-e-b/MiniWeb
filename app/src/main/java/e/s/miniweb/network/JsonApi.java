package e.s.miniweb.network;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

/**
 * Helper for calling network HTTP APIs that use JSON as their format.
 */
public class JsonApi {
    private static final String TAG = "JsonApi";
    private static final String UserAgent = "MiniWeb";

    /**
     * Make a GET call to an API over HTTPS
     * @param host target of call (e.g. "api.github.com", "api.usaspending.gov", etc)
     * @param path full path of the endpoint (e.g. "/api/v2/contracts")
     * @param bearerToken optional security token
     * @return JSON object or null on error
     * @noinspection SameParameterValue
     */
    public static JSONObject getJsonFromUrl(String host, String path, String bearerToken) {
        HttpURLConnection httpConn = null;
        JSONObject object = null;
        InputStream inStream = null;
        try {
            URL url = new URL("https://"+host+path);
            URLConnection connection = url.openConnection();
            httpConn = (HttpURLConnection) connection;

            httpConn.setRequestMethod("GET");
            httpConn.setRequestProperty("Accept", "application/json");
            httpConn.setRequestProperty("Host", host);
            httpConn.setRequestProperty("User-Agent", UserAgent);

            if (bearerToken != null){
                httpConn.setRequestProperty("Authorization", "Bearer " + bearerToken);
            }

            httpConn.setDoOutput(false);
            httpConn.setDoInput(true);
            httpConn.connect();

            int status = httpConn.getResponseCode();
            boolean isSuccess = false;

            if (status != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Reply is bad: " + status);
                inStream = httpConn.getErrorStream();
            } else {
                Log.i(TAG, "Reply is OK");
                isSuccess = true;
                inStream = httpConn.getInputStream();
            }

            BufferedReader bReader = new BufferedReader(new InputStreamReader(inStream));
            String temp;
            StringBuilder response = new StringBuilder();
            while ((temp = bReader.readLine()) != null) {
                response.append(temp);
            }
            if (isSuccess) {
                object = (JSONObject) new JSONTokener(response.toString()).nextValue();
            } else {
                Log.e(TAG, "Got error response! " + response);
            }

        } catch (ProtocolException e) {
            Log.e(TAG, "Network protocol error", e);
        } catch (JSONException e) {
            Log.e(TAG, "JSON format error", e);
        } catch (MalformedURLException e) {
            Log.e(TAG, "URL error", e);
        } catch (IOException e) {
            Log.e(TAG, "Read/Write error", e);
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();// this will close the bReader as well
                } catch (IOException ignored) {
                }
            }
            if (httpConn != null) {
                httpConn.disconnect();
            }
        }
        return object;
    }

    /**
     * Make a POST call to an API over HTTPS with a raw string body
     *
     * @param host target of call (e.g. "api.github.com", "api.usaspending.gov", etc)
     * @param path full path of the endpoint (e.g. "/api/v2/contracts")
     * @param bearerToken optional security token
     * @param body request body to post
     * @return JSON object, or null on error
     */
    public static JSONObject postJsonFromUrl(String host, String path, String bearerToken, String body) {
        HttpURLConnection httpConn = null;
        JSONObject object = null;
        InputStream inStream = null;
        try {
            URL url = new URL("https://"+host+path);
            httpConn = (HttpURLConnection) url.openConnection();

            httpConn.setRequestMethod("POST");
            httpConn.setRequestProperty("Accept", "application/json");
            httpConn.setRequestProperty("Content-Type", "application/json");
            httpConn.setRequestProperty("Host", host);
            httpConn.setRequestProperty("User-Agent", "eWaterApp");

            if (bearerToken != null){
                httpConn.setRequestProperty("Authorization", "Bearer " + bearerToken);
            }

            httpConn.setDoOutput(true);
            httpConn.setDoInput(true);
            httpConn.setUseCaches(false);

            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = httpConn.getOutputStream()) {
                os.write(bodyBytes);
            }

            httpConn.connect();

            int status = httpConn.getResponseCode();
            boolean isSuccess = false;

            if (status != HttpURLConnection.HTTP_OK)  {
                Log.i(TAG, "Reply is bad: "+status);
                inStream = httpConn.getErrorStream();
            } else  {
                Log.i(TAG, "Reply is OK");
                isSuccess = true;
                inStream = httpConn.getInputStream();
            }

            BufferedReader bReader = new BufferedReader(new InputStreamReader(inStream));
            String temp;
            StringBuilder response = new StringBuilder();
            while ((temp = bReader.readLine()) != null) {
                response.append(temp);
            }
            if (isSuccess) {
                object = (JSONObject) new JSONTokener(response.toString()).nextValue();
            } else {
                Log.e(TAG, "Got error response! " + response);
            }
        } catch (ProtocolException e) {
            Log.e(TAG, "Network protocol error", e);
        } catch (JSONException e) {
            Log.e(TAG, "JSON format error", e);
        } catch (MalformedURLException e) {
            Log.e(TAG, "URL error", e);
        } catch (IOException e) {
            Log.e(TAG, "Read/Write error", e);
        } finally {
            if (inStream != null) {
                try {
                    inStream.close();// this will close the bReader as well
                } catch (IOException ignored) {}
            }
            if (httpConn != null) {
                httpConn.disconnect();
            }
        }
        return object;
    }

    /**
     * Make a POST call to an API over HTTPS with a JSON object body
     *
     * @param host target of call (e.g. "api.github.com", "api.usaspending.gov", etc)
     * @param path full path of the endpoint (e.g. "/api/v2/contracts")
     * @param bearerToken optional security token
     * @param body request body to post
     * @return JSON object, or null on error
     */
    public static JSONObject postJsonToUrl(String host, String path, String bearerToken, JSONObject body){
        return postJsonFromUrl(host, path, bearerToken, body.toString());
    }
}
