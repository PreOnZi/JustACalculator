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

The last Android-only screens are stubbed behind `UnportedScreens.kt` and render
a labelled "not ported yet" panel on iOS rather than failing silently. Each one
disappears when its real implementation lands; nothing at the call site changes.

**Delete a seam the moment its dependency ports.** A *missing* seam fails to
compile and is loud; a *stale* seam compiles fine and quietly keeps showing a
placeholder for work that is already finished. Three of them went stale at once
(`PlatformAdCardStack`, `PlatformDebugPasswordGate`, `PlatformBuilding6Runner`)
and one of those was blocking entry to the whole city.

| Stub | Waiting on |
|---|---|
| `PlatformDoor4Room` | the room itself; its texture seam is done |
| `PlatformModelViewer` | SceneKit — the one interactive 3D viewer |

## Remaining

**Every screen is ported.** `commonMain` holds ~52.7k lines; the ~2.7k left in
`androidMain` are platform actuals, `MainActivity`, and the broadcast receivers
Android needs for scheduled notifications. There is no porting backlog.

One stub survives on purpose: `PlatformModelViewer`, the single interactive 3D
model viewer, which uses SceneView/Filament. Its iOS counterpart is SceneKit —
a rewrite rather than a port, and it is one screen.

Still open, none of it porting work:

- **Never create a `GlFloatBuffer` inside a draw call.** On Android
  `toGlBuffer()` allocates a direct `ByteBuffer` per frame — wasteful but ART
  reclaims it. On iOS each buffer **pins a FloatArray for its lifetime**, and
  pinned objects can never be moved or freed. The city renderer created ~6 per
  frame at its draw sites; at 33 fps that is a few hundred permanently-pinned
  arrays a second, so the GC's work per collection climbed without bound. The
  city looked fine for a moment, then collapsed to seconds per frame at 100%
  CPU — and looked like a frame-pacing problem, which it was not.

  Per-frame geometry now goes through the renderer's slotted `scratch()`
  buffers, which grow once and are reused. Constant geometry (the night scrim
  quad) is built lazily and held. If a draw path ever needs a new buffer,
  something is wrong.

- **The iOS Simulator cannot hardware-accelerate OpenGL ES.** Profiling the
  city there shows `gldMergeScanlines2x2` inside `GLRendererFloat` — Apple's
  *software* rasteriser — as the hottest symbol by a wide margin. The city runs
  at 100% CPU and a few frames per second in the simulator no matter what the
  code does; the calculator screen, which uses no GL, sits at 0%. **Simulator
  frame rate says nothing about this app.** Measure the city on a device.

- **Static city geometry is in VBOs.** It used to draw from client-side arrays,
  so the driver re-read every vertex from CPU memory on every draw call
  (`glDrawArrays_IMM_ES2Exec` in the profile). Per-frame geometry still uses
  client arrays through `scratch()`, where a VBO re-uploaded each frame buys
  nothing. One trap: `glVertexAttribPointer` captures the ARRAY_BUFFER binding
  **at call time**, so any client-side pointer set while a VBO is bound becomes
  an offset into it — the textured meshes' UVs need an explicit unbind first.

- **Do not pad the iOS root for the Dynamic Island.** Insetting there
  letterboxes the app and shows bare window as white bars top and bottom. The
  screens own their insets: the calculator layouts pair `statusBarsPadding()`
  with the `navigationBarsPadding()` that was already there. Both report zero
  on Android, where the window is not edge-to-edge.

- **What has and has not been run.** The app has been built and launched on
  iPhone and iPad simulators, and the calculator, the city and the intro all
  render correctly. Nothing has run on real hardware. The parts with no
  compile-time check on their correctness are still unverified in any
  meaningful sense: the `CVOpenGLESTextureCache` path in `Door4Room`, the
  face-landmark conventions in `Building7VanityRoom`, the MapKit view, the
  camera and microphone. And see the simulator note above — its frame rate
  says nothing about the city.
- **iOS orientation lock** — `LockOrientationWhileVisible` sets a flag nothing
  reads. The Swift AppDelegate needs to consult `IosOrientationLock` from
  `supportedInterfaceOrientationsFor`, or the city will rotate on iOS.
