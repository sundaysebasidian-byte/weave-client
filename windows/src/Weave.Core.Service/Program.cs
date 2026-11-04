using System.Diagnostics;
using System.Security.Principal;
using System.ServiceProcess;
using Weave.Windows.Core;

namespace Weave.Windows.Service;

internal static class Program
{
    // Keep the only job handle in the service. The OS closes it on service exit,
    // killing inherited children even if the service crashes between Start and child bookkeeping.
    private static ChildProcessLifetime? _lifetime;

    public static int Main(string[] args)
    {
        if (args.Length != 0) return 2;
        using var identity = WindowsIdentity.GetCurrent();
        if (identity.User?.IsWellKnown(WellKnownSidType.LocalSystemSid) != true) return 5;
        using var process = Process.GetCurrentProcess();
        _lifetime = new(process);
        ServiceProcessAccess.AllowImageQuery(process.SafeHandle);
        using var service = new CoreService();
        ServiceBase.Run(service);
        GC.KeepAlive(_lifetime);
        return service.ExitCode;
    }
}

internal sealed class CoreService : ServiceBase
{
    private readonly CancellationTokenSource _stopping = new();
    private BrokerServer? _server;
    private Task? _running;
    private int _stopStarted;

    public CoreService()
    {
        ServiceName = BrokerWindowsPipe.ServiceName;
        CanStop = true;
        CanShutdown = true;
        AutoLog = true;
    }

    protected override void OnStart(string[] args)
    {
        var root = Directory.GetParent(AppContext.BaseDirectory.TrimEnd(Path.DirectorySeparatorChar))?.FullName
            ?? throw new InvalidOperationException("The service installation is incomplete.");
        _server = new(BrokerInstallation.Inspect(root), BrokerInstallation.PrepareRuntimeRoot());
        _running = _server.RunAsync(_stopping.Token);
        _ = _running.ContinueWith(_ => { ExitCode = 1; Stop(); }, CancellationToken.None,
            TaskContinuationOptions.OnlyOnFaulted, TaskScheduler.Default);
    }

    protected override void OnStop()
    {
        if (Interlocked.Exchange(ref _stopStarted, 1) != 0) return;
        RequestAdditionalTime(20_000);
        _stopping.Cancel();
        try { _running?.WaitAsync(TimeSpan.FromSeconds(15)).GetAwaiter().GetResult(); }
        catch (System.TimeoutException) { Environment.Exit(1); } // OS closes the job before SCM observes exit.
        catch (OperationCanceledException) when (_stopping.IsCancellationRequested) { }
        catch { ExitCode = 1; }
        finally { _server?.Dispose(); }
    }

    protected override void OnShutdown() => OnStop();

    protected override void Dispose(bool disposing)
    {
        if (disposing) { _server?.Dispose(); _stopping.Dispose(); }
        base.Dispose(disposing);
    }
}
