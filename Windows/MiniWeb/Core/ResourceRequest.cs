using System.IO;
using Microsoft.Web.WebView2.Core;

namespace MiniWeb.Core;

/// <summary>
/// Generic HTTP request info
/// </summary>
public class ResourceRequest
{
    /// <summary>
    /// Requested resource, plus any query parameters
    /// </summary>
    public string Uri { get; set; } = "";

    /// <summary>
    /// Gets the HTTP method for the request, for example "GET", "POST"
    /// </summary>
    public string Method { get; set; } = "";

    /// <summary>
    /// Body of request, if any
    /// </summary>
    public byte[] Body { get; set; } = [];

    /// <summary>
    /// HTTP request headers
    /// </summary>
    public Dictionary<string, string> Headers { get; set; } = [];

    /// <summary>
    /// Map a CoreWebView2WebResourceRequest into a generic request
    /// </summary>
    public static ResourceRequest FromCoreWebViewRequest(CoreWebView2WebResourceRequest request)
    {
        var result = new ResourceRequest
        {
            Uri = request.Uri,
            Headers = request.Headers.ToDictionary(),
            Method = request.Method
        };

        if (request.Content is not null)
        {
            using var ms = new MemoryStream();
            request.Content.CopyTo(ms);
            ms.Seek(0, SeekOrigin.Begin);
            result.Body = ms.ToArray();
        }

        return result;
    }
}