package e.s.miniweb.core;

import org.json.JSONObject;

/// <summary>
/// Interface for page script callbacks
/// </summary>
public interface PageInteractions {
    /// <summary>
    /// Handle calls from Javascript through <c>window.chrome.webview.postMessage</c>.
    /// Return <c>true</c> if you handle the request, or <c>false</c> to fall through to the default handler.
    /// </summary>
    public boolean HandlePageRequest(Host host, JSONObject message);

    /** Called when the user navigates away from a page that was loaded */
    public void PageClosed(Host host);
}
