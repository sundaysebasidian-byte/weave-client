using System.Diagnostics;
using System.Net.NetworkInformation;

namespace Weave.Windows.TestFixtures;

internal static class SyntheticNetworkSnapshot
{
    public static async Task RecordAsync(Action<string> write)
    {
        if (!OperatingSystem.IsWindows() || Environment.GetEnvironmentVariable("WEAVE_TEST_TUN") != "1") return;
        write($"NETWORK_SNAPSHOT {DateTime.UtcNow:O}");
        foreach (var adapter in NetworkInterface.GetAllNetworkInterfaces())
            write($"Adapter {adapter.Name}: {adapter.OperationalStatus}; " +
                string.Join(", ", adapter.GetIPProperties().UnicastAddresses.Select(item => item.Address)));
        using var process = new Process { StartInfo = new()
        {
            FileName = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), "route.exe"),
            Arguments = "PRINT -4", UseShellExecute = false, CreateNoWindow = true,
            RedirectStandardOutput = true, RedirectStandardError = true,
        } };
        var started = false;
        try
        {
            started = process.Start();
            if (!started) return;
            using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(5));
            var stdout = process.StandardOutput.ReadToEndAsync(deadline.Token);
            var stderr = process.StandardError.ReadToEndAsync(deadline.Token);
            await process.WaitForExitAsync(deadline.Token);
            var text = await stdout + await stderr;
            write("IPv4 routes at failure:\n" + text[..Math.Min(text.Length, 16_384)]);
        }
        catch (Exception error) when (error is OperationCanceledException or System.ComponentModel.Win32Exception or IOException)
        { write("Route snapshot unavailable: " + error.GetType().Name); }
        finally
        {
            if (started && !process.HasExited)
            {
                try { process.Kill(); await process.WaitForExitAsync().WaitAsync(TimeSpan.FromSeconds(5)); }
                catch (Exception error) when (error is System.ComponentModel.Win32Exception or InvalidOperationException or TimeoutException)
                { write("Route snapshot process cleanup unconfirmed: " + error.GetType().Name); }
            }
        }
    }
}
