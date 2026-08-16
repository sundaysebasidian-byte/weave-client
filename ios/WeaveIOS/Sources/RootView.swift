import SwiftUI

struct RootView: View {
    @EnvironmentObject private var model: AppModel
    @State private var selection = 0

    var body: some View {
        TabView(selection: $selection) {
            MonetScene {
                NavigationStack { ConnectionView() }
            }
            .tabItem { Label("连接", systemImage: "power") }
            .tag(0)

            MonetScene {
                NavigationStack { RoutingView() }
            }
            .tabItem { Label("分流", systemImage: "point.3.connected.trianglepath.dotted") }
            .tag(1)

            MonetScene {
                NavigationStack { SubscriptionsView() }
            }
            .tabItem { Label("订阅", systemImage: "server.rack") }
            .tag(2)

            MonetScene {
                NavigationStack { SettingsView() }
            }
            .tabItem { Label("设置", systemImage: "gearshape.fill") }
            .tag(3)
        }
        .toolbarBackground(.ultraThinMaterial, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
        .overlay {
            if model.busy {
                ZStack {
                    Color.black.opacity(0.08).ignoresSafeArea()
                    ProgressView()
                        .controlSize(.large)
                        .padding(24)
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 22))
                }
                .transition(.opacity)
            }
        }
        .alert(
            "Weave",
            isPresented: Binding(
                get: { !model.notice.isEmpty },
                set: { if !$0 { model.notice = "" } }
            )
        ) {
            Button("好") { model.notice = "" }
        } message: {
            Text(model.notice)
        }
        .confirmationDialog(
            "导入局域网订阅？",
            isPresented: Binding(
                get: { model.pendingTransferLink != nil },
                set: { if !$0 { model.pendingTransferLink = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("填写到局域网互传") { model.confirmPendingTransfer() }
            Button("取消", role: .cancel) { model.pendingTransferLink = nil }
        } message: {
            Text("Weave 将连接同一局域网中的发送设备，验证一次性密文后再写入本机加密订阅库。")
        }
        .onOpenURL(perform: model.receiveDeepLink)
    }
}