- **iOS polish** — app icon (`iosApp/iosApp/Assets.xcassets/AppIcon.appiconset`
  is an empty placeholder), launch screen, and a real `DEVELOPMENT_TEAM` in
  `iosApp/Configuration/Config.xcconfig` for device builds and TestFlight.

### OpenGL ES (~12k lines, 8 files) — **done except `Door4Room`.**

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
| Building8Casino | ✅ commonMain |
| Calculatorcityview (3.9k) | ✅ commonMain |
| Building6Runner (3.7k, GLES 3.0) | ✅ commonMain |
| Door4Room (1.8k) | ⬜ external texture |
| ModelBitmap | ⬜ offscreen EGL pbuffer; seamed as `rememberModelIcon` |
| GltfSkinnedModel | ✅ commonMain — GLB parser rewritten |

**GltfSkinnedModel needed a parser rewrite, not the GL recipe.** Two shared
helpers came out of it, both reusable:

- `gl/LittleEndian.kt` — little-endian reads over a `ByteArray`, replacing
  `java.nio.ByteBuffer.order(LITTLE_ENDIAN)`. Fixed-endian on purpose: glTF
  is little-endian regardless of host, so native order would happen to work
  on both current targets and break on a big-endian one.
- `gl/Json.kt` — a `JsonObj`/`JsonArr` work-alike over
  kotlinx-serialization, mirroring the `org.json` method names so the ~30
  call sites ported unchanged. Same trick as the `Prefs` seam.

Calculatorcityview — the city hub — is in `commonMain`, and so is everything it
launches: CityDebugMenu, DebugPasswordGate, FlappyBirdGame, LowVolumeWarning,
CityLotteryPopup, TowerDefenseGame, TankGame and MazeGame all moved with it.
MazeGame's sensors run on CoreMotion through the `DeviceTilt` seam.

**Known gap:** `throttleRenderThread` is a no-op on iOS, so the deliberate
post-Building-4 frame stutter is Android-only. Everything else about frame
pacing now goes through `PlatformGlSurface`'s `targetFps`, which sets the
display link's `preferredFramesPerSecond` — without that cap the main-thread
render loop starved the coroutine driving the aerial→city transition and the
intro hung partway through at >10% CPU.

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


### Still open beyond the screens

- **iOS orientation lock** — `LockOrientationWhileVisible` sets a flag on iOS
  that nothing reads. The Swift AppDelegate needs to consult `IosOrientationLock`
  from `supportedInterfaceOrientationsFor`, or the city will rotate on iOS.
- **iOS polish** — app icon (`iosApp/iosApp/Assets.xcassets/AppIcon.appiconset`
  is an empty placeholder), launch screen, and a real `DEVELOPMENT_TEAM` in
  `iosApp/Configuration/Config.xcconfig` for device builds and TestFlight.

## Heat and battery

The app runs hot, so sustained work matters more than download size.

- **The iOS render loop now pauses when the app leaves the screen.** This is
  not just battery: iOS **terminates** an app that issues OpenGL commands while
  backgrounded, so the display link has to be down before the app suspends.
  Android already got this from `GLSurfaceView.onPause/onResume`.
- **The city pauses behind full-screen overlays.** `PlatformGlSurface` takes a
  `paused` flag; the city passes the `overlayOpen` it already computes. It used
  to render the whole scene at full rate behind every minigame, for as long as
  the player was in one. Pausing keeps the context and the built scene, so
  returning is instant.

Still open, in rough order of likely benefit:

- **Back-face culling is disabled** (`glDisable(GL_CULL_FACE)` in
  `Cityglrenderer.onSurfaceCreated`). On closed building geometry that is
  roughly double the fragment work. It is off deliberately — the .obj winding
  is unreliable, as `ModelBitmap` notes — so enabling it needs a careful look
  at every building for holes before it can ship.
- **Frustum culling now runs on the main camera pass** as well as the CCTV
  feed — it reuses the bounding spheres and `inFeedFrustum` that were already
  built for the feed. Skipped in aerial mode, where nearly everything is in
  shot, and never applied to a toppling building, which sweeps far outside the
  bounds recorded for it.
- Sensors, location and camera sessions were checked and all stop correctly on
  dispose.

