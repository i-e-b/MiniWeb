package e.s.miniweb.template;

import java.util.List;

public class TemplateResponse {
    public Object Model;
    public String TemplatePath;

    // If not null, the web view will be redirected to this url
    public String RedirectUrl;

    // Internal. Todo: move?
    public List<String> TemplateLines;
}
