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
    private readonly SubscriptionImporter _importer;
    private readonly MihomoConfigBuilder _configBuilder = new();
    private readonly WindowsSystemProxy _systemProxy;
    private MihomoProcess? _process;
    private BrokerClient? _broker;
    private RuntimeBundle? _activeBundle;
    private RuntimeSession? _runtimeSession;
    private bool _runtimeCleanupPending;
    public ConnectionEndpoint? ActiveConnection { get; private set; }
    public ConnectionHealthState Health { get; private set; }
    private readonly SemaphoreSlim _connectionGate = new(1, 1);
    private long _networkRevision;
    public long NetworkRevision => Interlocked.Read(ref _networkRevision);
    public event EventHandler? StatusChanged;
    public WindowsNetworkOptions NetworkOptions { get; set; } = new();

    public WeaveAppModel()
    {
        _importer = new SubscriptionImporter(resolveHost: (host, token) =>
        {
            var broker = _broker;
            if (broker is not null) return broker.ResolveHostAsync(host, token);
            var process = _process;
            return process is not null
                ? process.ResolveHostAsync(host, token)
                : System.Net.Dns.GetHostAddressesAsync(host, token);
        });
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

    public bool IsConnected => _broker?.IsReady == true || _process?.IsReady == true;

    private string _status = "未连接";
    public string Status { get => L.T(_status) + (_runtimeCleanupPending ? " · " + L.T("会话配置尚未清理") : ""); private set => _status = value; }

    public void Load()
    {
        _systemProxy.Recover();
        _runtimeCleanupPending = RuntimeSession.Recover(Path.Combine(_dataDirectory, "runtime-sessions")).Retained > 0;
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
            throw new InvalidDataException(L.T("传输包包含重复订阅，请在发送端分别选择"));
        await Task.Run(() => _vault.Merge(merged));
        foreach (var record in merged) ReplaceInCollection(record);
    }

    public bool Remove(string id)
    {
        if (AppRoutes.Any(route => route.Target.SubscriptionId == id))
            throw new InvalidOperationException(L.T("请先删除引用此订阅的应用分流，避免应用意外改走其他出口。"));
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

    public async Task<bool> EditAsync(SubscriptionRecord existing, string name, string source, CancellationToken token,
        Func<SubscriptionRecord, SubscriptionRecord, Task<bool>> confirm)
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
        if (!await confirm(existing, updated)) return false;
        token.ThrowIfCancellationRequested();
        await Task.Run(() => _vault.ReplaceReviewed(existing, updated), token);
        ReplaceInCollection(updated);
        return true;
    }

    public void AddOrReplaceRoute(WindowsAppRoute route)
    {
        var processName = Path.GetFileName(route.ProcessName.Trim());
        if (string.IsNullOrWhiteSpace(processName) || processName.Contains(',') ||
            !processName.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidDataException(L.T("请输入不含逗号的 Windows .exe 进程名，例如 chrome.exe"));
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
            if (IsConnected) return;
            await StopCoreAsync().ConfigureAwait(false);
            _systemProxy.Recover();
            var subscriptions = Subscriptions.ToArray();
            var routes = AppRoutes.ToArray();
            var options = NetworkOptions;
            try
            {
                Status = options.EnableTun ? "正在启动 TUN" : "正在启动系统代理";
                if (options.EnableTun)
                {
                    BrokerClient broker;
                    try
                    {
                        var installation = await Task.Run(() => BrokerInstallation.Inspect(AppContext.BaseDirectory), cancellationToken);
                        broker = await BrokerClient.ConnectAsync(installation.ServiceExecutable, cancellationToken).ConfigureAwait(false);
                    }
                    catch (Exception error) when (error is IOException or UnauthorizedAccessException or
                        System.ComponentModel.Win32Exception or TimeoutException)
                    {
                        throw new IOException(L.T("W-S01：无法连接受保护的核心服务。请重新运行 Weave 安装程序，完成服务安装后重试。"), error);
                    }
                    _broker = broker;
                    broker.Exited += (_, _) => { if (ReferenceEquals(_broker, broker)) CoreExited(); };
                    var profile = BrokerProfile.FromLocal(subscriptions, routes, subscriptionId, nodeId, options);
                    BrokerRuntimeInfo runtime;
                    try { runtime = await broker.StartAsync(profile, cancellationToken).ConfigureAwait(false); }
                    catch (BrokerRequestException error)
                    { throw new IOException(L.T("W-S02：核心服务未能启动连接。请检查订阅、路由设置和其他客户端的 TUN 状态。"), error); }
                    ActiveConnection = new(runtime.MixedPort, true, runtime.ProviderNodeCounts);
                }
                else
                {
                    var executable = FindMihomo() ?? throw new FileNotFoundException(L.T("未找到 mihomo.exe。请将它放到 Windows 发行包的 runtime 目录，或设置 WEAVE_MIHOMO_PATH。"));
                    _runtimeSession = RuntimeSession.Reserve(Path.Combine(_dataDirectory, "runtime-sessions"));
                    var bundle = await Task.Run(() => _configBuilder.Build(subscriptions, routes, subscriptionId, nodeId,
                        options, _runtimeSession.DirectoryPath), cancellationToken);
                    _activeBundle = bundle;
                    var process = new MihomoProcess(executable);
                    _process = process;
                    process.Exited += (_, _) => { if (ReferenceEquals(_process, process)) CoreExited(); };
                    var validation = await process.ValidateConfigAsync(bundle, cancellationToken).ConfigureAwait(false);
                    if (!validation.IsValid) throw new InvalidDataException(L.F($"Mihomo 配置校验失败：{validation.Diagnostics}"));
                    await process.StartAsync(bundle, cancellationToken).ConfigureAwait(false);
                    ActiveConnection = new(bundle.MixedPort, false, bundle.ProviderNodeCounts);
                }
                if (!IsConnected) throw new IOException(L.T("核心在启动时退出，请检查权限及配置。"));
                var endpoint = ActiveConnection;
                // HKCU and its encrypted recovery record remain owned by the original user.
                _systemProxy.Enable(endpoint.MixedPort);
                if (!IsConnected) throw new IOException(L.T("内核在应用系统设置时退出，已取消连接"));
                Health = ConnectionHealthState.Checking;
                Status = "正在核验网络";
                StatusChanged?.Invoke(this, EventArgs.Empty);
                var checkedRevision = Interlocked.Read(ref _networkRevision);
                var checkedHealth = await ConnectionHealth.CheckAsync(endpoint, cancellationToken).ConfigureAwait(false);
                Health = checkedRevision == Interlocked.Read(ref _networkRevision) ? checkedHealth : ConnectionHealthState.Unchecked;
                if (!IsConnected) throw new IOException(L.T("核心在启动时退出，请检查权限及配置。"));
                Status = Health != ConnectionHealthState.Reachable ? "内核运行中 · 网络待确认" :
                    options.RoutingMode == RoutingMode.Direct ? "直连 · 不经过代理节点" : options.EnableTun ? "已连接 · TUN + 系统代理" : "已连接 · 系统代理";
                StatusChanged?.Invoke(this, EventArgs.Empty);
            }
            catch
            {
                Health = ConnectionHealthState.Unchecked;
                Status = "启动失败";
                try { _systemProxy.Recover(); }
                catch { Status = "核心已停止；系统代理恢复失败，请在 Windows 设置中检查"; }
                try { await StopCoreAsync().ConfigureAwait(false); }
                catch { Status = "核心停止尚未确认，请检查服务状态"; throw; }
                finally { StatusChanged?.Invoke(this, EventArgs.Empty); }
                throw;
            }
        }
        finally { _connectionGate.Release(); }
    }

    private void CoreExited()
    {
        Interlocked.Increment(ref _networkRevision);
        Health = ConnectionHealthState.Unchecked;
        Status = "核心已停止";
        try { _systemProxy.Recover(); }
        catch { Status = "核心已停止；系统代理恢复失败，请在 Windows 设置中检查"; }
        StatusChanged?.Invoke(this, EventArgs.Empty);
    }

    private async Task StopCoreAsync()
    {
        ActiveConnection = null;
        var broker = _broker;
        if (broker is not null)
        {
            try { if (broker.IsReady) await broker.StopAsync(CancellationToken.None).ConfigureAwait(false); }
            finally
            {
                await broker.DisposeAsync().ConfigureAwait(false);
                _broker = null;
            }
        }
        var process = _process;
        if (process is not null)
        {
            // Retain its handle and files if process termination cannot be confirmed.
            await process.DisposeAsync().ConfigureAwait(false);
            _process = null;
        }
        await CleanupRuntimeAsync().ConfigureAwait(false);
    }

    public async Task DisconnectAsync()
    {
        await _connectionGate.WaitAsync();
        try
        {
            Interlocked.Increment(ref _networkRevision);
            Exception? recoveryError = null;
            try { _systemProxy.Recover(); } catch (Exception error) { recoveryError = error; }
            Health = ConnectionHealthState.Unchecked;
            try
            {
                await StopCoreAsync().ConfigureAwait(false);
                Status = recoveryError is null ? "未连接" : "核心已停止；系统代理恢复失败，请在 Windows 设置中检查";
            }
            catch { Status = "核心停止尚未确认，请检查服务状态"; throw; }
            finally { StatusChanged?.Invoke(this, EventArgs.Empty); }
            if (recoveryError is not null) throw new IOException(L.T("内核已停止，但系统代理恢复失败；请检查 Windows 代理设置。恢复记录已保留。"), recoveryError);
        }
        finally { _connectionGate.Release(); }
    }

    public async Task<int?> ProbeNodeAsync(ConnectionEndpoint expected, string nodeName, CancellationToken token)
    {
        var broker = _broker;
        var bundle = _activeBundle;
        if (!ReferenceEquals(expected, ActiveConnection) || !IsConnected) throw new IOException(L.T("当前连接已失效，请重新连接"));
        if (broker is not null) return await broker.ProbeNodeAsync(nodeName, token).ConfigureAwait(false);
        using var controller = new MihomoController(bundle ?? throw new IOException(L.T("当前连接已失效，请重新连接")));
        return await controller.ProbeNodeAsync(nodeName, token).ConfigureAwait(false);
    }

    public async Task<(long Down, long Up)> ReadTrafficAsync(ConnectionEndpoint expected, CancellationToken token)
    {
        var broker = _broker;
        var bundle = _activeBundle;
        if (!ReferenceEquals(expected, ActiveConnection) || !IsConnected) throw new IOException(L.T("当前连接已失效，请重新连接"));
        if (broker is not null) return await broker.ReadTrafficAsync(token).ConfigureAwait(false);
        using var controller = new MihomoController(bundle ?? throw new IOException(L.T("当前连接已失效，请重新连接")));
        return await controller.ReadTrafficAsync(token).ConfigureAwait(false);
    }

    public void InvalidateNetworkEvidence()
    {
        Interlocked.Increment(ref _networkRevision);
        if (!IsConnected) return;
        Health = ConnectionHealthState.Unchecked;
        Status = "网络已变化，等待重新确认";
        StatusChanged?.Invoke(this, EventArgs.Empty);
    }

    public async Task RecheckAsync(CancellationToken token)
    {
        await _connectionGate.WaitAsync(token);
        try
        {
            if (!IsConnected || ActiveConnection is not { } bundle) return;
            Health = ConnectionHealthState.Checking;
            Status = "正在核验网络";
            StatusChanged?.Invoke(this, EventArgs.Empty);
            var revision = Interlocked.Read(ref _networkRevision);
            var result = await ConnectionHealth.CheckAsync(bundle, token).ConfigureAwait(false);
            token.ThrowIfCancellationRequested();
            if (!IsConnected || !ReferenceEquals(bundle, ActiveConnection) || revision != Interlocked.Read(ref _networkRevision)) return;
            Health = result;
            Status = result == ConnectionHealthState.Reachable ? "网络连通性已确认" : "内核运行中 · 网络待确认";
            StatusChanged?.Invoke(this, EventArgs.Empty);
        }
        finally { _connectionGate.Release(); }
    }

    public async ValueTask DisposeAsync()
    {
        await DisconnectAsync().ConfigureAwait(false);
        _importer.Dispose();
    }

    private async Task CleanupRuntimeAsync()
    {
        _activeBundle = null;
        var session = _runtimeSession;
        _runtimeSession = null;
        if (session is null) return;
        await Task.Run(() =>
        {
            try { _runtimeCleanupPending |= !session.CleanupAfterCoreExit(); }
            catch (Exception error) when (error is IOException or UnauthorizedAccessException) { _runtimeCleanupPending = true; }
            finally { session.Dispose(); }
        });
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
