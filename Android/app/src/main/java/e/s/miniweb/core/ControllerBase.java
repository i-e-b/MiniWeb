package e.s.miniweb.core;

import e.s.miniweb.core.template.TemplateResponse;

public class ControllerBase {
    /**
     * Generate a model/template html view
     * @param viewPath path under the assets/views folder to the .html template (e.g. "home/index")
     * @param model an object that will be used to fill the template
     * @return data that will be used to render the page.
     */
    public TemplateResponse Page(String viewPath, Object model){
        TemplateResponse resp = new TemplateResponse();

        // just in case someone put the file ending on, we take it back off.
        if (viewPath.endsWith(".html")) viewPath=viewPath.substring(0, viewPath.length() - 5);

        resp.TemplatePath = viewPath;
        resp.Model = model;

        return resp;
    }

    /**
     * Send the user to another page. This will NOT clear the 'back' path
     * @return data that will be used to render the page.
     */
    public TemplateResponse Redirect(String url){
        TemplateResponse resp = new TemplateResponse();

        resp.RedirectUrl = url;

        return resp;
    }

    /**
     * Send the user back to home/index. This will also clear the 'back' path,
     * so the user doesn't get sent back through a finished journey.
     * @return data that will be used to render the page.
     */
    public TemplateResponse EndOfPath(){
        TemplateResponse resp = new TemplateResponse();

        resp.RedirectUrl = "app://home";
        resp.ShouldClearHistory = true;

        return resp;
    }
}
