using System.Net;
using System.Runtime.Versioning;

namespace Weave.Windows.Core;

public sealed class BrokerRequestException(string code) : IOException("The core service could not complete the request.")
{
    public string Code { get; } = code;
}

/// <summary>An authenticated connection owns the core lease; its control secret stays in the service.</summary>
public sealed class BrokerClient : IAsyncDisposable
{
    private sealed record Pending(TaskCompletionSource<BrokerReply> Completion, bool UsesSlot);
    private readonly Stream _stream;
    private readonly object _gate = new();
    private readonly Dictionary<long, Pending> _pending = [];
    private readonly CancellationTokenSource _lifetime = new();
    // Reserve the fourth server slot for Stop, even with three queries in flight.
    private readonly SemaphoreSlim _slots = new(3, 3);
    private readonly SemaphoreSlim _writes = new(1, 1);
    private readonly Task _reader;
    private long _sequence;
    private bool _closed;
    private int _stopping, _disposeRequested, _startAttempted;
    private Task? _stopTask;
    private BrokerRuntimeInfo? _runtime;
    public bool IsReady { get { lock (_gate) return !_closed && _runtime is not null; } }
    public event EventHandler? Exited;

    internal BrokerClient(Stream authenticatedStream)
    {
        _stream = authenticatedStream;
        _reader = ReadRepliesAsync();
    }

    [SupportedOSPlatform("windows")]
    public static async Task<BrokerClient> ConnectAsync(string installedServiceExecutable, CancellationToken token) =>
        new(await BrokerWindowsPipe.ConnectServiceAsync(installedServiceExecutable, token).ConfigureAwait(false));

    public async Task<BrokerRuntimeInfo> StartAsync(BrokerProfile profile, CancellationToken token)
    {
        if (Interlocked.Exchange(ref _startAttempted, 1) != 0) throw new InvalidOperationException("This service connection already attempted a start.");
        try
        {
            var reply = await RequestAsync(id => new BrokerStart(id, profile), token, TimeSpan.FromSeconds(60)).ConfigureAwait(false);
            if (reply.Ready != true || reply.Runtime is not { MixedPort: > 0 and <= 65535 } runtime ||
                runtime.ProviderNodeCounts is null || runtime.ProviderNodeCounts.Any(item => item.Value < 0))
                throw InvalidReply();
            lock (_gate)
            {
                if (_closed) throw new IOException("The core service disconnected during startup.");
                _runtime = runtime;
            }
            return runtime;
        }
        catch { await DisposeAsync().ConfigureAwait(false); throw; }
    }

    public async Task<bool> CheckReadyAsync(CancellationToken token)
    {
        var reply = await RequestAsync(id => new BrokerStatus(id), token).ConfigureAwait(false);
        return reply.Ready ?? throw InvalidReply();
    }

    public async Task<IPAddress[]> ResolveHostAsync(string host, CancellationToken token)
    {
        var reply = await RequestAsync(id => new BrokerResolve(id, host), token).ConfigureAwait(false);
        if (reply.Addresses is not { Length: > 0 and <= 128 } values) throw InvalidReply();
        var addresses = new List<IPAddress>();
        foreach (var value in values)
            if (IPAddress.TryParse(value, out var address)) addresses.Add(address);
            else throw InvalidReply();
        return addresses.Distinct().ToArray();
    }

    public async Task<int?> ProbeNodeAsync(string nodeName, CancellationToken token)
    {
        var reply = await RequestAsync(id => new BrokerProbe(id, nodeName), token).ConfigureAwait(false);
        if (reply.Delay is < 1 or > 10_000) throw InvalidReply();
        return reply.Delay;
    }

    public async Task<(long Down, long Up)> ReadTrafficAsync(CancellationToken token)
    {
        var reply = await RequestAsync(id => new BrokerTraffic(id), token).ConfigureAwait(false);
        if (reply.Down is not >= 0 || reply.Up is not >= 0) throw InvalidReply();
        return (reply.Down.Value, reply.Up.Value);
    }

    public Task StopAsync(CancellationToken token)
    {
        token.ThrowIfCancellationRequested();
        Task stop;
        lock (_gate) stop = _stopTask ??= StopCoreAsync();
        return stop.WaitAsync(token);
    }

