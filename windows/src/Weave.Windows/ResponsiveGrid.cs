using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using global::Windows.Foundation;

namespace Weave.Windows;

/// <summary>
/// Lays children side by side while each weighted child keeps at least <see cref="MinColumnWidth"/>, otherwise stacks them.
/// The decision is made from the panel's own width during measure, so it needs no events or visual-state changes and
/// stays correct for any window size, DPI and translation length. Weight 0 sizes a child to its content.
/// </summary>
public sealed class ResponsiveGrid : Panel
{
    public static readonly DependencyProperty MinColumnWidthProperty = DependencyProperty.Register(
        nameof(MinColumnWidth), typeof(double), typeof(ResponsiveGrid), new PropertyMetadata(240d, OnLayoutChanged));
    public static readonly DependencyProperty ColumnSpacingProperty = DependencyProperty.Register(
        nameof(ColumnSpacing), typeof(double), typeof(ResponsiveGrid), new PropertyMetadata(16d, OnLayoutChanged));
    public static readonly DependencyProperty RowSpacingProperty = DependencyProperty.Register(
        nameof(RowSpacing), typeof(double), typeof(ResponsiveGrid), new PropertyMetadata(16d, OnLayoutChanged));
    public static readonly DependencyProperty WeightProperty = DependencyProperty.RegisterAttached(
        "Weight", typeof(double), typeof(ResponsiveGrid), new PropertyMetadata(1d, OnChildWeightChanged));

    public double MinColumnWidth { get => (double)GetValue(MinColumnWidthProperty); set => SetValue(MinColumnWidthProperty, value); }
    public double ColumnSpacing { get => (double)GetValue(ColumnSpacingProperty); set => SetValue(ColumnSpacingProperty, value); }
    public double RowSpacing { get => (double)GetValue(RowSpacingProperty); set => SetValue(RowSpacingProperty, value); }
    public static double GetWeight(DependencyObject element) => (double)element.GetValue(WeightProperty);
    public static void SetWeight(DependencyObject element, double value) => element.SetValue(WeightProperty, value);

    private static void OnLayoutChanged(DependencyObject d, DependencyPropertyChangedEventArgs e) => (d as ResponsiveGrid)?.InvalidateMeasure();
    private static void OnChildWeightChanged(DependencyObject d, DependencyPropertyChangedEventArgs e) =>
        ((d as FrameworkElement)?.Parent as ResponsiveGrid)?.InvalidateMeasure();

    // Content widths of weight-0 children, captured in measure so arrange never re-measures.
    private double[] _contentWidths = Array.Empty<double>();

    private List<UIElement> VisibleChildren() => Children.Where(child => child.Visibility == Visibility.Visible).ToList();

    protected override Size MeasureOverride(Size availableSize)
    {
        var children = VisibleChildren();
        if (children.Count == 0) return new Size(0, 0);
        _contentWidths = new double[children.Count];
        for (var i = 0; i < children.Count; i++)
        {
            if (GetWeight(children[i]) > 0) continue;
            children[i].Measure(new Size(double.PositiveInfinity, double.PositiveInfinity));
            _contentWidths[i] = Math.Ceiling(children[i].DesiredSize.Width);
        }
        var widths = SideBySideWidths(children, availableSize.Width);
        double width = 0, height = 0;
        if (widths is not null)
        {
            for (var i = 0; i < children.Count; i++)
            {
                children[i].Measure(new Size(widths[i], double.PositiveInfinity));
                width += children[i].DesiredSize.Width;
                height = Math.Max(height, children[i].DesiredSize.Height);
            }
            width += ColumnSpacing * (children.Count - 1);
        }
        else
        {
            foreach (var child in children)
            {
                child.Measure(new Size(availableSize.Width, double.PositiveInfinity));
                width = Math.Max(width, child.DesiredSize.Width);
                height += child.DesiredSize.Height;
            }
            height += RowSpacing * (children.Count - 1);
        }
        return new Size(double.IsInfinity(availableSize.Width) ? width : availableSize.Width, height);
    }

    protected override Size ArrangeOverride(Size finalSize)
    {
        var children = VisibleChildren();
        var widths = children.Count == _contentWidths.Length ? SideBySideWidths(children, finalSize.Width) : null;
        if (widths is not null)
        {
            double x = 0, rowHeight = children.Max(child => child.DesiredSize.Height);
            for (var i = 0; i < children.Count; i++)
            {
                children[i].Arrange(new Rect(x, 0, widths[i], rowHeight));
                x += widths[i] + ColumnSpacing;
            }
        }
        else
        {
            double y = 0;
            foreach (var child in children)
            {
                child.Arrange(new Rect(0, y, finalSize.Width, child.DesiredSize.Height));
                y += child.DesiredSize.Height + RowSpacing;
            }
        }
        return finalSize;
    }

    /// <summary>Column widths when the children fit in one row; null when they must stack.</summary>
    private double[]? SideBySideWidths(IReadOnlyList<UIElement> children, double available)
    {
        if (children.Count < 2) return null;
        var widths = new double[children.Count];
        double fixedWidth = 0, totalWeight = 0, smallestWeight = double.MaxValue;
        for (var i = 0; i < children.Count; i++)
        {
            var weight = Math.Max(0, GetWeight(children[i]));
            if (weight == 0)
            {
                widths[i] = _contentWidths[i];
                fixedWidth += widths[i];
            }
            else { totalWeight += weight; smallestWeight = Math.Min(smallestWeight, weight); }
        }
        var gaps = ColumnSpacing * (children.Count - 1);
        if (totalWeight == 0) return double.IsInfinity(available) || fixedWidth + gaps <= available ? widths : null;
        if (double.IsInfinity(available))
        {
            for (var i = 0; i < children.Count; i++)
                if (GetWeight(children[i]) > 0) widths[i] = MinColumnWidth * GetWeight(children[i]) / smallestWeight;
            return widths;
        }
        var flexible = available - fixedWidth - gaps;
        if (flexible / totalWeight * smallestWeight < MinColumnWidth) return null;
        for (var i = 0; i < children.Count; i++)
            if (GetWeight(children[i]) > 0) widths[i] = Math.Floor(flexible * GetWeight(children[i]) / totalWeight);
        return widths;
    }
}
