using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Weave.Windows.Core;
using Windows.Storage.Pickers;
using WinRT.Interop;

namespace Weave.Windows;

public sealed partial class MainWindow : Window
{
    private readonly WeaveAppModel _model = new();
    private readonly CancellationTokenSource _lifetime = new();
    private bool _busy;
    private bool _initialized;
    private string _page = "0";
    private readonly Dictionary<string, double> _scrollOffsets = new();
    private bool _closed;
    private CancellationTokenSource? _probeCancellation;
    private readonly string _themePath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Weave", "appearance.txt");

    public MainWindow()
    {
        InitializeComponent();
        _initialized = true;
        RootGrid.Loaded += CapturePreviewIfRequested;
        AppWindow.Resize(new global::Windows.Graphics.SizeInt32(1160, 800));
        try { _model.Load(); }
        catch (Exception) { MessageText.Text = "本地配置读取失败。原文件已保留，请检查当前 Windows 用户与文件权限。"; }
        _model.StatusChanged += (_, _) => DispatcherQueue.TryEnqueue(() => { if (!_closed) UpdateStatus(); });
        ThemeSelector.ItemsSource = AppearancePalette.All.Select(palette => palette.Name).ToArray();
        DnsSelector.SelectedIndex = 0;
        try
        {
            if (File.Exists(_themePath) && int.TryParse(File.ReadAllText(_themePath), out var theme) && theme >= 0 && theme < AppearancePalette.All.Count)
                ThemeSelector.SelectedIndex = theme;
        }
        catch (IOException) { }
        catch (UnauthorizedAccessException) { }
        if (ThemeSelector.SelectedIndex < 0) ThemeSelector.SelectedIndex = 0;
        SubscriptionComboBox.ItemsSource = _model.Subscriptions;
        SubscriptionListView.ItemsSource = _model.Subscriptions;
        RouteSubscriptionComboBox.ItemsSource = _model.Subscriptions;
        RouteListView.ItemsSource = _model.AppRoutes;
        RouteTargetModeComboBox.ItemsSource = new[] { "自动测速", "固定节点", "直连", "阻止" };
        RouteTargetModeComboBox.SelectedIndex = 0;
        if (_model.Subscriptions.Count > 0)
        {
            SubscriptionComboBox.SelectedIndex = 0;
            RouteSubscriptionComboBox.SelectedIndex = 0;
        }

        Closed += MainWindow_Closed;
        UpdateStatus();
        UpdateNavigation();
    }

