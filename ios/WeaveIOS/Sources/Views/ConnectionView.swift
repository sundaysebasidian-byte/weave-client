import SwiftUI
import WeaveCore

struct ConnectionView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.monet) private var tokens

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 18) {
                PageHeader(title: "Weave", subtitle: "私密网络")
                    .padding(.top, 8)

                hero
                modePicker
                exitSelector
                healthCards
            }
            .padding(.horizontal, 18)
            .padding(.bottom, 30)
        }
        .scrollIndicators(.hidden)
        .navigationBarHidden(true)
    }

    private var hero: some View {
        GlassPanel(cornerRadius: 34) {
            VStack(alignment: .leading, spacing: 22) {
                HStack {
                    StatusPill(text: model.tunnel.statusLabel, active: model.tunnel.isActive)
                    Spacer()
                    Text("Packet Tunnel")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(tokens.muted)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text(model.tunnel.status == .connected ? "连接安全" : "保持私密")
                        .font(.system(size: 32, weight: .bold, design: .rounded))
                    Text(model.tunnel.message)
                        .font(.subheadline)
                        .foregroundStyle(tokens.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Button(action: model.toggleTunnel) {
                    Label(
                        model.tunnel.isActive ? "断开" : "连接",
                        systemImage: "power"
                    )
                }
                .buttonStyle(PrimaryGlassButtonStyle())
                .disabled(model.busy || model.tunnel.isBusy || model.selectedSubscription == nil)
            }
            .padding(22)
        }
    }

    private var modePicker: some View {
        VStack(alignment: .leading, spacing: 9) {
            Text("运行模式")
                .font(.caption.weight(.semibold))
                .foregroundStyle(tokens.muted)
                .padding(.leading, 4)
            GlassPanel(cornerRadius: 24) {
                Picker(
                    "运行模式",
                    selection: Binding(
                        get: { model.preferences.routingMode },
                        set: { value in model.updatePreferences { $0.routingMode = value } }
                    )
                ) {
                    ForEach(RoutingMode.allCases) { Text($0.label).tag($0) }
                }
                .pickerStyle(.segmented)
                .padding(6)
            }
        }
    }

    private var exitSelector: some View {
        GlassPanel(cornerRadius: 26) {
            VStack(spacing: 0) {
                Menu {
                    ForEach(model.subscriptions) { subscription in
                        Button {
                            model.selectSubscription(subscription.id)
                        } label: {
                            if subscription.id == model.selectedSubscriptionID {
                                Label(subscription.name, systemImage: "checkmark")
                            } else {
                                Text(subscription.name)
                            }
                        }
                    }
                } label: {
                    selectorRow(
                        icon: "tray.full.fill",
                        title: "订阅",
                        value: model.selectedSubscription?.name ?? "先选择订阅"
                    )
                }
                .buttonStyle(.plain)

                Divider().opacity(0.45).padding(.leading, 66)

                Menu {
                    Button {
                        model.selectNode(nil)
                    } label: {
                        if model.selectedNodeName == nil {
                            Label("自动选择", systemImage: "checkmark")
                        } else {
                            Text("自动选择")
                        }
                    }
                    ForEach(model.selectedNodes, id: \.self) { node in
                        Button {
                            model.selectNode(node)
                        } label: {
                            let name = ClashNodeParser.displayName(node)
                            if node == model.selectedNodeName {
                                Label(name, systemImage: "checkmark")
                            } else {
                                Text(name)
                            }
                        }
                    }
                } label: {
                    selectorRow(
                        icon: "point.topleft.down.to.point.bottomright.curvepath",
                        title: "出口",
                        value: model.selectedSubscription == nil
                            ? "请先选择订阅" : model.selectedNodeDisplayName
                    )
                }
                .buttonStyle(.plain)
                .disabled(model.selectedSubscription == nil)
            }
        }
    }

    private func selectorRow(icon: String, title: String, value: String) -> some View {
        HStack(spacing: 14) {
            GlassIcon(systemName: icon)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.caption).foregroundStyle(tokens.muted)
                Text(value)
                    .font(.headline)
                    .foregroundStyle(.primary)
                    .lineLimit(1)
            }
            Spacer()
            Image(systemName: "chevron.up.chevron.down")
                .font(.caption.weight(.semibold))
                .foregroundStyle(tokens.muted)
        }
        .contentShape(Rectangle())
        .padding(16)
    }

    private var healthCards: some View {
        HStack(spacing: 12) {
            GlassPanel(cornerRadius: 22) {
                metric(
                    icon: "server.rack",
                    label: "订阅节点",
                    value: "\(model.selectedNodes.count)",
                    support: model.selectedSubscription?.name ?? "未选择"
                )
            }
            GlassPanel(cornerRadius: 22) {
                metric(
                    icon: "arrow.triangle.branch",
                    label: "分流规则",
                    value: "\(model.routes.count)",
                    support: model.preferences.routingMode.label
                )
            }
        }
    }

    private func metric(icon: String, label: String, value: String, support: String) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: icon).foregroundStyle(tokens.ink)
            Text(label).font(.caption).foregroundStyle(tokens.muted)
            Text(value).font(.title2.bold())
            Text(support).font(.caption2).foregroundStyle(tokens.good).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(17)
    }
}
