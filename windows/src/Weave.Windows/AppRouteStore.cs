using System.Text.Json;
using Weave.Windows.Core;

namespace Weave.Windows;

internal sealed class AppRouteStore
{
    private static readonly JsonSerializerOptions Options = new(JsonSerializerDefaults.Web);
    private readonly string _path;
    private readonly ISecretProtector _protector;

    public AppRouteStore(string path, ISecretProtector protector)
    {
        _path = path;
        _protector = protector;
    }

    public IReadOnlyList<WindowsAppRoute> Load()
    {
        if (!File.Exists(_path))
        {
            return Array.Empty<WindowsAppRoute>();
        }

        var json = _protector.Unprotect(File.ReadAllBytes(_path));
        return JsonSerializer.Deserialize<List<WindowsAppRoute>>(json, Options)
            ?? new List<WindowsAppRoute>();
    }

    public void Save(IEnumerable<WindowsAppRoute> routes)
    {
        var parent = Path.GetDirectoryName(_path);
        if (!string.IsNullOrWhiteSpace(parent))
        {
            Directory.CreateDirectory(parent);
        }

        var json = JsonSerializer.SerializeToUtf8Bytes(routes.ToList(), Options);
        var encrypted = _protector.Protect(json);
        var pending = $"{_path}.{Guid.NewGuid():N}.pending";
        File.WriteAllBytes(pending, encrypted);
        File.Move(pending, _path, overwrite: true);
    }
}
