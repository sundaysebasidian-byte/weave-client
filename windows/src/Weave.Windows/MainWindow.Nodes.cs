using System.Collections.ObjectModel;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Weave.Windows.Core;
using Windows.ApplicationModel.DataTransfer;

namespace Weave.Windows;

public sealed partial class MainWindow
{
    private readonly ObservableCollection<NodeItem> _visibleNodes = new();
    private List<NodeItem> _nodeItems = new();
    private CancellationTokenSource? _qualityCancellation;
    private bool _syncingNodeSelection;
    private DateTimeOffset? _lastQualityCheck;

    private bool QualityCheckRunning => _qualityCancellation is not null;

    private void RebuildNodeItems()
    {
        _nodeItems = _activeSubscription?.Nodes.Select(node => new NodeItem(node)
        {
            IsExit = node.Id == _selectedNodeId,
        }).ToList() ?? new List<NodeItem>();
        _lastQualityCheck = null;
        if (NodeSortComboBox.SelectedIndex is 1 or 2) NodeSortComboBox.SelectedIndex = 0;
        UpdateProbeAvailability();
        ApplyNodeView();
    }

    private void ApplyNodeView()
    {
        var query = NodeSearchBox.Text.Trim();
        IEnumerable<NodeItem> items = _nodeItems;
        if (query.Length > 0)
        {
            items = items.Where(item =>
                item.Name.Contains(query, StringComparison.OrdinalIgnoreCase) ||
                item.Protocol.Contains(query, StringComparison.OrdinalIgnoreCase));
        }

        items = NodeSortComboBox.SelectedIndex switch
        {
            1 => items.OrderBy(item => item.Score is null).ThenByDescending(item => item.Score ?? -1)
                .ThenBy(item => item.Health?.MedianLatencyMs ?? int.MaxValue),
            2 => items.OrderBy(item => item.Health?.MedianLatencyMs ?? int.MaxValue),
            3 => items.OrderBy(item => item.Name, StringComparer.CurrentCulture),
            _ => items.OrderBy(item => item.Node.Index),
        };

        _syncingNodeSelection = true;
        try
        {
            _visibleNodes.Clear();
            foreach (var item in items) _visibleNodes.Add(item);
        }
        finally
        {
            _syncingNodeSelection = false;
        }

        SyncNodeListSelection();
        UpdateNodesSummary();
    }

    private void SyncNodeListSelection()
    {
        _syncingNodeSelection = true;
        try
        {
            NodeListView.SelectedItem = _visibleNodes.FirstOrDefault(item => item.Node.Id == _selectedNodeId);
        }
        finally
        {
            _syncingNodeSelection = false;
        }
    }

    private void UpdateNodesSummary()
    {
        var total = _nodeItems.Count;
        var tested = _nodeItems.Count(item => item.Health is not null);
        var summary = _activeSubscription is null
            ? "尚未导入订阅"
            : $"{_activeSubscription.Name} · {total} 个节点";
        if (_visibleNodes.Count != total) summary += $" · 显示 {_visibleNodes.Count} 个";
        if (tested > 0)
        {
            var stable = _nodeItems.Count(item => item.Score >= 85);
            summary += $" · 已测试 {tested} 个，稳定 {stable} 个";
        }

        if (_lastQualityCheck is { } checkedAt) summary += $" · 上次检查 {checkedAt:HH:mm}";
        NodesSummaryText.Text = summary;

        NodesEmptyPanel.Visibility = Show(_visibleNodes.Count == 0);
        NodesEmptyText.Text = _activeSubscription is null
            ? "导入订阅后，这里会列出全部节点。"
            : total == 0 ? "该订阅没有可用节点。" : "没有匹配的节点。";
        NodesEmptyImportButton.Visibility = Show(_activeSubscription is null);
        NodesBadge.Visibility = Show(total > 0);
        NodesBadge.Value = total;
    }

    private void NodeSearchBox_TextChanged(AutoSuggestBox sender, AutoSuggestBoxTextChangedEventArgs args)
    {
        if (_initialized) ApplyNodeView();
    }

