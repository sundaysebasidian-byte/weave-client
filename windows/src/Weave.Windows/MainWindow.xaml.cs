using System.Collections.ObjectModel;
using System.Runtime.InteropServices;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Weave.Windows.Core;
using Windows.Foundation;
using Windows.Graphics;
using Windows.UI;
using WinRT.Interop;

namespace Weave.Windows;

public sealed partial class MainWindow : Window
{
    private const int TrafficHistoryLength = 60;

    private readonly WeaveAppModel _model = new();
    private readonly CancellationTokenSource _lifetime = new();
    private readonly DispatcherQueueTimer _clock;
    private readonly DispatcherQueueTimer _toastTimer;
    private readonly Queue<(long Down, long Up)> _trafficHistory = new();
    private readonly ObservableCollection<RouteItem> _routeItems = new();
    private readonly HashSet<string> _dirtySettings = new(StringComparer.Ordinal);

    private SubscriptionRecord? _activeSubscription;
    private string? _selectedNodeId;
    private string? _runningSubscriptionId;
    private string? _runningNodeId;
    private bool _initialized;
    private bool _syncingSelection;
    private bool _connectionBusy;
    private bool _userStopping;
    private bool _wasConnected;
    private bool _pendingBarDismissed;
    private string _lastStatus = string.Empty;
    private DateTimeOffset? _connectedAt;
    private TrafficSnapshot? _lastTraffic;
    private long _peakDown;
    private long _peakUp;
    private Action? _toastAction;

    public MainWindow()
    {
        InitializeComponent();
        _clock = DispatcherQueue.CreateTimer();
        _clock.Interval = TimeSpan.FromSeconds(1);
        _clock.Tick += (_, _) => OnClockTick();
        _toastTimer = DispatcherQueue.CreateTimer();
        _toastTimer.IsRepeating = false;
        _toastTimer.Tick += (_, _) => ToastBar.IsOpen = false;

        ConfigureWindow();
        ConfigureKeyboard();
        ApplyHeroGlow();

        string? loadError = null;
        try
        {
            _model.Load();
        }
        catch (Exception exception)
        {
            loadError = $"本地配置读取失败，原文件已保留：{exception.Message}";
        }

        OverviewSubscriptionComboBox.ItemsSource = _model.Subscriptions;
        NodesSubscriptionComboBox.ItemsSource = _model.Subscriptions;
        SubscriptionListView.ItemsSource = _model.Subscriptions;
        RouteSubscriptionComboBox.ItemsSource = _model.Subscriptions;
        NodeListView.ItemsSource = _visibleNodes;
        RouteListView.ItemsSource = _routeItems;
        _model.Subscriptions.CollectionChanged += (_, _) => OnSubscriptionsChanged();
        _model.AppRoutes.CollectionChanged += (_, _) => OnRoutesChanged();
        _model.StatusChanged += (_, _) => DispatcherQueue.TryEnqueue(OnModelStatusChanged);

        LoadNetworkOptionsIntoControls();
        RestorePreference();
        RestoreRouteSubscriptionSelection();
        OnRoutesChanged();
        UpdateSubscriptionsPage();
        UpdateAboutSection();

        _initialized = true;
        NavView.SelectedItem = NavView.MenuItems[0];
        RootGrid.SizeChanged += (_, _) => ApplyResponsiveLayout();
        RootGrid.Loaded += RootGrid_Loaded;
        Closed += MainWindow_Closed;
        UpdateConnectionUi();

        if (loadError is not null) Notify(loadError, InfoBarSeverity.Error, "无法读取本地数据");
    }

    // ------------------------------------------------------------------ window chrome

    [DllImport("user32.dll")]
    private static extern uint GetDpiForWindow(IntPtr hwnd);

