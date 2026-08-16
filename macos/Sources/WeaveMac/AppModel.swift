import AppKit
import Combine
import CryptoKit
import Foundation
import Security

@MainActor
final class AppModel: ObservableObject {
    let vault = SubscriptionVault()
    let core = MihomoController()

    @Published var transferLink = ""
    @Published var transferConfirmationCode = ""
    @Published var importLink = ""
    @Published var importConfirmationCode = ""
    @Published var transferMessage = ""
    @Published var transferBusy = false
    @Published var selectedSubscriptionID: UUID?
    @Published var selectedNodeName: String?
    @Published var directImportBusy = false
    @Published var directImportMessage = ""
    @Published var editingSubscriptionID: UUID?
    @Published private(set) var refreshingSubscriptionID: UUID?
    @Published private(set) var subscriptions: [MacSubscription] = []
    @Published private(set) var selectedNodes: [String] = []

    private var transferServer: OneTimeTransferServer?
    private var nodeCache: [UUID: [String]] = [:]
    private var nodeCacheTask: Task<Void, Never>?
    private var cancellables = Set<AnyCancellable>()

    init() {
        subscriptions = vault.subscriptions
        selectedSubscriptionID = subscriptions.first?.id
        vault.$subscriptions
            .sink { [weak self] values in
                guard let self else { return }
                self.subscriptions = values
                if let selectedSubscriptionID = self.selectedSubscriptionID,
                   !values.contains(where: { $0.id == selectedSubscriptionID }) {
                    self.selectedSubscriptionID = values.first?.id
                    self.selectedNodeName = nil
                } else if self.selectedSubscriptionID == nil {
                    self.selectedSubscriptionID = values.first?.id
                }
                self.rebuildNodeCache(values)
            }
            .store(in: &cancellables)
    }

    var selectedSubscription: MacSubscription? {
        subscriptions.first { $0.id == selectedSubscriptionID }
    }

    func selectSubscription(_ id: UUID?) {
        selectedSubscriptionID = id
        selectedNodeName = nil
        selectedNodes = id.flatMap { nodeCache[$0] } ?? []
    }

    func toggleConnection() {
        if core.state == .localProxy {
            core.stop()
        } else if core.state == .starting {
            core.stop()
        } else {
            core.start(
                subscriptions: subscriptions,
                selectedSubscriptionID: selectedSubscriptionID,
                selectedNodeName: selectedNodeName,
                availableNodeNames: selectedNodes
            )
        }
    }

    private func rebuildNodeCache(_ values: [MacSubscription]) {
        nodeCacheTask?.cancel()
        let selectedID = selectedSubscriptionID
        nodeCacheTask = Task {
            let parsed = await Task.detached(priority: .userInitiated) {
                Dictionary(uniqueKeysWithValues: values.map {
                    ($0.id, ClashNodeNames.parse($0.payload))
                })
            }.value
            guard !Task.isCancelled else { return }
            nodeCache = parsed
            selectedNodes = selectedID.flatMap { parsed[$0] } ?? []
            if let selectedNodeName, !selectedNodes.contains(selectedNodeName) {
                self.selectedNodeName = nil
            }
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
                    let imported = try DirectSubscriptionImporter.inlinePayload(from: trimmed)
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
                        try DirectSubscriptionImporter.inlinePayload(from: trimmed)
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
    ) {
        do {
            try vault.update(id: id, name: name, source: source, payload: payload)
            directImportMessage = "订阅已更新"
        } catch {
            directImportMessage = error.localizedDescription
        }
    }

    func refreshSubscription(_ subscription: MacSubscription) {
        guard refreshingSubscriptionID == nil else { return }
        guard subscription.source.lowercased().hasPrefix("https://") else {
            directImportMessage = "本地文件订阅不能自动刷新，请在编辑页替换文件内容"
            return
        }
        refreshingSubscriptionID = subscription.id
        Task {
            defer { refreshingSubscriptionID = nil }
            do {
                let imported = try await DirectSubscriptionImporter.fetchHTTPS(subscription.source)
                try vault.update(
                    id: subscription.id,
                    name: subscription.name,
                    source: imported.source,
                    payload: imported.payload,
                )
                directImportMessage = "已刷新“\(subscription.name)”"
                if core.state == .localProxy {
                    core.stop()
                    let latestSubscriptions = vault.subscriptions
                    subscriptions = latestSubscriptions
                    core.start(
                        subscriptions: latestSubscriptions,
                        selectedSubscriptionID: selectedSubscriptionID,
                        selectedNodeName: selectedNodeName,
                        availableNodeNames: selectedNodes,
                    )
                }
            } catch {
                directImportMessage = error.localizedDescription
            }
        }
    }

    private func storeDirectSubscription(
        _ imported: ImportedSubscriptionPayload,
        preferredName: String
    ) async throws {
        let sanitizedPayload = try ClashProviderSanitizer.sanitize(imported.payload)
        let nodes = await Task.detached(priority: .userInitiated) {
            ClashNodeNames.parse(sanitizedPayload)
        }.value
        guard !nodes.isEmpty else {
            throw WeaveMacError.message("没有识别到可供 Mihomo 使用的 Clash 节点")
        }
        let trimmedName = preferredName.trimmingCharacters(in: .whitespacesAndNewlines)
        let name = trimmedName.isEmpty ? imported.suggestedName : String(trimmedName.prefix(80))
        try vault.importTransfer(
            [
                TransferSubscription(
                    name: name,
                    source: imported.source,
                    payload: sanitizedPayload
                ),
            ],
            nodeNames: [nodes]
        )
        directImportMessage = "已安全导入“\(name)” · \(nodes.count) 个节点"
    }

    private func decodeTransferResponse(
        _ response: Data,
        key: Data
    ) async throws -> ([TransferSubscription], [[String]]) {
        try await Task.detached(priority: .userInitiated) {
            let plaintext = try TransferCodec.open(response, key: key)
            let items = try TransferCodec.decode(plaintext)
            let nodeNames = items.map { ClashNodeNames.parse($0.payload) }
            guard nodeNames.allSatisfy({ !$0.isEmpty }) else {
                throw WeaveMacError.message("传输内容包含没有有效节点的订阅")
            }
            return (items, nodeNames)
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
                let decoded = try await Task.detached(priority: .userInitiated) {
                    let plaintext = try TransferCodec.open(response, key: link.key)
                    let items = try TransferCodec.decode(plaintext)
                    let nodeNames = items.map { ClashNodeNames.parse($0.payload) }
                    guard nodeNames.allSatisfy({ !$0.isEmpty }) else {
                        throw WeaveMacError.message("传输内容包含没有有效节点的订阅")
                    }
                    return (items, nodeNames)
                }.value
                try vault.importTransfer(decoded.0, nodeNames: decoded.1)
                importLink = ""
                importConfirmationCode = ""
                transferMessage = "已安全同步 \(decoded.0.count) 个订阅"
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
    func shutdown() {
        core.stop()
        stopExport()
    }
}
