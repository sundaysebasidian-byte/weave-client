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
    private readonly SubscriptionImporter _importer = new();
    private readonly MihomoConfigBuilder _configBuilder = new();
    private MihomoProcess? _process;
    private readonly SemaphoreSlim _connectionGate = new(1, 1);
    public event EventHandler? StatusChanged;
    public WindowsNetworkOptions NetworkOptions { get; set; } = new();

    public WeaveAppModel()
    {
        _vault = new SubscriptionVault(
            Path.Combine(_dataDirectory, "subscriptions.bin"),
            new WindowsDpapiProtector());
        _routeStore = new AppRouteStore(
            Path.Combine(_dataDirectory, "app-routes.bin"),
            new WindowsDpapiProtector());
    }

    public ObservableCollection<SubscriptionRecord> Subscriptions { get; } = new();

    public ObservableCollection<WindowsAppRoute> AppRoutes { get; } = new();

    public bool IsConnected => _process?.IsRunning == true;

    public string Status { get; private set; } = "未连接";

    public void Load()
    {
        Subscriptions.Clear();
        foreach (var subscription in _vault.List())
        {
            Subscriptions.Add(subscription);
        }

        foreach (var route in _routeStore.Load())
        {
            AppRoutes.Add(route);
        }

        Status = Subscriptions.Count == 0 ? "请先导入订阅" : "未连接";
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
        var record = await _importer.ImportUrlAsync(name, source, cancellationToken);
        _vault.Upsert(record);
        ReplaceInCollection(record);
        return record;
    }

    public SubscriptionRecord ImportFile(string name, string path)
    {
        var record = _importer.ImportFile(name, path);
        _vault.Upsert(record);
        ReplaceInCollection(record);
        return record;
    }

    public bool Remove(string id)
    {
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

    public async Task ConnectAsync(string subscriptionId, string? nodeId, CancellationToken cancellationToken)
    {
        await _connectionGate.WaitAsync(cancellationToken);
        try
        {
        if (_process is not null)
        {
            if (_process.IsRunning) return;
            await _process.DisposeAsync();
            _process = null;
        }

        using var identity = System.Security.Principal.WindowsIdentity.GetCurrent();
        if (!new System.Security.Principal.WindowsPrincipal(identity).IsInRole(System.Security.Principal.WindowsBuiltInRole.Administrator))
            throw new InvalidOperationException("TUN 需要管理员权限。请退出 Weave，右键应用选择“以管理员身份运行”。");

        var executable = FindMihomo();
        if (executable is null)
        {
            throw new FileNotFoundException("未找到 mihomo.exe。请将它放到 Windows 发行包的 runtime 目录，或设置 WEAVE_MIHOMO_PATH。");
        }

        var runtime = Path.Combine(_dataDirectory, "runtime", Guid.NewGuid().ToString("N"));
        var bundle = _configBuilder.Build(
            Subscriptions,
            AppRoutes,
            subscriptionId,
            nodeId,
            NetworkOptions,
            runtime);
        var process = new MihomoProcess(executable);
        process.Exited += (_, _) =>
        {
            if (ReferenceEquals(_process, process))
            {
                Status = "核心已停止";
                StatusChanged?.Invoke(this, EventArgs.Empty);
            }
        };
        try
        {
        var validation = await process.ValidateConfigAsync(bundle, cancellationToken).ConfigureAwait(false);
        if (!validation.IsValid)
        {
            await process.DisposeAsync().ConfigureAwait(false);
            throw new InvalidDataException($"Mihomo 配置校验失败：{validation.Diagnostics}");
        }

        Status = "正在启动 TUN";
        _process = process;
        await process.StartAsync(bundle, cancellationToken).ConfigureAwait(false);
        if (!process.IsRunning) throw new InvalidOperationException("核心在启动时退出，请检查权限及配置。");
        Status = "已连接 · Mihomo TUN";
        StatusChanged?.Invoke(this, EventArgs.Empty);
        }
        catch
        {
            _process = null;
            await process.DisposeAsync().ConfigureAwait(false);
            Status = "启动失败";
            StatusChanged?.Invoke(this, EventArgs.Empty);
            throw;
        }
        }
        finally { _connectionGate.Release(); }
    }

    public async Task DisconnectAsync()
    {
        await _connectionGate.WaitAsync();
        try
        {
        var process = _process;
        _process = null;
        if (process is not null)
        {
            await process.DisposeAsync().ConfigureAwait(false);
        }

        Status = "未连接";
        StatusChanged?.Invoke(this, EventArgs.Empty);
        }
        finally { _connectionGate.Release(); }
    }

    public async ValueTask DisposeAsync() => await DisconnectAsync().ConfigureAwait(false);

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
}
