namespace Weave.Windows.Core.Tests;

public sealed class SubscriptionReviewTests
{
    // A copying test protector isolates transaction semantics from platform DPAPI.
    private sealed class TestProtector : ISecretProtector
    {
        public byte[] Protect(byte[] bytes) => bytes.ToArray();
        public byte[] Unprotect(byte[] bytes) => bytes.ToArray();
    }

    private static SubscriptionRecord Record(string payload) => new()
    {
        Id = "test", Name = "Test", Source = "inline://test",
        Payload = payload, ProviderYaml = payload,
    };

    [Fact]
    public async Task AsyncRemovalPersistsAndHonoursCancellation()
    {
        var folder = Path.Combine(Path.GetTempPath(), "weave-remove-" + Guid.NewGuid().ToString("N"));
        try
        {
            var path = Path.Combine(folder, "test.vault");
            var vault = new SubscriptionVault(path, new TestProtector());
            vault.Upsert(Record("original"));
            using var cancellation = new CancellationTokenSource();
            cancellation.Cancel();
            await Assert.ThrowsAnyAsync<OperationCanceledException>(() => vault.RemoveAsync("test", cancellation.Token));
            Assert.Single(vault.List());
            var removals = await Task.WhenAll(Enumerable.Range(0, 8).Select(_ => vault.RemoveAsync("test")));
            Assert.Single(removals, removed => removed);
            Assert.Empty(new SubscriptionVault(path, new TestProtector()).List());
            Assert.Empty(Directory.GetFiles(folder, "*.pending"));
        }
        finally { if (Directory.Exists(folder)) Directory.Delete(folder, true); }
    }

    [Fact]
    public void ReviewedReplacementCommitsOnlyTheExpectedVersion()
    {
        var folder = Path.Combine(Path.GetTempPath(), "weave-review-" + Guid.NewGuid().ToString("N"));
        try
        {
            var vault = new SubscriptionVault(Path.Combine(folder, "test.vault"), new TestProtector());
            var original = Record("original");
            var reviewed = Record("reviewed");
            vault.Upsert(original);
            vault.ReplaceReviewed(original, reviewed);
            Assert.Equal("reviewed", Assert.Single(vault.List()).Payload);
            Assert.Throws<InvalidOperationException>(() => vault.ReplaceReviewed(original, Record("stale")));
            Assert.Equal("reviewed", Assert.Single(vault.List()).Payload);
            Assert.Empty(Directory.GetFiles(folder, "*.pending"));
        }
        finally { if (Directory.Exists(folder)) Directory.Delete(folder, true); }
    }

    [Fact]
    public void ReviewCannotRestoreDeletedSubscription()
    {
        var folder = Path.Combine(Path.GetTempPath(), "weave-review-" + Guid.NewGuid().ToString("N"));
        try
        {
            var vault = new SubscriptionVault(Path.Combine(folder, "test.vault"), new TestProtector());
            var original = Record("original");
            vault.Upsert(original);
            Assert.True(vault.Remove(original.Id));
            Assert.Throws<InvalidOperationException>(() => vault.ReplaceReviewed(original, Record("replacement")));
            Assert.Empty(vault.List());
        }
        finally { if (Directory.Exists(folder)) Directory.Delete(folder, true); }
    }
}
