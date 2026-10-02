import AppKit
import SwiftUI

private enum MacDestination: String, CaseIterable, Identifiable {
    case connection = "连接"
    case subscriptions = "订阅"
    case transfer = "互传"
    case settings = "设置"

    var id: String { rawValue }
    var glyph: WeaveGlyphKind {
        switch self {
        case .connection: .connection
        case .subscriptions: .subscriptions
        case .transfer: .transfer
        case .settings: .settings
        }
    }
}

private enum WeaveGlyphKind: Equatable {
    case connection
    case subscriptions
    case transfer
    case settings
}

struct ContentView: View {
    @State private var destination: MacDestination = .connection

    var body: some View {
        ZStack {
            WeaveImpressionBackdrop()
            HStack(spacing: 0) {
                WeaveSidebar(destination: $destination)
                    .frame(width: 184)
                Rectangle()
                    .fill(Color.weaveStroke)
                    .frame(width: 1)
                ZStack {
                    retainedPage(ConnectionView(), for: .connection)
                    retainedPage(SubscriptionListView(), for: .subscriptions)
                    retainedPage(TransferView(), for: .transfer)
                    retainedPage(SettingsView(), for: .settings)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.weaveCanvas)
            }
        }
        .tint(.weaveAcid)
        .transaction { transaction in
            transaction.animation = nil
            transaction.disablesAnimations = true
        }
        .frame(minWidth: 900, minHeight: 620)
    }

    private func retainedPage<Content: View>(
        _ content: Content,
        for item: MacDestination
    ) -> some View {
        content
            .background(Color.weaveCanvas)
            // A tiny non-zero opacity keeps SwiftUI from deferring the first render
            // of hidden ScrollViews; the active page is opaque and covers them.
            .opacity(destination == item ? 1 : 0.001)
            .allowsHitTesting(destination == item)
            .accessibilityHidden(destination != item)
            .zIndex(destination == item ? 1 : 0)
    }
}

private struct WeaveImpressionBackdrop: View {
    var body: some View {
        Color.weaveCanvas
        .ignoresSafeArea()
    }
}

private struct WeaveSidebar: View {
    @Binding var destination: MacDestination

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 11)
                        .fill(Color.weaveInk)
                    WeaveMark()
                        .stroke(
                            Color.weaveTeal,
                            style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round),
                        )
                        .padding(9)
                }
                .frame(width: 38, height: 38)
                VStack(alignment: .leading, spacing: 1) {
                    Text("WEAVE")
                        .font(.system(size: 17, weight: .black, design: .rounded))
                        .foregroundStyle(Color.weaveInk)
                    Text("PRIVATE NETWORK")
                        .font(.system(size: 8, weight: .bold))
                        .tracking(1.1)
                        .foregroundStyle(Color.weaveMuted)
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 24)

            VStack(spacing: 6) {
                ForEach(MacDestination.allCases) { item in
                    Button {
                        var transaction = Transaction(animation: nil)
                        transaction.disablesAnimations = true
                        withTransaction(transaction) {
                            destination = item
                        }
                    } label: {
                        HStack(spacing: 12) {
                            WeaveGlyph(kind: item.glyph)
                                .frame(width: 22)
                            Text(item.rawValue)
                                .font(.system(size: 14, weight: .semibold))
                            Spacer()
                        }
                        .foregroundStyle(destination == item ? Color.weaveInk : Color.weaveMuted)
                        .padding(.horizontal, 14)
                        .frame(height: 46)
                        .background(
                            RoundedRectangle(cornerRadius: 14)
                                .fill(destination == item ? Color.weavePrimaryContainer : .clear)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)

            Spacer()

            HStack(spacing: 9) {
                Circle()
                    .fill(Color.weaveGood)
                    .frame(width: 8, height: 8)
                VStack(alignment: .leading, spacing: 1) {
                    Text("本地优先")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.weaveInk)
                    Text("订阅与配置仅保存在本机")
                        .font(.caption2)
                        .foregroundStyle(Color.weaveMuted)
                }
            }
            .padding(20)
        }
        .background(Color.weavePaper)
    }
}

private struct WeaveMark: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let p = { (x: CGFloat, y: CGFloat) in
            CGPoint(x: rect.minX + x * rect.width, y: rect.minY + y * rect.height)
        }
        path.move(to: p(0.52, 0.52))
        path.addCurve(to: p(0.39, 0.18), control1: p(0.42, 0.43), control2: p(0.33, 0.28))
        path.addCurve(to: p(0.68, 0.23), control1: p(0.49, 0.09), control2: p(0.62, 0.13))
        path.addCurve(to: p(0.61, 0.51), control1: p(0.76, 0.32), control2: p(0.70, 0.42))
        path.move(to: p(0.48, 0.46))
        path.addCurve(to: p(0.12, 0.43), control1: p(0.39, 0.37), control2: p(0.22, 0.36))
        path.addCurve(to: p(0.35, 0.76), control1: p(0.05, 0.65), control2: p(0.20, 0.82))
        path.addCurve(to: p(0.52, 0.51), control1: p(0.45, 0.72), control2: p(0.52, 0.59))
        path.move(to: p(0.56, 0.48))
        path.addCurve(to: p(0.88, 0.60), control1: p(0.70, 0.42), control2: p(0.86, 0.48))
        path.addCurve(to: p(0.59, 0.80), control1: p(0.93, 0.78), control2: p(0.72, 0.88))
        path.addCurve(to: p(0.50, 0.53), control1: p(0.50, 0.77), control2: p(0.47, 0.63))
        return path
    }
}

