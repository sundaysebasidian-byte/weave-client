using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Weave.Windows.Core;
using Windows.ApplicationModel.DataTransfer;
using Windows.Storage;
using Windows.Storage.Pickers;
using Windows.System;
using WinRT.Interop;

namespace Weave.Windows;

public sealed partial class MainWindow
{
    private bool _subscriptionBusy;

    private void OnSubscriptionsChanged()
    {
        // Imports and refreshes replace records in place; follow the active subscription by id so
        // node lists are rebuilt from the new record, and fall back to the first one after a delete.
        var resolved = _activeSubscription is null
            ? null
            : _model.Subscriptions.FirstOrDefault(item => item.Id == _activeSubscription.Id);
        resolved ??= _model.Subscriptions.FirstOrDefault();
        if (ReferenceEquals(resolved, _activeSubscription))
        {
            ResyncSubscriptionSelectors();
        }
        else
        {
            SetActiveSubscription(resolved);
        }

        RestoreRouteSubscriptionSelection();
        UpdateSubscriptionsPage();
        OnRoutesChanged();
    }

    private void UpdateSubscriptionsPage()
    {
        var count = _model.Subscriptions.Count;
        SubscriptionCountText.Text = count == 0 ? "已导入" : $"已导入 · {count}";
        SubscriptionsEmptyPanel.Visibility = Show(count == 0);
        RefreshAllButton.IsEnabled = !_subscriptionBusy && _model.Subscriptions.Any(item => item.CanRefresh);
        ImportUrlButton.IsEnabled = !_subscriptionBusy;
        ImportUrlRing.IsActive = _subscriptionBusy;
        ImportUrlButtonText.Opacity = _subscriptionBusy ? 0 : 1;
    }

    private void SetSubscriptionBusy(bool busy)
    {
        _subscriptionBusy = busy;
        UpdateSubscriptionsPage();
    }

    /// <summary>Applies a freshly imported record: switch to it unless that would silently change a live exit.</summary>
    private void AdoptImported(SubscriptionRecord record, bool replacedExisting)
    {
        var isActive = record.Id == _activeSubscription?.Id;
        if (isActive || _activeSubscription is null || !_model.IsConnected)
        {
            SetActiveSubscription(record);
        }

        if (_model.IsConnected && (isActive || replacedExisting)) MarkDirty("订阅内容");
        var verb = replacedExisting ? "已更新" : "已导入";
        if (!_model.IsConnected || isActive || _activeSubscription?.Id == record.Id)
        {
            Notify($"{verb}“{record.Name}”，共 {record.Nodes.Count} 个节点。", InfoBarSeverity.Success);
        }
        else
        {
            Notify($"{verb}“{record.Name}”，共 {record.Nodes.Count} 个节点。当前连接的出口保持不变。",
                InfoBarSeverity.Success, null, "设为出口", () => SetActiveSubscription(
                    _model.Subscriptions.FirstOrDefault(item => item.Id == record.Id)));
        }
    }

    private async void ImportUrlButton_Click(object sender, RoutedEventArgs e) => await ImportUrlAsync();

