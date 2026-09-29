using MiniWeb.Core;

namespace MiniWeb.Controllers;

/// <summary>
/// Various example pages
/// </summary>
public class ExamplesController : ControllerBase, IPageInteractions
{
    /// <inheritdoc />
    public override void BindMethods(ControllerBinding bind)
    {

    }

    /// <inheritdoc />
    public async Task<bool> HandlePageRequest(Host host, string jsonStr)
    {
        return false;
    }

    /// <inheritdoc />
    public async Task PageClosed(Host host)
    {
    }
}