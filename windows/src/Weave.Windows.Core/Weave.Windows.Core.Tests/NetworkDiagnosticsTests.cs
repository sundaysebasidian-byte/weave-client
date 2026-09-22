using System.Net;
using System.Collections.Concurrent;

namespace Weave.Windows.Core.Tests;

public class NetworkDiagnosticsTests
{
    [Theory]
    [InlineData("8.8.8.8", false, true)]
    [InlineData("2606:4700:4700::1111", true, true)]
    [InlineData("127.0.0.1", false, false)]
    [InlineData("10.0.0.2", false, false)]
    [InlineData("192.0.2.10", false, false)]
    [InlineData("100.64.0.1", false, false)]
    [InlineData("::1", true, false)]
    [InlineData("fd00::1", true, false)]
    [InlineData("2001:db8::1", true, false)]
    [InlineData("8.8.8.8", true, false)]
    public void ExitEvidenceRejectsWrongFamiliesAndPlaceholders(string value, bool ipv6, bool expected)
        => Assert.Equal(expected, NetworkDiagnostics.IsExpectedExitAddress(value, ipv6));

    [Fact]
    public async Task DualStackResultsArriveIndependentlyAndRedirectsAreNotVerified()
    {
        using var client = new HttpClient(new Fixture());
        var progress = new Capture();
        var results = await NetworkDiagnostics.RunUsingClientAsync(client, CancellationToken.None, progress);
        Assert.Equal(11, results.Count);
        Assert.Equal(11, progress.Results.Count);
        Assert.Equal("8.8.8.8", results[0].Result);
        Assert.Equal("2606:4700:4700::1111", results[1].Result);
        Assert.All(results.Where(result => result.HttpStatus.HasValue), result => Assert.False(result.EndpointVerified));
    }

    [Fact]
    public async Task CancellationDoesNotPublishSuccessfulRows()
    {
        using var token = new CancellationTokenSource();
        token.Cancel();
        var progress = new Capture();
        using var client = new HttpClient(new Fixture());
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => NetworkDiagnostics.RunUsingClientAsync(client, token.Token, progress));
        Assert.Empty(progress.Results);
    }

    private sealed class Capture : IProgress<ProbeResult>
    {
        public ConcurrentBag<ProbeResult> Results { get; } = new();
        public void Report(ProbeResult result) => Results.Add(result);
    }
    private sealed class Fixture : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token)
        {
            token.ThrowIfCancellationRequested();
            var host = request.RequestUri!.Host;
            return Task.FromResult(host.Contains("ipify")
                ? new HttpResponseMessage(HttpStatusCode.OK) { Content = new StringContent("{\"ip\":\"" + (host.StartsWith("api6") ? "2606:4700:4700::1111" : "8.8.8.8") + "\"}") }
                : new HttpResponseMessage(HttpStatusCode.Found));
        }
    }
}
