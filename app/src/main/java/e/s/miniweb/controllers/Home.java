package e.s.miniweb.controllers;

import android.webkit.WebResourceRequest;

import java.util.Map;

import e.s.miniweb.template.TemplateEngine;
import e.s.miniweb.template.TemplateResponse;

public class Home {
    public Home(){
        TemplateEngine.BindMethod("home", "init", this::init);
    }

    public TemplateResponse init(Map<String,String>parameters, WebResourceRequest request){
        TemplateResponse resp = new TemplateResponse();
        resp.Model = this;
        resp.TemplateName = "whatever";
        return resp;
    }
}
