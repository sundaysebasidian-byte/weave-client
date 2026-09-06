using System.Diagnostics;
using System.Net;
using System.Net.Sockets;

namespace Weave.Windows.Core;

public sealed class MihomoProcess : IAsyncDisposable
{
    private readonly string _executablePath;
    private readonly object _gate = new();
    private Process? _process;
    private CancellationTokenSource? _logCancellation;

    public MihomoProcess(string executablePath)
    {
        _executablePath = executablePath;
    }

    public bool IsRunning
    {
        get
        {
            lock (_gate)
            {
                return _process is { HasExited: false };
            }
        }
    }

    public string LastDiagnostics { get; private set; } = string.Empty;

    public event EventHandler? Exited;

    public async Task StartAsync(RuntimeBundle bundle, CancellationToken cancellationToken = default)
    {
        lock (_gate)
        {
            if (_process is { HasExited: false })
            {
                throw new InvalidOperationException("Mihomo 已经在运行");
            }
        }

        if (!File.Exists(_executablePath))
        {
            throw new FileNotFoundException("未找到 Mihomo Windows 核心", _executablePath);
        }

        var startInfo = new ProcessStartInfo
        {
            FileName = _executablePath,
            Arguments = $"-d {Quote(bundle.Directory)} -f {Quote(bundle.ConfigPath)} --no-color",
            WorkingDirectory = bundle.Directory,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        };
        var process = new Process { StartInfo = startInfo, EnableRaisingEvents = true };
        process.Exited += (_, _) =>
        {
            // Retain the exited handle until StopAsync disposes it and its log readers.
            Exited?.Invoke(this, EventArgs.Empty);
        };
        if (!process.Start())
        {
            process.Dispose();
            throw new InvalidOperationException("无法启动 Mihomo");
        }

        CancellationTokenSource logCancellation;
        lock (_gate)
        {
            _process = process;
            _logCancellation = new CancellationTokenSource();
            logCancellation = _logCancellation;
        }
        _ = DrainAsync(process.StandardOutput, logCancellation.Token);
        _ = DrainAsync(process.StandardError, logCancellation.Token);

        try
        {
            await WaitForPortAsync(bundle.MixedPort, process, cancellationToken).ConfigureAwait(false);
        }
        catch
        {
            await StopAsync().ConfigureAwait(false);
            throw;
        }
    }

    public async Task<(bool IsValid, string Diagnostics)> ValidateConfigAsync(
        RuntimeBundle bundle,
        CancellationToken cancellationToken = default)
    {
        var startInfo = new ProcessStartInfo
        {
            FileName = _executablePath,
            Arguments = $"-t -d {Quote(bundle.Directory)} -f {Quote(bundle.ConfigPath)} --no-color",
            WorkingDirectory = bundle.Directory,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        };
        using var process = new Process { StartInfo = startInfo };
        if (!process.Start())
        {
            return (false, "无法启动 Mihomo 配置检查");
        }

        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(10));
        // Drain both pipes before waiting; a full diagnostic pipe otherwise deadlocks validation.
        var stdout = process.StandardOutput.ReadToEndAsync(timeout.Token);
        var stderr = process.StandardError.ReadToEndAsync(timeout.Token);
        try
        {
            await process.WaitForExitAsync(timeout.Token).ConfigureAwait(false);
        }
        catch (OperationCanceledException)
        {
            try { process.Kill(entireProcessTree: true); } catch { /* best effort */ }
            try { await Task.WhenAll(stdout, stderr).ConfigureAwait(false); }
            catch (OperationCanceledException) { }
            cancellationToken.ThrowIfCancellationRequested();
            return (false, "Mihomo 配置检查超时");
        }

        var output = (await stdout.ConfigureAwait(false) + await stderr.ConfigureAwait(false)).Trim();
        return (process.ExitCode == 0, output);
    }

    public async Task StopAsync()
    {
        Process? process;
        CancellationTokenSource? cancellation;
        lock (_gate)
        {
            process = _process;
            _process = null;
            cancellation = _logCancellation;
            _logCancellation = null;
        }

        cancellation?.Cancel();
        if (process is null)
        {
            cancellation?.Dispose();
            return;
        }

        try
        {
            if (!process.HasExited)
            {
                process.Kill(entireProcessTree: true);
                await process.WaitForExitAsync().ConfigureAwait(false);
            }
        }
        catch (InvalidOperationException)
        {
            // The process exited between the checks.
        }
        finally
        {
            process.Dispose();
            cancellation?.Dispose();
        }
    }

    public async ValueTask DisposeAsync() => await StopAsync().ConfigureAwait(false);

    private async Task WaitForPortAsync(int port, Process process, CancellationToken cancellationToken)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(12));
        while (true)
        {
            timeout.Token.ThrowIfCancellationRequested();
            if (process.HasExited)
            {
                throw new InvalidOperationException($"Mihomo 启动失败：{LastDiagnostics}");
            }

            using var client = new TcpClient();
            try
            {
                await client.ConnectAsync(IPAddress.Loopback, port, timeout.Token).ConfigureAwait(false);
                return;
            }
            catch (SocketException)
            {
                await Task.Delay(100, timeout.Token).ConfigureAwait(false);
            }
        }
    }

    private async Task DrainAsync(StreamReader reader, CancellationToken cancellationToken)
    {
        try
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                var line = await reader.ReadLineAsync(cancellationToken).ConfigureAwait(false);
                if (line is null)
                {
                    return;
                }

                LastDiagnostics = line.Length > 500 ? line[..500] : line;
            }
        }
        catch (OperationCanceledException)
        {
            // Shutdown is expected to cancel log readers.
        }
        catch (IOException) { }
        catch (ObjectDisposedException) { }
    }

    private static string Quote(string path) => $"\"{path.Replace("\"", "\\\"", StringComparison.Ordinal)}\"";
}
