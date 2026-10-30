namespace Weave.Windows.Core.Tests;

public sealed class RuntimeSessionTests : IDisposable
{
    private readonly string _root = Path.Combine(Path.GetTempPath(), "weave-runtime-test-" + Guid.NewGuid().ToString("N"));

    [Fact]
    public void ActiveLeaseProtectsSessionUntilReleased()
    {
        using var session = RuntimeSession.Reserve(_root);
        Directory.CreateDirectory(session.DirectoryPath);
        File.WriteAllText(Path.Combine(session.DirectoryPath, "config.yaml"), "fixture");
        Assert.Equal(0, RuntimeSession.Recover(_root).Removed);
        Assert.True(Directory.Exists(session.DirectoryPath));
        session.Dispose();
        Assert.Equal(1, RuntimeSession.Recover(_root).Removed);
        Assert.False(Directory.Exists(session.DirectoryPath));
    }

    [Fact]
    public void UnknownFilesAreRetained()
    {
        using var session = RuntimeSession.Reserve(_root);
        Directory.CreateDirectory(session.DirectoryPath);
        var file = Path.Combine(session.DirectoryPath, "user-data.txt");
        File.WriteAllText(file, "retain");
        Assert.False(session.CleanupAfterCoreExit());
        session.Dispose();
        Assert.Equal(1, RuntimeSession.Recover(_root).Retained);
        Assert.Equal("retain", File.ReadAllText(file));
    }

    [Fact]
    public void ExistingUnknownFoldersAreNeverSwept()
    {
        var directory = Path.Combine(_root, Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(directory);
        File.WriteAllText(Path.Combine(directory, "config.yaml"), "legacy");
        Assert.Equal((0, 0), RuntimeSession.Recover(_root));
        Assert.True(Directory.Exists(directory));
    }

    [Fact]
    public void FinishedSessionRemovesJournalAndConfiguration()
    {
        using var session = RuntimeSession.Reserve(_root);
        Directory.CreateDirectory(Path.Combine(session.DirectoryPath, "providers"));
        File.WriteAllText(Path.Combine(session.DirectoryPath, "providers", "fixture.yaml"), "proxies: []");
        Assert.True(session.CleanupAfterCoreExit());
        Assert.Empty(Directory.EnumerateFileSystemEntries(_root));
    }

    public void Dispose() { if (Directory.Exists(_root)) Directory.Delete(_root, true); }
}
