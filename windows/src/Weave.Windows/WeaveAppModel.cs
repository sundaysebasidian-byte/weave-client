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
    private readonly WindowsSystemProxy _systemProxy;
    private MihomoProcess? _process;
    public RuntimeBundle? ActiveBundle { get; private set; }
    private readonly SemaphoreSlim _connectionGate = new(1, 1);
    public event EventHandler? StatusChanged;
    public WindowsNetworkOptions NetworkOptions { get; set; } = new();

    public WeaveAppModel()
    {
        _systemProxy = new WindowsSystemProxy(_dataDirectory);
        _vault = new SubscriptionVault(
            Path.Combine(_dataDirectory, "subscriptions.bin"),
            new WindowsDpapiProtector());
        _routeStore = new AppRouteStore(
            Path.Combine(_dataDirectory, "app-routes.bin"),
            new WindowsDpapiProtector());
    }

    public ObservableCollection<SubscriptionRecord> Subscriptions { get; } = new();

    public ObservableCollection<WindowsAppRoute> AppRoutes { get; } = new();

    public bool IsConnected => _process?.IsReady == true;

    public string Status { get; private set; } = "未连接";

    public void Load()
    {
        _systemProxy.Recover();
        Subscriptions.Clear();
        AppRoutes.Clear();
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

    public async Task<SubscriptionRecord> ImportTextAsync(string name, string source, string payload)
    {
        var record = await Task.Run(() => _importer.ImportText(name, source, payload));
        record = PreserveIdentity(record);
        await Task.Run(() => _vault.Upsert(record));
        ReplaceInCollection(record);
        return record;
    }

    public async Task<SubscriptionRecord> ImportUrlAsync(string name, string source, CancellationToken cancellationToken)
    {
        var record = await _importer.ImportUrlAsync(name, source, cancellationToken);
        record = PreserveIdentity(record);
        await Task.Run(() => _vault.Upsert(record), cancellationToken);
        ReplaceInCollection(record);
        return record;
    }

    public async Task<SubscriptionRecord> ImportFileAsync(string name, string path)
    {
        var record = await Task.Run(() => _importer.ImportFile(name, path));
        record = PreserveIdentity(record);
        await Task.Run(() => _vault.Upsert(record));
        ReplaceInCollection(record);
        return record;
    }

    public async Task ImportTransferAsync(IReadOnlyList<TransferSubscription> items)
    {
        // Parse every selected subscription before the single atomic vault write.
        var parsed = await Task.Run(() => items.Select(item => _importer.ImportText(item.Name, item.Source, item.Payload)).ToArray());
        var merged = parsed.Select(PreserveIdentity).ToArray();
        if (merged.Select(item => item.Id).Distinct().Count() != merged.Length)
            throw new InvalidDataException("传输包包含重复订阅，请在发送端分别选择");
        await Task.Run(() => _vault.Merge(merged));
        foreach (var record in merged) ReplaceInCollection(record);
    }

    public bool Remove(string id)
    {
        if (AppRoutes.Any(route => route.Target.SubscriptionId == id))
            throw new InvalidOperationException("请先删除引用此订阅的应用分流，避免应用意外改走其他出口。");
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

    public async Task EditAsync(SubscriptionRecord existing, string name, string source, CancellationToken token)
    {
        var parsed = source.StartsWith("https://", StringComparison.OrdinalIgnoreCase)
            ? await _importer.ImportUrlAsync(name, source, token)
            : await Task.Run(() => _importer.ImportText(name, existing.Source, existing.ProviderYaml), token);
        var nodes = parsed.Nodes.Select(node => new ProxyNode
        {
            Id = existing.Nodes.FirstOrDefault(old => old.RawName == node.RawName && old.Protocol == node.Protocol)?.Id ?? node.Id,
            Name = node.Name, RawName = node.RawName, Protocol = node.Protocol, Index = node.Index,
        }).ToList();
        var updated = new SubscriptionRecord { Id = existing.Id, Name = parsed.Name, Source = parsed.Source,
            Payload = parsed.Payload, ProviderYaml = parsed.ProviderYaml, Nodes = nodes };
        await Task.Run(() => _vault.Upsert(updated), token);
        ReplaceInCollection(updated);
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
            if (_process.IsReady) return;
            await _process.DisposeAsync();
            _process = null;
            CleanupRuntime();
        }

        using var identity = System.Security.Principal.WindowsIdentity.GetCurrent();
        if (NetworkOptions.EnableTun && !new System.Security.Principal.WindowsPrincipal(identity).IsInRole(System.Security.Principal.WindowsBuiltInRole.Administrator))
            throw new InvalidOperationException("TUN 需要管理员权限。请退出 Weave，右键应用选择“以管理员身份运行”。");

        var executable = FindMihomo();
        if (executable is null)
        {
            throw new FileNotFoundException("未找到 mihomo.exe。请将它放到 Windows 发行包的 runtime 目录，或设置 WEAVE_MIHOMO_PATH。");
        }

        var runtime = Path.Combine(_dataDirectory, "runtime", Guid.NewGuid().ToString("N"));
        var subscriptions = Subscriptions.ToArray();
        var routes = AppRoutes.ToArray();
        var options = NetworkOptions;
        RuntimeBundle bundle;
        try { bundle = await Task.Run(() => _configBuilder.Build(
            subscriptions,
            routes,
            subscriptionId,
            nodeId,
            options,
            runtime), cancellationToken); }
        catch { RemoveSessionDirectory(runtime); throw; }
        ActiveBundle = bundle;
        var process = new MihomoProcess(executable);
        process.Exited += (_, _) =>
        {
            if (ReferenceEquals(_process, process))
            {
                Status = "核心已停止";
                try { _systemProxy.Recover(); } catch { Status = "核心已停止；系统代理恢复失败，请在 Windows 设置中检查"; }
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

        Status = options.EnableTun ? "正在启动 TUN" : "正在启动系统代理";
        _process = process;
        await process.StartAsync(bundle, cancellationToken).ConfigureAwait(false);
        if (!process.IsReady) throw new InvalidOperationException("核心在启动时退出，请检查权限及配置。");
        if (!options.EnableTun) _systemProxy.Enable(bundle.MixedPort);
        if (!process.IsReady) throw new InvalidOperationException("内核在应用系统设置时退出，已取消连接");
        Status = options.RoutingMode == RoutingMode.Direct ? "直连 · 不经过代理节点" : options.EnableTun ? "已连接 · TUN" : "已连接 · 系统代理";
        StatusChanged?.Invoke(this, EventArgs.Empty);
        }
        catch
        {
            _process = null;
            try { _systemProxy.Recover(); } catch { /* Recovery record is retained for the next launch. */ }
            await process.DisposeAsync().ConfigureAwait(false);
            CleanupRuntime();
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
        Exception? recoveryError = null;
        try { _systemProxy.Recover(); } catch (Exception error) { recoveryError = error; }
        try
        {
            if (process is not null) await process.DisposeAsync().ConfigureAwait(false);
        }
        finally { CleanupRuntime(); }

        Status = "未连接";
        StatusChanged?.Invoke(this, EventArgs.Empty);
        if (recoveryError is not null) throw new IOException("内核已停止，但系统代理恢复失败；请检查 Windows 代理设置。恢复记录已保留。", recoveryError);
        }
        finally { _connectionGate.Release(); }
    }

    public async ValueTask DisposeAsync()
    {
        await DisconnectAsync().ConfigureAwait(false);
        _importer.Dispose();
    }

    private void CleanupRuntime()
    {
        var bundle = ActiveBundle;
        ActiveBundle = null;
        if (bundle is not null) RemoveSessionDirectory(bundle.Directory);
    }

    private void RemoveSessionDirectory(string path)
    {
        var root = Path.GetFullPath(Path.Combine(_dataDirectory, "runtime"));
        // Only our own single GUID session, never a subscription-controlled path.
        if (Path.GetDirectoryName(Path.GetFullPath(path)) != root ||
            !Guid.TryParseExact(Path.GetFileName(path), "N", out _)) return;
        try { if (Directory.Exists(path)) Directory.Delete(path, recursive: true); }
        catch (IOException) { }
        catch (UnauthorizedAccessException) { }
    }

    private SubscriptionRecord PreserveIdentity(SubscriptionRecord record)
    {
        var old = Subscriptions.FirstOrDefault(item => item.Id == record.Id || (item.Source == record.Source &&
            (record.Source.StartsWith("https://", StringComparison.OrdinalIgnoreCase) || item.Name == record.Name)));
        if (old is null) return record;
        var nodes = record.Nodes.Select(node =>
        {
            var previous = old.Nodes.FirstOrDefault(item => item.RawName == node.RawName && item.Protocol == node.Protocol);
            return new ProxyNode { Id = previous?.Id ?? node.Id, Name = node.Name, RawName = node.RawName,
                Protocol = node.Protocol, Index = node.Index };
        }).ToList();
        return new SubscriptionRecord { Id = old.Id, Name = record.Name, Source = record.Source, Payload = record.Payload,
            ProviderYaml = record.ProviderYaml, Nodes = nodes, UpdatedAt = record.UpdatedAt };
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
}
