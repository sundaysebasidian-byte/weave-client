using System.Net;
using System.Net.Sockets;

namespace Weave.Windows.Core;

public static class PublicSubscriptionAddress
{
    public static bool IsPublic(IPAddress address)
    {
        if (address.IsIPv4MappedToIPv6) address = address.MapToIPv4();
        var b = address.GetAddressBytes();
        if (b.Length == 4) return IsPublicV4(b);
        if (address.AddressFamily != AddressFamily.InterNetworkV6 || address.ScopeId != 0) return false;
        if (b.AsSpan(0, 12).SequenceEqual(new byte[] { 0, 100, 255, 155, 0, 0, 0, 0, 0, 0, 0, 0 })) return IsPublicV4(b.AsSpan(12));
        if ((b[0] & 0xe0) != 0x20 || (b[0] == 0x20 && b[1] == 2)) return false;
        if (b[0] == 0x20 && b[1] == 1 && (b[2] < 2 || (b[2] == 0x0d && b[3] == 0xb8))) return false;
        return !(b[0] == 0x3f && b[1] == 0xff && (b[2] & 0xf0) == 0);
    }

    private static bool IsPublicV4(ReadOnlySpan<byte> b) => !(b[0] is 0 or 10 or 127 or >= 224 ||
        (b[0] == 100 && b[1] is >= 64 and <= 127) || (b[0] == 169 && b[1] == 254) ||
        (b[0] == 172 && b[1] is >= 16 and <= 31) ||
        (b[0] == 192 && (b[1] == 168 || (b[1] == 0 && b[2] is 0 or 2) || (b[1] == 88 && b[2] == 99))) ||
        (b[0] == 198 && (b[1] is 18 or 19 || (b[1] == 51 && b[2] == 100))) ||
        (b[0] == 203 && b[1] == 0 && b[2] == 113));
}
