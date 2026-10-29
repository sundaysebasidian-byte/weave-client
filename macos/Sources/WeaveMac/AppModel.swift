import AppKit
import Combine
import CryptoKit
import Foundation
import Security

@MainActor
final class AppModel: ObservableObject {
    private static let tunEnabledKey = "weave.macos.tun-enabled"
    private static let selectionKey = "weave.macos.connection-selection.v1"

    let vault: SubscriptionVault
    let core: MihomoController
    private let defaults: UserDefaults
    private var savedSelection: SavedConnectionSelection

    @Published var transferLink = ""
    @Published var transferConfirmationCode = ""
    @Published var importLink = ""
    @Published var importConfirmationCode = ""
    @Published var transferMessage = ""
    @Published var transferBusy = false
    @Published private(set) var selectedSubscriptionID: UUID?
    @Published private(set) var selectedNodeName: String?
    @Published var tunEnabled: Bool
    @Published var directImportBusy = false
    @Published var directImportMessage = ""
    @Published var editingSubscriptionID: UUID?
    @Published private(set) var refreshingSubscriptionID: UUID?
    @Published private(set) var subscriptions: [MacSubscription] = []
    @Published private(set) var selectedNodes: [String] = []
    @Published private(set) var nodeCacheLoading = false
    @Published var pendingSubscriptionUpdate: PendingSubscriptionUpdate?
    @Published private(set) var selectionNotice: String?

    private var transferServer: OneTimeTransferServer?
    private var nodeCache: [UUID: [String]] = [:]
    private var nodeCachePayloads: [UUID: String] = [:]
    private var nodeCacheTask: Task<Void, Never>?
    private var cancellables = Set<AnyCancellable>()

    init(vault: SubscriptionVault? = nil, core: MihomoController? = nil, defaults: UserDefaults = .standard) {
        self.vault = vault ?? SubscriptionVault()
        self.core = core ?? MihomoController()
        self.defaults = defaults
        tunEnabled = defaults.object(forKey: Self.tunEnabledKey) as? Bool ?? true
        if let data = defaults.data(forKey: Self.selectionKey) {
            do { savedSelection = try JSONDecoder().decode(SavedConnectionSelection.self, from: data) }
            catch {
                savedSelection = SavedConnectionSelection()
                selectionNotice = "无法读取上次出口选择，请重新选择订阅和节点"
            }
        } else {
            savedSelection = SavedConnectionSelection(subscriptionID: self.vault.subscriptions.first?.id)
        }
        subscriptions = self.vault.subscriptions
        selectedSubscriptionID = savedSelection.subscriptionID
        selectedNodeName = savedSelection.node(for: selectedSubscriptionID)
        self.vault.$subscriptions
            .sink { [weak self] values in
                guard let self else { return }
                self.subscriptions = values
                if self.selectedSubscriptionID == nil, self.selectionNotice == nil, let first = values.first {
                    self.selectSubscription(first.id)
                }
                self.rebuildNodeCache(values)
            }
            .store(in: &cancellables)
        Task { await self.core.recoverSystemProxy() }
    }

    var selectedSubscription: MacSubscription? {
        subscriptions.first { $0.id == selectedSubscriptionID }
    }

    func selectSubscription(_ id: UUID?) {
        savedSelection.subscriptionID = id
        selectedSubscriptionID = id
        selectedNodeName = savedSelection.node(for: id)
        selectedNodes = id.flatMap { nodeCache[$0] } ?? []
        persistSelection()
    }

    func selectNode(_ name: String?) {
        guard let selectedSubscriptionID else { return }
        savedSelection.nodeNames[selectedSubscriptionID.uuidString] = name
        selectedNodeName = name
        persistSelection()
    }

