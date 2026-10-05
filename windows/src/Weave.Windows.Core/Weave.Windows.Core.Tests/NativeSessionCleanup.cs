namespace Weave.Windows.Core.Tests;

internal static class NativeSessionCleanup
{
    // Windows/AV may briefly retain a cache mapping after WaitForExit has completed.
    // Only retry deletion of our exact test session; a persistent failure still fails the test.
    public static async Task DeleteAsync(string folder)
    {
        for (var attempt = 0; ; attempt++)
        {
            try { if (Directory.Exists(folder)) Directory.Delete(folder, true); return; }
            catch (Exception error) when (attempt < 10 && error is IOException or UnauthorizedAccessException)
            { await Task.Delay(200); }
        }
    }
}
