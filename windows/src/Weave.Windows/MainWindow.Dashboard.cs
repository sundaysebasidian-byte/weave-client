using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Input;
using Weave.Windows.Core;
using WinPoint = global::Windows.Foundation.Point;

namespace Weave.Windows;

/// <summary>The connection page's live content, the quick exit grid, and the subscription and node tables.</summary>
public sealed partial class MainWindow
{
    private static bool CaptureRequested => !string.IsNullOrEmpty(Environment.GetEnvironmentVariable("WEAVE_UI_CAPTURE"));

    private readonly TrafficSeries _traffic = new(60);
    private readonly NodeHistory _history = new();
    private DateTimeOffset? _connectedAt;
    private int _dashState = -1; // 0 idle, 1 connecting, 2 connected, 3 connected but unconfirmed
    // Replaced as soon as the saved appearance is applied; the default keeps early selection events from drawing with nulls.
    private KnotPalette _knotPalette = new("2B3866", "7BA3A3", "A599C2", "27324F", "CF6A50", "5E657F", false);
    private ProxyNode[] _shownNodes = Array.Empty<ProxyNode>();
    private NodeRow[] _allNodeRows = Array.Empty<NodeRow>();
    private readonly List<string> _regionCodes = new() { "*" };
    private string _regionFilter = "*";
    private string _nodeQuery = "";
    private bool _syncingQuick, _syncingChips, _importOpen;
    private DispatcherQueueTimer? _uptimeTimer, _probeRefreshTimer;

    private void BuildPages()
    {
        _pageElements = new[]
        {
            new UIElement[] { ConnectPage }, new UIElement[] { SubscriptionsPage }, new UIElement[] { RoutesPanel },
            new UIElement[] { SettingsPage }, new UIElement[] { DiagnosticsPanel }, new UIElement[] { TransferPanel },
        };
        foreach (var page in _pageElements) PageMotion.Entrance(page[0], 0);
        LoadCloseBehavior();
        ContentScroll.SizeChanged += (_, _) => UpdateListHeights();
        RootGrid.SizeChanged += (_, _) => { UpdateTitleBarInset(); UpdateCellWidths(); };
        RootGrid.Loaded += (_, _) => { UpdateTitleBarInset(); UpdateCellWidths(); UpdateListHeights(); UpdateDashboard(); };
        QuickNodes.SizeChanged += (_, _) => UpdateCellWidths();
        HeroCity.SizeChanged += (_, _) => FitHeroTitle();
        ConnectionHero.SizeChanged += (_, _) => FitKnot();
        ThemeGallery.SizeChanged += (_, _) => UpdateCellWidths();
    }

    // Two-column grids size their cells from the available width so they always fill the tile.
    private void UpdateCellWidths()
    {
        if (QuickNodes.ActualWidth > 40 && QuickNodes.ItemsPanelRoot is ItemsWrapGrid quick) quick.ItemWidth = Math.Floor((QuickNodes.ActualWidth - 2) / 2);
        if (ThemeGallery.ActualWidth > 40 && ThemeGallery.ItemsPanelRoot is ItemsWrapGrid gallery) gallery.ItemWidth = Math.Floor((ThemeGallery.ActualWidth - 8) / 2);
    }

    // The two lists scroll inside their tiles; their limits follow the window so a tile never outgrows the page.
    private void UpdateListHeights()
    {
        if (ContentScroll.ActualWidth > 0)
            PageStack.Width = Math.Max(0, Math.Min(1120, ContentScroll.ActualWidth - ContentScroll.Padding.Left - ContentScroll.Padding.Right));
        var viewport = ContentScroll.ViewportHeight;
        if (viewport <= 0) return;
        SubscriptionNodesList.MaxHeight = Math.Max(240, viewport - 150);
        SubscriptionListView.MaxHeight = Math.Max(120, viewport - 150 - (ImportPanel.Visibility == Visibility.Visible ? 360 : 0));
        // The library page fills the window, so a short list does not leave a hollow card floating at the top.
        NodesPanel.MinHeight = Math.Max(320, ContentScroll.ActualHeight - 32);
    }

    private readonly record struct ExitInfo(string Title, string Eyebrow, string Protocol, int? Latency);

    private ExitInfo DescribeExit(bool direct, bool empty, SubscriptionRecord? record, ProxyNode? node)
    {
        var english = L.Language == "en";
        if (direct) return new(L.T("直连"), "DIRECT", "", null);
        if (empty) return new(L.T("尚未连接"), L.T("请先导入订阅"), "", null);
        if (node is not null)
        {
            var code = NodeRegion.Code(node);
            var protocol = node.Protocol.ToUpperInvariant();
            var (tested, last) = _history.Latest(node.Id);
            return new(NodeRegion.Title(node.Name, code, english), (code.Length > 0 ? code + " · " : "") + protocol, protocol, tested ? last : null);
        }
        if (record is not null)
        {
            var known = record.Nodes.Select(item => _history.Latest(item.Id)).Where(item => item.Tested && item.Last.HasValue).Select(item => item.Last!.Value).ToArray();
            return new(L.T("自动"), "AUTO · " + record.Name, "", known.Length > 0 ? known.Min() : null);
        }
        return new(L.T("尚未连接"), "", "", null);
    }