private struct WeaveGlyph: View {
    let kind: WeaveGlyphKind

    var body: some View {
        Canvas { context, size in
            let width = min(size.width, size.height)
            let inset = width * 0.15
            let rect = CGRect(
                x: (size.width - width) / 2 + inset,
                y: (size.height - width) / 2 + inset,
                width: width - inset * 2,
                height: width - inset * 2
            )
            var path = Path()
            switch kind {
            case .connection:
                path.addArc(
                    center: CGPoint(x: rect.midX, y: rect.midY + rect.height * 0.08),
                    radius: rect.width * 0.40,
                    startAngle: .degrees(-52),
                    endAngle: .degrees(232),
                    clockwise: false
                )
                path.move(to: CGPoint(x: rect.midX, y: rect.minY))
                path.addLine(to: CGPoint(x: rect.midX, y: rect.midY))
            case .subscriptions:
                for row in 0..<3 {
                    let y = rect.minY + CGFloat(row) * rect.height * 0.38
                    path.addRoundedRect(
                        in: CGRect(x: rect.minX, y: y, width: rect.width, height: rect.height * 0.22),
                        cornerSize: CGSize(width: 3, height: 3)
                    )
                }
            case .transfer:
                path.move(to: CGPoint(x: rect.minX, y: rect.minY + rect.height * 0.32))
                path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY + rect.height * 0.32))
                path.move(to: CGPoint(x: rect.maxX, y: rect.minY + rect.height * 0.32))
                path.addLine(to: CGPoint(x: rect.maxX - rect.width * 0.24, y: rect.minY))
                path.move(to: CGPoint(x: rect.maxX, y: rect.minY + rect.height * 0.32))
                path.addLine(to: CGPoint(x: rect.maxX - rect.width * 0.24, y: rect.midY))
                path.move(to: CGPoint(x: rect.maxX, y: rect.maxY - rect.height * 0.18))
                path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY - rect.height * 0.18))
                path.move(to: CGPoint(x: rect.minX, y: rect.maxY - rect.height * 0.18))
                path.addLine(to: CGPoint(x: rect.minX + rect.width * 0.24, y: rect.maxY))
                path.move(to: CGPoint(x: rect.minX, y: rect.maxY - rect.height * 0.18))
                path.addLine(to: CGPoint(x: rect.minX + rect.width * 0.24, y: rect.midY))
            case .settings:
                for row in 0..<3 {
                    let y = rect.minY + CGFloat(row) * rect.height * 0.5
                    path.move(to: CGPoint(x: rect.minX, y: y))
                    path.addLine(to: CGPoint(x: rect.maxX, y: y))
                }
            }
            context.stroke(
                path,
                with: .color(Color.weaveInk),
                style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round)
            )
            if kind == .settings {
                let knobs = [0.28, 0.68, 0.42]
                for (index, position) in knobs.enumerated() {
                    let y = rect.minY + CGFloat(index) * rect.height * 0.5
                    context.fill(
                        Path(
                            ellipseIn: CGRect(
                                x: rect.minX + rect.width * position - 2.6,
                                y: y - 2.6,
                                width: 5.2,
                                height: 5.2
                            )
                        ),
                        with: .color(Color.weaveInk)
                    )
                }
            }
        }
    }
}

