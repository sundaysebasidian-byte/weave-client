using System.Net.Sockets;
using System.Text;

namespace Weave.Windows.TestFixtures;

internal static class SyntheticUpstream
{
    public static async Task<string> ServeAsync(TcpListener listener, CancellationToken token)
    {
        while (true)
        {
            using var connection = await listener.AcceptTcpClientAsync(token);
            Console.WriteLine("UPSTREAM_TCP_ACCEPTED");
            await using var stream = connection.GetStream();
            var header = await ReadHeaderAsync(stream, token);
            if (!header.StartsWith("CONNECT 198.51.100.42:80 ", StringComparison.Ordinal)) continue;
            await stream.WriteAsync("HTTP/1.1 200 Connection established\r\n\r\n"u8.ToArray(), token);
            if ((await ReadHeaderAsync(stream, token)).Length == 0) throw new EndOfStreamException("Missing synthetic HTTP request.");
            const string body = "weave-service-upstream";
            await stream.WriteAsync(Encoding.ASCII.GetBytes($"HTTP/1.1 200 OK\r\nContent-Length: {body.Length}\r\nConnection: close\r\n\r\n{body}"), token);
            return header;
        }
    }

    private static async Task<string> ReadHeaderAsync(Stream stream, CancellationToken token)
    {
        var bytes = new byte[8192];
        for (var length = 0; length < bytes.Length;)
        {
            if (await stream.ReadAsync(bytes.AsMemory(length, 1), token) == 0)
            {
                // A core TCP health check may connect and close without sending HTTP.
                if (length == 0) return "";
                throw new EndOfStreamException("Incomplete synthetic HTTP header.");
            }
            length++;
            if (length >= 4 && bytes.AsSpan(length - 4, 4).SequenceEqual("\r\n\r\n"u8)) return Encoding.ASCII.GetString(bytes, 0, length);
        }
        throw new InvalidDataException("Synthetic request header is too large.");
    }
}
