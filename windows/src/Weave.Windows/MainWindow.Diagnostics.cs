using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Weave.Windows.Core;

namespace Weave.Windows;

public sealed partial class MainWindow
{
    private bool _summaryOpen;

    private async void PreviewDiagnostics_Click(object sender, RoutedEventArgs e)
    {
        if (_busy || _privacyOpen || _summaryOpen) return;
        _summaryOpen = true;
        try
        {
            var summary = DiagnosticSafeSummary.Build(
                DiagnosticResults.ItemsSource as IEnumerable<ProbeResult> ?? Array.Empty<ProbeResult>(), _diagnosticsStale);
            var content = new StackPanel { Spacing = 14 };
            content.Children.Add(new TextBlock { Text = L.T("仅包含检测状态与耗时，不含 IP、订阅或节点信息。复制后，其他应用可能读取剪贴板。"), TextWrapping = TextWrapping.Wrap });
            content.Children.Add(new TextBlock { Text = summary, TextWrapping = TextWrapping.Wrap,
                FontFamily = new Microsoft.UI.Xaml.Media.FontFamily("Consolas"), FontSize = 12, IsTextSelectionEnabled = true });
            var dialog = new ContentDialog { Title = L.T("脱敏检测摘要"), XamlRoot = RootGrid.XamlRoot,
                Content = new ScrollViewer { MaxHeight = 420, Content = content },
                PrimaryButtonText = L.T("复制"), CloseButtonText = L.T("取消"), DefaultButton = ContentDialogButton.Close };
            if (await dialog.ShowAsync() != ContentDialogResult.Primary) return;
            var package = new global::Windows.ApplicationModel.DataTransfer.DataPackage();
            package.SetText(summary);
            var copied = global::Windows.ApplicationModel.DataTransfer.Clipboard.SetContentWithOptions(package,
                new global::Windows.ApplicationModel.DataTransfer.ClipboardContentOptions { IsAllowedInHistory = false, IsRoamable = false });
            MessageText.Text = L.T(copied ? "已复制脱敏摘要" : "无法写入剪贴板，请重试");
        }
        catch (Exception error) when (error is System.Runtime.InteropServices.COMException or InvalidOperationException or UnauthorizedAccessException)
        { MessageText.Text = L.T("无法写入剪贴板，请重试"); }
        finally { _summaryOpen = false; }
    }
}