    private void SubscriptionComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_initialized) return;
        NodeComboBox.ItemsSource = (SubscriptionComboBox.SelectedItem as SubscriptionRecord)?.Nodes;
        NodeComboBox.SelectedItem = null;
    }

    private void SubscriptionListView_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_initialized) return;
        if (e.AddedItems.FirstOrDefault() is SubscriptionRecord selected)
        {
            SubscriptionComboBox.SelectedItem = selected;
            SubscriptionNodesList.ItemsSource = selected.Nodes;
        }
    }

    private void RouteSubscriptionComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_initialized) return;
        RouteNodeComboBox.ItemsSource = (RouteSubscriptionComboBox.SelectedItem as SubscriptionRecord)?.Nodes;
        RouteNodeComboBox.SelectedItem = null;
    }

    private void RouteTargetModeComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_initialized) return;
        RouteNodeComboBox.IsEnabled = RouteTargetModeComboBox.SelectedIndex == 1;
        RouteSubscriptionComboBox.IsEnabled = RouteTargetModeComboBox.SelectedIndex is 0 or 1;
        if (RouteTargetModeComboBox.SelectedIndex != 1)
        {
            RouteNodeComboBox.SelectedItem = null;
        }
    }

    private async void AddRouteButton_Click(object sender, RoutedEventArgs e)
    {
        await RunActionAsync(() =>
        {
            var mode = RouteTargetModeComboBox.SelectedIndex;
            var subscription = RouteSubscriptionComboBox.SelectedItem as SubscriptionRecord;
            var target = mode switch
            {
                2 => RouteTarget.Direct(),
                3 => RouteTarget.Block(),
                1 when subscription is not null && RouteNodeComboBox.SelectedItem is ProxyNode node =>
                    RouteTarget.Fixed(subscription.Id, node.Id),
                0 when subscription is not null => RouteTarget.Automatic(subscription.Id),
                _ => throw new InvalidDataException("自动或固定节点分流需要先选择订阅；固定节点还需要选择节点"),
            };
            var processName = ProcessNameBox.Text.Trim();
            _model.AddOrReplaceRoute(new WindowsAppRoute
            {
                ProcessName = processName,
                DisplayName = processName,
                Target = target,
            });
            MessageText.Text = $"已保存 {processName} 的分流规则；重新连接后生效";
            return Task.CompletedTask;
        });
    }

    private void DeleteRouteButton_Click(object sender, RoutedEventArgs e)
    {
        if (_busy) return;
        if (sender is Button { Tag: string processName })
        {
            _model.RemoveRoute(processName);
            MessageText.Text = $"已删除 {processName} 的分流规则；重新连接后生效";
        }
    }

    private async void ImportTextButton_Click(object sender, RoutedEventArgs e)
    {
        await RunActionAsync(async () =>
        {
            var record = await _model.ImportTextAsync(
                SubscriptionNameBox.Text,
                "clipboard://manual",
                SubscriptionTextBox.Text);
            SubscriptionComboBox.SelectedItem = record;
            MessageText.Text = $"已导入 {record.Name}，发现 {record.Nodes.Count} 个节点";
        });
    }

    private async void ImportUrlButton_Click(object sender, RoutedEventArgs e)
    {
        await RunActionAsync(async () =>
        {
            var record = await _model.ImportUrlAsync(
                SubscriptionNameBox.Text,
                SubscriptionUrlBox.Text,
                _lifetime.Token);
            SubscriptionComboBox.SelectedItem = record;
            MessageText.Text = $"已导入 {record.Name}，发现 {record.Nodes.Count} 个节点";
        });
    }

    private async void ImportFileButton_Click(object sender, RoutedEventArgs e)
    {
        await RunActionAsync(async () =>
        {
            var picker = new FileOpenPicker();
            picker.FileTypeFilter.Add(".yaml");
            picker.FileTypeFilter.Add(".yml");
            picker.FileTypeFilter.Add(".txt");
            picker.FileTypeFilter.Add(".json");
            InitializeWithWindow.Initialize(picker, WindowNative.GetWindowHandle(this));
            var file = await picker.PickSingleFileAsync();
            if (file is null)
            {
                return;
            }

            var record = await _model.ImportFileAsync(SubscriptionNameBox.Text, file.Path);
            SubscriptionComboBox.SelectedItem = record;
            MessageText.Text = $"已导入 {record.Name}，发现 {record.Nodes.Count} 个节点";
        });
    }

    private async void DeleteSubscriptionButton_Click(object sender, RoutedEventArgs e)
    {
        if (_busy) return;
        if (sender is not Button { Tag: string id } ||
            _model.Subscriptions.FirstOrDefault(item => item.Id == id) is not { } record)
        {
            return;
        }

        var dialog = new ContentDialog
        {
            Title = "删除订阅？",
            Content = $"将从本机删除“{record.Name}”及其加密内容。",
            PrimaryButtonText = "删除",
            CloseButtonText = "取消",
            XamlRoot = RootGrid.XamlRoot,
        };
        if (await dialog.ShowAsync() != ContentDialogResult.Primary)
        {
            return;
        }

        try { _model.Remove(id); }
        catch (Exception error) { MessageText.Text = error.Message; return; }
        if (SubscriptionComboBox.SelectedItem is SubscriptionRecord selected && selected.Id == id)
        {
            SubscriptionComboBox.SelectedIndex = _model.Subscriptions.Count > 0 ? 0 : -1;
        }
        MessageText.Text = "订阅已删除";
    }

    private async void ConnectButton_Click(object sender, RoutedEventArgs e)
    {
        if (_busy) return;
        if (_model.IsConnected)
        {
            await RunActionAsync(async () =>
            {
                await _model.DisconnectAsync();
                ConnectButton.Content = "连接";
                MessageText.Text = "已停止本地内核。请确认系统网络已恢复。";
                UpdateStatus();
            });
            return;
        }

        if (SubscriptionComboBox.SelectedItem is not SubscriptionRecord subscription)
        {
            MessageText.Text = "请先导入并选择订阅";
            return;
        }

        await RunActionAsync(async () =>
        {
            var node = NodeComboBox.SelectedItem as ProxyNode;
            _model.NetworkOptions = new WindowsNetworkOptions { Ipv6Enabled = Ipv6Toggle.IsOn,
                RoutingMode = (RoutingMode)Math.Max(0, RoutingSelector.SelectedIndex),
                BlockUdpStun = StunToggle.IsOn, CustomDnsEndpoint = CustomDnsBox.Text.Trim(),
                DnsProfile = (DnsProfile)Math.Max(0, DnsSelector.SelectedIndex) };
            await _model.ConnectAsync(subscription.Id, node?.Id, _lifetime.Token);
            ConnectButton.Content = "断开连接";
            MessageText.Text = "内核与 TUN 已就绪。可在“网络与隐私”检查实际网站连通性。";
            UpdateStatus();
        });
    }

    private async Task RunActionAsync(Func<Task> action)
    {
        if (_busy || _closed) return;
        _busy = true;
        BusyRing.Visibility = Visibility.Visible;
        BusyRing.IsActive = true;
        ConnectButton.IsEnabled = false;
        ImportPanel.IsHitTestVisible = false;
        RoutesPanel.IsHitTestVisible = false;
        SubscriptionsPanel.IsHitTestVisible = false;
        try
        {
            await action();
        }
        catch (OperationCanceledException)
        {
            MessageText.Text = "操作已取消";
        }
        catch (Exception exception)
        {
            MessageText.Text = exception.Message;
        }
        finally
        {
            _busy = false;
            BusyRing.IsActive = false;
            BusyRing.Visibility = Visibility.Collapsed;
            ConnectButton.IsEnabled = true;
            ImportPanel.IsHitTestVisible = true;
            RoutesPanel.IsHitTestVisible = true;
            SubscriptionsPanel.IsHitTestVisible = true;
        }

        UpdateStatus();
    }

    private void UpdateStatus()
    {
        StatusText.Text = _model.Status;
        ConnectButton.Content = _model.IsConnected ? "断开连接" : "连接";
        HeroStatus.Text = _model.IsConnected ? "已连接" : "尚未连接";
        HeroDetail.Text = _model.IsConnected ? "Mihomo TUN · 内核运行中" : "本地内核 · 等待连接";
    }

    private void AutomaticNode_Click(object sender, RoutedEventArgs e) => NodeComboBox.SelectedItem = null;

    private void Navigate_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not Button { Tag: string page }) return;
        if (_page == page) return;
        _scrollOffsets[_page] = ContentScroll.VerticalOffset;
        _page = page;
        ConnectionPanel.Visibility = page == "0" ? Visibility.Visible : Visibility.Collapsed;
        ImportPanel.Visibility = SubscriptionsPanel.Visibility = NodesPanel.Visibility = page == "1" ? Visibility.Visible : Visibility.Collapsed;
        RoutesPanel.Visibility = page == "2" ? Visibility.Visible : Visibility.Collapsed;
        SettingsPanel.Visibility = page == "3" ? Visibility.Visible : Visibility.Collapsed;
        DiagnosticsPanel.Visibility = page == "4" ? Visibility.Visible : Visibility.Collapsed;
        UpdateNavigation();
        DispatcherQueue.TryEnqueue(() => ContentScroll.ChangeView(null, _scrollOffsets.GetValueOrDefault(_page), null, true));
    }

    private void UpdateNavigation()
    {
        if (!_initialized) return;
        var titles = new[] { "连接", "订阅", "应用分流", "设置", "网络与隐私" };
        var subtitles = new[] { "你的网络，从容掌握。", "整理订阅，找到适合你的出口。", "不同应用，各有去向。", "让外观和网络，符合你的习惯。", "以真实响应为依据，不以检测分数替代事实。" };
        var index = int.Parse(_page);
        PageTitle.Text = titles[index];
        PageSubtitle.Text = subtitles[index];
        var buttons = new[] { Nav0, Nav1, Nav2, Nav3, Nav4 };
        var theme = (ResourceDictionary)Application.Current.Resources.ThemeDictionaries[RootGrid.RequestedTheme == ElementTheme.Dark ? "Dark" : "Light"];
        for (var i = 0; i < buttons.Length; i++)
        {
            buttons[i].Background = i == index ? (Microsoft.UI.Xaml.Media.Brush)theme["WeaveGlassBrush"] : new Microsoft.UI.Xaml.Media.SolidColorBrush(Microsoft.UI.Colors.Transparent);
            buttons[i].Foreground = (Microsoft.UI.Xaml.Media.Brush)theme[i == index ? "WeaveInkBrush" : "WeaveMutedBrush"];
            buttons[i].FontWeight = i == index ? Microsoft.UI.Text.FontWeights.SemiBold : Microsoft.UI.Text.FontWeights.Normal;
        }
    }

    private void RootGrid_SizeChanged(object sender, SizeChangedEventArgs e)
    {
        if (!_initialized) return;
        var narrow = e.NewSize.Width < 1000;
        HeroColumn.Width = narrow ? new GridLength(0) : new GridLength(0.85, GridUnitType.Star);
        Grid.SetColumn(ConnectionHero, narrow ? 1 : 0);
        Grid.SetRow(ExitCard, narrow ? 1 : 0);
        Grid.SetRow(ConnectionNote, narrow ? 2 : 1);
        while (ConnectionPanel.RowDefinitions.Count < 3) ConnectionPanel.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        ConnectionHero.MinHeight = narrow ? 210 : 330;
    }

    private async void CapturePreviewIfRequested(object sender, RoutedEventArgs e)
    {
        // Explicit CI-only render capture, never enabled during normal use.
        var path = Environment.GetEnvironmentVariable("WEAVE_UI_CAPTURE");
        if (string.IsNullOrEmpty(path)) return;
        await Task.Delay(700);
        var bitmap = new Microsoft.UI.Xaml.Media.Imaging.RenderTargetBitmap();
        await bitmap.RenderAsync(RootGrid);
        var buffer = await bitmap.GetPixelsAsync();
        using var reader = global::Windows.Storage.Streams.DataReader.FromBuffer(buffer);
        var pixels = new byte[buffer.Length];
        reader.ReadBytes(pixels);
        var folder = await global::Windows.Storage.StorageFolder.GetFolderFromPathAsync(Path.GetDirectoryName(path)!);
        var file = await folder.CreateFileAsync(Path.GetFileName(path), global::Windows.Storage.CreationCollisionOption.ReplaceExisting);
        using var stream = await file.OpenAsync(global::Windows.Storage.FileAccessMode.ReadWrite);
        var encoder = await global::Windows.Graphics.Imaging.BitmapEncoder.CreateAsync(global::Windows.Graphics.Imaging.BitmapEncoder.PngEncoderId, stream);
        encoder.SetPixelData(global::Windows.Graphics.Imaging.BitmapPixelFormat.Bgra8, global::Windows.Graphics.Imaging.BitmapAlphaMode.Premultiplied, (uint)bitmap.PixelWidth, (uint)bitmap.PixelHeight, 96, 96, pixels);
        await encoder.FlushAsync();
    }

    private void ThemeSelector_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_initialized) return;
        var index = ThemeSelector.SelectedIndex;
        if (index < 0 || index >= AppearancePalette.All.Count) return;
        var palette = AppearancePalette.All[index];
        var theme = (ResourceDictionary)Application.Current.Resources.ThemeDictionaries[palette.Dark ? "Dark" : "Light"];
        void Set(string key, string hex) => ((Microsoft.UI.Xaml.Media.SolidColorBrush)theme[key]).Color = Color(hex);
        Set("WeaveCanvasBrush", palette.Canvas);
        Set("WeaveCardBrush", palette.Paper);
        Set("WeaveInkBrush", palette.Ink);
        Set("WeaveMutedBrush", palette.Muted);
        Set("WeaveAccentBrush", palette.Accent);
        ((Microsoft.UI.Xaml.Media.SolidColorBrush)theme["WeaveRimBrush"]).Color = Mix(Color(palette.Paper), Color(palette.Ink), palette.Dark ? .16 : .08);
        var glass = (Microsoft.UI.Xaml.Media.LinearGradientBrush)theme["WeaveGlassBrush"];
        glass.GradientStops[0].Color = Color(palette.Paper);
        glass.GradientStops[1].Color = Mix(Color(palette.Paper), Color(palette.Tint), index >= 4 ? .16 : .10);
        RootGrid.RequestedTheme = palette.Dark ? ElementTheme.Dark : ElementTheme.Light;
        UpdateNavigation();
        try { Directory.CreateDirectory(Path.GetDirectoryName(_themePath)!); File.WriteAllText(_themePath, index.ToString()); }
        catch (IOException) { MessageText.Text = "外观已切换，但偏好未能保存。"; }
        catch (UnauthorizedAccessException) { MessageText.Text = "外观已切换，但偏好未能保存。"; }
    }

    private static global::Windows.UI.Color Color(string hex) => global::Windows.UI.Color.FromArgb(255,
        Convert.ToByte(hex[..2], 16), Convert.ToByte(hex.Substring(2, 2), 16), Convert.ToByte(hex.Substring(4, 2), 16));
    private static global::Windows.UI.Color Mix(global::Windows.UI.Color a, global::Windows.UI.Color b, double amount) =>
        global::Windows.UI.Color.FromArgb(255, (byte)(a.R + (b.R - a.R) * amount),
            (byte)(a.G + (b.G - a.G) * amount), (byte)(a.B + (b.B - a.B) * amount));

    private async void RefreshSubscription_Click(object sender, RoutedEventArgs e)
    {
        var selected = SubscriptionListView.SelectedItems.Cast<SubscriptionRecord>().ToArray();
        if (selected.Length == 0) { MessageText.Text = "请先勾选需要更新的订阅"; return; }
        await RunActionAsync(async () =>
        {
            var count = 0;
            foreach (var record in selected)
            {
                if (!record.Source.StartsWith("https://", StringComparison.OrdinalIgnoreCase)) continue;
                await _model.EditAsync(record, record.Name, record.Source, _lifetime.Token);
                count++;
            }
            MessageText.Text = $"已更新 {count} 份远程订阅；本地文件需重新导入。分流引用的旧节点若已移除，需要重新选择。";
        });
    }

    private async void EditSubscription_Click(object sender, RoutedEventArgs e)
    {
        if (_busy) return;
        if (SubscriptionListView.SelectedItems.Count != 1) { MessageText.Text = "编辑时请只选择一份订阅"; return; }
        var record = (SubscriptionRecord)SubscriptionListView.SelectedItems[0];
        var name = new TextBox { Header = "名称", Text = record.Name };
        var url = new TextBox { Header = "订阅链接（保存后重新获取）", Text = record.Source,
            IsEnabled = record.Source.StartsWith("https://", StringComparison.OrdinalIgnoreCase) };
        var panel = new StackPanel { Spacing = 14 };
        panel.Children.Add(name); panel.Children.Add(url);
        var dialog = new ContentDialog { Title = "编辑订阅", Content = panel, PrimaryButtonText = "保存",
            CloseButtonText = "取消", XamlRoot = RootGrid.XamlRoot };
        if (await dialog.ShowAsync() == ContentDialogResult.Primary)
            await RunActionAsync(async () => { await _model.EditAsync(record, name.Text, url.Text, _lifetime.Token); MessageText.Text = "订阅已保存"; });
    }

    private async void ExportSubscriptions_Click(object sender, RoutedEventArgs e)
    {
        if (_busy) return;
        var selected = SubscriptionListView.SelectedItems.Cast<SubscriptionRecord>().ToArray();
        if (selected.Length == 0) { MessageText.Text = "请先勾选要分享的订阅；未勾选的不会导出"; return; }
        var warning = new ContentDialog { Title = $"导出 {selected.Length} 份订阅？",
            Content = "文件包含服务器和连接密码，不加密。请妥善保管，勿上传公开仓库。",
            PrimaryButtonText = "继续导出", CloseButtonText = "取消", XamlRoot = RootGrid.XamlRoot };
        if (await warning.ShowAsync() != ContentDialogResult.Primary) return;
        await RunActionAsync(async () =>
        {
            var picker = new FileSavePicker { SuggestedFileName = "Weave-subscriptions" };
            picker.FileTypeChoices.Add("订阅配置 ZIP", new List<string> { ".zip" });
            InitializeWithWindow.Initialize(picker, WindowNative.GetWindowHandle(this));
            var file = await picker.PickSaveFileAsync();
            if (file is null) return;
            await Task.Run(() =>
            {
                using var output = File.Create(file.Path);
                using var zip = new System.IO.Compression.ZipArchive(output, System.IO.Compression.ZipArchiveMode.Create);
                for (var i = 0; i < selected.Length; i++)
                {
                    using var writer = new StreamWriter(zip.CreateEntry($"subscription-{i + 1}.yaml").Open());
                    writer.Write(selected[i].ProviderYaml);
                }
            });
            MessageText.Text = $"已导出 {selected.Length} 份订阅。接收方解压后导入 YAML 即可。";
        });
    }

    private sealed record NodeResult(ProxyNode Node, int? Delay)
    {
        public string DisplayName => $"{Node.Name} · {(Delay is { } ms ? $"{ms} ms" : "超时 / 不可达")}";
    }
    private void NodeList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        var node = SubscriptionNodesList.SelectedItem switch { ProxyNode p => p, NodeResult r => r.Node, _ => null };
        if (node is not null) NodeComboBox.SelectedItem = node;
    }

    private async void TestNodes_Click(object sender, RoutedEventArgs e)
    {
        if (_model.ActiveBundle is not { } bundle || !_model.IsConnected ||
            SubscriptionComboBox.SelectedItem is not SubscriptionRecord record)
        { MessageText.Text = "请先连接，再测试当前订阅节点"; return; }
        await RunActionAsync(async () =>
        {
            using var cancellation = CancellationTokenSource.CreateLinkedTokenSource(_lifetime.Token);
            _probeCancellation = cancellation;
            try
            {
                using var controller = new MihomoController(bundle);
                using var gate = new SemaphoreSlim(3);
                var results = await Task.WhenAll(record.Nodes.Select(async node =>
                {
                    await gate.WaitAsync(cancellation.Token);
                    try { return new NodeResult(node, await controller.ProbeNodeAsync(MihomoConfigBuilder.NodePrefix(record.Id) + node.RawName, cancellation.Token)); }
                    catch (Exception error) when (error is HttpRequestException or OperationCanceledException or System.Text.Json.JsonException)
                    { cancellation.Token.ThrowIfCancellationRequested(); return new NodeResult(node, null); }
                    finally { gate.Release(); }
                }));
                SubscriptionNodesList.ItemsSource = results.OrderBy(result => result.Delay ?? int.MaxValue).ToArray();
                NodeTestText.Text = $"已测 {results.Length} 个，响应 {results.Count(result => result.Delay.HasValue)} 个。点击节点可选为默认出口，重新连接后生效。";
            }
            finally { _probeCancellation = null; }
        });
    }

    private async void RunDiagnostics_Click(object sender, RoutedEventArgs e)
    {
        if (DiagnosticConsent.IsChecked != true) { MessageText.Text = "请先允许本次检测访问测试网站"; return; }
        if (!_model.IsConnected || _model.ActiveBundle is not { } bundle) { MessageText.Text = "请先连接代理"; return; }
        await RunActionAsync(async () =>
        {
            using var cancellation = CancellationTokenSource.CreateLinkedTokenSource(_lifetime.Token);
            _probeCancellation = cancellation;
            try { DiagnosticResults.ItemsSource = await NetworkDiagnostics.RunAsync(bundle, cancellation.Token); }
            finally { _probeCancellation = null; DiagnosticConsent.IsChecked = false; }
        });
    }
    private void StopDiagnostics_Click(object sender, RoutedEventArgs e) => _probeCancellation?.Cancel();

    private async void MainWindow_Closed(object sender, WindowEventArgs args)
    {
        _closed = true;
        _lifetime.Cancel();
        try { await _model.DisposeAsync(); }
        finally { _lifetime.Dispose(); }
    }
}
