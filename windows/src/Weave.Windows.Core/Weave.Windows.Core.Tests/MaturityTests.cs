using System.Net;
using System.Text;

namespace Weave.Windows.Core.Tests;

public sealed class MaturityTests
{
    [Fact]
    public void FileReaderEnforcesActualBytesAndRejectsInvalidUtf8()
    {
        using var oversized = new MemoryStream(new byte[SubscriptionImporter.MaxBytes + 1]);
        Assert.Throws<InvalidDataException>(() => SubscriptionImporter.ReadFilePayload(oversized));
        using var invalid = new MemoryStream([0xff]);
        Assert.Throws<DecoderFallbackException>(() => SubscriptionImporter.ReadFilePayload(invalid));
        using var unicode = new MemoryStream([.. Encoding.Unicode.GetPreamble(), .. Encoding.Unicode.GetBytes("proxies: []")]);
        Assert.Equal("proxies: []", SubscriptionImporter.ReadFilePayload(unicode));
    }

    [Fact]
    public async Task CancelledImportDoesNotReadOrParse()
    {
        using var importer = new SubscriptionImporter();
        using var cancellation = new CancellationTokenSource();
        cancellation.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => importer.ImportFileAsync("test", "missing", cancellation.Token));
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => importer.ImportTextAsync("test", "clipboard:", "bad", cancellation.Token));
    }

    [Fact]
    public async Task CancelledProcessOperationsDoNotLaunchChildren()
    {
        await using var process = new MihomoProcess("missing-executable");
        var bundle = new RuntimeBundle { Directory = "", ConfigPath = "", MixedPort = 1 };
        using var cancellation = new CancellationTokenSource();
        cancellation.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => process.StartAsync(bundle, cancellation.Token));
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => process.ValidateConfigAsync(bundle, cancellation.Token));
        await Task.WhenAll(process.StopAsync(), process.StopAsync());
        Assert.False(process.IsRunning);
    }

    [Theory]
    [InlineData("[]")]
    [InlineData("{\"delay\":\"oops\"}")]
    [InlineData("{\"delay\":null}")]
    public async Task MalformedDelayIsNotAResult(string json)
    {
        using var controller = new MihomoController(new RuntimeBundle { Directory = "", ConfigPath = "", MixedPort = 1, ControllerPort = 2, ControllerSecret = "test" }, new ReplyHandler(json));
        Assert.Null(await controller.ProbeNodeAsync("test", CancellationToken.None));
    }

    [Fact]
    public async Task BatchProbesAreBoundedDeduplicatedAndCancellable()
    {
        var active = 0;
        var peak = 0;
        var results = await ConnectionSampling.ProbeNodesAsync(["a", "b", "c", "a"], async (_, token) =>
        {
            var count = Interlocked.Increment(ref active);
            lock (this) peak = Math.Max(peak, count);
            await Task.Delay(30, token);
            Interlocked.Decrement(ref active);
            return 42;
        }, CancellationToken.None, 2);
        Assert.Equal(3, results.Count);
        Assert.InRange(peak, 1, 2);
        using var cancellation = new CancellationTokenSource();
        cancellation.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => ConnectionSampling.ProbeNodesAsync(["a"],
            (_, _) => throw new InvalidOperationException("must not run"), cancellation.Token));
    }

    [Fact]
    public async Task SamplingPushesOnWorkerAndStopsWithoutLateEvents()
    {
        using var cancellation = new CancellationTokenSource();
        var samples = 0;
        var sampling = new ConnectionSampling(_ => Task.FromResult(true), _ => Task.FromResult((12L, 7L)));
        sampling.Updated += (_, sample) =>
        {
            Assert.True(Thread.CurrentThread.IsThreadPoolThread);
            Assert.Equal(new ConnectionSample(true, 12, 7), sample);
            Interlocked.Increment(ref samples);
            cancellation.Cancel();
        };
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => sampling.RunAsync(cancellation.Token));
        Assert.Equal(1, samples);
    }

    [Fact]
    public async Task SamplingThrottlesAndRejectsOverlappingRuns()
    {
        using var cancellation = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        var first = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var timer = System.Diagnostics.Stopwatch.StartNew();
        var timestamps = new List<TimeSpan>();
        var sampling = new ConnectionSampling(_ => Task.FromResult(false),
            _ => throw new InvalidOperationException("Disconnected sessions must not read traffic."));
        sampling.Updated += (_, sample) =>
        {
            Assert.False(sample.Ready);
            timestamps.Add(timer.Elapsed);
            first.TrySetResult();
            if (timestamps.Count == 2) cancellation.Cancel();
        };
        var running = sampling.RunAsync(cancellation.Token, TimeSpan.FromMilliseconds(100));
        await first.Task.WaitAsync(cancellation.Token);
        await Assert.ThrowsAsync<InvalidOperationException>(() => sampling.RunAsync(cancellation.Token));
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => running);
        Assert.Equal(2, timestamps.Count);
        Assert.True(timestamps[1] - timestamps[0] >= TimeSpan.FromMilliseconds(90));
    }

    private sealed class ReplyHandler(string json) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent(json) });
    }
}
