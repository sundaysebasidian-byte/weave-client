using Microsoft.UI.Xaml.Controls;
using Weave.Windows.Core;

namespace Weave.Windows;

public sealed partial class MainWindow
{
    private bool _changingLanguage;
    private string LanguagePath => Path.Combine(Path.GetDirectoryName(_themePath)!, "language.txt");
    private void LoadLanguage()
    {
        try { L.Language = File.Exists(LanguagePath) ? File.ReadAllText(LanguagePath).Trim() :
            System.Globalization.CultureInfo.CurrentUICulture.TwoLetterISOLanguageName == "zh" ? "zh-CN" : "en"; }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException) { }
        if (Environment.GetEnvironmentVariable("WEAVE_PREVIEW_LANGUAGE") is { } preview) L.Language = preview;
    }
    private void LanguageSelector_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_initialized || _changingLanguage || LanguageSelector.SelectedIndex < 0) return;
        var next = LanguageSelector.SelectedIndex == 1 ? "en" : "zh-CN";
        if (L.Language == next) return;
        _changingLanguage = true;
        try
        {
            L.Language = next;
            RootGrid.Language = next;
            RefreshOptions(ThemeSelector, AppearancePalette.All.Select(p => L.T(p.Name)));
            RefreshOptions(DnsSelector, new[] { L.T("加密 DNS"), L.T("广告过滤"), L.T("家庭过滤"), L.T("自订 DNS") });
            RefreshOptions(RoutingSelector, new[] { L.T("规则"), L.T("全局"), L.T("直连") });
            // A temporary SelectedIndex=-1 must not clear the user's fixed-node selection.
            var initialized = _initialized; _initialized = false;
            try { RefreshOptions(RouteTargetModeComboBox, new[] { L.T("自动测速"), L.T("固定节点"), L.T("直连"), L.T("阻止") }); }
            finally { _initialized = initialized; }
            foreach (var record in _model.Subscriptions) record.RefreshLanguage();
            foreach (var route in _model.AppRoutes) route.RefreshLanguage();
            if (DiagnosticResults.ItemsSource is IEnumerable<ProbeResult> probes)
                foreach (var probe in probes) probe.RefreshLanguage();
            if (SubscriptionNodesList.ItemsSource is IEnumerable<NodeResult> nodes)
                foreach (var node in nodes) node.RefreshLanguage();
            UpdateStatus(); UpdateNavigation(); RoutingMode_Changed(this, e);
            // Test rows and selections remain intact; transient notices are not stale-language history.
            MessageText.Text = ""; NodeTestText.Text = ""; DiagnosticProgress.Text = "";
            NodeComboBox.PlaceholderText = L.T(_missingFixedNodeId is null ? "自动选择 · 最低延迟" : "原固定节点已移除，请重新选择；不会自动切换出口");
            RefreshShareLanguage();
            Directory.CreateDirectory(Path.GetDirectoryName(LanguagePath)!);
            File.WriteAllText(LanguagePath, next);
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException)
        { MessageText.Text = L.T("语言偏好未能保存"); }
        finally { _changingLanguage = false; }
    }
    private static void RefreshOptions(ComboBox selector, IEnumerable<string> items)
    {
        var index = selector.SelectedIndex;
        selector.ItemsSource = items.ToArray();
        selector.SelectedIndex = index;
    }
}
