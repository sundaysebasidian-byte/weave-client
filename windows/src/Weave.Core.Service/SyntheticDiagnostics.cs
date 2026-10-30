#if WEAVE_SYNTHETIC_DIAGNOSTICS
using System.Text;
using System.Text.RegularExpressions;
using Weave.Windows.Core;

namespace Weave.Windows.Service;

// Compiled only into the disposable CI service, never into the packaged service.
internal sealed class SyntheticDiagnostics : IDisposable
{
    private readonly StreamWriter _writer;
    private readonly object _gate = new();
    private int _characters;
    private bool _disposed;

    public SyntheticDiagnostics(string installation)
    {
        if (!Regex.IsMatch(Path.GetFileName(installation), "^Weave-CI-[0-9a-f]{12}$"))
            throw new InvalidOperationException("Synthetic diagnostics require an isolated test installation.");
        _writer = new(new FileStream(Path.Combine(installation, "synthetic-core.log"),
            FileMode.CreateNew, FileAccess.Write, FileShare.Read), new UTF8Encoding(false)) { AutoFlush = true };
    }

    public void Configure(RuntimeBundle bundle)
    {
        var config = File.ReadAllText(bundle.ConfigPath);
        if (!config.Contains("log-level: warning", StringComparison.Ordinal))
            throw new InvalidDataException("Unexpected synthetic logging configuration.");
        File.WriteAllText(bundle.ConfigPath, config.Replace("log-level: warning", "log-level: debug", StringComparison.Ordinal));
        Record("SYNTHETIC_SESSION_START");
    }

    public void Record(string line)
    {
        lock (_gate)
        {
            if (_disposed || _characters >= 262_144) return;
            var bounded = line[..Math.Min(line.Length, Math.Min(4096, 262_144 - _characters))];
            _characters += bounded.Length;
            _writer.WriteLine($"{DateTime.UtcNow:O} {bounded}");
        }
    }

    public void Dispose()
    {
        lock (_gate)
        {
            if (_disposed) return;
            _disposed = true;
            _writer.Dispose();
        }
    }
}
#endif
