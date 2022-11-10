using System.Net;
using System.Text;
using Tag;

namespace TinyWebHook;

internal static class Program
{
    public const string NotFoundMsg = "NOT_FOUND";
    public const string HostErrorMsg = "HOST_ERROR";
    public const string HostUpMsg = "ANDROID_EMU_HOST_V1";
    
    private static volatile bool _running;
    private static string? _basePath;

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
        listener.Prefixes.Add("ht"+"tp://+:1310/"); // Comment this out to run as low-privilege user. Hot-load will not work.
        listener.Prefixes.Add("ht"+"tp://127.0.0.1:1310/");
        listener.Start();

        Console.WriteLine("Listening on http://10.0.2.2:1310/  or  http://127.0.0.1:1310/");

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
        if (bits.Length < 1) return SendOk(ctx, "text/html; charset=utf-8", WelcomePage());

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
    
    private static string GuessMime(string path) {
        if (path.EndsWith(".css")) return "text/css";
        if (path.EndsWith(".html")) return "text/html; charset=utf-8";
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
}

internal enum ContextResult
{
    Ok,NotFound,Error
}