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

    /// Sampled when a screen takes the per-screen lock; `nil` means no restriction.
    private var lockedMask: UIInterfaceOrientationMask?

    /// Sampled once, the first time UIKit asks while a scene is actually on
    /// screen. From then on the whole app stays in the orientation it launched
    /// in.
    ///
    /// This is a deliberately blunter policy than the per-screen lock above,
    /// and it exists because rotation has been crashing the app: Compose tears
    /// down and rebuilds the GL surface on every interface-orientation change,
    /// and not rotating at all is the reliable way to avoid that. It masks the
    /// crash rather than fixing it — if the underlying fault is ever found,
    /// deleting `launchMask` restores free rotation.
    private var launchMask: UIInterfaceOrientationMask?

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
        // A screen-level lock still wins — it is sampled at the moment it is
        // taken, so it can only ever agree with or narrow the launch lock.
        if let locked = lockedMask { return locked }
        if let launched = launchMask { return launched }

        // Freeze on the first answer given while a scene is genuinely on screen.
        // UIKit asks this several times during launch, before the window scene
        // has settled; answering from a non-active scene there would pin the
        // default portrait even for someone who started the app in landscape.
        if let now = Self.activeOrientationMask() {
            launchMask = now
            return now
        }
        return unrestrictedMask
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

    /// The orientation on screen right now, but only once a scene is actually
    /// foreground-active. Returns nil during launch, so the caller can leave
    /// things unrestricted until there is a real answer to freeze.
    private static func activeOrientationMask() -> UIInterfaceOrientationMask? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        return mask(for: scene?.interfaceOrientation)
    }

    /// The orientation on screen right now, as a single-value mask.
    private static func currentOrientationMask() -> UIInterfaceOrientationMask? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            // A scene that is not foreground-active can report a stale value.
            .first { $0.activationState == .foregroundActive }
            ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first

        return mask(for: scene?.interfaceOrientation)
    }

    private static func mask(for o: UIInterfaceOrientation?) -> UIInterfaceOrientationMask? {
        switch o {
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
