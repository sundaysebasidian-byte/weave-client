using System.Net;
using System.Net.Sockets;
using System.Security.Principal;
using System.Text;
using Weave.Windows.Core;

if (Environment.GetEnvironmentVariable("WEAVE_ISOLATED_SERVICE_TEST") != "1" || args is not [var mode, var servicePath] ||
    mode is not ("check" or "reject" or "crash")) return 2;
using var identity = WindowsIdentity.GetCurrent();
if (new WindowsPrincipal(identity).IsInRole(WindowsBuiltInRole.Administrator)) return 3;
using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(60));
try
{
    if (mode == "reject")
    {
        try { await using var invalid = await BrokerClient.ConnectAsync(servicePath, timeout.Token); }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException) { return 0; }
        return 4;
    }
    using var listener = new TcpListener(IPAddress.Loopback, 0);
    listener.Start();
    var server = ServeAsync(listener, timeout.Token);
    using var importer = new SubscriptionImporter();
    var port = ((IPEndPoint)listener.LocalEndpoint).Port;
    var subscription = importer.ImportText("service-fixture", "inline://service-fixture",
        $"proxies: [{{name: fixture, type: http, server: 127.0.0.1, port: {port}, username: fixture-user, password: fixture-password}}]");
    await using var client = await BrokerClient.ConnectAsync(servicePath, timeout.Token);
    if (await client.CheckReadyAsync(timeout.Token)) return 5;
    var profile = BrokerProfile.FromLocal([subscription], [], subscription.Id, subscription.Nodes[0].Id,
        new() { EnableTun = true, RoutingMode = RoutingMode.Global, Ipv6Enabled = false });
    await client.StartAsync(profile, timeout.Token);
    using var http = new HttpClient(new SocketsHttpHandler { UseProxy = false });
    if (await http.GetStringAsync("http://198.51.100.42/check", timeout.Token) != "weave-service-upstream") return 6;
    var header = await server;
    if (!header.Contains("Proxy-Authorization: Basic " + Convert.ToBase64String(Encoding.ASCII.GetBytes("fixture-user:fixture-password")), StringComparison.OrdinalIgnoreCase)) return 7;
    Console.WriteLine("CORE_READY");
    if (mode == "crash")
    {
        var ended = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        client.Exited += (_, _) => ended.TrySetResult();
        if (!client.IsReady) ended.TrySetResult();
        await ended.Task.WaitAsync(timeout.Token);
        if (client.IsReady) return 8;
        Console.WriteLine("CRASH_REVOKED");
    }
    else
    {
        await client.StopAsync(timeout.Token);
        if (client.IsReady) return 9;
        await using var next = await BrokerClient.ConnectAsync(servicePath, timeout.Token);
        if (await next.CheckReadyAsync(timeout.Token)) return 10;
        await next.StopAsync(timeout.Token);
        Console.WriteLine("STOP_CONFIRMED_AND_RECONNECTED");
    }
    return 0;
}
catch (Exception error)
{
    Console.Error.WriteLine(error.GetType().Name + ": " + error.Message);
    return 1;
}

static async Task<string> ServeAsync(TcpListener listener, CancellationToken token)
{
    while (true)
    {
        using var connection = await listener.AcceptTcpClientAsync(token);
        await using var stream = connection.GetStream();
        var header = await ReadHeaderAsync(stream, token);
        if (!header.StartsWith("CONNECT 198.51.100.42:80 ", StringComparison.Ordinal)) continue;
        await stream.WriteAsync("HTTP/1.1 200 Connection established\r\n\r\n"u8.ToArray(), token);
        await ReadHeaderAsync(stream, token);
        const string body = "weave-service-upstream";
        await stream.WriteAsync(Encoding.ASCII.GetBytes($"HTTP/1.1 200 OK\r\nContent-Length: {body.Length}\r\nConnection: close\r\n\r\n{body}"), token);
        return header;
    }
}

static async Task<string> ReadHeaderAsync(Stream stream, CancellationToken token)
{
    var bytes = new byte[8192];
    for (var length = 0; length < bytes.Length;)
    {
        if (await stream.ReadAsync(bytes.AsMemory(length, 1), token) == 0) throw new EndOfStreamException();
        length++;
        if (length >= 4 && bytes.AsSpan(length - 4, 4).SequenceEqual("\r\n\r\n"u8)) return Encoding.ASCII.GetString(bytes, 0, length);
    }
    throw new InvalidDataException("Synthetic request header is too large.");
}
