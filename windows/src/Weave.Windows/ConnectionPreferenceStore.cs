using System.Text.Json;
using Weave.Windows.Core;

namespace Weave.Windows;

internal sealed record ConnectionPreference(string? SubscriptionId, string? NodeId);

/// <summary>Remembers the last chosen exit so the app reopens where the user left it.</summary>
internal sealed class ConnectionPreferenceStore
{
    private readonly string _path;
    private readonly ISecretProtector _protector;

    public ConnectionPreferenceStore(string path, ISecretProtector protector)
    {
        _path = path;
        _protector = protector;
    }

    public ConnectionPreference Load()
    {
        try
        {
            if (!File.Exists(_path)) return new ConnectionPreference(null, null);
            var json = _protector.Unprotect(File.ReadAllBytes(_path));
            return JsonSerializer.Deserialize<ConnectionPreference>(json) ?? new ConnectionPreference(null, null);
        }
        catch (Exception)
        {
            // A lost preference only means the user picks the exit again; never block startup on it.
            return new ConnectionPreference(null, null);
        }
    }

    public void Save(ConnectionPreference preference)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(_path)!);
        var json = JsonSerializer.SerializeToUtf8Bytes(preference);
        var pending = $"{_path}.{Guid.NewGuid():N}.pending";
        File.WriteAllBytes(pending, _protector.Protect(json));
        File.Move(pending, _path, overwrite: true);
    }
}
