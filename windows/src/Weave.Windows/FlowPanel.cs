using Microsoft.UI.Xaml.Controls;
using global::Windows.Foundation;

namespace Weave.Windows;

/// <summary>Small content-sized toolbars. Unlike a uniform grid, long translations keep their width.</summary>
public sealed class FlowPanel : Panel
{
    private const double Gap = 8;
    protected override Size MeasureOverride(Size availableSize)
    {
        double x = 0, y = 0, lineHeight = 0, width = 0;
        foreach (var child in Children)
        {
            child.Measure(new Size(availableSize.Width, double.PositiveInfinity));
            var size = child.DesiredSize;
            if (x > 0 && x + size.Width > availableSize.Width)
            { width = Math.Max(width, x - Gap); y += lineHeight + Gap; x = 0; lineHeight = 0; }
            x += size.Width + Gap;
            lineHeight = Math.Max(lineHeight, size.Height);
        }
        return new Size(Math.Max(width, Math.Max(0, x - Gap)), y + lineHeight);
    }
    protected override Size ArrangeOverride(Size finalSize)
    {
        double x = 0, y = 0, lineHeight = 0;
        foreach (var child in Children)
        {
            var size = child.DesiredSize;
            var width = Math.Min(size.Width, finalSize.Width);
            if (x > 0 && x + width > finalSize.Width)
            { y += lineHeight + Gap; x = 0; lineHeight = 0; }
            child.Arrange(new Rect(x, y, width, size.Height));
            x += width + Gap;
            lineHeight = Math.Max(lineHeight, size.Height);
        }
        return finalSize;
    }
}
