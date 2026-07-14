using System.Diagnostics;
using System.Runtime.InteropServices;
using MiniWeb.Core;
using SkinnyJson;

namespace MiniWeb.Controllers;

/// <summary>
/// Initial page controller
/// </summary>
public class Home : ControllerBase, IPageInteractions
{
    /// <inheritdoc />
    public override void BindMethods(ControllerBinding bind)
    {
        bind.Method(this, "home", "index", Index);
    }

    private TemplateResponse Index(Host host, Dictionary<string, string> parameters, ResourceRequest request)
    {
        return Page("pages/home/index.html", null, this);
    }

    /// <inheritdoc />
    public Task<bool> HandlePageRequest(Host host, string jsonStr)
    {
        var msg = Json.Defrost<JsMessage>(jsonStr);
        switch (msg.Action)
        {
            case "show-logs":
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = "explorer.exe",
                    Arguments = $"/root,\"{RotatingLogs.GetDirectory()}\"",
                    UseShellExecute = true
                });
                return Task.FromResult(true);
            }

            case "open-project":
            {
                OpenUrl("https://github.com/i-e-b/MiniWeb");
                return Task.FromResult(true);
            }

            default:
                Console.WriteLine($"Unknown request: {msg.Action}");
                return Task.FromResult(false);
        }
    }

    private static void OpenUrl(string url)
    {
        try
        {
            Process.Start(url);
        }
        catch
        {
            if (RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
            {
                url = url.Replace("&", "^&");
                Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
                // Process.Start(new ProcessStartInfo("cmd", $"/c start {url}") { CreateNoWindow = true });
            }
            else if (RuntimeInformation.IsOSPlatform(OSPlatform.Linux))
            {
                Process.Start("xdg-open", url);
            }
            else if (RuntimeInformation.IsOSPlatform(OSPlatform.OSX))
            {
                Process.Start("open", url);
            }
            else
            {
                throw;
            }
        }
    }

    /// <inheritdoc />
    public Task PageClosed(Host host) { return Task.CompletedTask; }
}