    private void NodeSortComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_initialized) ApplyNodeView();
    }

    private void NodeListView_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_syncingNodeSelection) return;
        if (NodeListView.SelectedItem is NodeItem item)
        {
            SetSelectedNode(item.Node.Id);
        }
        else
        {
            // Ctrl+click would clear the row; the exit stays until the user explicitly picks automatic.
            SyncNodeListSelection();
        }
    }

    private void NodeMenuUse_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { Tag: NodeItem item }) SetSelectedNode(item.Node.Id);
    }

    private async void NodeMenuTest_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { Tag: NodeItem item }) await ProbeNodeAsync(item);
    }

    private async void NodeTestButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { Tag: NodeItem item }) await ProbeNodeAsync(item);
    }

    private void NodeMenuCopy_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not FrameworkElement { Tag: NodeItem item }) return;
        var package = new DataPackage();
        package.SetText(item.Node.RawName);
        Clipboard.SetContent(package);
        Notify($"已复制“{item.Name}”。", InfoBarSeverity.Informational);
    }

    private async void HealthCheckButton_Click(object sender, RoutedEventArgs e)
    {
        var item = _nodeItems.FirstOrDefault(candidate => candidate.Node.Id == _selectedNodeId);
        if (item is not null) await ProbeNodeAsync(item);
    }

    private void UpdateProbeAvailability()
    {
        var canProbe = !QualityCheckRunning && _activeSubscription is { } subscription &&
                       _model.CanProbeSubscription(subscription);
        foreach (var item in _nodeItems) item.CanProbe = canProbe && !item.IsTesting;
        CheckAllButton.IsEnabled = canProbe && _nodeItems.Count > 0;
        ToolTipService.SetToolTip(CheckAllButton, canProbe
            ? "对订阅内每个节点进行三轮健康检查 (F5)"
            : _model.IsConnected ? "当前订阅未加载到运行配置；重新连接后可检查" : "连接后可检查节点健康");
        CancelCheckButton.Visibility = Show(QualityCheckRunning);
        CheckProgressPanel.Visibility = Show(QualityCheckRunning);
    }

    private void UpdateNodeHealthCard()
    {
        var item = _nodeItems.FirstOrDefault(candidate => candidate.Node.Id == _selectedNodeId);
        var health = item?.Health;
        HealthMedianText.Text = UiFormat.Latency(health?.MedianLatencyMs);
        HealthP95Text.Text = UiFormat.Latency(health?.P95LatencyMs);
        HealthJitterText.Text = UiFormat.Latency(health?.JitterMs);
        HealthFailureText.Text = health is null ? "—" : $"{health.ProbeFailurePercent}%";
        HealthCheckButton.IsEnabled = item is { CanProbe: true, IsTesting: false };

        HealthStatusText.Text = item is null
            ? "固定一个节点后可单独测试；自动选择会由核心持续测速。"
            : item.IsTesting
                ? "正在进行三轮健康检查…"
                : health is not null
                    ? $"{item.ScoreText} · {health.SuccessfulSamples}/{health.TotalSamples} 轮成功"
                    : item.CanProbe ? "尚未测试该节点。" : "连接后可测试该节点；测试不会切换当前出口。";
    }

    private async Task ProbeNodeAsync(NodeItem item)
    {
        if (item.IsTesting || QualityCheckRunning || _activeSubscription is not { } subscription ||
            !_model.CanProbe(subscription, item.Node))
        {
            return;
        }

        item.IsTesting = true;
        item.CanProbe = false;
        UpdateNodeHealthCard();
        try
        {
            var snapshot = await _model.ProbeNodeAsync(subscription, item.Node, _lifetime.Token);
            if (ReferenceEquals(subscription, _activeSubscription))
            {
                item.SetQuality(NodeQualityMatrix.Build(new[] { snapshot })[0]);
            }
        }
        catch (OperationCanceledException)
        {
        }
        catch (Exception exception)
        {
            if (ReferenceEquals(subscription, _activeSubscription))
                Notify(exception.Message, InfoBarSeverity.Error, $"测试“{item.Name}”失败");
        }
        finally
        {
            item.IsTesting = false;
            UpdateProbeAvailability();
            UpdateNodeHealthCard();
            UpdateNodesSummary();
        }
    }

    private async void CheckAllButton_Click(object sender, RoutedEventArgs e) => await CheckAllNodesAsync();

    private async Task CheckAllNodesAsync()
    {
        if (QualityCheckRunning || _activeSubscription is not { } subscription ||
            !_model.CanProbeSubscription(subscription))
        {
            return;
        }

        var cancellation = CancellationTokenSource.CreateLinkedTokenSource(_lifetime.Token);
        _qualityCancellation = cancellation;
        var items = _nodeItems;
        foreach (var item in items)
        {
            item.SetQuality(null);
            item.IsTesting = true;
        }

        CheckProgressBar.Maximum = Math.Max(1, subscription.Nodes.Count);
        CheckProgressBar.Value = 0;
        CheckProgressText.Text = $"正在检查 0/{subscription.Nodes.Count}";
        UpdateProbeAvailability();
        UpdateNodeHealthCard();

        var lastProgress = 0;
        var progress = new Progress<int>(completed => DispatcherQueue.TryEnqueue(() =>
        {
            if (!ReferenceEquals(_qualityCancellation, cancellation) || completed <= lastProgress) return;
            lastProgress = completed;
            CheckProgressBar.Value = completed;
            CheckProgressText.Text = $"正在检查 {completed}/{subscription.Nodes.Count}";
        }));

        try
        {
            var rows = await _model.ProbeSubscriptionAsync(subscription, progress, cancellation.Token);
            if (ReferenceEquals(subscription, _activeSubscription) && ReferenceEquals(items, _nodeItems))
            {
                var byName = rows.ToDictionary(row => row.Name, StringComparer.Ordinal);
                foreach (var item in items)
                {
                    item.SetQuality(byName.GetValueOrDefault(item.Node.RawName));
                }

                _lastQualityCheck = DateTimeOffset.Now;
                var stable = rows.Count(row => row.StabilityScore >= 85);
                Notify($"已检查 {rows.Count} 个节点，其中 {stable} 个稳定。列表已按稳定度排序。",
                    InfoBarSeverity.Success, "检查完成");
                NodeSortComboBox.SelectedIndex = 1;
            }
        }
        catch (OperationCanceledException)
        {
            if (ReferenceEquals(subscription, _activeSubscription) && _model.IsConnected)
                Notify("已取消节点检查。", InfoBarSeverity.Informational);
        }
        catch (Exception exception)
        {
            if (ReferenceEquals(subscription, _activeSubscription) && _model.IsConnected)
                Notify(exception.Message, InfoBarSeverity.Error, "节点检查失败");
        }
        finally
        {
            foreach (var item in items) item.IsTesting = false;
            if (ReferenceEquals(_qualityCancellation, cancellation)) _qualityCancellation = null;
            cancellation.Dispose();
            UpdateProbeAvailability();
            UpdateNodeHealthCard();
            ApplyNodeView();
        }
    }

    private void CancelCheckButton_Click(object sender, RoutedEventArgs e) => CancelQualityCheck();

    private void CancelQualityCheck()
    {
        var cancellation = _qualityCancellation;
        if (cancellation is null) return;
        _qualityCancellation = null;
        cancellation.Cancel();
        if (_initialized) UpdateProbeAvailability();
    }
}
