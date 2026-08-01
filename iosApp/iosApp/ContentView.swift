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
        ComposeView()
    }
}