private struct ConnectionView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showSubscriptionSelector = false
    @State private var showNodeSelector = false

    private var isRunning: Bool { model.core.state.isConnected }
    private var statusColor: Color {
        switch model.core.state {
        case .tun, .localProxy: return .weaveGood
        case .failed: return .weaveError
        default: return .weaveMuted
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack(alignment: .bottom, spacing: 16) {
                    WeavePageHeader(eyebrow: "私密网络", title: "Weave")
                    Spacer()
                    Button {
                        // Kept intentionally quiet on macOS; the Android layout uses this
                        // affordance for secondary diagnostics and settings.
                    } label: {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(Color.weaveInk)
                            .frame(width: 36, height: 36)
                            .background(Color.weaveSurfaceVariant, in: Circle())
                    }
                    .buttonStyle(.plain)
                    .help("更多")
                }

                VStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .center) {
                        HStack(spacing: 8) {
                            Circle()
                                .fill(statusColor)
                                .frame(width: 8, height: 8)
                            Text(statusLabel)
                                .font(.system(size: 12, weight: .semibold))
                        }
                        .foregroundStyle(Color.weaveInk)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color.weaveSurfaceVariant, in: Capsule())
                        Spacer()
                        Text(model.core.coreAvailable ? "内核已安装" : "内核不可用")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(Color.weaveMuted)
                    }

                    Text(isRunning ? "连接安全" : "保持私密")
                        .font(.system(size: 30, weight: .semibold, design: .rounded))
                        .foregroundStyle(Color.weaveInk)
                        .padding(.top, 28)
                    Text(model.core.message)
                        .font(.system(size: 13))
                        .foregroundStyle(Color.weaveMuted)
                        .lineLimit(2)
                        .padding(.top, 6)

                    Button {
                        model.toggleConnection()
                    } label: {
                        HStack(spacing: 9) {
                            Image(systemName: isRunning ? "stop.fill" : "power")
                            Text(isRunning ? "断开" : "连接")
                            Spacer()
                            Image(systemName: "arrow.right")
                        }
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(isRunning ? Color.weaveInk : Color.white)
                        .padding(.horizontal, 18)
                        .frame(height: 56)
                        .background(
                            isRunning ? Color.weavePrimaryContainer : Color.weavePrimary,
                            in: RoundedRectangle(cornerRadius: 20)
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(connectDisabled)
                    .opacity(connectDisabled ? 0.45 : 1)
                    .padding(.top, 24)
                }
                .padding(22)
                .weaveCard(radius: 28)

                WeaveSectionLabel("出口")
                    .padding(.horizontal, 2)
                VStack(spacing: 10) {
                    WeavePickerRow(title: "订阅", icon: "square.stack.3d.up.fill") {
                        Button {
                            showSubscriptionSelector = true
                        } label: {
                            pickerValue(model.selectedSubscription?.name ?? "请选择")
                        }
                        .buttonStyle(.plain)
                        .frame(maxWidth: .infinity)
                        .popover(isPresented: $showSubscriptionSelector, arrowEdge: .bottom) {
                            SubscriptionSelectorPopover(
                                subscriptions: model.subscriptions,
                                selectedID: model.selectedSubscriptionID,
                                onSelect: model.selectSubscription
                            )
                        }
                    }
                    WeavePickerRow(title: "节点", icon: "point.3.connected.trianglepath.dotted") {
                        Button {
                            showNodeSelector = true
                        } label: {
                            pickerValue(
                                model.selectedNodeName.map(ClashNodeNames.display) ?? "自动选择"
                            )
                        }
                        .buttonStyle(.plain)
                        .frame(maxWidth: .infinity)
                        .disabled(model.selectedSubscription == nil)
                        .popover(isPresented: $showNodeSelector, arrowEdge: .bottom) {
                            NodeSelectorPopover(
                                nodes: model.selectedNodes,
                                selectedNode: model.selectedNodeName,
                                onSelect: { model.selectedNodeName = $0 }
                            )
                        }
                    }
                }
                .padding(14)
                .weaveCard(radius: 24)

                HStack(spacing: 11) {
                    Image(systemName: "bolt.fill")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Color.weavePrimary)
                        .frame(width: 36, height: 36)
                        .background(Color.weavePrimaryContainer, in: RoundedRectangle(cornerRadius: 12))
                    VStack(alignment: .leading, spacing: 3) {
                        Text("当前出口")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(Color.weaveMuted)
                        Text(model.selectedNodeName.map(ClashNodeNames.display) ?? "自动选择")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Color.weaveInk)
                            .lineLimit(1)
                    }
                    Spacer()
                    Text(model.core.state == .tun ? "双栈 TUN" : "等待连接")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Color.weaveMuted)
                }
                .padding(16)
                .weaveCard(radius: 22)

                HStack(alignment: .top, spacing: 11) {
                    Image(systemName: "info.circle.fill")
                        .foregroundStyle(Color.weaveGood)
                        .padding(.top, 1)
                    Text(model.core.state == .tun
                         ? "全设备 IPv4/IPv6 TUN 已接管流量，DNS 请求被劫持，标准 STUN 端口已阻断。"
                         : "默认使用双栈 TUN；如果 utun、路由或 DNS 未就绪，会拒绝连接而不是让 IPv4 直连。")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.weaveMuted)
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 0)
                }
                .padding(15)
                .background(Color.weaveSecondaryContainer, in: RoundedRectangle(cornerRadius: 18))
            }
            .frame(maxWidth: 920, alignment: .leading)
            .padding(.horizontal, 32)
            .padding(.vertical, 28)
        }
    }

    private var connectDisabled: Bool {
        model.core.state == .starting ||
            ((!model.core.coreAvailable || model.selectedSubscription == nil) && !isRunning)
    }

    private var statusLabel: String {
        switch model.core.state {
        case .tun, .localProxy: return "已保护"
        case .starting: return "正在连接"
        case .failed: return "需要处理"
        case .stopped: return "未连接"
        }
    }

    private func pickerValue(_ value: String) -> some View {
        HStack(spacing: 8) {
            Text(value)
                .foregroundStyle(Color.weaveInk)
                .lineLimit(1)
            Spacer(minLength: 8)
            Image(systemName: "chevron.up.chevron.down")
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(Color.weaveMuted)
        }
    }
}

private struct SubscriptionSelectorPopover: View {
    @Environment(\.dismiss) private var dismiss
    let subscriptions: [MacSubscription]
    let selectedID: UUID?
    let onSelect: (UUID?) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("选择订阅")
                .font(.system(size: 14, weight: .bold))
                .padding(.horizontal, 4)
            if subscriptions.isEmpty {
                Text("还没有订阅")
                    .font(.caption)
                    .foregroundStyle(Color.weaveMuted)
                    .frame(maxWidth: .infinity, minHeight: 72)
            } else {
                ScrollView {
                    LazyVStack(spacing: 5) {
                        ForEach(subscriptions) { subscription in
                            SelectorOption(
                                title: subscription.name,
                                subtitle: "\(subscription.nodeCount) 个节点",
                                selected: subscription.id == selectedID
                            ) {
                                onSelect(subscription.id)
                                dismiss()
                            }
                        }
                    }
                }
                .frame(maxHeight: 280)
            }
        }
        .padding(12)
        .frame(width: 310)
        .background(Color.weavePaper)
    }
}

