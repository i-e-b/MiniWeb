package e.s.miniweb.core.template;

import java.util.ArrayList;
import java.util.List;

/**
 * The path to a document template, and the model data to go in it, and some details for the hot-reload system.
 * This is all we need to render a final page.
 */
public class TemplateResponse {
    public Object Model;
    public String TemplatePath;

    // If not null, the web view will be redirected to this url
    public String RedirectUrl;

    // if true AND RedirectUrl is set, the web view history will be cleared after rendering the page.
    public boolean ShouldClearHistory = false;

    // Internal
    public List<String> TemplateLines;

    // These are used to match a refresh to a hot-reload
    public String Controller;
    public String Method;
    public String Params;
    public String LastPageChangeDate;

    /**
     * Duplicate lines in a template.
     * Used for repeating {for:} blocks
     */
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
