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

## CalculatorScreen: ported

`MainActivity.CalculatorScreen` — 2,257 lines and 64 effect blocks — now lives
in `commonMain/ui/CalculatorScreen.kt` and runs on both platforms. MainActivity
is a 57-line Android entry point; `MainViewController` is its iOS counterpart.
**`PortHarness.kt` is deleted.**

The terms/privacy screen and the story timers run on iOS.

Four dependencies are stubbed behind `UnportedScreens.kt` and render a labelled
"not ported yet" panel on iOS rather than failing silently. Each disappears when
its real implementation lands; nothing at the call site changes.

| Stub | Waiting on |
|---|---|
| `PlatformAdCardStack` | the OpenGL city (~12k lines) |
| `PlatformHomeScreenOverlay` | contacts + mic echo |
| `PlatformDebugPasswordGate` | the city debug menu |
| `createTalkAudioHandler` | AVAudioEngine mic echo (no-op on iOS today) |
| `PlatformCameraPreview` | AVFoundation capture |

## Remaining, in dependency order

1. **Rewrite the ~30 asset call sites** onto the `Assets` seam (the seam and the
   shared asset tree are done; the call sites in `androidMain` still use
   `context.assets.open`). Then move `res/raw` audio into the same tree and
   retire the 72 `R.raw.*` references alongside the audio seam.
2. **Coil 2 → 3** — 9 files use `coil.compose.AsyncImage`. Coil 2 is Android-only;
   Coil 3 (`coil3.*`) is multiplatform. Pairs with the asset URI change above.
3. **Audio** — `MediaPlayer`/`SoundPool` in ~9 files → an `expect` player over
   `AVAudioPlayer`. Covers the 72 `R.raw` voiceover/SFX references.
