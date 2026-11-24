import SwiftUI

@main
struct DrivePlayerApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(LibraryStore.shared)
                .environmentObject(PlayerManager.shared)
                .preferredColorScheme(.dark)
        }
    }
}
