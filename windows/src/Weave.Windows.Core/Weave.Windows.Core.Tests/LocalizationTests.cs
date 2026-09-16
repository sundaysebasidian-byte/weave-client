using System.Text;
using System.Text.RegularExpressions;
using System.Xml.Linq;

namespace Weave.Windows.Core.Tests;

public class LocalizationTests
{
    private static string Root
    {
        get
        {
            var directory = new DirectoryInfo(AppContext.BaseDirectory);
            while (directory is not null && !File.Exists(Path.Combine(directory.FullName, "windows", "installer.iss"))) directory = directory.Parent;
            return directory?.FullName ?? throw new InvalidOperationException("Repository root not found");
        }
    }
    [Fact]
    public void CatalogIsCompleteAndPreservesFormatArguments()
    {
        Assert.True(L.Catalog.Count >= 320);
        foreach (var (source, english) in L.Catalog)
        {
            Assert.False(string.IsNullOrWhiteSpace(english), source);
            Assert.DoesNotMatch("[\u3400-\u9fff]", english);
            var before = Regex.Matches(source, @"\{\d+[^}]*\}").Select(m => m.Value).Order().ToArray();
            var after = Regex.Matches(english, @"\{\d+[^}]*\}").Select(m => m.Value).Order().ToArray();
            Assert.Equal(before, after);
            if (before.Length > 0)
            {
                System.Text.CompositeFormat.Parse(source);
                System.Text.CompositeFormat.Parse(english);
            }
        }
    }
    [Fact]
    public void FormattingDoesNotTranslateUserNamesOrAddresses()
    {
        var name = "订阅 · 東京 / {server} 🇯🇵";
        Assert.Equal($"Imported {name} with 65 nodes.", L.Format($"已导入 {name}，发现 {65} 个节点", "en"));
        Assert.Equal($"已导入 {name}，发现 65 个节点", L.Format($"已导入 {name}，发现 {65} 个节点", "zh-CN"));
        Assert.Equal("192.0.2.13", L.Translate("192.0.2.13", "en"));
    }
    [Fact]
    public void AllXamlLabelsHaveLiveTranslations()
    {
        var xaml = XDocument.Load(Path.Combine(Root, "windows/src/Weave.Windows/MainWindow.xaml"));
        var keys = File.ReadAllText(Path.Combine(Root, "windows/src/Weave.Windows/UiText.Keys.cs"));
        foreach (var attr in xaml.Descendants().Attributes())
        {
            if (attr.Value.Contains("Source={StaticResource UiText}", StringComparison.Ordinal))
            {
                var key = attr.Value[9..attr.Value.IndexOf(',')];
                Assert.Contains("public string " + key + " => L.T(", keys);
            }
            else if (new[] { "Text", "Content", "Header", "PlaceholderText", "OnContent", "OffContent" }.Contains(attr.Name.LocalName))
                Assert.DoesNotMatch("[\u3400-\u9fff]", attr.Value);
        }
    }
    [Fact]
    public void AllLiteralMessageKeysHaveTranslations()
    {
        foreach (var folder in new[] { "Weave.Windows", "Weave.Windows.Core" })
        foreach (var path in Directory.EnumerateFiles(Path.Combine(Root, "windows/src", folder), "*.cs"))
        {
            var code = File.ReadAllText(path);
            foreach (Match match in Regex.Matches(code, "L\\.T\\(\"((?:\\\\.|[^\"\\\\])*)\"\\)"))
            {
                var source = Regex.Unescape(match.Groups[1].Value);
                Assert.True(L.Catalog.ContainsKey(source), $"{Path.GetFileName(path)}: {source}");
            }
        }
        foreach (var palette in AppearancePalette.All) Assert.True(L.Catalog.ContainsKey(palette.Name));
    }
    [Fact]
    public void PrivacyLabHasCompleteEnglishAndUnchangedChinese()
    {
        var html = File.ReadAllText(Path.Combine(Root, "windows/src/Weave.Windows/Assets/privacy-lab.html"));
        var english = PrivacyLabText.Localize(html, "en");
        Assert.DoesNotMatch("[\u3400-\u9fff]", english);
        Assert.Contains("lang=\"en\"", english);
        Assert.Contains("does not prove", english);
        Assert.Equal(html, PrivacyLabText.Localize(html, "zh-CN"));
        Assert.Equal(Regex.Matches(html, "https://[^'\"; ]+").Select(m => m.Value),
            Regex.Matches(english, "https://[^'\"; ]+").Select(m => m.Value));
    }
}
