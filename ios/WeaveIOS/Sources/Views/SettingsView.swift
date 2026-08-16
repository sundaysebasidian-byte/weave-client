import PhotosUI
import SwiftUI
import UIKit
import WeaveCore

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.monet) private var tokens
    @State private var showThemes = false
    @State private var showTransfer = false

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                PageHeader(title: "设置", subtitle: "连接与隐私")
                    .padding(.top, 8)

                sectionTitle("外观")
                GlassPanel(cornerRadius: 26) {
                    VStack(spacing: 0) {
                        Button { showThemes = true } label: {
                            settingsRow(
                                icon: "sparkles",
                                title: "艺术主题",
                                detail: model.palette.label,
                                trailing: "chevron.right"
                            )
                        }
                        .buttonStyle(.plain)
                        Divider().opacity(0.45).padding(.leading, 66)
                        Picker(
                            "显示模式",
                            selection: Binding(
                                get: { model.appearance },
                                set: model.updateAppearance
                            )
                        ) {
                            ForEach(AppearanceMode.allCases) { Text($0.label).tag($0) }
                        }
                        .pickerStyle(.menu)
                        .padding(.horizontal, 17)
                        .frame(minHeight: 58)
                    }
                }

                sectionTitle("连接")
                GlassPanel(cornerRadius: 26) {
                    VStack(spacing: 0) {
                        Picker(
                            "自动节点策略",
                            selection: Binding(
                                get: { model.preferences.automaticStrategy },
                                set: { value in model.updatePreferences { $0.automaticStrategy = value } }
                            )
                        ) {
                            ForEach(AutomaticStrategy.allCases) { Text($0.label).tag($0) }
                        }
                        .pickerStyle(.navigationLink)
                        .padding(.horizontal, 17)
                        .frame(minHeight: 58)
                        Divider().opacity(0.45).padding(.leading, 66)
                        Picker(
                            "IP 协议",
                            selection: Binding(
                                get: { model.preferences.ipv6Mode },
                                set: { value in model.updatePreferences { $0.ipv6Mode = value } }
                            )
                        ) {
                            ForEach(IPv6Mode.allCases) { Text($0.label).tag($0) }
                        }
                        .pickerStyle(.navigationLink)
                        .padding(.horizontal, 17)
                        .frame(minHeight: 58)
                        Divider().opacity(0.45).padding(.leading, 66)
                        Button { showTransfer = true } label: {
                            settingsRow(
                                icon: "arrow.left.arrow.right",
                                title: "局域网互传",
                                detail: "一次性加密链接",
                                trailing: "chevron.right"
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }

                sectionTitle("网络与安全")
                GlassPanel(cornerRadius: 26) {
                    VStack(spacing: 0) {
                        Picker(
                            "DNS",
                            selection: Binding(
                                get: { model.preferences.dnsProfile },
                                set: { value in model.updatePreferences { $0.dnsProfile = value } }
                            )
                        ) {
                            ForEach(DNSProfile.allCases) { Text($0.label).tag($0) }
                        }
                        .pickerStyle(.navigationLink)
                        .padding(.horizontal, 17)
                        .frame(minHeight: 58)

                        if model.preferences.dnsProfile == .custom {
                            Divider().opacity(0.45).padding(.leading, 66)
                            TextField(
                                "https://dns.example/dns-query",
                                text: Binding(
                                    get: { model.preferences.customDNSEndpoint },
                                    set: { value in model.updatePreferences { $0.customDNSEndpoint = value } }
                                )
                            )
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .keyboardType(.URL)
                            .padding(17)
                        }

                        Divider().opacity(0.45).padding(.leading, 66)
                        Toggle(
                            "阻止 UDP STUN",
                            isOn: Binding(
                                get: { model.preferences.blockSTUN },
                                set: { value in model.updatePreferences { $0.blockSTUN = value } }
                            )
                        )
                        .padding(17)

                        Divider().opacity(0.45).padding(.leading, 66)
                        Toggle(
                            "国内智能直连",
                            isOn: Binding(
                                get: { model.preferences.directMainlandChina },
                                set: { value in model.updatePreferences { $0.directMainlandChina = value } }
                            )
                        )
                        .padding(17)
                    }
                }

                sectionTitle("系统状态")
                GlassPanel(cornerRadius: 26) {
                    VStack(spacing: 0) {
                        settingsRow(
                            icon: "shield.lefthalf.filled",
                            title: "Packet Tunnel",
                            detail: model.tunnel.statusLabel,
                            trailing: nil
                        )
                        Divider().opacity(0.45).padding(.leading, 66)
                        settingsRow(
                            icon: "lock.shield",
                            title: "订阅存储",
                            detail: "Keychain + AES-256-GCM",
                            trailing: nil
                        )
                    }
                }

                Text("Weave 不提供节点、不记录访问域名，也不包含遥测 SDK。iOS 完整 VPN 需要 Apple Network Extension entitlement 与已签名的嵌入式内核。")
                    .font(.caption)
                    .foregroundStyle(tokens.muted)
                    .padding(.horizontal, 4)
                    .padding(.bottom, 28)
            }
            .padding(.horizontal, 18)
        }
        .scrollIndicators(.hidden)
        .navigationBarHidden(true)
        .sheet(isPresented: $showThemes) { ThemePickerSheet() }
        .sheet(isPresented: $showTransfer) { LANTransferView() }
    }

    private func sectionTitle(_ value: String) -> some View {
        Text(value)
            .font(.caption.weight(.semibold))
            .foregroundStyle(tokens.muted)
            .padding(.leading, 4)
            .padding(.top, 4)
    }

    private func settingsRow(
        icon: String,
        title: String,
        detail: String,
        trailing: String?
    ) -> some View {
        HStack(spacing: 13) {
            GlassIcon(systemName: icon)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.headline).foregroundStyle(.primary)
                Text(detail).font(.subheadline).foregroundStyle(tokens.muted).lineLimit(1)
            }
            Spacer()
            if let trailing {
                Image(systemName: trailing).font(.caption.weight(.bold)).foregroundStyle(tokens.muted)
            }
        }
        .padding(15)
        .contentShape(Rectangle())
    }
}

