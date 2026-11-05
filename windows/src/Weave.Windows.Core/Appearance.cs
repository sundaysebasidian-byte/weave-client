using System.Globalization;

namespace Weave.Windows.Core;

public enum AppearanceMode { System, Light, Dark }

/// <summary>
/// One palette. Colours are hex without '#': RRGGBB, except <see cref="Wash"/> which is RRGGBBAA (soft background washes).
/// Everything the UI draws derives from these few values, so a theme is one screenful of data.
/// </summary>
public sealed record ThemeColors(string Base, IReadOnlyList<string> Wash, string Ink, string Muted, string Faint,
    string Accent, string Accent2, string OnAccent, string Sun, IReadOnlyList<string> Knot,
    string Success, string Caution, string Danger);

public sealed record AppearanceTheme(string Name, ThemeColors Light, ThemeColors Dark)
{
    public ThemeColors For(bool dark) => dark ? Dark : Light;
}

/// <summary>The user's choice: which theme and whether it follows Windows, or is pinned to light or dark.</summary>
public readonly record struct AppearanceSetting(int Theme, AppearanceMode Mode)
{
    public static AppearanceSetting Default { get; } = new(0, AppearanceMode.System);

    public bool ResolveDark(bool systemIsDark) => Mode switch
    {
        AppearanceMode.Light => false,
        AppearanceMode.Dark => true,
        _ => systemIsDark,
    };

    public string Serialize() => $"v2:{Theme}:{Mode.ToString().ToLowerInvariant()}";

    /// <summary>
    /// Reads the saved text. The previous release stored a single palette index (0-7); it maps to the nearest new theme
    /// and keeps its light/dark character so an existing install does not suddenly flip brightness.
    /// </summary>
    public static AppearanceSetting Parse(string? text)
    {
        text = text?.Trim();
        if (string.IsNullOrEmpty(text)) return Default;
        if (text.StartsWith("v2:", StringComparison.Ordinal))
        {
            var parts = text.Split(':');
            if (parts.Length == 3 && int.TryParse(parts[1], NumberStyles.None, CultureInfo.InvariantCulture, out var theme) &&
                theme < Appearance.Themes.Count && Enum.TryParse<AppearanceMode>(parts[2], true, out var mode) &&
                Enum.IsDefined(mode))
                return new(theme, mode);
            return Default;
        }
        if (int.TryParse(text, NumberStyles.None, CultureInfo.InvariantCulture, out var legacy) && legacy < Appearance.LegacyMap.Length)
            return Appearance.LegacyMap[legacy];
        return Default;
    }
}

public static class Appearance
{
    // Old palette index -> new setting: 浅色, 白绿, 深色, 素纸, 印象日出, 睡莲, 罂粟花田, 暮色花园.
    internal static readonly AppearanceSetting[] LegacyMap =
    {
        new(0, AppearanceMode.Light), new(1, AppearanceMode.Light), new(4, AppearanceMode.Dark), new(5, AppearanceMode.Light),
        new(0, AppearanceMode.Light), new(1, AppearanceMode.Light), new(2, AppearanceMode.Light), new(3, AppearanceMode.Light),
    };

