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

    private static void DoRouting(string url, HttpListenerContext ctx)
    {
        switch (url)
        {
            case "/":
            {
                SendOk(ctx, WelcomePage());
                break;
            }

            case "/host":
            {
                SendOk(ctx, Encoding.UTF8.GetBytes(HostUpMsg));
                break;
            }

            case "/time":
            {
                SendOk(ctx, Encoding.UTF8.GetBytes(DateTime.UtcNow.ToString("yyyy-MM-dd HH:mm:ss")));
                break;
            }

            default:
            {
                SendNotFound(ctx, Encoding.UTF8.GetBytes(NotFoundMsg));
                break;
            }
        }
    }
    
    public static void Main()
    {
        Console.WriteLine("Starting little web hook...");

        _running = true;
        Console.CancelKeyPress += (_, _) => { _running = false; Console.WriteLine("Shutting down"); };

        var listener = new HttpListener();
        listener.IgnoreWriteExceptions = true;
        listener.Prefixes.Add("ht"+"tp://+:1310/");
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
                    Console.WriteLine($"HTTP listener responding to {url}");

                    Console.WriteLine($"Request '{url}'");

                    DoRouting(url, ctx);
                }
                catch (Exception innerEx)
                {
                    Console.WriteLine("Responder error: "+innerEx);
                    SendError(ctx, Encoding.UTF8.GetBytes(HostErrorMsg));
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
    }

    private static byte[] WelcomePage()
    {
        return T.g("html")[
            T.g("head")[
                T.g("title")["Tiny web hook"]
            ],
            T.g("body")[
                T.g("h1")["Tiny Web Hook"],
                T.g("p")[
                    "This is a little demo app that listens on localhost. ",
                    "If we are lucky, we can get the Android emulator to call it?"
                ]
            ]
        ].ToBytes(Encoding.UTF8);
    }

    private static void SendOk(HttpListenerContext ctx, byte[] bytes)
    {
        ctx.Response.StatusCode = 200;
        ctx.Response.StatusDescription = "OK";
        ctx.Response.AddHeader("Content-Type", "text/html");
        ctx.Response.OutputStream.Write(bytes);
        ctx.Response.OutputStream.Flush();
    }
    
    private static void SendNotFound(HttpListenerContext ctx, byte[] bytes)
    {
        ctx.Response.StatusCode = 404;
        ctx.Response.StatusDescription = "Not Found";
        ctx.Response.AddHeader("Content-Type", "text/html");
        ctx.Response.OutputStream.Write(bytes);
        ctx.Response.OutputStream.Flush();
    }
    
    
    private static void SendError(HttpListenerContext ctx, byte[] bytes)
    {
        ctx.Response.StatusCode = 500;
        ctx.Response.StatusDescription = "Server Error";
        ctx.Response.AddHeader("Content-Type", "text/html");
        ctx.Response.OutputStream.Write(bytes);
        ctx.Response.OutputStream.Flush();
    }
}