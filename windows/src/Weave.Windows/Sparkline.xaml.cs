using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;

namespace Weave.Windows;

/// <summary>A tiny delay trend: the measurements of one node during this run, higher = slower. Empty until there are two.</summary>
public sealed partial class Sparkline : UserControl
{
    private const double W = 84, H = 18, Pad = 2;

    public static readonly DependencyProperty SamplesProperty = DependencyProperty.Register(
        nameof(Samples), typeof(object), typeof(Sparkline), new PropertyMetadata(null, (d, _) => ((Sparkline)d).Update()));

    public Sparkline() => InitializeComponent();

    public object? Samples { get => GetValue(SamplesProperty); set => SetValue(SamplesProperty, value); }

    private void Update()
    {
        Line.Points.Clear();
        if (Samples is not IReadOnlyList<int?> samples || samples.Count < 2) return;
        var known = samples.Where(sample => sample.HasValue).Select(sample => sample!.Value).ToArray();
        if (known.Length == 0) return;
        double min = known.Min(), max = known.Max();
        var span = Math.Max(1, max - min);
        for (var i = 0; i < samples.Count; i++)
        {
            // A lost probe is drawn at the top: it is the worst outcome.
            var level = samples[i] is { } ms ? (ms - min) / span : 1.0;
            // Higher on screen = slower.
            Line.Points.Add(new global::Windows.Foundation.Point(i * W / (samples.Count - 1), H - Pad - level * (H - 2 * Pad)));
        }
    }
}
