package e.s.miniweb.template;

import android.content.res.AssetManager;
import android.webkit.WebResourceRequest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.CharBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TemplateEngine {
    // composite name => call-back; Composite is controller | method
    private static final Map<String, WebMethod> responders = new HashMap<String, WebMethod>();
    private static final Set<Object> controllers = new HashSet<Object>();
    private AssetManager assets;

    public TemplateEngine(AssetManager assets) {
        // Note: Is Java's reflection is so crap we can't pre-load class info in
        // a meaningful way? So we wait until we get a call, and cache from there?
        this.assets = assets;
    }

    public static void BindMethod(String controllerName, String methodName, WebMethod methodFunc) {
        String composite = controllerName+"|="+methodName;
        if (responders.containsKey(composite)){
            System.out.println("Reused method. Ignored.");
            return;
        }

        responders.put(composite, methodFunc);
    }

    public static void Use(Object o) {
        // just keep a reference for the gc
        controllers.add(o);
    }

    // todo: should give a plain string and do the template filling here
    public String Run(String controller, String method, String params, WebResourceRequest request) {
        String composite = controller+"|="+method;

        if ( ! responders.containsKey(composite)){
            System.out.println("Unknown web method!");
            return null;
        }

        WebMethod wm = responders.get(composite);
        TemplateResponse resp = wm.gen(null, request);
        // Call the LoadText method and pass it the resourceId
        //CharSequence raw = resources.getText(R.xml.home); // give the path name
        //XmlResourceParser par = resources.getXml(resp.Template);
        //String raw = par.toString();
        //String raw = LoadText(R.xml.home);
        try {
            InputStream is = assets.open("views/"+resp.Template+".html");
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();

            String readLine = null;

            try {
                // While the BufferedReader readLine is not null
                while ((readLine = br.readLine()) != null) {
                    sb.append(readLine);
                }

                // Close the InputStream and BufferedReader
                is.close();
                br.close();

            } catch (Exception e) {
                e.printStackTrace();
            }

            // todo: templates
            return sb.toString();
        } catch (Exception ex){
            return "Not Found";
        }
    }
}