namespace Weave.Windows.Core;

/// <summary>One authenticated pipe owns one core session. Paths are supplied only by
/// the installed service host; no operation accepts a controller URL or filesystem path.</summary>
public sealed class BrokerSession(string runtimeRoot, string coreExecutable, string geoDataDirectory) : IAsyncDisposable
{
    private readonly SemaphoreSlim _lifecycle = new(1, 1);
    private RuntimeSession? _runtime;
    private MihomoProcess? _core;
    private MihomoController? _controller;
    private HashSet<string> _nodes = new(StringComparer.Ordinal);
    private bool _started;
    private bool _closed;
    internal Action<RuntimeBundle>? TestBundleObserver { get; init; }
    internal Action<string>? TestLogObserver { get; init; }
    internal Func<System.Diagnostics.Process, Task>? TestTerminateAsync { get; init; }
    public bool CleanupPending { get; private set; }
    public bool IsReady => _core?.IsReady == true;
    public event EventHandler? Exited;

    public async Task<BrokerReply> HandleAsync(BrokerRequest request, CancellationToken token)
    {
        if (_closed) throw new ObjectDisposedException(nameof(BrokerSession));
        if (request is null || request.Id <= 0) throw new InvalidDataException("Invalid service request identifier.");
        switch (request)
        {
            case BrokerStart start:
                return new(request.Id, Runtime: await StartAsync(start.Profile, token).ConfigureAwait(false), Ready: true);
            case BrokerStop:
                await DisposeAsync().ConfigureAwait(false);
                return new(request.Id, Ready: false);
            case BrokerStatus:
                return new(request.Id, Ready: IsReady);
            case BrokerResolve resolve:
                if (resolve.Host is not { Length: > 0 and <= 253 } || resolve.Host.Any(char.IsControl) ||
                    Uri.CheckHostName(resolve.Host) != UriHostNameType.Dns) throw new InvalidDataException("Invalid service DNS name.");
                var addresses = await Controller().ResolveHostAsync(resolve.Host, token).ConfigureAwait(false);
                return new(request.Id, Addresses: addresses.Select(address => address.ToString()).ToArray());
            case BrokerProbe probe:
                if (probe.NodeName is null || !_nodes.Contains(probe.NodeName)) throw new InvalidDataException("Node is not part of this session.");
                return new(request.Id, Delay: await Controller().ProbeNodeAsync(probe.NodeName, token).ConfigureAwait(false));
            case BrokerTraffic:
                var traffic = await Controller().ReadTrafficAsync(token).ConfigureAwait(false);
                return new(request.Id, Down: traffic.Down, Up: traffic.Up);
            default:
                throw new InvalidDataException("Unsupported service operation.");
        }
    }

    private MihomoController Controller() => IsReady && _controller is { } controller
        ? controller : throw new IOException("The owned core session is unavailable.");

    private async Task<BrokerRuntimeInfo> StartAsync(BrokerProfile profile, CancellationToken token)
    {
        await _lifecycle.WaitAsync(token).ConfigureAwait(false);
        try
        {
            if (_closed || _started) throw new InvalidOperationException("This connection already attempted to start a core.");
            if (profile is null) throw new InvalidDataException("Missing service profile.");
            _started = true;
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token);
            timeout.CancelAfter(TimeSpan.FromSeconds(45));
            try
            {
                _runtime = RuntimeSession.Reserve(runtimeRoot);
                var bundle = await Task.Run(() => profile.Build(_runtime.DirectoryPath, geoDataDirectory), timeout.Token).ConfigureAwait(false);
                timeout.Token.ThrowIfCancellationRequested();
                TestBundleObserver?.Invoke(bundle);
                _core = new(coreExecutable) { TestLogObserver = TestLogObserver, TestTerminateAsync = TestTerminateAsync };
                _core.Exited += CoreExited;
                var validation = await _core.ValidateConfigAsync(bundle, timeout.Token).ConfigureAwait(false);
                if (!validation.IsValid) throw new InvalidDataException("The core rejected the service configuration.");
                await _core.StartAsync(bundle, timeout.Token).ConfigureAwait(false);
                _controller = new(bundle);
                _nodes = profile.Subscriptions.SelectMany(item => item.Nodes.Select(node => MihomoConfigBuilder.NodePrefix(item.Id) + node.RawName))
                    .ToHashSet(StringComparer.Ordinal);
                if (!_core.IsReady) throw new IOException("The core exited during startup.");
                return BrokerRuntimeInfo.FromBundle(bundle, profile.Settings.Ipv6Enabled);
            }
            catch { await StopCoreAsync().ConfigureAwait(false); throw; }
        }
        finally { _lifecycle.Release(); }
    }

    private void CoreExited(object? sender, EventArgs args) => Exited?.Invoke(this, args);

    private async Task StopCoreAsync()
    {
        var core = _core;
        _controller?.Dispose();
        _controller = null;
        _nodes = new(StringComparer.Ordinal);
        try
        {
            if (core is not null)
            {
                core.Exited -= CoreExited;
                await core.DisposeAsync().ConfigureAwait(false);
                _core = null;
            }
        }
        catch
        {
            // Keep both the process handle and exclusive lease until a later stop
            // confirms exit; recovery must not remove an uncertain core's files.
            CleanupPending = true;
            throw;
        }
        var runtime = _runtime;
        if (runtime is not null)
        {
            try
            {
                CleanupPending = !runtime.CleanupAfterCoreExit();
            }
            catch (Exception error) when (error is IOException or UnauthorizedAccessException) { CleanupPending = true; }
            finally { _runtime = null; runtime.Dispose(); }
        }
    }

    public async ValueTask DisposeAsync()
    {
        await _lifecycle.WaitAsync().ConfigureAwait(false);
        try { _closed = true; await StopCoreAsync().ConfigureAwait(false); }
        finally { _lifecycle.Release(); }
    }
}
