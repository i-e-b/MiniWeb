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
        throw new NotImplementedException();
    }

    /// <inheritdoc />
    public Task<bool> HandlePageRequest(Host host, string jsonStr)
    {
        throw new NotImplementedException();
    }

    /// <inheritdoc />
    public Task PageClosed(Host host)
    {
        throw new NotImplementedException();
    }
}