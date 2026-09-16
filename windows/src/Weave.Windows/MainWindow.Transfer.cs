using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Weave.Windows.Core;
using WinRT.Interop;
using System.Runtime.InteropServices.WindowsRuntime;

namespace Weave.Windows;

public sealed partial class MainWindow
{
    private OneTimeLanTransferServer? _transferServer;
    private IReadOnlyList<TransferSubscription> _pendingTransfer = Array.Empty<TransferSubscription>();
    private bool _cameraScanning;
    private int _shareCount;
    private void RefreshShareLanguage()
    {
        if (_transferServer is { } server && !server.Completion.IsCompleted)
        {
            ShareCode.Text = L.T("两端核对码  ") + server.Link.ConfirmationCode();
            ShareStatus.Text = L.F($"正在分享 {_shareCount} 份订阅 · 5 分钟有效 · 接收一次即关闭");
        }
        else ShareStatus.Text = L.T("尚未开始分享");
    }

    private async void ShareLan_Click(object sender, RoutedEventArgs e)
    {
        var selected = SubscriptionListView.SelectedItems.Cast<SubscriptionRecord>().ToArray();
        if (selected.Length == 0) { MessageText.Text = L.T("先在订阅列表勾选要同步的订阅"); return; }
        await RunActionAsync(async () =>
        {
            await StopShareAsync();
            var host = LanAddress.SelectedItem as string ?? throw new InvalidOperationException(L.T("请选择连接手机的 Wi-Fi、热点或 USB 网络地址"));
            _transferServer = await Task.Run(() => new OneTimeLanTransferServer(host,
                selected.Select(record => new TransferSubscription(record.Name, record.Source, record.ProviderYaml)).ToArray()));
            ShareLink.Text = _transferServer.Link.Encode();
            _shareCount = selected.Length;
            ShareCode.Text = L.T("两端核对码  ") + _transferServer.Link.ConfirmationCode();
            ShareQr.Source = await QrTransfer.RenderAsync(ShareLink.Text);
            ShareQr.Visibility = Visibility.Visible;
            ShareStatus.Text = L.F($"正在分享 {selected.Length} 份订阅 · 5 分钟有效 · 接收一次即关闭");
            SendSection.IsExpanded = true; ReceiveSection.IsExpanded = false;
            NavigateTo("5");
            _ = ObserveTransferAsync(_transferServer);
        });
    }
    private async Task ObserveTransferAsync(OneTimeLanTransferServer server)
    {
        await server.Completion;
        DispatcherQueue.TryEnqueue(() =>
        {
            if (_closed || !ReferenceEquals(server, _transferServer)) return;
            ShareStatus.Text = L.T("分享通道已关闭（已传送、超时或连接中断）。如接收未完成，请重新生成。");
            ShareQr.Source = null; ShareQr.Visibility = Visibility.Collapsed; ShareLink.Text = ""; ShareCode.Text = "";
        });
    }
    private async Task StopShareAsync()
    {
        var server = _transferServer; _transferServer = null;
        if (server is not null) await server.DisposeAsync();
        ShareQr.Source = null; ShareQr.Visibility = Visibility.Collapsed; ShareLink.Text = ""; ShareCode.Text = "";
        ShareStatus.Text = L.T("分享已停止");
    }
    private async void StopShare_Click(object sender, RoutedEventArgs e)
    {
        try { await StopShareAsync(); } catch { MessageText.Text = L.T("分享已关闭"); }
    }
    private void RefreshAddresses_Click(object sender, RoutedEventArgs e)
    {
        try { LanAddress.ItemsSource = OneTimeLanTransferServer.LocalAddresses(); LanAddress.SelectedIndex = 0; }
        catch (System.Net.NetworkInformation.NetworkInformationException) { MessageText.Text = L.T("无法读取网络接口"); }
    }
    private void CopyShareLink_Click(object sender, RoutedEventArgs e)
    {
        if (ShareLink.Text.Length == 0) return;
        var package = new global::Windows.ApplicationModel.DataTransfer.DataPackage();
        package.SetText(ShareLink.Text);
        global::Windows.ApplicationModel.DataTransfer.Clipboard.SetContentWithOptions(package,
            new global::Windows.ApplicationModel.DataTransfer.ClipboardContentOptions { IsAllowedInHistory = false, IsRoamable = false });
        MessageText.Text = L.T("已复制一次性链接，已请求禁用剪贴板历史和跨设备漫游；不要发到公开群聊");
    }
    private async void ReceiveLan_Click(object sender, RoutedEventArgs e)
    {
        await RunActionAsync(async () =>
        {
            _pendingTransfer = Array.Empty<TransferSubscription>(); ReceivePreview.ItemsSource = null;
            ReceiveConfirmArea.Visibility = Visibility.Collapsed;
            var link = LanTransferLink.Parse(ReceiveLink.Text);
            try
            {
                if (ReceiveCode.Text.Trim() != link.ConfirmationCode()) throw new InvalidDataException(L.T("请填写发送设备显示的六位核对码"));
                _pendingTransfer = await LanTransferClient.FetchAsync(link, _lifetime.Token);
                ReceivePreview.ItemsSource = _pendingTransfer;
                ReceivePreview.SelectAll();
                ReceiveConfirmArea.Visibility = Visibility.Visible;
                MessageText.Text = L.T("已解密，尚未写入。请确认勾选要导入的订阅；同源订阅会更新，不删除其他订阅。");
            }
            finally { System.Security.Cryptography.CryptographicOperations.ZeroMemory(link.Key); ReceiveLink.Text = ""; ReceiveCode.Text = ""; }
        });
    }
    private async void ConfirmTransfer_Click(object sender, RoutedEventArgs e)
    {
        var selected = ReceivePreview.SelectedItems.Cast<TransferSubscription>().ToArray();
        if (selected.Length == 0) { MessageText.Text = L.T("请先接收并选择订阅"); return; }
        await RunActionAsync(async () =>
        {
            await _model.ImportTransferAsync(selected);
            _pendingTransfer = Array.Empty<TransferSubscription>(); ReceivePreview.ItemsSource = null;
            ReceiveConfirmArea.Visibility = Visibility.Collapsed;
            MessageText.Text = L.F($"已安全同步 {selected.Length} 份订阅；未选中的内容未导入。重新连接后应用新配置。");
        });
    }
    private async void QrFile_Click(object sender, RoutedEventArgs e)
    {
        await RunActionAsync(async () =>
        {
            var path = DesktopFilePicker.Open(WindowNative.GetWindowHandle(this), L.T("二维码图片\0*.png;*.jpg;*.jpeg;*.bmp;*.webp\0\0"));
            if (path is null) return;
            var value = await QrTransfer.ReadAsync(path);
            AcceptQr(value);
        });
    }
    private void AcceptQr(string value)
    {
        if (value.StartsWith("weave://", StringComparison.OrdinalIgnoreCase))
        {
            var link = LanTransferLink.Parse(value);
            System.Security.Cryptography.CryptographicOperations.ZeroMemory(link.Key);
            ReceiveLink.Text = value; NavigateTo("5");
            ReceiveSection.IsExpanded = true; SendSection.IsExpanded = false;
            MessageText.Text = L.T("已识别手机互传二维码，请核对发送端六位码再接收");
        }
        else if (Uri.TryCreate(value, UriKind.Absolute, out var uri) && uri.Scheme == "https")
        {
            SubscriptionUrlBox.Text = value; NavigateTo("1");
            MessageText.Text = L.T("已识别订阅链接，请确认后点击导入");
        }
        else throw new InvalidDataException(L.T("二维码不是 HTTPS 订阅或 Weave 加密互传链接"));
    }

