namespace Weave.Windows.Core.Tests;

public class AtomicFileTests
{
    [Fact]
    public void ReplaceIsCompleteAndLeavesNoPendingFile()
    {
        var directory = Directory.CreateTempSubdirectory("weave-atomic-");
        try
        {
            var path = Path.Combine(directory.FullName, "record.bin");
            AtomicFile.WriteAllBytes(path, new byte[] { 1, 2 });
            AtomicFile.WriteAllBytes(path, new byte[] { 3, 4, 5 });
            Assert.Equal(new byte[] { 3, 4, 5 }, File.ReadAllBytes(path));
            Assert.Single(directory.GetFiles());
        }
        finally { directory.Delete(true); }
    }

    [Fact]
    public void FailedReplacementRemovesOnlyItsStagingFile()
    {
        var directory = Directory.CreateTempSubdirectory("weave-atomic-failure-");
        try
        {
            var target = Directory.CreateDirectory(Path.Combine(directory.FullName, "target"));
            var failure = Record.Exception(() => AtomicFile.WriteAllBytes(target.FullName, new byte[] { 1 }));
            Assert.True(failure is IOException or UnauthorizedAccessException);
            Assert.True(target.Exists);
            Assert.Empty(directory.GetFiles());
        }
        finally { directory.Delete(true); }
    }
}
