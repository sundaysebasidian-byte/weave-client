namespace Weave.Windows.Core;

// Kept in Android display order except for the original Windows three indices,
// which remain stable for existing installations.
public sealed record AppearancePalette(string Name, string Canvas, string Paper, string Ink,
    string Muted, string Accent, string Tint, bool Dark = false)
{
    public static IReadOnlyList<AppearancePalette> All { get; } = new[]
    {
        new AppearancePalette("浅色", "DFEAF0", "F8FCFF", "172D3D", "526773", "187A8B", "B9DDE3"),
        new AppearancePalette("白绿", "FFFFFF", "F9FAF8", "151813", "646960", "2C6E16", "EAF2E6"),
        new AppearancePalette("深色", "080F18", "15232E", "F3F8FC", "AABCCB", "9ADFE9", "29454F", true),
        new AppearancePalette("素纸", "FFFFFF", "FAFAFA", "171717", "606060", "252525", "ECECEC"),
        new AppearancePalette("印象日出", "F2ECE2", "FFF9F0", "3E5875", "747986", "527C74", "B8AAC5"),
        new AppearancePalette("睡莲", "EDF1EE", "FAFCF8", "405D6B", "6D7A83", "4F7D75", "AAA1C3"),
        new AppearancePalette("罂粟花田", "F3ECE3", "FFF9F0", "5A5260", "7D7475", "647B67", "B8A5BD"),
        new AppearancePalette("暮色花园", "F0EBF0", "FCF8F1", "3C456E", "76758A", "5B7780", "B59DBC"),
    };
}
