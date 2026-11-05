using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Weave.Windows.Core;
using WinColor = global::Windows.UI.Color;
using WinPoint = global::Windows.Foundation.Point;

namespace Weave.Windows;

/// <summary>A miniature of the real window in a theme's own colours, used by the appearance gallery.</summary>
public sealed class ThemePreview : UserControl
{
    private const double W = 256, H = 135;
    public static readonly DependencyProperty ColorsProperty = DependencyProperty.Register(
        nameof(Colors), typeof(ThemeColors), typeof(ThemePreview), new PropertyMetadata(null, (d, _) => ((ThemePreview)d).Rebuild()));
    public static readonly DependencyProperty IsDarkProperty = DependencyProperty.Register(
        nameof(IsDark), typeof(bool), typeof(ThemePreview), new PropertyMetadata(false, (d, _) => ((ThemePreview)d).Rebuild()));

    private readonly Canvas _canvas = new() { Width = W, Height = H };

    public ThemePreview()
    {
        Content = new Viewbox { Stretch = Stretch.Uniform, Child = _canvas };
        IsTabStop = false;
        IsHitTestVisible = false;
    }

    public ThemeColors? Colors { get => (ThemeColors?)GetValue(ColorsProperty); set => SetValue(ColorsProperty, value); }
    public bool IsDark { get => (bool)GetValue(IsDarkProperty); set => SetValue(IsDarkProperty, value); }

    private static WinColor Hex(string hex, double opacity = 1)
    {
        var (r, g, b, a) = Appearance.ParseHex(hex);
        return WinColor.FromArgb((byte)Math.Round(a * opacity), r, g, b);
    }
    private static SolidColorBrush Solid(string hex, double opacity = 1) => new(Hex(hex, opacity));

    private void Add(UIElement element, double x, double y) { Canvas.SetLeft(element, x); Canvas.SetTop(element, y); _canvas.Children.Add(element); }
    private void Box(double x, double y, double w, double h, double radius, Brush fill) =>
        Add(new Rectangle { Width = w, Height = h, RadiusX = radius, RadiusY = radius, Fill = fill }, x, y);

    private static RadialGradientBrush Wash(string rgba, WinPoint center, double rx, double ry)
    {
        var brush = new RadialGradientBrush { Center = center, GradientOrigin = center, RadiusX = rx, RadiusY = ry };
        var clear = Hex(rgba); clear.A = 0;
        brush.GradientStops.Add(new GradientStop { Color = Hex(rgba), Offset = 0 });
        brush.GradientStops.Add(new GradientStop { Color = clear, Offset = 0.76 });
        return brush;
    }

    private void Rebuild()
    {
        _canvas.Children.Clear();
        if (Colors is not { } c) return;
        var dark = IsDark;
        Box(0, 0, W, H, 16, Solid(c.Base));
        Box(0, 0, W, H, 16, Wash(c.Wash[0], new(0.9, 0), 0.7, 0.8));
        Box(0, 0, W, H, 16, Wash(c.Wash[1], new(0, 1), 0.62, 0.72));
        Box(0, 0, W, H, 16, Wash(c.Wash[2], new(1, 1), 0.58, 0.68));
        var glass = new SolidColorBrush(dark ? WinColor.FromArgb(18, 255, 255, 255) : WinColor.FromArgb(158, 255, 255, 255));
        Box(8, 8, 22, 119, 8, glass);
        foreach (var (y, sun) in new[] { (26.0, true), (42.0, false), (58.0, false), (74.0, false) })
            Add(new Ellipse { Width = sun ? 6.4 : 5.2, Height = sun ? 6.4 : 5.2, Fill = sun ? Solid(c.Sun) : Solid(c.Ink, 0.35) }, 19 - (sun ? 3.2 : 2.6), y - (sun ? 3.2 : 2.6));
        Box(38, 8, 130, 74, 12, glass);
        Box(50, 19, 26, 3.5, 1.7, Solid(c.Ink, 0.35)); Box(50, 29, 44, 14, 4, Solid(c.Ink, 0.78));
        Box(50, 50, 20, 7, 3.5, Solid(c.Ink, 0.14)); Box(73, 50, 20, 7, 3.5, Solid(c.Ink, 0.14));
        Box(50, 64, 38, 11, 5.5, Solid(c.Accent));
        var ribbon = new LinearGradientBrush { StartPoint = new(0, 0), EndPoint = new(1, 0) };
        ribbon.GradientStops.Add(new GradientStop { Color = Hex(c.Knot[0]), Offset = 0 });
        ribbon.GradientStops.Add(new GradientStop { Color = Hex(c.Knot[1]), Offset = 0.55 });
        ribbon.GradientStops.Add(new GradientStop { Color = Hex(c.Knot[2]), Offset = 1 });
        foreach (var (x, y, angle) in new[] { (100.0, 34.5, -38.0), (117.0, 36.5, 34.0) })
            Add(new Ellipse { Width = 44, Height = 23, Stroke = ribbon, StrokeThickness = 8, RenderTransform = new RotateTransform { Angle = angle, CenterX = 22, CenterY = 11.5 } }, x, y);
        Add(new Ellipse { Width = 10, Height = 10, Fill = Solid(c.Sun) }, 151, 16);
        Box(176, 8, 72, 74, 12, glass);
        Box(186, 19, 26, 12, 3, Solid(c.Ink, 0.7));
        var curve = new Microsoft.UI.Xaml.Shapes.Path { Stroke = ribbon, StrokeThickness = 2.4, StrokeStartLineCap = PenLineCap.Round, StrokeEndLineCap = PenLineCap.Round };
        var figure = new PathFigure { StartPoint = new(186, 66) };
        figure.Segments.Add(new BezierSegment { Point1 = new(196, 50), Point2 = new(204, 62), Point3 = new(214, 54) });
        figure.Segments.Add(new BezierSegment { Point1 = new(224, 46), Point2 = new(232, 50), Point3 = new(240, 58) });
        var geometry = new PathGeometry(); geometry.Figures.Add(figure); curve.Data = geometry;
        Add(curve, 0, 0);
        Box(38, 90, 130, 37, 12, glass);
        foreach (var (x, y) in new[] { (48.0, 98.0), (106.0, 98.0), (48.0, 111.0), (106.0, 111.0) }) Box(x, y, 54, 9, 4.5, Solid(c.Ink, 0.12));
        Box(176, 90, 72, 37, 12, glass);
        Box(186, 100, 22, 9, 4.5, Solid(c.Ink, 0.2)); Box(186, 114, 22, 7, 3.5, Solid(c.Ink, 0.12));
        Add(new Ellipse { Width = 10, Height = 10, Fill = Solid(c.Ink, 0.75) }, 229, 99); Add(new Ellipse { Width = 10, Height = 10, Fill = Solid(c.Ink, 0.2) }, 229, 113);
    }
}