    private void ConfigureWindow()
    {
        ExtendsContentIntoTitleBar = true;
        SetTitleBar(AppTitleBar);
        AppWindow.TitleBar.PreferredHeightOption = TitleBarHeightOption.Tall;
        AppWindow.TitleBar.ButtonBackgroundColor = Microsoft.UI.Colors.Transparent;
        AppWindow.TitleBar.ButtonInactiveBackgroundColor = Microsoft.UI.Colors.Transparent;

        var scale = GetDpiForWindow(WindowNative.GetWindowHandle(this)) / 96d;
        if (scale <= 0) scale = 1;
        var workArea = DisplayArea.GetFromWindowId(AppWindow.Id, DisplayAreaFallback.Primary).WorkArea;
        var width = Math.Min((int)(1220 * scale), workArea.Width - (int)(32 * scale));
        var height = Math.Min((int)(800 * scale), workArea.Height - (int)(32 * scale));
        AppWindow.MoveAndResize(new RectInt32(
            workArea.X + (workArea.Width - width) / 2,
            workArea.Y + (workArea.Height - height) / 2,
            width,
            height));
    }

    private void RootGrid_Loaded(object sender, RoutedEventArgs e)
    {
        UpdateCaptionInset();
        ApplyResponsiveLayout();
        _ = CaptureIfRequestedAsync();
    }

    private void UpdateCaptionInset()
    {
        var scale = RootGrid.XamlRoot?.RasterizationScale ?? 1;
        var inset = AppWindow.TitleBar.RightInset / scale;
        if (inset > 0) CaptionInsetColumn.Width = new GridLength(inset + 8);
    }

    private void ApplyHeroGlow()
    {
        var accent = Application.Current.Resources.TryGetValue("SystemAccentColor", out var value) && value is Color color
            ? color
            : Color.FromArgb(0xFF, 0x00, 0x78, 0xD4);
        HeroGlow.Background = new LinearGradientBrush
        {
            StartPoint = new Point(0, 0),
            EndPoint = new Point(1, 1),
            GradientStops =
            {
                new GradientStop { Color = Color.FromArgb(0x38, accent.R, accent.G, accent.B), Offset = 0 },
                new GradientStop { Color = Color.FromArgb(0x10, accent.R, accent.G, accent.B), Offset = 0.55 },
                new GradientStop { Color = Color.FromArgb(0x00, accent.R, accent.G, accent.B), Offset = 1 },
            },
        };
    }

    private void ApplyResponsiveLayout()
    {
        var narrow = RootGrid.ActualWidth is > 0 and < 980;
        if (narrow)
        {
            OverviewChartColumn.Width = new GridLength(1, GridUnitType.Star);
            OverviewExitColumn.Width = new GridLength(0);
            Grid.SetColumn(ExitCard, 0);
            Grid.SetRow(ExitCard, 1);
        }
        else
        {
            OverviewChartColumn.Width = new GridLength(1.7, GridUnitType.Star);
            OverviewExitColumn.Width = new GridLength(1, GridUnitType.Star);
            Grid.SetColumn(ExitCard, 1);
            Grid.SetRow(ExitCard, 0);
        }

        OverviewLowerGrid.ColumnSpacing = narrow ? 0 : 12;
    }

    // ------------------------------------------------------------------ navigation & keyboard

    private void ConfigureKeyboard()
    {
        RootGrid.KeyboardAcceleratorPlacementMode = KeyboardAcceleratorPlacementMode.Hidden;
        string[] pages = { "overview", "nodes", "subscriptions", "routes", "network" };
        for (var index = 0; index < pages.Length; index++)
        {
            var page = pages[index];
            AddAccelerator(global::Windows.System.VirtualKey.Number1 + index, global::Windows.System.VirtualKeyModifiers.Control, () => Navigate(page));
        }

        AddAccelerator(global::Windows.System.VirtualKey.Enter, global::Windows.System.VirtualKeyModifiers.Control,
            () => _ = ToggleConnectionAsync(),
            () => FocusManager.GetFocusedElement(RootGrid.XamlRoot) is not TextBox { AcceptsReturn: true });
        AddAccelerator(global::Windows.System.VirtualKey.F, global::Windows.System.VirtualKeyModifiers.Control, () =>
        {
            Navigate("nodes");
            DispatcherQueue.TryEnqueue(() => NodeSearchBox.Focus(FocusState.Keyboard));
        });
        AddAccelerator(global::Windows.System.VirtualKey.F5, global::Windows.System.VirtualKeyModifiers.None, () =>
        {
            if (NodesPage.Visibility == Visibility.Visible && CheckAllButton.IsEnabled) _ = CheckAllNodesAsync();
        });
    }

