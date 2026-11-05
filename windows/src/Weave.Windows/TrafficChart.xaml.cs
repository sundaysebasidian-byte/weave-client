using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Weave.Windows.Core;
using WinPoint = global::Windows.Foundation.Point;

namespace Weave.Windows;

public sealed partial class TrafficChart : UserControl
{
    private const double Top = 16, Bottom = 12;
    private TrafficSeries? _series;
    private double _seconds = 2;
    private bool _active;

    public TrafficChart() => InitializeComponent();

    /// <summary>Redraws from the series. <paramref name="seconds"/> is the time one sample stands for (for the hover read-out).</summary>
    public void SetData(TrafficSeries series, double seconds, bool active)
    {
        _series = series; _seconds = seconds; _active = active;
        Redraw();
    }

    private void Host_SizeChanged(object sender, SizeChangedEventArgs e) => Redraw();

    private void Redraw()
    {
        double w = Host.ActualWidth, h = Host.ActualHeight;
        if (w <= 0 || h <= 0) return;
        var baseline = h - Bottom;
        Place(GuideTop, Top, w); Place(GuideMid, Top + (baseline - Top) * 0.5, w); Place(GuideBase, baseline, w);
        Cursor.Height = h;
        var series = _series;
        if (!_active || series is null || series.Count < 2)
        {
            DownLine.Data = Line(new WinPoint(0, baseline), new WinPoint(w, baseline));
            DownLine.Opacity = 0.3;
            Area.Data = null; UpLine.Data = null; Head.Opacity = 0;
            return;
        }
        DownLine.Opacity = 1;
        var down = ChartMath.Layout(series.NormalizedDown(), series.Capacity, w, h, Top, Bottom);
        var up = ChartMath.Layout(series.NormalizedUp(), series.Capacity, w, h, Top, Bottom);
        DownLine.Data = Curve(down, false, h);
        Area.Data = Curve(down, true, h);
        UpLine.Data = Curve(up, false, h);
        Head.Opacity = 1;
        Canvas.SetLeft(Head, down[^1].X - 4.5); Canvas.SetTop(Head, down[^1].Y - 4.5);
    }

    private static void Place(Microsoft.UI.Xaml.Shapes.Line line, double y, double width) { line.X1 = 0; line.X2 = width; line.Y1 = line.Y2 = y; }

    private static Geometry Line(WinPoint a, WinPoint b)
    {
        var figure = new PathFigure { StartPoint = a };
        figure.Segments.Add(new LineSegment { Point = b });
        var geometry = new PathGeometry();
        geometry.Figures.Add(figure);
        return geometry;
    }

    private static Geometry Curve(ChartMath.Pt[] points, bool area, double height)
    {
        var figure = new PathFigure { StartPoint = new(points[0].X, points[0].Y), IsClosed = area };
        foreach (var c in ChartMath.Smooth(points))
            figure.Segments.Add(new BezierSegment { Point1 = new(c.Control1.X, c.Control1.Y), Point2 = new(c.Control2.X, c.Control2.Y), Point3 = new(c.End.X, c.End.Y) });
        if (area)
        {
            figure.Segments.Add(new LineSegment { Point = new(points[^1].X, height) });
            figure.Segments.Add(new LineSegment { Point = new(points[0].X, height) });
        }
        var geometry = new PathGeometry();
        geometry.Figures.Add(figure);
        return geometry;
    }

    private void Host_PointerMoved(object sender, PointerRoutedEventArgs e)
    {
        var series = _series;
        if (!_active || series is null || series.Count < 2 || Host.ActualWidth <= 0) { Host_PointerExited(sender, e); return; }
        var x = e.GetCurrentPoint(Host).Position.X;
        var offset = series.Capacity - series.Count;
        var index = (int)Math.Round(x / Host.ActualWidth * (series.Capacity - 1)) - offset;
        if (index < 0 || index >= series.Count) { Host_PointerExited(sender, e); return; }
        var cursorX = (index + offset) * Host.ActualWidth / (series.Capacity - 1);
        Canvas.SetLeft(Cursor, cursorX);
        Cursor.Opacity = 0.35;
        var ago = (series.Count - 1 - index) * _seconds;
        TipText.Text = $"{(ago < 1 ? "now" : "−" + ago.ToString("F0", System.Globalization.CultureInfo.InvariantCulture) + " s")}   ↓ {RateFormat.Format(series.DownAt(index))}   ↑ {RateFormat.Format(series.UpAt(index))}";
        Tip.Measure(new global::Windows.Foundation.Size(double.PositiveInfinity, double.PositiveInfinity));
        Tip.Margin = new Thickness(Math.Clamp(cursorX + 12, 0, Math.Max(0, Host.ActualWidth - Tip.DesiredSize.Width)), 0, 0, 0);
        Tip.Opacity = 1;
    }

    private void Host_PointerExited(object sender, PointerRoutedEventArgs e) { Cursor.Opacity = 0; Tip.Opacity = 0; }
}