private struct ThemePickerSheet: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                ForEach(WeavePalette.allCases) { palette in
                    Button {
                        model.updatePalette(palette)
                    } label: {
                        HStack(spacing: 14) {
                            paletteSwatch(palette)
                            Text(palette.label).foregroundStyle(.primary)
                            Spacer()
                            if model.palette == palette {
                                Image(systemName: "checkmark.circle.fill")
                            }
                        }
                    }
                }
            }
            .navigationTitle("艺术主题")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } }
            }
        }
        .presentationDetents([.medium])
    }

    private func paletteSwatch(_ palette: WeavePalette) -> some View {
        let values = MonetTokens.resolve(palette, dark: false)
        return Circle()
            .fill(
                LinearGradient(
                    colors: [values.accent, values.lavender, values.coral],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            )
            .frame(width: 42, height: 42)
            .overlay(Circle().stroke(.white.opacity(0.8), lineWidth: 1))
    }
}

private struct LANTransferView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var importLink = ""
    @State private var showScanner = false
    @State private var photoItem: PhotosPickerItem?

    var body: some View {
        NavigationStack {
            Form {
                Section("导出到局域网设备") {
                    if model.transferLink.isEmpty {
                        Button {
                            model.startExport()
                        } label: {
                            Label("生成一次性二维码和链接", systemImage: "qrcode")
                        }
                    } else {
                        QRCodeImage(value: model.transferLink)
                            .frame(maxWidth: .infinity)
                            .frame(height: 230)
                            .padding(.vertical, 8)
                        Text(model.transferLink)
                            .font(.caption.monospaced())
                            .lineLimit(3)
                            .textSelection(.enabled)
                        Text("确认短码：\(model.transferConfirmationCode)")
                            .font(.system(.body, design: .monospaced).weight(.bold))
                            .foregroundStyle(.tint)
                        Button {
                            UIPasteboard.general.string = model.transferLink
                            model.transferMessage = "一次性链接已复制"
                        } label: {
                            Label("复制链接", systemImage: "doc.on.doc")
                        }
                    }
                    if !model.transferMessage.isEmpty {
                        Text(model.transferMessage).font(.caption)
                    }
                }

                Section("从另一台设备导入") {
                    TextField("weave://lan/v1/…", text: $importLink, axis: .vertical)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField("发送设备显示的 6 位短码", text: $model.importConfirmationCode)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.numberPad)
                    Button {
                        model.importTransferLink(importLink)
                        importLink = ""
                    } label: {
                        Label("导入链接", systemImage: "square.and.arrow.down")
                    }
                    .disabled(importLink.trimmingCharacters(in: .whitespaces).isEmpty || model.importConfirmationCode.count != 6)
                    Button { showScanner = true } label: {
                        Label("扫描二维码", systemImage: "qrcode.viewfinder")
                    }
                    PhotosPicker(selection: $photoItem, matching: .images) {
                        Label("识别照片二维码", systemImage: "photo")
                    }
                }

                Section {
                    Text("HTTP 只承载 AES-256-GCM 密文；密钥只存在于二维码或链接 fragment 中。服务器在成功读取一次或 5 分钟后关闭。")
                        .font(.caption)
                }
            }
            .navigationTitle("局域网互传")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
            .fullScreenCover(isPresented: $showScanner) {
                ZStack(alignment: .topTrailing) {
                    CameraQRScanner { value in
                        showScanner = false
                        importLink = value
                        model.importConfirmationCode = ""
                        model.transferMessage = "二维码已读取，请输入发送设备显示的 6 位确认短码"
                    } onError: { error in
                        showScanner = false
                        model.notice = error
                    }
                    Button { showScanner = false } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.largeTitle)
                            .foregroundStyle(.white)
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
                        importLink = try await QRCodeDetector.detect(in: data)
                        model.importConfirmationCode = ""
                        model.transferMessage = "二维码已读取，请输入发送设备显示的 6 位确认短码"
                    } catch {
                        model.notice = error.localizedDescription
                    }
                }
            }
        }
        .onAppear {
            if importLink.isEmpty { importLink = model.transferImportLink }
        }
        .onChange(of: model.transferImportLink) { _, value in
            if !value.isEmpty { importLink = value }
        }
        .onDisappear { model.stopExport() }
    }
}
