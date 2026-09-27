using System.Net;
using System.Text;
using Xunit;

namespace Weave.Windows.Core.Tests;

public sealed class MihomoTrafficTests
{
    [Fact]
    public async Task ReadsAuthenticatedLoopbackTrafficStream()
    {
        var handler = new TrafficHandler(
            "{\"up\":1024,\"down\":2048,\"upTotal\":4096,\"downTotal\":8192}\n" +
            "{\"up\":0,\"down\":0}\n");
        using var http = new HttpClient(handler);
        using var traffic = new MihomoControllerClient(12345, "test-secret", http);
        var snapshots = new List<TrafficSnapshot>();

        await foreach (var snapshot in traffic.WatchTrafficAsync()) snapshots.Add(snapshot);

        Assert.Equal(2, snapshots.Count);
        Assert.Equal(new TrafficSnapshot(1024, 2048, 4096, 8192), snapshots[0]);
        Assert.Equal(new TrafficSnapshot(0, 0, null, null), snapshots[1]);
        Assert.Equal("Bearer", handler.AuthorizationScheme);
        Assert.Equal("test-secret", handler.AuthorizationParameter);
        Assert.Equal("http://127.0.0.1:12345/traffic", handler.RequestUri);
    }

    [Theory]
    [InlineData("{\"up\":-1,\"down\":0}\n")]
    [InlineData("{\"up\":0,\"down\":\"10\"}\n")]
    [InlineData("{\"up\":0}\n")]
    [InlineData("not-json\n")]
    public async Task RejectsInvalidTrafficWithoutShowingFalseRates(string payload)
    {
        using var http = new HttpClient(new TrafficHandler(payload));
        using var traffic = new MihomoControllerClient(12345, "test-secret", http);
        await Assert.ThrowsAsync<InvalidDataException>(async () =>
        {
            await foreach (var _ in traffic.WatchTrafficAsync()) { }
        });
    }

    private sealed class TrafficHandler(string payload) : HttpMessageHandler
    {
        public string? AuthorizationScheme { get; private set; }
        public string? AuthorizationParameter { get; private set; }
        public string? RequestUri { get; private set; }

        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken)
        {
            AuthorizationScheme = request.Headers.Authorization?.Scheme;
            AuthorizationParameter = request.Headers.Authorization?.Parameter;
            RequestUri = request.RequestUri?.ToString();
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(payload, Encoding.UTF8, "application/json"),
            });
        }
    }
}
