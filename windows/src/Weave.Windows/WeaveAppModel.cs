using System.Collections.ObjectModel;
using Weave.Windows.Core;

namespace Weave.Windows;

internal sealed class WeaveAppModel : IAsyncDisposable
{
    private readonly string _dataDirectory = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "Weave");
    private readonly SubscriptionVault _vault;
    private readonly AppRouteStore _routeStore;
    private readonly NetworkOptionsStore _optionsStore;
    private readonly SubscriptionImporter _importer = new();
    private readonly MihomoConfigBuilder _configBuilder = new();
    private MihomoProcess? _process;
    private string? _activeRuntimeDirectory;

    public WeaveAppModel()
    {
        _vault = new SubscriptionVault(
            Path.Combine(_dataDirectory, "subscriptions.bin"),
            new WindowsDpapiProtector());
        _routeStore = new AppRouteStore(
            Path.Combine(_dataDirectory, "app-routes.bin"),
            new WindowsDpapiProtector());
        _optionsStore = new NetworkOptionsStore(
            Path.Combine(_dataDirectory, "network-options.bin"),
            new WindowsDpapiProtector());
    }

    public ObservableCollection<SubscriptionRecord> Subscriptions { get; } = new();

    public ObservableCollection<WindowsAppRoute> AppRoutes { get; } = new();

    public WindowsNetworkOptions NetworkOptions { get; private set; } = new();

    public bool IsConnected => _process?.IsRunning == true;

    public string Status { get; private set; } = "未连接";

    public event EventHandler? StatusChanged;

    public void Load()
    {
        NetworkOptions = _optionsStore.Load();
        Subscriptions.Clear();
        foreach (var subscription in _vault.List())
        {
            Subscriptions.Add(subscription);
        }

        AppRoutes.Clear();
        foreach (var route in _routeStore.Load())
        {
            AppRoutes.Add(route);
        }

        SetStatus(Subscriptions.Count == 0 ? "请先导入订阅" : "未连接");
    }

    public SubscriptionRecord ImportText(string name, string source, string payload)
    {
        var record = _importer.ImportText(name, source, payload);
        _vault.Upsert(record);
        ReplaceInCollection(record);
        return record;
    }

    public async Task<SubscriptionRecord> ImportUrlAsync(string name, string source, CancellationToken cancellationToken)
    {
        var candidate = await _importer.ImportUrlAsync(name, source, cancellationToken);
        var old = Subscriptions.FirstOrDefault(item => item.Source == candidate.Source);
        var record = old is null
            ? candidate
            : SubscriptionUpdateGuard.Prepare(old, candidate, AppRoutes).Record;
        _vault.Upsert(record);
        ReplaceInCollection(record);
        return record;
    }

    public async Task<SubscriptionUpdate> RefreshAsync(SubscriptionRecord current, CancellationToken cancellationToken)
    {
        if (!Uri.TryCreate(current.Source, UriKind.Absolute, out var source) ||
            source.Scheme != Uri.UriSchemeHttps)
        {
            throw new InvalidDataException("只有 HTTPS 订阅可以刷新");
        }

        var candidate = await _importer.ImportUrlAsync(current.Name, current.Source, cancellationToken);
        var update = SubscriptionUpdateGuard.Prepare(current, candidate, AppRoutes);
        _vault.Upsert(update.Record);
        ReplaceInCollection(update.Record);
        return update;
    }

    public SubscriptionRecord ImportFile(string name, string path)
    {
        var candidate = _importer.ImportFile(name, path);
        var old = Subscriptions.FirstOrDefault(item => item.Source == candidate.Source);
        var record = old is null
            ? candidate
            : SubscriptionUpdateGuard.Prepare(old, candidate, AppRoutes).Record;
        _vault.Upsert(record);
        ReplaceInCollection(record);
        return record;
    }

    public bool Remove(string id)
    {
        if (AppRoutes.Any(route => route.Target.SubscriptionId == id))
        {
            throw new InvalidDataException("该订阅仍被应用分流使用，请先删除或修改相关规则");
        }

        var removed = _vault.Remove(id);
        if (removed)
        {
            var item = Subscriptions.FirstOrDefault(subscription => subscription.Id == id);
            if (item is not null)
            {
                Subscriptions.Remove(item);
            }
        }

        return removed;
    }

    public void AddOrReplaceRoute(WindowsAppRoute route)
    {
        var processName = Path.GetFileName(route.ProcessName.Trim());
        if (string.IsNullOrWhiteSpace(processName) || processName.Contains(',') ||
            !processName.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidDataException("请输入不含逗号的 Windows .exe 进程名，例如 chrome.exe");
        }

        var normalized = new WindowsAppRoute
        {
            ProcessName = processName,
            DisplayName = string.IsNullOrWhiteSpace(route.DisplayName) ? processName : route.DisplayName.Trim(),
            Target = route.Target,
        };
        var old = AppRoutes.FirstOrDefault(item =>
            item.ProcessName.Equals(processName, StringComparison.OrdinalIgnoreCase));
        if (old is not null)
        {
            AppRoutes[AppRoutes.IndexOf(old)] = normalized;
        }
        else
        {
            AppRoutes.Add(normalized);
        }

        _routeStore.Save(AppRoutes);
    }

    public bool RemoveRoute(string processName)
    {
        var route = AppRoutes.FirstOrDefault(item =>
            item.ProcessName.Equals(processName, StringComparison.OrdinalIgnoreCase));
        if (route is null)
        {
            return false;
        }

        AppRoutes.Remove(route);
        _routeStore.Save(AppRoutes);
        return true;
    }

    public async Task ConnectAsync(
        string subscriptionId,
        string? nodeId,
        WindowsNetworkOptions options,
        CancellationToken cancellationToken)
    {
        SaveNetworkOptions(options);
        if (_process?.IsRunning == true)
        {
            return;
        }

        if (_process is not null)
        {
            await DisconnectAsync().ConfigureAwait(false);
        }

        if (_activeRuntimeDirectory is { } previousRuntime)
        {
            _activeRuntimeDirectory = null;
            DeleteRuntime(previousRuntime);
        }

        var executable = FindMihomo();
        if (executable is null)
        {
            throw new FileNotFoundException("未找到 mihomo.exe。请将它放到 Windows 发行包的 runtime 目录，或设置 WEAVE_MIHOMO_PATH。");
        }

        var runtime = Path.Combine(_dataDirectory, "runtime", Guid.NewGuid().ToString("N"));
        RuntimeBundle bundle;
        try
        {
            bundle = _configBuilder.Build(
                Subscriptions,
                AppRoutes,
                subscriptionId,
                nodeId,
                options,
                runtime);
        }
        catch
        {
            DeleteRuntime(runtime);
            throw;
        }
        var process = new MihomoProcess(executable);
        process.Exited += (_, _) => HandleProcessExit(process);
        try
        {
            var validation = await process.ValidateConfigAsync(bundle, cancellationToken).ConfigureAwait(false);
            if (!validation.IsValid)
            {
                throw new InvalidDataException($"Mihomo 配置校验失败：{validation.Diagnostics}");
            }

            SetStatus("正在启动 TUN");
            await process.StartAsync(bundle, cancellationToken).ConfigureAwait(false);
            if (!process.IsRunning)
            {
                throw new InvalidOperationException("Mihomo 在启动后立即退出");
            }

            _process = process;
            _activeRuntimeDirectory = runtime;
            SetStatus("核心运行中 · TUN 已请求");
        }
        catch
        {
            await process.DisposeAsync().ConfigureAwait(false);
            DeleteRuntime(runtime);
            SetStatus("未连接");
            throw;
        }
    }

    public async Task DisconnectAsync()
    {
        var process = _process;
        _process = null;
        var runtime = _activeRuntimeDirectory;
        _activeRuntimeDirectory = null;
        if (process is not null)
        {
            await process.DisposeAsync().ConfigureAwait(false);
        }

        if (runtime is not null)
        {
            DeleteRuntime(runtime);
        }

        SetStatus("未连接");
    }

    public async ValueTask DisposeAsync() => await DisconnectAsync().ConfigureAwait(false);

    public void SaveNetworkOptions(WindowsNetworkOptions options)
    {
        MihomoConfigBuilder.ValidateOptions(options);
        _optionsStore.Save(options);
        NetworkOptions = options;
    }

    private static string? FindMihomo()
    {
        var candidates = new[]
        {
            Environment.GetEnvironmentVariable("WEAVE_MIHOMO_PATH"),
            Path.Combine(AppContext.BaseDirectory, "runtime", "mihomo.exe"),
            Path.Combine(AppContext.BaseDirectory, "mihomo.exe"),
        };
        return candidates.FirstOrDefault(path => !string.IsNullOrWhiteSpace(path) && File.Exists(path));
    }

    private void ReplaceInCollection(SubscriptionRecord record)
    {
        var old = Subscriptions.FirstOrDefault(item => item.Id == record.Id);
        if (old is not null)
        {
            var index = Subscriptions.IndexOf(old);
            Subscriptions[index] = record;
        }
        else
        {
            Subscriptions.Add(record);
        }
    }

    private void SetStatus(string status)
    {
        Status = status;
        StatusChanged?.Invoke(this, EventArgs.Empty);
    }

    private void HandleProcessExit(MihomoProcess process)
    {
        if (!ReferenceEquals(_process, process))
        {
            return;
        }

        _process = null;
        var runtime = _activeRuntimeDirectory;
        _activeRuntimeDirectory = null;
        SetStatus("核心已停止");
        _ = CleanupExitedProcessAsync(process, runtime);
    }

    private async Task CleanupExitedProcessAsync(MihomoProcess process, string? runtime)
    {
        try
        {
            await process.DisposeAsync().ConfigureAwait(false);
            if (runtime is not null)
            {
                DeleteRuntime(runtime);
            }
        }
        catch (Exception)
        {
            SetStatus("核心已停止；临时文件清理失败");
        }
    }

    private static void DeleteRuntime(string runtime)
    {
        if (Directory.Exists(runtime))
        {
            Directory.Delete(runtime, recursive: true);
        }
    }
}
