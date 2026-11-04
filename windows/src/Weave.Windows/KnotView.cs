using System.Runtime.InteropServices.WindowsRuntime;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Imaging;
using Weave.Windows.Core;

namespace Weave.Windows;

/// <summary>
/// Shows the Weave knot. The pixels come from <see cref="KnotRenderer"/> at the display's real resolution; while the ribbon is
/// being woven a lighter draft is drawn each frame and the full-quality image replaces it at the end.
/// </summary>
public sealed class KnotView : UserControl
{
    private const int LogicalSize = 360;
    private readonly Image _image = new() { Stretch = Stretch.Uniform, IsHitTestVisible = false };
    private WriteableBitmap? _bitmap;
    private KnotPalette _palette;
    private KnotState _state = KnotState.Idle;
    private double _progress = 1;
    private DispatcherQueueTimer? _timer;

    public KnotView()
    {
        Content = _image;
        IsTabStop = false;
        IsHitTestVisible = false;
        AutomationProperties.SetAccessibilityView(this, Microsoft.UI.Xaml.Automation.Peers.AccessibilityView.Raw);
        Loaded += (_, _) => { if (_palette != default) Render(final: true); };
        Unloaded += (_, _) => StopWeaving();
    }

    /// <summary>Draws the knot in a fixed state immediately.</summary>
    public void Apply(KnotPalette palette, KnotState state, double progress = 1)
    {
        StopWeaving();
        _palette = palette; _state = state; _progress = progress;
        Render(final: true);
    }

    /// <summary>Weaves the ribbon in over <paramref name="duration"/>, then leaves the tied, lit knot.</summary>
    public void Weave(TimeSpan duration)
    {
        StopWeaving();
        if (!PageMotion.AnimationsAllowed || duration <= TimeSpan.Zero) { _state = KnotState.Connected; _progress = 1; Render(true); return; }
        _state = KnotState.Connecting;
        var started = DateTimeOffset.UtcNow;
        var timer = DispatcherQueue.CreateTimer();
        timer.Interval = TimeSpan.FromMilliseconds(40);
        timer.Tick += (_, _) =>
        {
            var x = Math.Clamp((DateTimeOffset.UtcNow - started) / duration, 0, 1);
            _progress = x < 0.5 ? 4 * x * x * x : 1 - Math.Pow(-2 * x + 2, 3) / 2;
            if (x >= 1) { StopWeaving(); _state = KnotState.Connected; _progress = 1; Render(true); }
            else Render(false);
        };
        _timer = timer;
        timer.Start();
        Render(false);
    }

    public void StopWeaving()
    {
        var timer = _timer;
        _timer = null;
        timer?.Stop();
    }

    private void Render(bool final)
    {
        var scale = XamlRoot?.RasterizationScale ?? 1.25;
        var pixels = (int)Math.Clamp(Math.Round(LogicalSize * scale * (final ? 1.0 : 0.62)), 160, 1100);
        var bytes = KnotRenderer.Render(pixels, _palette, _state, _progress);
        if (_bitmap is null || _bitmap.PixelWidth != pixels)
        {
            _bitmap = new WriteableBitmap(pixels, pixels);
            _image.Source = _bitmap;
        }
        bytes.CopyTo(_bitmap.PixelBuffer);
        _bitmap.Invalidate();
    }
}