    private async void CameraQr_Click(object sender, RoutedEventArgs e)
    {
        if (_busy || _cameraScanning) return;
        _cameraScanning = true;
        var preview = new Image { Width = 480, Height = 360 };
        var panel = new StackPanel { Spacing = 12 };
        panel.Children.Add(preview);
        panel.Children.Add(new TextBlock { Text = L.T("对准订阅或 Weave 互传二维码。图像只在本机识别，不保存、不上传。"), TextWrapping = TextWrapping.Wrap });
        var dialog = new ContentDialog { Title = L.T("扫描二维码"), Content = panel, CloseButtonText = L.T("完成"), XamlRoot = RootGrid.XamlRoot };
        string? scanned = null;
        var renderPending = 0;
        var active = true;
        Microsoft.UI.Xaml.Media.Imaging.WriteableBitmap? previewBitmap = null;
        await using var scanner = new CameraQrScanner();
        scanner.Preview += (pixels, width, height) =>
        {
            if (!active || Interlocked.Exchange(ref renderPending, 1) != 0) return;
            if (!DispatcherQueue.TryEnqueue(async () =>
            {
                try
                {
                    if (!active) return;
                    if (previewBitmap is null || previewBitmap.PixelWidth != width || previewBitmap.PixelHeight != height)
                        previewBitmap = new Microsoft.UI.Xaml.Media.Imaging.WriteableBitmap(width, height);
                    using var stream = previewBitmap.PixelBuffer.AsStream();
                    await stream.WriteAsync(pixels);
                    previewBitmap.Invalidate(); preview.Source = previewBitmap;
                }
                catch (Exception error) when (error is System.Runtime.InteropServices.COMException or ObjectDisposedException) { }
                finally { Volatile.Write(ref renderPending, 0); }
            })) Volatile.Write(ref renderPending, 0);
        };
        scanner.Found += value => DispatcherQueue.TryEnqueue(() => { if (active) { scanned = value; dialog.Hide(); } });
        try
        {
            await scanner.StartAsync();
            if (scanned is null) await dialog.ShowAsync();
            if (scanned is not null) AcceptQr(scanned);
        }
        catch (Exception error) when (error is UnauthorizedAccessException or System.Runtime.InteropServices.COMException or InvalidOperationException or InvalidDataException)
        { MessageText.Text = L.T("无法扫描：请检查 Windows 摄像头权限，或改用二维码图片 / 链接。"); }
        finally { active = false; preview.Source = null; _cameraScanning = false; }
    }
}
