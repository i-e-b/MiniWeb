using System.IO;

namespace MiniWeb.Core;

/// <summary>
/// Load data streams from virtual paths
/// </summary>
public interface IAssetLoader
{
    /// <summary>
    /// Provide the resource at the given path as a readable stream.
    /// Returns <c>null</c> if the resource does not exist
    /// </summary>
    public Stream? LoadAsset(string path);
}

/// <summary>
/// Load data from a network request
/// </summary>
public interface INetworkRequest
{
    /// <summary>
    /// Reply with body string for a network request.
    /// Return <c>null</c> if call fails or is rejected.
    /// </summary>
    string? GetResponse(string url, object? requestModel);
}

/// <summary>
/// Check for permission sets
/// </summary>
public interface IPermissionsCheck
{
    /// <summary>
    /// Return <c>true</c> to allow the current user to view content based on
    /// tags in <paramref name="required"/>.
    /// If <c>false</c> is returned, user will not be shown content.
    /// </summary>
    bool GrantPermission(string[] required);
}