using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Weave.Windows.Core;
using WinRT.Interop;
using WinColor = global::Windows.UI.Color;

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
    private CancellationTokenSource? _operationCancellation;
    private CancellationTokenSource? _connectCancellation;
    private CancellationTokenSource? _networkCancellation;
    private CancellationTokenSource? _samplingCancellation;
    private int _samplingGeneration;
    private int _samplingSeconds;
    private int _idleTrafficSamples;
    private bool _minimized;
    private TrayIcon? _tray;
    private bool _hiddenToTray;
    private bool _dataLoaded;
    private readonly TaskCompletionSource _dataReady = new(TaskCreationOptions.RunContinuationsAsynchronously);
    private bool _settingsDirty;
    private bool _connectAccent = true;
    private readonly Style? _accentConnectStyle;
    private Style? _accentReceiveStyle;
    private UIElement[][] _pageElements = Array.Empty<UIElement[]>();
    private ConnectionEndpoint? _observedBundle;
    private long _observedRevision;
    private bool _observedConnected;
    private bool _diagnosticsStale;
    private DateTimeOffset? _diagnosticsMeasuredAt;
    private readonly string _themePath = Path.Combine(AppPaths.DataRoot, "appearance.txt");
    // Ctrl+1..6 follow the sidebar order, not the page numbers.
    private static readonly string[] AcceleratorPages = { "0", "1", "2", "4", "5", "3" };
    // AdaptiveTrigger in MainWindow.xaml switches to the compact layout below this many effective pixels of client width.
    // The preview window's outer width includes ~16 px of frame, so 640 lands at ~624 and stays under the threshold.
    private const double CompactLayoutThreshold = 720;
    private const double CompactPreviewWidth = 640;

    public MainWindow()
    {
        LoadLanguage();
        InitializeComponent();
        _accentConnectStyle = ConnectButton.Style;
        foreach (var tile in new ContentControl[] { ConnectionHero, TrafficTile, ExitCard, ConnectionNote, SubscriptionsPanel, NodesPanel, SettingsPanel, BehaviorCard, NetworkSettingsCard, PrivacySettingsCard, RoutesPanel, DiagnosticsPanel, TransferPanel })
        {
            tile.Shadow = new Microsoft.UI.Xaml.Media.ThemeShadow();
            tile.Translation = new System.Numerics.Vector3(0, 0, tile == ConnectionHero ? 10 : 5);
        }
        InitializeChrome();
        BuildPages();
        _accentReceiveStyle = ReceiveButton.Style;
        // Once a preview is waiting, its confirm button is the card's primary action; "receive" steps back to a neutral button.
        ReceiveConfirmArea.RegisterPropertyChangedCallback(UIElement.VisibilityProperty, (_, _) =>
        {
            if (ReceiveConfirmArea.Visibility == Visibility.Visible) ReceiveButton.ClearValue(FrameworkElement.StyleProperty);
            else ReceiveButton.Style = _accentReceiveStyle;
        });
        // Lines that only exist while they have text must not leave an empty gap in their card's stack.
        foreach (var line in new TextBlock[] { DiagnosticEvidenceText, DiagnosticProgress, NodeTestText, ShareCode })
        {
            var target = line;
            void Sync() => target.Visibility = string.IsNullOrEmpty(target.Text) ? Visibility.Collapsed : Visibility.Visible;
            target.RegisterPropertyChangedCallback(TextBlock.TextProperty, (_, _) => Sync());
            Sync();
        }
        ObserveMessages();
        _initialized = true;
        RootGrid.Loaded += CapturePreviewIfRequested;
        var area = Microsoft.UI.Windowing.DisplayArea.GetFromWindowId(AppWindow.Id, Microsoft.UI.Windowing.DisplayAreaFallback.Primary).WorkArea;
        var scale = Math.Max(1, GetDpiForWindow(WindowNative.GetWindowHandle(this)) / 96.0);
        AppWindow.Resize(new global::Windows.Graphics.SizeInt32(Math.Min((int)(1280 * scale), area.Width - 40), Math.Min((int)(860 * scale), area.Height - 60)));
        if (Environment.GetEnvironmentVariable("WEAVE_PREVIEW_WIDE") == "1")
            AppWindow.Resize(new global::Windows.Graphics.SizeInt32((int)(1400 * scale), (int)(900 * scale)));
        if (Environment.GetEnvironmentVariable("WEAVE_PREVIEW_COMPACT") == "1")
            AppWindow.Resize(new global::Windows.Graphics.SizeInt32((int)(CompactPreviewWidth * scale), (int)(760 * scale)));
        // Connecting is disabled until the encrypted library has been read (see InitializeDataAsync).
        ConnectButton.IsEnabled = false;
        _model.StatusChanged += (_, _) => DispatcherQueue.TryEnqueue(() => { if (!_closed) UpdateStatus(); });
        _model.Subscriptions.CollectionChanged += (_, _) => UpdateEmptyStates();
        _model.AppRoutes.CollectionChanged += (_, _) => UpdateEmptyStates();
        InitializeAppearanceControls();
        LanguageSelector.SelectedIndex = L.Language == "en" ? 1 : 0;
        RootGrid.Language = L.Language;
        RoutingSelector.ItemsSource = new[] { L.T("规则"), L.T("全局"), L.T("直连") };
        RoutingSelector.SelectedIndex = 0;
        DnsSelector.ItemsSource = new[] { L.T("加密 DNS"), L.T("广告过滤"), L.T("家庭过滤"), L.T("自订 DNS") };
        DnsSelector.SelectedIndex = 0;
        SubscriptionComboBox.ItemsSource = _model.Subscriptions;
        SubscriptionListView.ItemsSource = _model.Subscriptions;
        RouteSubscriptionComboBox.ItemsSource = _model.Subscriptions;
        ChainSubscription.ItemsSource = _model.Subscriptions;
        RouteListView.ItemsSource = _model.AppRoutes;
        RouteTargetModeComboBox.ItemsSource = new[] { L.T("自动测速"), L.T("固定节点"), L.T("直连"), L.T("阻止") };
        RouteTargetModeComboBox.SelectedIndex = 0;
        LoadAndApplyAppearance();

        Closed += MainWindow_Closed;
        _tray = new TrayIcon(WindowNative.GetWindowHandle(this),
            CurrentTrayState,
            () => DispatcherQueue.TryEnqueue(ShowFromTray),
            () => DispatcherQueue.TryEnqueue(TrayToggleConnection),
            () => DispatcherQueue.TryEnqueue(async () => await ShutdownAsync(closeWindow: true)),
            () => DispatcherQueue.TryEnqueue(ScheduleNetworkCheck));
        System.Net.NetworkInformation.NetworkChange.NetworkAddressChanged += NetworkAddressChanged;
        AppWindow.Closing += async (_, args) =>
        {
            if (_shutdownComplete) return;
            args.Cancel = true;
            if (_tray?.Available == true && !_shuttingDown) { _hiddenToTray = true; StopSampling(); AppWindow.Hide(); return; }
            await ShutdownAsync(closeWindow: true);
        };
        // Minimising pauses sampling; restoring (which activates the window) resumes it.
        AppWindow.Changed += (_, _) =>
        {
            var minimized = IsMinimized();
            if (minimized == _minimized) return;
            _minimized = minimized;
            DispatcherQueue.TryEnqueue(UpdateSampling);
        };
        Activated += (_, _) => { if (!_closed) UpdateSampling(); };
        UpdateStatus();
        UpdateNavigation();
        if (int.TryParse(Environment.GetEnvironmentVariable("WEAVE_PREVIEW_PAGE"), out var previewPage) && previewPage is >= 0 and <= 5)
            NavigateTo(previewPage.ToString());
        RefreshAddresses_Click(this, new RoutedEventArgs());
        _ = InitializeDataAsync();
    }

    // Registry recovery and the encrypted vault are read on a worker; selections and preferences are restored here.
    private async Task InitializeDataAsync()
    {
        try
        {
            try { await _model.LoadAsync(_lifetime.Token); }
            catch (OperationCanceledException) { return; }
            catch (Exception) { if (!_closed) MessageText.Text = L.T("本地配置读取失败。原文件已保留，请检查当前 Windows 用户与文件权限。"); }
            if (_closed) return;
            if (PreviewFixtures.Demo && _model.Subscriptions.Count == 0)
            {
                _model.AddPreviewSubscriptions(PreviewFixtures.Subscriptions());
                PreviewFixtures.SeedHistory(_history, _model.Subscriptions);
            }
            if (_model.Subscriptions.Count > 0)
            {
                SubscriptionComboBox.SelectedIndex = 0;
                RouteSubscriptionComboBox.SelectedIndex = 0;
                SubscriptionListView.SelectedItem = _model.Subscriptions[0]; // also fills the node table
            }
            LoadPreferences();
            _dataLoaded = true;
            ConnectButton.IsEnabled = !_busy;
            RefreshHealthRings();
            UpdateEmptyStates();
            if (PreviewFixtures.Connected) EnterPreviewSession();
            UpdateStatus();
        }
        finally { _dataReady.TrySetResult(); }
    }

    private void SubscriptionComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_initialized) return;
        NodeComboBox.ItemsSource = (SubscriptionComboBox.SelectedItem as SubscriptionRecord)?.Nodes;
        NodeComboBox.SelectedItem = null;
        _missingFixedNodeId = null;
        if (SubscriptionComboBox.SelectedItem is not null) _missingSubscriptionId = null;
        NodeComboBox.PlaceholderText = L.T("自动选择 · 最低延迟");
        RefreshQuickNodes();
        UpdateDashboard();
        MarkSettingsDirty();
        // Keep the library highlight in step when the subscription is picked on the connect page.
        if (SubscriptionComboBox.SelectedItem is SubscriptionRecord picked && !SubscriptionListView.SelectedItems.Contains(picked))
            SubscriptionListView.SelectedItem = picked;
    }

    [System.Runtime.InteropServices.DllImport("user32.dll")]
    private static extern uint GetDpiForWindow(IntPtr hwnd);

    private void SubscriptionListView_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!_initialized) return;
        if (e.AddedItems.FirstOrDefault() is SubscriptionRecord selected)
        {
            SubscriptionComboBox.SelectedItem = selected;
            ShowNodes(selected.Nodes);
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
        await RunActionAsync(async () =>
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
            await _model.AddOrReplaceRouteAsync(new WindowsAppRoute
            {
                ProcessName = processName,
                DisplayName = processName,
                Target = target,
            });
            MarkSettingsDirty();
            MessageText.Text = L.F($"已保存 {processName} 的分流规则；重新连接后生效");
        });
    }

    private async void DeleteRouteButton_Click(object sender, RoutedEventArgs e)
    {
        if (_busy || sender is not Button { Tag: string processName }) return;
        await RunActionAsync(async () =>
        {
            await _model.RemoveRouteAsync(processName);
            MarkSettingsDirty();
            MessageText.Text = L.F($"已删除 {processName} 的分流规则；重新连接后生效");
        });
    }

    private void ProcessNameBox_KeyDown(object sender, KeyRoutedEventArgs e) => SubmitOnEnter(e, () => AddRouteButton_Click(sender, e));
    private void SubscriptionUrlBox_KeyDown(object sender, KeyRoutedEventArgs e) => SubmitOnEnter(e, () => ImportUrlButton_Click(sender, e));
    private void ReceiveCode_KeyDown(object sender, KeyRoutedEventArgs e) => SubmitOnEnter(e, () => ReceiveLan_Click(sender, e));
    private static void SubmitOnEnter(KeyRoutedEventArgs e, Action submit)
    {
        if (e.Key != global::Windows.System.VirtualKey.Enter) return;
        e.Handled = true;
        submit();
    }

    private async void ImportTextButton_Click(object sender, RoutedEventArgs e)
    {
        await RunCancellableAsync(async token =>
        {
            var record = await _model.ImportTextAsync(
                SubscriptionNameBox.Text,
                "clipboard://manual",
                SubscriptionTextBox.Text,
                token);
            SubscriptionComboBox.SelectedItem = record;
            MessageText.Text = L.F($"已导入 {record.Name}，发现 {record.Nodes.Count} 个节点");
        });
    }

    private async void ImportUrlButton_Click(object sender, RoutedEventArgs e)
    {
        await RunCancellableAsync(async token =>
        {
            var record = await _model.ImportUrlAsync(
                SubscriptionNameBox.Text,
                SubscriptionUrlBox.Text,
                token);
            SubscriptionComboBox.SelectedItem = record;
            MessageText.Text = L.F($"已导入 {record.Name}，发现 {record.Nodes.Count} 个节点");
        });
    }

    private async void ImportFileButton_Click(object sender, RoutedEventArgs e)
    {
        await RunCancellableAsync(async token =>
        {
            var path = DesktopFilePicker.Open(WindowNative.GetWindowHandle(this));
            if (path is null)
            {
                return;
            }

            var record = await _model.ImportFileAsync(SubscriptionNameBox.Text, path, token);
            SubscriptionComboBox.SelectedItem = record;
            MessageText.Text = L.F($"已导入 {record.Name}，发现 {record.Nodes.Count} 个节点");
        });
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
        if (RoutingSelector.SelectedIndex != 2 && _missingFixedNodeId is not null)
        {
            MessageText.Text = L.T("原固定节点已移除，请重新选择；不会自动切换出口");
            return;
        }
        if (RoutingSelector.SelectedIndex != 2 && subscription is null)
        {
            // Nothing to connect to yet: take the user to where a subscription is added instead of just saying so.
            NavigateTo("1");
            MessageText.Text = L.T("请先导入并选择订阅");
            return;
        }

        SavePreferences();
        await RunActionAsync(async () =>
        {
            using var connection = CancellationTokenSource.CreateLinkedTokenSource(_lifetime.Token);
            connection.CancelAfter(TimeSpan.FromSeconds(60));
            _connectCancellation = connection;
            ConnectButton.IsEnabled = true;
            ConnectButton.Content = L.T("取消连接");
            UpdateStatus();
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
            _settingsDirty = false;
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
        BusyBar.Visibility = Visibility.Visible;
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
            _messageKind = MessageKind.Warning;
            MessageText.Text = L.T("操作已取消或超时。连接超时请检查核心服务及防火墙；订阅超时请检查当前网络。");
        }
        catch (HttpRequestException)
        {
            _messageKind = MessageKind.Error;
            MessageText.Text = L.T("网络请求失败，请检查连接与订阅地址。未展示原始请求地址，以保护订阅凭据。");
        }
        catch (Exception exception)
        {
            _messageKind = MessageKind.Error;
            MessageText.Text = exception.Message;
        }
        finally
        {
            _busy = false;
            BusyBar.Visibility = Visibility.Collapsed;
            ConnectButton.IsEnabled = _dataLoaded;
            ImportPanel.IsHitTestVisible = true;
            RoutesPanel.IsHitTestVisible = true;
            SubscriptionsPanel.IsHitTestVisible = true;
            if (!_closed) UpdateStatus();
        }

        UpdateStatus();
    }

    // Imports, transfers and other cancellable work share one token that Esc and window close both cancel.
    private Task RunCancellableAsync(Func<CancellationToken, Task> action) => RunActionAsync(async () =>
    {
        using var operation = CancellationTokenSource.CreateLinkedTokenSource(_lifetime.Token);
        _operationCancellation = operation;
        try { await action(operation.Token); }
        finally { _operationCancellation = null; }
    });

    private void UpdateStatus()
    {
        if (!ReferenceEquals(_observedBundle, _model.ActiveConnection) || _observedRevision != _model.NetworkRevision ||
            _observedConnected != _model.IsConnected)
        {
            _probeCancellation?.Cancel();
            _activePrivacyDialog?.Hide();
            if (DiagnosticResults.ItemsSource is not null) _diagnosticsStale = true;
            if (SubscriptionNodesList.ItemsSource is NodeRow[] rows && rows.Any(row => row.Tested))
            {
                ShowNodes((SubscriptionComboBox.SelectedItem as SubscriptionRecord)?.Nodes);
                NodeTestText.Text = L.T("网络已变化，请重新测速");
            }
            _observedBundle = _model.ActiveConnection;
            _observedRevision = _model.NetworkRevision;
            _observedConnected = _model.IsConnected;
        }
        DiagnosticEvidenceText.Text = _diagnosticsStale ? L.T("历史结果：网络或节点已变化，请重新检测") :
            _diagnosticsMeasuredAt is { } measured ? L.T("检测时间") + " · " + measured.ToString("g", L.Language == "en" ? System.Globalization.CultureInfo.GetCultureInfo("en-US") : System.Globalization.CultureInfo.GetCultureInfo("zh-CN")) : "";
        var connecting = _connectCancellation is not null;
        ConnectButton.Content = connecting ? L.T("取消连接") : _model.IsConnected ? L.T("断开连接") : L.T("连接");
        if (!_model.IsConnected) _settingsDirty = false;
        UpdateDashboard();
        UpdateApplyBar();
        UpdateSampling();
        _tray?.SetTip("Weave · " + _model.Status);
    }

    // Changes made while connected take effect on the next connection; offer to apply them in one click.
    private void MarkSettingsDirty()
    {
        if (!_initialized || _changingLanguage || _previewSettling || !_model.IsConnected) return;
        _settingsDirty = true;
        UpdateApplyBar();
    }
    private void UpdateApplyBar() => ApplyBar.IsOpen = _settingsDirty && _model.IsConnected;
    private void Setting_Changed(object sender, RoutedEventArgs e) => MarkSettingsDirty();
    private void CustomDnsBox_TextChanged(object sender, TextChangedEventArgs e) => MarkSettingsDirty();

    private void AutomaticNode_Click(object sender, RoutedEventArgs e)
    {
        _missingFixedNodeId = null;
        NodeComboBox.SelectedItem = null;
        NodeComboBox.PlaceholderText = L.T("自动选择 · 最低延迟");
        MarkSettingsDirty();
    }

    private void Navigate_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not Button { Tag: string page }) return;
        NavigateTo(page);
    }
    private void Navigate_Checked(object sender, RoutedEventArgs e)
    {
        if (sender is RadioButton { Tag: string page }) NavigateTo(page);
    }
    private void NavAccelerator_Invoked(KeyboardAccelerator sender, KeyboardAcceleratorInvokedEventArgs args)
    {
        var index = sender.Key - global::Windows.System.VirtualKey.Number1;
        if (index is < 0 or >= 6) return;
        args.Handled = true;
        NavigateTo(AcceleratorPages[index]);
    }
    private void EscapeAccelerator_Invoked(KeyboardAccelerator sender, KeyboardAcceleratorInvokedEventArgs args)
    {
        if (_connectCancellation is { } connection) connection.Cancel();
        else if (_operationCancellation is { } operation) operation.Cancel();
        else if (!string.IsNullOrEmpty(MessageText.Text)) MessageText.Text = "";
        else return;
        args.Handled = true;
    }


    // --- Traffic sampling: pushed by ConnectionSampling from a worker, marshalled to the UI thread, text only. ---

    private bool IsMinimized() => AppWindow.Presenter is Microsoft.UI.Windowing.OverlappedPresenter { State: Microsoft.UI.Windowing.OverlappedPresenterState.Minimized };

    private void UpdateSampling()
    {
        var wanted = !_closed && !_hiddenToTray && _model.IsConnected && !IsMinimized();
        if (wanted && _samplingCancellation is null) { _idleTrafficSamples = 0; StartSampling(2); }
        else if (!wanted && _samplingCancellation is not null) StopSampling();
    }

    private void StartSampling(int seconds)
    {
        StopSampling();
        var cancellation = CancellationTokenSource.CreateLinkedTokenSource(_lifetime.Token);
        _samplingCancellation = cancellation;
        _samplingSeconds = seconds;
        var generation = _samplingGeneration;
        var sampling = new ConnectionSampling(
            _ => Task.FromResult(_model.IsConnected && _model.ActiveConnection is not null),
            async token =>
            {
                if (_model.ActiveConnection is not { } bundle) return (-1L, -1L);
                try { return await _model.ReadTrafficAsync(bundle, token); }
                catch (Exception error) when (error is HttpRequestException or System.Text.Json.JsonException or IOException or KeyNotFoundException or FormatException or InvalidOperationException)
                { return (-1L, -1L); }
                catch (OperationCanceledException) when (!token.IsCancellationRequested) { return (-1L, -1L); } // one slow request, not shutdown
            });
        sampling.Updated += (_, sample) => DispatcherQueue.TryEnqueue(() => ApplySample(sample, generation));
        _ = ObserveSamplingAsync(sampling, cancellation, TimeSpan.FromSeconds(seconds));
    }

    private void StopSampling()
    {
        var cancellation = _samplingCancellation;
        _samplingCancellation = null;
        _samplingGeneration++;
        try { cancellation?.Cancel(); }
        catch (ObjectDisposedException) { }
    }

    // Awaiting the run task is how a sampling failure reaches the UI; cancellation is the normal way out.
    private async Task ObserveSamplingAsync(ConnectionSampling sampling, CancellationTokenSource cancellation, TimeSpan interval)
    {
        try { await sampling.RunAsync(cancellation.Token, interval); }
        catch (OperationCanceledException) { }
        catch (Exception error)
        {
            if (!_closed && ReferenceEquals(_samplingCancellation, cancellation))
            {
                _traffic.Clear();
                UpdateTrafficTexts();
                _messageKind = MessageKind.Error;
                MessageText.Text = error.Message;
            }
        }
        finally
        {
            if (ReferenceEquals(_samplingCancellation, cancellation)) _samplingCancellation = null;
            cancellation.Dispose();
        }
    }

    // --- Tray: menu state is read when the menu opens, so it always matches the window. ---

    private TrayState CurrentTrayState() => new(_model.IsConnected, _connectCancellation is not null, !_dataLoaded || (_busy && _connectCancellation is null));

    private void ShowFromTray()
    {
        _hiddenToTray = false;
        AppWindow.Show();
        Activate();
        UpdateSampling();
    }

    private void TrayToggleConnection()
    {
        if (_closed) return;
        // Without a usable selection the connect attempt could only fail with a message nobody would see: show the window instead.
        if (!_model.IsConnected && _connectCancellation is null && RoutingSelector.SelectedIndex != 2 && SubscriptionComboBox.SelectedItem is null)
        {
            ShowFromTray();
            return;
        }
        ConnectButton_Click(this, new RoutedEventArgs());
    }

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
                if (await UpdateSubscriptionWithReviewAsync(record, record.Name, record.Source)) count++;
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
        var panel = new StackPanel { Spacing = 16 };
        panel.Children.Add(name); panel.Children.Add(url);
        var dialog = new ContentDialog { Title = L.T("编辑订阅"), Content = panel, PrimaryButtonText = L.T("保存"),
            CloseButtonText = L.T("取消"), XamlRoot = RootGrid.XamlRoot, RequestedTheme = RootGrid.ActualTheme,
            DefaultButton = ContentDialogButton.Primary };
        if (await dialog.ShowAsync() == ContentDialogResult.Primary)
            await RunActionAsync(async () => { if (await UpdateSubscriptionWithReviewAsync(record, name.Text, url.Text)) MessageText.Text = L.T("订阅已保存"); });
    }

    private async void ExportSubscriptions_Click(object sender, RoutedEventArgs e)
    {
        if (_busy) return;
        var selected = SubscriptionListView.SelectedItems.Cast<SubscriptionRecord>().ToArray();
        if (selected.Length == 0) { MessageText.Text = L.T("请先勾选要分享的订阅；未勾选的不会导出"); return; }
        var warning = new ContentDialog { Title = L.F($"导出 {selected.Length} 份订阅？"),
            Content = L.T("文件包含服务器和连接密码，不加密。请妥善保管，勿上传公开仓库。"),
            PrimaryButtonText = L.T("继续导出"), CloseButtonText = L.T("取消"), XamlRoot = RootGrid.XamlRoot, RequestedTheme = RootGrid.ActualTheme,
            DefaultButton = ContentDialogButton.Close };
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

    private void NodeList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (SubscriptionNodesList.SelectedItem is NodeRow row) NodeComboBox.SelectedItem = row.Node;
    }

    private async void RunDiagnostics_Click(object sender, RoutedEventArgs e)
    {
        if (DiagnosticConsent.IsChecked != true) { MessageText.Text = L.T("请先允许本次检测访问测试网站"); return; }
        if (!_model.IsConnected || _model.ActiveConnection is not { } bundle) { MessageText.Text = L.T("请先连接代理"); return; }
        await RunActionAsync(async () =>
        {
            using var cancellation = CancellationTokenSource.CreateLinkedTokenSource(_lifetime.Token);
            cancellation.CancelAfter(TimeSpan.FromSeconds(45));
            _probeCancellation = cancellation;
            var revision = _model.NetworkRevision;
            var rows = new DiagnosticResultList();
            DiagnosticResults.ItemsSource = rows;
            _diagnosticsStale = true;
            _diagnosticsMeasuredAt = null;
            DiagnosticEvidenceText.Text = "";
            var acceptingProgress = true;
            var progress = new Progress<ProbeResult>(result =>
            {
                if (!acceptingProgress || cancellation.IsCancellationRequested || !EvidenceStillCurrent(bundle, revision)) return;
                rows.Record(result);
                DiagnosticProgress.Text = $"{rows.CompletedCount} / {rows.Count}";
            });
            DiagnosticProgress.Text = $"0 / {NetworkDiagnostics.Targets.Count + 2}";
            try
            {
                var results = await NetworkDiagnostics.RunAsync(bundle, cancellation.Token, progress);
                cancellation.Token.ThrowIfCancellationRequested();
                if (!EvidenceStillCurrent(bundle, revision)) return;
                foreach (var result in results) rows.Record(result);
                _diagnosticsStale = false;
                _diagnosticsMeasuredAt = DateTimeOffset.Now;
                DiagnosticProgress.Text = L.T("本次检测已完成；可达不代表解锁或无泄漏");
            }
            catch (OperationCanceledException)
            {
                _diagnosticsStale = true;
                DiagnosticProgress.Text = L.T("检测已停止，未完成项不作结论");
                MessageText.Text = L.T("检测已停止，未完成项不作结论");
            }
            finally { acceptingProgress = false; _probeCancellation = null; DiagnosticConsent.IsChecked = false; }
        });
    }
    private bool EvidenceStillCurrent(ConnectionEndpoint bundle, long revision) => !_closed && _model.IsConnected &&
        ReferenceEquals(bundle, _model.ActiveConnection) && revision == _model.NetworkRevision;
    private void StopDiagnostics_Click(object sender, RoutedEventArgs e) => _probeCancellation?.Cancel();

    private sealed record Preferences(string? SubscriptionId, string? NodeId, int Mode, int Dns, bool Ipv6, bool Stun, string CustomDns,
        bool Tun = true, bool ChinaDirect = true, bool Chain = false, string? ChainSubscriptionId = null, string? ChainNodeId = null, string DomainRules = "");
    private string PreferencesPath => Path.Combine(Path.GetDirectoryName(_themePath)!, "preferences.bin");
    private void SavePreferences()
    {
        // Until the library has been read the selectors are empty; saving now would overwrite the stored choices.
        if (!_dataLoaded) return;
        try
        {
            var value = new Preferences((SubscriptionComboBox.SelectedItem as SubscriptionRecord)?.Id ?? _missingSubscriptionId,
                (NodeComboBox.SelectedItem as ProxyNode)?.Id ?? _missingFixedNodeId, RoutingSelector.SelectedIndex, DnsSelector.SelectedIndex,
                Ipv6Toggle.IsOn, StunToggle.IsOn, CustomDnsBox.Text.Trim(), TunToggle.IsOn, ChinaDirectToggle.IsOn, ChainToggle.IsOn,
                (ChainSubscription.SelectedItem as SubscriptionRecord)?.Id, (ChainNode.SelectedItem as ProxyNode)?.Id, DomainRulesBox.Text);
            Directory.CreateDirectory(Path.GetDirectoryName(PreferencesPath)!);
            var data = System.Text.Json.JsonSerializer.SerializeToUtf8Bytes(value);
            try { AtomicFile.WriteAllBytes(PreferencesPath, new WindowsDpapiProtector().Protect(data)); }
            finally { System.Security.Cryptography.CryptographicOperations.ZeroMemory(data); }
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
            else if (value.SubscriptionId is not null) { SubscriptionComboBox.SelectedIndex = -1; _missingSubscriptionId = value.SubscriptionId; }
            _missingFixedNodeId = value.NodeId is not null && NodeComboBox.SelectedItem is null ? value.NodeId : null;
            NodeComboBox.PlaceholderText = L.T(_missingFixedNodeId is null ? "自动选择 · 最低延迟" : "原固定节点已移除，请重新选择；不会自动切换出口");
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
        _minSize?.Dispose();
        _lifetime.Cancel();
        StopSampling();
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
        MarkSettingsDirty();
    }
    private void ValidateRules_Click(object sender, RoutedEventArgs e)
    {
        try { var rules = DomainRoute.Parse(DomainRulesBox.Text); SavePreferences(); MarkSettingsDirty(); MessageText.Text = L.F($"已保存 {rules.Count} 条规则，重新连接后生效"); }
        catch (InvalidDataException error) { MessageText.Text = error.Message; }
    }

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
