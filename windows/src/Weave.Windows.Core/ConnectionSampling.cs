namespace Weave.Windows.Core;

public sealed record ConnectionSample(bool Ready, long Down, long Up);

/// <summary>Await RunAsync for errors/shutdown. Events run on a worker; marshal UI updates explicitly.</summary>
public sealed class ConnectionSampling(
    Func<CancellationToken, Task<bool>> checkReady,
    Func<CancellationToken, Task<(long Down, long Up)>> readTraffic)
{
    private int _running;
    public event EventHandler<ConnectionSample>? Updated;

    public async Task RunAsync(CancellationToken token, TimeSpan? interval = null)
    {
        var period = interval ?? TimeSpan.FromSeconds(2);
        if (period < TimeSpan.FromMilliseconds(100)) throw new ArgumentOutOfRangeException(nameof(interval));
        if (Interlocked.Exchange(ref _running, 1) != 0) throw new InvalidOperationException("Sampling is already running.");
        try
        {
            await Task.Run(async () =>
            {
                while (true)
                {
                    token.ThrowIfCancellationRequested();
                    var ready = await checkReady(token).ConfigureAwait(false);
                    var traffic = ready ? await readTraffic(token).ConfigureAwait(false) : (Down: 0L, Up: 0L);
                    token.ThrowIfCancellationRequested();
                    Updated?.Invoke(this, new(ready, traffic.Down, traffic.Up));
                    await Task.Delay(period, token).ConfigureAwait(false);
                }
            }, token).ConfigureAwait(false);
        }
        finally { Volatile.Write(ref _running, 0); }
    }

    /// <summary>
    /// Probes every node, a few at a time. <paramref name="report"/> fires on a worker thread as each node finishes,
    /// so the UI can fill rows in live instead of waiting for the whole batch.
    /// </summary>
    public static async Task<IReadOnlyDictionary<string, int?>> ProbeNodesAsync(
        IEnumerable<string> nodeNames, Func<string, CancellationToken, Task<int?>> probe,
        CancellationToken token, int maxConcurrency = 3, Action<string, int?>? report = null)
    {
        if (maxConcurrency is < 1 or > 16) throw new ArgumentOutOfRangeException(nameof(maxConcurrency));
        var results = new System.Collections.Concurrent.ConcurrentDictionary<string, int?>(StringComparer.Ordinal);
        await Parallel.ForEachAsync(nodeNames.Distinct(StringComparer.Ordinal),
            new ParallelOptions { MaxDegreeOfParallelism = maxConcurrency, CancellationToken = token },
            async (name, cancellation) =>
            {
                var delay = await probe(name, cancellation).ConfigureAwait(false);
                results[name] = delay;
                report?.Invoke(name, delay);
            }).ConfigureAwait(false);
        return results;
    }
}
