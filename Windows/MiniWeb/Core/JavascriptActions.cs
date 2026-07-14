using System.IO;
using Microsoft.Web.WebView2.Core;
using Microsoft.Win32;
using SkinnyJson;

namespace MiniWeb.Core;

/// <summary>
/// Handle calls from Javascript through <c>PageRequest</c>
/// </summary>
public static class JavascriptActions
{

    /// <summary>
    /// Handle calls from Javascript through <c>PageRequest</c>
    /// </summary>
    /// <param name="core">WebView2 core</param>
    /// <param name="jsonStr">JSON data sent from web page</param>
    public static async Task HandlePageRequest(CoreWebView2 core, string jsonStr)
    {
        try
        {
            var msg = Json.Defrost<JsMessage>(jsonStr);

            switch (msg.Action)
            {
                case "print":
                {
                    core.ShowPrintUI();
                    break;
                }

                case "load":
                {
                    // Load file dialog

                    var fileDialog = new OpenFileDialog();
                    if (fileDialog.ShowDialog() == true)
                    {
                        var file = new JsMessage { Data = await File.ReadAllTextAsync(fileDialog.FileName) };
                        core.AddHostObjectToScript("file", file);
                        var result = await core.ExecuteScriptAsync("echoFile();");
                        Console.WriteLine("Page result = " + result);
                    }

                    break;
                }

                case "save":
                {
                    var fileDialog = new SaveFileDialog();
                    if (fileDialog.ShowDialog() == true)
                    {
                        await File.WriteAllTextAsync(fileDialog.FileName, msg.Data);
                        Console.WriteLine("Data saved to " + fileDialog.FileName);
                    }

                    break;
                }

                default:
                {
                    Console.WriteLine($"Unhandled page action: {msg.Action}");
                    break;
                }
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine("Decode error " + ex);
        }
    }
}