using System.Diagnostics;
using Microsoft.UI.Xaml;
using Weave.Windows.Core;

namespace Weave.Windows;

public sealed partial class MainWindow
{
    // Explicit acceptance mode, restricted to an isolated data directory and a capture run.
    private async Task CheckInteractionsAsync(string capture)
    {
        if (Environment.GetEnvironmentVariable("WEAVE_UI_SELF_TEST") != "1") return;
        if (string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable("WEAVE_DATA_ROOT")))
            throw new InvalidOperationException("Interaction acceptance requires isolated user data.");
        var originalPage = _page;
        foreach (var page in AcceleratorPages)
        {
            NavigateTo(page);
            RootGrid.UpdateLayout();
            if (_pageElements[int.Parse(page)][0].Visibility != Visibility.Visible)
                throw new InvalidOperationException("Navigation did not show page " + page);
        }
        NavigateTo(originalPage);
        if (!_model.IsConnected)
        {
            RecheckNetwork_Click(this, new RoutedEventArgs());
            if (MessageText.Text != L.T("请先连接代理") || MessageBar.Opacity != 1)
                throw new InvalidOperationException("Disconnected network check did not explain its prerequisite.");
        }
        if (ShareLink.Text.Length == 0)
        {
            CopyShareLink_Click(this, new RoutedEventArgs());
            if (MessageText.Text != L.T("请先开始分享，再复制一次性链接。"))
                throw new InvalidOperationException("Empty sharing action did not explain its prerequisite.");
        }
        MessageClose_Click(this, new RoutedEventArgs());
        var disabledWhileBusy = false;
        var busy = RunActionAsync(async () =>
        {
            await Task.Yield();
            disabledWhileBusy = !ImportPanel.IsEnabled && !RoutesPanel.IsEnabled && !SubscriptionsPanel.IsEnabled;
        });
        await busy;
        if (!disabledWhileBusy) throw new InvalidOperationException("Busy controls must be visibly disabled.");
        if (!ImportPanel.IsEnabled || !RoutesPanel.IsEnabled || !SubscriptionsPanel.IsEnabled)
            throw new InvalidOperationException("Controls did not recover after the operation.");

        // A heartbeat on the UI dispatcher must keep running while the software artwork
        // renders. This catches accidentally moving rasterization back onto the UI thread.
        var watch = Stopwatch.StartNew();
        var previous = watch.Elapsed.TotalMilliseconds;
        double maxGap = 0;
        var ticks = 0;
        var timer = DispatcherQueue.CreateTimer();
        timer.Interval = TimeSpan.FromMilliseconds(16);
        timer.Tick += (_, _) => { var now = watch.Elapsed.TotalMilliseconds; maxGap = Math.Max(maxGap, now - previous); previous = now; ticks++; };
        timer.Start();
        try
        {
            Knot.BeginWeaving(_knotPalette);
            await Task.Delay(1500);
            Knot.CompleteWeave(_knotPalette);
            await Task.Delay(1200);
            await Knot.WaitForRenderAsync();
        }
        finally { timer.Stop(); Knot.StopWeaving(); }
        if (ticks < 20 || maxGap > 500)
            throw new InvalidOperationException($"UI dispatcher stalled during animation: {ticks} ticks, {maxGap:F0} ms gap.");
        Knot.Apply(_knotPalette, KnotState.Idle);
        await Knot.WaitForRenderAsync();
        File.WriteAllText(capture + ".interaction.txt", $"Navigation and busy recovery passed. UI heartbeat: {ticks} ticks, maximum gap {maxGap:F1} ms.");
    }
}
