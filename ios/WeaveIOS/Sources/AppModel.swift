import Combine
import Foundation
import NetworkExtension
import SwiftUI
import WeaveCore

enum AppearanceMode: String, CaseIterable, Identifiable {
    case system
    case light
    case dark

    var id: String { rawValue }
    var label: String {
        switch self {
        case .system: "跟随系统"
        case .light: "浅色"
        case .dark: "深色"
        }
    }
}

@MainActor
final class AppModel: ObservableObject {
    static let appGroupIdentifier = "group.io.weave.client"

    @Published private(set) var subscriptions: [WeaveSubscription] = []
    @Published var selectedSubscriptionID: UUID?
    @Published var selectedNodeName: String?
    @Published var routes: [DomainRouteRule] = []
    @Published var preferences = RuntimePreferences()
    @Published var palette: WeavePalette = .impressionSunrise
    @Published var appearance: AppearanceMode = .system
    @Published var notice = ""
    @Published var busy = false
    @Published var transferLink = ""
    @Published var transferConfirmationCode = ""
    @Published var transferMessage = ""
    @Published var importConfirmationCode = ""
    @Published var transferImportLink = ""
    @Published var pendingTransferLink: String?
    @Published var tunnel = TunnelManager()

    private var store: SecureSubscriptionStore?
    private var transferServer: OneTimeTransferServer?
    private var cancellables = Set<AnyCancellable>()
    private let defaults = UserDefaults.standard

