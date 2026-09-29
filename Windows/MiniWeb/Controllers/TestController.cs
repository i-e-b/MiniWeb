using MiniWeb.Core;

namespace MiniWeb.Controllers;

/// <summary>
/// More examples
/// </summary>
public class TestController : ControllerBase, IPageInteractions
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