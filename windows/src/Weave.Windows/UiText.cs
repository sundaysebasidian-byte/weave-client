using System.ComponentModel;
using Weave.Windows.Core;

namespace Weave.Windows;

/// <summary>A single live resource for XAML; updates without replacing the window or proxy session.</summary>
public sealed partial class UiText : INotifyPropertyChanged
{
    public UiText() => L.Changed += (_, _) => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(null));
    public event PropertyChangedEventHandler? PropertyChanged;
}
