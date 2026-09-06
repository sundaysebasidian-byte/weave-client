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
    private bool _closed;
    private readonly string _themePath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Weave", "appearance.txt");

    public MainWindow()
    {
        InitializeComponent();
        try { _model.Load(); }
        catch (Exception) { MessageText.Text = "本地配置读取失败。原文件已保留，请检查当前 Windows 用户与文件权限。"; }
        _model.StatusChanged += (_, _) => DispatcherQueue.TryEnqueue(() => { if (!_closed) UpdateStatus(); });
        DnsSelector.SelectedIndex = 0;
        try
        {
            if (File.Exists(_themePath) && int.TryParse(File.ReadAllText(_themePath), out var theme) && theme is >= 0 and <= 2)
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
    }

    private void SubscriptionComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        NodeComboBox.ItemsSource = (SubscriptionComboBox.SelectedItem as SubscriptionRecord)?.Nodes;
        NodeComboBox.SelectedItem = null;
    }

    private void SubscriptionListView_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (SubscriptionListView.SelectedItem is SubscriptionRecord selected)
        {
            SubscriptionComboBox.SelectedItem = selected;
            SubscriptionNodesList.ItemsSource = selected.Nodes;
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
                "clipboard://manual",
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

    private async void ImportFileButton_Click(object sender, RoutedEventArgs e)
    {
        await RunActionAsync(async () =>
        {
            var picker = new FileOpenPicker();
            picker.FileTypeFilter.Add(".yaml");
            picker.FileTypeFilter.Add(".yml");
            picker.FileTypeFilter.Add(".txt");
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

        _model.Remove(id);
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
            _model.NetworkOptions = new WindowsNetworkOptions { Ipv6Enabled = Ipv6Toggle.IsOn, DnsProfile = (DnsProfile)Math.Max(0, DnsSelector.SelectedIndex) };
            await _model.ConnectAsync(subscription.Id, node?.Id, _lifetime.Token);
            ConnectButton.Content = "断开连接";
            MessageText.Text = "Mihomo 已启动。Windows TUN 需要系统允许网络适配器与路由变更。";
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
        ImportPanel.IsEnabled = false;
        RoutesPanel.IsEnabled = false;
        SubscriptionsPanel.IsEnabled = false;
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
            ImportPanel.IsEnabled = true;
            RoutesPanel.IsEnabled = true;
            SubscriptionsPanel.IsEnabled = true;
        }

        UpdateStatus();
    }

    private void UpdateStatus()
    {
        StatusText.Text = _model.Status;
        ConnectButton.Content = _model.IsConnected ? "断开连接" : "连接";
    }

    private void AutomaticNode_Click(object sender, RoutedEventArgs e) => NodeComboBox.SelectedItem = null;

    private void Navigate_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not Button { Tag: string page }) return;
        ConnectionPanel.Visibility = page == "0" ? Visibility.Visible : Visibility.Collapsed;
        ImportPanel.Visibility = SubscriptionsPanel.Visibility = SubscriptionNodesList.Visibility = page == "1" ? Visibility.Visible : Visibility.Collapsed;
        RoutesPanel.Visibility = page == "2" ? Visibility.Visible : Visibility.Collapsed;
        SettingsPanel.Visibility = page == "3" ? Visibility.Visible : Visibility.Collapsed;
    }

    private void ThemeSelector_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        var index = ThemeSelector.SelectedIndex;
        RootGrid.RequestedTheme = index == 2 ? ElementTheme.Dark : ElementTheme.Light;
        // An element-local accent keeps the white-green palette separate from the base theme.
        if (index == 1)
            RootGrid.Resources["WeaveAccentBrush"] = new Microsoft.UI.Xaml.Media.SolidColorBrush(global::Windows.UI.Color.FromArgb(255, 22, 167, 108));
        else
            RootGrid.Resources.Remove("WeaveAccentBrush");
        // ThemeResource resolution is refreshed when the theme changes, including light -> white-green.
        RootGrid.RequestedTheme = index == 2 ? ElementTheme.Light : ElementTheme.Dark;
        RootGrid.RequestedTheme = index == 2 ? ElementTheme.Dark : ElementTheme.Light;
        try { Directory.CreateDirectory(Path.GetDirectoryName(_themePath)!); File.WriteAllText(_themePath, index.ToString()); }
        catch (IOException) { MessageText.Text = "外观已切换，但偏好未能保存。"; }
        catch (UnauthorizedAccessException) { MessageText.Text = "外观已切换，但偏好未能保存。"; }
    }

    private async void MainWindow_Closed(object sender, WindowEventArgs args)
    {
        _closed = true;
        _lifetime.Cancel();
        try { await _model.DisposeAsync(); }
        finally { _lifetime.Dispose(); }
    }
}
