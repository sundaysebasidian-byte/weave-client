using System.Diagnostics;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Weave.Windows.Core;
using Windows.Storage.Pickers;
using WinRT.Interop;

namespace Weave.Windows;

public sealed partial class MainWindow
{
    private string? _editingProcess;
    private string? _routeSubscriptionId;
    private string[] _runningProcesses = Array.Empty<string>();
    private DateTimeOffset _processesListedAt;

    private void OnRoutesChanged()
    {
        _routeItems.Clear();
        foreach (var route in _model.AppRoutes.OrderBy(route => route.ProcessName, StringComparer.OrdinalIgnoreCase))
        {
            _routeItems.Add(RouteItem.From(route, _model.Subscriptions));
        }

        RouteCountText.Text = _routeItems.Count == 0 ? "规则" : $"规则 · {_routeItems.Count}";
        RoutesEmptyPanel.Visibility = Show(_routeItems.Count == 0);
        RouteListView.Visibility = Show(_routeItems.Count > 0);
        UpdateNetworkChips();
    }

    private void RestoreRouteSubscriptionSelection()
    {
        var selected = _model.Subscriptions.FirstOrDefault(item => item.Id == _routeSubscriptionId)
                       ?? _activeSubscription
                       ?? _model.Subscriptions.FirstOrDefault();
        if (!ReferenceEquals(RouteSubscriptionComboBox.SelectedItem, selected))
        {
            RouteSubscriptionComboBox.SelectedItem = selected;
        }
    }

