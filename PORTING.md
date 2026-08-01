# iOS port — architecture and state of play

*Just a Calculator* is being ported to iOS via **Compose Multiplatform**, keeping
Android and iOS on one codebase. Work happens on the `ios-port` branch.

## Build commands

Android Studio's bundled JDK is the only JVM on this machine:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer

./gradlew :app:assembleDebug                    # Android APK
./gradlew :app:compileKotlinIosSimulatorArm64   # typecheck common + iOS (fast)
./gradlew :app:iosSimulatorArm64Test            # shared tests on the simulator
./gradlew :app:testDebugUnitTest                # the same tests on the JVM

# Full iOS app:
cd iosApp && xcodebuild -project iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
xcrun simctl install <device> <path>/JustACalculator.app
xcrun simctl launch <device> com.fictioncutshort.justacalculator
```

`DEVELOPER_DIR` is needed because `xcode-select` still points at
CommandLineTools. The permanent fix is one command, run once, requiring a
password:

```bash
sudo xcode-select -s /Applications/Xcode.app
```

**Kotlin/Native compiles klibs without Xcode** — only the final framework *link*
needs it. So `compileKotlinIosSimulatorArm64` is the fast feedback loop for
verifying that shared code is genuinely platform-free.

> **Simulator gotcha:** if `xcrun simctl list runtimes` is empty despite runtimes
> being installed, `pkill -f CoreSimulatorService`. The daemon caches the
> developer dir it started under and does not honour `DEVELOPER_DIR`.

> **Gotcha:** `by rememberUpdatedState(...)` needs an explicit
> `import androidx.compose.runtime.getValue`. The error it produces —
> "Property delegate must have a 'getValue(Nothing?, KProperty0<*>)' method" —
> does not mention the missing import.

> **Gotcha:** after moving a file between source sets, `rm -rf app/build/kotlin`.
> The incremental compiler caches source-set membership and reports phantom
> "Unresolved reference" errors for symbols that are plainly there.

## Module layout

```
app/src/
├── commonMain/kotlin/      shared Kotlin — the port's destination
│   └── .../platform/       the expect/actual seams (see below)
├── commonMain/composeResources/   fonts, drawables (replaces res/)
├── commonMain/assets/      game assets, shared by APK and .app bundle
├── androidMain/kotlin/     Android actuals + not-yet-ported UI
├── androidMain/{res,AndroidManifest.xml}
└── iosMain/kotlin/         iOS actuals + MainViewController entry point

iosApp/                     Xcode project (SwiftUI shell)
├── iosApp/iOSApp.swift     @main
├── iosApp/ContentView.swift  hosts ComposeUIViewController
├── iosApp/Info.plist       usage strings + CADisableMinimumFrameDurationOnPhone
└── Configuration/Config.xcconfig   TEAM_ID / BUNDLE_ID / APP_NAME
```

`iosX64` is deliberately absent — Compose Multiplatform 1.11+ no longer publishes
for the Intel simulator.

## The platform seams

Everything Android-specific goes through `com.fictioncutshort.justacalculator.platform`.
When porting a file, reach for these rather than inventing a new abstraction.

| Seam | Replaces | File |
|---|---|---|
| `AppContext` | `android.content.Context` (typealias on Android) | `Prefs.kt` |
| `Prefs` / `openPrefs(name)` | `SharedPreferences` / `getSharedPreferences` | `Prefs.kt` |
| `applicationScope()` | `context.applicationContext` | `Prefs.kt` |
| `currentAppContext()` | `LocalContext.current` | `Ui.kt` |
| `screenMetrics()` | `LocalConfiguration.current` | `Ui.kt` |
| `vibrateDevice()` / `vibrate()` | `Vibrator`, `VibrationEffect` | `Ui.kt`, `util/Vibration.kt` |
| `nowMillis()` | `System.currentTimeMillis()` | `Platform.kt` |
| `formatFixed/formatScientific` | `String.format("%.10f", …)` | `Platform.kt` |
| `logDebug` / `logWarn` | `android.util.Log.d/w` | `Platform.kt` |
| `currentTimeOfDay()` | `java.util.Calendar` | `Platform.kt` |
| `Assets.readBytes/readText/list/uri` | `context.assets.*`, `file:///android_asset/` | `Assets.kt` |
| `AppInit.initialize(ctx)` | — (holds the app-wide context) | `AppInit.kt` |
| `createSoundPlayer(path)` / `Sounds.path(name)` | `MediaPlayer.create(ctx, R.raw.x)`, `SoundPool.load` | `Sound.kt` |
| `PlatformWebView` | `AndroidView { WebView(…) }` | `WebView.kt` |
| `PlatformCameraPreview` | `CameraPreview(lifecycleOwner = …)` | `CameraView.kt` |
| `LocalNotifications` | `AlarmManager` + `NotificationCompat` | `Notifications.kt` |
| `hasPermission` | `ContextCompat.checkSelfPermission` | `Permissions.kt` |
| `openExternalUrl` | `Intent(ACTION_VIEW)` | `ExternalLinks.kt` |
| `writeUserVisibleFile` | `MediaStore` Downloads write | `UserFiles.kt` |
| `appPackageSizeBytes` | `applicationInfo.sourceDir` length | `UserFiles.kt` |
| `installImageLoader` | Coil 2 `ImageLoaderFactory` | `ImageLoading.kt` |
| `TypingClicker` | the one `TalkAudioHandler` method EffectsController needs | `TypingClicker.kt` |
| `OnAppLifecycleEvent` | `LifecycleEventObserver` | `Lifecycle.kt` |
| `rememberPermissionRequest` | `rememberLauncherForActivityResult` | `Lifecycle.kt` |

