using Weave.Windows.Core;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.Web.WebView2.Core;

namespace Weave.Windows;

public sealed partial class MainWindow
{
    private bool _privacyOpen;
    private ContentDialog? _activePrivacyDialog;
    private async void BrowserPrivacy_Click(object sender, RoutedEventArgs e) => await OpenPrivacyAsync(false);
    private async void DnsPrivacy_Click(object sender, RoutedEventArgs e) => await OpenPrivacyAsync(true);
    private async Task OpenPrivacyAsync(bool dns)
    {
        if (_privacyOpen || _summaryOpen || _busy) return;
        if (!_model.IsConnected || _model.ActiveBundle is not { } bundle) { MessageText.Text = L.T("请先连接代理"); return; }
        _privacyOpen = true;
        try { await RunPrivacySessionAsync(dns, bundle, _model.NetworkRevision); }
        finally { _privacyOpen = false; _activePrivacyDialog = null; }
    }

    private async Task RunPrivacySessionAsync(bool dns, RuntimeBundle bundle, long revision)
    {
        var consent = new ContentDialog
        {
            Title = dns ? L.T("打开 DNS 出口实测？") : L.T("开始浏览器隐私实验？"),
            Content = dns ? L.T("将在隔离的内置浏览器中访问 browserleaks.com/dns。该第三方会看到测试流量和出口地址；结果属于此浏览器，不代表其他应用。")
                : L.T("HTTPS 出口查询会访问 ipify；WebRTC 将向 Google / Cloudflare 的 STUN 服务发包，可能显露直连公网地址。指纹特征仅在本机计算，不上传。检测只代表内置浏览器，不替代你常用浏览器的实测。"),
            PrimaryButtonText = L.T("允许本次检测"), CloseButtonText = L.T("取消"), XamlRoot = RootGrid.XamlRoot,
        };
        _activePrivacyDialog = consent;
        if (await consent.ShowAsync() != ContentDialogResult.Primary || !EvidenceStillCurrent(bundle, revision)) return;
        var directory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Weave", "privacy-sessions", Guid.NewGuid().ToString("N"));
        var web = new WebView2 { Width = Math.Max(420, Math.Min(940, RootGrid.ActualWidth - 120)), Height = Math.Max(320, Math.Min(620, RootGrid.ActualHeight - 150)) };
        var loaded = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        web.Loaded += (_, _) => loaded.TrySetResult();
        var dialog = new ContentDialog { Title = dns ? L.T("DNS 出口 · 第三方实测") : L.T("浏览器隐私 · 本机实验"),
            Content = web, CloseButtonText = L.T("关闭并清理"), XamlRoot = RootGrid.XamlRoot };
        dialog.Resources["ContentDialogMaxWidth"] = 1100d;
        _activePrivacyDialog = dialog;
        var display = dialog.ShowAsync().AsTask();
        try
        {
            await loaded.Task.WaitAsync(TimeSpan.FromSeconds(10), _lifetime.Token);
            if (display.IsCompleted || !EvidenceStillCurrent(bundle, revision)) return;
            var environment = await CoreWebView2Environment.CreateWithOptionsAsync(null, directory, new CoreWebView2EnvironmentOptions
            {
                AdditionalBrowserArguments = $"--proxy-server=http://127.0.0.1:{bundle.MixedPort} --proxy-bypass-list=<-loopback> --disable-background-networking --disable-sync --no-first-run",
            });
            await web.EnsureCoreWebView2Async(environment);
            if (display.IsCompleted || !EvidenceStillCurrent(bundle, revision)) return;
            web.CoreWebView2.Settings.IsWebMessageEnabled = false;
            web.CoreWebView2.Settings.AreDevToolsEnabled = false;
            web.CoreWebView2.Settings.AreDefaultContextMenusEnabled = false;
            web.CoreWebView2.Settings.IsPasswordAutosaveEnabled = false;
            web.CoreWebView2.Settings.IsGeneralAutofillEnabled = false;
            web.CoreWebView2.NewWindowRequested += (_, args) => args.Handled = true;
            web.CoreWebView2.DownloadStarting += (_, args) => args.Cancel = true;
            web.CoreWebView2.PermissionRequested += (_, args) => args.State = CoreWebView2PermissionState.Deny;
            web.CoreWebView2.ProcessFailed += (_, args) =>
            {
                if (args.ProcessFailedKind is CoreWebView2ProcessFailedKind.BrowserProcessExited or CoreWebView2ProcessFailedKind.RenderProcessExited)
                {
                    MessageText.Text = L.T("浏览器检测已中断，未作安全结论；请重新检测");
                    dialog.Hide();
                }
            };
            web.CoreWebView2.NavigationStarting += (_, args) =>
            {
                var allowed = args.Uri == "about:blank" || (dns && Uri.TryCreate(args.Uri, UriKind.Absolute, out var uri) &&
                    uri.Scheme == "https" && uri.Host == "browserleaks.com" && (uri.AbsolutePath == "/dns" || uri.AbsolutePath == "/dns/"));
                if (!allowed) args.Cancel = true;
            };
            if (dns) web.CoreWebView2.Navigate("https://browserleaks.com/dns");
            else web.NavigateToString(PrivacyLabText.Localize(await File.ReadAllTextAsync(Path.Combine(AppContext.BaseDirectory, "Assets", "privacy-lab.html")), L.Language));
            await display;
            await web.CoreWebView2.Profile.ClearBrowsingDataAsync();
        }
        catch (Exception error) when (error is System.Runtime.InteropServices.COMException or InvalidOperationException or IOException or UnauthorizedAccessException or TimeoutException or OperationCanceledException)
        { MessageText.Text = L.T("浏览器实验无法打开；请检查 Microsoft Edge WebView2 Runtime 是否安装。未自动安装或下载组件。"); }
        finally
        {
            dialog.Hide();
            try { web.Close(); }
            catch (Exception error) when (error is System.Runtime.InteropServices.COMException or InvalidOperationException) { }
            // WebView2 may keep files locked briefly after Close. Retry only this session's
            // generated directory, never the user's browser profile or global cookies.
            foreach (var delay in new[] { 0, 250, 750, 1500 })
            {
                if (delay > 0) await Task.Delay(delay);
                try { if (Directory.Exists(directory)) Directory.Delete(directory, true); break; }
                catch (Exception error) when (error is IOException or UnauthorizedAccessException)
                { if (delay == 1500) MessageText.Text = L.T("浏览器已关闭，但部分临时文件仍被系统占用"); }
            }
        }
    }
}
