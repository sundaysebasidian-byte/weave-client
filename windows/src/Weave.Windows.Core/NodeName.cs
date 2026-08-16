namespace Weave.Windows.Core;

public static class NodeName
{
    public static string Core(string rawName)
    {
        var original = rawName.Trim();
        if (original.Length == 0)
        {
            return "未命名节点";
        }

        var value = original;
        while (true)
        {
            var match = EscapedPrefix.Match(value);
            if (!match.Success)
            {
                break;
            }

            value = value[match.Length..];
        }

        var offset = 0;
        while (offset < value.Length)
        {
            var codePoint = char.ConvertToUtf32(value, offset);
            if (!IsDecoration(codePoint))
            {
                break;
            }

            offset += char.IsSurrogatePair(value, offset) ? 2 : 1;
        }

        if (offset > 0)
        {
            value = value[offset..].TrimStart(' ', '\t', '·', '|', '-', '_', ':', '：');
        }

        return string.IsNullOrWhiteSpace(value) ? original : value.Trim();
    }

    private static bool IsDecoration(int codePoint) =>
        codePoint is >= 0x1F000 and <= 0x1FAFF ||
        codePoint is >= 0x2300 and <= 0x27FF ||
        codePoint is >= 0xE0020 and <= 0xE007F ||
        codePoint is 0x200D or 0xFE0F or 0x20E3;

    private static readonly System.Text.RegularExpressions.Regex EscapedPrefix = new(
        @"^(?:(?:\\u[0-9a-fA-F]{4,8}|\\U[0-9a-fA-F]{8})\s*)+",
        System.Text.RegularExpressions.RegexOptions.Compiled | System.Text.RegularExpressions.RegexOptions.CultureInvariant);
}
