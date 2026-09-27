using System.Diagnostics;
using System.Net.Http.Headers;
using System.Text.Json;

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
        process.Exited += (_, _) => Exited?.Invoke(this, EventArgs.Empty);
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
            await WaitForReadyAsync(bundle, process, cancellationToken).ConfigureAwait(false);
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
        var stdout = process.StandardOutput.ReadToEndAsync();
        var stderr = process.StandardError.ReadToEndAsync();
        try
        {
            await process.WaitForExitAsync(timeout.Token).ConfigureAwait(false);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            try { process.Kill(entireProcessTree: true); } catch { /* best effort */ }
            await process.WaitForExitAsync().ConfigureAwait(false);
            throw;
        }
        catch (OperationCanceledException)
        {
            try { process.Kill(entireProcessTree: true); } catch { /* best effort */ }
            await process.WaitForExitAsync().ConfigureAwait(false);
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

    private async Task WaitForReadyAsync(RuntimeBundle bundle, Process process, CancellationToken cancellationToken)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(12));
        using var client = new HttpClient(new HttpClientHandler { UseProxy = false })
        {
            Timeout = TimeSpan.FromSeconds(1),
        };
        try
        {
            while (true)
            {
                timeout.Token.ThrowIfCancellationRequested();
                if (process.HasExited)
                {
                    throw new InvalidOperationException($"Mihomo 启动失败：{LastDiagnostics}");
                }

                try
                {
                    using var request = new HttpRequestMessage(
                        HttpMethod.Get,
                        $"http://127.0.0.1:{bundle.ControlPort}/version");
                    request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", bundle.ControlSecret);
                    using var response = await client.SendAsync(request, timeout.Token).ConfigureAwait(false);
                    if (response.IsSuccessStatusCode)
                    {
                        using var version = JsonDocument.Parse(
                            await response.Content.ReadAsStringAsync(timeout.Token).ConfigureAwait(false));
                        if (version.RootElement.TryGetProperty("version", out _))
                        {
                            return;
                        }
                    }
                }
                catch (HttpRequestException)
                {
                    // The control API has not started yet.
                }
                catch (TaskCanceledException) when (!timeout.IsCancellationRequested)
                {
                    // A single probe timed out; the overall startup deadline still applies.
                }
                catch (JsonException)
                {
                    // Another service answered before Mihomo bound its control port.
                }

                await Task.Delay(100, timeout.Token).ConfigureAwait(false);
            }
        }
        catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
        {
            throw new TimeoutException($"Mihomo 控制接口启动超时：{LastDiagnostics}");
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
    }

    private static string Quote(string path) => $"\"{path.Replace("\"", "\\\"", StringComparison.Ordinal)}\"";
}
