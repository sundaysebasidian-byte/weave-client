using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.Web.WebView2.Core;

namespace Weave.Windows;

public sealed partial class MainWindow
{
    private bool _privacyOpen;
    private async void BrowserPrivacy_Click(object sender, RoutedEventArgs e) => await OpenPrivacyAsync(false);
    private async void DnsPrivacy_Click(object sender, RoutedEventArgs e) => await OpenPrivacyAsync(true);
    private async Task OpenPrivacyAsync(bool dns)
    {
        if (_privacyOpen || _busy) return;
        if (!_model.IsConnected || _model.ActiveBundle is not { } bundle) { MessageText.Text = "请先连接代理"; return; }
        var consent = new ContentDialog
        {
            Title = dns ? "打开 DNS 出口实测？" : "开始浏览器隐私实验？",
            Content = dns ? "将在隔离的内置浏览器中访问 browserleaks.com/dns。该第三方会看到测试流量和出口地址；结果属于此浏览器，不代表其他应用。"
                : "HTTPS 出口查询会访问 ipify；WebRTC 将向 Google / Cloudflare 的 STUN 服务发包，可能显露直连公网地址。指纹特征仅在本机计算，不上传。检测只代表内置浏览器，不替代你常用浏览器的实测。",
            PrimaryButtonText = "允许本次检测", CloseButtonText = "取消", XamlRoot = RootGrid.XamlRoot,
        };
        if (await consent.ShowAsync() != ContentDialogResult.Primary) return;
        _privacyOpen = true;
        var directory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Weave", "privacy-sessions", Guid.NewGuid().ToString("N"));
        var web = new WebView2 { Width = Math.Max(420, Math.Min(940, RootGrid.ActualWidth - 120)), Height = Math.Max(320, Math.Min(620, RootGrid.ActualHeight - 150)) };
        var loaded = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        web.Loaded += (_, _) => loaded.TrySetResult();
        var dialog = new ContentDialog { Title = dns ? "DNS 出口 · 第三方实测" : "浏览器隐私 · 本机实验",
            Content = web, CloseButtonText = "关闭并清理", XamlRoot = RootGrid.XamlRoot };
        dialog.Resources["ContentDialogMaxWidth"] = 1100d;
        var display = dialog.ShowAsync().AsTask();
        try
        {
            await loaded.Task.WaitAsync(TimeSpan.FromSeconds(10), _lifetime.Token);
            var environment = await CoreWebView2Environment.CreateWithOptionsAsync(null, directory, new CoreWebView2EnvironmentOptions
            {
                AdditionalBrowserArguments = $"--proxy-server=http://127.0.0.1:{bundle.MixedPort} --proxy-bypass-list=<-loopback> --disable-background-networking --disable-sync --no-first-run",
            });
            await web.EnsureCoreWebView2Async(environment);
            if (display.IsCompleted) return;
            web.CoreWebView2.Settings.IsWebMessageEnabled = false;
            web.CoreWebView2.Settings.AreDevToolsEnabled = false;
            web.CoreWebView2.Settings.AreDefaultContextMenusEnabled = false;
            web.CoreWebView2.Settings.IsPasswordAutosaveEnabled = false;
            web.CoreWebView2.Settings.IsGeneralAutofillEnabled = false;
            web.CoreWebView2.NewWindowRequested += (_, args) => args.Handled = true;
            web.CoreWebView2.DownloadStarting += (_, args) => args.Cancel = true;
            web.CoreWebView2.PermissionRequested += (_, args) => args.State = CoreWebView2PermissionState.Deny;
            web.CoreWebView2.NavigationStarting += (_, args) =>
            {
                var allowed = args.Uri == "about:blank" || (dns && Uri.TryCreate(args.Uri, UriKind.Absolute, out var uri) &&
                    uri.Scheme == "https" && uri.Host == "browserleaks.com" && uri.AbsolutePath.StartsWith("/dns", StringComparison.Ordinal));
                if (!allowed) args.Cancel = true;
            };
            if (dns) web.CoreWebView2.Navigate("https://browserleaks.com/dns");
            else web.NavigateToString(await File.ReadAllTextAsync(Path.Combine(AppContext.BaseDirectory, "Assets", "privacy-lab.html")));
            await display;
            await web.CoreWebView2.Profile.ClearBrowsingDataAsync();
        }
        catch (Exception error) when (error is System.Runtime.InteropServices.COMException or InvalidOperationException or IOException or TimeoutException or OperationCanceledException)
        { MessageText.Text = "浏览器实验无法打开；请检查 Microsoft Edge WebView2 Runtime 是否安装。未自动安装或下载组件。"; }
        finally
        {
            dialog.Hide();
            web.Close();
            _privacyOpen = false;
            try { if (Directory.Exists(directory)) Directory.Delete(directory, true); }
            catch (Exception error) when (error is IOException or UnauthorizedAccessException) { /* Runtime may still be releasing its isolated cache. */ }
        }
    }
}
