using System.IO;
using System.Net.Http;
using System.Reflection;
using System.Text;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.Core.DevToolsProtocolExtension;
using Microsoft.Web.WebView2.Wpf;
using Console = System.Console;

namespace MiniWeb.Core;

/// <summary>
/// In-app web site hosting for a WebView2 control
/// </summary>
public class Host : IAssetLoader, IPermissionsCheck, INetworkRequest
{
    private readonly SelfHostWebWindow _window;

    private volatile bool          _pageReady;
    private readonly Queue<string> _delayedScripts = new();

    /// <summary>
    /// Shared Http client for server-side requests to external web services
    /// </summary>
    public HttpClient Http { get; } = new();

    private WebView2 View { get; }
    private CoreWebView2 Core => View.CoreWebView2;

    /// <summary>
    /// Initial web view host
    /// </summary>
    public static Host? FirstHost { get; set; }

    /// <summary> Last page URL that was requested (does not include page resources) </summary>
    private string _lastNavigationUrl = "Unknown";

    /// <summary> Last controller used to render a page, or null if none</summary>
    private IPageInteractions? _lastInteractionProvider;

    /// <summary> Names of embedded resources in the executing assembly </summary>
    private readonly List<string> _resourceNames = [];

    /// <summary> Path for hot-load resources, or <c>null</c> if not available </summary>
    private readonly string? _hotLoadPath;

    /// <summary> Tool for rendering templates to documents </summary>
    private readonly TemplateEngine _template;

    /// <summary>
    /// Create a new host connected to a view
    /// </summary>
    public Host(WebView2 view, SelfHostWebWindow window)
    {
        _window = window;
        View = view;
        _template = new TemplateEngine(this, this, this);

        // Prepare all the controllers for everything
        ControllerBindings.BindAllControllers();

        // If we're running inside of a compiler output folder, try to load resources from source.
        var location = Assembly.GetCallingAssembly().Location;
        var idx      = location.LastIndexOf("bin\\Debug", StringComparison.Ordinal);
        if (idx > 0)
        {
            _hotLoadPath = location[..(idx - 1)] + "\\";
            //Console.WriteLine($"Hot-load path: {_hotLoadPath}");
        }
        else
        {
            _hotLoadPath = null;
            //Console.WriteLine("Hot-load is not available");
        }
    }

    /// <summary>
    /// Connect the view to this host and navigate to the home page
    /// </summary>
    public async Task Initialise(string? startUrl)
    {
        var customSchemeRegistrations = new List<CoreWebView2CustomSchemeRegistration>
        {
            new("app") // files embedded in the app, or controller responses
            {
                TreatAsSecure = true, HasAuthorityComponent = false, AllowedOrigins = ["*"]
            },
            new("asset") // files embedded in the app
            {
                TreatAsSecure = true, HasAuthorityComponent = false, AllowedOrigins = ["*"]
            },
            new("gen") // resources to be generated on request
            {
                TreatAsSecure = true, HasAuthorityComponent = false, AllowedOrigins = ["*"]
            }
        };

        var options = new CoreWebView2EnvironmentOptions(
            additionalBrowserArguments: null,
            language: null,
            targetCompatibleBrowserVersion: null,
            allowSingleSignOnUsingOSPrimaryAccount: false,
            customSchemeRegistrations: customSchemeRegistrations);

        options.EnableTrackingPrevention = false;

        var environment = await CoreWebView2Environment.CreateAsync(
            userDataFolder: null,
            options: options);

        await View.EnsureCoreWebView2Async(environment);


        Core.Settings.IsStatusBarEnabled = false;
        Core.Settings.IsGeneralAutofillEnabled = false;
        Core.Settings.IsPasswordAutosaveEnabled = false;
        Core.Settings.IsReputationCheckingRequired = false;

        Core.Settings.IsScriptEnabled = true;
        Core.Settings.IsWebMessageEnabled = true;

        var devMode = _hotLoadPath != null; // Dev tools enabled if running from debug folder
        Core.Settings.AreDevToolsEnabled = devMode;
        Core.Settings.AreDefaultContextMenusEnabled = devMode;

        await LinkWebConsole();

        Core.WebResourceRequested += DoWebRequest;
        Core.WebMessageReceived += DoWebMessage;

        Core.NavigationStarting += NavigationStarting;
        Core.NavigationCompleted += NavigationCompleted;
        Core.FrameNavigationStarting += FrameNavigationStarting;
        Core.DownloadStarting += DownloadStarting;

        Core.ProcessFailed += ProcessFailed;
        Core.PermissionRequested += PermissionRequested;
        Core.WindowCloseRequested += WindowClosing;

        Core.NewWindowRequested += RequestedNewWindow;

        Core.AddWebResourceRequestedFilter("app://*", CoreWebView2WebResourceContext.All, CoreWebView2WebResourceRequestSourceKinds.All);
        Core.AddWebResourceRequestedFilter("asset://*", CoreWebView2WebResourceContext.All, CoreWebView2WebResourceRequestSourceKinds.All);
        Core.AddWebResourceRequestedFilter("https://*", CoreWebView2WebResourceContext.All, CoreWebView2WebResourceRequestSourceKinds.All);

        if (FirstHost is null)
        {
            FirstHost = this;
            RotatingLogs.SetOutputDirectory(Path.Combine(GetCacheDirectory(), "Logs"));
            Console.WriteLine($"####################   App started {SelfHostWebWindow.BootTime:yyyy-MM-dd HH:mm:ss} UTC ####################");
            Console.WriteLine($"#################### Browser ready {DateTime.UtcNow:yyyy-MM-dd HH:mm:ss} UTC ####################");
        }

        Core.Navigate(startUrl ?? "https://home/index");
    }

