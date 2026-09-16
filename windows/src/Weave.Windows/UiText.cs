using System.ComponentModel;
using System.Text;
using Weave.Windows.Core;

namespace Weave.Windows;

/// <summary>A single live resource for XAML. Base64 keys keep punctuation out of Binding paths.</summary>
public sealed class UiText : INotifyPropertyChanged
{
    public UiText() => L.Changed += (_, _) => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(null));
    public string this[string key] => L.T(Encoding.UTF8.GetString(Convert.FromBase64String(key)));
    public event PropertyChangedEventHandler? PropertyChanged;
}
