using System.Windows;
using Microsoft.Win32;
using MiniWeb.Core;

namespace MiniWeb;


/// <summary>
/// Entry point to a MiniWeb view
/// </summary>
// ReSharper disable once RedundantExtendsListEntry
public partial class SelfHostWebWindow : Window
{
    private static readonly Lock _lock = new();

    /// <summary>
    /// App start-up time
    /// </summary>
    public static readonly DateTime BootTime = DateTime.UtcNow;

    /// <summary>
    /// First host window, if one has been opened
    /// </summary>
    public static Host? FirstHost { get; private set; }

    /// <summary>
    /// Web-view host connected to the view
    /// </summary>
    public Host Host { get; set; }

    private readonly string? _startUrl;

    /// <summary>
    /// Start main window
    /// </summary>
    public SelfHostWebWindow()
    {
        _startUrl = null;
        InitializeComponent();

        DismissButton.Visibility = Visibility.Hidden;
        DismissButton.Click += DismissClick;
        Host = new Host(Web, this);

        lock (_lock) { FirstHost ??= Host; }
    }

    /// <summary>
    /// Start secondary window with an initial URL
    /// </summary>
    public SelfHostWebWindow(string url)
    {
        _startUrl = url;
        InitializeComponent();

        DismissButton.Visibility = Visibility.Hidden;
        DismissButton.Click += DismissClick;
        Host = new Host(Web, this);

        lock (_lock) { FirstHost ??= Host; }
    }

    private void DismissClick(object sender, RoutedEventArgs e)
    {
        Web.Visibility = Visibility.Visible; // Restore the main web view
    }

    /// <inheritdoc />
    protected override async void OnSourceInitialized(EventArgs e)
    {
        try
        {
            await Host.Initialise(_startUrl);
        }
        catch (Exception ex)
        {
            Console.WriteLine(ex);
            Environment.Exit(123);
        }
    }

    /// <summary>
    /// Hide the WebView, and show a message for the user to dismiss
    /// </summary>
    public void ShowModalMessage(string message, string header)
    {
        Dispatcher.Invoke(() =>
        {
            try
            {
                MessageHeader.Text = header;
                MessageBody.Text = message;

                DismissButton.Visibility = Visibility.Visible;

                Web.Visibility = Visibility.Hidden; // Hide the web view, so the modal message can be seen

                InvalidateVisual();
            }
            catch (Exception ex)
            {
                Console.WriteLine("Failed to show modal box!" + ex);
                MessageBox.Show(message, header);
            }
        });
    }

    private const string RegistryKeyPath = @"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize";
    private const string RegistryValueName = "AppsUseLightTheme";

    /// <summary>
    /// Detect if user has a dark-mode theme
    /// </summary>
    public bool InDarkMode()
    {
        const bool dark  = true;
        const bool light = false;

        using var key = Registry.CurrentUser.OpenSubKey(RegistryKeyPath);

        var registryValueObject = key?.GetValue(RegistryValueName);
        if (registryValueObject == null) return light;

        var registryValue = (int)registryValueObject;

        // ReSharper disable once SimplifyConditionalTernaryExpression
        return registryValue > 0 ? light : dark;
    }
}