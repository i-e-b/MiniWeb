package e.s.miniweb.core;

import android.webkit.WebResourceRequest;

import java.util.Map;

public class ResourceRequest {

    /** Requested resource */
    public String uri;

    /** Gets the HTTP method for the request, for example "GET", "POST". */
    public String method;

    /** HTTP request headers */
    public Map<String, String> headers;

    public static ResourceRequest fromWebRequest(WebResourceRequest webRequest) {
        ResourceRequest result = new ResourceRequest();
        result.uri = webRequest.getUrl().toString();
        result.method = webRequest.getMethod();
        result.headers = webRequest.getRequestHeaders();
        return result;
    }
}
