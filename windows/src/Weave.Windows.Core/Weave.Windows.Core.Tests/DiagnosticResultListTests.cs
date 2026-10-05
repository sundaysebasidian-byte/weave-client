namespace Weave.Windows.Core.Tests;

public class DiagnosticResultListTests
{
    [Fact]
    public void OutOfOrderResultsKeepFixedSlotsAndIgnoreDuplicates()
    {
        var rows = new DiagnosticResultList();
        var names = rows.Select(row => row.Name).ToArray();
        rows.Record(new ProbeResult("Disney+", "", 10, 204));
        rows.Record(new ProbeResult("Google", "", 20, 403));
        rows.Record(new ProbeResult("Google", "", 1, 200));
        rows.Record(new ProbeResult("unknown", "private"));
        Assert.Equal(names, rows.Select(row => row.Name));
        Assert.Equal(11, rows.Count);
        Assert.Equal(2, rows.CompletedCount);
        Assert.Equal(403, rows.Single(row => row.Name == "Google").HttpStatus);
    }

    [Fact]
    public void PendingSlotsNeverBecomeMeasuredOrExportedEvidence()
    {
        var rows = new DiagnosticResultList();
        Assert.All(rows, row => { Assert.True(row.Pending); Assert.False(row.ExitVerified); Assert.False(row.EndpointVerified); });
        var summary = DiagnosticSafeSummary.Build(rows, true);
        Assert.Contains("ipv4_measured=False", summary);
        Assert.DoesNotContain("Google:", summary);
        rows.Record(new ProbeResult("Google", "", 12, 204));
        summary = DiagnosticSafeSummary.Build(rows, true);
        Assert.Contains("Google: VERIFIED", summary);
        Assert.DoesNotContain("Disney+:", summary);
    }

    [Fact]
    public void RedirectDetailsPreserveStatusWithoutClaimingVerification()
    {
        var row = new ProbeResult("Google", "", 9, 307);
        Assert.Contains("HTTP 307", row.Details);
        Assert.False(row.EndpointVerified);
    }
}
