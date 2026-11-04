using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;

namespace Weave.Windows.Core;

/// <summary>Proves forwarding through the core to this session's loopback responder.</summary>
internal sealed class CoreReadinessProbe : IAsyncDisposable
{
    private readonly TcpListener _listener;
    private readonly byte[] _key = RandomNumberGenerator.GetBytes(32);
    private readonly CancellationTokenSource _stopping = new();
    private readonly Task _serving;

    public CoreReadinessProbe(int targetPort)
    {
        if (targetPort is < 1 or > 65535) throw new InvalidDataException("Missing session readiness endpoint.");
        _listener = new(IPAddress.Loopback, targetPort) { ExclusiveAddressUse = true };
        try { _listener.Start(); }
        catch
        {
            _listener.Stop();
            CryptographicOperations.ZeroMemory(_key);
            _stopping.Dispose();
            throw;
        }
        _serving = ServeAsync();
    }

    public async Task<bool> IsForwardingAsync(int listenPort, CancellationToken token)
    {
        if (_serving.IsCompleted) throw new IOException("The session readiness responder stopped.");
        using var deadline = CancellationTokenSource.CreateLinkedTokenSource(token);
        deadline.CancelAfter(TimeSpan.FromSeconds(1));
        using var connection = new TcpClient { NoDelay = true };
        try
        {
            await connection.ConnectAsync(IPAddress.Loopback, listenPort, deadline.Token).ConfigureAwait(false);
            var challenge = RandomNumberGenerator.GetBytes(32);
            var response = new byte[32];
            var stream = connection.GetStream();
            await stream.WriteAsync(challenge, deadline.Token).ConfigureAwait(false);
            await stream.ReadExactlyAsync(response, deadline.Token).ConfigureAwait(false);
            return CryptographicOperations.FixedTimeEquals(response, HMACSHA256.HashData(_key, challenge));
        }
        catch (Exception error) when (error is IOException or SocketException or OperationCanceledException)
        {
            token.ThrowIfCancellationRequested();
            return false;
        }
    }

    private async Task ServeAsync()
    {
        try
        {
            while (!_stopping.IsCancellationRequested)
            {
                using var connection = await _listener.AcceptTcpClientAsync(_stopping.Token).ConfigureAwait(false);
                using var deadline = CancellationTokenSource.CreateLinkedTokenSource(_stopping.Token);
                deadline.CancelAfter(TimeSpan.FromSeconds(1));
                try
                {
                    var stream = connection.GetStream();
                    var challenge = new byte[32];
                    await stream.ReadExactlyAsync(challenge, deadline.Token).ConfigureAwait(false);
                    await stream.WriteAsync(HMACSHA256.HashData(_key, challenge), deadline.Token).ConfigureAwait(false);
                }
                catch (Exception error) when (error is IOException or SocketException or OperationCanceledException) { }
            }
        }
        catch (Exception error) when (_stopping.IsCancellationRequested && error is OperationCanceledException or SocketException or ObjectDisposedException) { }
    }

    public async ValueTask DisposeAsync()
    {
        _stopping.Cancel();
        _listener.Stop();
        await _serving.ConfigureAwait(false);
        CryptographicOperations.ZeroMemory(_key);
        _stopping.Dispose();
    }
}
