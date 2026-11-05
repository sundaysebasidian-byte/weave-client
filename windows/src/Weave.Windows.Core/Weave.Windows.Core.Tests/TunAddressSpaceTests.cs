using System.Net;

namespace Weave.Windows.Core.Tests;

public sealed class TunAddressSpaceTests
{
    [Fact]
    public void AvoidsTheDefaultMihomoAdapterAddresses()
    {
        var selected = TunAddressSpace.Select([IPAddress.Parse("198.18.0.1"), IPAddress.Parse("fdfe:dcba:9876::1")]);
        Assert.Equal("198.19.0.1/18", selected.FakeIpv4Range);
        Assert.Equal("fd57:6561:7665:0::1/126", selected.Ipv6Address);
    }

    [Theory]
    [InlineData("198.19.0.1")]
    [InlineData("198.19.63.254")]
    [InlineData("fd57:6561:7665::2")]
    public void SkipsAnOccupiedIpv4PoolOrIpv6Subnet(string occupied)
    {
        var selected = TunAddressSpace.Select([IPAddress.Parse(occupied)]);
        Assert.Equal("198.19.64.1/18", selected.FakeIpv4Range);
        Assert.Equal("fd57:6561:7665:1::1/126", selected.Ipv6Address);
    }

    [Fact]
    public void DoesNotReuseAnOccupiedAddressWhenAllCandidatesAreTaken()
    {
        var occupied = Enumerable.Range(0, 4).Select(index => IPAddress.Parse($"198.19.{index * 64}.1"));
        Assert.Throws<IOException>(() => TunAddressSpace.Select(occupied));
    }
}
