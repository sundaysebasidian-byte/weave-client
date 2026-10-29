using System.ComponentModel;
using System.IO.Pipes;
using System.Text.Json;
using Weave.Windows.Core;

namespace Weave.Windows.Service;

internal sealed class BrokerServer : IDisposable
{
    private readonly BrokerInstallation _installation;
    private readonly string _runtimeRoot;
    private readonly NamedPipeServerStream _pipe;

    public BrokerServer(BrokerInstallation installation, string runtimeRoot)
    {
        _installation = installation;
        _runtimeRoot = runtimeRoot;
        RuntimeSession.Recover(runtimeRoot);
        _pipe = BrokerWindowsPipe.CreateServer(BrokerWindowsPipe.PipeName);
    }

    public async Task RunAsync(CancellationToken stopping)
    {
        while (!stopping.IsCancellationRequested)
        {
            await _pipe.WaitForConnectionAsync(stopping).ConfigureAwait(false);
            await using var session = new BrokerSession(_runtimeRoot, _installation.CoreExecutable, _installation.GeoDataDirectory);
            try
            {
                await BrokerWindowsPipe.AuthenticateClientAsync(_pipe, _installation.GuiExecutable, stopping).ConfigureAwait(false);
                await BrokerConnection.RunAsync(_pipe, session, stopping).ConfigureAwait(false);
            }
            catch (OperationCanceledException) when (!stopping.IsCancellationRequested) { }
            catch (Exception error) when (error is IOException or InvalidDataException or JsonException or UnauthorizedAccessException or Win32Exception) { }
            finally
            {
                await session.DisposeAsync().ConfigureAwait(false);
                // If owned files/termination cannot be accounted for, stop this service.
                // Its process job prevents an uncertain child from surviving a restart.
                if (session.CleanupPending) throw new IOException("The previous core session requires recovery.");
                _pipe.Disconnect();
            }
        }
    }

    public void Dispose() => _pipe.Dispose();
}
