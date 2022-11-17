package e.s.miniweb.core.template;

import android.net.Uri;
import android.webkit.WebResourceRequest;

import java.util.Map;

public class InternalRequest implements WebResourceRequest {
    private final Uri target;

    public InternalRequest(Uri target){
        this.target = target;
    }

    @Override
    public Uri getUrl() {
        return target;
    }

    @Override
    public boolean isForMainFrame() {return false;}

    @Override
    public boolean isRedirect() {return false;}

    @Override
    public boolean hasGesture() {return false;}

    @Override
    public String getMethod() {return "GET";}

    @Override
    public Map<String, String> getRequestHeaders() {return null;}
}
