package e.s.miniweb.core.template;

import android.webkit.WebResourceRequest;

import java.util.Map;

@FunctionalInterface
public interface WebMethod {
    TemplateResponse RunControllerMethod(Map<String, String> parameters, WebResourceRequest request) throws Exception;
}
