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

    protected override async void OnLaunched(LaunchActivatedEventArgs args)
    {
        try
        {
            var directory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Weave");
            Directory.CreateDirectory(directory);
            var restarting = Environment.GetCommandLineArgs().Contains("--elevated-restart");
            for (var attempt = 0; attempt < (restarting ? 100 : 1); attempt++)
            {
                try { _instanceLock = new FileStream(Path.Combine(directory, "desktop-instance.lock"), FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None); break; }
                catch (IOException) { if (restarting) await Task.Delay(150); }
            }
            if (_instanceLock is null)
            {
                var current = System.Diagnostics.Process.GetCurrentProcess();
                foreach (var process in System.Diagnostics.Process.GetProcessesByName(current.ProcessName))
                {
                    using (process)
                    {
                        if (process.Id == current.Id || process.MainWindowHandle == IntPtr.Zero) continue;
                        ShowWindow(process.MainWindowHandle, 9); SetForegroundWindow(process.MainWindowHandle); break;
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

    [System.Runtime.InteropServices.DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr window, int command);
    [System.Runtime.InteropServices.DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr window);

    private static void RecordStartupFailure(Exception error)
    {
        // Opt-in build diagnostic only; normal users do not create a crash report.
        var path = Environment.GetEnvironmentVariable("WEAVE_STARTUP_DIAGNOSTIC");
        if (!string.IsNullOrEmpty(path)) File.AppendAllText(path, error + Environment.NewLine);
    }
}