    private void AddAccelerator(global::Windows.System.VirtualKey key, global::Windows.System.VirtualKeyModifiers modifiers,
        Action action, Func<bool>? canInvoke = null)
    {
        var accelerator = new KeyboardAccelerator { Key = key, Modifiers = modifiers };
        accelerator.Invoked += (_, args) =>
        {
            if (canInvoke?.Invoke() == false) return;
            args.Handled = true;
            action();
        };
        RootGrid.KeyboardAccelerators.Add(accelerator);
    }

    private void NavView_SelectionChanged(NavigationView sender, NavigationViewSelectionChangedEventArgs args)
    {
        if (args.SelectedItem is NavigationViewItem { Tag: string tag }) ShowPage(tag);
    }

    private void ShowPage(string tag)
    {
        OverviewPage.Visibility = tag == "overview" ? Visibility.Visible : Visibility.Collapsed;
        NodesPage.Visibility = tag == "nodes" ? Visibility.Visible : Visibility.Collapsed;
        SubscriptionsPage.Visibility = tag == "subscriptions" ? Visibility.Visible : Visibility.Collapsed;
        RoutesPage.Visibility = tag == "routes" ? Visibility.Visible : Visibility.Collapsed;
        NetworkPage.Visibility = tag == "network" ? Visibility.Visible : Visibility.Collapsed;
        if (tag == "overview") DrawTrafficChart();
    }

    private void Navigate(string tag)
    {
        var item = NavView.MenuItems.Concat(NavView.FooterMenuItems)
            .OfType<NavigationViewItem>()
            .FirstOrDefault(candidate => candidate.Tag as string == tag);
        if (item is not null) NavView.SelectedItem = item;
    }

    private void ManageNodesButton_Click(object sender, RoutedEventArgs e) => Navigate("nodes");

    private void GoToSubscriptionsButton_Click(object sender, RoutedEventArgs e) => Navigate("subscriptions");

    // ------------------------------------------------------------------ active exit

    private void RestorePreference()
    {
        var preference = _model.Preference;
        var subscription = _model.Subscriptions.FirstOrDefault(item => item.Id == preference.SubscriptionId)
                           ?? _model.Subscriptions.FirstOrDefault();
        _activeSubscription = subscription;
        _selectedNodeId = subscription is not null && subscription.Id == preference.SubscriptionId &&
                          subscription.Nodes.Any(node => node.Id == preference.NodeId)
            ? preference.NodeId
            : null;
        ResyncSubscriptionSelectors();
        RebuildNodeItems();
        UpdateExitUi();
    }

