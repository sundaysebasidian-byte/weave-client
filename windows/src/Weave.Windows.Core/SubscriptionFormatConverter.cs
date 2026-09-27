using System.Text;
using System.Text.Json;

namespace Weave.Windows.Core;

internal static class SubscriptionFormatConverter
{
    private static readonly UTF8Encoding StrictUtf8 = new(false, true);

    public static string Normalize(string input)
    {
        if (string.IsNullOrWhiteSpace(input))
        {
            throw new InvalidDataException("订阅内容为空");
        }

        return NormalizeOnce(input.Trim().TrimStart('\uFEFF'), allowBase64: true);
    }

    private static string NormalizeOnce(string payload, bool allowBase64)
    {
        if (payload.StartsWith("<!doctype html", StringComparison.OrdinalIgnoreCase) ||
            payload.StartsWith("<html", StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidDataException("订阅地址返回的是网页，不是节点配置");
        }

        if (payload.StartsWith('{'))
        {
            return ProxyYamlWriter.Render(JsonSubscriptionConverter.Parse(payload));
        }

        var firstLine = payload.Split('\n').Select(line => line.Trim())
            .FirstOrDefault(line => line.Length > 0 && !line.StartsWith('#')) ?? string.Empty;
        if (firstLine.Contains("://", StringComparison.Ordinal))
        {
            return ProxyYamlWriter.Render(UriSubscriptionConverter.Parse(payload));
        }

        if (payload.Contains("proxies:", StringComparison.OrdinalIgnoreCase))
        {
            return payload;
        }

        if (allowBase64)
        {
            try
            {
                var compact = new string(payload.Where(character => !char.IsWhiteSpace(character)).ToArray())
                    .Replace('-', '+').Replace('_', '/');
                compact = compact.PadRight(compact.Length + ((4 - compact.Length % 4) % 4), '=');
                var decoded = StrictUtf8.GetString(Convert.FromBase64String(compact));
                return NormalizeOnce(decoded.Trim().TrimStart('\uFEFF'), allowBase64: false);
            }
            catch (FormatException)
            {
                // Report the same actionable error for malformed Base64 and unknown text.
            }
            catch (DecoderFallbackException)
            {
                throw new InvalidDataException("订阅文本不是有效 UTF-8");
            }
        }

        throw new InvalidDataException("未识别到支持的 Clash YAML、节点 URI 或 JSON 订阅");
    }
}

internal sealed class ProxySpec
{
    public ProxySpec(string name, string type, string server, int port)
    {
        Name = name;
        Type = type;
        Fields["server"] = server;
        Fields["port"] = port;
    }

    public string Name { get; }
    public string Type { get; }
    public Dictionary<string, object> Fields { get; } = new(StringComparer.Ordinal);

    public Dictionary<string, object> Nested(string name)
    {
        if (!Fields.TryGetValue(name, out var existing))
        {
            existing = new Dictionary<string, object>(StringComparer.Ordinal);
            Fields[name] = existing;
        }

        return (Dictionary<string, object>)existing;
    }
}

internal static class ProxyYamlWriter
{
    public static string Render(IReadOnlyList<ProxySpec> specs)
    {
        if (specs.Count == 0)
        {
            throw new InvalidDataException("订阅中没有可转换的代理节点");
        }

        var builder = new StringBuilder("proxies:\n");
        foreach (var spec in specs)
        {
            builder.Append("  - name: ").AppendLine(Quote(spec.Name));
            builder.Append("    type: ").AppendLine(spec.Type);
            foreach (var (key, value) in spec.Fields)
            {
                WriteValue(builder, key, value, 4);
            }
        }

        return builder.ToString();
    }

    private static void WriteValue(StringBuilder builder, string key, object value, int indent)
    {
        builder.Append(' ', indent).Append(key).Append(':');
        if (value is Dictionary<string, object> nested)
        {
            builder.AppendLine();
            foreach (var (nestedKey, nestedValue) in nested)
            {
                WriteValue(builder, nestedKey, nestedValue, indent + 2);
            }
        }
        else if (value is IReadOnlyList<string> list)
        {
            builder.AppendLine();
            foreach (var item in list)
            {
                builder.Append(' ', indent + 2).Append("- ").AppendLine(Quote(item));
            }
        }
        else
        {
            builder.Append(' ').AppendLine(value switch
            {
                string text => Quote(text),
                bool flag => flag ? "true" : "false",
                int number => number.ToString(System.Globalization.CultureInfo.InvariantCulture),
                _ => throw new InvalidDataException("订阅包含无法转换的节点字段"),
            });
        }
    }

    private static string Quote(string value)
    {
        if (value.Any(character => char.IsControl(character)))
        {
            throw new InvalidDataException("订阅节点包含不可用的控制字符");
        }

        return $"'{value.Replace("'", "''", StringComparison.Ordinal)}'";
    }
}
