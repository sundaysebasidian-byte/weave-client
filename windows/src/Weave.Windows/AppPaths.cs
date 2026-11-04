namespace Weave.Windows;

/// <summary>
/// Where per-user files live (encrypted library, routes, appearance, language, the single-instance lock).
/// WEAVE_DATA_ROOT redirects all of them. It exists for UI previews and automated checks: a preview run then never reads or
/// writes the real library and does not compete with an installed instance for the lock.
/// </summary>
internal static class AppPaths
{
    public static string DataRoot { get; } = Environment.GetEnvironmentVariable("WEAVE_DATA_ROOT") is { Length: > 0 } custom
        ? Path.GetFullPath(custom)
        : Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Weave");
}
