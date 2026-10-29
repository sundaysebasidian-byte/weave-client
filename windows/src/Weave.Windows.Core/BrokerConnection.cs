namespace Weave.Windows.Core;

/// <summary>Runs only after Windows pipe peer verification. EOF/cancellation revokes
/// this connection's core lease, including a start operation still in progress.</summary>
public static class BrokerConnection
{
    public static async Task RunAsync(Stream stream, BrokerSession session, CancellationToken stopping)
    {
        using var lifetime = CancellationTokenSource.CreateLinkedTokenSource(stopping);
        using var writes = new SemaphoreSlim(1, 1);
        var pending = new Dictionary<long, Task>();
        long? stopId = null;
        void CoreExited(object? sender, EventArgs args) => lifetime.Cancel();
        session.Exited += CoreExited;
        try
        {
            while (!lifetime.IsCancellationRequested)
            {
                var request = await BrokerWire.ReadAsync<BrokerRequest>(stream, lifetime.Token).ConfigureAwait(false);
                if (request is null) break;
                foreach (var id in pending.Where(item => item.Value.IsCompleted).Select(item => item.Key).ToArray())
                {
                    await pending[id].ConfigureAwait(false);
                    pending.Remove(id);
                }
                if (request.Id <= 0 || pending.ContainsKey(request.Id) || pending.Count >= 4)
                    throw new InvalidDataException("Too many or duplicate pending service requests.");
                if (request is BrokerStop) { stopId = request.Id; break; }
                pending.Add(request.Id, HandleAsync(request));
            }
        }
        finally
        {
            session.Exited -= CoreExited;
            lifetime.Cancel();
            try { await Task.WhenAll(pending.Values).ConfigureAwait(false); }
            finally { await session.DisposeAsync().ConfigureAwait(false); }
        }
        // Acknowledge only after the core is stopped, including an in-flight validation.
        if (stopId is { } acknowledgedId)
        {
            using var acknowledgement = CancellationTokenSource.CreateLinkedTokenSource(stopping);
            acknowledgement.CancelAfter(TimeSpan.FromSeconds(5));
            await BrokerWire.WriteAsync(stream, new BrokerReply(acknowledgedId, Ready: false), acknowledgement.Token).ConfigureAwait(false);
        }

        async Task HandleAsync(BrokerRequest request)
        {
            try
            {
                BrokerReply reply;
                try { reply = await session.HandleAsync(request, lifetime.Token).ConfigureAwait(false); }
                catch (InvalidDataException) { reply = new(request.Id, Error: "invalid-profile"); }
                catch (OperationCanceledException) when (!lifetime.IsCancellationRequested) { reply = new(request.Id, Error: "timeout"); }
                catch (Exception error) when (error is HttpRequestException or System.Text.Json.JsonException)
                { reply = new(request.Id, Error: "query-failed"); }
                catch (Exception error) when (error is IOException or InvalidOperationException or System.ComponentModel.Win32Exception or UnauthorizedAccessException)
                { reply = new(request.Id, Error: "core-unavailable"); }
                await writes.WaitAsync(lifetime.Token).ConfigureAwait(false);
                try { await BrokerWire.WriteAsync(stream, reply, lifetime.Token).ConfigureAwait(false); }
                finally { writes.Release(); }
            }
            catch (OperationCanceledException) when (lifetime.IsCancellationRequested) { }
            catch { lifetime.Cancel(); throw; }
        }
    }
}
