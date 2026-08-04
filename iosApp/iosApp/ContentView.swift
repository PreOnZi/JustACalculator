import SwiftUI
import UIKit
import JustACalculatorKit

/// Hosts the Kotlin/Compose UI. `MainViewControllerKt.MainViewController()` is
/// the Swift name Kotlin generates for the top-level `MainViewController()`
/// function in `iosMain/.../MainViewController.kt`.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // Compose owns the full screen and insets itself — see
        // MainViewController.kt. Letting SwiftUI inset here as well would
        // double up, and would also zero out the insets Compose reports, so
        // the screens that position against them would drift.
        ComposeView()
            .ignoresSafeArea(.all)
    }
}
