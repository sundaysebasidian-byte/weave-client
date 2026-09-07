using Microsoft.UI.Xaml;

namespace Weave.Windows;

public partial class App : Application
{
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
