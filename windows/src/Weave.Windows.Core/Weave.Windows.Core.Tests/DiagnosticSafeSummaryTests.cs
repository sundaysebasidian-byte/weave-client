using System.Net;

namespace Weave.Windows.Core.Tests;

public class DiagnosticSafeSummaryTests
{
    [Fact]
    public void ExportNeverCopiesRawAddressesNamesOrErrors()
    {
        var rows = new[] {
            new ProbeResult("代理出口 IPv4", "8.8.8.8"),
            new ProbeResult("Google", "private-secret.example/password", 12, Failure: ProbeFailure.Dns),
            new ProbeResult("private-node", "https://private-subscription/?token=secret"),
        };
        var summary = DiagnosticSafeSummary.Build(rows, true);
        Assert.Contains("historical=True", summary);
        Assert.Contains("failure=Dns", summary);
        Assert.DoesNotContain("private", summary);
        Assert.DoesNotContain("8.8.8.8", summary);
        Assert.DoesNotContain("secret", summary);
    }

    [Fact]
    public void StatusFieldsAreBoundedAndRedirectIsNotVerified()
    {
        var summary = DiagnosticSafeSummary.Build(new[] { new ProbeResult("Google", "", -500, 302), new ProbeResult("X", "", long.MaxValue, 999) }, false);
        Assert.Contains("Google: UNCONFIRMED; http=302; ms=unknown", summary);
        Assert.Contains("X: UNCONFIRMED; http=unknown; ms=unknown", summary);
    }

    [Fact]
    public void FailureClassificationIsStructured()
    {
        Assert.Equal(ProbeFailure.Dns, NetworkDiagnostics.ClassifyFailure(new HttpRequestException(HttpRequestError.NameResolutionError, "secret")));
        Assert.Equal(ProbeFailure.Tls, NetworkDiagnostics.ClassifyFailure(new HttpRequestException(HttpRequestError.SecureConnectionError, "secret")));
        Assert.Equal(ProbeFailure.Connection, NetworkDiagnostics.ClassifyFailure(new HttpRequestException(HttpRequestError.ConnectionError, "secret")));
        Assert.Equal(ProbeFailure.Timeout, NetworkDiagnostics.ClassifyFailure(new TaskCanceledException()));
    }
}
