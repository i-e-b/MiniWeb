package e.s.miniweb.core.template;

import java.util.Map;

import e.s.miniweb.core.ResourceRequest;

@FunctionalInterface
public interface WebMethod {
    TemplateResponse RunControllerMethod(Map<String, String> parameters, ResourceRequest request) throws Exception;
}