private struct NodeSelectorPopover: View {
    @Environment(\.dismiss) private var dismiss
    let nodes: [String]
    let selectedNode: String?
    let onSelect: (String?) -> Void
    @State private var query = ""

    private var filteredNodes: [String] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nodes }
        return nodes.filter {
            ClashNodeNames.display($0).localizedCaseInsensitiveContains(trimmed)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("选择节点")
                .font(.system(size: 14, weight: .bold))
                .padding(.horizontal, 4)
            TextField("搜索节点", text: $query)
                .textFieldStyle(.plain)
                .padding(.horizontal, 11)
                .frame(height: 34)
                .background(Color.weaveCanvas, in: RoundedRectangle(cornerRadius: 10))
            ScrollView {
                LazyVStack(spacing: 5) {
                    SelectorOption(
                        title: "自动选择",
                        subtitle: "按延迟自动测试",
                        selected: selectedNode == nil
                    ) {
                        onSelect(nil)
                        dismiss()
                    }
                    ForEach(filteredNodes, id: \.self) { node in
                        SelectorOption(
                            title: ClashNodeNames.display(node),
                            subtitle: nil,
                            selected: node == selectedNode
                        ) {
                            onSelect(node)
                            dismiss()
                        }
                    }
                }
            }
            .frame(height: 300)
        }
        .padding(12)
        .frame(width: 340)
        .background(Color.weavePaper)
    }
}

private struct SelectorOption: View {
    let title: String
    let subtitle: String?
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 13, weight: .semibold))
                        .lineLimit(1)
                    if let subtitle {
                        Text(subtitle)
                            .font(.caption2)
                            .foregroundStyle(Color.weaveMuted)
                    }
                }
                Spacer()
                if selected {
                    Image(systemName: "checkmark")
                        .font(.system(size: 11, weight: .black))
                }
            }
            .foregroundStyle(Color.weaveInk)
            .padding(.horizontal, 11)
            .frame(minHeight: 38)
            .background(
                selected ? Color.weaveAcid : Color.clear,
                in: RoundedRectangle(cornerRadius: 10)
            )
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

private struct WeavePickerRow<Content: View>: View {
    let title: String
    let icon: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundStyle(Color.weaveMuted)
                .frame(width: 20)
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color.weaveMuted)
                .frame(width: 34, alignment: .leading)
            content()
        }
        .padding(.horizontal, 14)
        .frame(height: 44)
        .background(Color.weaveCanvas, in: RoundedRectangle(cornerRadius: 14))
    }
}

