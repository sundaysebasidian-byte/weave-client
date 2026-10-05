using System.Globalization;
using System.Text.Json;

namespace Weave.Windows.Core;

/// <summary>Only application-owned text is translated. Interpolation arguments stay untouched.</summary>
public static class L
{
    private static readonly IReadOnlyDictionary<string, string> English = Load();
    private static string _language = "zh-CN";
    public static event EventHandler? Changed;
    public static string Language
    {
        get => _language;
        set
        {
            var normalized = value == "en" ? "en" : "zh-CN";
            if (_language == normalized) return;
            _language = normalized;
            Changed?.Invoke(null, EventArgs.Empty);
        }
    }
    public static IReadOnlyDictionary<string, string> Catalog => English;
    public static string Translate(string source, string language) => language == "en" && English.TryGetValue(source, out var translated) ? translated : source;
    public static string T(string source) => Translate(source, Language);
    public static string F(FormattableString message) => Format(message, Language);
    public static string Format(FormattableString message, string language) => string.Format(
        CultureInfo.GetCultureInfo(language == "en" ? "en-US" : "zh-CN"), Translate(message.Format, language), message.GetArguments());
    private static IReadOnlyDictionary<string, string> Load()
    {
        using var stream = typeof(L).Assembly.GetManifestResourceStream("Weave.Windows.Core.Strings.en.json")
            ?? throw new InvalidOperationException("Missing localization catalog");
        return JsonSerializer.Deserialize<Dictionary<string, string>>(stream)!;
    }
}
