import SwiftUI

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // The Compose view draws its own safe-area insets.
                .ignoresSafeArea(.all)
                .ignoresSafeArea(.keyboard)
        }
    }
}
