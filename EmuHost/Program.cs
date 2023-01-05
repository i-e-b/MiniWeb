using System.Net;
using System.Text;
using Tag;

namespace TinyWebHook;

internal static class Program
{
    public const string Protocol = "http";
    public const string PublicHost = "127.0.0.1:1310";
    public const string EmulatorHost = "+:1310";
    public const string NotFoundMsg = "NOT_FOUND";
    public const string HostErrorMsg = "HOST_ERROR";
    public const string HostUpMsg = "ANDROID_EMU_HOST_V1";
    
    private static volatile bool _running;
    private static string? _basePath;
    
    private static string? _lastPagePush;

    public static int Main(string[]? args)
    {
        if (args is null || args.Length < 1)
        {
            Console.WriteLine("Please specify the Android app \"assets\" folder to watch.");
            return 1;
        }

        _basePath = string.Join(" ", args);
        if (!Directory.Exists(_basePath))
        {
            Console.WriteLine($"Path at \"{_basePath}\" was not found, or could not be accessed (check permissions?)");
            return 1;
        }

        Console.WriteLine("Starting little web hook...");

        _running = true;
        Console.CancelKeyPress += (_, _) => { _running = false; Console.WriteLine("Shutting down"); };

        var listener = new HttpListener();
        listener.IgnoreWriteExceptions = true;
        listener.Prefixes.Add($"{Protocol}://{EmulatorHost}/"); // Comment this out to run as low-privilege user. Hot-load will not work.
        listener.Prefixes.Add($"{Protocol}://{PublicHost}/");
        listener.Start();

        Console.WriteLine($"Listening on {Protocol}://{EmulatorHost}/  or  {Protocol}://{PublicHost}/");

        while (_running)
        {
            try
            {
                var ctx = listener.GetContext();
                Console.WriteLine("Got request");

                try
                {
                    var url = ctx.Request.RawUrl ?? "<null>";
                    Console.WriteLine($"Request '{url}'");

                    var kind = DoRouting(url, ctx);
                    
                    Console.WriteLine(kind.ToString());
                }
                catch (Exception innerEx)
                {
                    Console.WriteLine("Responder error: " + innerEx);
                    SendError(ctx, HostErrorMsg);
                }
                finally
                {
                    ctx.Response.Close();
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine("Failure in HTTP handler" + ex);
                Thread.Sleep(1000);
            }
        }
        Console.WriteLine("Normal exit");
        return 0;
    }

    private static ContextResult DoRouting(string url, HttpListenerContext ctx)
    {
        var bits = url.Split(new[] { '/' }, StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries);
        if (bits.Length < 1) return SendOk(ctx, "text/html", WelcomePage());

        switch (bits[0])
        {
            case "host": return SendOk(ctx, "text/plain", Encoding.UTF8.GetBytes(HostUpMsg));

            case "time": return SendOk(ctx, "text/plain", Encoding.UTF8.GetBytes(DateTime.UtcNow.ToString("yyyy-MM-dd HH:mm:ss")));

            case "assets":
            {
                if (string.IsNullOrWhiteSpace(_basePath)) return SendError(ctx, "Invalid base path");

                var path = Path.Combine(_basePath, string.Join(Path.DirectorySeparatorChar, bits.Skip(1)));
                return !File.Exists(path)
                    ? SendNotFound(ctx, $"Not found: {path}")
                    : SendOk(ctx, GuessMime(path), File.ReadAllBytes(path));
            }

            case "local-assets": // a hack to fix-up CSS files etc when viewing in web browser.
            {
                if (string.IsNullOrWhiteSpace(_basePath)) return SendError(ctx, "Invalid base path");
                var path = Path.Combine(_basePath, string.Join(Path.DirectorySeparatorChar, bits.Skip(1)));
                
                if (!File.Exists(path)) return SendNotFound(ctx, $"Not found: {path}");

                if (path.Contains(".html") || path.Contains(".js") || path.Contains(".css"))
                {
                    return SendOk(ctx, GuessMime(path), FixUpPaths(File.ReadAllText(path)));
                }
                return SendOk(ctx, GuessMime(path), File.ReadAllBytes(path));

            }

            case "push":
                return HandlePagePush(ctx);
            
            case "screen-shot":
                return HandleScreenShotPush(ctx);
            
            case "get":
                return SendOk(ctx, "text/html", Encoding.UTF8.GetBytes(_lastPagePush ?? "<nothing pushed>"));

            case "touched":
            {
                if (string.IsNullOrWhiteSpace(_basePath)) return SendError(ctx, "Invalid base path");

                var path = Path.Combine(_basePath, string.Join(Path.DirectorySeparatorChar, bits.Skip(1)));
                if (!File.Exists(path)) return SendNotFound(ctx, $"Not found: {path}");
                
                var time = new FileInfo(path).LastWriteTimeUtc.ToString("yyyy-MM-dd HH:mm:ss");
                return SendOk(ctx, "text/plain", Encoding.UTF8.GetBytes(time));
            }

            default:
            {
                return SendNotFound(ctx, NotFoundMsg);
            }
        }
    }

    private static byte[] FixUpPaths(string body)
    {
        return Encoding.UTF8.GetBytes(body .Replace("asset://", $"{Protocol}://{PublicHost}/local-assets/"));
    }

    private static ContextResult HandleScreenShotPush(HttpListenerContext ctx)
    {
        if (ctx.Request.HttpMethod != "POST") return SendBadMethod(ctx, ctx.Request.HttpMethod, "POST");
        if (!ctx.Request.HasEntityBody) return SendBadMethod(ctx, ctx.Request.HttpMethod, "POST");
        
        using var file = File.Open(@"C:\Temp\LastMiniWebSS.png", FileMode.Create, FileAccess.Write, FileShare.None);
        
        ctx.Request.InputStream.CopyTo(file);
        file.Flush();
        file.Close();
        
        return SendOk(ctx, "text/plain", Array.Empty<byte>());
    }

    private static ContextResult HandlePagePush(HttpListenerContext ctx)
    {
        if (ctx.Request.HttpMethod != "POST") return SendBadMethod(ctx, ctx.Request.HttpMethod, "POST");
        if (!ctx.Request.HasEntityBody) return SendBadMethod(ctx, ctx.Request.HttpMethod, "POST");
        
        using var ms = new MemoryStream();
        ctx.Request.InputStream.CopyTo(ms);
        ms.Seek(0, SeekOrigin.Begin);
        
        _lastPagePush = Encoding.UTF8.GetString(ms.ToArray())
            .Replace("asset://", $"{Protocol}://{PublicHost}/local-assets/"); // change app urls to web urls
        return SendOk(ctx, "text/plain", Array.Empty<byte>());
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

        return "application/octet-stream";
    }
    
    private static byte[] WelcomePage()
    {
        return T.g("html")[
            T.g("head")[
                T.g("title")["MiniWeb Hot-Loader"]
            ],
            T.g("body")[
                T.g("h1")["MiniWeb Hot-Loader"],
                T.g("p")[
                    "This site is a companion to the MiniWeb Android template library. ",
                    "If you update files while the Android app is running in an emulator on this PC, ",
                    "the pages should auto-reload."
                ],
                T.g("p")[
                    "Currently monitoring assets at ",
                    T.g("code")[_basePath ?? "<invalid>"]
                ]
            ]
        ].ToBytes(Encoding.UTF8);
    }

    private static ContextResult SendOk(HttpListenerContext ctx, string contentType, byte[] bytes)
    {
        ctx.Response.StatusCode = 200;
        ctx.Response.StatusDescription = "OK";
        ctx.Response.AddHeader("Content-Type", contentType);
        ctx.Response.OutputStream.Write(bytes);
        ctx.Response.OutputStream.Flush();
        return ContextResult.Ok;
    }
    
    private static ContextResult SendNotFound(HttpListenerContext ctx, string message)
    {
        Console.WriteLine($"Warning: {message}");

        ctx.Response.StatusCode = 404;
        ctx.Response.StatusDescription = "Not Found";
        ctx.Response.AddHeader("Content-Type", "text/html");
        ctx.Response.OutputStream.Write((byte[]?)Encoding.UTF8.GetBytes(message));
        ctx.Response.OutputStream.Flush();
        return ContextResult.NotFound;
    }
    
    private static ContextResult SendError(HttpListenerContext ctx, string message)
    {
        Console.WriteLine($"Error: {message}");

        ctx.Response.StatusCode = 500;
        ctx.Response.StatusDescription = "Server Error";
        ctx.Response.AddHeader("Content-Type", "text/html");
        ctx.Response.OutputStream.Write((byte[]?)Encoding.UTF8.GetBytes(message));
        ctx.Response.OutputStream.Flush();
        return ContextResult.Error;
    }
    
    private static ContextResult SendBadMethod(HttpListenerContext ctx, string wrong, string correct)
    {
        Console.WriteLine($"Warning: Called with wrong HTTP method. Expected '{correct}', got '{wrong}'");

        ctx.Response.StatusCode = 405;
        ctx.Response.StatusDescription = "Method Not Allowed";
        ctx.Response.AddHeader("Content-Type", "text/plain");
        ctx.Response.OutputStream.Write((byte[]?)Encoding.UTF8.GetBytes($"Wrong method. Use '{correct}'"));
        ctx.Response.OutputStream.Flush();
        return ContextResult.Error;
    }
}

internal enum ContextResult
{
    Ok,NotFound,Error
}