**Kotlin/Native does not clean up after you the way the JVM does.** A device
Time Profiler in Release showed a dedicated GC thread at 12.4% and six more
worker threads busy, on a scene that renders fine. The cause was ordinary
idiomatic Kotlin in the per-frame path:

- `for ((i, m) in meshes.withIndex())` allocates an `IndexedValue` **per mesh
  per pass**. The JVM's escape analysis removes those; Kotlin/Native's does
  not. Four such loops over a few thousand meshes at 33 fps is hundreds of
  thousands of objects a second. Use `for (i in meshes.indices)`.
- `FloatArray(16)` matrices allocated per frame are now held as fields.

`onDrawFrame` should allocate **nothing**. If it does, the GC bill shows up as
CPU that no renderer optimisation can touch.

**Kotlin/Native does not clean up after you the way the JVM does.** A device
Time Profiler in Release showed a dedicated GC thread at 12.4% and six more
worker threads busy, on a scene that renders fine. The cause was ordinary
idiomatic Kotlin in the per-frame path:

- `meshes.withIndex()` allocates an `IndexedValue` **per mesh per pass**. The
  JVM's escape analysis removes those; Kotlin/Native's does not. Four such
  loops over a few thousand meshes at 33 fps is hundreds of thousands of
  objects a second. Use `for (i in meshes.indices)`.
- `FloatArray(16)` matrices allocated per frame are now held as fields.

`onDrawFrame` should allocate **nothing**. If it does, the GC bill shows up as
CPU that no renderer optimisation can touch.

## Asset weight

Shipped assets are ~56 MB (down from ~89 MB); the rest of the repo —
`design-sources/`, `iosApp/`, tests, `.github/` — does not reach the app.

Checked and clean, so do not go looking again:

- **No unused assets.** The ones that look unreferenced (`bridge5.obj`,
  `debris7.obj`, `spikes3.obj`…) are loaded by constructed names like
  `"models/bridge/bridge${i+1}.obj"`.
- **No duplication** between `commonMain/assets` and `composeResources`.
- `androidMain/res/raw` holds only `keep.xml`.

**Do not convert the voiceover to mono.** It has been tried and reverted once.
The channels carry genuinely different content — the L−R difference peaks at
−3.3 dB and averages −22 dB on vo034 — so these are true stereo recordings,
not dual-mono. Collapsing them loses audio and can phase-cancel. Lowering the
stereo bitrate is the safe lever if the ~19 MB of voiceover ever has to shrink.

**Watch decoded size, not file size.** Several images were trivial on disk but
enormous in memory — `filters/background.jpg` was 3870x5796, which is 85 MB
once decoded to ARGB. All five have been scaled to display size:

| | was | now | decoded |
|---|---|---|---|
| `background.jpg` | 3870x5796 | 1240x1858 | 85.6 → 8.8 MB |
| `social01.webp` | 4000x3000 | 1080x810 | 45.8 → 3.3 MB |
| `kytka.webp` | 3504x2336 | 1080x720 | 31.2 → 3.0 MB |
| `news2.png` | 2560x2110 | 1024x844 | 20.6 → 3.3 MB |
| `news5.png` | 2486x1566 | 1024x646 | 14.9 → 2.5 MB |

The quiz articles (`articles/0*.png`, 1920x1920) were **left alone on purpose**:
they are ~50 KB each already, only one is on screen at a time, and re-encoding
them through ffmpeg ballooned them from 0.4 MB to 2.9 MB — the originals are
optimised PNGs and a naive re-encode writes full RGB. Same applies to
`sprites/keys/` (21 files, 33 KB each).

The social-feed videos are already lean — `socialvid01` is 1.0 MB / 8.6s and
`socialvid02` 0.3 MB / 1.6s. Nothing to do there.

`models/mutebutton.obj` is 3.9 MB and looks like an over-tessellated button.
It is not: the mute button is the *outside* of an entire room, and the geometry
is the interior. Leave it.

## Notes

- The "ads" are in-fiction; there is no AdMob/Play Services dependency to port.
- `sceneview` (Filament) is used by exactly one file (`MazeGame`); on iOS the
  natural counterpart is SceneKit or a small GLES renderer reusing the code
  from item 4.