    private async void SubscriptionUrlBox_KeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key != VirtualKey.Enter) return;
        e.Handled = true;
        await ImportUrlAsync();
    }

    private async Task ImportUrlAsync()
    {
        if (_subscriptionBusy) return;
        var url = SubscriptionUrlBox.Text.Trim();
        if (url.Length == 0)
        {
            Notify("请输入 HTTPS 订阅链接。", InfoBarSeverity.Warning);
            SubscriptionUrlBox.Focus(FocusState.Programmatic);
            return;
        }

        SetSubscriptionBusy(true);
        try
        {
            await RunActionAsync(async () =>
            {
                var knownIds = _model.Subscriptions.Select(item => item.Id).ToHashSet(StringComparer.Ordinal);
                var record = await _model.ImportUrlAsync(SubscriptionNameBox.Text, url, _lifetime.Token);
                SubscriptionUrlBox.Text = string.Empty;
                SubscriptionNameBox.Text = string.Empty;
                AdoptImported(record, knownIds.Contains(record.Id));
            }, "导入失败");
        }
        finally
        {
            SetSubscriptionBusy(false);
        }
    }

    private async void ImportFileButton_Click(object sender, RoutedEventArgs e)
    {
        var picker = new FileOpenPicker();
        foreach (var extension in new[] { ".yaml", ".yml", ".txt", ".json" }) picker.FileTypeFilter.Add(extension);
        InitializeWithWindow.Initialize(picker, WindowNative.GetWindowHandle(this));
        var file = await picker.PickSingleFileAsync();
        if (file is not null) await ImportFileAsync(file.Path);
    }

    private async Task ImportFileAsync(string path)
    {
        await RunActionAsync(() =>
        {
            var knownIds = _model.Subscriptions.Select(item => item.Id).ToHashSet(StringComparer.Ordinal);
            var record = _model.ImportFile(SubscriptionNameBox.Text, path);
            SubscriptionNameBox.Text = string.Empty;
            AdoptImported(record, knownIds.Contains(record.Id));
            return Task.CompletedTask;
        }, "导入失败");
    }

    private void PasteToggle_Changed(object sender, RoutedEventArgs e)
    {
        PastePanel.Visibility = Show(PasteToggle.IsChecked == true);
        if (PasteToggle.IsChecked == true) SubscriptionTextBox.Focus(FocusState.Programmatic);
    }

    private async void ImportTextButton_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(SubscriptionTextBox.Text))
        {
            Notify("请先粘贴订阅内容。", InfoBarSeverity.Warning);
            return;
        }

        await RunActionAsync(() =>
        {
            var record = _model.ImportText(SubscriptionNameBox.Text, $"inline://{Guid.NewGuid():N}", SubscriptionTextBox.Text);
            SubscriptionTextBox.Text = string.Empty;
            SubscriptionNameBox.Text = string.Empty;
            PasteToggle.IsChecked = false;
            AdoptImported(record, replacedExisting: false);
            return Task.CompletedTask;
        }, "导入失败");
    }

    private void ContentHost_DragOver(object sender, DragEventArgs e)
    {
        if (!e.DataView.Contains(StandardDataFormats.StorageItems)) return;
        e.AcceptedOperation = DataPackageOperation.Copy;
        e.DragUIOverride.Caption = "导入为订阅";
    }

    private async void ContentHost_Drop(object sender, DragEventArgs e)
    {
        if (!e.DataView.Contains(StandardDataFormats.StorageItems)) return;
        var items = await e.DataView.GetStorageItemsAsync();
        var file = items.OfType<StorageFile>().FirstOrDefault();
        if (file is null)
        {
            Notify("请拖入订阅文件，而不是文件夹。", InfoBarSeverity.Warning);
            return;
        }

        Navigate("subscriptions");
        await ImportFileAsync(file.Path);
    }

    private async void RefreshSubscriptionButton_Click(object sender, RoutedEventArgs e)
    {
        if (_subscriptionBusy || sender is not Button { Tag: string id } ||
            _model.Subscriptions.FirstOrDefault(item => item.Id == id) is not { } current)
        {
            return;
        }

        SetSubscriptionBusy(true);
        try
        {
            await RunActionAsync(async () =>
            {
                var update = await _model.RefreshAsync(current, _lifetime.Token);
                ApplyRefreshed(update);
                Notify($"已刷新“{update.Record.Name}”：新增 {update.AddedNodes}，移除 {update.RemovedNodes}，保留 {update.RetainedNodes} 个节点。",
                    InfoBarSeverity.Success);
            }, "刷新失败");
        }
        finally
        {
            SetSubscriptionBusy(false);
        }
    }

    private async void RefreshAllButton_Click(object sender, RoutedEventArgs e)
    {
        if (_subscriptionBusy) return;
        var targets = _model.Subscriptions.Where(item => item.CanRefresh).ToList();
        if (targets.Count == 0) return;

        SetSubscriptionBusy(true);
        var refreshed = 0;
        var failures = new List<string>();
        try
        {
            foreach (var target in targets)
            {
                var current = _model.Subscriptions.FirstOrDefault(item => item.Id == target.Id);
                if (current is null) continue;
                try
                {
                    ApplyRefreshed(await _model.RefreshAsync(current, _lifetime.Token));
                    refreshed++;
                }
                catch (OperationCanceledException)
                {
                    return;
                }
                catch (Exception exception)
                {
                    failures.Add($"{current.Name}：{exception.Message}");
                }
            }
        }
        finally
        {
            SetSubscriptionBusy(false);
        }

        if (failures.Count == 0)
        {
            Notify($"已刷新 {refreshed} 个订阅。", InfoBarSeverity.Success);
        }
        else
        {
            Notify(string.Join("\n", failures), InfoBarSeverity.Warning, $"已刷新 {refreshed} 个，{failures.Count} 个失败");
        }
    }

    private void ApplyRefreshed(SubscriptionUpdate update) => MarkDirty("订阅内容");

    private async void DeleteSubscriptionButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not Button { Tag: string id } ||
            _model.Subscriptions.FirstOrDefault(item => item.Id == id) is not { } record)
        {
            return;
        }

        var inUse = _model.IsConnected && _runningSubscriptionId == id;
        var dialog = new ContentDialog
        {
            Title = "删除订阅？",
            Content = $"将从本机删除“{record.Name}”及其加密内容，此操作无法撤销。" +
                      (inUse ? "\n当前连接仍会使用已加载的配置，直到断开。" : string.Empty),
            PrimaryButtonText = "删除",
            CloseButtonText = "取消",
            DefaultButton = ContentDialogButton.Close,
            XamlRoot = RootGrid.XamlRoot,
            RequestedTheme = RootGrid.ActualTheme,
        };
        if (await dialog.ShowAsync() != ContentDialogResult.Primary) return;

        await RunActionAsync(() =>
        {
            _model.Remove(id);
            MarkDirty("订阅内容");
            Notify($"已删除“{record.Name}”。", InfoBarSeverity.Informational);
            return Task.CompletedTask;
        }, "无法删除订阅");
    }
}