`Prefs` mirrors the `SharedPreferences` shape *exactly* (same method names, same
fluent `edit()…commit()`), so ~750 existing call sites moved without edits. The
Android actual delegates straight through, so **shipped Play Store saves keep
loading**. iOS maps each preference file to its own `NSUserDefaults` suite.

## Done

- Gradle converted to KMP + Compose Multiplatform 1.11.1 (Kotlin 2.3.0, AGP 8.13.2).
- **~13.8k lines in `commonMain`**, compiling for Android *and* both iOS targets:
  calculator engine, story/step system, state machine, all persistence stores,
  word game, 3D maths, and 16 UI files.
- Android APK still builds and is behaviourally unchanged.
- **The iOS app builds, installs and runs in the Simulator.** `iosApp/` holds a
  SwiftUI shell hosting `ComposeUIViewController`; 2,614 shared-code symbols
  link in, and all 283 game assets ship in the bundle.
- `PortHarness.kt` — a temporary working calculator assembled from shared
  pieces, proving the pipeline end to end. **Delete it once `CalculatorScreen`
  is ported.**
- 10 shared tests (`commonTest`) pass identically on the JVM and the iOS
  simulator, covering the arithmetic and the number-formatting seam most likely
  to diverge between platforms.
- **Audio fully ported.** All 67 clips moved out of `res/raw` into the shared
  asset tree and all 73 `R.raw.*` references retired. Android plays them through
  MediaPlayer/SoundPool from assets, iOS through AVAudioPlayer.
- **The real portrait layout runs on iOS.** `PortraitCalculatorContent`,
  `Landscapecalculatorlayout`, `Browseroverlay` and `Calculatorcomponents` are
  shared, driven by the real `CalculatorActions` state and persistence.

### Two things to be careful of

- **`MediaPlayer` is not always audio.** `Door4Room` and `TankGame` use it to
  drive video Surfaces (`setSurface`, `prepareAsync`, `setAudioAttributes`).
  Those stay on the real MediaPlayer; only their genuine audio goes through
  [SoundPlayer].
- **iOS audio session is set to Playback** so narration is not silenced by the
  ringer switch, matching Android's behaviour.

### Assets

One source tree, `app/src/commonMain/assets`, feeds both platforms: Android via
`sourceSets["main"].assets`, iOS via a folder reference in the Xcode project that
lands them at `<bundle>/assets/`. Relative paths are therefore identical
(`"models/stickman.obj"`), and the [Assets] seam is deliberately **synchronous** —
the GL renderers and audio players load from threads with no coroutine scope.

## The path to deleting `PortHarness.kt`

`MainActivity.CalculatorScreen` (2,257 lines, **64 LaunchedEffect/DisposableEffect
blocks**) is the last big piece. It owns the terms/privacy screen, the timers
that animate the story, and every overlay. Until it is ported, iOS shows the
real calculator but no story.

Its dependencies — 15 of 18 shared:

