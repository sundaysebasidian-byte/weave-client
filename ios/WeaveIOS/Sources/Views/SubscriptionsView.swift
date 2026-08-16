import PhotosUI
import SwiftUI
import UniformTypeIdentifiers
import WeaveCore

struct SubscriptionsView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.monet) private var tokens
    @State private var showImport = false

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                HStack(alignment: .top) {
                    PageHeader(title: "订阅", subtitle: "本机加密管理")
                    Button { showImport = true } label: {
                        Image(systemName: "plus")
                            .font(.title2.weight(.semibold))
                            .frame(width: 52, height: 52)
                            .background(tokens.accent.opacity(0.42), in: Circle())
                            .overlay(Circle().stroke(.white.opacity(0.72), lineWidth: 1))
                    }
                    .accessibilityLabel("导入订阅")
                }
                .padding(.top, 8)

                GlassPanel(cornerRadius: 24) {
                    HStack(spacing: 14) {
                        GlassIcon(systemName: "arrow.triangle.2.circlepath")
                        VStack(alignment: .leading, spacing: 4) {
                            Text("订阅已载入").font(.headline)
                            Text("共 \(model.subscriptions.reduce(0) { $0 + $1.nodeCount }) 个节点")
                                .font(.subheadline)
                                .foregroundStyle(tokens.muted)
                        }
                        Spacer()
                        Text("AES-256-GCM")
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(tokens.good)
                    }
                    .padding(17)
                }

                if model.subscriptions.isEmpty {
                    GlassPanel(cornerRadius: 26) {
                        ContentUnavailableView(
                            "还没有订阅",
                            systemImage: "tray.and.arrow.down",
                            description: Text("支持 HTTPS、URI/Base64、Clash YAML、二维码、文件和 Weave 局域网链接。")
                        )
                        .padding(.vertical, 36)
                    }
                } else {
                    ForEach(model.subscriptions) { subscription in
                        NavigationLink {
                            SubscriptionDetailView(subscriptionID: subscription.id)
                        } label: {
                            GlassPanel(cornerRadius: 25) {
                                HStack(spacing: 14) {
                                    GlassIcon(systemName: "shippingbox.and.arrow.backward.fill")
                                    VStack(alignment: .leading, spacing: 5) {
                                        Text(subscription.name)
                                            .font(.headline)
                                            .foregroundStyle(.primary)
                                            .lineLimit(1)
                                        Text("\(subscription.nodeCount) 个节点 · \(sourceLabel(subscription.source))")
                                            .font(.subheadline)
                                            .foregroundStyle(tokens.muted)
                                            .lineLimit(1)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(tokens.muted)
                                }
                                .padding(17)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 30)
        }
        .scrollIndicators(.hidden)
        .navigationBarHidden(true)
        .sheet(isPresented: $showImport) { ImportSubscriptionSheet() }
    }

    private func sourceLabel(_ source: String) -> String {
        if source.hasPrefix("https://") { return URL(string: source)?.host ?? "远程" }
        if source.hasPrefix("file://") { return "本地文件" }
        return "本地导入"
    }
}

private struct SubscriptionDetailView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.monet) private var tokens
    @Environment(\.dismiss) private var dismiss
    let subscriptionID: UUID
    @State private var name = ""
    @State private var source = ""
    @State private var search = ""
    @State private var confirmDelete = false

    private var subscription: WeaveSubscription? {
        model.subscriptions.first { $0.id == subscriptionID }
    }

    private var nodes: [String] {
        guard let subscription else { return [] }
        let values = ClashNodeParser.parse(subscription.payload)
        let query = search.trimmingCharacters(in: .whitespacesAndNewlines)
        return query.isEmpty ? values : values.filter {
            ClashNodeParser.displayName($0).localizedCaseInsensitiveContains(query)
        }
    }

    var body: some View {
        MonetScene {
            ScrollView {
                LazyVStack(spacing: 16) {
                    if let subscription {
                        GlassPanel(cornerRadius: 26) {
                            VStack(spacing: 14) {
                                TextField("订阅名称", text: $name)
                                    .textFieldStyle(.roundedBorder)
                                TextField("HTTPS 地址", text: $source)
                                    .textInputAutocapitalization(.never)
                                    .autocorrectionDisabled()
                                    .textFieldStyle(.roundedBorder)
                                Button("保存并更新") {
                                    model.replaceSubscription(subscription, name: name, source: source)
                                }
                                .buttonStyle(PrimaryGlassButtonStyle())
                            }
                            .padding(18)
                        }

                        GlassPanel(cornerRadius: 24) {
                            HStack {
                                Label("\(subscription.nodeCount) 个节点", systemImage: "point.3.filled.connected.trianglepath.dotted")
                                    .font(.headline)
                                Spacer()
                                if subscription.id == model.selectedSubscriptionID {
                                    Text("当前订阅").font(.caption.weight(.semibold)).foregroundStyle(tokens.good)
                                }
                            }
                            .padding(17)
                        }

                        ForEach(nodes, id: \.self) { node in
                            Button {
                                model.selectSubscription(subscription.id)
                                model.selectNode(node)
                            } label: {
                                GlassPanel(cornerRadius: 22) {
                                    HStack(spacing: 13) {
                                        GlassIcon(systemName: "network")
                                        Text(ClashNodeParser.displayName(node))
                                            .font(.headline)
                                            .foregroundStyle(.primary)
                                            .lineLimit(1)
                                        Spacer()
                                        if subscription.id == model.selectedSubscriptionID,
                                           node == model.selectedNodeName {
                                            Image(systemName: "checkmark.circle.fill")
                                                .foregroundStyle(tokens.good)
                                        }
                                    }
                                    .padding(14)
                                }
                            }
                            .buttonStyle(.plain)
                        }

                        Button(role: .destructive) { confirmDelete = true } label: {
                            Label("删除订阅", systemImage: "trash")
                                .frame(maxWidth: .infinity)
                                .padding()
                        }
                    } else {
                        ContentUnavailableView("订阅已不存在", systemImage: "exclamationmark.triangle")
                    }
                }
                .padding(18)
            }
            .scrollIndicators(.hidden)
        }
        .navigationTitle(subscription?.name ?? "订阅详情")
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $search, prompt: "搜索节点")
        .onAppear {
            name = subscription?.name ?? ""
            source = subscription?.source ?? ""
        }
        .confirmationDialog(
            "永久删除这个订阅？",
            isPresented: $confirmDelete,
            titleVisibility: .visible
        ) {
            Button("删除订阅及相关分流", role: .destructive) {
                if let subscription { model.deleteSubscription(subscription); dismiss() }
            }
        } message: {
            Text("加密配置、默认出口引用和相关域名规则都会被清理。")
        }
    }
}