private struct SubscriptionListView: View {
    @EnvironmentObject private var model: AppModel
    @State private var showImporter = false
    @State private var editingSubscription: MacSubscription?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                HStack(alignment: .bottom, spacing: 18) {
                    WeavePageHeader(eyebrow: "SOURCES", title: "订阅")
                    Spacer()
                    Button {
                        model.resetDirectImportMessage()
                        showImporter = true
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "plus")
                            Text("添加订阅")
                        }
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Color.weaveInk)
                        .padding(.horizontal, 16)
                        .frame(height: 40)
                        .background(Color.weaveAcid, in: RoundedRectangle(cornerRadius: 13))
                    }
                    .buttonStyle(.plain)
                }

                HStack(spacing: 14) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 15).fill(Color.weaveAcid)
                        Image(systemName: "arrow.triangle.2.circlepath")
                            .font(.system(size: 19, weight: .bold))
                            .foregroundStyle(Color.weaveInk)
                    }
                    .frame(width: 48, height: 48)
                    VStack(alignment: .leading, spacing: 3) {
                        Text("本机订阅已载入")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(Color.weaveInk)
                        Text("共 \(model.subscriptions.reduce(0) { $0 + $1.nodeCount }) 个节点 · 自动刷新可用")
                            .font(.caption)
                            .foregroundStyle(Color.weaveMuted)
                    }
                    Spacer()
                    Text("KEYCHAIN")
                        .font(.system(size: 9, weight: .bold))
                        .tracking(1.2)
                        .foregroundStyle(Color.weaveMuted)
                }
                .padding(19)
                .weaveCard(radius: 22)

                if model.subscriptions.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "server.rack")
                            .font(.system(size: 30))
                            .foregroundStyle(Color.weaveMuted)
                        Text("还没有订阅")
                            .font(.title3.bold())
                        Text("可从链接、二维码图片或配置文件直接导入")
                            .font(.caption)
                            .foregroundStyle(Color.weaveMuted)
                    }
                    .frame(maxWidth: .infinity, minHeight: 210)
                    .weaveCard()
                } else {
                    LazyVStack(spacing: 12) {
                        ForEach(model.subscriptions) { subscription in
                            HStack(spacing: 15) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 15)
                                        .fill(Color.weaveAcid.opacity(0.74))
                                    Text(String(subscription.name.prefix(1)).uppercased())
                                        .font(.system(size: 17, weight: .black, design: .rounded))
                                        .foregroundStyle(Color.weaveInk)
                                }
                                .frame(width: 48, height: 48)
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(subscription.name)
                                        .font(.system(size: 15, weight: .bold))
                                    Text("\(subscription.nodeCount) 个节点 · 本机加密")
                                        .font(.caption)
                                        .foregroundStyle(Color.weaveMuted)
                                }
                                Spacer()
                                Button {
                                    editingSubscription = subscription
                                } label: {
                                    Image(systemName: "pencil")
                                        .foregroundStyle(Color.weaveTeal)
                                        .frame(width: 34, height: 34)
                                        .background(
                                            Color.weaveTeal.opacity(0.10),
                                            in: RoundedRectangle(cornerRadius: 11)
                                        )
                                }
                                .buttonStyle(.plain)
                                .help("编辑订阅")
                                Button {
                                    model.refreshSubscription(subscription)
                                } label: {
                                    if model.refreshingSubscriptionID == subscription.id {
                                        ProgressView()
                                            .controlSize(.small)
                                    } else {
                                        Image(systemName: "arrow.clockwise")
                                    }
                                }
                                .foregroundStyle(Color.weaveTeal)
                                .frame(width: 34, height: 34)
                                .background(
                                    Color.weaveTeal.opacity(0.10),
                                    in: RoundedRectangle(cornerRadius: 11)
                                )
                                .buttonStyle(.plain)
                                .disabled(model.refreshingSubscriptionID != nil)
                                .help("刷新 HTTPS 订阅")
                                Button(role: .destructive) {
                                    model.vault.remove(subscription)
                                } label: {
                                    Image(systemName: "trash")
                                        .foregroundStyle(Color.weaveError)
                                        .frame(width: 34, height: 34)
                                        .background(
                                            Color.weaveError.opacity(0.08),
                                            in: RoundedRectangle(cornerRadius: 11)
                                        )
                                }
                                .buttonStyle(.plain)
                                .help("永久删除订阅")
                            }
                            .padding(15)
                            .weaveCard()
                        }
                    }
                }

                HStack(spacing: 10) {
                    Image(systemName: "lock.shield.fill")
                        .foregroundStyle(Color.weaveGood)
                    Text("订阅正文使用 Keychain 主密钥和 AES-256-GCM 保存在本机。")
                        .font(.caption)
                        .foregroundStyle(Color.weaveMuted)
                }
                .padding(.top, 2)
            }
            .padding(28)
        }
        .sheet(isPresented: $showImporter) {
            DirectSubscriptionImportSheet()
                .environmentObject(model)
        }
        .sheet(item: $editingSubscription) { subscription in
            SubscriptionEditorSheet(subscription: subscription)
                .environmentObject(model)
        }
    }
}

private struct SubscriptionEditorSheet: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var model: AppModel
    let subscription: MacSubscription
    @State private var name: String
    @State private var source: String
    @State private var payload: String
    @State private var message = ""

    init(subscription: MacSubscription) {
        self.subscription = subscription
        _name = State(initialValue: subscription.name)
        _source = State(initialValue: subscription.source)
        _payload = State(initialValue: subscription.payload)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                WeavePageHeader(eyebrow: "EDIT SOURCE", title: "编辑订阅")
                Spacer()
                Button("关闭") { dismiss() }
                    .buttonStyle(.plain)
            }
            TextField("订阅名称", text: $name)
                .textFieldStyle(.roundedBorder)
            TextField("HTTPS 来源（可选）", text: $source)
                .textFieldStyle(.roundedBorder)
                .font(.system(size: 12, design: .monospaced))
            TextEditor(text: $payload)
                .font(.system(size: 12, design: .monospaced))
                .frame(minHeight: 330)
                .padding(8)
                .background(Color.weaveCanvas, in: RoundedRectangle(cornerRadius: 14))
            HStack {
                Text("保存前会再次剥离 Clash 控制面字段并校验节点")
                    .font(.caption)
                    .foregroundStyle(Color.weaveMuted)
                Spacer()
                Button("保存") {
                    model.updateSubscription(id: subscription.id, name: name, source: source, payload: payload)
                    message = model.directImportMessage
                    if message == "订阅已更新" { dismiss() }
                }
                .weavePrimaryButton()
                .frame(width: 120)
            }
            if !message.isEmpty {
                Text(message)
                    .font(.caption)
                    .foregroundStyle(message == "订阅已更新" ? Color.weaveGood : Color.weaveError)
            }
        }
        .padding(24)
        .frame(width: 720, height: 560)
        .background(Color.weavePaper)
    }
}

