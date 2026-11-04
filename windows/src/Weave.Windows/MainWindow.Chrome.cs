using Microsoft.UI.Xaml;
using WinRT.Interop;
using WinColor = global::Windows.UI.Color;

namespace Weave.Windows;

/// <summary>Window chrome: custom title bar, Mica backdrop with a solid fallback, icon, minimum size, caption button colours.</summary>
public sealed partial class MainWindow
{
    private bool _micaActive;
    private WindowMinSize? _minSize;

    private void InitializeChrome()
    {
        ExtendsContentIntoTitleBar = true;
        SetTitleBar(AppTitleBar);
        try
        {
            if (Microsoft.UI.Windowing.AppWindowTitleBar.IsCustomizationSupported())
                AppWindow.TitleBar.PreferredHeightOption = Microsoft.UI.Windowing.TitleBarHeightOption.Tall;
        }
        catch (Exception) { /* Standard-height caption buttons are an acceptable fallback. */ }
        try
        {
            // Windows 11 only. Elsewhere (or with transparency effects unavailable) the opaque canvas layer stays.
            if (Microsoft.UI.Composition.SystemBackdrops.MicaController.IsSupported())
            {
                SystemBackdrop = new Microsoft.UI.Xaml.Media.MicaBackdrop { Kind = Microsoft.UI.Composition.SystemBackdrops.MicaKind.BaseAlt };
                _micaActive = true;
            }
        }
        catch (Exception) { _micaActive = false; }
        BackdropFallback.Visibility = _micaActive ? Visibility.Collapsed : Visibility.Visible;
        try
        {
            var icon = Path.Combine(AppContext.BaseDirectory, "Assets", "Weave.ico");
            if (File.Exists(icon)) AppWindow.SetIcon(icon);
        }
        catch (Exception) { /* The executable's own icon resource is still used. */ }
        _minSize = new WindowMinSize(WindowNative.GetWindowHandle(this), 480, 520);
    }

    // Caption buttons follow the palette, not the system theme: transparent at rest, ink-tinted on hover/press.
    private void ApplyCaptionColors(WinColor ink, WinColor muted)
    {
        try
        {
            if (!Microsoft.UI.Windowing.AppWindowTitleBar.IsCustomizationSupported()) return;
            var bar = AppWindow.TitleBar;
            var clear = Microsoft.UI.Colors.Transparent;
            bar.ButtonBackgroundColor = clear;
            bar.ButtonInactiveBackgroundColor = clear;
            bar.ButtonForegroundColor = ink;
            bar.ButtonInactiveForegroundColor = muted;
            bar.ButtonHoverForegroundColor = ink;
            bar.ButtonPressedForegroundColor = ink;
            bar.ButtonHoverBackgroundColor = WinColor.FromArgb(0x24, ink.R, ink.G, ink.B);
            bar.ButtonPressedBackgroundColor = WinColor.FromArgb(0x3D, ink.R, ink.G, ink.B);
        }
        catch (Exception) { /* Caption colours are cosmetic. */ }
    }
}