    // Design notes: one dominant hue per theme, a single warm or bright "sun" accent used sparingly, and ribbon colours that
    // stay within the theme's family. Dark palettes are deeper and greyer, never neon. Muted text is tuned to >= 4.5:1.
    public static IReadOnlyList<AppearanceTheme> Themes { get; } = new[]
    {
        new AppearanceTheme("印象日出",
            new("F3EDE3", new[] { "E8C9AD99", "B9C7D673", "CBC4D859" }, "27324F", "5E657F", "A3A7B8", "2F3A64", "232C50", "FFFFFF", "CF6A50",
                new[] { "2B3866", "7BA3A3", "A599C2" }, "0F7B0F", "9D5D00", "C42B1C"),
            new("14161D", new[] { "4A2D2299", "232C4A99", "2E243666" }, "EFE8DE", "A2A5B4", "5B5F70", "E3A47E", "D38E66", "2A1710", "E5775B",
                new[] { "6E81BD", "5E9C98", "9F8FC2" }, "6CCB5F", "FCE100", "FF99A4")),
        new AppearanceTheme("睡莲",
            new("EBF0EB", new[] { "BFD6C899", "CFCADB66", "C2D3DE66" }, "213A38", "566E6B", "A0B2AE", "2B5A55", "1F4642", "FFFFFF", "C4768A",
                new[] { "21504B", "85B6A6", "A79BC4" }, "0F7B0F", "9D5D00", "C42B1C"),
            new("0F1615", new[] { "1F3D3A99", "2B2A4488", "1B2F3E88" }, "E4EEE9", "93AAA4", "51635F", "8CC9B5", "6FB29D", "08201A", "DE8A9E",
                new[] { "3F8C82", "7FB8A4", "9A8DC4" }, "6CCB5F", "FCE100", "FF99A4")),
        new AppearanceTheme("罂粟花田",
            new("F2EDE2", new[] { "E7D5A899", "C4CFB673", "E9C8B766" }, "33372D", "666A58", "ABAE9E", "4A5A3D", "37452F", "FFFFFF", "C4452D",
                new[] { "4C5F3F", "C8AC68", "BE5C43" }, "0F7B0F", "9D5D00", "C42B1C"),
            new("15150F", new[] { "3A362199", "26301F99", "3E221866" }, "EEEADD", "A3A28E", "5F5E50", "D8B36B", "C39D55", "231A06", "E4583F",
                new[] { "7C9268", "BFA45F", "BD6A52" }, "6CCB5F", "FCE100", "FF99A4")),
        new AppearanceTheme("暮色花园",
            new("EEEBEF", new[] { "D0C6DD99", "E5C6D073", "BEC9DD66" }, "2D2A48", "63617F", "ADACC0", "3E3A70", "2C2956", "FFFFFF", "B66E8A",
                new[] { "2C2957", "7B6EAC", "C69AB3" }, "0F7B0F", "9D5D00", "C42B1C"),
            new("121120", new[] { "2B215299", "3A203873", "1E2A4A88" }, "EBE8F5", "9F9EBC", "5A5974", "B6A7EE", "9E8FDC", "17123A", "D78AA6",
                new[] { "6F66BE", "9A87C8", "C18BAB" }, "6CCB5F", "FCE100", "FF99A4")),
        new AppearanceTheme("星夜",
            new("ECEEF2", new[] { "C7D1E599", "E6DDB866", "D6D3E666" }, "1D2547", "5A6485", "A5ABC0", "242F63", "18214D", "FFFFFF", "B38E3A",
                new[] { "1E2A64", "5E86B6", "B79A57" }, "0F7B0F", "9D5D00", "C42B1C"),
            new("0E1222", new[] { "1E2C6699", "123A4A66", "2C226088" }, "EBEDF8", "98A2C8", "555E80", "E3C169", "CEA84F", "1E1704", "EFD179",
                new[] { "6A82D0", "62A8A6", "A596D0" }, "6CCB5F", "FCE100", "FF99A4")),
        new AppearanceTheme("素纸",
            new("F4F3F0", new[] { "E2DED699", "D8DBE066", "E6E2DA59" }, "1C1C20", "66666D", "B0B0B5", "232327", "121214", "FFFFFF", "C2402F",
                new[] { "1E1E23", "6E7078", "B8B4A9" }, "0F7B0F", "9D5D00", "C42B1C"),
            new("111113", new[] { "2A2A2F99", "1E1E2266", "26231F66" }, "F0EFEB", "9A9AA0", "56565C", "EDEBE3", "CDCAC1", "141416", "E0523E",
                new[] { "E4E2DC", "8D8D95", "55555E" }, "6CCB5F", "FCE100", "FF99A4")),
    };

    public static (byte R, byte G, byte B, byte A) ParseHex(string hex) => (
        Convert.ToByte(hex[..2], 16), Convert.ToByte(hex.Substring(2, 2), 16), Convert.ToByte(hex.Substring(4, 2), 16),
        hex.Length >= 8 ? Convert.ToByte(hex.Substring(6, 2), 16) : (byte)255);

    public static double Luminance(string hex)
    {
        var (r, g, b, _) = ParseHex(hex);
        static double Channel(byte value) { var s = value / 255.0; return s <= 0.03928 ? s / 12.92 : Math.Pow((s + 0.055) / 1.055, 2.4); }
        return 0.2126 * Channel(r) + 0.7152 * Channel(g) + 0.0722 * Channel(b);
    }

    /// <summary>WCAG contrast ratio between two opaque hex colours.</summary>
    public static double Contrast(string a, string b)
    {
        var x = Luminance(a); var y = Luminance(b);
        return (Math.Max(x, y) + 0.05) / (Math.Min(x, y) + 0.05);
    }
}
