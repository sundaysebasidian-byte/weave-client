using System.Net;
using System.Text;
using Xunit;

namespace Weave.Windows.Core.Tests;

public sealed class MihomoControllerHealthTests
{
    private static readonly ProxyNode Node = new()
    {
        Id = "node-1",
        Name = "HK / 1",
        RawName = "HK / 1",
        Protocol = "vless",
    };

    [Fact]
    public async Task AggregatesThreeFreshProbeResponses()
    {
        var handler = new HealthHandler(
            (HttpStatusCode.OK, "{\"delay\":100}"),
            (HttpStatusCode.OK, "{\"delay\":300}"),
            (HttpStatusCode.OK, "{\"delay\":65535}"));
        using var http = new HttpClient(handler);
        using var controller = new MihomoControllerClient(12345, "test-secret", http);

        var result = await controller.ProbeNodeAsync("provider-a", Node);

        Assert.Equal(3, handler.RequestCount);
        Assert.All(handler.RequestPaths, path =>
            Assert.Contains("/providers/proxies/provider-a/HK%20%2F%201/healthcheck", path));
        Assert.Equal(300, result.MedianLatencyMs);
        Assert.Equal(300, result.P95LatencyMs);
        Assert.Equal(200, result.JitterMs);
        Assert.Equal(33, result.ProbeFailurePercent);
        Assert.Equal(2, result.SuccessfulSamples);
    }

    [Fact]
    public async Task ProbeTimeoutResponsesCountAsFailedSamples()
    {
        var handler = new HealthHandler(
            (HttpStatusCode.GatewayTimeout, ""),
            (HttpStatusCode.RequestTimeout, ""),
            (HttpStatusCode.OK, "{\"delay\":0}"));
        using var http = new HttpClient(handler);
        using var controller = new MihomoControllerClient(12345, "test-secret", http);

        var result = await controller.ProbeNodeAsync("provider-a", Node);

        Assert.Null(result.MedianLatencyMs);
        Assert.Null(result.P95LatencyMs);
        Assert.Null(result.JitterMs);
        Assert.Equal(100, result.ProbeFailurePercent);
    }

    [Fact]
    public async Task MissingProviderIsAnErrorRatherThanPacketLoss()
    {
        using var http = new HttpClient(new HealthHandler((HttpStatusCode.NotFound, "")));
        using var controller = new MihomoControllerClient(12345, "test-secret", http);
        await Assert.ThrowsAsync<HttpRequestException>(() => controller.ProbeNodeAsync("missing", Node));
    }

    private sealed class HealthHandler(params (HttpStatusCode Status, string Body)[] responses) : HttpMessageHandler
    {
        public int RequestCount { get; private set; }
        public List<string> RequestPaths { get; } = new();

        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken)
        {
            Assert.Equal("Bearer", request.Headers.Authorization?.Scheme);
            Assert.Equal("test-secret", request.Headers.Authorization?.Parameter);
            RequestPaths.Add(request.RequestUri!.AbsolutePath);
            var response = responses[RequestCount++];
            return Task.FromResult(new HttpResponseMessage(response.Status)
            {
                Content = new StringContent(response.Body, Encoding.UTF8, "application/json"),
            });
        }
    }
}
