using System.Runtime.InteropServices.WindowsRuntime;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Animation;
using Microsoft.UI.Xaml.Media.Imaging;
using Microsoft.UI.Xaml.Shapes;
using Weave.Windows.Core;

namespace Weave.Windows;

/// <summary>
/// Shows the Weave knot. The pixels come from <see cref="KnotRenderer"/> at the display's real resolution.
/// State changes never jump: connecting weaves the ribbon in over the ghost, a confirmed connection finishes the weave and
/// lights the sun with a bloom and a ripple, and disconnecting cross-fades back to the ghost. Fades and the bloom are
/// independent (compositor) animations; only the weave itself redraws frames, at a lighter resolution.
/// </summary>
public sealed class KnotView : UserControl
{
    private const int LogicalSize = 360;
    // Where KnotRenderer puts the sun, as fractions of the square.
    private const double SunX = 0.80, SunY = 0.19, SunRadius = 0.055;
    private readonly Grid _root = new();
    private readonly Image _back = new() { Stretch = Stretch.Uniform, IsHitTestVisible = false };
    private readonly Image _front = new() { Stretch = Stretch.Uniform, IsHitTestVisible = false };
    private readonly Ellipse _bloom = new() { IsHitTestVisible = false, Opacity = 0, RenderTransformOrigin = new(0.5, 0.5) };
    private readonly Ellipse _ripple = new() { IsHitTestVisible = false, Opacity = 0, StrokeThickness = 1.4, RenderTransformOrigin = new(0.5, 0.5) };
    private readonly ScaleTransform _frontScale = new() { ScaleX = 1, ScaleY = 1 };
    private readonly ScaleTransform _bloomScale = new();
    private readonly ScaleTransform _rippleScale = new();
    private WriteableBitmap? _frontBitmap;
    private KnotPalette _palette;
    private KnotState _state = KnotState.Idle;
    private double _progress = 1;
    private DispatcherQueueTimer? _timer;
    private Storyboard? _fade;

    public KnotView()
    {
        _front.RenderTransform = _frontScale;
        _front.RenderTransformOrigin = new(0.46, 0.52);
        _bloom.RenderTransform = _bloomScale;
        _ripple.RenderTransform = _rippleScale;
        _root.Children.Add(_back);
        _root.Children.Add(_front);
        _root.Children.Add(_bloom);
        _root.Children.Add(_ripple);
        Content = _root;
        IsTabStop = false;
        IsHitTestVisible = false;
        AutomationProperties.SetAccessibilityView(this, Microsoft.UI.Xaml.Automation.Peers.AccessibilityView.Raw);
        Loaded += (_, _) => { if (_palette != default) Render(final: true); };
        Unloaded += (_, _) => StopWeaving();
        SizeChanged += (_, _) => PlaceSun();
    }

    /// <summary>Draws the knot in a fixed state immediately (first frame, theme change).</summary>
    public void Apply(KnotPalette palette, KnotState state, double progress = 1)
    {
        StopWeaving();
        _fade?.Stop();
        _palette = palette; _state = state; _progress = progress;
        _back.Source = null;
        _front.Opacity = 1;
        _frontScale.ScaleX = _frontScale.ScaleY = 1;
        Render(final: true);
    }

    /// <summary>Connecting: the ribbon weaves itself in and holds a little back until the link is confirmed.</summary>
    public void BeginWeaving(KnotPalette palette)
    {
        _palette = palette;
        if (!PageMotion.AnimationsAllowed) { Apply(palette, KnotState.Connecting, 0.7); return; }
        Snapshot();
        _state = KnotState.Connecting;
        _progress = 0;
        _front.Opacity = 1; // the ghost stays underneath while the ribbon is woven over it
        Render(final: false);
        RunTimer(elapsed =>
        {
            // Approaches 85% and breathes there until the connection is confirmed.
            var t = elapsed.TotalSeconds;
            _progress = 0.85 * (1 - Math.Exp(-t / 0.75)) + 0.015 * Math.Sin(t * 2.4);
            Render(final: false);
            return false;
        });
    }

    /// <summary>Connected: finishes the weave from wherever it is, then lights the sun.</summary>
    public void CompleteWeave(KnotPalette palette)
    {
        _palette = palette;
        if (!PageMotion.AnimationsAllowed) { Apply(palette, KnotState.Connected); return; }
        var start = _state == KnotState.Connecting ? Math.Clamp(_progress, 0, 1) : 0;
        if (_state != KnotState.Connecting)
        {
            Snapshot();
            _state = KnotState.Connecting; _progress = 0;
            _front.Opacity = 1;
            Render(final: false);
        }
        var duration = TimeSpan.FromMilliseconds(start > 0.1 ? 620 : 1100);
        RunTimer(elapsed =>
        {
            var x = Math.Clamp(elapsed / duration, 0, 1);
            _progress = start + (1 - start) * (1 - Math.Pow(1 - x, 3));
            if (x < 1) { Render(final: false); return false; }
            _state = KnotState.Connected; _progress = 1;
            Snapshot();
            Render(final: true);
            CrossFade(TimeSpan.FromMilliseconds(420), 0.95);
            Ignite();
            return true;
        });
    }

    /// <summary>Disconnected or failed: the lit knot dims back to the frosted ghost.</summary>
    public void FadeTo(KnotPalette palette, KnotState state)
    {
        StopWeaving();
        _palette = palette;
        if (!PageMotion.AnimationsAllowed) { Apply(palette, state); return; }
        Snapshot();
        _state = state; _progress = state == KnotState.Connecting ? 0.3 : 1;
        Render(final: true);
        CrossFade(TimeSpan.FromMilliseconds(520), 1.03);
    }

