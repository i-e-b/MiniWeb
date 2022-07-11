package e.s.miniweb.template;

import java.util.ArrayList;
import java.util.List;

public class TemplateResponse {
    public Object Model;
    public String TemplatePath;

    // If not null, the web view will be redirected to this url
    public String RedirectUrl;

    // Internal. Todo: move?
    public List<String> TemplateLines;

    public TemplateResponse cloneRange(int startIndex, int endIndex) {
        TemplateResponse result = new TemplateResponse();

        result.Model = Model;
        result.TemplatePath = TemplatePath;
        result.RedirectUrl = RedirectUrl;

        if (endIndex >= TemplateLines.size()) endIndex = TemplateLines.size() - 1;

        result.TemplateLines = new ArrayList<>();
        for(int i = startIndex; i <= endIndex; i++) {
            result.TemplateLines.add(TemplateLines.get(i));
        }

        return result;
    }
}