    private void SubscriptionSelector_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_initialized || _syncingSelection) return;
        // Replacing a record in the collection briefly clears selectors; keep the current exit in that case.
        if (sender is Selector { SelectedItem: SubscriptionRecord record })
        {
            SetActiveSubscription(record);
        }
        else
        {
            ResyncSubscriptionSelectors();
        }
    }

    private void SetActiveSubscription(SubscriptionRecord? record)
    {
        if (ReferenceEquals(record, _activeSubscription))
        {
            ResyncSubscriptionSelectors();
            return;
        }

        var sameSubscription = record is not null && record.Id == _activeSubscription?.Id;
        _activeSubscription = record;
        if (!sameSubscription)
        {
            _selectedNodeId = null;
        }
        else if (_selectedNodeId is not null && record!.Nodes.All(node => node.Id != _selectedNodeId))
        {
            _selectedNodeId = null;
            Notify("原先固定的节点已不在订阅中，出口已改为自动选择。", InfoBarSeverity.Warning);
        }

        CancelQualityCheck();
        ResyncSubscriptionSelectors();
        RebuildNodeItems();
        PersistPreference();
        UpdateExitUi();
        UpdateConnectionUi();
    }

    private void SetSelectedNode(string? nodeId)
    {
        if (_selectedNodeId == nodeId) return;
        _selectedNodeId = nodeId;
        PersistPreference();
        foreach (var item in _nodeItems) item.IsExit = item.Node.Id == nodeId;
        SyncNodeListSelection();
        UpdateExitUi();
        UpdatePendingBar();
    }

    private void ResyncSubscriptionSelectors()
    {
        _syncingSelection = true;
        try
        {
            OverviewSubscriptionComboBox.SelectedItem = _activeSubscription;
            NodesSubscriptionComboBox.SelectedItem = _activeSubscription;
            SubscriptionListView.SelectedItem = _activeSubscription;
        }
        finally
        {
            _syncingSelection = false;
        }
    }

    private void PersistPreference()
    {
        try
        {
            _model.SavePreference(_activeSubscription?.Id, _selectedNodeId);
        }
        catch (Exception)
        {
            // Remembering the last exit is a convenience; failing to store it must not interrupt the user.
        }
    }

    private void UseAutoExitButton_Click(object sender, RoutedEventArgs e) => SetSelectedNode(null);

    private void UpdateExitUi()
    {
        var node = SelectedNode;
        var exitName = node?.DisplayName ?? "自动选择";
        ExitGlyph.Glyph = node is null ? "" : "";
        NodesExitGlyph.Glyph = ExitGlyph.Glyph;
        ExitNodeText.Text = exitName;
        ExitNodeDetailText.Text = node is null
            ? "在订阅内按延迟测速自动切换"
            : $"{node.Protocol.ToUpperInvariant()} · 固定出口";
        NodesExitRun.Text = exitName;
        NodesExitHintRun.Text = node is null ? "  ·  点击列表中的节点即可固定出口" : "  ·  右键节点可测试或复制名称";
        UseAutoExitButton.Visibility = node is null ? Visibility.Collapsed : Visibility.Visible;
        NodesUseAutoButton.Visibility = UseAutoExitButton.Visibility;
        HeroRouteText.Text = _activeSubscription is null
            ? "导入订阅后即可连接"
            : $"{_activeSubscription.Name} · {exitName}";
        UpdateNodeHealthCard();
    }

    private ProxyNode? SelectedNode => _selectedNodeId is null
        ? null
        : _activeSubscription?.Nodes.FirstOrDefault(node => node.Id == _selectedNodeId);

    // ------------------------------------------------------------------ connection

    private async void ConnectButton_Click(object sender, RoutedEventArgs e) => await ToggleConnectionAsync();

    private async void ReconnectButton_Click(object sender, RoutedEventArgs e)
    {
        if (_connectionBusy || !_model.IsConnected) return;
        await DisconnectAsync(announce: false);
        await ConnectAsync();
    }

    private async Task ToggleConnectionAsync()
    {
        if (_connectionBusy) return;
        if (_model.IsConnected)
        {
            await DisconnectAsync(announce: true);
        }
        else
        {
            await ConnectAsync();
        }
    }

    private async Task ConnectAsync()
    {
        if (_activeSubscription is not { } subscription)
        {
            Notify("请先导入订阅。", InfoBarSeverity.Warning);
            Navigate("subscriptions");
            return;
        }

        _connectionBusy = true;
        UpdateConnectionUi();
        try
        {
            await _model.ConnectAsync(subscription.Id, _selectedNodeId, _model.NetworkOptions, _lifetime.Token);
            _runningSubscriptionId = subscription.Id;
            _runningNodeId = _selectedNodeId;
            _dirtySettings.Clear();
            _pendingBarDismissed = false;
            _connectedAt = DateTimeOffset.Now;
            ResetTrafficHistory();
            Notify("Mihomo 已启动。请在 Windows 网络设置中确认 TUN 适配器与实际出口。", InfoBarSeverity.Success, "已连接");
        }
        catch (OperationCanceledException)
        {
        }
        catch (Exception exception)
        {
            Notify(exception.Message, InfoBarSeverity.Error, "连接失败");
        }
        finally
        {
            _connectionBusy = false;
            UpdateConnectionUi();
        }
    }

    private async Task DisconnectAsync(bool announce)
    {
        _connectionBusy = true;
        _userStopping = true;
        CancelQualityCheck();
        UpdateConnectionUi();
        try
        {
            await _model.DisconnectAsync();
            if (announce) Notify("已断开，系统网络配置保持不变。", InfoBarSeverity.Informational);
        }
        catch (Exception exception)
        {
            Notify(exception.Message, InfoBarSeverity.Error, "断开时出错");
        }
        finally
        {
            _userStopping = false;
            _connectionBusy = false;
            _connectedAt = null;
            _runningSubscriptionId = null;
            _runningNodeId = null;
            _dirtySettings.Clear();
            UpdateConnectionUi();
        }
    }

    private void OnModelStatusChanged()
    {
        var connected = _model.IsConnected;
        if (_wasConnected && !connected && !_userStopping && !_connectionBusy)
        {
            _connectedAt = null;
            _runningSubscriptionId = null;
            _runningNodeId = null;
            CancelQualityCheck();
            Notify("Mihomo 核心意外停止，流量已不再经过 Weave。可以重新连接，或检查核心与订阅配置。",
                InfoBarSeverity.Error, "连接已中断");
        }

        if (connected != _wasConnected || _model.Status != _lastStatus)
        {
            UpdateConnectionUi();
        }

        UpdateTrafficText();
    }

    private void UpdateConnectionUi()
    {
        var connected = _model.IsConnected;
        _wasConnected = connected;
        _lastStatus = _model.Status;

        var state = _connectionBusy
            ? connected ? "正在断开…" : "正在连接…"
            : connected ? "已连接" : "未连接";
        StatusText.Text = state;
        StatusDotOn.Visibility = Show(connected && !_connectionBusy);
        StatusDotBusy.Visibility = Show(_connectionBusy);
        StatusDotIdle.Visibility = Show(!connected && !_connectionBusy);

        HeroStateText.Text = state;
        HeroRing.IsActive = _connectionBusy;
        HeroGlow.Opacity = connected ? 1 : 0;
        HeroGlyphOn.Visibility = Show(connected);
        HeroGlyphIdle.Visibility = Show(!connected);
        HeroDetailText.Text = connected || _connectionBusy ? _model.Status : _activeSubscription is null ? "" : "流量将经由 TUN 接管";
        ConnectButtonText.Text = connected ? "断开连接" : "连接";
        ConnectButton.Style = (Style)Application.Current.Resources[connected ? "DefaultButtonStyle" : "AccentButtonStyle"];
        ConnectButton.IsEnabled = !_connectionBusy && (connected || _activeSubscription is not null);
        CoreMissingBar.IsOpen = !connected && WeaveAppModel.MihomoPath is null;

        if (connected) _clock.Start(); else _clock.Stop();
        if (!connected) ResetTrafficHistory();

        UpdateProbeAvailability();
        UpdateNodeHealthCard();
        UpdatePendingBar();
        UpdateTrafficText();
        UpdateDuration();
    }

    // ------------------------------------------------------------------ pending changes

    private void MarkDirty(string reason)
    {
        if (!_model.IsConnected) return;
        _dirtySettings.Add(reason);
        _pendingBarDismissed = false;
        UpdatePendingBar();
    }

    private void UpdatePendingBar()
    {
        var reasons = new List<string>();
        if (_model.IsConnected && !_connectionBusy)
        {
            if (_activeSubscription?.Id != _runningSubscriptionId || _selectedNodeId != _runningNodeId) reasons.Add("出口");
            reasons.AddRange(_dirtySettings);
        }

        if (reasons.Count == 0)
        {
            _pendingBarDismissed = false;
            PendingBar.IsOpen = false;
            return;
        }

        PendingBar.Message = $"{string.Join("、", reasons)}已更改，当前连接仍在使用旧配置；重新连接后生效。";
        PendingBar.IsOpen = !_pendingBarDismissed;
    }

    private void PendingBar_Closed(InfoBar sender, InfoBarClosedEventArgs args)
    {
        if (args.Reason == InfoBarCloseReason.CloseButton) _pendingBarDismissed = true;
    }

    // ------------------------------------------------------------------ overview metrics

    private void UpdateTrafficText()
    {
        var traffic = _model.IsConnected ? _model.Traffic : null;
        if (!ReferenceEquals(traffic, _lastTraffic))
        {
            _lastTraffic = traffic;
            if (traffic is not null)
            {
                _peakDown = Math.Max(_peakDown, traffic.DownloadBytesPerSecond);
                _peakUp = Math.Max(_peakUp, traffic.UploadBytesPerSecond);
            }
        }

        if (!_model.IsConnected)
        {
            DownRateText.Text = "—";
            UpRateText.Text = "—";
            DownPeakText.Text = "峰值 —";
            UpPeakText.Text = "峰值 —";
            TotalDownText.Text = "—";
            TotalUpText.Text = "上传 —";
            return;
        }

        DownRateText.Text = traffic is null ? "等待数据" : UiFormat.Rate(traffic.DownloadBytesPerSecond);
        UpRateText.Text = traffic is null ? "等待数据" : UiFormat.Rate(traffic.UploadBytesPerSecond);
        DownPeakText.Text = $"峰值 {UiFormat.Rate(_peakDown)}";
        UpPeakText.Text = $"峰值 {UiFormat.Rate(_peakUp)}";
        TotalDownText.Text = traffic?.DownloadTotalBytes is { } down ? UiFormat.Bytes(down) : "—";
        TotalUpText.Text = traffic?.UploadTotalBytes is { } up ? $"上传 {UiFormat.Bytes(up)}" : "上传 —";
    }

    private void OnClockTick()
    {
        UpdateDuration();
        if (!_model.IsConnected) return;
        var traffic = _model.Traffic;
        _trafficHistory.Enqueue((traffic?.DownloadBytesPerSecond ?? 0, traffic?.UploadBytesPerSecond ?? 0));
        while (_trafficHistory.Count > TrafficHistoryLength) _trafficHistory.Dequeue();
        DrawTrafficChart();
    }

    private void UpdateDuration()
    {
        if (_model.IsConnected && _connectedAt is { } since)
        {
            DurationText.Text = UiFormat.Duration(DateTimeOffset.Now - since);
            DurationDetailText.Text = $"开始于 {since:HH:mm}";
        }
        else
        {
            DurationText.Text = "—";
            DurationDetailText.Text = _connectionBusy ? "正在处理…" : "未连接";
        }
    }

    private void ResetTrafficHistory()
    {
        _trafficHistory.Clear();
        _peakDown = 0;
        _peakUp = 0;
        _lastTraffic = null;
        DrawTrafficChart();
    }

    private void TrafficChart_SizeChanged(object sender, SizeChangedEventArgs e) => DrawTrafficChart();

    private void DrawTrafficChart()
    {
        var width = TrafficChart.ActualWidth;
        var height = TrafficChart.ActualHeight;
        var samples = _trafficHistory.ToArray();
        TrafficEmptyText.Visibility = samples.Length == 0 ? Visibility.Visible : Visibility.Collapsed;
        if (samples.Length == 0 || width <= 0 || height <= 0)
        {
            DownLine.Points = new PointCollection();
            UpLine.Points = new PointCollection();
            DownArea.Points = new PointCollection();
            TrafficScaleText.Text = string.Empty;
            TrafficHalfScaleText.Text = string.Empty;
            return;
        }

        var peak = samples.Max(sample => Math.Max(sample.Down, sample.Up));
        var ceiling = NiceCeiling(Math.Max(peak, 16 * 1024));
        var step = width / (TrafficHistoryLength - 1);
        var offset = TrafficHistoryLength - samples.Length;
        var down = new PointCollection();
        var up = new PointCollection();
        for (var index = 0; index < samples.Length; index++)
        {
            var x = (index + offset) * step;
            down.Add(new Point(x, Y(samples[index].Down)));
            up.Add(new Point(x, Y(samples[index].Up)));
        }

        var area = new PointCollection { new Point(offset * step, height) };
        foreach (var point in down) area.Add(point);
        area.Add(new Point(width, height));
        DownLine.Points = down;
        UpLine.Points = up;
        DownArea.Points = area;
        TrafficScaleText.Text = UiFormat.Rate(ceiling);
        TrafficHalfScaleText.Text = UiFormat.Rate(ceiling / 2);

        double Y(long value) => height - 1 - value / (double)ceiling * (height - 2);
    }

    private static long NiceCeiling(long value)
    {
        long magnitude = 1;
        while (magnitude * 10 <= value) magnitude *= 10;
        foreach (var factor in new[] { 1, 2, 5, 10 })
        {
            if (magnitude * factor >= value) return magnitude * factor;
        }

        return magnitude * 10;
    }

    private void UpdateNetworkChips()
    {
        var options = _model.NetworkOptions;
        ChipDnsText.Text = options.DnsProfile switch
        {
            DnsProfile.AdBlock => "广告过滤 DNS",
            DnsProfile.Family => "家庭过滤 DNS",
            DnsProfile.Custom => "自定义 DNS",
            _ => "加密 DNS",
        };
        ChipIpv6Text.Text = options.Ipv6Enabled ? "IPv6" : "仅 IPv4";
        ChipStun.Visibility = options.BlockUdpStun ? Visibility.Visible : Visibility.Collapsed;
        ChipRoutesText.Text = _model.AppRoutes.Count == 0 ? "无应用分流" : $"{_model.AppRoutes.Count} 条应用分流";
    }

    // ------------------------------------------------------------------ notifications

    private void Notify(string message, InfoBarSeverity severity, string? title = null,
        string? actionLabel = null, Action? action = null)
    {
        _toastTimer.Stop();
        _toastAction = action;
        ToastBar.Severity = severity;
        ToastBar.Title = title ?? string.Empty;
        ToastBar.Message = message;
        ToastActionButton.Content = actionLabel;
        ToastActionButton.Visibility = action is null ? Visibility.Collapsed : Visibility.Visible;
        ToastHost.Visibility = Visibility.Visible;
        ToastBar.IsOpen = true;
        if (severity is InfoBarSeverity.Success or InfoBarSeverity.Informational)
        {
            _toastTimer.Interval = TimeSpan.FromSeconds(action is null ? 4 : 8);
            _toastTimer.Start();
        }
    }

    private void ToastActionButton_Click(object sender, RoutedEventArgs e)
    {
        var action = _toastAction;
        _toastAction = null;
        ToastBar.IsOpen = false;
        action?.Invoke();
    }

    private void ToastBar_Closed(InfoBar sender, InfoBarClosedEventArgs args)
    {
        _toastTimer.Stop();
        _toastAction = null;
        ToastHost.Visibility = Visibility.Collapsed;
    }

    private async Task RunActionAsync(Func<Task> action, string failureTitle)
    {
        try
        {
            await action();
        }
        catch (OperationCanceledException)
        {
            Notify("操作已取消。", InfoBarSeverity.Informational);
        }
        catch (Exception exception)
        {
            Notify(exception.Message, InfoBarSeverity.Error, failureTitle);
        }
    }

    private static Visibility Show(bool visible) => visible ? Visibility.Visible : Visibility.Collapsed;

    // ------------------------------------------------------------------ lifetime

    private async void MainWindow_Closed(object sender, WindowEventArgs args)
    {
        _clock.Stop();
        _toastTimer.Stop();
        _lifetime.Cancel();
        CancelQualityCheck();
        await _model.DisposeAsync();
        _lifetime.Dispose();
    }
}