    // The illustration takes under half of the hero so the headline, chips and buttons keep their room in narrower tiles.
    private void FitKnot()
    {
        var size = Math.Clamp((ConnectionHero.ActualWidth - 50) * 0.42, 160, 300);
        if (Math.Abs(Knot.Width - size) > 1) Knot.Width = Knot.Height = Math.Round(size);
    }

    // The headline is as large as the column allows: a short city name is monumental, a longer phrase steps down instead of being cut.
    private TextBlock? _heroProbe;

    private void FitHeroTitle()
    {
        var text = HeroCity.Text;
        if (string.IsNullOrEmpty(text)) return;
        var room = HeroCity.ActualWidth;
        double chosen;
        if (room <= 0) chosen = text.Length <= 3 ? 92 : text.Length <= 5 ? 72 : 54;
        else
        {
            var probe = _heroProbe ??= new TextBlock
            {
                FontFamily = HeroCity.FontFamily, FontWeight = HeroCity.FontWeight, CharacterSpacing = HeroCity.CharacterSpacing,
                TextWrapping = TextWrapping.NoWrap,
            };
            probe.Text = text;
            chosen = 34;
            foreach (var size in new double[] { 92, 72, 54, 44, 38 })
            {
                probe.FontSize = size;
                probe.Measure(new global::Windows.Foundation.Size(double.PositiveInfinity, double.PositiveInfinity));
                if (probe.DesiredSize.Width <= room) { chosen = size; break; }
            }
        }
        if (Math.Abs(HeroCity.FontSize - chosen) > 0.1) HeroCity.FontSize = chosen;
    }

    /// <summary>Repaints everything that describes the connection: hero, pill, traffic tile, knot. Safe to call often.</summary>
    private void UpdateDashboard()
    {
        if (!_initialized || _closed) return;
        var connecting = _connectCancellation is not null;
        var connected = _model.IsConnected;
        var state = connecting ? 1 : !connected ? 0 : _model.Health == ConnectionHealthState.Reachable ? 2 : 3;
        var direct = RoutingSelector.SelectedIndex == 2;
        var empty = _dataLoaded && _model.Subscriptions.Count == 0 && !direct;
        var exit = DescribeExit(direct, empty, SubscriptionComboBox.SelectedItem as SubscriptionRecord, NodeComboBox.SelectedItem as ProxyNode);

        HeroCity.Text = exit.Title;
        FitHeroTitle();
        HeroCity.Opacity = state is 2 or 3 ? 1 : 0.4;
        HeroEyebrow.Text = exit.Eyebrow.ToUpperInvariant();
        HeroProtocol.Text = exit.Protocol;
        HeroProtocolChip.Visibility = exit.Protocol.Length > 0 ? Visibility.Visible : Visibility.Collapsed;
        HeroLatency.Text = exit.Latency is { } ms ? $"{ms} ms" : "";
        HeroLatencyChip.Visibility = exit.Latency.HasValue ? Visibility.Visible : Visibility.Collapsed;
        HeroMode.Text = direct ? L.T("直连") : TunToggle.IsOn ? "TUN" : L.T("系统代理");
        HeroModeChip.Visibility = empty ? Visibility.Collapsed : Visibility.Visible;

        var dot = ThemeBrush(state switch { 2 => "WeaveSunBrush", 1 or 3 => "WeaveCautionBrush", _ => "WeaveFaintBrush" });
        ConnectionDot.Fill = dot;
        StatusDot.Fill = dot;
        // The pill is a polite live region, so it names the state only; live numbers stay out of it.
        StatusText.Text = state switch
        {
            2 => exit.Title + (exit.Latency is { } latency ? $" · {latency} ms" : ""),
            3 => L.T("网络待确认"),
            1 => _model.Status,
            _ => empty ? L.T("请先导入订阅") : L.T("未连接"),
        };

        // Primary (accent) while a connection can be started or a subscription imported; neutral while it can only be cancelled or ended.
        var accent = state == 0;
        if (accent != _connectAccent)
        {
            _connectAccent = accent;
            ConnectButton.Style = accent ? _accentConnectStyle : (Style)Application.Current.Resources["QuietPillButton"];
        }
        if (empty && state == 0) ConnectButton.Content = L.T("导入订阅");

        if (connected && _connectedAt is null) { _connectedAt = DateTimeOffset.UtcNow; _traffic.Clear(); StartUptimeTimer(); }
        else if (!connected && _connectedAt is not null) { _connectedAt = null; StopUptimeTimer(); _traffic.Clear(); }
        UpdateTrafficTexts(exit.Latency);

        if (state != _dashState)
        {
            var previous = _dashState;
            _dashState = state;
            switch (state)
            {
                case 2 when previous is 0 or 1: Knot.Weave(TimeSpan.FromMilliseconds(1300)); break;
                case 2 or 3: Knot.Apply(_knotPalette, KnotState.Connected); break;
                case 1: Knot.Apply(_knotPalette, KnotState.Connecting, 0.3); break;
                default: Knot.Apply(_knotPalette, KnotState.Idle); break;
            }
            if (state == 2 && previous == 1) Toast(L.F($"已连接 · {exit.Title}"), MessageKind.Success);
        }
    }

