using System.Collections.ObjectModel;
using Weave.Windows.Core;

namespace Weave.Windows;

internal sealed class WeaveAppModel : IAsyncDisposable
{
    private readonly string _dataDirectory =
        Environment.GetEnvironmentVariable("WEAVE_DATA_DIR") is { Length: > 0 } overrideDirectory
            ? overrideDirectory
            : Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Weave");
    private readonly SubscriptionVault _vault;
    private readonly AppRouteStore _routeStore;
    private readonly NetworkOptionsStore _optionsStore;
    private readonly ConnectionPreferenceStore _preferenceStore;
    private readonly SubscriptionImporter _importer = new();
    private readonly MihomoConfigBuilder _configBuilder = new();
    private readonly object _trafficGate = new();
    private MihomoProcess? _process;
    private string? _activeRuntimeDirectory;
    private RuntimeBundle? _activeBundle;
    private IReadOnlyDictionary<string, SubscriptionRecord>? _loadedSubscriptions;
    private CancellationTokenSource? _trafficCancellation;
    private Task? _trafficTask;
    private TrafficSnapshot? _traffic;

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
        _preferenceStore = new ConnectionPreferenceStore(
            Path.Combine(_dataDirectory, "connection-preference.bin"),
            new WindowsDpapiProtector());
    }

    public string DataDirectory => _dataDirectory;

    public static string? MihomoPath => FindMihomo();

    public ConnectionPreference Preference { get; private set; } = new(null, null);

    public ObservableCollection<SubscriptionRecord> Subscriptions { get; } = new();

    public ObservableCollection<WindowsAppRoute> AppRoutes { get; } = new();

    public WindowsNetworkOptions NetworkOptions { get; private set; } = new();

    public bool IsConnected => _process?.IsRunning == true;

    public string Status { get; private set; } = "未连接";

    public TrafficSnapshot? Traffic => Volatile.Read(ref _traffic);

    public bool CanProbe(SubscriptionRecord subscription, ProxyNode node)
    {
        return CanProbeSubscription(subscription) &&
               subscription.Nodes.Any(candidate => candidate.Id == node.Id);
    }

    public bool CanProbeSubscription(SubscriptionRecord subscription)
    {
        if (!IsConnected || _activeBundle is null || _loadedSubscriptions is not { } loadedSubscriptions)
            return false;
        return subscription.Nodes.Count > 0 &&
               loadedSubscriptions.TryGetValue(subscription.Id, out var loaded) &&
               ReferenceEquals(loaded, subscription);
    }

    public event EventHandler? StatusChanged;

    public void Load()
    {
        NetworkOptions = _optionsStore.Load();
        Preference = _preferenceStore.Load();
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
        IReadOnlyDictionary<string, SubscriptionRecord> loadedSubscriptions;
        try
        {
            bundle = _configBuilder.Build(
                Subscriptions,
                AppRoutes,
                subscriptionId,
                nodeId,
                options,
                runtime);
            loadedSubscriptions = Subscriptions.ToDictionary(item => item.Id, StringComparer.Ordinal);
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
            _activeBundle = bundle;
            _loadedSubscriptions = loadedSubscriptions;
            SetStatus("核心运行中 · TUN 已请求");
            StartTrafficMonitor(process, bundle);
        }
        catch
        {
            if (ReferenceEquals(_process, process))
            {
                _process = null;
                _activeRuntimeDirectory = null;
                _activeBundle = null;
                _loadedSubscriptions = null;
            }
            var trafficMonitor = DetachTrafficMonitor();
            await CompleteTrafficMonitorAsync(trafficMonitor).ConfigureAwait(false);
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
        _activeBundle = null;
        _loadedSubscriptions = null;
        var trafficMonitor = DetachTrafficMonitor();
        await CompleteTrafficMonitorAsync(trafficMonitor).ConfigureAwait(false);
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

    public async Task<NodeHealthSnapshot> ProbeNodeAsync(
        SubscriptionRecord subscription,
        ProxyNode node,
        CancellationToken cancellationToken)
    {
        if (!CanProbe(subscription, node) || _activeBundle is not { } bundle)
            throw new InvalidDataException("该节点未被当前运行配置加载；请重新连接后测试");

        CancellationToken connectionToken;
        lock (_trafficGate)
        {
            connectionToken = _trafficCancellation?.Token ?? CancellationToken.None;
        }
        using var linked = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken, connectionToken);
        using var client = new MihomoControllerClient(bundle.ControlPort, bundle.ControlSecret);
        return await client.ProbeNodeAsync(
            MihomoConfigBuilder.ProviderName(subscription), node, linked.Token).ConfigureAwait(false);
    }

    public async Task<IReadOnlyList<NodeQualityRow>> ProbeSubscriptionAsync(
        SubscriptionRecord subscription,
        IProgress<int>? progress,
        CancellationToken cancellationToken)
    {
        if (!CanProbeSubscription(subscription) || _activeBundle is not { } bundle)
            throw new InvalidDataException("该订阅未被当前运行配置加载；请重新连接后测试");

        CancellationToken connectionToken;
        lock (_trafficGate)
        {
            connectionToken = _trafficCancellation?.Token ?? CancellationToken.None;
        }
        using var linked = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken, connectionToken);
        using var client = new MihomoControllerClient(bundle.ControlPort, bundle.ControlSecret);
        var snapshots = await client.ProbeSubscriptionAsync(
            MihomoConfigBuilder.ProviderName(subscription), subscription.Nodes, progress, linked.Token).ConfigureAwait(false);
        return NodeQualityMatrix.Build(snapshots);
    }

    public async ValueTask DisposeAsync() => await DisconnectAsync().ConfigureAwait(false);

    public void SaveNetworkOptions(WindowsNetworkOptions options)
    {
        MihomoConfigBuilder.ValidateOptions(options);
        _optionsStore.Save(options);
        NetworkOptions = options;
    }

    public void SavePreference(string? subscriptionId, string? nodeId)
    {
        var preference = new ConnectionPreference(subscriptionId, nodeId);
        if (preference == Preference) return;
        _preferenceStore.Save(preference);
        Preference = preference;
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
        _activeBundle = null;
        _loadedSubscriptions = null;
        var trafficMonitor = DetachTrafficMonitor();
        SetStatus("核心已停止");
        _ = CleanupExitedProcessAsync(process, runtime, trafficMonitor);
    }

    private async Task CleanupExitedProcessAsync(
        MihomoProcess process,
        string? runtime,
        (CancellationTokenSource? Cancellation, Task? Task) trafficMonitor)
    {
        try
        {
            await CompleteTrafficMonitorAsync(trafficMonitor).ConfigureAwait(false);
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

    private void StartTrafficMonitor(MihomoProcess process, RuntimeBundle bundle)
    {
        var cancellation = new CancellationTokenSource();
        lock (_trafficGate)
        {
            if (!ReferenceEquals(_process, process) || !process.IsRunning)
            {
                cancellation.Dispose();
                return;
            }
            _trafficCancellation = cancellation;
            _trafficTask = ObserveTrafficAsync(process, bundle, cancellation.Token);
        }
    }

    private async Task ObserveTrafficAsync(
        MihomoProcess process,
        RuntimeBundle bundle,
        CancellationToken cancellationToken)
    {
        using var client = new MihomoControllerClient(bundle.ControlPort, bundle.ControlSecret);
        while (!cancellationToken.IsCancellationRequested && process.IsRunning)
        {
            try
            {
                await foreach (var snapshot in client.WatchTrafficAsync(cancellationToken).ConfigureAwait(false))
                {
                    if (ReferenceEquals(_process, process)) SetTraffic(snapshot);
                }
            }
            catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
            {
                break;
            }
            catch (Exception)
            {
                if (ReferenceEquals(_process, process)) SetTraffic(null);
            }

            if (cancellationToken.IsCancellationRequested || !process.IsRunning) break;
            try
            {
                await Task.Delay(TimeSpan.FromSeconds(2), cancellationToken).ConfigureAwait(false);
            }
            catch (OperationCanceledException)
            {
                break;
            }
        }
    }

    private (CancellationTokenSource? Cancellation, Task? Task) DetachTrafficMonitor()
    {
        CancellationTokenSource? cancellation;
        Task? task;
        lock (_trafficGate)
        {
            cancellation = _trafficCancellation;
            task = _trafficTask;
            _trafficCancellation = null;
            _trafficTask = null;
        }
        cancellation?.Cancel();
        SetTraffic(null);
        return (cancellation, task);
    }

    private static async Task CompleteTrafficMonitorAsync(
        (CancellationTokenSource? Cancellation, Task? Task) monitor)
    {
        try
        {
            if (monitor.Task is not null) await monitor.Task.ConfigureAwait(false);
        }
        catch (Exception)
        {
            // Monitoring failures must not prevent the core from stopping and cleaning up.
        }
        finally
        {
            monitor.Cancellation?.Dispose();
        }
    }

    private void SetTraffic(TrafficSnapshot? snapshot)
    {
        Volatile.Write(ref _traffic, snapshot);
        StatusChanged?.Invoke(this, EventArgs.Empty);
    }

    private static void DeleteRuntime(string runtime)
    {
        if (Directory.Exists(runtime))
        {
            Directory.Delete(runtime, recursive: true);
        }
    }
}
