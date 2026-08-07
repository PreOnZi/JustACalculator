import SwiftUI

@main
struct iOSApp: App {
    // SwiftUI's App lifecycle has no orientation hook of its own; the delegate
    // is here so UIKit has somewhere to ask. See AppDelegate.swift.
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                // The Compose view draws its own safe-area insets.
                .ignoresSafeArea(.all)
                .ignoresSafeArea(.keyboard)
        }
    }
}
