using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Net.NetworkInformation;

namespace Weave.Windows.Core;

public sealed class MihomoProcess : IAsyncDisposable
{
    private readonly string _executablePath;
    private readonly object _gate = new();
    private Process? _process;
    private CancellationTokenSource? _logCancellation;
    private ChildProcessLifetime? _childLifetime;
    private MihomoController? _controller;
    private volatile bool _ready;
    public bool IsReady => _ready && IsRunning;

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
        _ready = false;
        LastDiagnostics = string.Empty;
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
            Arguments = $"-d {Quote(bundle.Directory)} -f {Quote(bundle.ConfigPath)}",
            WorkingDirectory = bundle.Directory,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        };
        var process = new Process { StartInfo = startInfo, EnableRaisingEvents = true };
        process.Exited += (_, _) =>
        {
            _ready = false;
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
            if (OperatingSystem.IsWindows()) _childLifetime = new ChildProcessLifetime(process);
            _controller = new MihomoController(bundle);
            await WaitForReadyAsync(bundle, process, cancellationToken).ConfigureAwait(false);
            if (process.HasExited) throw new InvalidOperationException("内核已退出");
            _ready = true;
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
            Arguments = $"-t -d {Quote(bundle.Directory)} -f {Quote(bundle.ConfigPath)}",
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
        return (process.ExitCode == 0, process.ExitCode == 0 ? "配置校验通过" : SafeDiagnostic(output));
    }

    public async Task StopAsync()
    {
        _ready = false;
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
                if (_controller is not null)
                {
                    using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(2));
                    try { await _controller.DisableTunAsync(timeout.Token).ConfigureAwait(false); }
                    catch (Exception error) when (error is HttpRequestException or OperationCanceledException) { }
                }
                process.Kill(entireProcessTree: true);
                using var exitTimeout = new CancellationTokenSource(TimeSpan.FromSeconds(5));
                await process.WaitForExitAsync(exitTimeout.Token).ConfigureAwait(false);
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
            _controller?.Dispose();
            _controller = null;
            _childLifetime?.Dispose();
            _childLifetime = null;
        }
    }

    public async ValueTask DisposeAsync() => await StopAsync().ConfigureAwait(false);

    private async Task WaitForReadyAsync(RuntimeBundle bundle, Process process, CancellationToken cancellationToken)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(20));
        while (true)
        {
            timeout.Token.ThrowIfCancellationRequested();
            if (process.HasExited)
            {
                throw new InvalidOperationException($"Mihomo 启动失败：{LastDiagnostics}");
            }

            try
            {
                var tunReady = !bundle.RequiresTun || (OperatingSystem.IsWindows() &&
                    NetworkInterface.GetAllNetworkInterfaces().Any(adapter =>
                        adapter.Name.Equals(bundle.TunDevice, StringComparison.OrdinalIgnoreCase) &&
                        adapter.OperationalStatus == OperationalStatus.Up));
                if (tunReady && await _controller!.IsReadyAsync(bundle, timeout.Token).ConfigureAwait(false)) return;
            }
            catch (Exception error) when (error is HttpRequestException or System.Text.Json.JsonException or OperationCanceledException or NetworkInformationException)
            {
                timeout.Token.ThrowIfCancellationRequested();
            }
            await Task.Delay(250, timeout.Token).ConfigureAwait(false);
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

                LastDiagnostics = SafeDiagnostic(line);
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

    private static string SafeDiagnostic(string message)
    {
        if (message.Contains("address already in use", StringComparison.OrdinalIgnoreCase)) return "本地端口被占用";
        if (message.Contains("permission", StringComparison.OrdinalIgnoreCase) || message.Contains("access is denied", StringComparison.OrdinalIgnoreCase)) return "系统拒绝权限，请检查管理员权限";
        if (message.Contains("tun", StringComparison.OrdinalIgnoreCase)) return "TUN 适配器或路由未就绪";
        return "内核配置或运行错误，请检查订阅与网络设置（原始日志不展示，以避免泄露凭据）";
    }
}