    private async Task StopCoreAsync()
    {
        Interlocked.Exchange(ref _stopping, 1);
        try
        {
            var reply = await RequestAsync(id => new BrokerStop(id), CancellationToken.None, stopping: true).ConfigureAwait(false);
            if (reply.Ready != false) throw InvalidReply();
        }
        finally { await DisposeAsync().ConfigureAwait(false); }
    }

    private async Task<BrokerReply> RequestAsync(Func<long, BrokerRequest> create, CancellationToken token,
        TimeSpan? timeout = null, bool stopping = false)
    {
        using var linked = CancellationTokenSource.CreateLinkedTokenSource(token, _lifetime.Token);
        if (!stopping) await _slots.WaitAsync(linked.Token).ConfigureAwait(false);
        var slotOwned = !stopping;
        try
        {
            await _writes.WaitAsync(linked.Token).ConfigureAwait(false);
            Task<BrokerReply> completion;
            try
            {
                token.ThrowIfCancellationRequested();
                var id = Interlocked.Increment(ref _sequence);
                if (id <= 0) throw InvalidReply();
                var request = create(id);
                var pending = new Pending(new(TaskCreationOptions.RunContinuationsAsynchronously), !stopping);
                lock (_gate)
                {
                    if (_closed || (!stopping && Volatile.Read(ref _stopping) != 0))
                        throw new IOException("The core service connection is closing.");
                    _pending.Add(id, pending);
                    slotOwned = false; // The reader/abort path now owns this slot.
                }
                completion = pending.Completion.Task;
                // A cancelled caller may stop awaiting a reply that the reader must still drain.
                _ = completion.ContinueWith(task => _ = task.Exception, CancellationToken.None,
                    TaskContinuationOptions.OnlyOnFaulted | TaskContinuationOptions.ExecuteSynchronously, TaskScheduler.Default);
                try { await BrokerWire.WriteAsync(_stream, request, linked.Token).ConfigureAwait(false); }
                catch { Abort(); throw; } // A partial frame cannot be reused.
            }
            finally { _writes.Release(); }
            BrokerReply reply;
            try { reply = await completion.WaitAsync(timeout ?? TimeSpan.FromSeconds(10), token).ConfigureAwait(false); }
            catch (TimeoutException) { Abort(); throw new IOException("The core service response timed out."); }
            if (reply.Error is { } error)
            {
                if (error is not ("invalid-profile" or "timeout" or "query-failed" or "core-unavailable")) throw InvalidReply();
                throw new BrokerRequestException(error);
            }
            return reply;
        }
        finally { if (slotOwned) _slots.Release(); }
    }

    private async Task ReadRepliesAsync()
    {
        try
        {
            while (await BrokerWire.ReadAsync<BrokerReply>(_stream, _lifetime.Token).ConfigureAwait(false) is { } reply)
            {
                Pending? pending;
                lock (_gate)
                {
                    if (!_pending.Remove(reply.Id, out pending)) throw new InvalidDataException("Unsolicited service response.");
                }
                if (pending.UsesSlot) _slots.Release();
                pending.Completion.TrySetResult(reply);
            }
        }
        catch (Exception error) when (error is IOException or InvalidDataException or OperationCanceledException or ObjectDisposedException or System.Text.Json.JsonException) { }
        finally
        {
            Abort();
            if (Volatile.Read(ref _disposeRequested) == 0 && Volatile.Read(ref _stopping) == 0)
                Exited?.Invoke(this, EventArgs.Empty);
        }
    }

    private InvalidDataException InvalidReply()
    {
        Abort();
        return new("Invalid core service response.");
    }

    private void Abort()
    {
        Pending[] pending;
        lock (_gate)
        {
            if (_closed) return;
            _closed = true;
            _runtime = null;
            pending = _pending.Values.ToArray();
            _pending.Clear();
        }
        _lifetime.Cancel();
        _stream.Dispose();
        foreach (var item in pending)
        {
            if (item.UsesSlot) _slots.Release();
            item.Completion.TrySetException(new IOException("The core service disconnected."));
        }
    }

    public async ValueTask DisposeAsync()
    {
        Interlocked.Exchange(ref _disposeRequested, 1);
        Abort();
        await _reader.ConfigureAwait(false);
    }
}