| Dependency | State |
|---|---|
| AutoProgressEffects | ✅ commonMain |
| BrowserEffects | ✅ commonMain |
| DormancyManager | ✅ commonMain |
| EffectsController | ✅ commonMain |
| Consolewindow | ✅ commonMain |
| Browseroverlay | ✅ commonMain |
| Landscapecalculatorlayout | ✅ commonMain |
| PausedCalculatorOverlay | ✅ commonMain |
| PhoneOverlay | ✅ commonMain |
| PortraitCalculatorContent | ✅ commonMain |
| ScambleGameOverlay | ✅ commonMain |
| Filecreation | ✅ commonMain |
| Calculatorcomponents | ✅ commonMain |
| Adcardstack | ⬜ **blocked on the GL city** — it launches CalculatorCityView, BeepCheckScreen and VoiceoverManager. Its 25 drawables are already on Compose Resources. |
| LetterBlockGame | ✅ commonMain |
| HomeScreenOverlay | ⬜ needs TalkAudioHandler, PhonebookContact, CalcFakeNotification |
| util/Notifications | ✅ commonMain |
| TalkAudioHandler | ⬜ `AudioRecord`/`AudioTrack` realtime mic echo — hard |
| Camerapreview | ⬜ CameraX → AVFoundation — hard (placeholder ships today) |

## Remaining, in dependency order

1. **Rewrite the ~30 asset call sites** onto the `Assets` seam (the seam and the
   shared asset tree are done; the call sites in `androidMain` still use
   `context.assets.open`). Then move `res/raw` audio into the same tree and
   retire the 72 `R.raw.*` references alongside the audio seam.
2. **Coil 2 → 3** — 9 files use `coil.compose.AsyncImage`. Coil 2 is Android-only;
   Coil 3 (`coil3.*`) is multiplatform. Pairs with the asset URI change above.
3. **Audio** — `MediaPlayer`/`SoundPool` in ~9 files → an `expect` player over
   `AVAudioPlayer`. Covers the 72 `R.raw` voiceover/SFX references.
4. **OpenGL ES** (~12k lines, 8 files) — all hand-written **GLES 2.0 with GLSL ES
   1.00**, which iOS still supports, so the shaders and draw calls port nearly
   verbatim. What must be replaced: `GLSurfaceView` → `GLKView`/`CAEAGLLayer`
   hosted in a `UIKitView`, and `java.nio.FloatBuffer` → Kotlin/Native memory.
   Files: `Cityglrenderer` (4.6k), `Building6Runner` (3.7k), `Door4Room` (1.8k),
   `Building8Casino`, `CityCctv`, `ModelBitmap`, `GltfSkinnedModel`, `ObjLoader`.
5. **`android.graphics`** — `Bitmap`/`Canvas`/`Paint` in 13 files. Most uses are
   procedural texture generation and can move to Compose's own `ImageBitmap` +
   `Canvas`, which are multiplatform.
6. **Camera + face filters** (`Building7VanityRoom`, `Camerapreview`, `Door4Room`)
   — CameraX → `AVCaptureSession`; ML Kit face detection → **Vision**
   (`VNDetectFaceLandmarksRequest`), which is on-device and needs no dependency.
7. **Map** (`Building5Map`) — osmdroid → MapKit, or keep raster OSM tiles and
   draw them into a Compose canvas to preserve the exact look.
8. **Misc services** — WebView → `WKWebView`; contacts → `CNContactStore`;
   location → `CLLocationManager`; notifications + `AlarmManager` →
   `UNUserNotificationCenter`; `MediaStore` saves → `PHPhotoLibrary`;
   sensors (`MazeGame`) → `CoreMotion`; `Toast` → an in-app snackbar.
9. **`MainActivity.kt`** (2.2k lines) — split into a common `App()` composable
   plus thin Android/iOS entry points. This is what replaces `PortHarness.kt`
   and switches the iOS app from a demo to the real game.
10. **iOS polish** — app icon (`iosApp/iosApp/Assets.xcassets/AppIcon.appiconset`
    is currently an empty placeholder), launch screen, and a real
    `DEVELOPMENT_TEAM` in `iosApp/Configuration/Config.xcconfig` for device
    builds and TestFlight.

## Notes

- The "ads" are in-fiction; there is no AdMob/Play Services dependency to port.
- `sceneview` (Filament) is used by exactly one file (`MazeGame`); on iOS the
  natural counterpart is SceneKit or a small GLES renderer reusing the code
  from item 4.
