using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Weave.Windows.Core;
using WinColor = global::Windows.UI.Color;

namespace Weave.Windows;

/// <summary>
/// Theme and light/dark. The user picks one of six themes and, separately, follows Windows or pins light or dark. Applying a choice
/// rewrites the shared brushes in App.xaml in place (both modes, so ThemeResource lookups are right either way), then flips
/// RequestedTheme; nothing is rebuilt and the proxy session is untouched.
/// </summary>
public sealed partial class MainWindow
{
    private AppearanceSetting _appearance = AppearanceSetting.Default;
    private bool _appearanceDark;
    private bool _applyingAppearance;
    private readonly List<ThemeChoice> _themeChoices = new();
    private global::Windows.UI.ViewManagement.UISettings? _uiSettings;

    private void InitializeAppearanceControls()
    {
        ModeSelector.ItemsSource = new[] { L.T("跟随系统"), L.T("浅色"), L.T("深色") };
        CloseBehaviorSelector.ItemsSource = new[] { L.T("最小化到托盘"), L.T("退出程序") };
        for (var i = 0; i < Appearance.Themes.Count; i++) _themeChoices.Add(new ThemeChoice(i, Appearance.Themes[i], false));
        ThemeGallery.ItemsSource = _themeChoices;
    }

    private void RefreshAppearanceLanguage()
    {
        RefreshOptions(ModeSelector, new[] { L.T("跟随系统"), L.T("浅色"), L.T("深色") });
        RefreshOptions(CloseBehaviorSelector, new[] { L.T("最小化到托盘"), L.T("退出程序") });
        foreach (var choice in _themeChoices) choice.RefreshLanguage();
    }

    private void LoadAndApplyAppearance()
    {
        try { _appearance = AppearanceSetting.Parse(File.Exists(_themePath) ? File.ReadAllText(_themePath) : null); }
        catch (IOException) { }
        catch (UnauthorizedAccessException) { }
        // Preview and capture overrides.
        if (int.TryParse(Environment.GetEnvironmentVariable("WEAVE_PREVIEW_THEME"), out var theme) && theme >= 0 && theme < Appearance.Themes.Count)
            _appearance = _appearance with { Theme = theme };
        if (Enum.TryParse<AppearanceMode>(Environment.GetEnvironmentVariable("WEAVE_PREVIEW_MODE"), true, out var mode))
            _appearance = _appearance with { Mode = mode };
        try
        {
            _uiSettings = new global::Windows.UI.ViewManagement.UISettings();
            // Raised on a worker thread when the user changes Windows' light/dark or accent settings.
            _uiSettings.ColorValuesChanged += (_, _) => DispatcherQueue.TryEnqueue(() => { if (!_closed && _appearance.Mode == AppearanceMode.System) ApplyAppearance(false); });
        }
        catch (Exception) { _uiSettings = null; }
        ApplyAppearance(false);
    }

    private bool SystemIsDark()
    {
        try
        {
            if (_uiSettings is null) return false;
            var c = _uiSettings.GetColorValue(global::Windows.UI.ViewManagement.UIColorType.Background);
            return c.R + c.G + c.B < 384;
        }
        catch (Exception) { return false; }
    }

    private void ApplyAppearance(bool save)
    {
        var theme = Appearance.Themes[_appearance.Theme];
        _appearanceDark = _appearance.ResolveDark(SystemIsDark());
        SetThemeBrushes(false, theme.Light);
        SetThemeBrushes(true, theme.Dark);
        RootGrid.RequestedTheme = _appearanceDark ? ElementTheme.Dark : ElementTheme.Light;
        var colors = theme.For(_appearanceDark);
        ApplyCaptionColors(Rgb(colors.Ink), Rgb(colors.Muted));
        // With Mica the base is translucent so the wallpaper tints the window; captures and non-Mica systems get an opaque base.
        BackdropFallback.Opacity = _micaActive && !CaptureRequested ? (_appearanceDark ? 0.86 : 0.80) : 1;

        _applyingAppearance = true;
        try
        {
            foreach (var choice in _themeChoices) choice.SetDark(_appearanceDark);
            ThemeGallery.SelectedIndex = _appearance.Theme;
            ModeSelector.SelectedIndex = (int)_appearance.Mode;
        }
        finally { _applyingAppearance = false; }
        ThemeToggleGlyph.Glyph = _appearanceDark ? "" : ""; // sun: go light / moon: go dark
        UpdateKnotPalette();
        UpdateNavigation();
        UpdateDashboard();
        if (!save) return;
        try { Directory.CreateDirectory(Path.GetDirectoryName(_themePath)!); File.WriteAllText(_themePath, _appearance.Serialize()); }
        catch (IOException) { MessageText.Text = L.T("外观已切换，但偏好未能保存。"); }
        catch (UnauthorizedAccessException) { MessageText.Text = L.T("外观已切换，但偏好未能保存。"); }
    }