private struct DirectSubscriptionImportSheet: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var model: AppModel
    @State private var subscriptionName = ""
    @State private var subscriptionLink = ""

    private var trimmedLink: String {
        subscriptionLink.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var importSucceeded: Bool {
        model.directImportMessage.hasPrefix("已")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack(alignment: .top, spacing: 16) {
                ZStack {
                    RoundedRectangle(cornerRadius: 15)
                        .fill(Color.weaveAcid)
                    Image(systemName: "square.and.arrow.down")
                        .font(.system(size: 19, weight: .bold))
                        .foregroundStyle(Color.weaveInk)
                }
                .frame(width: 48, height: 48)
                VStack(alignment: .leading, spacing: 4) {
                    Text("添加订阅")
                        .font(.system(size: 23, weight: .black, design: .rounded))
                        .foregroundStyle(Color.weaveInk)
                    Text("导入前会校验格式和 Mihomo 节点")
                        .font(.caption)
                        .foregroundStyle(Color.weaveMuted)
                }
                Spacer()
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Color.weaveMuted)
                        .frame(width: 30, height: 30)
                        .background(Color.weaveCanvas, in: Circle())
                }
                .buttonStyle(.plain)
                .disabled(model.directImportBusy)
            }

            VStack(alignment: .leading, spacing: 12) {
                WeaveSectionLabel("名称（可选）")
                TextField("留空则使用订阅名称", text: $subscriptionName)
                    .textFieldStyle(.plain)
                    .padding(.horizontal, 14)
                    .frame(height: 44)
                    .background(Color.weaveCanvas, in: RoundedRectangle(cornerRadius: 13))

                WeaveSectionLabel("订阅链接")
                    .padding(.top, 4)
                TextField("HTTPS、URI、Clash YAML 或 weave://lan/…", text: $subscriptionLink)
                    .textFieldStyle(.plain)
                    .font(.system(size: 12, design: .monospaced))
                    .padding(.horizontal, 14)
                    .frame(height: 44)
                    .background(Color.weaveCanvas, in: RoundedRectangle(cornerRadius: 13))

                Button {
                    model.importSubscriptionURL(
                        name: subscriptionName,
                        rawURL: subscriptionLink
                    )
                } label: {
                    HStack {
                        Text("从链接导入")
                        Spacer()
                        Image(systemName: "arrow.down")
                    }
                    .weavePrimaryButton()
                }
                .buttonStyle(.plain)
                .disabled(trimmedLink.isEmpty || model.directImportBusy)
                .opacity(trimmedLink.isEmpty || model.directImportBusy ? 0.46 : 1)
            }
            .padding(17)
            .weaveCard()

            HStack(spacing: 12) {
                DirectImportOption(
                    icon: "qrcode.viewfinder",
                    title: "识别二维码图片",
                    subtitle: "HTTPS、Weave 或 Clash YAML"
                ) {
                    model.importSubscriptionQRCode(name: subscriptionName)
                }
                DirectImportOption(
                    icon: "doc.badge.plus",
                    title: "选择配置文件",
                    subtitle: "Clash / Mihomo YAML"
                ) {
                    model.importSubscriptionFile(name: subscriptionName)
                }
            }
            .disabled(model.directImportBusy)
            .opacity(model.directImportBusy ? 0.56 : 1)

            if model.directImportBusy || !model.directImportMessage.isEmpty {
                HStack(spacing: 10) {
                    if model.directImportBusy {
                        ProgressView()
                            .controlSize(.small)
                    } else {
                        Image(
                            systemName: importSucceeded
                                ? "checkmark.shield.fill"
                                : "exclamationmark.triangle.fill"
                        )
                    }
                    Text(model.directImportMessage)
                        .lineLimit(3)
                }
                .font(.caption)
                .foregroundStyle(importSucceeded ? Color.weaveGood : Color.weaveInk)
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    (importSucceeded ? Color.weaveGood : Color.weaveAcid).opacity(0.12),
                    in: RoundedRectangle(cornerRadius: 14)
                )
            }
        }
        .padding(24)
        .frame(width: 580)
        .background(Color.weavePaper)
    }
}

