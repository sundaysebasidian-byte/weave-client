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
    private bool _shuttingDown;
    private bool _shutdownComplete;
    private CancellationTokenSource? _probeCancellation;
    private readonly DispatcherTimer _trafficTimer = new() { Interval = TimeSpan.FromSeconds(2) };
    private CancellationTokenSource? _connectCancellation;
    private CancellationTokenSource? _networkCancellation;
    private TrayIcon? _tray;
    private bool _hiddenToTray;
    private int _idleTrafficSamples;
    private bool _trafficBusy;
    private readonly string _themePath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Weave", "appearance.txt");

    public MainWindow()
    {
        LoadLanguage();
        InitializeComponent();
        foreach (var card in new[] { ConnectionHero, ExitCard, ConnectionNote, ImportPanel, SubscriptionsPanel, SettingsPanel, NetworkSettingsCard, PrivacySettingsCard, RoutesPanel, NodesPanel, DiagnosticsPanel, TransferPanel })
        {
            card.Shadow = new Microsoft.UI.Xaml.Media.ThemeShadow();
            card.Translation = new System.Numerics.Vector3(0, 0, card == ConnectionHero ? 14 : 8);
        }
        SidebarSurface.Shadow = new Microsoft.UI.Xaml.Media.ThemeShadow();
        SidebarSurface.Translation = new System.Numerics.Vector3(0, 0, 8);
        ConnectButton.Shadow = new Microsoft.UI.Xaml.Media.ThemeShadow();
        ConnectButton.Translation = new System.Numerics.Vector3(0, 0, 10);
        _initialized = true;
        RootGrid.Loaded += CapturePreviewIfRequested;
        var area = Microsoft.UI.Windowing.DisplayArea.GetFromWindowId(AppWindow.Id, Microsoft.UI.Windowing.DisplayAreaFallback.Primary).WorkArea;
        var scale = Math.Max(1, GetDpiForWindow(WindowNative.GetWindowHandle(this)) / 96.0);
        AppWindow.Resize(new global::Windows.Graphics.SizeInt32(Math.Min((int)(1280 * scale), area.Width - 40), Math.Min((int)(860 * scale), area.Height - 60)));
        if (Environment.GetEnvironmentVariable("WEAVE_PREVIEW_WIDE") == "1")
            AppWindow.Resize(new global::Windows.Graphics.SizeInt32((int)(1400 * scale), (int)(900 * scale)));
        if (Environment.GetEnvironmentVariable("WEAVE_PREVIEW_COMPACT") == "1")
            AppWindow.Resize(new global::Windows.Graphics.SizeInt32((int)(900 * scale), (int)(760 * scale)));
        try { _model.Load(); }
        catch (Exception) { MessageText.Text = L.T("本地配置读取失败。原文件已保留，请检查当前 Windows 用户与文件权限。"); }
        _model.StatusChanged += (_, _) => DispatcherQueue.TryEnqueue(() => { if (!_closed) UpdateStatus(); });
        ThemeSelector.ItemsSource = AppearancePalette.All.Select(palette => L.T(palette.Name)).ToArray();
        LanguageSelector.SelectedIndex = L.Language == "en" ? 1 : 0;
        RootGrid.Language = L.Language;
        RoutingSelector.ItemsSource = new[] { L.T("规则"), L.T("全局"), L.T("直连") };
        RoutingSelector.SelectedIndex = 0;
        DnsSelector.ItemsSource = new[] { L.T("加密 DNS"), L.T("广告过滤"), L.T("家庭过滤"), L.T("自订 DNS") };
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
        ChainSubscription.ItemsSource = _model.Subscriptions;
        RouteListView.ItemsSource = _model.AppRoutes;
        RouteTargetModeComboBox.ItemsSource = new[] { L.T("自动测速"), L.T("固定节点"), L.T("直连"), L.T("阻止") };
        RouteTargetModeComboBox.SelectedIndex = 0;
        if (_model.Subscriptions.Count > 0)
        {
            SubscriptionComboBox.SelectedIndex = 0;
            RouteSubscriptionComboBox.SelectedIndex = 0;
        }
        LoadPreferences();
        if (int.TryParse(Environment.GetEnvironmentVariable("WEAVE_PREVIEW_THEME"), out var previewTheme) &&
            previewTheme >= 0 && previewTheme < AppearancePalette.All.Count) ThemeSelector.SelectedIndex = previewTheme;

        Closed += MainWindow_Closed;
        _tray = new TrayIcon(WindowNative.GetWindowHandle(this),
            () => DispatcherQueue.TryEnqueue(() => { _hiddenToTray = false; _trafficTimer.Start(); AppWindow.Show(); Activate(); }),
            () => DispatcherQueue.TryEnqueue(async () => await ShutdownAsync(closeWindow: true)),
            () => DispatcherQueue.TryEnqueue(ScheduleNetworkCheck));
        System.Net.NetworkInformation.NetworkChange.NetworkAddressChanged += NetworkAddressChanged;
        AppWindow.Closing += async (_, args) =>
        {
            if (_shutdownComplete) return;
            args.Cancel = true;
            if (_tray?.Available == true && !_shuttingDown) { _hiddenToTray = true; _trafficTimer.Stop(); AppWindow.Hide(); return; }
            await ShutdownAsync(closeWindow: true);
        };
        UpdateStatus();
        UpdateNavigation();
        if (int.TryParse(Environment.GetEnvironmentVariable("WEAVE_PREVIEW_PAGE"), out var previewPage) && previewPage is >= 0 and <= 5)
            NavigateTo(previewPage.ToString());
        RefreshAddresses_Click(this, new RoutedEventArgs());
        _trafficTimer.Tick += TrafficTick;
        _trafficTimer.Start();
    }

    private void SubscriptionComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_initialized) return;
        NodeComboBox.ItemsSource = (SubscriptionComboBox.SelectedItem as SubscriptionRecord)?.Nodes;
        NodeComboBox.SelectedItem = null;
    }

    [System.Runtime.InteropServices.DllImport("user32.dll")]
    private static extern uint GetDpiForWindow(IntPtr hwnd);

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
                _ => throw new InvalidDataException(L.T("自动或固定节点分流需要先选择订阅；固定节点还需要选择节点")),
            };
            var processName = ProcessNameBox.Text.Trim();
            _model.AddOrReplaceRoute(new WindowsAppRoute
            {
                ProcessName = processName,
                DisplayName = processName,
                Target = target,
            });
            MessageText.Text = L.F($"已保存 {processName} 的分流规则；重新连接后生效");
            return Task.CompletedTask;
        });
    }

    private void DeleteRouteButton_Click(object sender, RoutedEventArgs e)
    {
        if (_busy) return;
        if (sender is Button { Tag: string processName })
        {
            _model.RemoveRoute(processName);
            MessageText.Text = L.F($"已删除 {processName} 的分流规则；重新连接后生效");
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
            MessageText.Text = L.F($"已导入 {record.Name}，发现 {record.Nodes.Count} 个节点");
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
            MessageText.Text = L.F($"已导入 {record.Name}，发现 {record.Nodes.Count} 个节点");
        });
    }

    private async void ImportFileButton_Click(object sender, RoutedEventArgs e)
    {
        await RunActionAsync(async () =>
        {
            var path = DesktopFilePicker.Open(WindowNative.GetWindowHandle(this));
            if (path is null)
            {
                return;
            }

            var record = await _model.ImportFileAsync(SubscriptionNameBox.Text, path);
            SubscriptionComboBox.SelectedItem = record;
            MessageText.Text = L.F($"已导入 {record.Name}，发现 {record.Nodes.Count} 个节点");
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
            Title = L.T("删除订阅？"),
            Content = L.F($"将从本机删除“{record.Name}”及其加密内容。"),
            PrimaryButtonText = L.T("删除"),
            CloseButtonText = L.T("取消"),
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
        MessageText.Text = L.T("订阅已删除");
    }

    private async void ConnectButton_Click(object sender, RoutedEventArgs e)
    {
        if (_connectCancellation is not null) { _connectCancellation.Cancel(); return; }
        if (_busy) return;
        if (_model.IsConnected)
        {
            await RunActionAsync(async () =>
            {
                await _model.DisconnectAsync();
                ConnectButton.Content = L.T("连接");
                MessageText.Text = L.T("已停止本地内核。请确认系统网络已恢复。");
                UpdateStatus();
            });
            return;
        }

        var subscription = SubscriptionComboBox.SelectedItem as SubscriptionRecord;
        if (RoutingSelector.SelectedIndex != 2 && subscription is null)
        {
            MessageText.Text = L.T("请先导入并选择订阅");
            return;
        }

        SavePreferences();
        using var identity = System.Security.Principal.WindowsIdentity.GetCurrent();
        if (TunToggle.IsOn && !new System.Security.Principal.WindowsPrincipal(identity).IsInRole(System.Security.Principal.WindowsBuiltInRole.Administrator))
        {
            var dialog = new ContentDialog { Title = L.T("允许启动 Windows TUN？"),
                Content = L.T("需要以当前 Windows 用户的管理员权限重新打开 Weave。订阅会保留；重开后点击连接。若你使用标准账户，请勿换成其他用户，否则无法读取本账户的加密订阅。"),
                PrimaryButtonText = L.T("重新打开并申请权限"), CloseButtonText = L.T("取消"), XamlRoot = RootGrid.XamlRoot };
            if (await dialog.ShowAsync() != ContentDialogResult.Primary) return;
            try
            {
                System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(Environment.ProcessPath!, "--elevated-restart") { UseShellExecute = true, Verb = "runas" });
                await ShutdownAsync(closeWindow: true);
            }
            catch (System.ComponentModel.Win32Exception) { MessageText.Text = L.T("管理员授权未完成，未改变系统网络。"); }
            return;
        }

        await RunActionAsync(async () =>
        {
            using var connection = CancellationTokenSource.CreateLinkedTokenSource(_lifetime.Token);
            connection.CancelAfter(TimeSpan.FromSeconds(60));
            _connectCancellation = connection;
            ConnectButton.IsEnabled = true;
            ConnectButton.Content = L.T("取消连接");
            try
            {
            var node = NodeComboBox.SelectedItem as ProxyNode;
            _model.NetworkOptions = new WindowsNetworkOptions { Ipv6Enabled = Ipv6Toggle.IsOn,
                EnableTun = TunToggle.IsOn, ChinaDirect = ChinaDirectToggle.IsOn,
                GeoDataDirectory = Path.Combine(AppContext.BaseDirectory, "geodata"),
                DomainRules = RoutingSelector.SelectedIndex == 0 ? DomainRoute.Parse(DomainRulesBox.Text) : Array.Empty<DomainRoute>(),
                ChainEntrySubscriptionId = ChainToggle.IsOn && RoutingSelector.SelectedIndex != 2 ? (ChainSubscription.SelectedItem as SubscriptionRecord)?.Id ?? throw new InvalidDataException(L.T("请选择入口订阅")) : null,
                ChainEntryNodeId = ChainToggle.IsOn && RoutingSelector.SelectedIndex != 2 ? (ChainNode.SelectedItem as ProxyNode)?.Id ?? throw new InvalidDataException(L.T("请选择入口节点")) : null,
                RoutingMode = (RoutingMode)Math.Max(0, RoutingSelector.SelectedIndex),
                BlockUdpStun = StunToggle.IsOn, CustomDnsEndpoint = CustomDnsBox.Text.Trim(),
                DnsProfile = (DnsProfile)Math.Max(0, DnsSelector.SelectedIndex) };
            await _model.ConnectAsync(subscription?.Id ?? "", node?.Id, connection.Token);
            ConnectButton.Content = L.T("断开连接");
            MessageText.Text = ConnectionHealth.Description(_model.Health);
            UpdateStatus();
            }
            finally { _connectCancellation = null; }
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
            MessageText.Text = L.T("操作已取消或超时。连接超时请检查管理员权限及防火墙；订阅超时请检查当前网络。");
        }
        catch (HttpRequestException)
        {
            MessageText.Text = L.T("网络请求失败，请检查连接与订阅地址。未展示原始请求地址，以保护订阅凭据。");
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
            if (!_closed) UpdateStatus();
        }

        UpdateStatus();
    }

    private void UpdateStatus()
    {
        StatusText.Text = _model.Status;
        ConnectButton.Content = _connectCancellation is not null ? L.T("取消连接") : _model.IsConnected ? L.T("断开连接") : L.T("连接");
        HeroStatus.Text = !_model.IsConnected ? L.T("尚未连接") : _model.Health == ConnectionHealthState.Reachable ? L.T("已连接") : L.T("网络待确认");
        HeroDetail.Text = _model.IsConnected ? _model.Status : RoutingSelector.SelectedIndex == 2 ? L.T("直连不隐藏公网地址") : L.T("选择订阅后连接");
        if (!_model.IsConnected) { DownloadRate.Text = "—"; UploadRate.Text = "—"; }
    }

    private void AutomaticNode_Click(object sender, RoutedEventArgs e) => NodeComboBox.SelectedItem = null;

    private void Navigate_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not Button { Tag: string page }) return;
        NavigateTo(page);
    }
    private void NavigateTo(string page)
    {
        if (_page == page) return;
        _scrollOffsets[_page] = ContentScroll.VerticalOffset;
        _page = page;
        ConnectionPanel.Visibility = page == "0" ? Visibility.Visible : Visibility.Collapsed;
        ImportPanel.Visibility = SubscriptionsPanel.Visibility = NodesPanel.Visibility = page == "1" ? Visibility.Visible : Visibility.Collapsed;
        RoutesPanel.Visibility = page == "2" ? Visibility.Visible : Visibility.Collapsed;
        SettingsPanel.Visibility = page == "3" ? Visibility.Visible : Visibility.Collapsed;
        NetworkSettingsCard.Visibility = PrivacySettingsCard.Visibility = SettingsPanel.Visibility;
        DiagnosticsPanel.Visibility = page == "4" ? Visibility.Visible : Visibility.Collapsed;
        TransferPanel.Visibility = page == "5" ? Visibility.Visible : Visibility.Collapsed;
        UpdateNavigation();
        DispatcherQueue.TryEnqueue(() => ContentScroll.ChangeView(null, _scrollOffsets.GetValueOrDefault(_page), null, true));
    }

    private void UpdateNavigation()
    {
        if (!_initialized) return;
        var titles = new[] { L.T("连接"), L.T("订阅"), L.T("分流规则"), L.T("设置"), L.T("网络与隐私"), L.T("设备同步") };
        var index = int.Parse(_page);
        PageTitle.Text = titles[index];
        PageSubtitle.Text = "";
        var buttons = new[] { Nav0, Nav1, Nav2, Nav3, Nav4, Nav5 };
        var theme = (ResourceDictionary)Application.Current.Resources.ThemeDictionaries[RootGrid.RequestedTheme == ElementTheme.Dark ? "Dark" : "Light"];
        for (var i = 0; i < buttons.Length; i++)
        {
            buttons[i].Background = i == index ? (Microsoft.UI.Xaml.Media.Brush)theme["WeaveSelectionBrush"] : new Microsoft.UI.Xaml.Media.SolidColorBrush(Microsoft.UI.Colors.Transparent);
            buttons[i].BorderThickness = new Thickness(i == index ? 1 : 0);
            buttons[i].BorderBrush = (Microsoft.UI.Xaml.Media.Brush)theme["WeaveLightEdgeBrush"];
            buttons[i].Foreground = (Microsoft.UI.Xaml.Media.Brush)theme[i == index ? "WeaveAccentBrush" : "WeaveMutedBrush"];
            buttons[i].FontWeight = i == index ? Microsoft.UI.Text.FontWeights.SemiBold : Microsoft.UI.Text.FontWeights.Normal;
        }
    }

    private void RootGrid_SizeChanged(object sender, SizeChangedEventArgs e)
    {
        if (!_initialized) return;
        // Reflow individual controls, not entire cards: keep network controls close to the exit.
        SidebarColumn.Width = new GridLength(e.NewSize.Width < 1100 ? 276 : 292);
        var narrow = e.NewSize.Width < 920;
        Grid.SetColumnSpan(SubscriptionComboBox, narrow ? 2 : 1);
        Grid.SetColumn(NodeSelection, narrow ? 0 : 1);
        Grid.SetRow(NodeSelection, narrow ? 1 : 0);
        Grid.SetColumnSpan(NodeSelection, narrow ? 2 : 1);
        Grid.SetColumnSpan(RoutingSelector, narrow ? 2 : 1);
        Grid.SetColumn(TunToggle, narrow ? 0 : 1);
        Grid.SetRow(TunToggle, narrow ? 1 : 0);
        Grid.SetColumnSpan(TunToggle, narrow ? 2 : 1);
    }

    private async void CapturePreviewIfRequested(object sender, RoutedEventArgs e)
    {
        // Explicit CI-only render capture, never enabled during normal use.
        var path = Environment.GetEnvironmentVariable("WEAVE_UI_CAPTURE");
        if (string.IsNullOrEmpty(path)) return;
        await Task.Delay(700);
        double? previousNavigationTop = null;
        foreach (var button in new[] { Nav0, Nav1, Nav2, Nav4, Nav5, Nav3 })
        {
            var caption = (StackPanel)button.Content;
            if (Math.Abs(button.ActualHeight - 56) > 1 || caption.DesiredSize.Width > button.ActualWidth - button.Padding.Left - button.Padding.Right + 1)
                throw new InvalidOperationException("Navigation row is cramped or clips its label: " + button.Name);
            var top = button.TransformToVisual(NavigationItems).TransformPoint(new global::Windows.Foundation.Point()).Y;
            if (previousNavigationTop is { } previous && Math.Abs(top - previous - 66) > 1)
                throw new InvalidOperationException("Navigation spacing is not uniform: " + button.Name);
            previousNavigationTop = top;
        }
        var navigationBottom = SidebarNavigationScroll.TransformToVisual(RootGrid)
            .TransformPoint(new global::Windows.Foundation.Point()).Y + SidebarNavigationScroll.ActualHeight;
        if (SidebarFooter.TransformToVisual(RootGrid).TransformPoint(new global::Windows.Foundation.Point()).Y < navigationBottom - 1)
            throw new InvalidOperationException("Navigation overlaps the traffic footer");
        if (_page == "0")
        {
            // Real XAML layout regression check; never run in regular user sessions.
            var stacked = RootGrid.ActualWidth < 920;
            if (Grid.GetRow(NodeSelection) != (stacked ? 1 : 0) || Grid.GetRow(TunToggle) != (stacked ? 1 : 0))
                throw new InvalidOperationException("Home controls did not reflow for the window width");
            if (!stacked && RootGrid.ActualHeight >= 600)
                foreach (var control in new FrameworkElement[] { ConnectButton, SubscriptionComboBox, NodeComboBox, RoutingSelector, TunToggle })
                {
                    var top = control.TransformToVisual(ContentScroll).TransformPoint(new global::Windows.Foundation.Point()).Y;
                    if (control.ActualWidth < 40 || top < 0 || top + control.ActualHeight > ContentScroll.ActualHeight)
                        throw new InvalidOperationException("Primary home control is outside the first viewport: " + control.Name);
                }
        }
        if (Environment.GetEnvironmentVariable("WEAVE_PREVIEW_SWITCH_LANGUAGE") == "1")
        {
            var mode = RoutingSelector.SelectedIndex;
            var dns = DnsSelector.SelectedIndex;
            var subscription = SubscriptionComboBox.SelectedItem;
            var node = NodeComboBox.SelectedItem;
            LanguageSelector.SelectedIndex = L.Language == "en" ? 0 : 1;
            await Task.Delay(150);
            var caption = ((StackPanel)Nav0.Content).Children.OfType<TextBlock>().Single().Text;
            if (caption != L.T("连接") || PageTitle.Text != L.T(new[] { "连接", "订阅", "分流规则", "设置", "网络与隐私", "设备同步" }[int.Parse(_page)]) ||
                mode != RoutingSelector.SelectedIndex || dns != DnsSelector.SelectedIndex ||
                !ReferenceEquals(subscription, SubscriptionComboBox.SelectedItem) || !ReferenceEquals(node, NodeComboBox.SelectedItem))
                throw new InvalidOperationException("Live language switch failed or changed network selection");
        }
        var bitmap = new Microsoft.UI.Xaml.Media.Imaging.RenderTargetBitmap();
        await bitmap.RenderAsync(RootGrid, (int)(RootGrid.ActualWidth * 2), (int)(RootGrid.ActualHeight * 2));
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
        if (index >= 4) ((Microsoft.UI.Xaml.Media.SolidColorBrush)theme["WeaveMutedBrush"]).Color = Mix(Color(palette.Muted), Color(palette.Ink), .20);
        Set("WeaveAccentBrush", palette.Accent);
        ((Microsoft.UI.Xaml.Media.SolidColorBrush)theme["WeaveRimBrush"]).Color = Mix(Color(palette.Paper), Color(palette.Ink), palette.Dark ? .16 : .08);
        var glass = (Microsoft.UI.Xaml.Media.LinearGradientBrush)theme["WeaveGlassBrush"];
        glass.GradientStops[0].Color = Color(palette.Paper);
        glass.GradientStops[1].Color = Mix(Color(palette.Paper), Color(palette.Tint), index >= 4 ? .16 : .10);
        var topGlass = glass.GradientStops[0].Color; topGlass.A = 235;
        var bottomGlass = glass.GradientStops[1].Color; bottomGlass.A = 220;
        glass.GradientStops[0].Color = topGlass; glass.GradientStops[1].Color = bottomGlass;
        var sidebar = (Microsoft.UI.Xaml.Media.AcrylicBrush)theme["WeaveSidebarBrush"];
        sidebar.TintColor = sidebar.FallbackColor = Color(palette.Paper);
        sidebar.TintOpacity = palette.Dark ? .48 : .28;
        sidebar.TintLuminosityOpacity = palette.Dark ? .55 : .78;
        var acrylic = (Microsoft.UI.Xaml.Media.AcrylicBrush)theme["WeavePanelAcrylic"];
        acrylic.TintColor = acrylic.FallbackColor = Color(palette.Paper);
        acrylic.TintOpacity = palette.Dark ? .48 : .22;
        acrylic.TintLuminosityOpacity = palette.Dark ? .55 : .72;
        void Gradient(string key, params global::Windows.UI.Color[] colors)
        {
            var brush = (Microsoft.UI.Xaml.Media.LinearGradientBrush)theme[key];
            for (var stop = 0; stop < colors.Length; stop++) brush.GradientStops[stop].Color = colors[stop];
        }
        var paper = Color(palette.Paper); var tint = Color(palette.Tint); var accent = Color(palette.Accent);
        static global::Windows.UI.Color Alpha(global::Windows.UI.Color color, byte opacity) { color.A = opacity; return color; }
        var white = Color("FFFFFF");
        Gradient("WeaveAtmosphereBrush", Mix(Color(palette.Canvas), tint, .12), Color(palette.Canvas), Mix(Color(palette.Canvas), tint, .08));
        AtmosphericRibbons.Opacity = index >= 4 ? .55 : .16;
        Gradient("WeaveHeroBrush", Alpha(Mix(paper, accent, .02), 245), Alpha(Mix(paper, tint, .30), 230), Alpha(Mix(paper, accent, .05), 240));
        Gradient("WeaveIconBrush", Mix(paper, white, palette.Dark ? .10 : .9), Mix(paper, accent, palette.Dark ? .32 : .24));
        Gradient("WeaveLightEdgeBrush", Alpha(Mix(paper, white, palette.Dark ? .36 : 1), 245),
            Alpha(Mix(paper, accent, .16), 95), Alpha(Mix(paper, white, palette.Dark ? .24 : .95), 225));
        Gradient("WeaveInnerEdgeBrush", Alpha(white, 12), Alpha(white, 4), Alpha(white, palette.Dark ? (byte)48 : (byte)170));
        Gradient("WeaveSheenBrush", Alpha(white, palette.Dark ? (byte)12 : (byte)42), Alpha(white, 7), Alpha(white, 0), Alpha(white, 22));
        Gradient("WeaveRibbonBrush", Alpha(tint, 0), Alpha(Mix(tint, accent, .24), 115), Alpha(tint, 38));
        Gradient("WeaveSelectionBrush", Alpha(Mix(paper, white, palette.Dark ? .1 : .8), 240),
            Alpha(Mix(paper, accent, palette.Dark ? .20 : .09), 240), Alpha(Mix(paper, tint, .3), 235));
        Gradient("WeaveActionBrush", Mix(accent, white, .18), Mix(accent, Color(palette.Dark ? "FFFFFF" : "102E3D"), .16));
        RootGrid.RequestedTheme = palette.Dark ? ElementTheme.Dark : ElementTheme.Light;
        UpdateNavigation();
        try { Directory.CreateDirectory(Path.GetDirectoryName(_themePath)!); File.WriteAllText(_themePath, index.ToString()); }
        catch (IOException) { MessageText.Text = L.T("外观已切换，但偏好未能保存。"); }
        catch (UnauthorizedAccessException) { MessageText.Text = L.T("外观已切换，但偏好未能保存。"); }
    }

    private static global::Windows.UI.Color Color(string hex) => global::Windows.UI.Color.FromArgb(255,
        Convert.ToByte(hex[..2], 16), Convert.ToByte(hex.Substring(2, 2), 16), Convert.ToByte(hex.Substring(4, 2), 16));
    private static global::Windows.UI.Color Mix(global::Windows.UI.Color a, global::Windows.UI.Color b, double amount) =>
        global::Windows.UI.Color.FromArgb(255, (byte)(a.R + (b.R - a.R) * amount),
            (byte)(a.G + (b.G - a.G) * amount), (byte)(a.B + (b.B - a.B) * amount));

    private async void RefreshSubscription_Click(object sender, RoutedEventArgs e)
    {
        var selected = SubscriptionListView.SelectedItems.Cast<SubscriptionRecord>().ToArray();
        if (selected.Length == 0) { MessageText.Text = L.T("请先勾选需要更新的订阅"); return; }
        await RunActionAsync(async () =>
        {
            var count = 0;
            foreach (var record in selected)
            {
                if (!record.Source.StartsWith("https://", StringComparison.OrdinalIgnoreCase)) continue;
                await _model.EditAsync(record, record.Name, record.Source, _lifetime.Token);
                count++;
            }
            MessageText.Text = L.F($"已更新 {count} 份远程订阅；本地文件需重新导入。分流引用的旧节点若已移除，需要重新选择。");
        });
    }

    private async void EditSubscription_Click(object sender, RoutedEventArgs e)
    {
        if (_busy) return;
        if (SubscriptionListView.SelectedItems.Count != 1) { MessageText.Text = L.T("编辑时请只选择一份订阅"); return; }
        var record = (SubscriptionRecord)SubscriptionListView.SelectedItems[0];
        var name = new TextBox { Header = L.T("名称"), Text = record.Name };
        var url = new TextBox { Header = L.T("订阅链接（保存后重新获取）"), Text = record.Source,
            IsEnabled = record.Source.StartsWith("https://", StringComparison.OrdinalIgnoreCase) };
        var panel = new StackPanel { Spacing = 14 };
        panel.Children.Add(name); panel.Children.Add(url);
        var dialog = new ContentDialog { Title = L.T("编辑订阅"), Content = panel, PrimaryButtonText = L.T("保存"),
            CloseButtonText = L.T("取消"), XamlRoot = RootGrid.XamlRoot };
        if (await dialog.ShowAsync() == ContentDialogResult.Primary)
            await RunActionAsync(async () => { await _model.EditAsync(record, name.Text, url.Text, _lifetime.Token); MessageText.Text = L.T("订阅已保存"); });
    }

    private async void ExportSubscriptions_Click(object sender, RoutedEventArgs e)
    {
        if (_busy) return;
        var selected = SubscriptionListView.SelectedItems.Cast<SubscriptionRecord>().ToArray();
        if (selected.Length == 0) { MessageText.Text = L.T("请先勾选要分享的订阅；未勾选的不会导出"); return; }
        var warning = new ContentDialog { Title = L.F($"导出 {selected.Length} 份订阅？"),
            Content = L.T("文件包含服务器和连接密码，不加密。请妥善保管，勿上传公开仓库。"),
            PrimaryButtonText = L.T("继续导出"), CloseButtonText = L.T("取消"), XamlRoot = RootGrid.XamlRoot };
        if (await warning.ShowAsync() != ContentDialogResult.Primary) return;
        await RunActionAsync(async () =>
        {
            var path = DesktopFilePicker.SaveZip(WindowNative.GetWindowHandle(this));
            if (path is null) return;
            await Task.Run(() =>
            {
                using var output = File.Create(path);
                using var zip = new System.IO.Compression.ZipArchive(output, System.IO.Compression.ZipArchiveMode.Create);
                for (var i = 0; i < selected.Length; i++)
                {
                    using var writer = new StreamWriter(zip.CreateEntry($"subscription-{i + 1}.yaml").Open());
                    writer.Write(selected[i].ProviderYaml);
                }
            });
            MessageText.Text = L.F($"已导出 {selected.Length} 份订阅。接收方解压后导入 YAML 即可。");
        });
    }

    private sealed record NodeResult(ProxyNode Node, int? Delay) : System.ComponentModel.INotifyPropertyChanged
    {
        public event System.ComponentModel.PropertyChangedEventHandler? PropertyChanged;
        public void RefreshLanguage() => PropertyChanged?.Invoke(this, new(nameof(DisplayName)));
        public string DisplayName => $"{Node.Name} · {(Delay is { } ms ? $"{ms} ms" : L.T("超时 / 不可达"))}";
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
        { MessageText.Text = L.T("请先连接，再测试当前订阅节点"); return; }
        if (!bundle.ProviderNodeCounts.ContainsKey("provider-" + record.Id))
        { MessageText.Text = L.T("此订阅不在当前会话中。请先选择它并重新连接，再测试节点；直连模式不加载订阅。"); return; }
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
                NodeTestText.Text = L.F($"已测 {results.Length} 个，响应 {results.Count(result => result.Delay.HasValue)} 个。点击节点可选为默认出口，重新连接后生效。");
            }
            finally { _probeCancellation = null; }
        });
    }

    private async void RunDiagnostics_Click(object sender, RoutedEventArgs e)
    {
        if (DiagnosticConsent.IsChecked != true) { MessageText.Text = L.T("请先允许本次检测访问测试网站"); return; }
        if (!_model.IsConnected || _model.ActiveBundle is not { } bundle) { MessageText.Text = L.T("请先连接代理"); return; }
        await RunActionAsync(async () =>
        {
            using var cancellation = CancellationTokenSource.CreateLinkedTokenSource(_lifetime.Token);
            _probeCancellation = cancellation;
            try { DiagnosticResults.ItemsSource = await NetworkDiagnostics.RunAsync(bundle, cancellation.Token); }
            finally { _probeCancellation = null; DiagnosticConsent.IsChecked = false; }
        });
    }
    private void StopDiagnostics_Click(object sender, RoutedEventArgs e) => _probeCancellation?.Cancel();

    private sealed record Preferences(string? SubscriptionId, string? NodeId, int Mode, int Dns, bool Ipv6, bool Stun, string CustomDns,
        bool Tun = true, bool ChinaDirect = true, bool Chain = false, string? ChainSubscriptionId = null, string? ChainNodeId = null, string DomainRules = "");
    private string PreferencesPath => Path.Combine(Path.GetDirectoryName(_themePath)!, "preferences.bin");
    private void SavePreferences()
    {
        try
        {
            var value = new Preferences((SubscriptionComboBox.SelectedItem as SubscriptionRecord)?.Id,
                (NodeComboBox.SelectedItem as ProxyNode)?.Id, RoutingSelector.SelectedIndex, DnsSelector.SelectedIndex,
                Ipv6Toggle.IsOn, StunToggle.IsOn, CustomDnsBox.Text.Trim(), TunToggle.IsOn, ChinaDirectToggle.IsOn, ChainToggle.IsOn,
                (ChainSubscription.SelectedItem as SubscriptionRecord)?.Id, (ChainNode.SelectedItem as ProxyNode)?.Id, DomainRulesBox.Text);
            Directory.CreateDirectory(Path.GetDirectoryName(PreferencesPath)!);
            var pending = PreferencesPath + ".pending";
            var data = System.Text.Json.JsonSerializer.SerializeToUtf8Bytes(value);
            try { File.WriteAllBytes(pending, new WindowsDpapiProtector().Protect(data)); }
            finally { System.Security.Cryptography.CryptographicOperations.ZeroMemory(data); }
            File.Move(pending, PreferencesPath, overwrite: true);
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException or System.ComponentModel.Win32Exception) { MessageText.Text = L.T("偏好暂未保存"); }
    }
    private void LoadPreferences()
    {
        try
        {
            if (!File.Exists(PreferencesPath)) return;
            var data = new WindowsDpapiProtector().Unprotect(File.ReadAllBytes(PreferencesPath));
            Preferences? value;
            try { value = System.Text.Json.JsonSerializer.Deserialize<Preferences>(data); }
            finally { System.Security.Cryptography.CryptographicOperations.ZeroMemory(data); }
            if (value is null) return;
            var record = _model.Subscriptions.FirstOrDefault(item => item.Id == value.SubscriptionId);
            if (record is not null) { SubscriptionComboBox.SelectedItem = record; NodeComboBox.SelectedItem = record.Nodes.FirstOrDefault(node => node.Id == value.NodeId); }
            RoutingSelector.SelectedIndex = Math.Clamp(value.Mode, 0, 2);
            DnsSelector.SelectedIndex = Math.Clamp(value.Dns, 0, 3);
            Ipv6Toggle.IsOn = value.Ipv6; StunToggle.IsOn = value.Stun; CustomDnsBox.Text = value.CustomDns;
            TunToggle.IsOn = value.Tun; ChinaDirectToggle.IsOn = value.ChinaDirect; ChainToggle.IsOn = value.Chain;
            ChainSubscription.SelectedItem = _model.Subscriptions.FirstOrDefault(item => item.Id == value.ChainSubscriptionId);
            ChainNode.SelectedItem = (ChainSubscription.SelectedItem as SubscriptionRecord)?.Nodes.FirstOrDefault(node => node.Id == value.ChainNodeId);
            DomainRulesBox.Text = value.DomainRules;
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException or System.Text.Json.JsonException or System.ComponentModel.Win32Exception) { }
    }

    private async void MainWindow_Closed(object sender, WindowEventArgs args)
        => await ShutdownAsync(closeWindow: false);

    private async Task ShutdownAsync(bool closeWindow)
    {
        if (_shuttingDown || _shutdownComplete) return;
        _shuttingDown = true;
        SavePreferences();
        _closed = true;
        System.Net.NetworkInformation.NetworkChange.NetworkAddressChanged -= NetworkAddressChanged;
        _networkCancellation?.Cancel();
        _tray?.Dispose();
        _lifetime.Cancel();
        _trafficTimer.Stop();
        try { await Task.WhenAll(StopShareAsync(), _model.DisposeAsync().AsTask()); }
        catch (Exception) { /* Encrypted system-proxy recovery record remains for the next launch. */ }
        finally
        {
            _lifetime.Dispose();
            _shutdownComplete = true;
            if (closeWindow) Close();
        }
    }

    private void ChainSubscription_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (!_initialized) return;
        ChainNode.ItemsSource = (ChainSubscription.SelectedItem as SubscriptionRecord)?.Nodes;
        ChainNode.SelectedIndex = -1;
    }
    private void RoutingMode_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (!_initialized) return;
        SubscriptionComboBox.IsEnabled = NodeComboBox.IsEnabled = AutomaticNodeButton.IsEnabled = ChainToggle.IsEnabled = RoutingSelector.SelectedIndex != 2;
        ModeExplanation.Text = RoutingSelector.SelectedIndex switch
        {
            1 => L.T("全部走默认出口（或默认链），忽略应用、域名和国内直连规则。"),
            2 => L.T("全部直连，不经过任何代理节点。隐私端口拦截仍生效。"),
            _ => L.T("应用规则 → 自订规则 → 局域网 / 国内直连 → 默认出口。"),
        };
    }
    private void ValidateRules_Click(object sender, RoutedEventArgs e)
    {
        try { var rules = DomainRoute.Parse(DomainRulesBox.Text); SavePreferences(); MessageText.Text = L.F($"已保存 {rules.Count} 条规则，重新连接后生效"); }
        catch (InvalidDataException error) { MessageText.Text = error.Message; }
    }
    private async void TrafficTick(object? sender, object e)
    {
        // No polling while hidden/minimized; back off after three idle samples.
        if (_closed || _hiddenToTray || _trafficBusy || !_model.IsConnected || _model.ActiveBundle is not { } bundle ||
            (AppWindow.Presenter is Microsoft.UI.Windowing.OverlappedPresenter presenter && presenter.State == Microsoft.UI.Windowing.OverlappedPresenterState.Minimized)) return;
        _trafficBusy = true;
        try
        {
            using var controller = new MihomoController(bundle);
            var traffic = await controller.ReadTrafficAsync(_lifetime.Token);
            if (_closed || !_model.IsConnected || !ReferenceEquals(bundle, _model.ActiveBundle)) return;
            DownloadRate.Text = Rate(traffic.Down); UploadRate.Text = Rate(traffic.Up);
            _idleTrafficSamples = traffic.Down == 0 && traffic.Up == 0 ? Math.Min(3, _idleTrafficSamples + 1) : 0;
            _trafficTimer.Interval = TimeSpan.FromSeconds(_idleTrafficSamples >= 3 ? 10 : 2);
        }
        catch (Exception error) when (error is HttpRequestException or OperationCanceledException or System.Text.Json.JsonException or IOException or KeyNotFoundException or FormatException or InvalidOperationException) { }
        finally { _trafficBusy = false; }
    }
    private static string Rate(long bytes) => bytes >= 1024 * 1024 ? $"{bytes / 1048576.0:F1} MB/s" : $"{Math.Max(0, bytes) / 1024.0:F1} KB/s";

    private void NetworkAddressChanged(object? sender, EventArgs e)
        => DispatcherQueue.TryEnqueue(ScheduleNetworkCheck);

    private async void ScheduleNetworkCheck()
    {
        if (_closed || !_model.IsConnected) return;
        _networkCancellation?.Cancel();
        var cancellation = CancellationTokenSource.CreateLinkedTokenSource(_lifetime.Token);
        _networkCancellation = cancellation;
        _model.InvalidateNetworkEvidence();
        try
        {
            await Task.Delay(2500, cancellation.Token);
            // Wait for an in-flight import/connect instead of abandoning the recheck.
            while (_busy) await Task.Delay(500, cancellation.Token);
            await _model.RecheckAsync(cancellation.Token);
        }
        catch (OperationCanceledException) { }
        catch (Exception) { if (!_closed) MessageText.Text = L.T("网络核验失败，可手动重新连接；未改走直连。"); }
        finally
        {
            if (ReferenceEquals(_networkCancellation, cancellation)) _networkCancellation = null;
            cancellation.Dispose();
        }
    }

    private async void RecheckNetwork_Click(object sender, RoutedEventArgs e)
        => await RunActionAsync(() => _model.RecheckAsync(_lifetime.Token));

    private async void ReconnectNetwork_Click(object sender, RoutedEventArgs e)
    {
        if (_busy) return;
        var stopped = false;
        await RunActionAsync(async () => { await _model.DisconnectAsync(); stopped = true; });
        if (stopped && !_closed) ConnectButton_Click(sender, e);
    }
}
