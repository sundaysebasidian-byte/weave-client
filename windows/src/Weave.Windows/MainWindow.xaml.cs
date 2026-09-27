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
    private bool _healthChecking;
    private CancellationTokenSource? _healthCancellation;

    public MainWindow()
    {
        InitializeComponent();
        _model.Load();
        DnsProfileComboBox.SelectedIndex = _model.NetworkOptions.DnsProfile switch
        {
            DnsProfile.AdBlock => 1,
            DnsProfile.Family => 2,
            DnsProfile.Custom => 3,
            _ => 0,
        };
        CustomDnsEndpointBox.Text = _model.NetworkOptions.CustomDnsEndpoint ?? string.Empty;
        Ipv6CheckBox.IsChecked = _model.NetworkOptions.Ipv6Enabled;
        BlockStunCheckBox.IsChecked = _model.NetworkOptions.BlockUdpStun;
        SubscriptionComboBox.ItemsSource = _model.Subscriptions;
        SubscriptionListView.ItemsSource = _model.Subscriptions;
        RouteSubscriptionComboBox.ItemsSource = _model.Subscriptions;
        RouteListView.ItemsSource = _model.AppRoutes;
        RouteTargetModeComboBox.ItemsSource = new[] { "自动测速", "固定节点", "直连", "阻止" };
        RouteTargetModeComboBox.SelectedIndex = 0;
        _model.StatusChanged += (_, _) => DispatcherQueue.TryEnqueue(UpdateStatus);
        if (_model.Subscriptions.Count > 0)
        {
            SubscriptionComboBox.SelectedIndex = 0;
            RouteSubscriptionComboBox.SelectedIndex = 0;
        }

        Closed += MainWindow_Closed;
        UpdateStatus();
    }

    private void SubscriptionComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        _healthCancellation?.Cancel();
        ClearQualityResult();
        NodeComboBox.ItemsSource = (SubscriptionComboBox.SelectedItem as SubscriptionRecord)?.Nodes;
        NodeComboBox.SelectedItem = null;
    }

    private void NodeComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (HealthResultText is null || HealthCheckButton is null) return;
        HealthResultText.Text = "连接后选择节点可进行健康检查";
        UpdateStatus();
    }

    private void SubscriptionListView_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (SubscriptionListView.SelectedItem is SubscriptionRecord selected)
        {
            SubscriptionComboBox.SelectedItem = selected;
        }
    }

    private void RouteSubscriptionComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        RouteNodeComboBox.ItemsSource = (RouteSubscriptionComboBox.SelectedItem as SubscriptionRecord)?.Nodes;
        RouteNodeComboBox.SelectedItem = null;
    }

    private void RouteTargetModeComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        RouteNodeComboBox.IsEnabled = RouteTargetModeComboBox.SelectedIndex == 1;
        RouteSubscriptionComboBox.IsEnabled = RouteTargetModeComboBox.SelectedIndex is 0 or 1;
        if (RouteTargetModeComboBox.SelectedIndex != 1)
        {
            RouteNodeComboBox.SelectedItem = null;
        }
    }

    private void DnsProfileComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (CustomDnsEndpointBox is not null)
        {
            CustomDnsEndpointBox.IsEnabled = DnsProfileComboBox.SelectedIndex == 3;
        }
    }

    private async void SaveNetworkOptionsButton_Click(object sender, RoutedEventArgs e)
    {
        await RunActionAsync(() =>
        {
            _model.SaveNetworkOptions(ReadNetworkOptions());
            MessageText.Text = "网络设置已保存；重新连接后生效";
            return Task.CompletedTask;
        });
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
        if (sender is Button { Tag: string processName })
        {
            _model.RemoveRoute(processName);
            MessageText.Text = $"已删除 {processName} 的分流规则；重新连接后生效";
        }
    }

    private async void ImportTextButton_Click(object sender, RoutedEventArgs e)
    {
        await RunActionAsync(() =>
        {
            var record = _model.ImportText(
                SubscriptionNameBox.Text,
                $"inline://{Guid.NewGuid():N}",
                SubscriptionTextBox.Text);
            SubscriptionComboBox.SelectedItem = record;
            MessageText.Text = $"已导入 {record.Name}，发现 {record.Nodes.Count} 个节点";
            return Task.CompletedTask;
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

    private async void RefreshSubscriptionButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not Button { Tag: string id } ||
            _model.Subscriptions.FirstOrDefault(item => item.Id == id) is not { } current)
        {
            return;
        }

        await RunActionAsync(async () =>
        {
            var update = await _model.RefreshAsync(current, _lifetime.Token);
            SubscriptionComboBox.SelectedItem = update.Record;
            MessageText.Text = $"已刷新 {update.Record.Name}：新增 {update.AddedNodes}、移除 {update.RemovedNodes}、保留 {update.RetainedNodes} 个节点；重新连接后生效";
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

            var record = _model.ImportFile(SubscriptionNameBox.Text, file.Path);
            SubscriptionComboBox.SelectedItem = record;
            MessageText.Text = $"已导入 {record.Name}，发现 {record.Nodes.Count} 个节点";
        });
    }

    private async void DeleteSubscriptionButton_Click(object sender, RoutedEventArgs e)
    {
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

        await RunActionAsync(() =>
        {
            _model.Remove(id);
            if (SubscriptionComboBox.SelectedItem is SubscriptionRecord selected && selected.Id == id)
            {
                SubscriptionComboBox.SelectedIndex = _model.Subscriptions.Count > 0 ? 0 : -1;
            }
            MessageText.Text = "订阅已删除";
            return Task.CompletedTask;
        });
    }

    private async void ConnectButton_Click(object sender, RoutedEventArgs e)
    {
        if (_model.IsConnected)
        {
            await RunActionAsync(async () =>
            {
                await _model.DisconnectAsync();
                MessageText.Text = "已断开，系统网络配置保持不变";
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
            await _model.ConnectAsync(subscription.Id, node?.Id, ReadNetworkOptions(), _lifetime.Token);
            MessageText.Text = "Mihomo 已启动；请验证 Windows TUN 适配器和实际出口。";
            UpdateStatus();
        });
    }

    private async void HealthCheckButton_Click(object sender, RoutedEventArgs e)
    {
        if (_healthChecking) return;
        if (SubscriptionComboBox.SelectedItem is not SubscriptionRecord subscription ||
            NodeComboBox.SelectedItem is not ProxyNode node || !_model.CanProbe(subscription, node))
            return;

        _healthChecking = true;
        HealthResultText.Text = "正在进行三轮节点健康检查…";
        UpdateStatus();
        try
        {
            var result = await _model.ProbeNodeAsync(subscription, node, _lifetime.Token);
            if (ReferenceEquals(SubscriptionComboBox.SelectedItem, subscription) &&
                ReferenceEquals(NodeComboBox.SelectedItem, node) && _model.CanProbe(subscription, node))
            {
                HealthResultText.Text = $"中位 {FormatLatency(result.MedianLatencyMs)} · " +
                    $"P95 {FormatLatency(result.P95LatencyMs)} · " +
                    $"抖动 {FormatLatency(result.JitterMs)} · " +
                    $"失败 {result.ProbeFailurePercent}%（{result.SuccessfulSamples}/{result.TotalSamples} 轮成功）";
            }
        }
        catch (OperationCanceledException)
        {
            HealthResultText.Text = "健康检查已取消";
        }
        catch (Exception exception)
        {
            HealthResultText.Text = $"健康检查失败：{exception.Message}";
        }
        finally
        {
            _healthChecking = false;
            UpdateStatus();
        }
    }

    private async void SubscriptionHealthButton_Click(object sender, RoutedEventArgs e)
    {
        if (_healthChecking || SubscriptionComboBox.SelectedItem is not SubscriptionRecord subscription ||
            !_model.CanProbeSubscription(subscription)) return;

        _healthChecking = true;
        using var cancellation = CancellationTokenSource.CreateLinkedTokenSource(_lifetime.Token);
        _healthCancellation = cancellation;
        ClearQualityResult();
        QualityResultText.Text = $"正在检查节点：0/{subscription.Nodes.Count}…";
        UpdateStatus();
        var progress = new Progress<int>(completed =>
        {
            if (ReferenceEquals(_healthCancellation, cancellation) &&
                !cancellation.IsCancellationRequested &&
                ReferenceEquals(SubscriptionComboBox.SelectedItem, subscription) &&
                _model.CanProbeSubscription(subscription))
                QualityResultText.Text = $"正在检查节点：{completed}/{subscription.Nodes.Count}…";
        });
        try
        {
            var rows = await _model.ProbeSubscriptionAsync(subscription, progress, cancellation.Token);
            if (ReferenceEquals(SubscriptionComboBox.SelectedItem, subscription) &&
                _model.CanProbeSubscription(subscription))
            {
                QualityListView.ItemsSource = rows;
                QualityListView.Visibility = Visibility.Visible;
                QualityResultText.Text = $"已完成 {rows.Count} 个节点的检查；按稳定度从高到低排列。";
            }
        }
        catch (OperationCanceledException)
        {
            if (ReferenceEquals(SubscriptionComboBox.SelectedItem, subscription) && _model.IsConnected)
                QualityResultText.Text = "订阅检查已取消";
        }
        catch (Exception exception)
        {
            if (ReferenceEquals(SubscriptionComboBox.SelectedItem, subscription) && _model.IsConnected)
                QualityResultText.Text = $"订阅检查失败：{exception.Message}";
        }
        finally
        {
            _healthCancellation = null;
            _healthChecking = false;
            UpdateStatus();
        }
    }

    private void CancelHealthButton_Click(object sender, RoutedEventArgs e)
    {
        _healthCancellation?.Cancel();
        UpdateStatus();
    }

    private void ClearQualityResult()
    {
        if (QualityListView is null || QualityResultText is null) return;
        QualityListView.ItemsSource = null;
        QualityListView.Visibility = Visibility.Collapsed;
        QualityResultText.Text = "连接后可检查已加载的订阅";
    }

    private async Task RunActionAsync(Func<Task> action)
    {
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

        UpdateStatus();
    }

    private void UpdateStatus()
    {
        StatusText.Text = _model.Status;
        ConnectButton.Content = _model.IsConnected ? "断开连接" : "连接";
        HealthCheckButton.IsEnabled = !_healthChecking &&
            SubscriptionComboBox.SelectedItem is SubscriptionRecord subscription &&
            NodeComboBox.SelectedItem is ProxyNode node && _model.CanProbe(subscription, node);
        SubscriptionHealthButton.IsEnabled = !_healthChecking &&
            SubscriptionComboBox.SelectedItem is SubscriptionRecord selected &&
            _model.CanProbeSubscription(selected);
        CancelHealthButton.IsEnabled = _healthCancellation is { IsCancellationRequested: false };
        if (!_model.IsConnected) HealthResultText.Text = "连接后选择节点可进行健康检查";
        if (!_model.IsConnected ||
            SubscriptionComboBox.SelectedItem is not SubscriptionRecord active ||
            !_model.CanProbeSubscription(active))
        {
            _healthCancellation?.Cancel();
            ClearQualityResult();
            if (_model.IsConnected)
                QualityResultText.Text = "当前订阅未加载到运行配置；重新连接后可检查";
        }
        TrafficText.Text = !_model.IsConnected
            ? "未连接时不显示流量"
            : _model.Traffic is { } traffic
                ? $"↑ {FormatBytes(traffic.UploadBytesPerSecond)}/s · ↓ {FormatBytes(traffic.DownloadBytesPerSecond)}/s" +
                  (traffic.UploadTotalBytes is { } upload && traffic.DownloadTotalBytes is { } download
                      ? $" · 本次累计 ↑ {FormatBytes(upload)} / ↓ {FormatBytes(download)}"
                      : string.Empty)
                : "实时速率暂不可用";
    }

    private static string FormatLatency(int? value) => value is { } milliseconds ? $"{milliseconds} ms" : "—";

    private static string FormatBytes(long bytes)
    {
        if (bytes < 1024) return $"{bytes} B";
        if (bytes < 1024L * 1024) return $"{bytes / 1024d:0.0} KiB";
        if (bytes < 1024L * 1024 * 1024) return $"{bytes / (1024d * 1024):0.0} MiB";
        return $"{bytes / (1024d * 1024 * 1024):0.0} GiB";
    }

    private WindowsNetworkOptions ReadNetworkOptions() => new()
    {
        EnableTun = true,
        Ipv6Enabled = Ipv6CheckBox.IsChecked == true,
        BlockUdpStun = BlockStunCheckBox.IsChecked == true,
        DnsProfile = DnsProfileComboBox.SelectedIndex switch
        {
            1 => DnsProfile.AdBlock,
            2 => DnsProfile.Family,
            3 => DnsProfile.Custom,
            _ => DnsProfile.Privacy,
        },
        CustomDnsEndpoint = CustomDnsEndpointBox.Text.Trim(),
    };

    private async void MainWindow_Closed(object sender, WindowEventArgs args)
    {
        _lifetime.Cancel();
        _healthCancellation?.Cancel();
        await _model.DisposeAsync();
        _lifetime.Dispose();
    }
}
