package e.s.miniweb.core;

import e.s.miniweb.template.TemplateResponse;

public class ControllerBase {
    /**
     * Generate a model/template html view
     * @param viewPath path under the assets/views folder to the .html template (e.g. "home/index")
     * @param model an object that will be used to fill the template
     * @return data that will be used to render the page.
     */
    public TemplateResponse Page(String viewPath, Object model){
        TemplateResponse resp = new TemplateResponse();

        resp.TemplatePath = viewPath;
        resp.Model = model;

        return resp;
    }

    /**
     * Send the user back to home/index. This will also clear the 'back' button
     * so the user doesn't get sent bck through a finished journey.
     * @return data that will be used to render the page.
     */
    public TemplateResponse EndOfPath(){
        TemplateResponse resp = new TemplateResponse();

        resp.RedirectUrl = "app://home";

        return resp;
    }
}
