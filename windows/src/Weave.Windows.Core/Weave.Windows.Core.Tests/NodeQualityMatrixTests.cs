using System.Collections.Concurrent;
using System.Net;
using System.Text;
using Xunit;

namespace Weave.Windows.Core.Tests;

public sealed class NodeQualityMatrixTests
{
    [Fact]
    public async Task SubscriptionProbeMeasuresEachNodeAndRanksUnavailableLast()
    {
        var nodes = new[] { Node("slow"), Node("failed"), Node("fast") };
        var handler = new BatchHandler();
        using var http = new HttpClient(handler);
        using var controller = new MihomoControllerClient(12345, "test-secret", http);

        var snapshots = await controller.ProbeSubscriptionAsync("provider-a", nodes);
        var rows = NodeQualityMatrix.Build(snapshots);

        Assert.Equal(new[] { "fast", "slow", "failed" }, rows.Select(row => row.Name));
        Assert.Equal(93, rows[0].StabilityScore);
        Assert.Equal("稳定", rows[0].StabilityLabel);
        Assert.Null(rows[2].StabilityScore);
        Assert.Equal("未完成", rows[2].StabilityLabel);
        Assert.Equal(100, rows[2].Health.ProbeFailurePercent);
        Assert.All(nodes, node => Assert.Equal(3, handler.Rounds[node.RawName]));
        Assert.InRange(handler.MaximumConcurrentRequests, 2, 8);
    }

    [Fact]
    public async Task SubscriptionProbeLimitsConcurrentNodesAndCanBeCancelled()
    {
        var nodes = Enumerable.Range(0, 10).Select(index => Node($"node-{index}")).ToArray();
        var handler = new BatchHandler();
        using var http = new HttpClient(handler);
        using var controller = new MihomoControllerClient(12345, "test-secret", http);

        var snapshots = await controller.ProbeSubscriptionAsync("provider-a", nodes);

        Assert.Equal(10, snapshots.Count);
        Assert.Equal(8, handler.MaximumConcurrentRequests);
        using var cancellation = new CancellationTokenSource();
        cancellation.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() =>
            controller.ProbeSubscriptionAsync("provider-a", nodes, cancellationToken: cancellation.Token));
    }

    private static ProxyNode Node(string name) => new()
    {
        Id = name,
        Name = name,
        RawName = name,
        Protocol = "vless",
    };

    private sealed class BatchHandler : HttpMessageHandler
    {
        private readonly ConcurrentDictionary<string, int> _rounds = new();
        private int _activeRequests;
        private int _maximumConcurrentRequests;

        public IReadOnlyDictionary<string, int> Rounds => _rounds;
        public int MaximumConcurrentRequests => _maximumConcurrentRequests;

        protected override async Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken)
        {
            Assert.Equal("Bearer", request.Headers.Authorization?.Scheme);
            Assert.Equal("test-secret", request.Headers.Authorization?.Parameter);
            var name = request.RequestUri!.AbsolutePath.Split('/')[4];
            var round = _rounds.AddOrUpdate(name, 1, (_, count) => count + 1);
            var active = Interlocked.Increment(ref _activeRequests);
            var maximum = _maximumConcurrentRequests;
            while (active > maximum &&
                   Interlocked.CompareExchange(ref _maximumConcurrentRequests, active, maximum) != maximum)
                maximum = _maximumConcurrentRequests;
            try
            {
                await Task.Delay(20, cancellationToken);
                var status = name == "failed" ? HttpStatusCode.GatewayTimeout : HttpStatusCode.OK;
                var delay = name == "fast" ? 40 + round * 10 : 100 + round * 100;
                return new HttpResponseMessage(status)
                {
                    Content = new StringContent($"{{\"delay\":{delay}}}", Encoding.UTF8, "application/json"),
                };
            }
            finally
            {
                Interlocked.Decrement(ref _activeRequests);
            }
        }
    }
}