private struct DirectImportOption: View {
    let icon: String
    let title: String
    let subtitle: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.weaveAcid.opacity(0.72))
                    Image(systemName: icon)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Color.weaveInk)
                }
                .frame(width: 40, height: 40)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 13, weight: .bold))
                    Text(subtitle)
                        .font(.caption2)
                        .foregroundStyle(Color.weaveMuted)
                }
                Spacer(minLength: 6)
                Image(systemName: "chevron.right")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(Color.weaveMuted)
            }
            .foregroundStyle(Color.weaveInk)
            .padding(14)
            .frame(maxWidth: .infinity)
            .background(Color.weavePaper, in: RoundedRectangle(cornerRadius: 17))
            .overlay {
                RoundedRectangle(cornerRadius: 17)
                    .stroke(Color.weaveStroke, lineWidth: 1)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

private struct TransferView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                WeavePageHeader(eyebrow: "LOCAL TRANSFER", title: "局域网互传")

                HStack(alignment: .top, spacing: 18) {
                    VStack(alignment: .leading, spacing: 16) {
                        WeaveCardTitle(
                            icon: "qrcode",
                            title: "从这台 Mac 导出",
                            subtitle: "一次性二维码或链接"
                        )
                        Text("传输 \(model.subscriptions.count) 个订阅。密钥只存在于二维码和链接中。")
                            .font(.caption)
                            .foregroundStyle(Color.weaveMuted)

                        if model.transferLink.isEmpty {
                            Button {
                                model.startExport()
                            } label: {
                                HStack {
                                    Text("生成传输码")
                                    Spacer()
                                    Image(systemName: "arrow.up.right")
                                }
                                .weavePrimaryButton()
                            }
                            .buttonStyle(.plain)
                            .disabled(model.subscriptions.isEmpty || model.transferBusy)
                        } else {
                            QRCodeView(value: model.transferLink)
                                .frame(width: 210, height: 210)
                                .padding(12)
                                .background(.white, in: RoundedRectangle(cornerRadius: 18))
                                .frame(maxWidth: .infinity)
                            Text(model.transferLink)
                                .font(.caption2.monospaced())
                                .foregroundStyle(Color.weaveMuted)
                                .lineLimit(2)
                                .textSelection(.enabled)
                            Text("确认短码：\(model.transferConfirmationCode)")
                                .font(.system(size: 16, weight: .bold, design: .monospaced))
                                .foregroundStyle(Color.weaveTeal)
                            HStack {
                                Button("复制链接") { model.copyTransferLink() }
                                Button("立即失效", role: .destructive) { model.stopExport() }
                            }
                        }
                    }
                    .padding(20)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .weaveCard()

                    VStack(alignment: .leading, spacing: 16) {
                        WeaveCardTitle(
                            icon: "square.and.arrow.down",
                            title: "导入到这台 Mac",
                            subtitle: "链接或二维码图片"
                        )
                        TextField("weave://lan/…", text: $model.importLink)
                            .textFieldStyle(.plain)
                            .font(.system(size: 12, design: .monospaced))
                            .padding(.horizontal, 14)
                            .frame(height: 46)
                            .background(Color.weaveCanvas, in: RoundedRectangle(cornerRadius: 14))
                        TextField("发送设备显示的 6 位短码", text: $model.importConfirmationCode)
                            .textFieldStyle(.plain)
                            .font(.system(size: 12, design: .monospaced))
                            .padding(.horizontal, 14)
                            .frame(height: 46)
                            .background(Color.weaveCanvas, in: RoundedRectangle(cornerRadius: 14))
                        Button {
                            model.importFromCurrentLink()
                        } label: {
                            HStack {
                                Text("从链接导入")
                                Spacer()
                                Image(systemName: "arrow.down")
                            }
                            .weavePrimaryButton()
                        }
                        .buttonStyle(.plain)
                        .disabled(model.importLink.isEmpty || model.importConfirmationCode.count != 6 || model.transferBusy)

                        Button {
                            model.importQRCodeImage()
                        } label: {
                            HStack {
                                Image(systemName: "photo")
                                Text("识别二维码图片")
                            }
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Color.weaveInk)
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .background(Color.weaveCanvas, in: RoundedRectangle(cornerRadius: 14))
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(20)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .weaveCard()
                }

                if !model.transferMessage.isEmpty {
                    HStack(spacing: 10) {
                        Image(systemName: "checkmark.shield.fill")
                        Text(model.transferMessage)
                    }
                    .font(.caption)
                    .foregroundStyle(Color.weaveGood)
                    .padding(15)
                    .background(Color.weaveGood.opacity(0.1), in: RoundedRectangle(cornerRadius: 15))
                }

                Text("仅接受私有局域网地址 · 传输一次或 5 分钟后失效 · HTTP 不承载明文")
                    .font(.caption2)
                    .foregroundStyle(Color.weaveMuted)
            }
            .padding(28)
        }
    }
}

private struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    private let rows = [
        ("版本", "0.1.0-alpha09", "number"),
        ("架构", "Apple Silicon arm64", "cpu"),
        ("本地存储", "Keychain + AES-256-GCM", "lock.fill"),
        ("VPN 模式", "Mihomo 原生双栈 TUN · 失败闭锁", "network"),
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                WeavePageHeader(eyebrow: "PREFERENCES", title: "设置")
                VStack(spacing: 0) {
                    ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                        HStack(spacing: 13) {
                            Image(systemName: row.2)
                                .foregroundStyle(Color.weaveMuted)
                                .frame(width: 22)
                            Text(row.0)
                                .font(.system(size: 14, weight: .semibold))
                            Spacer()
                            Text(row.1)
                                .font(.system(size: 13))
                                .foregroundStyle(Color.weaveMuted)
                        }
                        .padding(.horizontal, 17)
                        .frame(height: 54)
                        if index < rows.count - 1 {
                            Divider().padding(.leading, 52)
                        }
                    }
                }
                .weaveCard()

                HStack(spacing: 12) {
                    Image(systemName: model.core.state.isConnected ? "network" : "lock.shield")
                        .foregroundStyle(model.core.state.isConnected ? Color.weaveGood : Color.weaveMuted)
                    VStack(alignment: .leading, spacing: 3) {
                        Text("系统代理接管")
                            .font(.system(size: 14, weight: .semibold))
                        Text(model.core.state == .tun
                             ? "全设备 IPv4/IPv6 TUN · DNS 劫持 · STUN 防泄漏"
                             : model.core.state == .localProxy
                             ? "HTTP/HTTPS/SOCKS → 127.0.0.1:7890；PAC 和原设置会自动恢复"
                             : "TUN 未就绪时拒绝连接；关闭下方开关才会使用本地代理")
                            .font(.caption)
                            .foregroundStyle(Color.weaveMuted)
                    }
                    Spacer()
                    Text("自动")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(Color.weaveTeal)
                }
                .padding(17)
                .weaveCard()

                VStack(alignment: .leading, spacing: 10) {
                    Toggle(isOn: Binding(
                        get: { model.tunEnabled },
                        set: { model.setTunEnabled($0) },
                    )) {
                        VStack(alignment: .leading, spacing: 3) {
                            Text("强制全设备双栈 TUN")
                                .font(.system(size: 14, weight: .semibold))
                            Text("接管 IPv4、IPv6、UDP 和 DNS；失败时拒绝连接")
                                .font(.caption)
                                .foregroundStyle(Color.weaveMuted)
                        }
                    }
                    .toggleStyle(.switch)
                    .disabled(model.core.state.isActive)

                    Text("关闭后才会允许本地 HTTP/HTTPS/SOCKS 代理模式；该模式不保证 IPv4、IPv6 或 DNS 全部经过 Weave。")
                        .font(.caption2)
                        .foregroundStyle(Color.weaveMuted)
                }
                .padding(17)
                .weaveCard()
            }
            .padding(28)
        }
    }
}

