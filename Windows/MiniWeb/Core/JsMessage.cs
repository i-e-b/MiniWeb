using JetBrains.Annotations;

namespace MiniWeb.Core;

/// <summary>
/// Message from web page to Host
/// </summary>
[UsedImplicitly]
public class JsMessage
{
    /// <summary>
    /// Action to perform
    /// </summary>
    public string? Action { get; set; }

    /// <summary>
    /// Data for the action (usually Base64 encoded)
    /// </summary>
    public string? Data { get; set; }

    /// <summary>
    /// Additional parameters
    /// </summary>
    public Dictionary<string, object?> Parameters { get; set; } = [];

    /// <summary>
    /// Try to get a parameter from the message.
    /// Returns <c>null</c> if no such parameter was given
    /// </summary>
    public object? GetParameter(string key)
    {
        return Parameters.GetValueOrDefault(key, null);
    }
}