// ReSharper disable MemberCanBeMadeStatic.Global

using System.Diagnostics.CodeAnalysis;
using System.Text;
using JetBrains.Annotations;

namespace MiniWeb.Core;

#pragma warning disable CA1822

/// <summary>
/// Base class for web Controllers
/// </summary>
[SuppressMessage("ReSharper", "UnusedMember.Global")]
public abstract class ControllerBase
{
    /// <summary>
    /// Implement this in your controller to bind methods
    /// </summary>
    public abstract void BindMethods(ControllerBinding bind);

    /// <summary>
    /// Generate a model/template html view
    /// </summary>
    /// <param name="viewPath">path under the assets/views folder to the .html template (e.g. "home/index")</param>
    /// <param name="model">[Optional] an object that will be used to fill the template</param>
    /// <param name="interactions">[Optional] provider for page callbacks</param>
    /// <returns>data that will be used to render the page</returns>
    protected TemplateResponse Page([PathReference("~/")]string viewPath, object? model = null, IPageInteractions? interactions = null){
        return new TemplateResponse
        {
            TemplatePath = viewPath,
            Model = model,
            InteractionProvider = interactions
        };
    }

    /// <summary>
    /// Trigger the download of a file
    /// </summary>
    protected TemplateResponse FileDownload(byte[] data, string mime, string fileName)
    {
        return new FileDownloadResponse
        {
            FileData = data,
            MimeType = mime,
            FileName = fileName
        };
    }

    /**
     * Send the user to another page. This will NOT clear the 'back' path
     * @return data that will be used to render the page.
     */
    protected TemplateResponse Redirect(string url){
        return new TemplateResponse
        {
            RedirectUrl = url
        };
    }

    /// <summary>
    /// Run an async function as a background task
    /// </summary>
    protected void InBackground(Func<Task> task)
    {
        _ = Task.Run(async () => { await task(); });
    }


    /// <summary>
    /// Decode POSTed uri-form data to a dictionary
    /// </summary>
    protected static Dictionary<string, string> DecodeFormData(byte[]? formData)
    {
        var result = new Dictionary<string, string>();

        if (formData is null || formData.Length < 1) return result;

        var str   = Encoding.UTF8.GetString(formData);
        var pairs = str.Split("&", StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries);

        foreach (var pair in pairs)
        {
            var bits = pair.Split("=", 2, StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries);

            if (bits.Length < 1) continue;
            if (bits.Length < 2) result.TryAdd(Uri.UnescapeDataString(PlusToSpace(bits[0])), Uri.UnescapeDataString(PlusToSpace(bits[0])));
            else result.TryAdd(Uri.UnescapeDataString(PlusToSpace(bits[0])), Uri.UnescapeDataString(PlusToSpace(bits[1])));
        }

        return result;
    }

    private static string PlusToSpace(string s) => s.Replace("+", " ");
}