    var selectionIssue: String? {
        if selectedSubscriptionID != nil && selectedSubscription == nil { return "上次选择的订阅已不存在，请重新选择" }
        if !nodeCacheLoading, let selectedNodeName, !selectedNodes.contains(selectedNodeName) {
            return "固定节点“\(ClashNodeNames.display(selectedNodeName))”已不存在，请选择新节点或明确选择自动模式"
        }
        return nil
    }

    private func persistSelection() {
        do {
            defaults.set(try JSONEncoder().encode(savedSelection), forKey: Self.selectionKey)
            selectionNotice = core.state.isActive ? "出口选择将在重新连接后生效，当前连接保持不变" : nil
        } catch { selectionNotice = "出口选择保存失败：\(error.localizedDescription)" }
    }

    func setTunEnabled(_ enabled: Bool) {
        tunEnabled = enabled
        defaults.set(enabled, forKey: Self.tunEnabledKey)
    }

    func toggleConnection() {
        if core.state.isConnected || core.state == .starting {
            Task { await core.stop() }
        } else {
            guard !nodeCacheLoading, selectionIssue == nil else { return }
            core.start(
                subscriptions: subscriptions,
                selectedSubscriptionID: selectedSubscriptionID,
                selectedNodeName: selectedNodeName,
                availableNodeNames: selectedNodes,
                preferTun: tunEnabled,
            )
        }
    }

    private func rebuildNodeCache(_ values: [MacSubscription]) {
        nodeCacheTask?.cancel()
        nodeCacheLoading = true
        let cached = nodeCache
        let payloads = nodeCachePayloads
        nodeCacheTask = Task {
            let worker = Task.detached(priority: .userInitiated) {
                var parsed: [UUID: [String]] = [:]
                for value in values {
                    if Task.isCancelled { return parsed }
                    parsed[value.id] = payloads[value.id] == value.payload ? cached[value.id] : ClashNodeNames.parse(value.payload)
                }
                return parsed
            }
            let parsed = await withTaskCancellationHandler(operation: { await worker.value }, onCancel: { worker.cancel() })
            guard !Task.isCancelled else { return }
            nodeCache = parsed
            nodeCachePayloads = Dictionary(uniqueKeysWithValues: values.map { ($0.id, $0.payload) })
            selectedNodes = selectedSubscriptionID.flatMap { parsed[$0] } ?? []
            nodeCacheLoading = false
        }
    }

    func importSubscriptionURL(name: String, rawURL: String) {
        guard !directImportBusy else { return }
        directImportBusy = true
        directImportMessage = "正在导入订阅"
        Task {
            do {
                let trimmed = rawURL.trimmingCharacters(in: .whitespacesAndNewlines)
                if trimmed.lowercased().hasPrefix("weave://lan/") {
                    throw WeaveMacError.message("局域网链接请在“局域网互传”页面输入发送端 6 位短码")
                } else if trimmed.lowercased().hasPrefix("https://") {
                    let imported = try await DirectSubscriptionImporter.fetchHTTPS(trimmed)
                    try await storeDirectSubscription(imported, preferredName: name)
                } else {
                    let imported = try await Task.detached(priority: .userInitiated) {
                        try DirectSubscriptionImporter.inlinePayload(from: trimmed)
                    }.value
                    try await storeDirectSubscription(imported, preferredName: name)
                }
            } catch {
                directImportMessage = error.localizedDescription
            }
            directImportBusy = false
        }
    }

    func importSubscriptionFile(name: String) {
        guard !directImportBusy else { return }
        directImportBusy = true
        directImportMessage = ""
        Task {
            do {
                if let imported = try await DirectSubscriptionImporter.chooseClashFile() {
                    try await storeDirectSubscription(imported, preferredName: name)
                }
            } catch {
                directImportMessage = error.localizedDescription
            }
            directImportBusy = false
        }
    }

