using System.Text.Json;

namespace Weave.Windows.Core;

public interface ISecretProtector
{
    byte[] Protect(byte[] plaintext);

    byte[] Unprotect(byte[] ciphertext);
}

public sealed class SubscriptionVault
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        WriteIndented = false,
    };

    private readonly string _path;
    private readonly ISecretProtector _protector;
    private readonly object _gate = new();

    public SubscriptionVault(string path, ISecretProtector protector)
    {
        _path = path;
        _protector = protector;
    }

    public IReadOnlyList<SubscriptionRecord> List()
    {
        lock (_gate)
        {
            return ListUnsafe();
        }
    }

    public void Upsert(SubscriptionRecord record)
    {
        lock (_gate)
        {
            var records = ListUnsafe();
            var index = records.FindIndex(item => item.Id.Equals(record.Id, StringComparison.Ordinal));
            if (index >= 0)
            {
                records[index] = record;
            }
            else
            {
                records.Add(record);
            }

            SaveUnsafe(records);
        }
    }

    public void Merge(IReadOnlyList<SubscriptionRecord> incoming)
    {
        lock (_gate)
        {
            var records = ListUnsafe();
            foreach (var record in incoming)
            {
                var index = records.FindIndex(item => item.Id == record.Id);
                if (index < 0) records.Add(record); else records[index] = record;
            }
            SaveUnsafe(records);
        }
    }

    public bool Remove(string id)
    {
        lock (_gate)
        {
            var records = ListUnsafe();
            var removed = records.RemoveAll(item => item.Id.Equals(id, StringComparison.Ordinal)) > 0;
            if (removed)
            {
                SaveUnsafe(records);
            }

            return removed;
        }
    }

    private List<SubscriptionRecord> ListUnsafe()
    {
        if (!File.Exists(_path))
        {
            return new List<SubscriptionRecord>();
        }

        var encrypted = File.ReadAllBytes(_path);
        var json = _protector.Unprotect(encrypted);
        try { return JsonSerializer.Deserialize<List<SubscriptionRecord>>(json, JsonOptions) ?? new List<SubscriptionRecord>(); }
        finally { System.Security.Cryptography.CryptographicOperations.ZeroMemory(json); }
    }

    private void SaveUnsafe(List<SubscriptionRecord> records)
    {
        var parent = Path.GetDirectoryName(_path);
        if (!string.IsNullOrWhiteSpace(parent))
        {
            Directory.CreateDirectory(parent);
        }

        var json = JsonSerializer.SerializeToUtf8Bytes(records, JsonOptions);
        byte[] encrypted;
        try { encrypted = _protector.Protect(json); }
        finally { System.Security.Cryptography.CryptographicOperations.ZeroMemory(json); }
        var pending = $"{_path}.{Guid.NewGuid():N}.pending";
        File.WriteAllBytes(pending, encrypted);
        File.Move(pending, _path, overwrite: true);
    }
}