    /// <summary>
    /// Capture web browser console and exception details, then write into our main logs.
    /// </summary>
    private async Task LinkWebConsole()
    {
        var helper = Core.GetDevToolsProtocolHelper();
        await helper.Runtime.EnableAsync();

        helper.Runtime.ConsoleAPICalled += (_, e) =>
        {
            foreach (var arg in e.Args){
                Console.WriteLine($"{DateTime.UtcNow:yyyy-MM-dd HH:mm:ss} [{e.Type}: " + arg.Value.ToString() + "]");
            }
        };

        helper.Runtime.ExceptionThrown += (_, e) =>
        {
            Console.WriteLine($"{DateTime.UtcNow:yyyy-MM-dd HH:mm:ss} [Browser Exception:\r\n" + e.ExceptionDetails.Exception.Description.Replace("\n","\r\n") + "\r\n]");
        };

        await Core.CallDevToolsProtocolMethodAsync("Log.enable", "{}");
    }

    private void RequestedNewWindow(object? sender, CoreWebView2NewWindowRequestedEventArgs e)
    {
        Console.WriteLine($"New window requested: Url='{e.Uri}';");

        // Start a new window, and request a starting URL
        var secondary = new SelfHostWebWindow(e.Uri)
        {
            ShowActivated = true, // New window should display over this one
            Left = _window.Left + 32, // Position over the current window
            Top = _window.Top + 32,
            Width = _window.Width - 64,
            Height = _window.Height - 64
        };

        secondary.Show();

        e.Handled = true; // suppress automatic window creation
    }

    private void FrameNavigationStarting(object? sender, CoreWebView2NavigationStartingEventArgs e)
    {
    }

    private void WindowClosing(object? sender, object e)
    {
        try
        {
            _lastInteractionProvider?.PageClosed(this);
        }
        catch (Exception ex)
        {
            Console.WriteLine("Failure on 'PageClosed' call from 'WindowClosing': " + ex);
        }
    }

    private void PermissionRequested(object? sender, CoreWebView2PermissionRequestedEventArgs e)
    {
        Console.WriteLine("Permission requested: " + e.PermissionKind);
        e.State = CoreWebView2PermissionState.Allow;
    }

    private void NavigationStarting(object? sender, CoreWebView2NavigationStartingEventArgs e)
    {
        _pageReady = false;
        ClearDelayedScripts();

        try
        {
            _lastInteractionProvider?.PageClosed(this);
        }
        catch (Exception ex)
        {
            Console.WriteLine("Failure on 'PageClosed' call: " + ex);
        }

        _lastInteractionProvider = null;
        _lastNavigationUrl = e.Uri;
        Console.WriteLine($"Page requested: {e.NavigationKind} {e.Uri}");
    }