    func importSubscriptionQRCode(name: String) {
        guard !directImportBusy else { return }
        directImportBusy = true
        directImportMessage = ""
        Task {
            do {
                guard let rawValue = try await QRCodeImporter.chooseAndRead() else {
                    directImportBusy = false
                    return
                }
                let trimmed = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
                if trimmed.lowercased().hasPrefix("weave://lan/") {
                    throw WeaveMacError.message("局域网二维码请在“局域网互传”页面输入发送端 6 位短码")
                } else {
                    let imported = if trimmed.lowercased().hasPrefix("https://") {
                        try await DirectSubscriptionImporter.fetchHTTPS(trimmed)
                    } else {
                        try await Task.detached(priority: .userInitiated) {
                            try DirectSubscriptionImporter.inlinePayload(from: trimmed)
                        }.value
                    }
                    try await storeDirectSubscription(imported, preferredName: name)
                }
            } catch {
                directImportMessage = error.localizedDescription
            }
            directImportBusy = false
        }
    }

    func resetDirectImportMessage() {
        directImportMessage = ""
    }

    func updateSubscription(
        id: UUID,
        name: String,
        source: String,
        payload: String,
    ) async -> Bool {
        guard !directImportBusy else { return false }
        directImportBusy = true
        defer { directImportBusy = false }
        do {
            try await vault.update(id: id, name: name, source: source, payload: payload)
            directImportMessage = core.state.isActive ? "订阅已更新，重新连接后生效；当前连接保持不变" : "订阅已更新"
            return true
        } catch {
            directImportMessage = error.localizedDescription
            return false
        }
    }

    func refreshSubscription(_ subscription: MacSubscription) {
        guard refreshingSubscriptionID == nil, pendingSubscriptionUpdate == nil else { return }
        guard subscription.source.lowercased().hasPrefix("https://") else {
            directImportMessage = "本地文件订阅不能自动刷新，请在编辑页替换文件内容"
            return
        }
        refreshingSubscriptionID = subscription.id
        Task {
            defer { refreshingSubscriptionID = nil }
            do {
                let imported = try await DirectSubscriptionImporter.fetchHTTPS(subscription.source)
                try await stageSubscriptionUpdate(subscription, imported: imported)
                directImportMessage = "更新已下载，请检查变化后保存"
            } catch {
                directImportMessage = error.localizedDescription
            }
        }
    }

    func stageSubscriptionUpdate(_ original: MacSubscription, imported: ImportedSubscriptionPayload) async throws {
        let prepared = try await Task.detached(priority: .userInitiated) {
            try PendingSubscriptionUpdate(original: original, source: imported.source, payload: imported.payload)
        }.value
        try Task.checkCancellation()
        guard vault.subscriptions.first(where: { $0.id == original.id }) == original else {
            throw WeaveMacError.message("下载期间订阅已被修改，请重新刷新")
        }
        pendingSubscriptionUpdate = prepared
    }

    func applyPendingSubscriptionUpdate() async {
        guard !directImportBusy, let pending = pendingSubscriptionUpdate else { return }
        directImportBusy = true
        defer { directImportBusy = false }
        do {
            guard vault.subscriptions.first(where: { $0.id == pending.id }) == pending.original else {
                throw WeaveMacError.message("预览期间订阅已被修改，已保留当前版本；请重新刷新")
            }
            try await vault.update(id: pending.id, name: pending.original.name, source: pending.source, payload: pending.payload)
            pendingSubscriptionUpdate = nil
            directImportMessage = core.state.isActive ? "更新已保存，重新连接后生效；当前连接保持不变" : "更新已保存"
        } catch {
            pendingSubscriptionUpdate = nil
            directImportMessage = error.localizedDescription
        }
    }

    private func storeDirectSubscription(
        _ imported: ImportedSubscriptionPayload,
        preferredName: String
    ) async throws {
        let trimmedName = preferredName.trimmingCharacters(in: .whitespacesAndNewlines)
        let name = trimmedName.isEmpty ? imported.suggestedName : String(trimmedName.prefix(80))
        try await vault.importTransfer(
            [
                TransferSubscription(
                    name: name,
                    source: imported.source,
                    payload: imported.payload
                ),
            ]
        )
        directImportMessage = "已安全导入“\(name)”"
    }

