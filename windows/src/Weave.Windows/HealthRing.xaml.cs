using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;

namespace Weave.Windows;

public sealed partial class HealthRing : UserControl
{
    private const double Radius = 18.75, Center = 20;

    public static readonly DependencyProperty FractionProperty = DependencyProperty.Register(
        nameof(Fraction), typeof(double), typeof(HealthRing), new PropertyMetadata(double.NaN, (d, _) => ((HealthRing)d).Update()));
    public static readonly DependencyProperty MonogramProperty = DependencyProperty.Register(
        nameof(Monogram), typeof(string), typeof(HealthRing), new PropertyMetadata("", (d, e) => ((HealthRing)d).Letter.Text = (string)e.NewValue));

    public HealthRing()
    {
        InitializeComponent();
        Loaded += (_, _) => Update();
    }

    /// <summary>Share of nodes that answered, 0..1. NaN while the subscription has not been measured.</summary>
    public double Fraction { get => (double)GetValue(FractionProperty); set => SetValue(FractionProperty, value); }
    public string Monogram { get => (string)GetValue(MonogramProperty); set => SetValue(MonogramProperty, value); }

    private void Update()
    {
        var fraction = Fraction;
        if (double.IsNaN(fraction))
        {
            VisualStateManager.GoToState(this, "Unknown", false);
            Arc.Data = null;
            return;
        }
        fraction = Math.Clamp(fraction, 0, 1);
        VisualStateManager.GoToState(this, fraction >= 0.75 ? "Healthy" : fraction >= 0.4 ? "Fair" : "Poor", false);
        Arc.Opacity = 1;
        Arc.Data = ArcFor(fraction);
    }

    // A full circle cannot be drawn as one arc (start == end), so 100 % stops a hair short.
    private static Geometry? ArcFor(double fraction)
    {
        if (fraction <= 0.001) return null;
        var sweep = Math.Min(fraction, 0.9995) * 2 * Math.PI;
        var end = new global::Windows.Foundation.Point(Center + Radius * Math.Sin(sweep), Center - Radius * Math.Cos(sweep));
        var figure = new PathFigure { StartPoint = new(Center, Center - Radius), IsClosed = false };
        figure.Segments.Add(new ArcSegment { Point = end, Size = new(Radius, Radius), SweepDirection = SweepDirection.Clockwise, IsLargeArc = sweep > Math.PI });
        var geometry = new PathGeometry();
        geometry.Figures.Add(figure);
        return geometry;
    }
}
