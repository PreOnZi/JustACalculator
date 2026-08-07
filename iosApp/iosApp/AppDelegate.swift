import SwiftUI
import UIKit
import JustACalculatorKit

/// Answers UIKit's orientation question on behalf of the Compose screens.
///
/// The city and the maze freeze orientation while they are on screen. On
/// Android that is a per-activity setting; iOS has no per-view equivalent, so
/// the shared code raises a flag (`IosOrientationLock`) and this delegate turns
/// it into an answer for `supportedInterfaceOrientationsFor`.
///
/// The lock pins whatever orientation the player was already holding rather
/// than forcing a named one — same as Android. Forcing, say, portrait would
/// spin the device out from under someone playing in landscape, and would tear
/// down and rebuild the GL surface mid-scene to do it.
final class AppDelegate: NSObject, UIApplicationDelegate {

    /// Sampled when the lock is taken; `nil` means no restriction.
    private var lockedMask: UIInterfaceOrientationMask?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        IosOrientationLock.shared.onChange = { [weak self] in
            self?.lockChanged()
        }
        return true
    }

    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        lockedMask ?? unrestrictedMask
    }

    /// Mirrors the `UISupportedInterfaceOrientations` entries in Info.plist.
    /// Both have to agree: UIKit intersects them, so anything allowed here but
    /// absent there is silently dropped.
    private var unrestrictedMask: UIInterfaceOrientationMask {
        UIDevice.current.userInterfaceIdiom == .pad ? .all : .allButUpsideDown
    }

    private func lockChanged() {
        // Kotlin flips the flag during composition, which is not necessarily a
        // context where UIKit may be touched.
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }

            if IosOrientationLock.shared.locked {
                // Sample once. Re-sampling on every notification would let the
                // lock drift if it were ever re-acquired after a rotation.
                if self.lockedMask == nil {
                    self.lockedMask = Self.currentOrientationMask() ?? self.unrestrictedMask
                }
            } else {
                self.lockedMask = nil
            }

            Self.invalidateSupportedOrientations()
        }
    }

    /// The orientation on screen right now, as a single-value mask.
    private static func currentOrientationMask() -> UIInterfaceOrientationMask? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            // A scene that is not foreground-active can report a stale value.
            .first { $0.activationState == .foregroundActive }
            ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first

        switch scene?.interfaceOrientation {
        case .portrait:            return .portrait
        case .portraitUpsideDown:  return .portraitUpsideDown
        case .landscapeLeft:       return .landscapeLeft
        case .landscapeRight:      return .landscapeRight
        default:                   return nil  // .unknown — leave it unrestricted
        }
    }

    /// Tells UIKit to ask again. It caches the previous answer otherwise.
    private static func invalidateSupportedOrientations() {
        let root = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .rootViewController

        if #available(iOS 16.0, *) {
            root?.setNeedsUpdateOfSupportedInterfaceOrientations()
        } else {
            // The 15.0 deployment target still has to be served; this is the
            // pre-16 spelling of the same request.
            UIViewController.attemptRotationToDeviceOrientation()
        }
    }
}
