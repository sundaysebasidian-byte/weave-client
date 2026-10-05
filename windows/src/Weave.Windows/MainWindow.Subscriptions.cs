using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Weave.Windows.Core;

namespace Weave.Windows;

public sealed partial class MainWindow
{
    private string? _missingFixedNodeId;
    private string? _missingSubscriptionId;

    private async Task<bool> UpdateSubscriptionWithReviewAsync(SubscriptionRecord record, string name, string source)
    {
        var selected = SubscriptionComboBox.SelectedItem as SubscriptionRecord;
        var nodeId = (NodeComboBox.SelectedItem as ProxyNode)?.Id ?? _missingFixedNodeId;
        var changed = await _model.EditAsync(record, name, source, _lifetime.Token, async (before, after) =>
        {
            var retained = after.Nodes.Select(node => node.Id).ToHashSet();
            var previous = before.Nodes.Select(node => node.Id).ToHashSet();
            var removed = before.Nodes.Where(node => !retained.Contains(node.Id)).ToArray();
            var added = after.Nodes.Where(node => !previous.Contains(node.Id)).ToArray();
            var removedIds = removed.Select(node => node.Id).ToHashSet();
            var affected = _model.AppRoutes.Count(route => route.Target.SubscriptionId == before.Id &&
                route.Target.NodeId is { } id && removedIds.Contains(id));
            var text = new System.Text.StringBuilder(before.Name).AppendLine()
                .Append(before.Nodes.Count).Append(" → ").Append(after.Nodes.Count).AppendLine()
                .Append(L.T("新增节点")).Append(": ").Append(added.Length).AppendLine()
                .AppendLine(string.Join("\n", added.Take(40).Select(node => "+ " + node.Name)))
                .Append(L.T("移除节点")).Append(": ").Append(removed.Length).AppendLine()
                .AppendLine(string.Join("\n", removed.Take(40).Select(node => "− " + node.Name)))
                .Append(L.T("受影响的应用规则")).Append(": ").Append(affected).AppendLine()
                .AppendLine(L.T("仅列出前 40 个变化名称。确认后保存，当前连接仍使用旧配置，重新连接后生效。"));
            if (selected?.Id == before.Id && nodeId is not null && removedIds.Contains(nodeId))
                text.AppendLine(L.T("原固定节点已移除，请重新选择；不会自动切换出口"));
            var dialog = new ContentDialog {
                Title = L.T("订阅更新预览"),
                Content = new ScrollViewer { MaxHeight = 420, Content = new TextBlock { Text = text.ToString(), TextWrapping = TextWrapping.Wrap } },
                PrimaryButtonText = L.T("确认替换"), CloseButtonText = L.T("取消"), XamlRoot = RootGrid.XamlRoot,
                DefaultButton = ContentDialogButton.Close,
            };
            return await dialog.ShowAsync() == ContentDialogResult.Primary;
        });
        if (changed && selected?.Id == record.Id)
        {
            var updated = _model.Subscriptions.First(item => item.Id == record.Id);
            SubscriptionComboBox.SelectedItem = updated;
            NodeComboBox.SelectedItem = updated.Nodes.FirstOrDefault(node => node.Id == nodeId);
            _missingFixedNodeId = nodeId is not null && NodeComboBox.SelectedItem is null ? nodeId : null;
            NodeComboBox.PlaceholderText = L.T(_missingFixedNodeId is null ? "自动选择 · 最低延迟" : "原固定节点已移除，请重新选择；不会自动切换出口");
            SubscriptionNodesList.ItemsSource = updated.Nodes;
        }
        return changed;
    }

    private void DefaultNode_Changed(object sender, SelectionChangedEventArgs e)
    {
        if (NodeComboBox.SelectedItem is ProxyNode) _missingFixedNodeId = null;
    }
}