    public void StopWeaving()
    {
        var timer = _timer;
        _timer = null;
        timer?.Stop();
    }

    private void RunTimer(Func<TimeSpan, bool> frame)
    {
        StopWeaving();
        var started = DateTimeOffset.UtcNow;
        var timer = DispatcherQueue.CreateTimer();
        timer.Interval = TimeSpan.FromMilliseconds(33);
        timer.Tick += (_, _) => { if (ReferenceEquals(_timer, timer) && frame(DateTimeOffset.UtcNow - started)) StopWeaving(); };
        _timer = timer;
        timer.Start();
    }

    // Keeps what is on screen as the back layer so the next state can fade in over it.
    private void Snapshot()
    {
        _fade?.Stop();
        if (_frontBitmap is null) return;
        var copy = new WriteableBitmap(_frontBitmap.PixelWidth, _frontBitmap.PixelHeight);
        using (var from = _frontBitmap.PixelBuffer.AsStream())
        using (var to = copy.PixelBuffer.AsStream()) from.CopyTo(to);
        copy.Invalidate();
        _back.Source = copy;
        _back.Opacity = 1;
        _frontBitmap = null; // the next render allocates a fresh front bitmap
    }

    private void CrossFade(TimeSpan duration, double fromScale)
    {
        _fade?.Stop();
        var story = new Storyboard();
        void Add(DependencyObject target, string property, double from, double to, TimeSpan length)
        {
            var animation = new DoubleAnimation { From = from, To = to, Duration = length, EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut } };
            Storyboard.SetTarget(animation, target);
            Storyboard.SetTargetProperty(animation, property);
            story.Children.Add(animation);
        }
        Add(_front, "Opacity", 0, 1, duration);
        Add(_back, "Opacity", 1, 0, duration + TimeSpan.FromMilliseconds(120));
        if (Math.Abs(fromScale - 1) > 0.001)
        {
            Add(_frontScale, "ScaleX", fromScale, 1, duration + TimeSpan.FromMilliseconds(220));
            Add(_frontScale, "ScaleY", fromScale, 1, duration + TimeSpan.FromMilliseconds(220));
        }
        story.Completed += (_, _) => { if (ReferenceEquals(_fade, story)) _back.Source = null; };
        _fade = story;
        story.Begin();
    }

    // The sun catches: a warm bloom swells and settles while a thin ring ripples outwards.
    private void Ignite()
    {
        if (_palette == default) return;
        var (r, g, b, _) = Appearance.ParseHex(_palette.Sun);
        var sun = global::Windows.UI.Color.FromArgb(255, r, g, b);
        var clear = sun; clear.A = 0;
        var fill = new RadialGradientBrush();
        fill.GradientStops.Add(new GradientStop { Color = sun, Offset = 0 });
        fill.GradientStops.Add(new GradientStop { Color = clear, Offset = 1 });
        _bloom.Fill = fill;
        _ripple.Stroke = new SolidColorBrush(sun);
        PlaceSun();
        var story = new Storyboard();
        void Key(DependencyObject target, string property, params (double At, double Value)[] frames)
        {
            var animation = new DoubleAnimationUsingKeyFrames();
            foreach (var (at, value) in frames)
                animation.KeyFrames.Add(new EasingDoubleKeyFrame
                {
                    KeyTime = TimeSpan.FromMilliseconds(at), Value = value,
                    EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut },
                });
            Storyboard.SetTarget(animation, target);
            Storyboard.SetTargetProperty(animation, property);
            story.Children.Add(animation);
        }
        Key(_bloom, "Opacity", (0, 0), (180, 0.95), (1100, 0));
        Key(_bloomScale, "ScaleX", (0, 0.3), (380, 1.35), (1100, 1.05));
        Key(_bloomScale, "ScaleY", (0, 0.3), (380, 1.35), (1100, 1.05));
        Key(_ripple, "Opacity", (0, 0), (120, 0.8), (900, 0));
        Key(_rippleScale, "ScaleX", (0, 0.6), (900, 2.8));
        Key(_rippleScale, "ScaleY", (0, 0.6), (900, 2.8));
        story.Begin();
    }

    private void PlaceSun()
    {
        var side = Math.Min(ActualWidth, ActualHeight);
        if (side <= 0) return;
        double left = (ActualWidth - side) / 2, top = (ActualHeight - side) / 2;
        Place(_bloom, side * SunRadius * 2 * 2.6);
        Place(_ripple, side * SunRadius * 2);
        void Place(FrameworkElement element, double diameter)
        {
            element.Width = element.Height = diameter;
            element.HorizontalAlignment = HorizontalAlignment.Left;
            element.VerticalAlignment = VerticalAlignment.Top;
            element.Margin = new Thickness(left + side * SunX - diameter / 2, top + side * SunY - diameter / 2, 0, 0);
        }
    }

    private void Render(bool final)
    {
        if (_palette == default) return;
        var scale = XamlRoot?.RasterizationScale ?? 1.25;
        var pixels = (int)Math.Clamp(Math.Round(LogicalSize * scale * (final ? 1.0 : 0.62)), 160, 1100);
        var bytes = KnotRenderer.Render(pixels, _palette, _state, _progress);
        if (_frontBitmap is null || _frontBitmap.PixelWidth != pixels)
        {
            _frontBitmap = new WriteableBitmap(pixels, pixels);
            _front.Source = _frontBitmap;
        }
        bytes.CopyTo(_frontBitmap.PixelBuffer);
        _frontBitmap.Invalidate();
    }
}