    private func decodeTransferResponse(
        _ response: Data,
        key: Data
    ) async throws -> [TransferSubscription] {
        try await Task.detached(priority: .userInitiated) {
            let plaintext = try TransferCodec.open(response, key: key)
            return try TransferCodec.decode(plaintext)
        }.value
    }

    func startExport() {
        guard !vault.subscriptions.isEmpty, !transferBusy else {
            transferMessage = "没有可导出的订阅"
            return
        }
        transferBusy = true
        Task {
            do {
                stopExport()
                let items = vault.exportItems()
                let material = try await Task.detached(priority: .userInitiated) {
                    var random = [UInt8](repeating: 0, count: 48)
                    guard SecRandomCopyBytes(
                        kSecRandomDefault,
                        random.count,
                        &random
                    ) == errSecSuccess else {
                        throw WeaveMacError.message("无法生成一次性传输密钥")
                    }
                    let key = Data(random.prefix(32))
                    let token = random.suffix(16)
                        .map { String(format: "%02x", $0) }
                        .joined()
                    let plaintext = try TransferCodec.encode(items)
                    return (
                        key,
                        token,
                        try TransferCodec.seal(plaintext, key: key)
                    )
                }.value
                guard let host = LocalAddress.privateIPv4() else {
                    throw WeaveMacError.message("未找到可用的局域网 IPv4 地址")
                }
                let server = OneTimeTransferServer(packet: material.2, token: material.1)
                let port = try await server.start()
                transferServer = server
                transferLink = TransferLink(
                    host: host,
                    port: port,
                    token: material.1,
                    key: material.0
                ).string
                transferConfirmationCode = TransferLink(
                    host: host,
                    port: port,
                    token: material.1,
                    key: material.0
                ).confirmationCode
                transferMessage = "一次性链接将在 5 分钟或导入一次后失效"
            } catch {
                transferMessage = error.localizedDescription
            }
            transferBusy = false
        }
    }

    func stopExport() {
        transferServer?.stop()
        transferServer = nil
        transferLink = ""
        transferConfirmationCode = ""
    }

    func copyTransferLink() {
        guard !transferLink.isEmpty else { return }
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(transferLink, forType: .string)
        transferMessage = "一次性链接已复制"
    }

    func importFromCurrentLink() {
        let raw = importLink
        let code = importConfirmationCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !raw.isEmpty, code.range(of: "^[0-9]{6}$", options: .regularExpression) != nil,
              !transferBusy else {
            transferMessage = "请输入发送设备显示的 6 位确认短码"
            return
        }
        transferBusy = true
        Task {
            do {
                let link = try TransferLink.parse(raw)
                guard code == link.confirmationCode else {
                    throw WeaveMacError.message("短码不匹配：请让发送设备重新显示当前二维码和短码")
                }
                let response = try await LANTransferClient.fetch(link)
                let decoded = try await decodeTransferResponse(response, key: link.key)
                try await vault.importTransfer(decoded)
                importLink = ""
                importConfirmationCode = ""
                transferMessage = "已安全同步 \(decoded.count) 个订阅"
            } catch {
                transferMessage = error.localizedDescription
            }
            transferBusy = false
        }
    }

    func importQRCodeImage() {
        Task {
            do {
                if let value = try await QRCodeImporter.chooseAndRead() {
                    importLink = value
                    importConfirmationCode = ""
                    transferMessage = "二维码已读取，请输入发送设备显示的 6 位确认短码"
                }
            } catch {
                transferMessage = error.localizedDescription
            }
        }
    }

    /// Called by the application termination hook so a normal quit cannot leave the
    /// previous system proxy transaction active until the next launch.
    func shutdown() async {
        await core.stop()
        stopExport()
    }
}
