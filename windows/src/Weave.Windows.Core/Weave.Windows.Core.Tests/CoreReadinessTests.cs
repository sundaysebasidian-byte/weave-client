using System.Net;
using System.Net.Sockets;
using System.Text;
using Weave.Windows.TestFixtures;

namespace Weave.Windows.Core.Tests;

[Collection("Native data path")]
public sealed class CoreReadinessTests
{
    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task LoadedControllerCannotReportReadyUntilForwardingStarts(bool cancelWhileLoading)
    {
        var executable = Environment.GetEnvironmentVariable("WEAVE_TEST_CORE");
        if (string.IsNullOrEmpty(executable)) return;
        using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(25));
        using var startingCancellation = CancellationTokenSource.CreateLinkedTokenSource(deadline.Token);
        using var rules = new TcpListener(IPAddress.Loopback, 0);
        using var upstream = new TcpListener(IPAddress.Loopback, 0);
        rules.Start(); upstream.Start();
        var requested = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var release = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var ruleServing = ServeRulesAsync(rules, requested, release, deadline.Token);
        var forwarding = SyntheticUpstream.ServeAsync(upstream, deadline.Token);
        using var importer = new SubscriptionImporter();
        var subscription = importer.ImportText("startup-fixture", "inline://startup-fixture",
            $"proxies: [{{name: fixture, type: http, server: 127.0.0.1, port: {Port(upstream)}}}]");
        var folder = Path.Combine(Path.GetTempPath(), "weave-readiness-" + Guid.NewGuid().ToString("N"));
        await using var process = new MihomoProcess(executable);
        Task? starting = null;
        try
        {
            var bundle = new MihomoConfigBuilder().Build([subscription], [], subscription.Id, subscription.Nodes[0].Id,
                new() { EnableTun = false, RoutingMode = RoutingMode.Global, Ipv6Enabled = false }, folder);
            File.AppendAllText(bundle.ConfigPath, $"\nrule-providers:\n  startup-gate:\n    type: http\n    behavior: domain\n    format: yaml\n    url: http://127.0.0.1:{Port(rules)}/rules\n    path: ./providers/startup-gate.yaml\n    proxy: DIRECT\n    interval: 86400\n");
            Assert.True((await process.ValidateConfigAsync(bundle, deadline.Token)).IsValid);
            starting = process.StartAsync(bundle, startingCancellation.Token);
            await requested.Task.WaitAsync(deadline.Token);
            using var controller = new MihomoController(bundle);
            Assert.True(await controller.IsReadyAsync(bundle, deadline.Token));
            // Keep a deterministic startup dependency blocked across several readiness polls.
            // The controller is already available, but normal traffic is still rejected.
            await Task.WhenAny(starting, Task.Delay(1500, deadline.Token));
            Assert.False(starting.IsCompleted, "Start completed before the core enabled forwarding.");
            Assert.False(process.IsReady);
            if (cancelWhileLoading)
            {
                startingCancellation.Cancel();
                await Assert.ThrowsAnyAsync<OperationCanceledException>(() => starting);
                Assert.False(process.IsRunning);
                Assert.False(process.IsReady);
                using var releasedPort = new TcpListener(IPAddress.Loopback, bundle.ReadinessTargetPort);
                releasedPort.Start();
            }
            else
            {
                release.TrySetResult();
                await ruleServing;
                await starting;
                Assert.True(process.IsReady);
                using var client = new HttpClient(new SocketsHttpHandler
                {
                    UseProxy = true, Proxy = new WebProxy($"http://127.0.0.1:{bundle.MixedPort}")
                });
                // No settling delay: the first request after Start must already work.
                Assert.Equal("weave-service-upstream",
                    await client.GetStringAsync("http://198.51.100.42/check", deadline.Token));
                Assert.Contains("CONNECT 198.51.100.42:80", await forwarding);
            }
        }
        finally
        {
            deadline.Cancel();
            release.TrySetCanceled();
            if (starting is not null) await starting.ConfigureAwait(ConfigureAwaitOptions.SuppressThrowing | ConfigureAwaitOptions.ContinueOnCapturedContext);
            await process.StopAsync();
            rules.Stop(); upstream.Stop();
            await Task.WhenAll(ruleServing, forwarding).ConfigureAwait(ConfigureAwaitOptions.SuppressThrowing | ConfigureAwaitOptions.ContinueOnCapturedContext);
            await NativeSessionCleanup.DeleteAsync(folder);
        }
    }

    [Fact]
    public async Task UnrelatedListenerCannotConfirmReadiness()
    {
        using var reservation = new TcpListener(IPAddress.Loopback, 0);
        reservation.Start();
        var targetPort = Port(reservation);
        reservation.Stop();
        await using var probe = new CoreReadinessProbe(targetPort);
        using var fake = new TcpListener(IPAddress.Loopback, 0);
        fake.Start();
        using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(5));
        var verification = probe.IsForwardingAsync(Port(fake), deadline.Token);
        using var connection = await fake.AcceptTcpClientAsync(deadline.Token);
        var challenge = new byte[32];
        await connection.GetStream().ReadExactlyAsync(challenge, deadline.Token);
        await connection.GetStream().WriteAsync(challenge, deadline.Token);
        Assert.False(await verification);
    }

    private static int Port(TcpListener listener) => ((IPEndPoint)listener.LocalEndpoint).Port;

    private static async Task ServeRulesAsync(TcpListener listener, TaskCompletionSource requested,
        TaskCompletionSource release, CancellationToken token)
    {
        using var connection = await listener.AcceptTcpClientAsync(token);
        var stream = connection.GetStream();
        var header = new byte[8192];
        var size = 0;
        while (size < header.Length)
        {
            if (await stream.ReadAsync(header.AsMemory(size, 1), token) == 0) throw new EndOfStreamException();
            size++;
            if (size >= 4 && header.AsSpan(size - 4, 4).SequenceEqual("\r\n\r\n"u8)) break;
        }
        requested.TrySetResult();
        await release.Task.WaitAsync(token);
        const string body = "payload:\n  - '+.weave-readiness.invalid'\n";
        await stream.WriteAsync(Encoding.ASCII.GetBytes($"HTTP/1.1 200 OK\r\nContent-Length: {body.Length}\r\nConnection: close\r\n\r\n{body}"), token);
    }
}
