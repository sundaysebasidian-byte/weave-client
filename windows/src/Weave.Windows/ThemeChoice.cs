using System.ComponentModel;
using Weave.Windows.Core;

namespace Weave.Windows;

/// <summary>One cell of the appearance gallery. Its preview follows the active light/dark mode.</summary>
public sealed class ThemeChoice : INotifyPropertyChanged
{
    private readonly AppearanceTheme _theme;
    private bool _dark;

    public ThemeChoice(int index, AppearanceTheme theme, bool dark) { Index = index; _theme = theme; _dark = dark; }

    public event PropertyChangedEventHandler? PropertyChanged;
    public int Index { get; }
    public string Name => L.T(_theme.Name);
    public ThemeColors Colors => _theme.For(_dark);
    public bool IsDark => _dark;

    public void SetDark(bool dark)
    {
        if (_dark == dark) return;
        _dark = dark;
        PropertyChanged?.Invoke(this, new(null));
    }

    public void RefreshLanguage() => PropertyChanged?.Invoke(this, new(nameof(Name)));
}
