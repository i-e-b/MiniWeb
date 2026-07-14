namespace MiniWeb.Core;

/// <summary>
/// Interface for page script callbacks
/// </summary>
public interface IPageInteractions
{
    /// <summary>
    /// Handle calls from Javascript through <c>window.chrome.webview.postMessage</c>.
    /// Return <c>true</c> if you handle the request, or <c>false</c> to fall through to the default handler.
    /// </summary>
    public Task<bool> HandlePageRequest(Host host, string jsonStr);

    /// <summary>
    /// Called when the user navigates away from a page that was loaded
    /// </summary>
    // ReSharper disable once UnusedParameter.Global
    public Task PageClosed(Host host);
}