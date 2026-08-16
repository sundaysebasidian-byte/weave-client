import SwiftUI
import WeaveCore

struct RoutingView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.monet) private var tokens
    @State private var showAdd = false

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 16) {
                HStack(alignment: .top) {
                    PageHeader(title: "智能分流", subtitle: "按域名选择出口")
                    Button { showAdd = true } label: {
                        Image(systemName: "plus")
                            .font(.title2.weight(.semibold))
                            .frame(width: 52, height: 52)
                            .background(.ultraThinMaterial, in: Circle())
                    }
                    .accessibilityLabel("添加分流规则")
                }
                .padding(.top, 8)

                GlassPanel(cornerRadius: 24) {
                    HStack(alignment: .top, spacing: 14) {
                        GlassIcon(systemName: "iphone.gen3.radiowaves.left.and.right")
                        VStack(alignment: .leading, spacing: 6) {
                            Text("iOS 平台边界").font(.headline)
                            Text("个人 Packet Tunnel 无法读取流量来自哪个 App；因此这里按域名精确分流。按应用 VPN 仅适用于 MDM 管理设备，不提供虚假入口。")
                                .font(.caption)
                                .foregroundStyle(tokens.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .padding(17)
                }

                if model.routes.isEmpty {
                    GlassPanel(cornerRadius: 26) {
                        ContentUnavailableView(
                            "还没有分流规则",
                            systemImage: "arrow.triangle.branch",
                            description: Text("添加域名后缀，并为它选择任意订阅的自动策略、固定节点、直连或阻止。")
                        )
                        .padding(.vertical, 34)
                    }
                } else {
                    ForEach(model.routes) { route in
                        GlassPanel(cornerRadius: 24) {
                            HStack(spacing: 14) {
                                GlassIcon(systemName: targetIcon(route.target))
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(route.domainSuffix).font(.headline).lineLimit(1)
                                    Text(model.targetLabel(route.target))
                                        .font(.subheadline)
                                        .foregroundStyle(tokens.muted)
                                        .lineLimit(1)
                                }
                                Spacer()
                                Button(role: .destructive) {
                                    model.deleteRoute(route)
                                } label: {
                                    Image(systemName: "trash")
                                        .foregroundStyle(tokens.coral)
                                        .padding(10)
                                }
                                .accessibilityLabel("删除 \(route.domainSuffix)")
                            }
                            .padding(15)
                        }
                    }
                }
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 30)
        }
        .scrollIndicators(.hidden)
        .navigationBarHidden(true)
        .sheet(isPresented: $showAdd) { AddRouteSheet() }
    }

    private func targetIcon(_ target: RouteTarget) -> String {
        switch target {
        case .direct: "arrow.right"
        case .block: "nosign"
        case .automatic: "gauge.with.dots.needle.50percent"
        case .fixed: "mappin.and.ellipse"
        }
    }
}

private struct AddRouteSheet: View {
    enum TargetKind: String, CaseIterable, Identifiable {
        case automatic = "自动"
        case fixed = "固定节点"
        case direct = "直连"
        case block = "阻止"
        var id: String { rawValue }
    }

    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var domain = ""
    @State private var kind: TargetKind = .automatic
    @State private var subscriptionID: UUID?
    @State private var nodeName: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("匹配") {
                    TextField("例如 youtube.com", text: $domain)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                Section("出口类型") {
                    Picker("类型", selection: $kind) {
                        ForEach(TargetKind.allCases) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                }
                if kind == .automatic || kind == .fixed {
                    Section("先选订阅") {
                        Picker("订阅", selection: $subscriptionID) {
                            Text("请选择").tag(UUID?.none)
                            ForEach(model.subscriptions) { Text($0.name).tag(Optional($0.id)) }
                        }
                        .onChange(of: subscriptionID) { _, _ in nodeName = nil }
                    }
                }
                if kind == .fixed, let subscriptionID,
                   let subscription = model.subscriptions.first(where: { $0.id == subscriptionID }) {
                    Section("再选节点") {
                        Picker("节点", selection: $nodeName) {
                            Text("请选择").tag(String?.none)
                            ForEach(ClashNodeParser.parse(subscription.payload), id: \.self) {
                                Text(ClashNodeParser.displayName($0)).tag(Optional($0))
                            }
                        }
                    }
                }
            }
            .navigationTitle("添加分流")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("添加") {
                        guard let target else { return }
                        model.addRoute(domainSuffix: domain, target: target)
                        dismiss()
                    }
                    .disabled(domain.trimmingCharacters(in: .whitespaces).isEmpty || target == nil)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private var target: RouteTarget? {
        switch kind {
        case .direct: return RouteTarget.direct
        case .block: return RouteTarget.block
        case .automatic:
            return subscriptionID.map { RouteTarget.automatic(subscriptionID: $0) }
        case .fixed:
            guard let subscriptionID, let nodeName else { return nil }
            return .fixed(subscriptionID: subscriptionID, nodeName: nodeName)
        }
    }
}