    init() {
        restoreSettings()
        do {
            store = try SecureSubscriptionStore()
        } catch {
            notice = error.localizedDescription
        }
        tunnel.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &cancellables)
        Task {
            await reloadSubscriptions()
            await tunnel.prepare()
        }
    }

    var preferredColorScheme: ColorScheme? {
        switch appearance {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }

    var selectedSubscription: WeaveSubscription? {
        subscriptions.first { $0.id == selectedSubscriptionID }
    }

    var selectedNodes: [String] {
        selectedSubscription.map { ClashNodeParser.parse($0.payload) } ?? []
    }

    var selectedNodeDisplayName: String {
        selectedNodeName.map(ClashNodeParser.displayName) ?? "自动选择"
    }

    func selectSubscription(_ id: UUID?) {
        guard selectedSubscriptionID != id else { return }
        invalidateActiveTunnel()
        selectedSubscriptionID = id
        selectedNodeName = nil
        persistSettings()
    }

    func selectNode(_ name: String?) {
        guard selectedNodeName != name else { return }
        invalidateActiveTunnel()
        selectedNodeName = name
        persistSettings()
    }

    func updatePreferences(_ mutate: (inout RuntimePreferences) -> Void) {
        var updated = preferences
        mutate(&updated)
        guard updated != preferences else { return }
        invalidateActiveTunnel()
        preferences = updated
        persistSettings()
    }

    func updatePalette(_ value: WeavePalette) {
        palette = value
        persistSettings()
    }

    func updateAppearance(_ value: AppearanceMode) {
        appearance = value
        persistSettings()
    }

    func importRemote(name: String, url: String) {
        perform {
            let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.lowercased().hasPrefix("weave://lan/") {
                    try await self.importTransfer(trimmed, confirmationCode: self.importConfirmationCode)
            } else {
                let payload = try await SubscriptionImporter.fetchHTTPS(trimmed)
                try await self.store(payload, preferredName: name)
            }
        }
    }

    func importInline(name: String, value: String) {
        perform {
            let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.lowercased().hasPrefix("weave://lan/") {
                    try await self.importTransfer(trimmed, confirmationCode: self.importConfirmationCode)
            } else {
                let payload = try SubscriptionImporter.inlinePayload(from: trimmed)
                try await self.store(payload, preferredName: name)
            }
        }
    }

    func importFile(name: String, data: Data, filename: String) {
        perform {
            let payload = try SubscriptionImporter.importData(
                data,
                suggestedName: filename,
                source: "file://local-import"
            )
            try await self.store(payload, preferredName: name)
        }
    }

    func replaceSubscription(_ subscription: WeaveSubscription, name: String, source: String) {
        perform {
            self.invalidateActiveTunnel()
            let payload: ImportedSubscriptionPayload
            if source.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().hasPrefix("https://") {
                payload = try await SubscriptionImporter.fetchHTTPS(source)
            } else {
                payload = ImportedSubscriptionPayload(
                    suggestedName: subscription.name,
                    source: subscription.source,
                    payload: subscription.payload
                )
            }
            guard let store = self.store else { throw WeaveError.message("订阅库不可用") }
            _ = try await store.importPayload(payload, preferredName: name, replacing: subscription.id)
            await self.reloadSubscriptions()
            self.notice = "订阅已更新"
        }
    }

    func deleteSubscription(_ subscription: WeaveSubscription) {
        perform {
            self.invalidateActiveTunnel()
            guard let store = self.store else { throw WeaveError.message("订阅库不可用") }
            try await store.remove(id: subscription.id)
            self.routes.removeAll { $0.target.subscriptionID == subscription.id }
            if self.selectedSubscriptionID == subscription.id {
                self.selectedSubscriptionID = nil
                self.selectedNodeName = nil
            }
            await self.reloadSubscriptions()
            self.persistSettings()
            self.notice = "订阅已删除，相关规则已清理"
        }
    }

    func addRoute(domainSuffix: String, target: RouteTarget) {
        let trimmed = domainSuffix.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { notice = "请输入域名后缀"; return }
        invalidateActiveTunnel()
        routes.append(DomainRouteRule(domainSuffix: trimmed, target: target))
        persistSettings()
    }

    func deleteRoute(_ route: DomainRouteRule) {
        invalidateActiveTunnel()
        routes.removeAll { $0.id == route.id }
        persistSettings()
    }

    func targetLabel(_ target: RouteTarget) -> String {
        switch target {
        case .direct: "直连"
        case .block: "阻止"
        case let .automatic(id):
            subscriptions.first(where: { $0.id == id }).map { "\($0.name) · 自动" } ?? "目标失效"
        case let .fixed(id, node):
            subscriptions.first(where: { $0.id == id }).map {
                "\($0.name) · \(ClashNodeParser.displayName(node))"
            } ?? "目标失效"
        }
    }

    func toggleTunnel() {
        if tunnel.isActive {
            tunnel.stop()
            return
        }
        perform {
            guard let selectedSubscriptionID = self.selectedSubscriptionID else {
                throw WeaveError.message("请先选择订阅")
            }
            guard Bundle.main.object(forInfoDictionaryKey: "WeaveMobileCoreEmbedded") as? Bool == true else {
                throw WeaveError.message("当前构建未嵌入 WeaveMihomoMobile 移动内核，已安全拒绝建立空隧道")
            }
            guard let container = self.sharedContainerURL() else {
                throw WeaveError.message("App Group 不可用；请使用包含 group.io.weave.client 的签名配置")
            }
            let descriptor = try RuntimeConfigCompiler.stage(
                in: container,
                subscriptions: self.subscriptions,
                selection: RuntimeSelection(
                    subscriptionID: selectedSubscriptionID,
                    nodeName: self.selectedNodeName
                ),
                routes: self.routes,
                preferences: self.preferences
            )
            do {
                try await self.tunnel.start(descriptor: descriptor)
            } catch {
                RuntimeConfigCompiler.clearRuntime(in: container)
                throw error
            }
        }
    }

    func startExport() {
        perform {
            guard let store = self.store else { throw WeaveError.message("订阅库不可用") }
            let items = try await store.exportItems()
            guard !items.isEmpty else { throw WeaveError.message("没有可导出的订阅") }
            self.stopExport()
            let material = try TransferMaterial.make(items: items)
            guard let host = LocalAddress.privateIPv4() else {
                throw WeaveError.message("未找到可用的局域网 IPv4 地址")
            }
            let server = OneTimeTransferServer(packet: material.packet, token: material.token)
            let port = try await server.start()
            self.transferServer = server
            self.transferLink = TransferLink(
                host: host,
                port: port,
                token: material.token,
                key: material.key
            ).string
            self.transferConfirmationCode = TransferLink(
                host: host,
                port: port,
                token: material.token,
                key: material.key
            ).confirmationCode
            self.transferMessage = "链接将在 5 分钟或成功导入一次后失效"
        }
    }

    func stopExport() {
        transferServer?.stop()
        transferServer = nil
        transferLink = ""
        transferConfirmationCode = ""
    }

    func importTransferLink(_ value: String) {
        perform { try await self.importTransfer(value, confirmationCode: self.importConfirmationCode) }
    }

    func receiveDeepLink(_ url: URL) {
        do {
            _ = try TransferLink.parse(url.absoluteString)
            pendingTransferLink = url.absoluteString
            transferMessage = "链接已读取，请在互传页面输入发送设备显示的 6 位确认短码"
        } catch {
            notice = error.localizedDescription
        }
    }

    func confirmPendingTransfer() {
        guard let value = pendingTransferLink else { return }
        pendingTransferLink = nil
        transferImportLink = value
        transferMessage = "链接已读取，请在互传页面输入发送设备显示的 6 位确认短码"
        // The URL is intentionally not fetched until the user enters the out-of-band code.
        _ = value
    }

    private func importTransfer(_ value: String, confirmationCode: String) async throws {
        let link = try TransferLink.parse(value)
        let code = confirmationCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard code.range(of: "^[0-9]{6}$", options: .regularExpression) != nil else {
            throw WeaveError.message("请输入发送设备显示的 6 位确认短码")
        }
        guard code == link.confirmationCode else {
            throw WeaveError.message("短码不匹配：请让发送设备重新显示当前二维码和短码")
        }
        let response = try await LANTransferClient.fetch(link)
        let plaintext = try TransferCodec.open(response, key: link.key)
        let items = try TransferCodec.decode(plaintext)
        guard let store else { throw WeaveError.message("订阅库不可用") }
        try await store.replaceAll(with: items)
        await reloadSubscriptions()
        importConfirmationCode = ""
        transferImportLink = ""
        transferMessage = "已安全同步 \(items.count) 个订阅"
        notice = transferMessage
    }

    private func store(_ payload: ImportedSubscriptionPayload, preferredName: String) async throws {
        guard let store else { throw WeaveError.message("订阅库不可用") }
        let saved = try await store.importPayload(payload, preferredName: preferredName)
        await reloadSubscriptions()
        if selectedSubscriptionID == nil { selectedSubscriptionID = saved.id }
        persistSettings()
        notice = "已导入“\(saved.name)” · \(saved.nodeCount) 个节点"
    }

    private func reloadSubscriptions() async {
        guard let store else { return }
        do {
            subscriptions = try await store.all()
            if let id = selectedSubscriptionID,
               !subscriptions.contains(where: { $0.id == id }) {
                selectedSubscriptionID = subscriptions.first?.id
                selectedNodeName = nil
            } else if selectedSubscriptionID == nil {
                selectedSubscriptionID = subscriptions.first?.id
            }
            if let selectedNodeName, !selectedNodes.contains(selectedNodeName) {
                self.selectedNodeName = nil
            }
        } catch {
            notice = error.localizedDescription
        }
    }

    private func perform(_ action: @escaping @MainActor () async throws -> Void) {
        guard !busy else { return }
        busy = true
        Task {
            defer { busy = false }
            do { try await action() }
            catch { notice = error.localizedDescription }
        }
    }

    private func sharedContainerURL() -> URL? {
        FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: Self.appGroupIdentifier
        )
    }

    private func restoreSettings() {
        if let raw = defaults.string(forKey: "palette"), let value = WeavePalette(rawValue: raw) {
            palette = value
        }
        if let raw = defaults.string(forKey: "appearance"), let value = AppearanceMode(rawValue: raw) {
            appearance = value
        }
        if let value = defaults.string(forKey: "selectedSubscriptionID") {
            selectedSubscriptionID = UUID(uuidString: value)
        }
        selectedNodeName = defaults.string(forKey: "selectedNodeName")
        if let data = defaults.data(forKey: "runtimePreferences"),
           let value = try? JSONDecoder().decode(RuntimePreferences.self, from: data) {
            preferences = value
        }
        if let data = defaults.data(forKey: "domainRoutes"),
           let value = try? JSONDecoder().decode([DomainRouteRule].self, from: data) {
            routes = value
        }
    }

    private func persistSettings() {
        defaults.set(palette.rawValue, forKey: "palette")
        defaults.set(appearance.rawValue, forKey: "appearance")
        defaults.set(selectedSubscriptionID?.uuidString, forKey: "selectedSubscriptionID")
        defaults.set(selectedNodeName, forKey: "selectedNodeName")
        defaults.set(try? JSONEncoder().encode(preferences), forKey: "runtimePreferences")
        defaults.set(try? JSONEncoder().encode(routes), forKey: "domainRoutes")
    }

    private func invalidateActiveTunnel() {
        guard tunnel.isActive else { return }
        tunnel.stop()
        notice = "配置已更新，请重新连接以应用更改"
    }
}