    private static WinColor Rgb(string hex, double opacity = 1)
    {
        var (r, g, b, a) = Appearance.ParseHex(hex);
        return WinColor.FromArgb((byte)Math.Round(a * opacity), r, g, b);
    }

    private static WinColor Blend(string from, string to, double amount)
    {
        var (r1, g1, b1, _) = Appearance.ParseHex(from); var (r2, g2, b2, _) = Appearance.ParseHex(to);
        return WinColor.FromArgb(255, (byte)(r1 + (r2 - r1) * amount), (byte)(g1 + (g2 - g1) * amount), (byte)(b1 + (b2 - b1) * amount));
    }

    private static void SetThemeBrushes(bool dark, ThemeColors c)
    {
        var d = ThemeDictionary(dark);
        void Solid(string key, WinColor value) => ((SolidColorBrush)d[key]).Color = value;
        void Gradient(string key, params WinColor[] stops)
        {
            IList<GradientStop> collection = d[key] switch
            {
                LinearGradientBrush linear => linear.GradientStops,
                RadialGradientBrush radial => radial.GradientStops,
                _ => throw new InvalidOperationException(key + " is not a gradient brush"),
            };
            for (var i = 0; i < stops.Length; i++) collection[i].Color = stops[i];
        }
        const string White = "FFFFFF", Black = "000000";
        Solid("WeaveCanvasBrush", Rgb(c.Base));
        Solid("WeaveCardBrush", Blend(c.Base, dark ? White : White, dark ? 0.06 : 0.55));
        Solid("WeavePopBrush", Blend(c.Base, White, dark ? 0.10 : 0.62));
        Solid("WeaveInkBrush", Rgb(c.Ink));
        Solid("WeaveMutedBrush", Rgb(c.Muted));
        Solid("WeaveFaintBrush", Rgb(c.Faint));
        Solid("WeaveAccentBrush", Rgb(c.Accent));
        Solid("WeaveAccent2Brush", Rgb(c.Accent2));
        Solid("WeaveSunBrush", Rgb(c.Sun));
        Solid("WeaveRimBrush", Rgb(c.Ink, 0.10));
        Solid("WeaveHairBrush", Rgb(c.Ink, 0.10));
        Solid("WeaveSubtleFillBrush", Rgb(c.Ink, dark ? 0.08 : 0.06));
        Solid("WeaveSubtlePressedBrush", Rgb(c.Ink, dark ? 0.14 : 0.11));
        Solid("WeaveSelectedBrush", dark ? Rgb(White, 0.10) : Rgb(White, 0.85));
        Solid("WeaveTileBrush", dark ? Rgb(White, 0.05) : Rgb(White, 0.50));
        Solid("WeaveTileStrokeBrush", dark ? Rgb(White, 0.09) : Rgb(White, 0.80));
        Solid("WeaveTileOuterBrush", dark ? Rgb(White, 0) : Rgb(c.Ink, 0.07));
        Solid("WeaveSuccessBrush", Rgb(c.Success));
        Solid("WeaveCautionBrush", Rgb(c.Caution));
        Solid("WeaveDangerBrush", Rgb(c.Danger));
        Solid("WeaveActionTextBrush", Rgb(c.OnAccent));
        Solid("WeaveAccentFillBrush", Rgb(c.Accent));
        Solid("WeaveAccentFillHoverBrush", Blend(c.Accent, White, 0.10));
        Solid("WeaveAccentFillPressedBrush", Rgb(c.Accent2));
        Gradient("WeaveActionBrush", Rgb(c.Accent), Rgb(c.Accent2));
        Gradient("WeaveActionHoverBrush", Blend(c.Accent, White, 0.10), Blend(c.Accent2, White, 0.10));
        Gradient("WeaveActionPressedBrush", Rgb(c.Accent2), Blend(c.Accent2, Black, 0.14));
        for (var i = 0; i < 3; i++)
        {
            var start = Rgb(c.Wash[i]); var clear = start; clear.A = 0;
            Gradient("WeaveWash" + i + "Brush", start, clear);
        }
        var area = Rgb(c.Knot[1]); area.A = 0x6B; var none = area; none.A = 0;
        Gradient("WeaveChartAreaBrush", area, none);
        Gradient("WeaveChartLineBrush", Rgb(c.Knot[0]), Rgb(c.Knot[2]));
        Solid("WeaveChartDotBrush", Rgb(c.Knot[2]));
    }

