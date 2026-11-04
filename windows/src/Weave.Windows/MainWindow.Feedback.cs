using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Weave.Windows.Core;
using global::Windows.ApplicationModel.DataTransfer;

namespace Weave.Windows;

internal enum MessageKind { Info, Success, Warning, Error }

/// <summary>
/// Feedback: messages become floating notices with a tone and an end (success and info fade by themselves, errors wait to be
/// dismissed), plus drag-and-drop and paste import, copy confirmations and the like.
/// </summary>
public sealed partial class MainWindow
{
    private MessageKind _messageKind = MessageKind.Info;
    private DispatcherQueueTimer? _messageTimer;

    // Every `MessageText.Text = ...` in the window lands here; the kind is set just before for failures.
    private void ObserveMessages() =>
        MessageText.RegisterPropertyChangedCallback(TextBlock.TextProperty, (_, _) => OnMessageChanged());

    private void OnMessageChanged()
    {
        _messageTimer?.Stop();
        if (string.IsNullOrEmpty(MessageText.Text))
        {
            MessageBar.Opacity = 0;
            MessageBar.IsHitTestVisible = false;
            _messageKind = MessageKind.Info;
            return;
        }
        var kind = _messageKind;
        _messageKind = MessageKind.Info;
        MessageDot.Fill = ThemeBrush(kind switch
        {
            MessageKind.Error => "WeaveDangerBrush", MessageKind.Warning => "WeaveCautionBrush",
            MessageKind.Success => "WeaveSuccessBrush", _ => "WeaveSunBrush",
        });
        MessageBar.Opacity = 1;
        MessageBar.IsHitTestVisible = true;
        var seconds = kind switch { MessageKind.Error => 0, MessageKind.Warning => 9, _ => 5 };
        if (seconds == 0) return; // errors stay until dismissed
        var timer = _messageTimer ??= DispatcherQueue.CreateTimer();
        timer.IsRepeating = false;
        timer.Interval = TimeSpan.FromSeconds(seconds);
        timer.Tick -= MessageTimerTick;
        timer.Tick += MessageTimerTick;
        timer.Start();
    }

    private void MessageTimerTick(DispatcherQueueTimer sender, object args)
    {
        sender.Stop();
        if (!_closed) MessageText.Text = "";
    }

    private void MessageClose_Click(object sender, RoutedEventArgs e) => MessageText.Text = "";

    private void Toast(string text, MessageKind kind = MessageKind.Info)
    {
        MessageText.Text = "";
        _messageKind = kind;
        MessageText.Text = text;
    }

    private void CopyText(string text, string confirmation)
    {
        try
        {
            var package = new DataPackage();
            package.SetText(text);
            Clipboard.SetContentWithOptions(package, new ClipboardContentOptions { IsAllowedInHistory = false, IsRoamable = false });
            Toast(confirmation, MessageKind.Success);
        }
        catch (Exception error) when (error is System.Runtime.InteropServices.COMException or InvalidOperationException or UnauthorizedAccessException)
        {
            Toast(L.T("无法写入剪贴板，请重试"), MessageKind.Error);
        }
    }

    // ---------------------------------------------------------------- drag and drop, paste

    private static bool CanImport(DataPackageView view) =>
        view.Contains(StandardDataFormats.StorageItems) || view.Contains(StandardDataFormats.WebLink) || view.Contains(StandardDataFormats.Text);

    private void Root_DragOver(object sender, DragEventArgs e)
    {
        if (_busy || !_dataLoaded || !CanImport(e.DataView))
        {
            e.AcceptedOperation = DataPackageOperation.None;
            DropHint.Opacity = 0;
            return;
        }
        e.AcceptedOperation = DataPackageOperation.Copy;
        e.DragUIOverride.Caption = L.T("松开以导入");
        e.DragUIOverride.IsGlyphVisible = false;
        DropHint.Opacity = 1;
    }

    private void Root_DragLeave(object sender, DragEventArgs e) => DropHint.Opacity = 0;

    private async void Root_Drop(object sender, DragEventArgs e)
    {
        DropHint.Opacity = 0;
        if (_busy || !_dataLoaded) return;
        var deferral = e.GetDeferral();
        try
        {
            string? path = null, text = null;
            if (e.DataView.Contains(StandardDataFormats.StorageItems))
            {
                var items = await e.DataView.GetStorageItemsAsync();
                path = items.OfType<global::Windows.Storage.StorageFile>().Select(file => file.Path).FirstOrDefault(value => !string.IsNullOrEmpty(value));
            }
            if (path is null && e.DataView.Contains(StandardDataFormats.WebLink)) text = (await e.DataView.GetWebLinkAsync()).AbsoluteUri;
            if (path is null && text is null && e.DataView.Contains(StandardDataFormats.Text)) text = await e.DataView.GetTextAsync();
            await ImportSourceAsync(path, text);
        }
        finally { deferral.Complete(); }
    }

    // Ctrl+V on the Subscriptions page imports a copied link or configuration; inside a text field it stays an ordinary paste.
    private async void PasteAccelerator_Invoked(KeyboardAccelerator sender, KeyboardAcceleratorInvokedEventArgs args)
    {
        if (_page != "1" || _busy || !_dataLoaded) return;
        if (FocusManager.GetFocusedElement(RootGrid.XamlRoot) is TextBox or ComboBox or RichEditBox or PasswordBox) return;
        var view = Clipboard.GetContent();
        if (!view.Contains(StandardDataFormats.Text)) return;
        args.Handled = true;
        await ImportSourceAsync(null, await view.GetTextAsync());
    }

    private async Task ImportSourceAsync(string? path, string? text)
    {
        if (path is null && string.IsNullOrWhiteSpace(text)) return;
        NavigateTo("1");
        await RunCancellableAsync(async token =>
        {
            SubscriptionRecord record;
            if (path is not null) record = await _model.ImportFileAsync(SubscriptionNameBox.Text, path, token);
            else
            {
                var value = text!.Trim();
                record = value.StartsWith("https://", StringComparison.OrdinalIgnoreCase) && !value.Contains('\n')
                    ? await _model.ImportUrlAsync(SubscriptionNameBox.Text, value, token)
                    : await _model.ImportTextAsync(SubscriptionNameBox.Text, "clipboard://manual", value, token);
            }
            SubscriptionComboBox.SelectedItem = record;
            SubscriptionListView.SelectedItems.Clear();
            SubscriptionListView.SelectedItems.Add(record);
            SubscriptionUrlBox.Text = ""; SubscriptionTextBox.Text = "";
            RefreshHealthRings();
            RefreshQuickNodes();
            Toast(L.F($"已导入 {record.Name}，发现 {record.Nodes.Count} 个节点"), MessageKind.Success);
        });
    }
}
