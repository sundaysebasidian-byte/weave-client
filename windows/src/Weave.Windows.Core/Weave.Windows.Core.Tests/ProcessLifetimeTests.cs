using System.Diagnostics;
using System.Net;
using System.Net.Sockets;

namespace Weave.Windows.Core.Tests;

[Collection("Native data path")]
public sealed class ProcessLifetimeTests
{
    [Fact]
    public async Task FailedTerminationRetainsTheOwnedCoreForRetry()
    {
        var executable = Environment.GetEnvironmentVariable("WEAVE_TEST_CORE");
        if (string.IsNullOrEmpty(executable)) return;
        var folder = Path.Combine(Path.GetTempPath(), "weave-stop-retry-" + Guid.NewGuid().ToString("N"));
        var failTermination = true;
        Process? child = null;
        await using var core = new MihomoProcess(executable)
        {
            TestTerminateAsync = async process =>
            {
                child ??= Process.GetProcessById(process.Id);
                if (failTermination) throw new IOException("Injected unconfirmed termination.");
                if (!process.HasExited) process.Kill(entireProcessTree: true);
                await process.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(5));
            }
        };
        try
        {
            var bundle = new MihomoConfigBuilder().Build([], [], null, null,
                new() { EnableTun = false, RoutingMode = RoutingMode.Direct }, folder);
            await core.StartAsync(bundle);
            await Assert.ThrowsAsync<IOException>(() => core.StopAsync());
            Assert.False(child!.HasExited);
            Assert.True(core.IsRunning);
            Assert.False(core.IsReady);
            await Assert.ThrowsAsync<InvalidOperationException>(() => core.StartAsync(bundle));
            using var stillOwned = new TcpListener(IPAddress.Loopback, bundle.ReadinessTargetPort);
            Assert.Throws<SocketException>(() => stillOwned.Start());
            failTermination = false;
            await core.StopAsync();
            Assert.False(core.IsRunning);
            using var released = new TcpListener(IPAddress.Loopback, bundle.ReadinessTargetPort);
            released.Start();
            await core.StopAsync();
        }
        finally
        {
            // Even a failed baseline assertion must stop only this test's child.
            failTermination = false;
            if (child is not null)
            {
                try
                {
                    if (!child.HasExited) child.Kill(entireProcessTree: true);
                    await child.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(5));
                }
                catch (InvalidOperationException) { }
                child.Dispose();
            }
            await core.StopAsync();
            await NativeSessionCleanup.DeleteAsync(folder);
        }
    }
}