    private void RouteSubscriptionComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (RouteSubscriptionComboBox.SelectedItem is not SubscriptionRecord record) return;
        var previousNode = (RouteNodeComboBox.SelectedItem as ProxyNode)?.Id;
        _routeSubscriptionId = record.Id;
        RouteNodeComboBox.ItemsSource = record.Nodes;
        RouteNodeComboBox.SelectedItem = record.Nodes.FirstOrDefault(node => node.Id == previousNode);
    }

    private void RouteTargetModeComboBox_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (RouteNodeComboBox is null || RouteSubscriptionComboBox is null) return;
        var mode = RouteTargetModeComboBox.SelectedIndex;
        RouteSubscriptionComboBox.IsEnabled = mode is 0 or 1;
        RouteNodeComboBox.IsEnabled = mode == 1;
        if (mode != 1) RouteNodeComboBox.SelectedItem = null;
    }

    private async void ProcessNameBox_GotFocus(object sender, RoutedEventArgs e)
    {
        if (DateTimeOffset.Now - _processesListedAt < TimeSpan.FromSeconds(10)) return;
        _processesListedAt = DateTimeOffset.Now;
        _runningProcesses = await Task.Run(() =>
        {
            var names = new SortedSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var process in Process.GetProcesses())
            {
                using (process)
                {
                    try
                    {
                        if (process.Id > 4 && !string.IsNullOrWhiteSpace(process.ProcessName))
                            names.Add($"{process.ProcessName}.exe");
                    }
                    catch (InvalidOperationException)
                    {
                        // The process exited while enumerating.
                    }
                }
            }

            return names.ToArray();
        });
    }

    private void ProcessNameBox_TextChanged(AutoSuggestBox sender, AutoSuggestBoxTextChangedEventArgs args)
    {
        if (args.Reason != AutoSuggestionBoxTextChangeReason.UserInput) return;
        var query = sender.Text.Trim();
        sender.ItemsSource = query.Length == 0
            ? null
            : _runningProcesses
                .Where(name => name.Contains(query, StringComparison.OrdinalIgnoreCase))
                .OrderBy(name => !name.StartsWith(query, StringComparison.OrdinalIgnoreCase))
                .Take(8)
                .ToArray();
    }

    private void ProcessNameBox_QuerySubmitted(AutoSuggestBox sender, AutoSuggestBoxQuerySubmittedEventArgs args)
    {
        if (args.ChosenSuggestion is string chosen)
        {
            sender.Text = chosen;
            return;
        }

        SaveRoute();
    }

    private async void BrowseExeButton_Click(object sender, RoutedEventArgs e)
    {
        var picker = new FileOpenPicker();
        picker.FileTypeFilter.Add(".exe");
        InitializeWithWindow.Initialize(picker, WindowNative.GetWindowHandle(this));
        var file = await picker.PickSingleFileAsync();
        if (file is not null) ProcessNameBox.Text = file.Name;
    }

    private void SaveRouteButton_Click(object sender, RoutedEventArgs e) => SaveRoute();

    private void SaveRoute()
    {
        try
        {
            var subscription = RouteSubscriptionComboBox.SelectedItem as SubscriptionRecord;
            var target = RouteTargetModeComboBox.SelectedIndex switch
            {
                2 => RouteTarget.Direct(),
                3 => RouteTarget.Block(),
                1 when subscription is not null && RouteNodeComboBox.SelectedItem is ProxyNode node =>
                    RouteTarget.Fixed(subscription.Id, node.Id),
                1 => throw new InvalidDataException("固定节点规则需要先选择订阅和节点"),
                _ when subscription is not null => RouteTarget.Automatic(subscription.Id),
                _ => throw new InvalidDataException("自动测速规则需要先选择订阅"),
            };
            var processName = ProcessNameBox.Text.Trim();
            _model.AddOrReplaceRoute(new WindowsAppRoute
            {
                ProcessName = processName,
                DisplayName = processName,
                Target = target,
            });

            var savedName = Path.GetFileName(processName);
            if (_editingProcess is { } original && !original.Equals(savedName, StringComparison.OrdinalIgnoreCase))
            {
                _model.RemoveRoute(original);
            }

            var updated = _editingProcess is not null;
            ResetRouteEditor();
            MarkDirty("应用分流");
            Notify(updated ? $"已更新 {savedName} 的分流规则。" : $"已添加 {savedName} 的分流规则。", InfoBarSeverity.Success);
        }
        catch (Exception exception)
        {
            Notify(exception.Message, InfoBarSeverity.Warning, "无法保存规则");
        }
    }

    private void RouteListView_ItemClick(object sender, ItemClickEventArgs e)
    {
        if (e.ClickedItem is RouteItem item) BeginEditRoute(item.Route);
    }

    private void EditRouteButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button { Tag: string processName } &&
            _model.AppRoutes.FirstOrDefault(route => route.ProcessName == processName) is { } route)
        {
            BeginEditRoute(route);
        }
    }

    private void BeginEditRoute(WindowsAppRoute route)
    {
        _editingProcess = route.ProcessName;
        ProcessNameBox.Text = route.ProcessName;
        RouteTargetModeComboBox.SelectedIndex = route.Target.Kind switch
        {
            RouteKind.FixedNode => 1,
            RouteKind.Direct => 2,
            RouteKind.Block => 3,
            _ => 0,
        };
        if (_model.Subscriptions.FirstOrDefault(item => item.Id == route.Target.SubscriptionId) is { } subscription)
        {
            RouteSubscriptionComboBox.SelectedItem = subscription;
            RouteNodeComboBox.SelectedItem = subscription.Nodes.FirstOrDefault(node => node.Id == route.Target.NodeId);
        }

        RouteEditorTitle.Text = $"编辑规则 · {route.ProcessName}";
        SaveRouteButtonText.Text = "更新规则";
        CancelRouteEditButton.Visibility = Visibility.Visible;
        ProcessNameBox.Focus(FocusState.Programmatic);
    }

    private void CancelRouteEditButton_Click(object sender, RoutedEventArgs e) => ResetRouteEditor();

    private void ResetRouteEditor()
    {
        _editingProcess = null;
        ProcessNameBox.Text = string.Empty;
        RouteEditorTitle.Text = "添加规则";
        SaveRouteButtonText.Text = "添加规则";
        CancelRouteEditButton.Visibility = Visibility.Collapsed;
    }

    private void DeleteRouteButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not Button { Tag: string processName } ||
            _model.AppRoutes.FirstOrDefault(route => route.ProcessName == processName) is not { } route)
        {
            return;
        }

        try
        {
            _model.RemoveRoute(processName);
            if (processName.Equals(_editingProcess, StringComparison.OrdinalIgnoreCase)) ResetRouteEditor();
            MarkDirty("应用分流");
            Notify($"已删除 {processName} 的分流规则。", InfoBarSeverity.Informational, null, "撤销", () =>
            {
                try
                {
                    if (route.Target.SubscriptionId is { } subscriptionId &&
                        _model.Subscriptions.All(item => item.Id != subscriptionId))
                    {
                        throw new InvalidDataException("规则引用的订阅已被删除");
                    }

                    _model.AddOrReplaceRoute(route);
                    MarkDirty("应用分流");
                }
                catch (Exception exception)
                {
                    Notify(exception.Message, InfoBarSeverity.Error, "无法恢复规则");
                }
            });
        }
        catch (Exception exception)
        {
            Notify(exception.Message, InfoBarSeverity.Error, "无法删除规则");
        }
    }
}
