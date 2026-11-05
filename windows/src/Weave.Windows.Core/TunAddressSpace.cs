using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace Weave.Windows.Core;

internal sealed record TunAddressSpace(string FakeIpv4Range, string Ipv6Address)
{
    internal static TunAddressSpace Current() => Select(NetworkInterface.GetAllNetworkInterfaces()
        .SelectMany(adapter => adapter.GetIPProperties().UnicastAddresses.Select(item => item.Address)));

    internal static TunAddressSpace Select(IEnumerable<IPAddress> assigned)
    {
        var addresses = assigned.ToArray();
        // Mihomo v1.19.30 derives the TUN IPv4 /30 from dns.fake-ip-range;
        // setting tun.inet4-address in YAML has no effect in that version.
        // Stay outside the usual 198.18/16 pool and skip occupied candidate subnets.
        for (var index = 0; index < 4; index++)
        {
            var v6 = IPAddress.Parse($"fd57:6561:7665:{index:x}::1").GetAddressBytes();
            var busy = addresses.Any(address =>
            {
                var bytes = address.GetAddressBytes();
                if (address.AddressFamily == AddressFamily.InterNetwork)
                    return bytes[0] == 198 && bytes[1] == 19 && bytes[2] / 64 == index;
                return address.AddressFamily == AddressFamily.InterNetworkV6 &&
                    bytes.AsSpan(0, 15).SequenceEqual(v6.AsSpan(0, 15)) && (bytes[15] & 0xfc) == 0;
            });
            if (!busy) return new($"198.19.{index * 64}.1/18", $"fd57:6561:7665:{index:x}::1/126");
        }
        throw new IOException(L.T("Weave 的候选 TUN 地址段均已被占用。请关闭其他 VPN 的 TUN 后重试。"));
    }
}
