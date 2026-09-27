using System.Text.Json;
using Weave.Windows.Core;

namespace Weave.Windows;

internal sealed class NetworkOptionsStore
{
    private readonly string _path;
    private readonly ISecretProtector _protector;

    public NetworkOptionsStore(string path, ISecretProtector protector)
    {
        _path = path;
        _protector = protector;
    }

    public WindowsNetworkOptions Load()
    {
        if (!File.Exists(_path))
        {
            return new WindowsNetworkOptions();
        }

        var json = _protector.Unprotect(File.ReadAllBytes(_path));
        return JsonSerializer.Deserialize<WindowsNetworkOptions>(json)
            ?? new WindowsNetworkOptions();
    }

    public void Save(WindowsNetworkOptions options)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(_path)!);
        var json = JsonSerializer.SerializeToUtf8Bytes(options);
        var pending = $"{_path}.{Guid.NewGuid():N}.pending";
        File.WriteAllBytes(pending, _protector.Protect(json));
        File.Move(pending, _path, overwrite: true);
    }
}