    private void UpdateTrafficTexts(int? latency = null)
    {
        var connected = _model.IsConnected;
        var have = connected && _traffic.Count > 0;
        var (dv, du) = have ? RateFormat.Parts(_traffic.LastDown) : ("—", "");
        DownloadValue.Text = dv; DownloadUnit.Text = du;
        var (uv, uu) = have ? RateFormat.Parts(_traffic.LastUp) : ("—", "");
        UploadValue.Text = uv; UploadUnit.Text = uu;
        var (sv, su) = have ? RateFormat.Total(_traffic.TotalDown + _traffic.TotalUp) : ("—", "");
        SessionValue.Text = sv; SessionUnit.Text = su;
        LatencyValue.Text = connected && latency.HasValue ? latency.Value.ToString() : "—";
        LatencyUnit.Text = connected && latency.HasValue ? "ms" : "";
        UptimeValue.Text = connected && _connectedAt is { } since ? RateFormat.Duration(DateTimeOffset.UtcNow - since) : "—";
        Chart.SetData(_traffic, Math.Max(1, _samplingSeconds), have);
    }

    private void StartUptimeTimer()
    {
        if (_uptimeTimer is not null) return;
        var timer = DispatcherQueue.CreateTimer();
        timer.Interval = TimeSpan.FromSeconds(1);
        timer.Tick += (_, _) =>
        {
            if (_closed || _connectedAt is not { } since) return;
            UptimeValue.Text = RateFormat.Duration(DateTimeOffset.UtcNow - since);
        };
        _uptimeTimer = timer;
        timer.Start();
    }

    private void StopUptimeTimer() { _uptimeTimer?.Stop(); _uptimeTimer = null; }

    // Traffic is pushed by ConnectionSampling from a worker and marshalled here; each sample feeds the chart and the totals.
    private void ApplySample(ConnectionSample sample, int generation)
    {
        if (_closed || generation != _samplingGeneration || !_model.IsConnected) return;
        if (!sample.Ready || sample.Down < 0) return; // not ready, or a transient read failure: keep the last values
        _traffic.Add(sample.Down, sample.Up, _samplingSeconds);
        UpdateTrafficTexts(DescribeExit(RoutingSelector.SelectedIndex == 2, false, SubscriptionComboBox.SelectedItem as SubscriptionRecord, NodeComboBox.SelectedItem as ProxyNode).Latency);
        // Back off to 10 s after three idle samples; return to 2 s as soon as traffic resumes.
        _idleTrafficSamples = sample.Down == 0 && sample.Up == 0 ? Math.Min(3, _idleTrafficSamples + 1) : 0;
        var seconds = _idleTrafficSamples >= 3 ? 10 : 2;
        if (seconds != _samplingSeconds) StartSampling(seconds);
    }

    // Preview only: a believable connected session without a core.
    private void EnterPreviewSession()
    {
        _model.EnterPreviewConnected();
        if (_model.Subscriptions.Count > 0)
        {
            SubscriptionComboBox.SelectedItem = _model.Subscriptions[0];
            NodeComboBox.SelectedItem = _model.Subscriptions[0].Nodes[0];
        }
        _samplingSeconds = 2;
        _connectedAt = DateTimeOffset.UtcNow - TimeSpan.FromSeconds(728);
        for (var i = 0; i < 60; i++)
        {
            var wave = 0.5 + 0.35 * Math.Sin(i / 7.0) + 0.1 * Math.Sin(i / 2.3);
            _traffic.Add((long)(wave * 2.6 * 1048576), (long)((0.3 + 0.2 * Math.Sin(i / 5.0)) * 180 * 1024), 12);
        }
        StartUptimeTimer();
        // Picking the exit above is setup, not a change to apply. Selection and toggle events from that setup arrive later,
        // so settle the flag again once the queue has drained.
        _settingsDirty = false;
        UpdateApplyBar();
        _observedBundle = _model.ActiveConnection; // the demo session is the baseline, not a network change
        _observedRevision = _model.NetworkRevision;
        _observedConnected = _model.IsConnected;
        _previewSettling = true;
        DispatcherQueue.TryEnqueue(Microsoft.UI.Dispatching.DispatcherQueuePriority.Low, () =>
        {
            _previewSettling = false;
            _settingsDirty = false;
            UpdateApplyBar();
        });
    }