4. **OpenGL ES** (~12k lines, 8 files) — **foundation done, renderers pending.**

   Measured surface: **65 distinct `gl*` calls**, **44 `GL_` constants**, **65
   nio buffer uses**. Note this is *not* all GLES 2.0 — `Building6Runner` uses
   GLES 3.0 (`glGenVertexArrays`, `glBindBufferBase`, `glUniformBlockBinding`).
   iOS supports ES 3.0, so it ports, but the seam must cover both. Shaders are
   GLSL ES 1.00/3.00 and port verbatim.

   | Layer | State |
   |---|---|
   | `gl/Matrix.kt` — the 8 `android.opengl.Matrix` functions | ✅ shared, 11 tests |
   | `gl/GlBuffer.kt` — `java.nio.FloatBuffer` → direct buffer / pinned array | ✅ shared, 7 tests |
   | `gl/Gl.kt` — 63 calls + 44 constants | ✅ shared, both platforms compile |
   | `PlatformGlSurface` — `GLSurfaceView` → `GLKView`/`EAGL` + `CADisplayLink` | ✅ shared, both compile |
   | Cityglrenderer (4.6k) + CityCctv | ✅ commonMain |
   | Building8Casino | ✅ converted, waits on the city UI |
   | Calculatorcityview (3.9k) | ⬜ **fully converted; blocked only on 9 screen refs** |
   | Building6Runner (3.7k, GLES 3.0), Door4Room (1.8k) | ⬜ |
   | ModelBitmap, GltfSkinnedModel | ⬜ |

   **Calculatorcityview has no Android or JVM APIs left in it.** The joystick is
   Compose `pointerInput`, vibration/prefs/time/orientation are seamed, and the
   `java.util` uses are gone. Moving it to commonMain currently fails on exactly
   **nine unresolved references**, all to screens it launches:

       CityDebugMenu, CityLotteryPopup, DebugPasswordGate, FlappyBirdGame,
       LowVolumeWarning, MazeGame, TankGame, TowerDefenseGame,
       rememberCurrencyIcon

   It is the city hub, so it moves only once those do — or once they are stubbed
   behind `UnportedScreens.kt` the way the ad-card stack is. Several of them
   (FlappyBirdGame, LowVolumeWarning, CityLotteryPopup) look close to portable
   already.

   **Known gap:** `throttleRenderThread` is a no-op on iOS, so the deliberate
   post-Building-4 frame stutter is Android-only until the display link's
   `preferredFramesPerSecond` is varied instead.

   **Known gap:** `LockOrientationWhileVisible` sets a flag on iOS that nothing
   reads yet — the Swift AppDelegate needs to consult `IosOrientationLock` from
   `supportedInterfaceOrientationsFor`. Until then the city will rotate on iOS.

   **The conversion recipe**, validated end to end on `Building8Casino` (40 GL
   calls, 513 lines) — it compiled for iOS with only two unrelated UI
   dependencies left:
   1. `GLES20.` / `GLES30.` → `Gl.`
   2. `ByteBuffer.allocateDirect(x.size * 4)…asFloatBuffer()` → `x.toGlBuffer()`,
      `FloatBuffer` → `GlFloatBuffer`
   3. `GLSurfaceView.Renderer` → `GlRenderer`; drop the `GL10?`/`EGLConfig?`
      parameters from the three overrides
   4. the `AndroidView { GLSurfaceView(...) }` block → `PlatformGlSurface(renderer, modifier)`
   5. `@Volatile` → `kotlin.concurrent.Volatile` (the JVM one is not multiplatform)
   6. `System.nanoTime()` → `nowMillis() * 1_000_000L`

   Building8Casino is converted and stays in `androidMain` only because it calls
   `CityJoystick` (Calculatorcityview) and `ArcadeBrowser` (Building8Games). It
   moves for free once those do.

   **Two behavioural differences the surface host cannot hide**, worth knowing
   before the renderers move:
   - GLKView does not drive itself. GLKViewController normally owns the render
     loop, and there is no controller when the view is hosted in Compose, so a
     `CADisplayLink` supplies the equivalent of `RENDERMODE_CONTINUOUSLY`.
   - GLKView calls its delegate on the **main thread**; GLSurfaceView uses a
     dedicated render thread. The renderers only touch GL inside their
     callbacks so this is invisible to them, but a slow frame blocks the UI on
     iOS in a way it does not on Android.

   All 7 files already use the shared `Matrix`. The renderers still call
   `GLES20.`/`GLES30.` directly and construct `java.nio` buffers — swapping both
   is mechanical now that `Gl` and `GlFloatBuffer` exist and mirror those APIs.

   Two things the `Gl` iOS actual absorbs, so the renderers never see them:
   inside `actual object Gl`, a bare `glFoo(...)` resolves to `Gl.glFoo` and
   recurses — every platform call must be written `platform.gles3.glFoo(...)`.
   And `toCPointer()` needs an explicit type argument
   (`offset.toLong().toCPointer<ByteVar>()`) for the "read from the bound
   buffer at this offset" idiom.

   **iOS GL binding facts, verified by compiling against them** (each of these
   would have made a blind wrapper wrong):

   - The Kotlin/Native package is **`platform.gles3`**, *not*
     `platform.OpenGLES3` as the klib filename suggests. `platform.gles2` and
     `platform.glescommon` exist alongside it. `EAGL` and `GLKit` are also
     available, which is what the surface host will need.
   - **Constants are `Int`, but functions take `UInt`.** `glClear(GL_COLOR_BUFFER_BIT)`
     does not compile; it needs `.toUInt()`. Android is `Int` throughout, so the
     shared `Gl` should expose `Int` (matching the existing call sites) and the
     iOS actual should do the conversion.
   - `glCreateShader`/`glCreateProgram` return `UInt`, so program and shader
     handles need converting at the boundary too.
   - `glGetUniformLocation` accepts a Kotlin `String` directly — no manual
     null-termination needed.

   The 64 call signatures are enumerated by grepping
   `GLES\d*\.(gl[A-Za-z0-9]+)\s*\(` across `ui/screens/`.


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
