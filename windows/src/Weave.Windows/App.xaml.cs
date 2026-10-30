using Microsoft.UI.Xaml;

namespace Weave.Windows;

public partial class App : Application
{
    private FileStream? _instanceLock;
    public static MainWindow? MainWindow { get; private set; }

    public App()
    {
        UnhandledException += (_, args) => RecordStartupFailure(new Exception(args.Message, args.Exception));
        try { InitializeComponent(); }
        catch (Exception error) { RecordStartupFailure(error); throw; }
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        try
        {
            var directory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Weave");
            Directory.CreateDirectory(directory);
            try { _instanceLock = new FileStream(Path.Combine(directory, "desktop-instance.lock"), FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None); }
            catch (IOException) { /* Activate the existing instance below. */ }
            if (_instanceLock is null)
            {
                var current = System.Diagnostics.Process.GetCurrentProcess();
                foreach (var process in System.Diagnostics.Process.GetProcessesByName(current.ProcessName))
                {
                    using (process)
                    {
                        try
                        {
                            if (process.Id == current.Id || process.SessionId != current.SessionId) continue;
                            if (WindowActivation.TryShow(process.Id)) break;
                        }
                        catch (Exception error) when (error is InvalidOperationException or System.ComponentModel.Win32Exception)
                        { /* Another instance may exit while its window is being found. */ }
                    }
                }
                Exit();
                return;
            }
            AppDomain.CurrentDomain.ProcessExit += (_, _) => _instanceLock?.Dispose();
            MainWindow = new MainWindow();
            MainWindow.Activate();
        }
        catch (Exception error) { RecordStartupFailure(error); throw; }
    }

    private static void RecordStartupFailure(Exception error)
    {
        // Opt-in build diagnostic only; normal users do not create a crash report.
        var path = Environment.GetEnvironmentVariable("WEAVE_STARTUP_DIAGNOSTIC");
        if (!string.IsNullOrEmpty(path)) File.AppendAllText(path, error + Environment.NewLine);
    }
}
