package e.s.miniweb.controllers;

import android.webkit.WebResourceRequest;

import java.util.Map;

import e.s.miniweb.R;
import e.s.miniweb.template.TemplateEngine;
import e.s.miniweb.template.TemplateResponse;

public class Home {
    public Home(){
        TemplateEngine.BindMethod("home", "index", this::index);
    }

    public TemplateResponse index(Map<String,String>parameters, WebResourceRequest request){
        TemplateResponse resp = new TemplateResponse();
        resp.Model = this;
        resp.Template = "home/index";
        return resp;
    }

}