    private void NavigationCompleted(object? sender, CoreWebView2NavigationCompletedEventArgs e)
    {
        if (e.WebErrorStatus == CoreWebView2WebErrorStatus.ConnectionAborted)
        {
            // This happens when we do a download
            Core.Navigate(_lastNavigationUrl);
            return;
        }

        Console.WriteLine($"Navigation complete. URL={_lastNavigationUrl}; OK={e.IsSuccess}; Status={e.WebErrorStatus};");

        if (!e.IsSuccess)
        {
            ShowErrorPage(e.WebErrorStatus);
        }
        else
        {
            _pageReady = true;
            TriggerDelayedScripts();
        }
    }

    private void DoWebMessage(object? sender, CoreWebView2WebMessageReceivedEventArgs e)
    {
        View.Dispatcher.Invoke(async () =>
        {
            if (_lastInteractionProvider is not null)
            {
                try
                {
                    var handled = await _lastInteractionProvider.HandlePageRequest(this, e.WebMessageAsJson);
                    if (handled) return;
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"Error in page request handler: {ex}");
                }
            }

            await JavascriptActions.HandlePageRequest(Core, e.WebMessageAsJson);
        });
    }

    private void ProcessFailed(object? sender, CoreWebView2ProcessFailedEventArgs e)
    {
        Console.WriteLine($"Web view stopped responding. Last Request: {_lastNavigationUrl}; Reason: {e.Reason.ToString()}");
    }

    private void DownloadStarting(object? sender, CoreWebView2DownloadStartingEventArgs e)
    {
        Console.WriteLine($"Download started: {e.ResultFilePath} ({e.DownloadOperation.MimeType})");
    }

    /// <summary>
    /// Handle requests for web resources. This is the main route for self-hosting our pages
    /// without needing to spin up a localhost server.
    /// <p>NOTE: This MUST NOT be an <c>async</c> method, due to CoreWebView2 internals</p>
    /// </summary>
    private void DoWebRequest(object? sender, CoreWebView2WebResourceRequestedEventArgs e)
    {
        Console.WriteLine($"{e.Request.Method} {e.Request.Uri}");

        var url = e.Request.Uri.Replace("https://", "app://");

        if (url.EndsWith("/favicon.ico"))
        {
            e.Response = RawFile("<?xml version=\"1.0\" encoding=\"UTF-8\"?><svg version=\"1.1\" viewBox=\"0 0 48 48\"\nxmlns=\"http://www.w3.org/2000/svg\"><circle cx=\"24\" cy=\"24\" r=\"18\" fill=\"#5b86bf\"/></svg>"u8.ToArray() , "image/svg+xml");
        }
        else if (url.StartsWith("app://")) // Files, controllers, templates, etc
        {
            var rsrc = url.Replace("app://", "");
            var mime = GuessMime(rsrc);

            try
            {
                var uri = new Uri(url);
                var key = MethodKeyFromUrl(uri);

                var query  = ControllerBinding.MapParams(uri.Query);
                var action = ControllerBinding.GetMethod(key);

                var request = action?.Invoke(this, query, ResourceRequest.FromCoreWebViewRequest(e.Request));

                if (request?.RedirectUrl is not null)
                {
                    e.Response = TemplateFile("internal/Redirect.html", request, "text/html; charset=utf-8");
                    return;
                }

                if (request is FileDownloadResponse fileRequest)
                {
                    e.Response = DownloadFile(fileRequest.FileData, fileRequest.MimeType, fileRequest.FileName);
                    return;
                }


                if (request?.InteractionProvider is not null)
                {
                    _lastInteractionProvider = request.InteractionProvider;
                }

                if (request?.TemplatePath is not null)
                {
                    e.Response = TemplateFile(request.TemplatePath, request.Model, "text/html; charset=utf-8");
                    return;
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Failed to execute controller method. Url='{url}'; Error='{ex}'");
            }

            // Fall-back to plain embedded file
            e.Response = EmbeddedFile(rsrc, mime);
        }
        else if (url.StartsWith("asset://")) // Only raw embedded files
        {
            var rsrc = url.Replace("asset://", "");
            var mime = GuessMime(rsrc);
            e.Response = EmbeddedFile(rsrc, mime);
        }
        else
        {
            e.Response = FileNotFound();
        }
    }

    private static string MethodKeyFromUrl(Uri uri)
    {
        var controller = uri.Host;
        var method     = uri.AbsolutePath;
        if (string.IsNullOrWhiteSpace(method)) method = "";
        if (method.StartsWith('/')) method = method[1..];
        if (string.IsNullOrWhiteSpace(method)) method = "index";
        var key = ControllerBinding.MakeKey(controller, method);
        return key;
    }

    private void ShowErrorPage(CoreWebView2WebErrorStatus fault)
    {
        Core.NavigateToString(
            $"""
             <!doctype html>
             <html lang="en">
             <head>
                 <title>Error</title>
                 <link rel="stylesheet" href="asset://styles/default.css" type="text/css">
             </head>
             <body>
             <h1>ERROR</h1>
             <p>An error occurred in the application</p>
             <p>Target: '{_lastNavigationUrl}'; Fault: '{fault}';</p>
             <a href="app://home/index">Home</a>
             </body>
             </html>
             """);
    }

    private MemoryStream? GetEmbeddedFile(string path)
    {
        try
        {
            if (_hotLoadPath is not null)
            {
                var hotFile = _hotLoadPath + path.Replace("/", "\\");
                if (File.Exists(hotFile))
                {
                    //Console.WriteLine($"Hot-load at '{hotFile}'");
                    return new MemoryStream(File.ReadAllBytes(hotFile));
                }

                //Console.WriteLine($"No hot-load found. Probed '{hotFile}'");
            }

            var assembly = Assembly.GetExecutingAssembly();

            if (_resourceNames.Count < 1)
            {
                _resourceNames.AddRange(assembly.GetManifestResourceNames());
                /*Console.WriteLine("Resources available:");
                Console.WriteLine("    " + string.Join("\r\n    ", _resourceNames));*/
            }

            var fixedName    = path.Replace("/", ".");

            var resourceName = _resourceNames.SingleOrDefault(str => str.EndsWith(fixedName));
            if (resourceName is null) return null;

            using var stream = assembly.GetManifestResourceStream(resourceName);
            if (stream is null) return null;

            var outp = new MemoryStream();
            stream.CopyTo(outp);
            outp.Seek(0, SeekOrigin.Begin);
            return outp;
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Failed to load '{path}': {ex}");
            return null;
        }
    }

    private static string GuessMime(string path) {
        if (path.EndsWith(".css")) return "text/css";
        if (path.EndsWith(".html")) return "text/html";
        if (path.EndsWith(".js")) return "application/javascript";

        if (path.EndsWith(".svg")) return "image/svg+xml";

        if (path.EndsWith(".png")) return "image/png";
        if (path.EndsWith(".webp")) return "image/webp";
        if (path.EndsWith(".jpg")) return "image/jpeg";
        if (path.EndsWith(".jpeg")) return "image/jpeg";
        if (path.EndsWith(".gif")) return "image/gif";

        if (path.EndsWith(".mp4")) return "video/mp4";

        return "application/octet-stream";
    }

    private CoreWebView2WebResourceResponse FileNotFound()
    {
        return Core.Environment.CreateWebResourceResponse(
            Content: null,
            StatusCode: 404,
            ReasonPhrase: "Not Found",
            Headers: null);
    }

    private CoreWebView2WebResourceResponse EmbeddedFile(string name, string type)
    {
        var content = GetEmbeddedFile(name);
        if (content is null) return FileNotFound();

        return Core.Environment.CreateWebResourceResponse(
            Content: content,
            StatusCode: 200,
            ReasonPhrase: "OK",
            Headers: "Content-Type: "+type);
    }

    private CoreWebView2WebResourceResponse RawFile(byte[] data, string type)
    {
        return Core.Environment.CreateWebResourceResponse(
            Content: new MemoryStream(data),
            StatusCode: 200,
            ReasonPhrase: "OK",
            Headers: "Content-Type: "+type);
    }

    private CoreWebView2WebResourceResponse DownloadFile(byte[] data, string type, string fileName)
    {
        return Core.Environment.CreateWebResourceResponse(
            Content: new MemoryStream(data),
            StatusCode: 200,
            ReasonPhrase: "OK",
            Headers: "Content-Type: " + type + "\r\nContent-Disposition: attachment; filename=\"" + fileName + "\"");
    }

    private CoreWebView2WebResourceResponse TemplateFile(string rsrc, object? model, string mime)
    {
        var content = GetEmbeddedFile(rsrc);
        if (content is null) return FileNotFound();

        var result = _template.Render(content, model);
        if (!result.Success || result.Result is null) return FileNotFound();

        // Unless page looks like it has its own headers, wrap the result in a standard page container
        // This links in default styles and scripts.
        if (!result.Result.StartsWith("<!doctype") && !result.Result.StartsWith("<html"))
        {
            result.Result = WrapPageStringWithHtmlHeaders(result);
        }

        return RawFile(Encoding.UTF8.GetBytes(result.Result), mime);
    }

    /// <summary>
    /// Wrap page result in standard HTML header and footer. This links in default styles and scripts.
    /// </summary>
    private string WrapPageStringWithHtmlHeaders(TemplateResponse result)
    {
        var sb = new StringBuilder();

        sb.Append("<!doctype html><html><head><meta charset=\"UTF-8\">"); // document with header and char set.
        sb.Append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />"); // makes styling consistent
        sb.Append("<link rel=\"stylesheet\" href=\"asset://styles/default.css\" type=\"text/css\">"); // default styles for both light & dark

        // Add specific dark or light mode styles
        sb.Append("<link rel=\"stylesheet\" href=\"asset://styles/");

        if (_window.InDarkMode()){
            sb.Append("default-dark.css");
        } else {
            sb.Append("default-light.css");
        }
        sb.Append("\" type=\"text/css\">");

        // Add default script
        sb.Append("<script type=\"text/javascript\" src=\"asset://scripts/common.js\"></script>");

        // Add page content
        sb.Append("</head><body>");
        sb.Append(result.Result);
        sb.Append("</body></html>");


        return sb.ToString();
    }

    /// <inheritdoc />
    public Stream? LoadAsset(string path) =>
        GetEmbeddedFile(path);

    /// <inheritdoc />
    public bool GrantPermission(string[] required) => true;

    /// <inheritdoc />
    public string? GetResponse(string url, object? requestModel)
    {
        Console.WriteLine("Url requested: " + url);

        return null;
    }

    /// <summary>
    /// Invoke client-side script on the view core
    /// </summary>
    // ReSharper disable once UnusedMethodReturnValue.Global
    public async Task<string> ExecuteScriptAsync(string s)
    {
        //Console.WriteLine($"Sending JS: ({s})");
        return await View.Dispatcher.Invoke(async () => await Core.ExecuteScriptAsync(s));
    }

    private void ClearDelayedScripts()
    {
        _delayedScripts.Clear();
    }

    private void TriggerDelayedScripts()
    {
        while (_delayedScripts.TryDequeue(out var next))
        {
            Console.WriteLine($"Delayed script: '{next}'");
            _ = Task.Run(async () => { await View.Dispatcher.Invoke(async () => await Core.ExecuteScriptAsync(next)); });
        }
    }

    /// <summary>
    /// Invoke client-side script on the view core, as a background task
    /// </summary>
    public void ExecuteScriptBackground(string s)
    {
        if (_pageReady)
        {
            TriggerDelayedScripts();
            _ = Task.Run(async () => { await View.Dispatcher.Invoke(async () => await Core.ExecuteScriptAsync(s)); });
        }
        else
        {
            _delayedScripts.Enqueue(s);
        }
    }

    /// <summary>
    /// Get directory used for WebView2 cache, and available for application temporary files
    /// </summary>
    public string GetCacheDirectory()
    {
        return View.Dispatcher.Invoke(() => Core.Environment.UserDataFolder);
    }

    /// <summary>
    /// Reload the current page by URL.
    /// </summary>
    public void ReloadCurrentPage()
    {
        View.Dispatcher.Invoke(() => Core.Reload());
    }

    /// <summary>
    /// Hide the WebView, and show a message for the user to dismiss
    /// </summary>
    public void ShowModalMessage(string message, string header)
    {
        _window.ShowModalMessage(message, header);
    }
}