    // The tokens live in a dictionary merged after XamlControlsResources so they outrank the stock control resources.
    private static ResourceDictionary ThemeDictionary(bool dark)
    {
        var name = dark ? "Dark" : "Light";
        foreach (var merged in Application.Current.Resources.MergedDictionaries)
            if (merged.ThemeDictionaries.TryGetValue(name, out var found) && found is ResourceDictionary rd && rd.ContainsKey("WeaveCanvasBrush")) return rd;
        return (ResourceDictionary)Application.Current.Resources.ThemeDictionaries[name];
    }

    private Brush ThemeBrush(string key) => (Brush)ThemeDictionary(_appearanceDark)[key];

    private ThemeColors CurrentColors => Appearance.Themes[_appearance.Theme].For(_appearanceDark);

    private void UpdateKnotPalette()
    {
        var c = CurrentColors;
        _knotPalette = new KnotPalette(c.Knot[0], c.Knot[1], c.Knot[2], c.Ink, c.Sun, c.Muted, _appearanceDark);
        _dashState = -1; // force the knot to be redrawn in the new colours
    }

    private void ModeSelector_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_initialized || _applyingAppearance || ModeSelector.SelectedIndex < 0 || ModeSelector.SelectedIndex == (int)_appearance.Mode) return;
        _appearance = _appearance with { Mode = (AppearanceMode)ModeSelector.SelectedIndex };
        ApplyAppearance(true);
    }

    private void ThemeGallery_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_initialized || _applyingAppearance || ThemeGallery.SelectedIndex < 0 || ThemeGallery.SelectedIndex == _appearance.Theme) return;
        _appearance = _appearance with { Theme = ThemeGallery.SelectedIndex };
        ApplyAppearance(true);
    }

    private void ThemeToggle_Click(object sender, RoutedEventArgs e)
    {
        _appearance = _appearance with { Mode = _appearanceDark ? AppearanceMode.Light : AppearanceMode.Dark };
        ApplyAppearance(true);
    }

    private void CloseBehavior_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (!_initialized || CloseBehaviorSelector.SelectedIndex < 0) return;
        _closeToTray = CloseBehaviorSelector.SelectedIndex == 0;
        try { Directory.CreateDirectory(AppPaths.DataRoot); File.WriteAllText(Path.Combine(AppPaths.DataRoot, "close-behavior.txt"), _closeToTray ? "tray" : "exit"); }
        catch (IOException) { }
        catch (UnauthorizedAccessException) { }
    }

    private bool _closeToTray = true;

    private void LoadCloseBehavior()
    {
        try { _closeToTray = !File.Exists(Path.Combine(AppPaths.DataRoot, "close-behavior.txt")) || File.ReadAllText(Path.Combine(AppPaths.DataRoot, "close-behavior.txt")).Trim() != "exit"; }
        catch (IOException) { }
        catch (UnauthorizedAccessException) { }
        CloseBehaviorSelector.SelectedIndex = _closeToTray ? 0 : 1;
    }

    // Keeps the status pill clear of the system caption buttons, whatever their width at this DPI.
    private void UpdateTitleBarInset()
    {
        try
        {
            var scale = RootGrid.XamlRoot?.RasterizationScale ?? 1.0;
            var inset = AppWindow.TitleBar.RightInset / scale;
            TitleBarContent.Margin = new Thickness(0, 0, Math.Max(0, inset) + 12, 0);
        }
        catch (Exception) { /* The fixed margin from XAML is a fine fallback. */ }
    }
}
