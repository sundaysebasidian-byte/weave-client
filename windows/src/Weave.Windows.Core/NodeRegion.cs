using System.Text;

namespace Weave.Windows.Core;

/// <summary>
/// Best-effort region for a node, from a flag emoji in its original name, a standalone country code, or a well-known place name.
/// Returns "" when unsure; the UI then shows a neutral chip rather than guessing.
/// </summary>
public static class NodeRegion
{
    private static readonly (string Code, string Zh, string En, string[] Keywords)[] Table =
    {
        ("JP", "日本", "Japan", ["日本", "东京", "東京", "大阪", "tokyo", "osaka", "japan"]),
        ("SG", "新加坡", "Singapore", ["新加坡", "狮城", "singapore"]),
        ("HK", "香港", "Hong Kong", ["香港", "hong kong", "hongkong"]),
        ("TW", "台湾", "Taiwan", ["台湾", "台灣", "台北", "taiwan", "taipei"]),
        ("KR", "韩国", "Korea", ["韩国", "韓國", "首尔", "首爾", "seoul", "korea"]),
        ("US", "美国", "United States", ["美国", "美國", "洛杉矶", "圣何塞", "西雅图", "纽约", "硅谷", "芝加哥", "达拉斯", "los angeles", "san jose", "seattle", "new york", "united states"]),
        ("GB", "英国", "United Kingdom", ["英国", "英國", "伦敦", "london", "united kingdom"]),
        ("DE", "德国", "Germany", ["德国", "德國", "法兰克福", "frankfurt", "germany"]),
        ("FR", "法国", "France", ["法国", "法國", "巴黎", "paris", "france"]),
        ("NL", "荷兰", "Netherlands", ["荷兰", "荷蘭", "阿姆斯特丹", "amsterdam", "netherlands"]),
        ("CA", "加拿大", "Canada", ["加拿大", "多伦多", "温哥华", "toronto", "vancouver", "canada"]),
        ("AU", "澳大利亚", "Australia", ["澳大利亚", "澳洲", "悉尼", "sydney", "australia"]),
        ("RU", "俄罗斯", "Russia", ["俄罗斯", "莫斯科", "moscow", "russia"]),
        ("IN", "印度", "India", ["印度", "孟买", "mumbai", "india"]),
        ("TH", "泰国", "Thailand", ["泰国", "曼谷", "bangkok", "thailand"]),
        ("VN", "越南", "Vietnam", ["越南", "vietnam"]),
        ("MY", "马来西亚", "Malaysia", ["马来西亚", "吉隆坡", "malaysia"]),
        ("ID", "印尼", "Indonesia", ["印尼", "印度尼西亚", "雅加达", "indonesia"]),
        ("PH", "菲律宾", "Philippines", ["菲律宾", "马尼拉", "philippines"]),
        ("TR", "土耳其", "Turkey", ["土耳其", "turkey"]),
        ("AE", "阿联酋", "UAE", ["阿联酋", "迪拜", "dubai"]),
        ("IT", "意大利", "Italy", ["意大利", "米兰", "italy"]),
        ("ES", "西班牙", "Spain", ["西班牙", "马德里", "spain"]),
        ("SE", "瑞典", "Sweden", ["瑞典", "sweden"]),
        ("CH", "瑞士", "Switzerland", ["瑞士", "switzerland"]),
        ("BR", "巴西", "Brazil", ["巴西", "brazil"]),
        ("AR", "阿根廷", "Argentina", ["阿根廷", "argentina"]),
    };

    private static readonly HashSet<string> Generic = new(StringComparer.OrdinalIgnoreCase)
    {
        "iepl", "iplc", "bgp", "cn2", "gia", "vip", "premium", "pro", "node", "专线", "高速", "中继", "直连", "原生", "解锁", "游戏", "家宽", "流媒体",
    };

    public static string Code(ProxyNode node) => Code(node.RawName, node.Name);

    public static string Code(string rawName, string name)
    {
        var flag = FlagCode(rawName);
        if (flag.Length == 2 && Table.Any(entry => entry.Code == flag)) return flag;
        foreach (var token in Tokens(name))
            if (token.Length == 2 && Table.Any(entry => entry.Code.Equals(token, StringComparison.OrdinalIgnoreCase)))
                return token.ToUpperInvariant();
        var lower = name.ToLowerInvariant();
        foreach (var entry in Table)
            foreach (var keyword in entry.Keywords)
                if (lower.Contains(keyword, StringComparison.Ordinal)) return entry.Code;
        return "";
    }

    public static string RegionName(string code, bool english)
    {
        foreach (var entry in Table) if (entry.Code == code) return english ? entry.En : entry.Zh;
        return code;
    }

    /// <summary>The short place name shown large on the connection card: the first meaningful word of the node name.</summary>
    public static string Title(string name, string code, bool english)
    {
        foreach (var token in Tokens(name))
        {
            if (token.All(char.IsDigit) || Generic.Contains(token)) continue;
            if (token.Length == 2 && code.Equals(token, StringComparison.OrdinalIgnoreCase)) return RegionName(code, english);
            return token;
        }
        return code.Length > 0 ? RegionName(code, english) : name;
    }

    private static IEnumerable<string> Tokens(string name) =>
        name.Split([' ', '·', '-', '_', '|', '/', '(', ')', '[', ']', '（', '）', '【', '】', '|', ',', '，'], StringSplitOptions.RemoveEmptyEntries);

    private static string FlagCode(string rawName)
    {
        for (var i = 0; i + 1 < rawName.Length; i++)
        {
            if (!char.IsHighSurrogate(rawName[i]) || i + 3 >= rawName.Length) continue;
            var first = char.ConvertToUtf32(rawName[i], rawName[i + 1]);
            if (!char.IsHighSurrogate(rawName[i + 2])) continue;
            var second = char.ConvertToUtf32(rawName[i + 2], rawName[i + 3]);
            if (first is >= 0x1F1E6 and <= 0x1F1FF && second is >= 0x1F1E6 and <= 0x1F1FF)
                return new string(new[] { (char)('A' + first - 0x1F1E6), (char)('A' + second - 0x1F1E6) });
        }
        return "";
    }
}
