using System.Diagnostics;
using System.Net;
using System.Net.Sockets;

namespace Weave.Windows.Core.Tests;

[Collection("Native data path")]
public sealed class ProcessLifetimeTests
{
    [Theory]
    [InlineData(false, false)]
    [InlineData(false, true)]
    [InlineData(true, false)]
    [InlineData(true, true)]
    public async Task StopInterruptsPendingOperation(bool validate, bool dispose)
    {
        if (OperatingSystem.IsWindows()) return; // Unix fixture; native readiness coverage also runs on Windows.
        var folder = Path.Combine(Path.GetTempPath(), "weave-cancel-operation-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(folder);
        var executable = Path.Combine(folder, "blocked-core");
        await File.WriteAllTextAsync(executable, "#!/bin/sh\ntouch started\nexec sleep 30\n");
        File.SetUnixFileMode(executable, UnixFileMode.UserRead | UnixFileMode.UserWrite | UnixFileMode.UserExecute);
        await using var core = new MihomoProcess(executable);
        Task? operation = null;
        try
        {
            var bundle = new MihomoConfigBuilder().Build([], [], null, null,
                new() { EnableTun = false, RoutingMode = RoutingMode.Direct }, Path.Combine(folder, "runtime"));
            operation = validate ? core.ValidateConfigAsync(bundle) : core.StartAsync(bundle);
            using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(5));
            while (!File.Exists(Path.Combine(folder, "runtime", "started"))) await Task.Delay(10, deadline.Token);
            Assert.False(operation.IsCompleted);
            var elapsed = Stopwatch.StartNew();
            await (dispose ? core.DisposeAsync().AsTask() : core.StopAsync()).WaitAsync(TimeSpan.FromSeconds(3));
            Assert.True(elapsed.Elapsed < TimeSpan.FromSeconds(3));
            await Assert.ThrowsAnyAsync<OperationCanceledException>(() => operation);
            Assert.False(core.IsRunning);
            Assert.False(core.IsReady);
            await core.StopAsync();
        }
        finally
        {
            await core.StopAsync();
            if (operation is not null) await operation.ConfigureAwait(ConfigureAwaitOptions.SuppressThrowing | ConfigureAwaitOptions.ContinueOnCapturedContext);
            await NativeSessionCleanup.DeleteAsync(folder);
        }
    }

    [Fact]
    public async Task StopHonoursPreCancelledToken()
    {
        await using var core = new MihomoProcess("unused");
        using var cancellation = new CancellationTokenSource();
        cancellation.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => core.StopAsync(cancellation.Token));
        await core.StopAsync();
    }

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
            await Task.WhenAll(core.StopAsync(), core.StopAsync(), core.StopAsync());
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
