using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media.Imaging;
using Weave.Windows.Core;
using Windows.Graphics.Imaging;
using Windows.Storage;
using Windows.Storage.Streams;

namespace Weave.Windows;

// CI-only visual verification. Enabled exclusively by WEAVE_UI_CAPTURE_DIR and never reached in normal use.
public sealed partial class MainWindow
{
    private async Task CaptureIfRequestedAsync()
    {
        var directory = Environment.GetEnvironmentVariable("WEAVE_UI_CAPTURE_DIR");
        if (string.IsNullOrWhiteSpace(directory)) return;

        try
        {
            Directory.CreateDirectory(directory);
            SeedCaptureData();
            // Mica is composed by the system and is not part of a XAML render, so paint its base colour.
            CaptureBackdrop.Visibility = Visibility.Visible;
            foreach (var theme in new[] { ElementTheme.Light, ElementTheme.Dark })
            {
                RootGrid.RequestedTheme = theme;
                foreach (var page in new[] { "overview", "nodes", "subscriptions", "routes", "network" })
                {
                    Navigate(page);
                    await Task.Delay(700);
                    DrawTrafficChart();
                    await Task.Delay(100);
                    await RenderToPngAsync(Path.Combine(directory, $"{theme.ToString().ToLowerInvariant()}-{page}.png"));
                }
            }

            await File.WriteAllTextAsync(Path.Combine(directory, "capture-complete.txt"), "ok");
        }
        catch (Exception exception)
        {
            await File.WriteAllTextAsync(Path.Combine(directory, "capture-error.txt"), exception.ToString());
        }

        Close();
    }

    private void SeedCaptureData()
    {
        var sample = Environment.GetEnvironmentVariable("WEAVE_UI_CAPTURE_SAMPLE");
        if (_model.Subscriptions.Count == 0 && !string.IsNullOrWhiteSpace(sample) && File.Exists(sample))
        {
            var record = _model.ImportFile("示例订阅", sample);
            SetActiveSubscription(record);
            var nodes = record.Nodes;
            _model.AddOrReplaceRoute(new WindowsAppRoute
            {
                ProcessName = "chrome.exe", DisplayName = "chrome.exe", Target = RouteTarget.Automatic(record.Id),
            });
            if (nodes.Count > 1)
            {
                _model.AddOrReplaceRoute(new WindowsAppRoute
                {
                    ProcessName = "Telegram.exe", DisplayName = "Telegram.exe", Target = RouteTarget.Fixed(record.Id, nodes[1].Id),
                });
            }

            _model.AddOrReplaceRoute(new WindowsAppRoute
            {
                ProcessName = "steam.exe", DisplayName = "steam.exe", Target = RouteTarget.Direct(),
            });
            if (nodes.Count > 2) SetSelectedNode(nodes[2].Id);
        }

        // Deterministic sample measurements so the table and health card render populated states.
        var random = new Random(7);
        var snapshots = _nodeItems.Select(item =>
        {
            var baseLatency = random.Next(40, 420);
            var samples = Enumerable.Range(0, 3)
                .Select(_ => random.Next(10) == 0 ? (int?)null : baseLatency + random.Next(0, 90))
                .ToArray();
            return NodeHealthSnapshot.FromSamples(item.Node, samples);
        }).ToArray();
        var rows = NodeQualityMatrix.Build(snapshots).ToDictionary(row => row.Name, StringComparer.Ordinal);
        foreach (var item in _nodeItems) item.SetQuality(rows.GetValueOrDefault(item.Node.RawName));
        _lastQualityCheck = DateTimeOffset.Now;
        NodeSortComboBox.SelectedIndex = 1;
        UpdateNodeHealthCard();

        for (var second = 0; second < TrafficHistoryLength; second++)
        {
            var wave = (Math.Sin(second / 6.0) + 1.3) * 380_000 + random.Next(0, 160_000);
            _trafficHistory.Enqueue(((long)wave, (long)(wave / 7 + random.Next(0, 20_000))));
        }
    }

    private async Task RenderToPngAsync(string path)
    {
        var bitmap = new RenderTargetBitmap();
        await bitmap.RenderAsync(RootGrid);
        var buffer = await bitmap.GetPixelsAsync();
        var pixels = new byte[buffer.Length];
        using (var reader = DataReader.FromBuffer(buffer))
        {
            reader.ReadBytes(pixels);
        }

        var folder = await StorageFolder.GetFolderFromPathAsync(Path.GetDirectoryName(path)!);
        var file = await folder.CreateFileAsync(Path.GetFileName(path), CreationCollisionOption.ReplaceExisting);
        using var stream = await file.OpenAsync(FileAccessMode.ReadWrite);
        var encoder = await BitmapEncoder.CreateAsync(BitmapEncoder.PngEncoderId, stream);
        encoder.SetPixelData(BitmapPixelFormat.Bgra8, BitmapAlphaMode.Premultiplied,
            (uint)bitmap.PixelWidth, (uint)bitmap.PixelHeight, 96, 96, pixels);
        await encoder.FlushAsync();
    }
}
