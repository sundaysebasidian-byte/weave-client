import SwiftUI

@main
struct WeaveIOSApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .preferredColorScheme(model.preferredColorScheme)
        }
    }
}