    private bool _previewSettling;

    // ---------------------------------------------------------------- navigation

    private void NavigateTo(string page)
    {
        if (_page == page) return;
        _scrollOffsets[_page] = ContentScroll.VerticalOffset;
        foreach (var element in _pageElements[int.Parse(_page)]) element.Visibility = Visibility.Collapsed;
        _page = page;
        foreach (var element in _pageElements[int.Parse(page)]) element.Visibility = Visibility.Visible;
        UpdateNavigation();
        UpdateListHeights();
        if (page == "1" && _dataLoaded && _model.Subscriptions.Count == 0)
            DispatcherQueue.TryEnqueue(() => SubscriptionUrlBox.Focus(FocusState.Keyboard));
        DispatcherQueue.TryEnqueue(() => ContentScroll.ChangeView(null, _scrollOffsets.GetValueOrDefault(_page), null, true));
    }

    private void UpdateNavigation()
    {
        if (!_initialized) return;
        var titles = new[] { L.T("连接"), L.T("订阅"), L.T("分流规则"), L.T("设置"), L.T("网络与隐私"), L.T("设备同步") };
        var index = int.Parse(_page);
        PageTitle.Text = titles[index];
        // Connect and Subscriptions are built from titled tiles; the other pages carry a quiet heading.
        PageHeader.Visibility = index is 0 or 1 ? Visibility.Collapsed : Visibility.Visible;
        // Selection, hover and focus visuals are visual states of the nav item style; only the checked item changes here.
        new[] { Nav0, Nav1, Nav2, Nav3, Nav4, Nav5 }[index].IsChecked = true;
    }

    private void UpdateEmptyStates()
    {
        if (!_dataLoaded) return;
        var any = _model.Subscriptions.Count > 0;
        SubscriptionComboBox.Visibility = QuickNodes.Visibility = QuickTestButton.Visibility = any ? Visibility.Visible : Visibility.Collapsed;
        ExitEmptyState.Visibility = any ? Visibility.Collapsed : Visibility.Visible;
        ManageSubscriptionsButton.Visibility = any ? Visibility.Visible : Visibility.Collapsed;
        RouteListView.Visibility = _model.AppRoutes.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        RouteCountText.Text = _model.AppRoutes.Count.ToString();
        // With a library the import form folds away behind the + button; with none it is the whole point of the page.
        ImportPanel.Visibility = !any || _importOpen ? Visibility.Visible : Visibility.Collapsed;
        UpdateListHeights();
        if (!any) { _shownNodes = Array.Empty<ProxyNode>(); RebuildRows(); }
        UpdateDashboard();
    }

