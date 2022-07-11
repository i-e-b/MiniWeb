package e.s.miniweb.template;

import android.webkit.WebResourceRequest;

import java.util.Map;

@FunctionalInterface
public interface WebMethod {
    TemplateResponse generate(Map<String, String> parameters, WebResourceRequest request);
}