private struct WeavePageHeader: View {
    let eyebrow: String
    let title: String

    init(eyebrow: String, title: String) {
        self.eyebrow = eyebrow
        self.title = title
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(eyebrow)
                .font(.system(size: 9, weight: .bold))
                .tracking(1.6)
                .foregroundStyle(Color.weaveMuted)
            Text(title)
                .font(.system(size: 30, weight: .black, design: .rounded))
                .foregroundStyle(Color.weaveInk)
        }
    }
}

private struct WeaveSectionLabel: View {
    let value: String

    init(_ value: String) {
        self.value = value
    }

    var body: some View {
        Text(value)
            .font(.system(size: 11, weight: .bold))
            .tracking(0.8)
            .foregroundStyle(Color.weaveMuted)
    }
}

private struct WeaveCardTitle: View {
    let icon: String
    let title: String
    let subtitle: String

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 13).fill(Color.weaveAcid)
                Image(systemName: icon)
                    .foregroundStyle(Color.weaveInk)
                    .font(.system(size: 16, weight: .bold))
            }
            .frame(width: 42, height: 42)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 15, weight: .bold))
                Text(subtitle).font(.caption2).foregroundStyle(Color.weaveMuted)
            }
        }
    }
}

private struct WeaveCardModifier: ViewModifier {
    let radius: CGFloat

    func body(content: Content) -> some View {
        content
            .background(Color.weavePaper, in: RoundedRectangle(cornerRadius: radius))
            .overlay {
                RoundedRectangle(cornerRadius: radius)
                    .stroke(Color.weaveStroke, lineWidth: 1)
            }
    }
}

private extension View {
    func weaveCard() -> some View {
        modifier(WeaveCardModifier(radius: 20))
    }

    func weaveCard(radius: CGFloat) -> some View {
        modifier(WeaveCardModifier(radius: radius))
    }

    func weavePrimaryButton() -> some View {
        font(.system(size: 14, weight: .bold))
            .foregroundStyle(Color.white)
            .padding(.horizontal, 17)
            .frame(maxWidth: .infinity)
            .frame(height: 46)
            .background(Color.weavePrimary, in: RoundedRectangle(cornerRadius: 14))
    }
}

private extension NSColor {
    static func weaveDynamic(
        name: String,
        light: (CGFloat, CGFloat, CGFloat),
        dark: (CGFloat, CGFloat, CGFloat)
    ) -> NSColor {
        NSColor(name: name) { appearance in
            let isDark = appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
            let color = isDark ? dark : light
            return NSColor(
                srgbRed: color.0 / 255,
                green: color.1 / 255,
                blue: color.2 / 255,
                alpha: 1
            )
        }
    }
}

private extension Color {
    static let weaveInk = Color(nsColor: .weaveDynamic(
        name: "WeaveInk",
        light: (29, 37, 45),
        dark: (233, 238, 242)
    ))
    // Android's minimal-light tokens: a cool slate action color and quiet sea-glass secondary.
    static let weavePrimary = Color(red: 47 / 255, green: 72 / 255, blue: 88 / 255)
    static let weavePrimaryContainer = Color(red: 220 / 255, green: 231 / 255, blue: 238 / 255)
    static let weaveSecondaryContainer = Color(red: 220 / 255, green: 234 / 255, blue: 228 / 255)
    static let weaveSurfaceVariant = Color(red: 233 / 255, green: 237 / 255, blue: 241 / 255)
    static let weaveAcid = Color(red: 220 / 255, green: 231 / 255, blue: 238 / 255)
    static let weaveTeal = Color(red: 76 / 255, green: 106 / 255, blue: 97 / 255)
    static let weaveCanvas = Color(nsColor: .weaveDynamic(
        name: "WeaveCanvas",
        light: (244, 246, 248),
        dark: (14, 19, 24)
    ))
    static let weavePaper = Color(nsColor: .weaveDynamic(
        name: "WeavePaper",
        light: (251, 252, 253),
        dark: (23, 30, 37)
    ))
    static let weaveMuted = Color(nsColor: .weaveDynamic(
        name: "WeaveMuted",
        light: (93, 104, 115),
        dark: (166, 177, 187)
    ))
    static let weaveStroke = Color(nsColor: .weaveDynamic(
        name: "WeaveStroke",
        light: (209, 216, 222),
        dark: (58, 67, 75)
    ))
    static let weaveGood = Color(red: 61 / 255, green: 111 / 255, blue: 99 / 255)
    static let weaveError = Color(red: 186 / 255, green: 26 / 255, blue: 26 / 255)
}