private struct ImportSubscriptionSheet: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var value = ""
    @State private var showFileImporter = false
    @State private var showScanner = false
    @State private var photoItem: PhotosPickerItem?

    var body: some View {
        NavigationStack {
            Form {
                Section("名称（可选）") {
                    TextField("自动使用订阅名称", text: $name)
                }
                Section("链接或内容") {
                    TextEditor(text: $value)
                        .frame(minHeight: 125)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Button("导入输入内容") { importValue(value) }
                        .disabled(value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                Section("其他入口") {
                    Button { showScanner = true } label: {
                        Label("扫描二维码", systemImage: "qrcode.viewfinder")
                    }
                    PhotosPicker(selection: $photoItem, matching: .images) {
                        Label("识别照片二维码", systemImage: "photo")
                    }
                    Button { showFileImporter = true } label: {
                        Label("选择配置文件", systemImage: "doc")
                    }
                }
                Section {
                    Text("公网订阅只接受 HTTPS；远程响应限制为 5 MiB，并拒绝私网重定向。URI/Base64 会先转换为 Mihomo provider，再写入加密订阅库。")
                        .font(.caption)
                }
            }
            .navigationTitle("导入订阅")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
            .fileImporter(
                isPresented: $showFileImporter,
                allowedContentTypes: [.plainText, .data],
                allowsMultipleSelection: false
            ) { result in
                do {
                    let url = try result.get()[0]
                    let scoped = url.startAccessingSecurityScopedResource()
                    defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                    let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize
                    guard size.map({ $0 <= SubscriptionImporter.maxPayloadBytes }) != false else {
                        throw WeaveError.message("订阅文件超过 5 MiB 限制")
                    }
                    model.importFile(name: name, data: try Data(contentsOf: url), filename: url.deletingPathExtension().lastPathComponent)
                    dismiss()
                } catch {
                    model.notice = error.localizedDescription
                }
            }
            .fullScreenCover(isPresented: $showScanner) {
                ZStack(alignment: .topTrailing) {
                    CameraQRScanner { result in
                        showScanner = false
                        importValue(result)
                    } onError: { error in
                        showScanner = false
                        model.notice = error
                    }
                    Button { showScanner = false } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.largeTitle)
                            .symbolRenderingMode(.palette)
                            .foregroundStyle(.white, .black.opacity(0.35))
                            .padding()
                    }
                }
                .ignoresSafeArea()
            }
            .onChange(of: photoItem) { _, item in
                guard let item else { return }
                Task {
                    do {
                        guard let data = try await item.loadTransferable(type: Data.self) else {
                            throw WeaveError.message("无法读取照片")
                        }
                        importValue(try await QRCodeDetector.detect(in: data))
                    } catch {
                        model.notice = error.localizedDescription
                    }
                }
            }
        }
        .presentationDetents([.large])
    }

    private func importValue(_ raw: String) {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.lowercased().hasPrefix("https://") {
            model.importRemote(name: name, url: trimmed)
        } else {
            model.importInline(name: name, value: trimmed)
        }
        dismiss()
    }
}
