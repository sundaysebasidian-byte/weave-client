using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace Weave.Windows;

public sealed partial class LatencyBars : UserControl
{
    public static readonly DependencyProperty LevelProperty = DependencyProperty.Register(
        nameof(Level), typeof(int), typeof(LatencyBars), new PropertyMetadata(0, (d, _) => ((LatencyBars)d).Update()));

    public LatencyBars()
    {
        InitializeComponent();
        Loaded += (_, _) => Update();
    }

    /// <summary>0 = nothing measured, 1..4 = weak..excellent (see <c>LatencyScale.Level</c>).</summary>
    public int Level { get => (int)GetValue(LevelProperty); set => SetValue(LevelProperty, value); }

    private void Update() => VisualStateManager.GoToState(this, "L" + Math.Clamp(Level, 0, 4), false);
}