    private void RoutingMode_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (!_initialized) return;
        var direct = RoutingSelector.SelectedIndex == 2;
        SubscriptionComboBox.IsEnabled = NodeComboBox.IsEnabled = QuickNodes.IsEnabled = ChainToggle.IsEnabled = !direct;
        ModeExplanation.Text = RoutingSelector.SelectedIndex switch
        {
            1 => L.T("全部走默认出口（或默认链），忽略应用、域名和国内直连规则。"),
            2 => L.T("全部直连，不经过任何代理节点。隐私端口拦截仍生效。"),
            _ => L.T("应用规则 → 自订规则 → 局域网 / 国内直连 → 默认出口。"),
        };
        ToolTipService.SetToolTip(RoutingSelector, ModeExplanation.Text);
        UpdateDashboard();
        MarkSettingsDirty();
    }

    private void AddSubscription_Click(object sender, RoutedEventArgs e)
    {
        _importOpen = !_importOpen;
        UpdateEmptyStates();
        if (ImportPanel.Visibility == Visibility.Visible)
            DispatcherQueue.TryEnqueue(() => SubscriptionUrlBox.Focus(FocusState.Keyboard));
    }

    // ---------------------------------------------------------------- quick exit grid

    private NodeRow RowFor(ProxyNode node)
    {
        var (tested, last) = _history.Latest(node.Id);
        return new NodeRow(node, tested, last, _history.Samples(node.Id));
    }

    private void RefreshHealthRings()
    {
        foreach (var record in _model.Subscriptions)
            record.Health = SubscriptionHealth.Fraction(record.Nodes, _history) ?? double.NaN;
    }

    private void RefreshQuickNodes()
    {
        if (!_initialized) return;
        var previous = _syncingQuick;
        _syncingQuick = true;
        try
        {
            if (SubscriptionComboBox.SelectedItem is not SubscriptionRecord record) { QuickNodes.ItemsSource = null; return; }
            var current = NodeComboBox.SelectedItem as ProxyNode;
            // Fastest known first; before any test the subscription's own order stands.
            var ranked = record.Nodes
                .Select(node => (Node: node, Seen: _history.Latest(node.Id)))
                .OrderBy(item => item.Seen.Tested ? item.Seen.Last ?? int.MaxValue - 1 : int.MaxValue)
                .ThenBy(item => item.Node.Index)
                .Select(item => item.Node).Take(5).ToList();
            if (current is not null && ranked.Count > 0 && !ranked.Contains(current)) ranked[^1] = current;
            var rows = new List<NodeRow> { new(null, isAuto: true) };
            rows.AddRange(ranked.Select(RowFor));
            QuickNodes.ItemsSource = rows;
        }
        finally { _syncingQuick = previous; }
        SyncQuickSelection();
        UpdateCellWidths();
    }

    private void SyncQuickSelection()
    {
        if (QuickNodes.ItemsSource is not List<NodeRow> rows) return;
        var previous = _syncingQuick;
        _syncingQuick = true;
        try
        {
            var current = NodeComboBox.SelectedItem as ProxyNode;
            QuickNodes.SelectedItem = rows.FirstOrDefault(row => current is null ? row.IsAuto : ReferenceEquals(row.Node, current));
        }
        finally { _syncingQuick = previous; }
    }

    private void QuickNodes_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_syncingQuick || QuickNodes.SelectedItem is not NodeRow row) return;
        _missingFixedNodeId = null;
        NodeComboBox.PlaceholderText = L.T("自动选择 · 最低延迟");
        var target = row.IsAuto ? null : row.Node;
        // A changed selection runs DefaultNode_Changed; an unchanged one still has to repaint.
        if (!ReferenceEquals(NodeComboBox.SelectedItem, target)) NodeComboBox.SelectedItem = target;
        else { UpdateDashboard(); MarkSettingsDirty(); }
    }

    // ---------------------------------------------------------------- node table

    private void ShowNodes(IEnumerable<ProxyNode>? nodes)
    {
        _shownNodes = nodes?.ToArray() ?? Array.Empty<ProxyNode>();
        RebuildRows();
    }

    private void RebuildRows()
    {
        _allNodeRows = _shownNodes.Select(RowFor).ToArray();
        RebuildRegionChips();
        ApplyNodeFilter();
    }

    private void RebuildRegionChips()
    {
        var english = L.Language == "en";
        var groups = _allNodeRows.GroupBy(row => row.Region).OrderByDescending(group => group.Count()).ThenBy(group => group.Key, StringComparer.Ordinal).ToList();
        var labels = new List<string> { $"{L.T("全部")} {_allNodeRows.Length}" };
        _regionCodes.Clear();
        _regionCodes.Add("*");
        foreach (var group in groups)
        {
            _regionCodes.Add(group.Key);
            labels.Add($"{(group.Key.Length == 0 ? L.T("其他") : NodeRegion.RegionName(group.Key, english))} {group.Count()}");
        }
        RegionChips.Visibility = _allNodeRows.Length == 0 ? Visibility.Collapsed : Visibility.Visible; // "All 0" says nothing
        _syncingChips = true;
        try
        {
            RegionChips.ItemsSource = labels;
            var index = Math.Max(0, _regionCodes.IndexOf(_regionFilter));
            _regionFilter = _regionCodes[index];
            RegionChips.SelectedIndex = index;
        }
        finally { _syncingChips = false; }
    }

    private void ApplyNodeFilter()
    {
        IEnumerable<NodeRow> rows = _allNodeRows;
        if (_regionFilter != "*") rows = rows.Where(row => row.Region == _regionFilter);
        if (_nodeQuery.Length > 0)
            rows = rows.Where(row => row.Name.Contains(_nodeQuery, StringComparison.OrdinalIgnoreCase) ||
                row.Protocol.Contains(_nodeQuery, StringComparison.OrdinalIgnoreCase) || row.Region.Contains(_nodeQuery, StringComparison.OrdinalIgnoreCase));
        // Once something has been measured the fastest lead; until then the subscription's own order.
        rows = _allNodeRows.Any(row => row.Tested) ? rows.OrderBy(row => row.Delay ?? int.MaxValue).ThenBy(row => row.Node!.Index) : rows.OrderBy(row => row.Node!.Index);
        var list = rows.ToArray();
        SubscriptionNodesList.ItemsSource = list;
        if (NodeComboBox.SelectedItem is ProxyNode current) SubscriptionNodesList.SelectedItem = list.FirstOrDefault(row => ReferenceEquals(row.Node, current));
        if (list.Length == 0 && _nodeQuery.Length == 0 && _regionFilter == "*" && string.IsNullOrEmpty(NodeTestText.Text))
            NodeTestText.Text = ((UiText)Application.Current.Resources["UiText"]).SelectASubscriptionToViewItsNodes;
        else if (list.Length > 0 && NodeTestText.Text == ((UiText)Application.Current.Resources["UiText"]).SelectASubscriptionToViewItsNodes) NodeTestText.Text = "";
    }

    private void RegionChips_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_syncingChips || RegionChips.SelectedIndex < 0 || RegionChips.SelectedIndex >= _regionCodes.Count) return;
        _regionFilter = _regionCodes[RegionChips.SelectedIndex];
        ApplyNodeFilter();
    }

    private void NodeSearchBox_TextChanged(object sender, TextChangedEventArgs e)
    {
        _nodeQuery = NodeSearchBox.Text.Trim();
        ApplyNodeFilter();
    }

    private void RefreshRowLanguage()
    {
        foreach (var row in _allNodeRows) row.RefreshLanguage();
        if (QuickNodes.ItemsSource is IEnumerable<NodeRow> quick) foreach (var row in quick) row.RefreshLanguage();
        RebuildRegionChips();
    }

    // A test fills rows in as nodes answer; refreshes are coalesced so a long list does not re-sort per node.
    private void ScheduleProbeRefresh()
    {
        if (_probeRefreshTimer is { IsRunning: true }) return;
        var timer = _probeRefreshTimer ??= DispatcherQueue.CreateTimer();
        timer.IsRepeating = false;
        timer.Interval = TimeSpan.FromMilliseconds(180);
        timer.Tick -= ProbeRefreshTick;
        timer.Tick += ProbeRefreshTick;
        timer.Start();
    }
    private void ProbeRefreshTick(DispatcherQueueTimer sender, object args) => FlushProbeRefresh();

    private void FlushProbeRefresh()
    {
        if (_closed) return;
        RebuildRows();
        RefreshHealthRings();
        RefreshQuickNodes();
        UpdateDashboard();
    }

    private async void TestNodes_Click(object sender, RoutedEventArgs e)
    {
        if (_model.ActiveConnection is not { } bundle || !_model.IsConnected ||
            SubscriptionComboBox.SelectedItem is not SubscriptionRecord record)
        { MessageText.Text = L.T("请先连接，再测试当前订阅节点"); return; }
        if (!bundle.ProviderNodeCounts.ContainsKey("provider-" + record.Id))
        { MessageText.Text = L.T("此订阅不在当前会话中。请先选择它并重新连接，再测试节点；直连模式不加载订阅。"); return; }
        await RunActionAsync(async () =>
        {
            using var cancellation = CancellationTokenSource.CreateLinkedTokenSource(_lifetime.Token);
            _probeCancellation = cancellation;
            var revision = _model.NetworkRevision;
            try
            {
                // Snapshot on the UI thread; the probes run on workers, three at a time.
                var nodes = record.Nodes.ToArray();
                if (!ReferenceEquals(_shownNodes.FirstOrDefault(), nodes.FirstOrDefault())) ShowNodes(nodes);
                var prefix = MihomoConfigBuilder.NodePrefix(record.Id);
                var byName = nodes.ToLookup(node => prefix + node.RawName, StringComparer.Ordinal);
                var total = byName.Count;
                var done = 0;
                NodeTestText.Text = L.F($"测速中 {done} / {total}");
                await ConnectionSampling.ProbeNodesAsync(byName.Select(group => group.Key),
                    async (name, token) =>
                    {
                        try { return await _model.ProbeNodeAsync(bundle, name, token); }
                        catch (Exception error) when (error is HttpRequestException or OperationCanceledException or System.Text.Json.JsonException or IOException)
                        { token.ThrowIfCancellationRequested(); return null; }
                    }, cancellation.Token, 3,
                    (name, delay) => DispatcherQueue.TryEnqueue(() =>
                    {
                        if (_closed || !EvidenceStillCurrent(bundle, revision)) return;
                        foreach (var node in byName[name]) _history.Record(node.Id, delay);
                        done++;
                        NodeTestText.Text = L.F($"测速中 {done} / {total}");
                        ScheduleProbeRefresh();
                    }));
                cancellation.Token.ThrowIfCancellationRequested();
                if (!EvidenceStillCurrent(bundle, revision)) return;
                _probeRefreshTimer?.Stop();
                FlushProbeRefresh();
                var answered = nodes.Count(node => _history.Latest(node.Id).Last.HasValue);
                NodeTestText.Text = L.F($"已测 {nodes.Length} 个，响应 {answered} 个。点击节点可选为默认出口，重新连接后生效。");
            }
            finally { _probeCancellation = null; }
        });
    }

    // ---------------------------------------------------------------- subscriptions

    private void SubscriptionListView_RightTapped(object sender, RightTappedRoutedEventArgs e)
    {
        if ((e.OriginalSource as FrameworkElement)?.DataContext is not SubscriptionRecord record) return;
        if (!SubscriptionListView.SelectedItems.Contains(record))
        {
            SubscriptionListView.SelectedItems.Clear();
            SubscriptionListView.SelectedItems.Add(record);
        }
        LibraryMenu.ShowAt(SubscriptionListView, new FlyoutShowOptions { Position = e.GetPosition(SubscriptionListView) });
    }

    private void SubscriptionListView_KeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key != global::Windows.System.VirtualKey.Delete) return;
        e.Handled = true;
        DeleteSelectedSubscriptions_Click(sender, e);
    }

    private async void DeleteSelectedSubscriptions_Click(object sender, RoutedEventArgs e)
    {
        if (_busy) return;
        var selected = SubscriptionListView.SelectedItems.Cast<SubscriptionRecord>().ToArray();
        if (selected.Length == 0) { MessageText.Text = L.T("请先选择要删除的订阅"); return; }
        var dialog = new ContentDialog
        {
            Title = selected.Length == 1 ? L.T("删除订阅？") : L.F($"删除 {selected.Length} 份订阅？"),
            Content = selected.Length == 1 ? L.F($"将从本机删除“{selected[0].Name}”及其加密内容。") : L.F($"将从本机删除 {selected.Length} 份订阅及其加密内容。"),
            PrimaryButtonText = L.T("删除"), CloseButtonText = L.T("取消"),
            XamlRoot = RootGrid.XamlRoot, RequestedTheme = RootGrid.ActualTheme, DefaultButton = ContentDialogButton.Close,
        };
        if (await dialog.ShowAsync() != ContentDialogResult.Primary) return;
        // The DPAPI write runs on a worker; the collection is updated back on this (UI) context. RunActionAsync locks the
        // subscription list while it runs, so a repeat click cannot queue a second delete.
        await RunActionAsync(async () =>
        {
            foreach (var record in selected)
            {
                await _model.RemoveAsync(record.Id, _lifetime.Token);
                foreach (var node in record.Nodes) _history.Forget(node.Id);
            }
            if (SubscriptionComboBox.SelectedItem is not SubscriptionRecord stillThere || !_model.Subscriptions.Contains(stillThere))
                SubscriptionComboBox.SelectedIndex = _model.Subscriptions.Count > 0 ? 0 : -1;
            if (_model.Subscriptions.Count == 0) ShowNodes(null);
            RefreshQuickNodes();
            MessageText.Text = L.T("订阅已删除");
        });
    }

    private void NodeList_RightTapped(object sender, RightTappedRoutedEventArgs e)
    {
        if ((e.OriginalSource as FrameworkElement)?.DataContext is not NodeRow row) return;
        SubscriptionNodesList.SelectedItem = row;
        NodeMenu.ShowAt(SubscriptionNodesList, new FlyoutShowOptions { Position = e.GetPosition(SubscriptionNodesList) });
    }

    private void SetAsExit_Click(object sender, RoutedEventArgs e)
    {
        if (SubscriptionNodesList.SelectedItem is not NodeRow { Node: { } node } row) return;
        NodeComboBox.SelectedItem = node;
        MessageText.Text = L.F($"出口已设为 {row.Name}；重新连接后生效");
    }

    private void CopyNodeName_Click(object sender, RoutedEventArgs e)
    {
        if (SubscriptionNodesList.SelectedItem is not NodeRow row) return;
        CopyText(row.Name, L.T("已复制"));
    }

    // ---------------------------------------------------------------- capture (CI and local layout review only)

    private async void CapturePreviewIfRequested(object sender, RoutedEventArgs e)
    {
        // Explicit render capture, never enabled during normal use.
        var path = Environment.GetEnvironmentVariable("WEAVE_UI_CAPTURE");
        if (string.IsNullOrEmpty(path)) return;
        try { await CapturePreviewAsync(path); }
        catch (Exception error)
        {
            try { App.RecordStartupFailure(new InvalidOperationException($"Preview capture failed: {path}", error)); }
            finally { Environment.Exit(1); }
        }
    }

    private async Task CapturePreviewAsync(string path)
    {
        await _dataReady.Task;
        if (_page == "4" && Environment.GetEnvironmentVariable("WEAVE_PREVIEW_DIAGNOSTICS") == "1")
        {
            // Synthetic evidence exclusively for layout review; no requests or user data.
            DiagnosticResults.ItemsSource = new[] {
                new ProbeResult("代理出口 IPv4", "8.8.8.8"),
                new ProbeResult("Google", "", 28, 204),
                new ProbeResult("ChatGPT", "", 145, 403),
                new ProbeResult("X", "域名解析失败", Failure: ProbeFailure.Dns),
            };
            DiagnosticProgress.Text = "CI · synthetic layout fixture";
        }
        await Task.Delay(1500);
        var compactLayout = RootGrid.ActualWidth < CompactLayoutThreshold;
        if (compactLayout != (Environment.GetEnvironmentVariable("WEAVE_PREVIEW_COMPACT") == "1"))
            throw new InvalidOperationException($"Window width {RootGrid.ActualWidth:F0} is on the wrong side of the {CompactLayoutThreshold:F0} px layout threshold");
        var origin = new WinPoint();
        foreach (var button in new[] { Nav0, Nav1, Nav2, Nav4, Nav5, Nav3 })
        {
            var at = button.TransformToVisual(RootGrid).TransformPoint(origin);
            if (Math.Abs(button.ActualWidth - 56) > 1 || Math.Abs(button.ActualHeight - 58) > 1)
                throw new InvalidOperationException($"Navigation item has the wrong size: {button.Name} {button.ActualWidth:F1}x{button.ActualHeight:F1}");
            if (at.X < 0 || at.X + button.ActualWidth > SidebarSurface.ActualWidth + 1)
                throw new InvalidOperationException("Navigation item is clipped: " + button.Name);
        }
        if (_page == "0")
        {
            foreach (var tile in new FrameworkElement[] { ConnectionHero, TrafficTile, ExitCard, ConnectionNote, ConnectButton })
            {
                var at = tile.TransformToVisual(ContentScroll).TransformPoint(origin);
                if (tile.ActualWidth < 100 || at.X < -1 || at.X + tile.ActualWidth > ContentScroll.ActualWidth + 1)
                    throw new InvalidOperationException("Home tile is clipped horizontally: " + tile.Name);
            }
            var heroTop = ConnectionHero.TransformToVisual(ContentScroll).TransformPoint(origin).Y;
            var trafficTop = TrafficTile.TransformToVisual(ContentScroll).TransformPoint(origin).Y;
            var sideBySide = Math.Abs(heroTop - trafficTop) < 1;
            // Stacked when compact, and also in the mid range where two columns would drop under their minimum width.
            if (sideBySide != (RootGrid.ActualWidth >= HomeSideBySideMinWidth))
                throw new InvalidOperationException($"Home tiles did not reflow for the window width {RootGrid.ActualWidth:F0}");
            if (ConnectionHero.ActualHeight < 300) throw new InvalidOperationException("Hero tile is cramped");
        }
        if (Environment.GetEnvironmentVariable("WEAVE_PREVIEW_SWITCH_LANGUAGE") == "1")
        {
            var mode = RoutingSelector.SelectedIndex;
            var dns = DnsSelector.SelectedIndex;
            var subscription = SubscriptionComboBox.SelectedItem;
            var node = NodeComboBox.SelectedItem;
            LanguageSelector.SelectedIndex = L.Language == "en" ? 0 : 1;
            await Task.Delay(150);
            if (NavLabel0.Text != L.T("连接") || PageTitle.Text != L.T(new[] { "连接", "订阅", "分流规则", "设置", "网络与隐私", "设备同步" }[int.Parse(_page)]) ||
                mode != RoutingSelector.SelectedIndex || dns != DnsSelector.SelectedIndex ||
                !ReferenceEquals(subscription, SubscriptionComboBox.SelectedItem) || !ReferenceEquals(node, NodeComboBox.SelectedItem))
                throw new InvalidOperationException("Live language switch failed or changed network selection");
        }
        // Element sizes next to the picture, so a layout review can read numbers as well as look.
        var layout = new System.Text.StringBuilder();
        foreach (var (name, element) in new (string, FrameworkElement)[] {
            ("RootGrid", RootGrid), ("ContentScroll", ContentScroll), ("PageStack", PageStack), ("ConnectPage", ConnectPage),
            ("ConnectionHero", ConnectionHero), ("Knot", Knot), ("HeroCity", HeroCity), ("TrafficTile", TrafficTile), ("Chart", Chart),
            ("ExitCard", ExitCard), ("QuickNodes", QuickNodes), ("ConnectionNote", ConnectionNote), ("SubscriptionsPage", SubscriptionsPage),
            ("SettingsPage", SettingsPage), ("ThemeGallery", ThemeGallery), ("RoutesPanel", RoutesPanel), ("DiagnosticsPanel", DiagnosticsPanel),
            ("TransferPanel", TransferPanel) })
        {
            var where = element.Visibility == Visibility.Visible ? element.TransformToVisual(ContentScroll).TransformPoint(origin) : origin;
            layout.AppendLine($"{name}: {element.ActualWidth:F0}x{element.ActualHeight:F0} at {where.X:F0},{where.Y:F0} desired {element.DesiredSize.Width:F0}x{element.DesiredSize.Height:F0} {(element.Visibility == Visibility.Visible ? "" : "(hidden)")}");
        }
        File.WriteAllText(path + ".layout.txt", layout.ToString());
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
        File.WriteAllText(path + ".ready", "rendered");
    }
}
