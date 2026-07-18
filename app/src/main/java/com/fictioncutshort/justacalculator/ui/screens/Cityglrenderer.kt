package com.fictioncutshort.justacalculator.ui.screens

import android.content.res.AssetManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// CityGLRenderer  —  Calculator City
//
//   Columns X:  C1=-300  C2=-100  C3=+100  C4=+300
//   Rows    Z:  RA=-400  RB=-200  RC=0  RD=+200  RE=+400
//   BW=80 (half-width X), BD=60 (half-depth Z) → rectangular buildings ~160×120
//   Chamfer ch=12 on all building corners → button-like rounded shape
//
//   Digit buildings  (rows B/C/D, cols 1/2/3): 7,8,9 / 4,5,6 / 1,2,3  — cream
//   Function buildings (row A cols 1/2/3): C  ()  %  — gray-beige
//   Operator buildings (col 4 all rows):  /  *  −  +  =  — dark gray
//   Damaged buildings (row E cols 1/2/3): DEL  0  .  — washed-out colors
//
//   Lava:  z ≈ -500 to -860 (360 deep)   Green terrain north of lava
//   Sun:   vertical disc to NE in sky, only visible during gameplay
// ─────────────────────────────────────────────────────────────────────────────

// Point lights the shader can carry at once. The city has ~30 (street lamps,
// building-corner lamps, and the lit windows of every unexplored building), so
// the nearest MAX_LIGHTS to the camera are uploaded each frame. Keep this
// comfortably above the number that can plausibly reach one spot, or lamps pop in
// and out of the set as the player walks and the light seems to follow them.
private const val MAX_LIGHTS = 12

class CityGLRenderer(private val assets: AssetManager? = null) : GLSurfaceView.Renderer {

    // ── The key light ─────────────────────────────────────────────────────────
    // Exactly one directional light at any moment, fixed in WORLD space so it
    // never moves with the player: the sun by day, the moon by night, crossfaded
    // by darknessLevel. Each is drawn in the sky along its own bearing, so the
    // light always agrees with the body you can see.
    private val SUN = norm(0.42f, 0.68f, -0.60f)     // high, north-east
    private val MOON = norm(-0.50f, 0.62f, 0.60f)    // opposite quarter: south-west
    private val SUN_COL  = floatArrayOf(1.00f, 0.96f, 0.88f)
    private val MOON_COL = floatArrayOf(0.46f, 0.55f, 0.78f)
    // The city's cream is already 0.96 albedo, so key + ambient must stay at or
    // under 1.0 or every sunlit face clips to flat white.
    private val SUN_STRENGTH  = 0.62f
    private val MOON_STRENGTH = 0.30f

    // Sky bodies are re-centred on the camera every frame, so they behave like
    // bodies at infinity: never closer, always the same bearing, with the city
    // passing in front of them as the player walks.
    private val SKY_DIST = 2200f

    // How far a lamp / lit window throws light.
    private val LAMP_LIGHT_RADIUS   = 190f
    private val WINDOW_LIGHT_RADIUS = 230f

    private fun norm(x: Float, y: Float, z: Float): FloatArray {
        val l = sqrt(x*x + y*y + z*z)
        return floatArrayOf(x/l, y/l, z/l)
    }

    // Camera — written from Compose thread, read on GL thread
    @Volatile var camX = 0f;      @Volatile var camY = 1300f
    @Volatile var camZ = 0f;      @Volatile var camYaw = 0f
    @Volatile var camPitch = -90f; @Volatile var lavaShift = 0f
    @Volatile var fov = 80f;       @Volatile var aerialMode = true
    @Volatile var introLookZ = 0f
    @Volatile var aerialBlend = 1f
    // Third-person look-at camera (used during gameplay)
    @Volatile var useLookAt  = false
    @Volatile var lookAtX    = 0f
    @Volatile var lookAtY    = 32f
    @Volatile var lookAtZ    = 0f

    // Player orb position (rendered only when showPlayer=true)
    @Volatile var playerX    = -200f
    @Volatile var playerZ    =  150f
    @Volatile var showPlayer = false

    // Building scale — 1f during aerial intro, 2f once walking starts
    @Volatile var buildingHeightScale = 1f
    @Volatile var needsRebuild = false
    @Volatile var radAngle    = 0f      // degrees — drives the spinning arc ring on the RAD button
    @Volatile var isLandscape = false   // true when device is in landscape orientation
    @Volatile var bridgePieces = 0      // 0-9: each completed building adds one bridge segment
    @Volatile var b1DoorGreen = false   // true after Building 1 TD is completed
    @Volatile var b2DoorGreen = false   // true after Building 2 maze is completed
    @Volatile var b3DoorGreen = false   // true after Building 3 tank game is completed
    @Volatile var b5EntranceGlow = false // true after Building 5 — RGB-lit Building 8 door
    // 0..3: each step spreads the grid one third of the way from CELL=200
    // (compact calculator-keypad layout, intro aerial framing) to CELL=260
    // (full city). Buildings, doors, and roof labels always render the same
    // 3D models — only their positions and the visibility of the aerialSkip
    // detail (sidewalks, lamps, cameras) change between stages.
    @Volatile var cellStage: Int = 3
    // 0..1 — composited as a dark-blue alpha overlay over the whole frame so the
    // city gets gloomier as the player completes buildings. Atmospheric only;
    // colors and shapes still read.
    @Volatile var darknessLevel = 0f

    private var prog=0; private var aPos=0; private var uMVP=0
    private var uCol=0; private var uFog=0; private var uAerial=0; private var uGray=0
    private var uShift=0; private var uTip=0; private var uPivot=0
    private var aUV=0; private var uTexSampler=0; private var uTexOn=0
    // Model textures, keyed by the file name the MTL's map_Kd names. Uploaded once,
    // on the GL thread, when the models load.
    private val modelTextures = HashMap<String, Int>()
    private var uLit=0; private var uDark=0; private var uKeyDir=0; private var uKeyCol=0
    private var uCamPos=0; private var uLightPos=0; private var uLightRad=0
    // False if GL_OES_standard_derivatives is unavailable — we then run the old
    // flat shader and skip all lighting work.
    private var lightingOn = true

    // Every warm light source in the city, collected while the scene is built:
    // street lamps, the per-building corner lamps, and one light for each
    // building's windows. Entries are [x, y, z, radius, digit], where digit is
    // 1..9 for a building's windows (that light goes out once the building has
    // been explored) and -1 for a lamp, which always burns.
    private val lampLights = mutableListOf<FloatArray>()
    private val lightPosBuf = FloatArray(MAX_LIGHTS * 3)
    private val lightRadBuf = FloatArray(MAX_LIGHTS)

    private data class Mesh(
        val buf: FloatBuffer, val mode: Int, val cnt: Int,
        val r: Float, val g: Float, val b: Float, val a: Float = 1f,
        val fog: Float = 0.65f, val lava: Boolean = false,
        val aerialSkip: Boolean = false,   // skip during aerial intro
        val radArc: Boolean = false, val arcAngle: Float = 0f,  // spinning arc ring segment
        val glow: Boolean = false,         // additive blend, fades in with darknessLevel
        val lamp: Boolean = false,         // lamp bulb/halo glow — stays dark by day, warms in only at dusk
        val softShadow: Boolean = false,   // alpha blend, dark ground shadow under buildings
        val noAO: Boolean = false,         // force uAerial=1 for self-lit / metallic fixtures
        val windowDigit: Int = -1,         // 1..9 → main-building window panel, colour driven per-frame
        val radShell: Boolean = false,     // Building 10's outer wall/lid — see-through from inside
        val radDoor: Boolean = false,      // Building 10's black door panel — dropped once opened
        val sky: Boolean = false,          // sun/sky body — re-centred on the camera, never shaded
        val skyNight: Boolean = false,     // sky body belonging to the night (the moon)
        val crack: Boolean = false,        // a tear in the sky — only during the collapse
        val tex: Int = 0,                  // GL texture — 0 for the flat-coloured majority
        val uv: FloatBuffer? = null        // its UVs, in step with buf
    )
    private val meshes = mutableListOf<Mesh>()

    // Building 10 (the mute button) in world space — its centre and outer radius.
    // Set by addRadButton; used each frame to tell whether the camera is inside it
    // (in which case its shell is drawn transparent, so the city still reads).
    private var radBx = 0f
    private var radBz = 0f
    private var radOuterR = 0f
    private var radDoorTopY = 0f
    // ── The city coming down ─────────────────────────────────────────────────
    // 0 = intact, 1 = gone. Driven by CalculatorCityView once the player has been
    // inside the mute button long enough. Each building is given its own moment to
    // go, so the skyline collapses raggedly rather than sinking as one slab.
    @Volatile var collapse = 0f
    // [firstMeshIdx, lastMeshIdx, cx, cz, startAt, seed] per collapsible building.
    private val collapseGroups = mutableListOf<FloatArray>()
    private var collapseMark = 0

    // World-space AABBs of the furniture inside Building 10, [cx, cz, halfX, halfZ].
    // Read by CalculatorCityView so the player can't walk through the shelves.
    @Volatile var radPropFootprints: List<FloatArray> = emptyList()

    // ── The CCTV desk ────────────────────────────────────────────────────────
    // The three computer monitors on the desk inside Building 10 show live views of
    // the city, taken from the security cameras on the buildings. See CityCctv.
    // The screens are found in the model by material: the blue panels inset in each
    // monitor's black bezel (mutebutton.blend → Material.006, three of them, one per
    // monitor). NOT the whiteboards on stands over by the wall — those are the other
    // white panels in the room, and they stay white.
    private val cctv = CityCctv()
    private val SCREEN_MTL = "Material.006"
    // Feeds re-rendered per frame, round robin. One is enough: with 12 cells every
    // view still refreshes ~5x a second, which is what CCTV looks like anyway.
    private val CCTV_FEEDS_PER_FRAME = 1
    // Per-mesh bounding sphere [cx, cy, cz, r], parallel to `meshes`. Only the CCTV
    // passes use it — a feed camera down in the streets sees a small slice of the
    // city, and without the cull each screen costs a whole extra city.
    private var meshSpheres = FloatArray(0)
    private val frustum = Array(6) { FloatArray(4) }

    // Security cameras — one per building corner, drawn dynamically each frame
    // because they yaw to track the player. Each entry: [px, py, pz, outX, outZ]
    // where (outX, outZ) is the diagonal outward direction from the building corner.
    private val cameraMounts = mutableListOf<FloatArray>()
    // Per-frame world-space verts of the camera red lights (Material.002/.003),
    // captured in drawCamerasModel and re-drawn additively in PASS 2.
    private var cameraLightArrs: List<FloatArray> = emptyList()

    // Blender-authored meshes loaded from app/src/main/assets/models/. Each list
    // entry is one material group; the loader splits the OBJ on `usemtl` so each
    // group can be drawn with its own color (or treated as glow / additive).
    private var lampOnGroups:  List<ObjGroup> = emptyList()
    private var lampOffGroups: List<ObjGroup> = emptyList()
    private var cameraOnGroups: List<ObjGroup> = emptyList()
    private var doorGroups:    List<ObjGroup> = emptyList()
    private var bridgeGroups:   Array<List<ObjGroup>> = arrayOf()   // 9 custom bridge pieces
    private var muteButtonGroups: List<ObjGroup> = emptyList()      // building-10 mute button body
    private var muteButtonBounds: List<ObjBounds> = emptyList()     // its props, for collision
    private var damagedGroupsA: List<ObjGroup> = emptyList()   // buildd1
    private var damagedGroupsB: List<ObjGroup> = emptyList()   // buildd2
    private var damagedGroupsC: List<ObjGroup> = emptyList()   // buildd3
    private var mainBuildingGroups: List<ObjGroup> = emptyList()
    // Stickman figures scattered OUTSIDE the city (south rubble, between the
    // damaged buildings, and standing on the lava). Tagged aerialSkip so they
    // only appear once the player is down in the city (cellStage == 3,
    // aerialMode == false) — never from the intro/fly-over aerial pose.
    private var stickmanGroups:  List<ObjGroup> = emptyList()
    private var stickman2Groups: List<ObjGroup> = emptyList()
    // Night-mode monster (deployed after Building 3). Position/spin driven each
    // frame from CalculatorCityView; drawn dynamically like the cameras.
    private var monsterGroups:   List<ObjGroup> = emptyList()
    // Per-frame world-space verts (+colour) of the monster's bright face groups,
    // captured in drawMonster and re-drawn additively in PASS 2 to illuminate them.
    private var monsterFaceArrs: List<Pair<FloatArray, FloatArray>> = emptyList()
    @Volatile var monsterActive = false
    @Volatile var monsterX = 0f
    @Volatile var monsterZ = 0f
    @Volatile var monsterAngle = 0f                 // degrees, yaw around +Y (faces travel dir)
    @Volatile var monsterScale = 13f                // model is ±4 tall → ~104 units at 13
    @Volatile var monsterYBob   = 0f                // extra world-Y (legacy; unused by slam)
    // Body-slam tilt: the monster pivots over its feet by monsterTilt degrees,
    // toppling toward the horizontal direction (monsterTiltX, monsterTiltZ).
    @Volatile var monsterTilt   = 0f
    @Volatile var monsterTiltX  = 0f
    @Volatile var monsterTiltZ  = 1f
    // Extra monster instances for the "clone swarm" attack. Each entry is a
    // world transform [x, z, angleDeg, scale]; drawn alongside the main monster.
    @Volatile var monsterClones: List<FloatArray> = emptyList()
    // Model-space Y of the bright face centroid (auto-measured); feet sit at -4.
    @Volatile var monsterFaceModelY = 0f
    // World-space Y of the monster's face, for the catch zoom. Feet are at y=0,
    // so a model-space height h maps to world (h + 4)*scale.
    val monsterFaceY: Float get() = (monsterFaceModelY + 4f) * monsterScale
    // Add this to a desired travel heading (atan2(vx,vz) in degrees) to get the
    // yaw that points the model's actual face along that heading. Auto-measured
    // from the mesh (centroid of the bright face materials) so it's correct for
    // whatever orientation the model was authored in.
    @Volatile var monsterFaceYawOffsetDeg = 0f
    // Per-interactive-building (digits 1..9) "completed" flag — drives the
    // window colour to BLACK once the user has cleared that building. Read
    // every frame in the render loop so no scene rebuild is needed when it
    // flips. Indexed by digit-1.
    @Volatile var buildingCompleted: BooleanArray = BooleanArray(9)
    private var debrisGroups:  Array<List<ObjGroup>> = Array(10) { emptyList() }
    // Per-interactive-building (digits 1..9) slide-up fraction. 0=closed, 1=fully
    // retracted. Animated from CalculatorCityView during the door-open →
    // walk-through sequence. Building 10's door is not one of these — it's a black
    // panel cut into the mute button's own wall (see radDoorOpen).
    @Volatile var doorOpenFraction: FloatArray = FloatArray(9)
    // Door instances populated in buildScene/addBuilding; consumed each frame by
    // drawDoors. Each entry: [doorCenterX, doorCenterY (=dh*0.5), doorCenterZ, faceIdx, scaleX, scaleY, scaleZ, slideMax, digitIndex(0..8)]
    private val doorMounts = mutableListOf<FloatArray>()
    // Per-material night-time Kd lookup. The cameranight model shares geometry
    // with cameraon, so we only need its colors — we blend them with the day
    // colors at draw time based on darknessLevel.
    private var cameraNightColors: Map<String, FloatArray> = emptyMap()
    // Per-group pre-uploaded buffers — recomputed for each lamp instance because
    // the model is translated into world space at scene build time. Cameras are
    // re-transformed every frame (because they yaw), so they don't pre-upload.
    private val LAMP_SCALE   = 22f   // model is ±1 unit tall → footprint sits at y=0 when offset = scale
    private val CAMERA_SCALE = 4f
    private val LAMP_Y_OFFSET = LAMP_SCALE  // keep these matched so the lamp base stays on the ground
    private val LAMP_YAW_DEG = 225f  // 45° base + 180° flip; rotates lamp around the vertical Y axis

    // ── Shaders ───────────────────────────────────────────────────────────────
    // Every mesh in this renderer is built in WORLD space (uMVP is only
    // projection × view), so the vertex position doubles as the world position.
    // That lets the fragment shader recover a true per-face normal from the
    // screen-space derivatives of it — no normal attribute, no change to a single
    // vertex buffer or draw call, and it covers the meshes that are rebuilt every
    // frame (doors, monster, cameras) for free.
    // vRel is the position RELATIVE TO THE CAMERA, not the absolute world position.
    // That matters: the city runs out to +-2200, and at that magnitude a mediump
    // float in the fragment stage has about 1-unit resolution, so the sub-pixel
    // dFdx/dFdy of an absolute coordinate quantises to zero, cross() returns the
    // zero vector, and normalize() of that is NaN - which is what blew every
    // surface out to white. Relative to the camera the numbers are small and the
    // derivatives survive. The normal is identical either way: subtracting a
    // constant does not change a derivative.
    private val VS = """
        uniform mat4 uMVP;
        uniform vec3 uCamPos;
        // Per-draw world-space displacement. Used to drop, shove and shake a single
        // building as the city falls apart; zero for everything else.
        uniform vec3 uShift;
        // ...and the same building TIPPING OVER: a rotation of uTip.w radians about
        // the horizontal axis uTip.xyz, through the world point uPivot (the edge the
        // building hinges on). w = 0 for everything that isn't currently going over.
        // Rotating here rather than baking it into the vertices keeps every mesh
        // buffer static; the fragment shader takes its normals from the derivatives
        // of the position, so the lighting rolls over with the building for free.
        uniform vec4 uTip;
        uniform vec3 uPivot;
        attribute vec4 aPosition;
        // Only the textured groups (a material with a map_Kd — the whiteboards'
        // image planes) bind this; everything else leaves the array disabled and
        // never looks at vUV.
        attribute vec2 aUV;
        varying float vDepth;
        varying float vY;
        varying vec3  vRel;
        varying vec2  vUV;
        void main(){
            vUV = aUV;
            vec3 p = aPosition.xyz;
            if (uTip.w != 0.0) {
                vec3 q = p - uPivot;
                float c = cos(uTip.w);
                float s = sin(uTip.w);
                // Rodrigues rotation of q about the unit axis uTip.xyz.
                q = q * c + cross(uTip.xyz, q) * s + uTip.xyz * dot(uTip.xyz, q) * (1.0 - c);
                p = q + uPivot;
            }
            p += uShift;
            gl_Position = uMVP * vec4(p, 1.0);
            vDepth = gl_Position.z / gl_Position.w;
            vY = p.y;
            vRel = p - uCamPos;
        }""".trimIndent()

    // Lit shader. Needs GL_OES_standard_derivatives (universal on Android, but if
    // it's missing we fall back to FS_FLAT below and the game looks as it did).
    //   * sun      - one directional light, aimed from the sun disc in the sky
    //   * lamps    - up to MAX_LIGHTS point lights, the nearest street/building
    //                lamps to the camera, which take over as night falls
    //   * ambient  - sky fill, cool and dim at night, so unlit faces still read
    private val FS_LIT = """
        #extension GL_OES_standard_derivatives : enable
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
        uniform vec4  uColor;
        uniform float uFog;
        uniform float uAerial;
        uniform float uGray;
        uniform float uLit;
        uniform float uDark;
        uniform vec3  uKeyDir;
        uniform vec3  uKeyCol;
        uniform vec3  uCamPos;
        uniform vec3  uLightPos[$MAX_LIGHTS];
        uniform float uLightRad[$MAX_LIGHTS];
        uniform sampler2D uTex;
        uniform float uTexOn;
        varying float vDepth;
        varying float vY;
        varying vec3  vRel;
        varying vec2  vUV;
        void main(){
            // An image, where there is one, stands in for the flat colour — and then
            // takes the same sun, the same lamps, the same night as every other
            // surface in the city, because that is all that happens below.
            vec3 albedo = uTexOn > 0.5 ? texture2D(uTex, vUV).rgb : uColor.rgb;
            vec3 col;
            if (uLit > 0.5) {
                // Flat (faceted) normal straight off the triangle, in camera-relative
                // space (see VS). If the cross product is degenerate - a sliver of a
                // triangle, or a face seen exactly edge-on - fall back to straight up
                // rather than normalizing a zero vector into NaN, which renders white.
                vec3 g = cross(dFdx(vRel), dFdy(vRel));
                float glen = length(g);
                vec3 N = glen > 1e-8 ? g / glen : vec3(0.0, 1.0, 0.0);
                // Culling is disabled, so faces can point either way - flip the
                // normal toward the viewer rather than letting walls go black.
                // The view vector is simply -vRel: the camera is at the origin here.
                if (dot(N, -vRel) < 0.0) N = -N;

                float day = 1.0 - uDark;
                // ONE key light, fixed in world space: sun by day, moon by night.
                // Its direction and colour come in already crossfaded, so nothing
                // about the lighting depends on where the player is standing.
                float ndl = max(dot(N, uKeyDir), 0.0);
                vec3 key = uKeyCol * ndl;
                vec3 amb = mix(vec3(0.13, 0.16, 0.26), vec3(0.38, 0.39, 0.43), day);

                // Lamps and the lit windows of unexplored buildings. Each carries
                // its own radius; a radius of 0 means "off" (branch-free).
                vec3 lamps = vec3(0.0);
                for (int i = 0; i < $MAX_LIGHTS; i++) {
                    float rad = max(uLightRad[i], 0.001);
                    float on  = step(0.5, uLightRad[i]);
                    // Lights are brought into the same camera-relative space.
                    vec3  d   = (uLightPos[i] - uCamPos) - vRel;
                    float dist = length(d);
                    float att = clamp(1.0 - dist / rad, 0.0, 1.0);
                    att = att * att * on;
                    float nl = max(dot(N, d / max(dist, 0.001)), 0.0);
                    lamps += vec3(1.00, 0.82, 0.45) * att * (0.35 + 0.65 * nl) * 0.75;
                }
                // Several can overlap; cap the total so a junction doesn't blow out.
                lamps = min(lamps, vec3(1.3)) * uDark;

                col = albedo * (amb + key + lamps);
            } else {
                // Unlit - lines, lava, the arcs, glows, shadow blobs, the sky, and
                // the whole aerial intro. Keeps the original fake vertical-gradient
                // "AO" so none of that artwork shifts.
                float ao = mix(0.76 + clamp(vY/300.0,0.0,1.0)*0.24, 1.0, uAerial);
                col = albedo * ao;
            }
            float fog = clamp(vDepth * uFog, 0.0, 0.55);
            vec3 fogC = vec3(0.29, 0.22, 0.16);
            col = mix(col, fogC, fog);
            // Easter-egg grayscale (code 1134206): desaturate the whole city.
            float luma = dot(col, vec3(0.299, 0.587, 0.114));
            col = mix(col, vec3(luma), uGray);
            gl_FragColor = vec4(col, uColor.a);
        }""".trimIndent()

    // Pre-lighting fallback: flat colour with a fake vertical gradient for "AO".
    private val FS_FLAT = """
        precision mediump float;
        uniform vec4  uColor;
        uniform float uFog;
        uniform float uAerial;
        uniform float uGray;
        uniform sampler2D uTex;
        uniform float uTexOn;
        varying float vDepth;
        varying float vY;
        varying vec3  vRel;
        varying vec2  vUV;
        void main(){
            vec3 albedo = uTexOn > 0.5 ? texture2D(uTex, vUV).rgb : uColor.rgb;
            float fog = clamp(vDepth * uFog, 0.0, 0.55);
            float ao  = mix(0.76 + clamp(vY/300.0,0.0,1.0)*0.24, 1.0, uAerial);
            vec3 fogC = vec3(0.29, 0.22, 0.16);
            vec3 col  = albedo * ao;
            col = mix(col, fogC, fog);
            float luma = dot(col, vec3(0.299, 0.587, 0.114));
            col = mix(col, vec3(luma), uGray);
            gl_FragColor = vec4(col, uColor.a);
        }""".trimIndent()

    // ── Grid constants ────────────────────────────────────────────────────────
    // Compose drives BW/BD between AERIAL (matches original top-down look) and GROUND
    // (slimmer → wider streets) via 3 discrete cuts during the intro fade. Doors are
    // pinned to GROUND — invisible from straight overhead in aerial mode, flush at ground.
    val BW_AERIAL = 85f
    val BD_AERIAL = 72f
    val BW_GROUND = 60f
    val BD_GROUND = 50f

    @Volatile var BW    = BW_AERIAL
    @Volatile var BD    = BD_AERIAL
    @Volatile var CELL  = 260f

    // Active cell pitch — interpolated by cellStage so each cut spreads the
    // grid one third of the way from the compact calculator-keypad layout
    // (CELL=200) to the full city (CELL=260). Stage 0 = 200, stage 3 = CELL.
    private fun activeCell(): Float = 200f + (CELL - 200f) * cellStage / 3f
    private val C1: Float get() = -activeCell() * 1.5f
    private val C2: Float get() = -activeCell() * 0.5f
    private val C3: Float get() =  activeCell() * 0.5f
    private val C4: Float get() =  activeCell() * 1.5f
    private val RA: Float get() = -activeCell() * 2f
    private val RB: Float get() = -activeCell() * 1f
    private val RC: Float        =  0f
    private val RD: Float get() =  activeCell() * 1f
    private val RE: Float get() =  activeCell() * 2f

    // City boundary (volatile via getters because BD/BW shift with the
    // applyFootprint stages during the intro).
    private val CITY_N: Float get() = RA - BD
    private val CITY_S: Float get() = RE + BD
    private val CITY_W: Float get() = C1 - BW
    private val CITY_E: Float get() = C4 + BW

    // Features
    private val LAVA_S: Float get() = CITY_N - 40f
    private val LAVA_N: Float get() = LAVA_S - 360f
    // The lava's surface. It used to be drawn at y=20 — a sheet floating twenty units
    // above the ground plane, which nothing ever revealed, because nothing ever went
    // through it. Then the bridge started falling into it and you could watch the
    // pieces sink past a lava surface hanging in the air above them. It sits on the
    // ground now, a hair above it so it doesn't z-fight the ground quad, and the
    // bridge (which is placed against this) came down with it.
    private val LAVA_Y = 0.4f
    // Extra walkable lane west of the city. Building 7 sits in the westmost
    // column with a WEST-facing door; without this gap the door opens straight
    // into the perimeter wall and is unreachable. Pushing the wall ~one street
    // width west opens a lane the player can step into to trigger the door.
    val WEST_LANE = 150f
    private val WALL_X: Float get() = CITY_W - 20f - WEST_LANE

    private val BH = 280f

    // Interactive buildings: [digit, cx, cz, height, doorFace (0=S,1=N,2=E,3=W)]
    // Door faces chosen so no two adjacent buildings ever face each other across a street.
    private val BUILDINGS: Array<FloatArray> get() = arrayOf(
        floatArrayOf(7f, C1, RB, BH*1.08f, 3f),  // W
        floatArrayOf(8f, C2, RB, BH*1.32f, 1f),  // N
        floatArrayOf(9f, C3, RB, BH*0.92f, 2f),  // E
        floatArrayOf(4f, C1, RC, BH*1.02f, 0f),  // S
        floatArrayOf(5f, C2, RC, BH*1.25f, 2f),  // E
        floatArrayOf(6f, C3, RC, BH*0.96f, 1f),  // N
        floatArrayOf(1f, C1, RD, BH*1.05f, 2f),  // E
        floatArrayOf(2f, C2, RD, BH*1.14f, 1f),  // N
        floatArrayOf(3f, C3, RD, BH*0.89f, 3f),  // W
    )

    private val FUNCTION_BLDGS: Array<FloatArray> get() = arrayOf(
        floatArrayOf(C1, RA, BH*0.65f),
        floatArrayOf(C2, RA, BH*0.70f),
        floatArrayOf(C3, RA, BH*0.62f),
    )
    private val FUNCTION_LABELS = arrayOf("C", "()", "%")

    private val DAMAGED_BLDGS: Array<FloatArray> get() = arrayOf(
        floatArrayOf(C1, RE, BH*0.72f),
        floatArrayOf(C2, RE, BH*0.68f),
        floatArrayOf(C3, RE, BH*0.65f),
    )
    private val DAMAGED_LABELS = arrayOf("DEL", "0", ".")

    private val OPERATOR_BLDGS: Array<FloatArray> get() = arrayOf(
        floatArrayOf(C4, RA, BH*0.95f),
        floatArrayOf(C4, RB, BH*1.10f),
        floatArrayOf(C4, RC, BH*1.05f),
        floatArrayOf(C4, RD, BH*0.98f),
        floatArrayOf(C4, RE, BH*0.90f),
    )
    private val OPERATOR_LABELS = arrayOf("/", "*", "-", "+", "=")

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.96f, 0.94f, 0.88f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LESS)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        // Lit shader needs GL_OES_standard_derivatives. It's on every Android GPU
        // we care about, but if the compile fails we silently drop to the old flat
        // shader rather than shipping a black screen.
        val ext = GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: ""
        lightingOn = ext.contains("GL_OES_standard_derivatives")
        prog = if (lightingOn) runCatching { buildProg(VS, FS_LIT) }.getOrElse { 0 } else 0
        if (prog == 0) { lightingOn = false; prog = buildProg(VS, FS_FLAT) }

        aPos    = GLES20.glGetAttribLocation(prog, "aPosition")
        uMVP    = GLES20.glGetUniformLocation(prog, "uMVP")
        uCol    = GLES20.glGetUniformLocation(prog, "uColor")
        uFog    = GLES20.glGetUniformLocation(prog, "uFog")
        uAerial = GLES20.glGetUniformLocation(prog, "uAerial")
        uGray   = GLES20.glGetUniformLocation(prog, "uGray")
        uShift  = GLES20.glGetUniformLocation(prog, "uShift")
        uTip    = GLES20.glGetUniformLocation(prog, "uTip")
        uPivot  = GLES20.glGetUniformLocation(prog, "uPivot")
        aUV         = GLES20.glGetAttribLocation(prog, "aUV")
        uTexSampler = GLES20.glGetUniformLocation(prog, "uTex")
        uTexOn      = GLES20.glGetUniformLocation(prog, "uTexOn")
        if (lightingOn) {
            uLit      = GLES20.glGetUniformLocation(prog, "uLit")
            uDark     = GLES20.glGetUniformLocation(prog, "uDark")
            uKeyDir   = GLES20.glGetUniformLocation(prog, "uKeyDir")
            uKeyCol   = GLES20.glGetUniformLocation(prog, "uKeyCol")
            uCamPos   = GLES20.glGetUniformLocation(prog, "uCamPos")
            uLightPos = GLES20.glGetUniformLocation(prog, "uLightPos")
            uLightRad = GLES20.glGetUniformLocation(prog, "uLightRad")
        }
        cctv.init()
        loadModels()
        buildScene()
    }

    private fun loadModels() {
        val a = assets ?: return
        try { lampOnGroups   = ObjLoader.load(a, "models/lampon.obj",   "models/lampon.mtl") } catch (_: Throwable) {}
        try { lampOffGroups  = ObjLoader.load(a, "models/lampoff.obj",  "models/lampoff.mtl") } catch (_: Throwable) {}
        try { cameraOnGroups = ObjLoader.load(a, "models/cameraon.obj", "models/cameraon.mtl") } catch (_: Throwable) {}
        try {
            val night = ObjLoader.load(a, "models/cameranight.obj", "models/cameranight.mtl")
            // Only the per-material colors matter — geometry is identical to cameraon.
            cameraNightColors = night.associate { it.materialName to floatArrayOf(it.r, it.g, it.b) }
        } catch (_: Throwable) {}
        try { doorGroups      = ObjLoader.load(a, "models/door.obj",                 "models/door.mtl") } catch (_: Throwable) {}
        // Custom bridge pieces (1..9, south→north). Materials absent; groups are
        // tagged by object name so the lamp "Icosphere" meshes can glow at night.
        bridgeGroups = Array(9) { i ->
            runCatching { ObjLoader.load(a, "models/bridge/bridge${i + 1}.obj", "models/bridge/bridge${i + 1}.mtl") }
                .getOrDefault(emptyList())
        }
        try { muteButtonGroups = ObjLoader.load(a, "models/mutebutton.obj", "models/mutebutton.mtl") } catch (_: Throwable) {}
        try { muteButtonBounds = ObjLoader.loadBounds(a, "models/mutebutton.obj") } catch (_: Throwable) {}
        try { damagedGroupsA  = ObjLoader.load(a, "models/builddamage/buildd1.obj",  "models/builddamage/buildd1.mtl") } catch (_: Throwable) {}
        try { damagedGroupsB  = ObjLoader.load(a, "models/builddamage/buildd2.obj",  "models/builddamage/buildd2.mtl") } catch (_: Throwable) {}
        try { damagedGroupsC  = ObjLoader.load(a, "models/builddamage/buildd3.obj",  "models/builddamage/buildd3.mtl") } catch (_: Throwable) {}
        try { mainBuildingGroups = ObjLoader.load(a, "models/mainbuilding.obj", "models/mainbuilding.mtl") } catch (_: Throwable) {}
        try { stickmanGroups  = ObjLoader.load(a, "models/stickman.obj",  "models/stickman.mtl") } catch (_: Throwable) {}
        try { stickman2Groups = ObjLoader.load(a, "models/stickman2.obj", "models/stickman2.mtl") } catch (_: Throwable) {}
        try {
            monsterGroups   = ObjLoader.load(a, "models/monster.obj",   "models/monster.mtl")
            computeMonsterFaceHeading()
        } catch (_: Throwable) {}
        for (i in 0 until 10) {
            try { debrisGroups[i] = ObjLoader.load(a, "models/debris/debris${i+1}.obj", "models/debris/debris${i+1}.mtl") } catch (_: Throwable) {}
        }
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h); sw = w; sh = h
    }
    private var sw = 1; private var sh = 1

    private var lastFrameMs = 0L
    override fun onDrawFrame(gl: GL10?) {
        // Frame-rate cap (~33 fps). The scene rendered continuously at full device
        // frame-rate, which ran the GPU hot and drained the battery; pacing the GL
        // thread here roughly halves that sustained load with no visible cost.
        val nowMs = android.os.SystemClock.uptimeMillis()
        val since = nowMs - lastFrameMs
        if (lastFrameMs != 0L && since in 0 until 30L) {
            try { Thread.sleep(30L - since) } catch (_: InterruptedException) {}
        }
        lastFrameMs = android.os.SystemClock.uptimeMillis()
        if (needsRebuild) { needsRebuild = false; buildScene() }
        // Easter-egg background colour (707) + grayscale (1134206) — both read
        // live so a tweak made on the calculator shows next time the city draws.
        val clearRgb = com.fictioncutshort.justacalculator.logic.EasterEggTheme.cityClearRgb()
        GLES20.glClearColor(clearRgb[0], clearRgb[1], clearRgb[2], 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(prog)
        GLES20.glUniform1f(uGray,
            if (com.fictioncutshort.justacalculator.logic.EasterEggTheme.grayscale) 1f else 0f)

        if (lightingOn) {
            val dk = darknessLevel.coerceIn(0f, 1f)
            GLES20.glUniform1f(uDark, dk)
            // Crossfade the one key light from sun to moon. Both bearings are fixed
            // in world space, so the lighting never shifts with the player.
            val kx = SUN[0] + (MOON[0] - SUN[0]) * dk
            val ky = SUN[1] + (MOON[1] - SUN[1]) * dk
            val kz = SUN[2] + (MOON[2] - SUN[2]) * dk
            val kl = sqrt(kx*kx + ky*ky + kz*kz).coerceAtLeast(1e-4f)
            GLES20.glUniform3f(uKeyDir, kx/kl, ky/kl, kz/kl)
            val s = SUN_STRENGTH + (MOON_STRENGTH - SUN_STRENGTH) * dk
            GLES20.glUniform3f(uKeyCol,
                (SUN_COL[0] + (MOON_COL[0] - SUN_COL[0]) * dk) * s,
                (SUN_COL[1] + (MOON_COL[1] - SUN_COL[1]) * dk) * s,
                (SUN_COL[2] + (MOON_COL[2] - SUN_COL[2]) * dk) * s)
            GLES20.glUniform3f(uCamPos, camX, camY, camZ)
            uploadNearestLights()
        }

        val proj = FloatArray(16); val view = FloatArray(16); val mvp = FloatArray(16)
        Matrix.perspectiveM(proj, 0, fov, sw.toFloat() / sh.toFloat(), 0.5f, 3000f)

        // The ground shaking. Applied to the EYE, not the world, so the player is
        // what gets thrown about while the city falls around them. Grows through
        // the collapse.
        var shX = 0f; var shY = 0f; var shZ = 0f
        if (collapse > 0f) {
            val q = collapse.coerceIn(0f, 1f)
            val amp = 1.2f + q * 8f
            val t = (System.nanoTime() / 1_000_000L) * 0.001f
            shX = (sin(t * 27f) + sin(t * 61f) * 0.4f) * amp
            shY = sin(t * 43f) * amp * 0.7f
            shZ = (cos(t * 33f) + cos(t * 71f) * 0.4f) * amp
        }

        if (aerialMode) {
            val lookingDown = abs(introLookZ - camZ) < 2f
            Matrix.setLookAtM(view, 0,
                camX, camY, camZ,
                camX, if (lookingDown) 0f else 32f, introLookZ,
                0f, 0f, -1f)
        } else if (useLookAt) {
            Matrix.setLookAtM(view, 0,
                camX, camY, camZ,
                lookAtX, lookAtY, lookAtZ,
                0f, 1f, 0f)
        } else {
            Matrix.setIdentityM(view, 0)
            Matrix.rotateM(view, 0, camPitch + shY * 0.25f, 1f, 0f, 0f)
            Matrix.rotateM(view, 0, camYaw + shX * 0.25f, 0f, 1f, 0f)
            Matrix.translateM(view, 0, -(camX + shX), -(camY + shY), -(camZ + shZ))
        }

        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniform1f(uAerial, aerialBlend)

        val radA = ((radAngle % 360f) + 360f) % 360f
        val dkLvl = darknessLevel.coerceIn(0f, 1f)

        // Standing inside Building 10 — its drum wall then draws as tinted glass so
        // the city, the bridge and the arcs spinning around it all still read.
        val insideRad = !aerialMode && radOuterR > 0f &&
            hypot(camX - radBx, camZ - radBz) < radOuterR

        // ── The collapse ─────────────────────────────────────────────────────
        // Each building's meshes are shifted (and eventually dropped) as one, so
        // the skyline comes apart building by building instead of all at once.
        val col = collapse.coerceIn(0f, 1f)
        val xf = FloatArray(10)
        setXform(null)
        // meshIdx -> collapse group, so the loop below can look its building up.
        val groupOf: HashMap<Int, FloatArray>? = if (col > 0f) HashMap<Int, FloatArray>().also { m ->
            for (g in collapseGroups) {
                for (i in g[0].toInt()..g[1].toInt()) m[i] = g
            }
        } else null

        // ── The CCTV desk ────────────────────────────────────────────────────
        // Only while the player is actually in the room with the monitors: this
        // re-renders the city from a security camera into one cell of the feed
        // atlas, and it is the one thing in the frame that costs a second look at
        // the whole scene. Runs BEFORE pass 1 so it can borrow the shader's
        // uniforms and hand them straight back.
        if (insideRad && cctv.ready) drawCctvFeeds(mvp, col, groupOf)

        // ── PASS 1 — opaque + soft-shadow meshes (glow deferred to pass 2)
        var blendMode = 0   // 0=off, 1=alpha (shadows)
        var curAerial = aerialBlend
        for ((meshIdx, m) in meshes.withIndex()) {
            // A building that has finished falling is simply not drawn any more.
            if (groupOf != null) {
                val g = groupOf[meshIdx]
                if (g != null) {
                    if (!collapseXform(g, col, xf)) continue
                    setXform(xf)
                } else setXform(null)
            }
            // Hide aerialSkip detail until the city has spread to its final
            // CELL=260 layout (cellStage == 3), OR whenever the renderer is
            // in aerialMode (the forceAerial post-minigame fly-over). The
            // cellStage gate lets the new intro keep sidewalks/lamps/cameras/
            // debris hidden through phase 2 even while aerialMode is false
            // (smooth pitch+yaw forward motion).
            if (m.aerialSkip && (cellStage < 3 || aerialMode)) continue
            if (m.glow) continue
            if (m.sky) continue                      // drawn below, camera-relative
            if (m.radDoor && radDoorOpen) continue   // Building 10 opened — no door left
            val r: Float; val g: Float; val b: Float
            if (m.lava) {
                val p = 0.5f + sin(lavaShift * 2f * PI.toFloat() + m.r * 4f).toFloat() * 0.5f
                r = p; g = p * 0.28f; b = 0.02f
            } else if (m.radArc) {
                // Two teal arcs of 108° each, 180° apart — spinning at radAngle
                val seg = ((m.arcAngle % 360f) + 360f) % 360f
                val inArc1 = arcInRange(seg, radA, radA + 108f)
                val inArc2 = arcInRange(seg, radA + 180f, radA + 288f)
                if (!inArc1 && !inArc2) continue  // dark gap — skip entirely
                r = 0.0f; g = 0.702f; b = 0.753f  // #00B3C0
            } else if (m.windowDigit in 1..9) {
                // Window panes on the main building model. Three states, picked
                // each frame:
                //   completed → near-black (the building is "done", windows are dead)
                //   dark      → warm yellow, modulated by darknessLevel
                //   light     → daytime cyan/blue (the model's authored tint)
                val idx = m.windowDigit - 1
                val done = idx in buildingCompleted.indices && buildingCompleted[idx]
                if (done) {
                    r = 0.02f; g = 0.02f; b = 0.03f
                } else {
                    val dayR = 0.30f; val dayG = 0.78f; val dayB = 0.80f
                    val nightR = 1.0f; val nightG = 0.86f; val nightB = 0.35f
                    val t = dkLvl
                    r = dayR + (nightR - dayR) * t
                    g = dayG + (nightG - dayG) * t
                    b = dayB + (nightB - dayB) * t
                }
            } else { r = m.r; g = m.g; b = m.b }

            val seeThrough = m.radShell && insideRad
            val want = if (m.softShadow || seeThrough) 1 else 0
            if (want != blendMode) {
                if (want == 0) {
                    GLES20.glDisable(GLES20.GL_BLEND)
                    GLES20.glDepthMask(true)
                } else {
                    GLES20.glEnable(GLES20.GL_BLEND)
                    GLES20.glDepthMask(false)
                    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                }
                blendMode = want
            }

            val wantAerial = if (m.noAO) 1f else aerialBlend
            if (wantAerial != curAerial) {
                GLES20.glUniform1f(uAerial, wantAerial)
                curAerial = wantAerial
            }
            setLit(if (m.lava || m.radArc || m.softShadow || m.noAO ||
                       m.mode == GLES20.GL_LINES) 0f else 1f)

            bindMeshTexture(m)
            GLES20.glUniform4f(uCol, r, g, b, if (seeThrough) 0.22f else m.a)
            GLES20.glUniform1f(uFog, if (aerialMode) m.fog else 0f)
            m.buf.position(0)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, m.buf)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glDrawArrays(m.mode, 0, m.cnt)
        }
        if (blendMode != 0) {
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glDepthMask(true)
        }
        setXform(null)   // the meshes drawn from here on carry their own transforms
        clearMeshTexture()   // ...and none of them are textured

        if (showPlayer) {
            val ox = playerX; val oy = 32f + 8f; val oz = playerZ
            drawOrb(ox, oy, oz, 8f, mvp)
        }

        drawCameras()
        drawDoors()
        drawEntranceRgb()   // RGB frame on Building 8's door (visible from the street)

        // Monster drawn BEFORE the night overlay so the darkness covers it too —
        // it reads as a dark shape in the gloom rather than a self-lit cut-out.
        drawMonster()

        // Dark-blue atmospheric overlay — gets stronger as the player completes
        // buildings. Drawn last with the depth test off so it tints everything.
        if (darknessLevel > 0.001f) {
            val identity = FloatArray(16); Matrix.setIdentityM(identity, 0)
            GLES20.glUniformMatrix4fv(uMVP, 1, false, identity, 0)
            GLES20.glUniform1f(uFog, 0f)
            GLES20.glUniform1f(uAerial, 1f)
            setLit(0f)
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            val a = (darknessLevel * 0.88f).coerceIn(0f, 1f)
            GLES20.glUniform4f(uCol, 0.010f, 0.016f, 0.045f, a)
            val quad = floatArrayOf(-1f,-1f,0f,  1f,-1f,0f,  1f,1f,0f,  -1f,1f,0f).toFB()
            quad.position(0)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, quad)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        }

        // CRITICAL: the overlay above swapped uMVP to identity for its fullscreen
        // quad. Restore the real scene matrix — the monster and the PASS-2 glow
        // (lamp bulbs, halos, lit windows) are world-space, so without this they
        // were being drawn off-screen (= invisible lamps + invisible monster).
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)

        // The monitors. Drawn AFTER the night overlay — a screen is a light source,
        // and in a dark room it is the only thing you can see. Depth still holds, so
        // walking behind the desk hides them.
        if (insideRad && cctv.ready) {
            cctv.draw(mvp, dkLvl)
            GLES20.glUseProgram(prog)
        }

        // ── Sky bodies (sun + moon) ──────────────────────────────────────────
        // Drawn AFTER the night overlay, so the moon isn't crushed by the very
        // darkness it's supposed to be lighting. The camera's translation is folded
        // into the matrix, so both keep a fixed bearing and never get closer no
        // matter how far the player walks; depth testing still lets buildings pass
        // in front of them. The sun fades out and the moon fades in with the night.
        if (!aerialMode && cellStage >= 3) {
            val skyMvp = mvp.copyOf()
            Matrix.translateM(skyMvp, 0, camX, camY, camZ)
            GLES20.glUniformMatrix4fv(uMVP, 1, false, skyMvp, 0)
            setLit(0f)
            GLES20.glUniform1f(uFog, 0f)
            GLES20.glUniform1f(uAerial, 1f)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            for (m in meshes) {
                if (!m.sky) continue
                val fade = if (m.crack) {
                    // A crack opens once the collapse has run past its turn.
                    if (col <= 0f) continue
                    ((col - m.arcAngle * 0.6f) * 3f).coerceIn(0f, 1f)
                } else if (m.skyNight) dkLvl else 1f - dkLvl
                if (fade < 0.01f) continue
                GLES20.glUniform4f(uCol, m.r, m.g, m.b, m.a * fade)
                m.buf.position(0)
                GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, m.buf)
                GLES20.glEnableVertexAttribArray(aPos)
                GLES20.glDrawArrays(m.mode, 0, m.cnt)
            }
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        }

        // ── PASS 2 — light sources drawn ADDITIVELY on top of the darkness so
        // they cut through it. Windows + camera lights were ALSO drawn in pass 1
        // (so they already wrote depth) — use GL_LEQUAL so the re-draw at equal
        // depth isn't rejected, otherwise their glow never appears.
        if (!aerialMode && dkLvl > 0.01f) {
            GLES20.glUniform1f(uAerial, 1f)  // disable AO so lights stay full-bright
            setLit(0f)                       // light sources are emissive, never shaded
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
            GLES20.glDepthMask(false)
            GLES20.glDepthFunc(GLES20.GL_LEQUAL)
            for ((meshIdx, m) in meshes.withIndex()) {
                if (m.aerialSkip && (cellStage < 3 || aerialMode)) continue
                // Lit (un-completed) windows also glow here so they punch through
                // the night as genuine light sources, not just dim tinted panes.
                val litWindow = m.windowDigit in 1..9 &&
                    !((m.windowDigit - 1) in buildingCompleted.indices && buildingCompleted[m.windowDigit - 1])
                if (!m.glow && !litWindow) continue
                // A glow rides its building down too — the bridge's lamps are in
                // here, and without this they burn on over the lava after the plank
                // carrying them has gone into it.
                if (groupOf != null) {
                    val g = groupOf[meshIdx]
                    if (g != null) {
                        if (!collapseXform(g, col, xf)) continue
                        setXform(xf)
                    } else setXform(null)
                }
                val r: Float; val g: Float; val b: Float; val a: Float
                if (litWindow) {
                    r = 1.0f; g = 0.82f; b = 0.30f       // warm window light
                    a = 0.85f * dkLvl
                } else {
                    // Lamp bulbs/halos stay dark through the day and only warm up once
                    // real dusk sets in (dkLvl past ~0.5) — they used to glow the moment
                    // the first building was done. Other glow sources keep their ramp.
                    val glowLvl = if (m.lamp) ((dkLvl - 0.5f) / 0.4f).coerceIn(0f, 1f) else dkLvl
                    r = m.r; g = m.g; b = m.b; a = m.a * glowLvl
                }
                if (a < 0.01f) continue
                GLES20.glUniform4f(uCol, r, g, b, a)
                GLES20.glUniform1f(uFog, 0f)
                m.buf.position(0)
                GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, m.buf)
                GLES20.glEnableVertexAttribArray(aPos)
                GLES20.glDrawArrays(m.mode, 0, m.cnt)
            }
            setXform(null)
            // Camera red lights — additive, same warm "defy the dark" pass.
            drawCameraLights(dkLvl)
            // Illuminate the monster's red/white faces.
            drawMonsterFaces(dkLvl)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glDepthMask(true)
            GLES20.glDepthFunc(GLES20.GL_LESS)
        }
    }

    // ── Building 8 entrance: a running-RGB light frame around the door ──────────
    // Drawn (additively) right after the doors so it's visible from the street,
    // day or night. Only appears once Building 5 is finished. Sets up + restores
    // its own GL blend/depth state.
    private fun drawEntranceRgb() {
        setLit(0f)   // emissive RGB frame
        if (!b5EntranceGlow || aerialMode) return
        val mount = doorMounts.firstOrNull { it[8].toInt() == 7 } ?: return   // digit 8
        // Bolted to Building 8's wall, so it rides that wall down — and once the
        // building has finished falling there is no doorway left to light.
        val col = collapse.coerceIn(0f, 1f)
        val xf = FloatArray(10)
        val pt = FloatArray(3)
        var falls = false
        if (col > 0f) {
            val g = collapseGroupNear(mount[0], mount[2])
            if (g != null) {
                if (!collapseXform(g, col, xf)) return
                falls = true
            }
        }
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)   // additive glow
        GLES20.glDepthMask(false)
        GLES20.glUniform1f(uAerial, 1f)
        GLES20.glUniform1f(uFog, 0f)
        val cx = mount[0]; val dh = mount[1]; val cz = mount[2]
        val face = mount[3].toInt()
        val dw = mount[4] * DOOR_HALF_W
        // Wall-plane basis: n = outward normal, t = width tangent (up is +Y).
        val nx: Float; val nz: Float; val tx: Float; val tz: Float
        when (face) {
            0 -> { nx = 0f; nz = 1f; tx = 1f; tz = 0f }
            1 -> { nx = 0f; nz = -1f; tx = 1f; tz = 0f }
            2 -> { nx = 1f; nz = 0f; tx = 0f; tz = 1f }
            else -> { nx = -1f; nz = 0f; tx = 0f; tz = 1f }
        }
        val tOut = dw + 0.35f                 // hug the outer edge of the doorway
        val yBot = 0.15f
        val yTop = dh + 0.35f
        val eSpan = 2f * tOut                 // top / bottom edge length
        val hSpan = yTop - yBot               // side edge length
        val perim = 2f * eSpan + 2f * hSpan
        val n = (perim / 1.3f).toInt().coerceIn(24, 90)
        val phase = ((System.nanoTime() / 1_000_000L) % 2600L) / 2600f
        val br = 0.85f                        // bulb half-size (large, bright)
        val push = 1.0f                       // sit well in front of the wall
        GLES20.glUniform1f(uFog, 0f)
        for (i in 0 until n) {
            val d = (i.toFloat() / n) * perim
            // Position (s along width, y up) around the rectangle perimeter.
            val s: Float; val y: Float
            when {
                d < eSpan -> { s = -tOut + d; y = yBot }
                d < eSpan + hSpan -> { s = tOut; y = yBot + (d - eSpan) }
                d < 2f * eSpan + hSpan -> { s = tOut - (d - eSpan - hSpan); y = yTop }
                else -> { s = -tOut; y = yTop - (d - 2f * eSpan - hSpan) }
            }
            val bx = cx + tx * s + nx * push
            val bz = cz + tz * s + nz * push
            val hue = ((i.toFloat() / n) + phase) % 1f
            val (r, g, b) = hsvBright(hue)
            val quad = floatArrayOf(
                bx - tx * br, y - br, bz - tz * br,
                bx + tx * br, y - br, bz + tz * br,
                bx + tx * br, y + br, bz + tz * br,
                bx - tx * br, y - br, bz - tz * br,
                bx + tx * br, y + br, bz + tz * br,
                bx - tx * br, y + br, bz - tz * br,
            )
            if (falls) {
                var k = 0
                while (k < quad.size) {
                    applyXform(xf, quad[k], quad[k + 1], quad[k + 2], pt)
                    quad[k] = pt[0]; quad[k + 1] = pt[1]; quad[k + 2] = pt[2]
                    k += 3
                }
            }
            val v = quad.toFB()
            GLES20.glUniform4f(uCol, r, g, b, 1f)
            v.position(0)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, v)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        }
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)
    }

    /** Fully-saturated hue (0..1) → RGB. */
    private fun hsvBright(h: Float): Triple<Float, Float, Float> {
        val f = (h * 6f)
        val i = f.toInt() % 6
        val frac = f - f.toInt()
        return when (i) {
            0 -> Triple(1f, frac, 0f)
            1 -> Triple(1f - frac, 1f, 0f)
            2 -> Triple(0f, 1f, frac)
            3 -> Triple(0f, 1f - frac, 1f)
            4 -> Triple(frac, 0f, 1f)
            else -> Triple(1f, 0f, 1f - frac)
        }
    }

    // ── The CCTV feeds ────────────────────────────────────────────────────────
    // Re-renders the city from a security camera into the feed atlas, then puts
    // the shader back exactly as pass 1 expects to find it: same viewport, same
    // matrix, same eye, and the lights nearest THAT eye rather than the camera's.

    private fun drawCctvFeeds(mvpMain: FloatArray, col: Float, groupOf: HashMap<Int, FloatArray>?) {
        // The monster is what the desk is really for: cameras near it drift off
        // their beat and follow it, so it turns up on a screen before it turns up
        // at the door.
        cctv.watch(monsterX, monsterZ, monsterActive)
        cctv.refresh(CCTV_FEEDS_PER_FRAME) { feedMvp, ex, ey, ez ->
            drawFeedScene(feedMvp, ex, ey, ez, col, groupOf)
        }
        GLES20.glViewport(0, 0, sw, sh)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvpMain, 0)
        GLES20.glUniform1f(uAerial, aerialBlend)
        setXform(null)
        if (lightingOn) {
            GLES20.glUniform3f(uCamPos, camX, camY, camZ)
            uploadNearestLights()
        }
    }

    /** One feed: the city as that camera sees it, frustum-culled down to what it can. */
    private fun drawFeedScene(
        feedMvp: FloatArray, ex: Float, ey: Float, ez: Float,
        col: Float, groupOf: HashMap<Int, FloatArray>?,
    ) {
        GLES20.glUniformMatrix4fv(uMVP, 1, false, feedMvp, 0)
        GLES20.glUniform1f(uAerial, aerialBlend)
        GLES20.glUniform1f(uFog, 0f)
        if (lightingOn) {
            GLES20.glUniform3f(uCamPos, ex, ey, ez)
            uploadNearestLights(ex, ez)
        }
        extractFrustum(feedMvp)

        val radA = ((radAngle % 360f) + 360f) % 360f
        val dkLvl = darknessLevel.coerceIn(0f, 1f)
        val xf = FloatArray(10)
        setXform(null)

        for ((i, m) in meshes.withIndex()) {
            // A feed carries the city itself, not the mood: no shadow blobs, no
            // glow pass, no sky. It's a 256px window on a monitor.
            if (m.glow || m.sky || m.softShadow) continue
            if (m.mode == GLES20.GL_LINES) continue
            if (m.aerialSkip && cellStage < 3) continue
            if (m.radDoor && radDoorOpen) continue
            // Textured surfaces are all inside Building 10, which no street camera
            // can see into — and this pass never binds a texture, so drawing them
            // here would just paint them flat white.
            if (m.tex != 0) continue

            var falling = false
            if (groupOf != null) {
                val g = groupOf[i]
                if (g != null) {
                    if (!collapseXform(g, col, xf)) continue   // this one has already fallen
                    falling = true
                }
            }
            // A toppling building sweeps well outside its own bounds, so a sphere
            // test against where it used to stand would cull it out of shot right
            // when it's the thing worth watching. Only cull what is standing still.
            if (!falling) {
                if (!inFeedFrustum(i, 0f, 0f, 0f)) continue
                setXform(null)
            } else setXform(xf)

            val r: Float; val g: Float; val b: Float
            if (m.lava) {
                val p = 0.5f + sin(lavaShift * 2f * PI.toFloat() + m.r * 4f).toFloat() * 0.5f
                r = p; g = p * 0.28f; b = 0.02f
            } else if (m.radArc) {
                val seg = ((m.arcAngle % 360f) + 360f) % 360f
                if (!arcInRange(seg, radA, radA + 108f) && !arcInRange(seg, radA + 180f, radA + 288f)) continue
                r = 0.0f; g = 0.702f; b = 0.753f
            } else if (m.windowDigit in 1..9) {
                val idx = m.windowDigit - 1
                if (idx in buildingCompleted.indices && buildingCompleted[idx]) {
                    r = 0.02f; g = 0.02f; b = 0.03f
                } else {
                    r = 0.30f + 0.70f * dkLvl
                    g = 0.78f + 0.08f * dkLvl
                    b = 0.80f - 0.45f * dkLvl
                }
            } else { r = m.r; g = m.g; b = m.b }

            setLit(if (m.lava || m.radArc || m.noAO) 0f else 1f)
            GLES20.glUniform4f(uCol, r, g, b, m.a)
            m.buf.position(0)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, m.buf)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glDrawArrays(m.mode, 0, m.cnt)
        }
        setXform(null)

        // The doors have to come along: the buildings have real holes cut for them.
        drawDoors()
        drawMonster()
    }

    /** Gribb–Hartmann planes of [m] (column-major), normalised, inward-facing. */
    private fun extractFrustum(m: FloatArray) {
        fun plane(i: Int, s: Float, row: Int) {
            val p = frustum[i]
            for (k in 0 until 4) p[k] = m[k * 4 + 3] + s * m[k * 4 + row]
            val len = sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2])
            if (len > 1e-6f) { p[0] /= len; p[1] /= len; p[2] /= len; p[3] /= len }
        }
        plane(0,  1f, 0); plane(1, -1f, 0)   // left, right
        plane(2,  1f, 1); plane(3, -1f, 1)   // bottom, top
        plane(4,  1f, 2); plane(5, -1f, 2)   // near, far
    }

    private fun inFeedFrustum(idx: Int, sx: Float, sy: Float, sz: Float): Boolean {
        val b = idx * 4
        if (b + 3 >= meshSpheres.size) return true
        val r = meshSpheres[b + 3]
        if (r <= 0f) return true
        val x = meshSpheres[b] + sx
        val y = meshSpheres[b + 1] + sy
        val z = meshSpheres[b + 2] + sz
        for (p in frustum) {
            if (p[0] * x + p[1] * y + p[2] * z + p[3] < -r) return false
        }
        return true
    }

    // ── Scene ─────────────────────────────────────────────────────────────────

    fun rebuildScene() { buildScene() }

    private fun buildScene() {
        meshes.clear()
        cameraMounts.clear()
        doorMounts.clear()
        lampLights.clear()
        collapseGroups.clear()
        cctv.setMonitors(emptyList())   // re-found from the model in addRadButton
        cctv.feeds = emptyList()
        if (isLandscape) { buildSceneLandscape(); finishScene(); return }
        addGround()
        for (b in BUILDINGS)      addBuildingShadow(b[1], b[2], b[3]*buildingHeightScale)
        for (f in FUNCTION_BLDGS) addBuildingShadow(f[0], f[1], f[2]*buildingHeightScale)
        for (i in DAMAGED_BLDGS.indices) addBuildingShadow(DAMAGED_BLDGS[i][0], DAMAGED_BLDGS[i][1], DAMAGED_BLDGS[i][2]*buildingHeightScale)
        for (o in OPERATOR_BLDGS) addBuildingShadow(o[0], o[1], o[2]*buildingHeightScale)
        for (b in BUILDINGS) collapsible(b[1], b[2]) {
            addBuilding(b[1], b[2], b[3]*buildingHeightScale, b[4].toInt(), b[0].toInt().toString())
        }
        // All non-door buildings (function / damaged / operator) share the ruin
        // renderer now — each takes its own row palette and Y rotation.
        for (i in FUNCTION_BLDGS.indices) collapsible(FUNCTION_BLDGS[i][0], FUNCTION_BLDGS[i][1]) {
            addDamagedBuilding(FUNCTION_BLDGS[i][0], FUNCTION_BLDGS[i][1], FUNCTION_BLDGS[i][2]*buildingHeightScale, FUNCTION_LABELS[i])
        }
        for (i in DAMAGED_BLDGS.indices) collapsible(DAMAGED_BLDGS[i][0], DAMAGED_BLDGS[i][1]) {
            addDamagedBuilding(DAMAGED_BLDGS[i][0],  DAMAGED_BLDGS[i][1],  DAMAGED_BLDGS[i][2]*buildingHeightScale,  DAMAGED_LABELS[i])
        }
        for (i in OPERATOR_BLDGS.indices) collapsible(OPERATOR_BLDGS[i][0], OPERATOR_BLDGS[i][1]) {
            addDamagedBuilding(OPERATOR_BLDGS[i][0], OPERATOR_BLDGS[i][1], OPERATOR_BLDGS[i][2]*buildingHeightScale, OPERATOR_LABELS[i])
        }
        addWestWall()
        addLava()
        addGreenNorth()
        addRadButton(80f * buildingHeightScale, scale = RAD_SCALE)
        addDebris()
        addStickmen()
        if (bridgePieces > 0) addBridge(bridgePieces)
        run {
            // Intersection grid follows whichever cell the scene is using.
            val cell = activeCell()
            addAtmosphere(
                bldgs = BUILDINGS, funcs = FUNCTION_BLDGS,
                damaged = DAMAGED_BLDGS, ops = OPERATOR_BLDGS,
                lampCols = listOf(-cell, 0f, cell),
                lampRows = listOf(-cell * 1.5f, -cell * 0.5f, cell * 0.5f, cell * 1.5f)
            )
        }
        addCameras(BUILDINGS, FUNCTION_BLDGS, DAMAGED_BLDGS, OPERATOR_BLDGS)
        addSkyCracks()
        // Must run last: it reads back the window meshes everything above added.
        collectWindowLights()
        finishScene()
    }

    // Everything that has to read the finished scene back: the per-mesh bounds the
    // CCTV cull needs, and the feed cameras, which are picked from the security
    // cameras addCameras just hung on the buildings.
    private fun finishScene() {
        meshSpheres = FloatArray(meshes.size * 4)
        for ((i, m) in meshes.withIndex()) {
            val b = m.buf
            var mnX = Float.MAX_VALUE; var mxX = -Float.MAX_VALUE
            var mnY = Float.MAX_VALUE; var mxY = -Float.MAX_VALUE
            var mnZ = Float.MAX_VALUE; var mxZ = -Float.MAX_VALUE
            var k = 0
            while (k + 2 < m.cnt * 3) {
                val x = b.get(k); val y = b.get(k + 1); val z = b.get(k + 2)
                if (x < mnX) mnX = x; if (x > mxX) mxX = x
                if (y < mnY) mnY = y; if (y > mxY) mxY = y
                if (z < mnZ) mnZ = z; if (z > mxZ) mxZ = z
                k += 3
            }
            b.position(0)
            if (mnX > mxX) continue
            val cx = (mnX + mxX) * 0.5f; val cy = (mnY + mxY) * 0.5f; val cz = (mnZ + mxZ) * 0.5f
            meshSpheres[i * 4]     = cx
            meshSpheres[i * 4 + 1] = cy
            meshSpheres[i * 4 + 2] = cz
            meshSpheres[i * 4 + 3] = 0.5f * sqrt(
                (mxX - mnX) * (mxX - mnX) + (mxY - mnY) * (mxY - mnY) + (mxZ - mnZ) * (mxZ - mnZ))
        }
        buildCctvFeeds()
    }

    // One feed per atlas cell, spread evenly over the cameras hanging on the
    // buildings. Each looks out and down along the diagonal its corner faces —
    // the same way its model is aimed — over a different distance, so no two
    // screens show the same slab of street.
    private fun buildCctvFeeds() {
        val mounts = cameraMounts
        if (mounts.isEmpty()) { cctv.feeds = emptyList(); return }
        cctv.feeds = List(CityCctv.CELLS) { i ->
            val m = mounts[i * mounts.size / CityCctv.CELLS]
            val reach = 280f + (i % 3) * 140f         // how far down the street it looks
            val floor = 45f + (i % 2) * 40f           // and how steeply
            CctvFeed(
                m[0], m[1], m[2],
                m[0] + m[3] * reach, floor, m[2] + m[4] * reach,
                i * 1.7f,
            )
        }
    }

    // ── Landscape scene (horizontal orientation) ──────────────────────────────
    // Buildings on the RIGHT (+X side, matching the keyboard panel):
    //   C1=200, C2=400, C3=600, C4=800  — same Z rows as portrait
    // Lava on the LEFT-BOTTOM (-X, +Z corner): X=-680..85, Z=275..580
    // RAD button on the LEFT-MIDDLE: bx=-350, bz=-100
    // Wall at X=95 with gap Z=272..328 (between "1" RD=200 and "DEL" RE=400)

    private fun buildSceneLandscape() {
        val cell = activeCell()
        val lC1 = cell * 1f; val lC2 = cell * 2f; val lC3 = cell * 3f; val lC4 = cell * 4f
        val lRA = -cell * 2f; val lRB = -cell * 1f; val lRC = 0f; val lRD = cell * 1f; val lRE = cell * 2f

        val lBUILDINGS = arrayOf(
            floatArrayOf(7f, lC1, lRB, BH*1.08f, 3f),  // W
            floatArrayOf(8f, lC2, lRB, BH*1.32f, 1f),  // N
            floatArrayOf(9f, lC3, lRB, BH*0.92f, 2f),  // E
            floatArrayOf(4f, lC1, lRC, BH*1.02f, 0f),  // S
            floatArrayOf(5f, lC2, lRC, BH*1.25f, 2f),  // E
            floatArrayOf(6f, lC3, lRC, BH*0.96f, 1f),  // N
            floatArrayOf(1f, lC1, lRD, BH*1.05f, 2f),  // E
            floatArrayOf(2f, lC2, lRD, BH*1.14f, 1f),  // N
            floatArrayOf(3f, lC3, lRD, BH*0.89f, 3f),  // W
        )
        val lFUNCTION_BLDGS = arrayOf(
            floatArrayOf(lC1, lRA, BH*0.65f),
            floatArrayOf(lC2, lRA, BH*0.70f),
            floatArrayOf(lC3, lRA, BH*0.62f),
        )
        val lDAMAGED_BLDGS = arrayOf(
            floatArrayOf(lC1, lRE, BH*0.72f),
            floatArrayOf(lC2, lRE, BH*0.68f),
            floatArrayOf(lC3, lRE, BH*0.65f),
        )
        val lOPERATOR_BLDGS = arrayOf(
            floatArrayOf(lC4, lRA, BH*0.95f),
            floatArrayOf(lC4, lRB, BH*1.10f),
            floatArrayOf(lC4, lRC, BH*1.05f),
            floatArrayOf(lC4, lRD, BH*0.98f),
            floatArrayOf(lC4, lRE, BH*0.90f),
        )

        // Ground — large flat plane covering full landscape area
        addQ(-800f, 0f, -700f,  1000f, 0f, -700f,  1000f, 0f, 700f,  -800f, 0f, 700f,
            0.96f, 0.94f, 0.88f, fog=0.0f)
        // Street lanes (Z direction, between building rows)
        val sg = (CELL - 2f*BD) / 2f
        for (rowZ in listOf(lerp(lRA,lRB,0.5f), lerp(lRB,lRC,0.5f), lerp(lRC,lRD,0.5f), lerp(lRD,lRE,0.5f))) {
            addQ(lC1-BW, 0.3f, rowZ-sg,  lC4+BW+80f, 0.3f, rowZ-sg,  lC4+BW+80f, 0.3f, rowZ+sg,  lC1-BW, 0.3f, rowZ+sg,
                0.96f, 0.94f, 0.88f, fog=0.0f)
        }
        // Street lanes (X direction, between columns)
        for (colX in listOf(lerp(lC1,lC2,0.5f), lerp(lC2,lC3,0.5f), lerp(lC3,lC4,0.5f))) {
            val gw = (CELL - 2f*BW) / 2f
            addQ(colX-gw, 0.3f, lRA-BD,  colX+gw, 0.3f, lRA-BD,  colX+gw, 0.3f, lRE+BD,  colX-gw, 0.3f, lRE+BD,
                0.96f, 0.94f, 0.88f, fog=0.0f)
        }

        // Building shadows
        for (b in lBUILDINGS)       addBuildingShadow(b[1], b[2], b[3]*buildingHeightScale)
        for (f in lFUNCTION_BLDGS)  addBuildingShadow(f[0], f[1], f[2]*buildingHeightScale)
        for (d in lDAMAGED_BLDGS)   addBuildingShadow(d[0], d[1], d[2]*buildingHeightScale)
        for (o in lOPERATOR_BLDGS)  addBuildingShadow(o[0], o[1], o[2]*buildingHeightScale)

        // Buildings
        for (b in lBUILDINGS) addBuilding(b[1], b[2], b[3]*buildingHeightScale, b[4].toInt(), b[0].toInt().toString())
        for (i in lFUNCTION_BLDGS.indices)  addDamagedBuilding(lFUNCTION_BLDGS[i][0],  lFUNCTION_BLDGS[i][1],  lFUNCTION_BLDGS[i][2]*buildingHeightScale,  FUNCTION_LABELS[i])
        for (i in lDAMAGED_BLDGS.indices)   addDamagedBuilding(lDAMAGED_BLDGS[i][0],   lDAMAGED_BLDGS[i][1],   lDAMAGED_BLDGS[i][2]*buildingHeightScale,   DAMAGED_LABELS[i])
        for (i in lOPERATOR_BLDGS.indices)  addDamagedBuilding(lOPERATOR_BLDGS[i][0],  lOPERATOR_BLDGS[i][1],  lOPERATOR_BLDGS[i][2]*buildingHeightScale,  OPERATOR_LABELS[i])

        // West wall at X=95 with gap between "1" (lRD+BD=272) and "DEL" (lRE-BD=328)
        addLandscapeWall(lRD + BD, lRE - BD)

        // Lava — south-west corner (left-bottom in aerial view)
        addRoundedRectFill(-680f, 20f, 275f, 85f, 580f, 12f, 1.0f, 0.32f, 0.04f, fog=0.05f)

        // RAD spinning button — left-middle in aerial view
        addRadButton(80f * buildingHeightScale, bx = -CELL * 1.25f, bz = -CELL * 0.5f)  // -350, -140 at CELL=280

        // Debris on east operator column gaps
        addDebrisLandscape(lC4, lRA, lRB, lRC, lRD, lRE)
        addStickmenLandscape(lC1, lC2, lC3, lRA, lRE)

        // Landscape intersection grid: between lC1..lC4 in X, between
        // lRA..lRE in Z — derived from the same active cell as the buildings.
        addAtmosphere(
            bldgs = lBUILDINGS, funcs = lFUNCTION_BLDGS,
            damaged = lDAMAGED_BLDGS, ops = lOPERATOR_BLDGS,
            lampCols = listOf(cell * 1.5f, cell * 2.5f, cell * 3.5f),
            lampRows = listOf(-cell * 1.5f, -cell * 0.5f, cell * 0.5f, cell * 1.5f)
        )
        addCameras(lBUILDINGS, lFUNCTION_BLDGS, lDAMAGED_BLDGS, lOPERATOR_BLDGS)
        collectWindowLights()
    }

    // Wall with gap: two sections north and south of the gap opening
    private fun addLandscapeWall(gapN: Float, gapS: Float) {
        val wx = 95f; val wt = 20f; val wh = BH * 1.5f * buildingHeightScale
        addWallSection(wx, wt, wh, -620f, gapN)
        addWallSection(wx, wt, wh, gapS, 620f)
    }

    private fun addWallSection(wx: Float, wt: Float, wh: Float, zFrom: Float, zTo: Float) {
        val c = 0.96f; val m = 0.22f; val d = 0.14f; val b = 0.09f
        // East face (visible from city side):
        addQ(wx+wt, 0f, zTo,   wx+wt, 0f, zFrom,   wx+wt, wh, zFrom,   wx+wt, wh, zTo,   c, c, c*0.92f, fog=0.0f)
        // Top:
        addQ(wx, wh, zTo,   wx+wt, wh, zTo,   wx+wt, wh, zFrom,   wx, wh, zFrom,   c, c, c*0.92f, fog=0.0f)
        // North end cap:
        addQ(wx+wt, 0f, zFrom,   wx, 0f, zFrom,   wx, wh, zFrom,   wx+wt, wh, zFrom,   c, c, c*0.92f, fog=0.0f)
        // South end cap:
        addQ(wx, 0f, zTo,   wx+wt, 0f, zTo,   wx+wt, wh, zTo,   wx, wh, zTo,   c, c, c*0.92f, fog=0.0f)
        // Horizontal detail lines:
        for (i in 1..8) addL(wx+wt+0.4f, wh*i/9f, zTo,   wx+wt+0.4f, wh*i/9f, zFrom,   m, d, b)
    }

    private fun addDebrisLandscape(c4: Float, rA: Float, rB: Float, rC: Float, rD: Float, rE: Float) {
        val debrisColors = listOf(
            floatArrayOf(0.42f, 0.40f, 0.38f), floatArrayOf(0.55f, 0.30f, 0.22f),
            floatArrayOf(0.28f, 0.25f, 0.22f), floatArrayOf(0.50f, 0.46f, 0.38f),
        )
        val col4Gaps = listOf(
            Pair(rA + BD + 2f, rB - BD - 2f), Pair(rB + BD + 2f, rC - BD - 2f),
            Pair(rC + BD + 2f, rD - BD - 2f), Pair(rD + BD + 2f, rE - BD - 2f),
        )
        val rnd = java.util.Random(133L)
        for ((gz0, gz1) in col4Gaps) {
            if (gz1 <= gz0) continue
            val gapW = c4 + BW + 80f - (c4 - BW - 80f)
            repeat(14) {
                val px = c4 - BW - 75f + rnd.nextFloat() * gapW
                val pz = gz0 + rnd.nextFloat() * (gz1 - gz0)
                val scale = 8f + rnd.nextFloat() * 18f
                val yaw = rnd.nextFloat() * 2f * PI.toFloat()
                val col = debrisColors[rnd.nextInt(debrisColors.size)]
                val tintBase = 0.85f + rnd.nextFloat() * 0.30f
                addDebrisInstance(px, pz, scale, yaw, col[0] * tintBase, col[1] * tintBase, col[2] * tintBase, fog = 0.28f)
            }
        }
        // South debris from damaged row (DEL/0/.) at lRE
        val lC1L = c4 - CELL * 3f; val lC2L = c4 - CELL * 2f; val lC3L = c4 - CELL * 1f
        val southColors = listOf(
            floatArrayOf(0.68f, 0.65f, 0.62f), floatArrayOf(0.65f, 0.32f, 0.18f),
            floatArrayOf(0.54f, 0.50f, 0.45f), floatArrayOf(0.32f, 0.28f, 0.24f),
        )
        val rnd2 = java.util.Random(77L)
        for (cx in listOf(lC1L, lC2L, lC3L)) {
            val z1 = rE + BD + 4f
            repeat(12) {
                val px = cx + rnd2.nextFloat() * BW * 1.8f - BW * 0.9f
                val pz = z1 + rnd2.nextFloat() * 80f
                val scale = 5f + rnd2.nextFloat() * 12f
                val yaw = rnd2.nextFloat() * 2f * PI.toFloat()
                val sc = southColors[rnd2.nextInt(southColors.size)]
                val tintBase = 0.85f + rnd2.nextFloat() * 0.30f
                addDebrisInstance(px, pz, scale, yaw, sc[0] * tintBase, sc[1] * tintBase, sc[2] * tintBase, fog = 0.30f)
            }
        }
    }

    // ── Ground ────────────────────────────────────────────────────────────────

    private fun addGround() {
        addQ(-700f,0f,-1100f, 700f,0f,-1100f, 700f,0f,700f, -700f,0f,700f,
            0.96f,0.94f,0.88f, fog=0.0f)
        val sg = (CELL - 2f*BD) / 2f
        for (rowZ in listOf(lerp(RA,RB,0.5f), lerp(RB,RC,0.5f), lerp(RC,RD,0.5f), lerp(RD,RE,0.5f))) {
            addQ(CITY_W,0.3f,rowZ-sg, CITY_E,0.3f,rowZ-sg, CITY_E,0.3f,rowZ+sg, CITY_W,0.3f,rowZ+sg,
                0.96f,0.94f,0.88f, fog=0.0f)
        }
        for (colX in listOf(lerp(C1,C2,0.5f), lerp(C2,C3,0.5f), lerp(C3,C4,0.5f))) {
            val gw = (CELL - 2f*BW) / 2f
            addQ(colX-gw,0.3f,CITY_N, colX+gw,0.3f,CITY_N, colX+gw,0.3f,CITY_S, colX-gw,0.3f,CITY_S,
                0.96f,0.94f,0.88f, fog=0.0f)
        }
        addQ(WALL_X,0.3f,CITY_S, CITY_E,0.3f,CITY_S, CITY_E,0.3f,CITY_S+180f, WALL_X,0.3f,CITY_S+180f,
            0.96f,0.94f,0.88f, fog=0.0f)
        addQ(CITY_W,0.3f,LAVA_S, CITY_E,0.3f,LAVA_S, CITY_E,0.3f,CITY_N, CITY_W,0.3f,CITY_N,
            0.96f,0.94f,0.88f, fog=0.0f)  // formerly green north patch — now cream no fog

    }

    // ── Chamfered wall helper ─────────────────────────────────────────────────
    // Draws 8 side faces + flat top with chamfered corners (button shape)

    private fun addChamfWalls(
        x0: Float, z0: Float, x1: Float, z1: Float, h: Float,
        sR: Float, sG: Float, sB: Float,
        nR: Float, nG: Float, nB: Float,
        eR: Float, eG: Float, eB: Float,
        wR: Float, wG: Float, wB: Float,
        tR: Float, tG: Float, tB: Float,
        ch: Float = 12f, fog: Float = 0.65f
    ) {
        // 4 main faces
        addQ(x0+ch,0f,z1, x1-ch,0f,z1, x1-ch,h,z1, x0+ch,h,z1, sR,sG,sB, fog=fog)
        addQ(x1-ch,0f,z0, x0+ch,0f,z0, x0+ch,h,z0, x1-ch,h,z0, nR,nG,nB, fog=fog)
        addQ(x1,0f,z1-ch, x1,0f,z0+ch, x1,h,z0+ch, x1,h,z1-ch, eR,eG,eB, fog=fog)
        addQ(x0,0f,z0+ch, x0,0f,z1-ch, x0,h,z1-ch, x0,h,z0+ch, wR,wG,wB, fog=fog)
        // 4 corner faces (blend adjacent)
        addQ(x0,0f,z1-ch, x0+ch,0f,z1, x0+ch,h,z1, x0,h,z1-ch, (sR+wR)/2,(sG+wG)/2,(sB+wB)/2, fog=fog) // SW
        addQ(x1-ch,0f,z1, x1,0f,z1-ch, x1,h,z1-ch, x1-ch,h,z1, (sR+eR)/2,(sG+eG)/2,(sB+eB)/2, fog=fog) // SE
        addQ(x1,0f,z0+ch, x1-ch,0f,z0, x1-ch,h,z0, x1,h,z0+ch, (nR+eR)/2,(nG+eG)/2,(nB+eB)/2, fog=fog) // NE
        addQ(x0+ch,0f,z0, x0,0f,z0+ch, x0,h,z0+ch, x0+ch,h,z0, (nR+wR)/2,(nG+wG)/2,(nB+wB)/2, fog=fog) // NW
        // Octagonal top (chamfered corners — 8-triangle fan)
        val tcx = (x0+x1)/2f; val tcz = (z0+z1)/2f
        val op = floatArrayOf(
            x0+ch, z0,   x1-ch, z0,   x1, z0+ch, x1, z1-ch,
            x1-ch, z1,   x0+ch, z1,   x0, z1-ch, x0, z0+ch
        )
        val ov = mutableListOf<Float>()
        for (i in 0 until 8) {
            val pi = i*2; val pj = ((i+1)%8)*2
            ov += listOf(tcx, h, tcz, op[pi], h, op[pi+1], op[pj], h, op[pj+1])
        }
        meshes.add(Mesh(ov.toFloatArray().toFB(), GLES20.GL_TRIANGLES, 24, tR,tG,tB,1f,fog))
    }

    private fun addChamfEdges(x0: Float, z0: Float, x1: Float, z1: Float, h: Float, ch: Float = 12f) {
        val bk = 0.04f; val EO = 1.5f
        // 8 vertical corner lines
        addL(x0-EO,0f,z1-ch, x0-EO,h,z1-ch, bk,bk,bk)
        addL(x0+ch,0f,z1+EO, x0+ch,h,z1+EO, bk,bk,bk)
        addL(x1-ch,0f,z1+EO, x1-ch,h,z1+EO, bk,bk,bk)
        addL(x1+EO,0f,z1-ch, x1+EO,h,z1-ch, bk,bk,bk)
        addL(x1+EO,0f,z0+ch, x1+EO,h,z0+ch, bk,bk,bk)
        addL(x1-ch,0f,z0-EO, x1-ch,h,z0-EO, bk,bk,bk)
        addL(x0+ch,0f,z0-EO, x0+ch,h,z0-EO, bk,bk,bk)
        addL(x0-EO,0f,z0+ch, x0-EO,h,z0+ch, bk,bk,bk)
        // Top outline (octagon)
        val y = h + EO
        addL(x0+ch,y,z1, x1-ch,y,z1, bk,bk,bk)
        addL(x1-ch,y,z1, x1,y,z1-ch, bk,bk,bk)
        addL(x1,y,z1-ch, x1,y,z0+ch, bk,bk,bk)
        addL(x1,y,z0+ch, x1-ch,y,z0, bk,bk,bk)
        addL(x1-ch,y,z0, x0+ch,y,z0, bk,bk,bk)
        addL(x0+ch,y,z0, x0,y,z0+ch, bk,bk,bk)
        addL(x0,y,z0+ch, x0,y,z1-ch, bk,bk,bk)
        addL(x0,y,z1-ch, x0+ch,y,z1, bk,bk,bk)
    }

    // ── Interactive building (digit 1–9) ──────────────────────────────────────
    // Renders mainbuilding.obj per digit plot. Per-material colours pull from
    // the calculator palette (body = cream, accents kept dark, windows tagged
    // for per-frame state-driven colour), sidewalk material is hidden from the
    // aerial view, and CPU diffuse is baked into per-(material × brightness)
    // sub-meshes so each face catches the sun. The model carries an actual
    // door cutout; we rotate it around Y so the opening faces the registered
    // door direction and the existing sliding-door panel slots straight in.
    //
    // Model bounds (model-space):
    //   X ±5.740 (sidewalk full),   building proper X ±4.078
    //   Z ±5.740 (sidewalk full),   building proper Z ±3.063
    //   Y -6.646 .. +10.741         lowest = sidewalk bottom; building "ground
    //                                floor" at y≈-4.93, ridge top at y≈10.74
    private val MB_BODY_HALF_X = 4.078f
    private val MB_BODY_HALF_Z = 3.063f
    private val MB_Y_BOTTOM    = -6.645585f
    private val MB_Y_GROUND    = -4.931460f   // building's first-floor base
    private val MB_Y_TOP       = 10.740813f
    private val MB_SIDEWALK_Y_LIMIT = -5.286f // anything with all-verts ≤ this is sidewalk

    // The mainbuilding model came in noticeably stretched-tall compared to the
    // original chamfered buttons. Scale all model-driven heights by this factor
    // (the door panel, interior darkness block, ridge lamp, and roof label all
    // use the same effective height so the proportions stay matched).
    private val MAIN_BUILDING_HEIGHT_SCALE = 0.70f

    private fun addBuilding(cx: Float, cz: Float, h: Float, door: Int, label: String) {
        val digit = label.toIntOrNull() ?: -1
        // Door panel half-width (in world units). 0.40 × BW_GROUND = 24,
        // i.e. a 48-unit-wide panel — wide enough to cover the model's full
        // ground-floor opening with a few units of overlap on each side, so
        // there's no see-through gap between the panel edge and the wall
        // jamb when the door is closed.
        val dw = BW_GROUND * 0.40f
        val gx0 = cx - BW_GROUND; val gx1 = cx + BW_GROUND
        val gz0 = cz - BD_GROUND; val gz1 = cz + BD_GROUND

        if (mainBuildingGroups.isNotEmpty()) {
            val effH = h * MAIN_BUILDING_HEIGHT_SCALE
            renderMainBuilding(cx, cz, effH, door, digit)
            // Door panel covers the model's ground-floor opening (which spans
            // model y MB_Y_BOTTOM → MB_Y_GROUND ≈ 1.71 model-units once the
            // lift below puts the sidewalk on the ground). That's ~10% of the
            // model's total height in world units. The door's TOP is at that
            // ground-floor height so the slide-up retracts cleanly into the
            // wall above the opening.
            val dh = effH * 0.31f
            // Door panel sits RECESSED inside the model's opening (so it
            // reads as a real doorway at the end of the ramp instead of
            // floating in front of the wall, AND so the wider panel above
            // doesn't poke out past the jamb). The model is non-uniformly
            // scaled (sX = BW/4.078, sZ = BD/3.063), so after the 90°/-90°
            // rotation for face 2/3 the wall the door docks onto sits at
            // cx ± BD — not cx ± BW.
            val recess = 7f
            when (door) {
                0 -> registerDoor(label, cx,                       cz + BD_GROUND - recess, 0, dw, dh)
                1 -> registerDoor(label, cx,                       cz - BD_GROUND + recess, 1, dw, dh)
                2 -> registerDoor(label, cx + BD_GROUND - recess,  cz,                       2, dw, dh)
                3 -> registerDoor(label, cx - BD_GROUND + recess,  cz,                       3, dw, dh)
            }
            // Per-building street lamp at one sidewalk corner — alternates
            // NW / SE across the 3×3 digit grid (checkerboard) so the row
            // doesn't read as a uniform line of posts. Damaged / function /
            // operator buildings don't get a lamp (they have no sidewalk).
            if (digit in 1..9) {
                // Map digit 1..9 → grid (row, col) where row=(d-1)/3, col=(d-1)%3,
                // checkerboard via (row+col)%2. 0 → NW corner, 1 → SE corner.
                val cornerNW = (((digit - 1) / 3 + (digit - 1) % 3) % 2) == 0
                addBuildingLamp(cx, cz, effH, door, cornerNW)
            }
            // Roof label sits on the model's ridge top — same effH as the model.
            addDigit(cx, effH + 2.4f, cz, 40f, label)
            return
        }
        // Fallback (model missing) — original chamfered button.
        val x0 = cx - BW; val x1 = cx + BW; val z0 = cz - BD; val z1 = cz + BD
        addChamfWalls(x0, z0, x1, z1, h,
            0.91f, 0.89f, 0.85f,
            0.56f, 0.55f, 0.52f,
            0.76f, 0.74f, 0.71f,
            0.67f, 0.65f, 0.63f,
            0.87f, 0.85f, 0.81f,
            fog = 0.0f)
        val dh = h * 0.18f
        val eps = 0.8f
        when (door) {
            0 -> registerDoor(label, cx,             gz1 + 0.4f + eps, 0, dw, dh)
            1 -> registerDoor(label, cx,             gz0 - 0.4f - eps, 1, dw, dh)
            2 -> registerDoor(label, gx1 + 0.4f + eps,   cz,           2, dw, dh)
            3 -> registerDoor(label, gx0 - 0.4f - eps,   cz,           3, dw, dh)
        }
        addDigit(cx, h + 2.4f, cz, 40f, label)
    }

    // Per-material world colour for the mainbuilding mesh. The user's body
    // material is the default Blender 0.8 grey, which we swap for the
    // calculator's digit-button cream; ridge / accents / sidewalk keep the
    // model's authored Kd. Windows are handled per-frame in the render loop —
    // here we just pass the day-blue placeholder and tag with windowDigit.
    private fun mainBuildingBaseColor(materialName: String, g: ObjGroup): FloatArray = when (materialName) {
        // Body → cream digit-button colour, or the easter-egg number colour
        // (code 58008) when one is chosen. The digit buildings ARE the number
        // buttons in the city, so recolouring them mirrors the calculator.
        "Material"     -> com.fictioncutshort.justacalculator.logic.EasterEggTheme.cityNumberRgbOrNull()
            ?: floatArrayOf(0.91f, 0.89f, 0.85f)
        "Material.001" -> floatArrayOf(0.30f, 0.78f, 0.80f)   // windows (day) — overridden per-frame
        else           -> floatArrayOf(g.r, g.g, g.b)         // ridge / sidewalk / accents stay model-authored
    }

    private fun renderMainBuilding(cx: Float, cz: Float, h: Float, door: Int, digit: Int) {
        // Non-uniform XZ scale so the building proper exactly fills the BW×BD
        // plot; the sidewalk wraps around it asymmetrically (the model is
        // square externally but the calculator plot is wider in X than Z).
        val sX = BW / MB_BODY_HALF_X
        val sZ = BD / MB_BODY_HALF_Z
        // Full model height maps to h. The whole thing is lifted by a hair
        // above the ground (0.5 units) so the sidewalk's bottom face doesn't
        // share a depth plane with the ground quad — that overlap was the
        // most likely cause of the rare flicker while walking.
        val sY = h / (MB_Y_TOP - MB_Y_BOTTOM)
        val liftY = -MB_Y_BOTTOM * sY + 0.5f

        // Yaw must match drawDoors's per-face rotation matrix so the model's
        // opening, the registered door panel, and the door-walk-through all
        // line up. drawDoors uses (cosY, sinY) = (0,1) for face 2 / (0,-1)
        // for face 3, i.e. +90° / -90° around Y.
        val yawDeg = when (door) {
            0    -> 0f      // S — model's authored opening faces +Z
            1    -> 180f    // N
            2    -> 90f     // E — rotate +90° around Y so model +Z → world +X
            else -> -90f    // W — rotate -90° around Y so model +Z → world -X
        }
        val yawRad = (yawDeg.toDouble() * PI / 180.0).toFloat()
        val cy = cos(yawRad)
        val sy = sin(yawRad)

        val lx = 0.30f; val ly2 = 0.45f; val lz = -0.84f
        val nLevels = 6
        val diffMin = 0.72f
        val diffRange = 0.28f

        for (g in mainBuildingGroups) {
            val baseCol = mainBuildingBaseColor(g.materialName, g)
            val isWindow = g.materialName == "Material.001"
            // Sidewalk-tagging: we infer per-triangle (not per-material) since
            // the user's body material may also contain non-sidewalk geometry.
            // Any triangle whose vertices are all below the sidewalk-y cutoff
            // is hidden from the aerial view via aerialSkip.
            val buckets = Array(nLevels) { ArrayList<Float>(64) }
            val sidewalkBuckets = Array(nLevels) { ArrayList<Float>(64) }
            val src = g.verts
            var i = 0
            while (i + 8 < src.size) {
                val x0m = src[i    ]; val y0m = src[i + 1]; val z0m = src[i + 2]
                val x1m = src[i + 3]; val y1m = src[i + 4]; val z1m = src[i + 5]
                val x2m = src[i + 6]; val y2m = src[i + 7]; val z2m = src[i + 8]

                val sx0 = x0m * sX; val sy0 = y0m * sY; val sz0 = z0m * sZ
                val sx1 = x1m * sX; val sy1 = y1m * sY; val sz1 = z1m * sZ
                val sx2 = x2m * sX; val sy2 = y2m * sY; val sz2 = z2m * sZ

                val x0w =  sx0 * cy + sz0 * sy + cx
                val z0w = -sx0 * sy + sz0 * cy + cz
                val x1w =  sx1 * cy + sz1 * sy + cx
                val z1w = -sx1 * sy + sz1 * cy + cz
                val x2w =  sx2 * cy + sz2 * sy + cx
                val z2w = -sx2 * sy + sz2 * cy + cz
                val y0w = sy0 + liftY
                val y1w = sy1 + liftY
                val y2w = sy2 + liftY

                val e1x = x1w - x0w; val e1y = y1w - y0w; val e1z = z1w - z0w
                val e2x = x2w - x0w; val e2y = y2w - y0w; val e2z = z2w - z0w
                val nxR = e1y * e2z - e1z * e2y
                val nyR = e1z * e2x - e1x * e2z
                val nzR = e1x * e2y - e1y * e2x
                val nLen = sqrt(nxR * nxR + nyR * nyR + nzR * nzR)
                val nx: Float; val ny: Float; val nz: Float
                if (nLen > 1e-5f) { nx = nxR / nLen; ny = nyR / nLen; nz = nzR / nLen }
                else              { nx = 0f; ny = 1f; nz = 0f }
                val ndotl = kotlin.math.abs(nx * lx + ny * ly2 + nz * lz)
                val diffuse = diffMin + ndotl * diffRange
                val level = (((diffuse - diffMin) / diffRange) * (nLevels - 1)).toInt().coerceIn(0, nLevels - 1)

                val isSidewalk = y0m <= MB_SIDEWALK_Y_LIMIT &&
                                 y1m <= MB_SIDEWALK_Y_LIMIT &&
                                 y2m <= MB_SIDEWALK_Y_LIMIT
                val b = if (isSidewalk) sidewalkBuckets[level] else buckets[level]
                b.add(x0w); b.add(y0w); b.add(z0w)
                b.add(x1w); b.add(y1w); b.add(z1w)
                b.add(x2w); b.add(y2w); b.add(z2w)
                i += 9
            }
            for (level in 0 until nLevels) {
                val brightness = diffMin + level.toFloat() / (nLevels - 1) * diffRange
                val tagDigit = if (isWindow && digit in 1..9) digit else -1
                val visibleVerts = buckets[level]
                if (visibleVerts.isNotEmpty()) {
                    val arr = FloatArray(visibleVerts.size).also { for (k in visibleVerts.indices) it[k] = visibleVerts[k] }
                    meshes.add(Mesh(
                        arr.toFB(), GLES20.GL_TRIANGLES, arr.size / 3,
                        baseCol[0] * brightness, baseCol[1] * brightness, baseCol[2] * brightness,
                        1f, 0f, windowDigit = tagDigit
                    ))
                }
                val sidewalkVerts = sidewalkBuckets[level]
                if (sidewalkVerts.isNotEmpty()) {
                    val arr = FloatArray(sidewalkVerts.size).also { for (k in sidewalkVerts.indices) it[k] = sidewalkVerts[k] }
                    meshes.add(Mesh(
                        arr.toFB(), GLES20.GL_TRIANGLES, arr.size / 3,
                        baseCol[0] * brightness, baseCol[1] * brightness, baseCol[2] * brightness,
                        1f, 0f, aerialSkip = true
                    ))
                }
            }
        }
    }


    // ── 7-segment digits + symbol chars ──────────────────────────────────────

    private fun addDigit(cx: Float, y: Float, cz: Float, s: Float, digit: String,
                         dim: Boolean = false, colR: Float = -1f, colG: Float = -1f, colB: Float = -1f) {
        val r: Float; val g: Float; val b: Float
        if (colR >= 0f) { r=colR; g=colG; b=colB }
        else if (dim) { r=0.0f; g=0.0f; b=0.0f } else { r=0.0f; g=0.0f; b=0.0f }
        val f = 0.04f; val sw2 = s*0.17f; val half = s*0.82f
        fun hSeg(zc: Float) = addQ(cx-half,y,zc-sw2, cx+half,y,zc-sw2, cx+half,y,zc+sw2, cx-half,y,zc+sw2, r,g,b,fog=f)
        fun vSeg(xc: Float, zT: Float, zB: Float) = addQ(xc-sw2,y,zT, xc+sw2,y,zT, xc+sw2,y,zB, xc-sw2,y,zB, r,g,b,fog=f)

        when (digit) {
            "." -> {
                val dw = s*0.22f
                addQ(cx-dw,y,cz+half-dw*1.5f, cx+dw,y,cz+half-dw*1.5f, cx+dw,y,cz+half+dw*0.5f, cx-dw,y,cz+half+dw*0.5f, r,g,b,fog=f)
                return
            }
            "+" -> {
                hSeg(cz)  // middle horizontal
                addQ(cx-sw2,y,cz-half, cx+sw2,y,cz-half, cx+sw2,y,cz, cx-sw2,y,cz, r,g,b,fog=f)   // top half vertical
                addQ(cx-sw2,y,cz, cx+sw2,y,cz, cx+sw2,y,cz+half, cx-sw2,y,cz+half, r,g,b,fog=f)   // bottom half vertical
                return
            }
            "/" -> {
                // Diagonal slash parallelogram from bottom-left to top-right
                val d = sw2 * 1.5f
                addQ(cx-half,y,cz+half-d, cx-half+d*2,y,cz+half, cx+half,y,cz-half+d, cx+half-d*2,y,cz-half, r,g,b,fog=f)
                return
            }
            "%" -> {
                val sq = s * 0.22f
                // Top-left square (dot)
                addQ(cx-half,y,cz-half, cx-half+sq*2,y,cz-half, cx-half+sq*2,y,cz-half+sq*2, cx-half,y,cz-half+sq*2, r,g,b,fog=f)
                // Bottom-right square (dot)
                addQ(cx+half-sq*2,y,cz+half-sq*2, cx+half,y,cz+half-sq*2, cx+half,y,cz+half, cx+half-sq*2,y,cz+half, r,g,b,fog=f)
                // Diagonal slash between them
                val d = sw2 * 1.3f
                addQ(cx-half+sq,y,cz+half-d, cx-half+sq+d*2,y,cz+half-sq, cx+half-sq,y,cz-half+d, cx+half-sq-d*2,y,cz-half+sq, r,g,b,fog=f)
                return
            }
            "()" -> {
                // Two I-beam parentheses side by side
                val barX = half * 0.44f; val barW = sw2; val tabW = sw2 * 2.2f
                // Left bar ( with tabs at top/bottom
                addQ(cx-barX-barW,y,cz-half, cx-barX+barW,y,cz-half, cx-barX+barW,y,cz+half, cx-barX-barW,y,cz+half, r,g,b,fog=f)
                addQ(cx-barX-barW,y,cz-half, cx-barX+tabW,y,cz-half, cx-barX+tabW,y,cz-half+sw2*2, cx-barX-barW,y,cz-half+sw2*2, r,g,b,fog=f)
                addQ(cx-barX-barW,y,cz+half-sw2*2, cx-barX+tabW,y,cz+half-sw2*2, cx-barX+tabW,y,cz+half, cx-barX-barW,y,cz+half, r,g,b,fog=f)
                // Right bar ) with tabs
                addQ(cx+barX-barW,y,cz-half, cx+barX+barW,y,cz-half, cx+barX+barW,y,cz+half, cx+barX-barW,y,cz+half, r,g,b,fog=f)
                addQ(cx+barX-tabW,y,cz-half, cx+barX+barW,y,cz-half, cx+barX+barW,y,cz-half+sw2*2, cx+barX-tabW,y,cz-half+sw2*2, r,g,b,fog=f)
                addQ(cx+barX-tabW,y,cz+half-sw2*2, cx+barX+barW,y,cz+half-sw2*2, cx+barX+barW,y,cz+half, cx+barX-tabW,y,cz+half, r,g,b,fog=f)
                return
            }
            "D" -> {
                // Left bar + top + bottom + inset right bar  →  clearly reads as D not U
                vSeg(cx-half, cz-half, cz+half)                                  // left full bar
                hSeg(cz-half)                                                     // top bar
                hSeg(cz+half)                                                     // bottom bar
                vSeg(cx+half, cz-half+sw2*2f, cz+half-sw2*2f)                    // right bar (inset so it doesn't overlap corners)
                return
            }
            "*" -> {
                // Asterisk: + cross, then two clean diagonal bars slightly raised to avoid z-fighting
                hSeg(cz)  // horizontal
                addQ(cx-sw2,y,cz-half, cx+sw2,y,cz-half, cx+sw2,y,cz+half, cx-sw2,y,cz+half, r,g,b,fog=f)  // full vertical
                val d = sw2 * 0.71f  // half-bar-width for diagonals (perpendicular offset)
                val dy = y + 0.15f   // raise diagonals slightly to prevent z-fighting with cross
                // NW→SE diagonal (top-left → bottom-right)
                addQ(cx-half+d,dy,cz-half-d, cx-half-d,dy,cz-half+d, cx+half-d,dy,cz+half+d, cx+half+d,dy,cz+half-d, r,g,b,fog=f)
                // NE→SW diagonal (top-right → bottom-left)
                addQ(cx+half+d,dy,cz-half+d, cx+half-d,dy,cz-half-d, cx-half-d,dy,cz+half-d, cx-half+d,dy,cz+half+d, r,g,b,fog=f)
                return
            }
        }

        // Standard 7-segment: a=top b=top-right c=bot-right d=bottom e=bot-left f=top-left g=mid
        val s7 = when (digit) {
            "1" -> booleanArrayOf(false,true,true,false,false,false,false)
            "2" -> booleanArrayOf(true,true,false,true,true,false,true)
            "3" -> booleanArrayOf(true,true,true,true,false,false,true)
            "4" -> booleanArrayOf(false,true,true,false,false,true,true)
            "5" -> booleanArrayOf(true,false,true,true,false,true,true)
            "6" -> booleanArrayOf(true,false,true,true,true,true,true)
            "7" -> booleanArrayOf(true,true,true,false,false,false,false)
            "8" -> booleanArrayOf(true,true,true,true,true,true,true)
            "9" -> booleanArrayOf(true,true,true,true,false,true,true)
            "0" -> booleanArrayOf(true,true,true,true,true,true,false)
            "d" -> booleanArrayOf(false,true,true,true,true,false,true)
            "E" -> booleanArrayOf(true,false,false,true,true,true,true)
            "L" -> booleanArrayOf(false,false,false,true,true,true,false)
            "-" -> booleanArrayOf(false,false,false,false,false,false,true)
            "=" -> booleanArrayOf(false,false,false,true,false,false,true)  // g + d = two horizontals
            "C" -> booleanArrayOf(true,false,false,true,true,true,false)   // a+d+e+f
            "R" -> booleanArrayOf(true,true,true,false,false,true,true)    // a+b+c+f+g  → P-bump + lower-right leg
            "A" -> booleanArrayOf(true,true,true,false,true,true,true)     // all except d
            else -> booleanArrayOf(false,false,false,false,false,false,false)
        }
        if (s7[0]) hSeg(cz-half)
        if (s7[1]) vSeg(cx+half, cz-half, cz)
        if (s7[2]) vSeg(cx+half, cz, cz+half)
        if (s7[3]) hSeg(cz+half)
        if (s7[4]) vSeg(cx-half, cz, cz+half)
        if (s7[5]) vSeg(cx-half, cz-half, cz)
        if (s7[6]) hSeg(cz)
    }

    // ── Damaged building (row E: DEL  0  .  +  function row A  +  operator col 4)
    // All non-door buildings now share the damaged-ruin look: one of three
    // Blender models (buildd1/2/3) is picked per plot with a free Y rotation,
    // both keyed off world position so the assignment is stable across scene
    // rebuilds while neighbouring ruins still read as distinct. The dominant
    // hue is driven by the calculator's row palette (DEL orange, 0 cream, /+-=
    // dark gray, etc.) — the model materials only contribute brightness
    // variation per triangle. CPU-side flat-normal diffuse is baked into per-
    // bucket sub-meshes so each face catches the sun differently without
    // needing a fragment-shader extension.
    private fun damagedPalette(label: String): FloatArray = when (label) {
        "DEL"     -> floatArrayOf(0.83f, 0.47f, 0.24f)   // rust orange — matches the original DEL row
        "0"       -> floatArrayOf(0.91f, 0.89f, 0.85f)   // digit cream
        "."       -> floatArrayOf(0.83f, 0.82f, 0.77f)   // washed cream
        "C"       -> floatArrayOf(0.79f, 0.27f, 0.24f)   // function-C dark red
        "()", "%" -> floatArrayOf(0.55f, 0.54f, 0.51f)   // function gray-beige
        else      -> floatArrayOf(0.42f, 0.42f, 0.42f)   // operator dark gray (/ * - + =)
    }
    private fun roofLabelWhite(label: String): Boolean =
        label != "0" && label != "."

    private fun addDamagedBuilding(cx: Float, cz: Float, h: Float, label: String) {
        val pal = damagedPalette(label)
        val seed = ((cx * 13.7f + cz * 31.1f).toInt() and 0x7fffffff)
        val pick = seed % 3
        // Snap rotation to 0/90/180/270 so neighbouring ruins stay parallel to
        // the calculator grid — keeps the silhouettes axis-aligned (free angles
        // pushed the corners off the cameras and looked messy).
        val yawDeg = ((seed / 7 % 4) * 90).toFloat()
        val (groups, halfW, halfH) = when (pick) {
            0    -> Triple(damagedGroupsA, 4.675f, 4.675f)
            1    -> Triple(damagedGroupsB, 3.134f, 7.080f)
            else -> Triple(damagedGroupsC, 3.689f, 5.705f)
        }
        if (groups.isNotEmpty()) {
            // Uniform XZ scale on the plot's mean half-footprint so any yaw
            // looks consistent on the ground; height stretches to fit the row's
            // chosen h.
            val sXZ = (BW + BD) * 0.5f / halfW
            val sY  = h * 0.5f / halfH
            val liftY = h * 0.5f
            val yawRad = (yawDeg.toDouble() * PI / 180.0).toFloat()
            val cy = cos(yawRad)
            val sy = sin(yawRad)

            // Sun roughly matches the existing sun disc (NE, high in the sky).
            val lx = 0.30f; val ly2 = 0.45f; val lz = -0.84f
            val nLevels = 6
            val diffMin = 0.72f
            val diffRange = 0.28f

            for (g in groups) {
                // Material luminance gives subtle per-material variation
                // without dragging the hue away from the row palette.
                val matLum = (g.r + g.g + g.b) / 3f
                val matMod = (0.78f + matLum * 0.42f).coerceAtMost(1.05f)

                val buckets = Array(nLevels) { ArrayList<Float>(64) }
                val src = g.verts
                var i = 0
                while (i + 8 < src.size) {
                    val x0m = src[i    ] * sXZ; val y0m = src[i + 1] * sY; val z0m = src[i + 2] * sXZ
                    val x1m = src[i + 3] * sXZ; val y1m = src[i + 4] * sY; val z1m = src[i + 5] * sXZ
                    val x2m = src[i + 6] * sXZ; val y2m = src[i + 7] * sY; val z2m = src[i + 8] * sXZ
                    val x0 =  x0m * cy + z0m * sy + cx
                    val z0 = -x0m * sy + z0m * cy + cz
                    val x1 =  x1m * cy + z1m * sy + cx
                    val z1 = -x1m * sy + z1m * cy + cz
                    val x2 =  x2m * cy + z2m * sy + cx
                    val z2 = -x2m * sy + z2m * cy + cz
                    val y0 = y0m + liftY
                    val y1 = y1m + liftY
                    val y2 = y2m + liftY

                    val e1x = x1 - x0; val e1y = y1 - y0; val e1z = z1 - z0
                    val e2x = x2 - x0; val e2y = y2 - y0; val e2z = z2 - z0
                    val nxR = e1y * e2z - e1z * e2y
                    val nyR = e1z * e2x - e1x * e2z
                    val nzR = e1x * e2y - e1y * e2x
                    val nLen = sqrt(nxR * nxR + nyR * nyR + nzR * nzR)
                    val nx: Float; val ny: Float; val nz: Float
                    if (nLen > 1e-5f) { nx = nxR / nLen; ny = nyR / nLen; nz = nzR / nLen }
                    else              { nx = 0f; ny = 1f; nz = 0f }

                    val ndotl = kotlin.math.abs(nx * lx + ny * ly2 + nz * lz)
                    val diffuse = diffMin + ndotl * diffRange
                    val level = (((diffuse - diffMin) / diffRange) * (nLevels - 1)).toInt().coerceIn(0, nLevels - 1)

                    val b = buckets[level]
                    b.add(x0); b.add(y0); b.add(z0)
                    b.add(x1); b.add(y1); b.add(z1)
                    b.add(x2); b.add(y2); b.add(z2)
                    i += 9
                }
                for (level in 0 until nLevels) {
                    val verts = buckets[level]
                    if (verts.isEmpty()) continue
                    val brightness = diffMin + level.toFloat() / (nLevels - 1) * diffRange
                    val mul = (matMod * brightness)
                    val arr = FloatArray(verts.size).also { for (k in verts.indices) it[k] = verts[k] }
                    meshes.add(Mesh(
                        arr.toFB(), GLES20.GL_TRIANGLES, arr.size / 3,
                        (pal[0] * mul).coerceAtMost(1f),
                        (pal[1] * mul).coerceAtMost(1f),
                        (pal[2] * mul).coerceAtMost(1f),
                        1f, 0f
                    ))
                }
            }
        } else {
            // Chamfered fallback in the row palette if the models don't load.
            val x0 = cx - BW; val x1 = cx + BW; val z0 = cz - BD; val z1 = cz + BD
            addChamfWalls(x0, z0, x1, z1, h,
                pal[0], pal[1], pal[2],
                pal[0] * 0.62f, pal[1] * 0.62f, pal[2] * 0.62f,
                pal[0] * 0.83f, pal[1] * 0.83f, pal[2] * 0.83f,
                pal[0] * 0.74f, pal[1] * 0.74f, pal[2] * 0.74f,
                pal[0] * 0.95f, pal[1] * 0.95f, pal[2] * 0.95f,
                fog = 0.0f)
        }

        // Roof label — white on every ruin except the "0" / "." cream ones
        // (which keep the original dark digit so they stay legible on cream).
        val ly = h + 2f
        val white = roofLabelWhite(label)
        when (label) {
            "DEL" -> {
                val ds = 20f; val spacing = ds * 2.2f; val startX = cx - spacing
                addDigit(startX,                  ly + 0.4f, cz, ds, "d", colR = 1.0f, colG = 1.0f, colB = 1.0f)
                addDigit(startX + spacing,        ly + 0.4f, cz, ds, "E", colR = 1.0f, colG = 1.0f, colB = 1.0f)
                addDigit(startX + spacing * 2f,   ly + 0.4f, cz, ds, "L", colR = 1.0f, colG = 1.0f, colB = 1.0f)
            }
            else -> {
                if (white) addDigit(cx, ly + 0.4f, cz, 30f, label, colR = 1.0f, colG = 1.0f, colB = 1.0f)
                else       addDigit(cx, ly + 0.4f, cz, 30f, label)
            }
        }
    }

    // ── West wall ─────────────────────────────────────────────────────────────

    private fun addWestWall() {
        val wx=WALL_X; val wt=20f; val wh=BH*1.5f*buildingHeightScale
        val z0=RA-BD; val z1=RE+BD
        addQ(wx+wt,0f,z1, wx+wt,0f,z0, wx+wt,wh,z0, wx+wt,wh,z1, 0.96f,0.94f,0.88f, fog=0.0f)
        addQ(wx,wh,z1, wx+wt,wh,z1, wx+wt,wh,z0, wx,wh,z0,       0.96f,0.94f,0.88f, fog=0.0f)
        addQ(wx,0f,z1, wx+wt,0f,z1, wx+wt,wh,z1, wx,wh,z1,       0.96f,0.94f,0.88f, fog=0.0f)
        addQ(wx+wt,0f,z0, wx,0f,z0, wx,wh,z0, wx+wt,wh,z0,       0.96f,0.94f,0.88f, fog=0.0f)
        val bk=0.04f

        for (i in 1..8) addL(wx+wt+0.4f,wh*i/9f,z1, wx+wt+0.4f,wh*i/9f,z0, 0.22f,0.14f,0.09f)
        val rand = java.util.Random(42L)
        repeat(4) {
            val czC = lerp(z1, z0, rand.nextFloat()); val cy = rand.nextFloat()*wh*0.80f
            addL(wx+wt+0.5f,cy,czC, wx+wt+0.5f,cy+40f+rand.nextFloat()*70f,czC+rand.nextFloat()*22f-11f, 0.20f,0.14f,0.10f)
        }
    }

    // ── Lava screen ───────────────────────────────────────────────────────────

    private fun addLava() {
        val lxW=-600f; val lxE=600f
        addRoundedRectFill(lxW, LAVA_Y, LAVA_N, lxE, LAVA_S - 20f,
            12f, 1.0f, 0.32f, 0.04f, fog=0.05f)
        // Molten streaks — red/orange, glow so they emit light at night (PASS 2).
        val rnd = java.util.Random(4242L)
        val y = LAVA_Y + 1f
        val zSpan = abs(LAVA_N - LAVA_S)
        for (i in 0 until 18) {
            val cx = lxW + 70f + rnd.nextFloat() * (lxE - lxW - 140f)
            val cz = LAVA_N + 40f + rnd.nextFloat() * (zSpan - 80f)
            val hw = 7f + rnd.nextFloat() * 9f       // half-width
            val hl = 28f + rnd.nextFloat() * 50f     // half-length (streaks run along Z)
            val orange = i % 2 == 0
            val r = if (orange) 1.0f else 0.95f
            val g = if (orange) 0.46f else 0.13f
            val b = if (orange) 0.06f else 0.02f
            addQ(cx-hw, y, cz-hl,  cx+hw, y, cz-hl,  cx+hw, y, cz+hl,  cx-hw, y, cz+hl,
                 r, g, b, a=1f, fog=0.05f, glow=true)
        }
    }

    private fun addRoundedRectFill(
        x0: Float, y: Float, z0: Float, x1: Float, z1: Float,
        r: Float, rr: Float, gg: Float, bb: Float, segs: Int=6, fog: Float=0.20f
    ) {
        val pi = PI.toFloat()
        val cx = (x0+x1)/2f; val cz = (z0+z1)/2f
        val corners = arrayOf(
            floatArrayOf(x1-r, z0+r, -pi/2f),
            floatArrayOf(x1-r, z1-r,  0f   ),
            floatArrayOf(x0+r, z1-r,  pi/2f),
            floatArrayOf(x0+r, z0+r,  pi   ),
        )
        val pts = mutableListOf<Pair<Float,Float>>()
        for (cd in corners) {
            val ccx=cd[0]; val ccz=cd[1]; val a0=cd[2]
            for (i in 0..segs) {
                val a = a0+(pi/2f)*i/segs
                pts.add(Pair(ccx+r*cos(a), ccz+r*sin(a)))
            }
        }
        val verts = mutableListOf<Float>()
        for (i in pts.indices) {
            val j = (i+1)%pts.size
            verts += listOf(cx,y,cz, pts[i].first,y,pts[i].second, pts[j].first,y,pts[j].second)
        }
        val arr = verts.toFloatArray()
        val fb = ByteBuffer.allocateDirect(arr.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            .also { it.put(arr); it.position(0) }
        meshes.add(Mesh(fb, GLES20.GL_TRIANGLES, arr.size/3, rr,gg,bb,1f,fog))
    }

    // ── Green terrain north of lava ───────────────────────────────────────────

    private fun addGreenNorth() {
        addQ(-900f,0f,-2200f, 900f,0f,-2200f, 900f,0f,LAVA_N, -900f,0f,LAVA_N, 0.96f,0.94f,0.88f, fog=0.0f)
        val lzN=LAVA_N-14f; val lzS=LAVA_S+14f
        addQ(-900f,0f,lzN, CITY_W-14f,0f,lzN, CITY_W-14f,0f,lzS, -900f,0f,lzS, 0.96f,0.94f,0.88f, fog=0.0f)
        addQ(CITY_E+14f,0f,lzN, 900f,0f,lzN, 900f,0f,lzS, CITY_E+14f,0f,lzS, 0.96f,0.94f,0.88f, fog=0.0f)
        addQ(-900f,0f,lzS, 900f,0f,lzS, 900f,0f,CITY_N, -900f,0f,CITY_N, 0.96f,0.94f,0.88f, fog=0.0f)
    }

    // ── Sun — a disc in the SKY, at the far end of the SUN bearing ───────────
    // Built around the point SUN * SUN_DIST, then re-centred on the camera each
    // frame (the `sky` flag), so it reads as a body at infinity rather than a
    // slab parked inside the city. It never gets nearer, it keeps its place in
    // the sky as the player walks, and the buildings sweep across it.
    // Emissive: the sun is a light source, so it must never be shaded.

    /**
     * Cracks in the sky. Black jagged rifts hung on the sky dome, camera-relative
     * like the sun, so they sit ON the sky rather than in the city. Drawn only
     * during the collapse, opening one after another — the sky tearing, not
     * shattering all at once.
     */
    private fun addSkyCracks() {
        val rnd = java.util.Random(20261013L)   // fixed: the same sky tears the same way
        val n = 7
        for (i in 0 until n) {
            var az = rnd.nextFloat() * 360f
            var el = 22f + rnd.nextFloat() * 50f
            var width = 26f + rnd.nextFloat() * 26f
            val segs = 7 + rnd.nextInt(5)
            fun pt(azimuth: Float, elev: Float, w: Float): FloatArray {
                val a = Math.toRadians(azimuth.toDouble())
                val e = Math.toRadians(elev.toDouble())
                val r = SKY_DIST * 0.92f
                return floatArrayOf(
                    (cos(e) * sin(a)).toFloat() * r,
                    sin(e).toFloat() * r,
                    (cos(e) * cos(a)).toFloat() * r,
                    w)
            }
            val verts = mutableListOf<Float>()
            var prev = pt(az, el, width)
            for (sgi in 0 until segs) {
                az += (rnd.nextFloat() - 0.35f) * 13f
                el += (rnd.nextFloat() - 0.6f) * 9f
                width *= 0.86f
                val cur = pt(az, el, width)
                val w0 = prev[3]; val w1 = cur[3]
                verts += listOf(
                    prev[0], prev[1] - w0, prev[2],  cur[0], cur[1] - w1, cur[2],  cur[0], cur[1] + w1, cur[2],
                    prev[0], prev[1] - w0, prev[2],  cur[0], cur[1] + w1, cur[2],  prev[0], prev[1] + w0, prev[2],
                )
                prev = cur
            }
            val arr = verts.toFloatArray()
            if (arr.isEmpty()) continue
            // Near-black: a crack is the ABSENCE of sky.
            meshes.add(Mesh(arr.toFB(), GLES20.GL_TRIANGLES, arr.size / 3,
                0.02f, 0.01f, 0.03f, 1f, fog = 0f,
                aerialSkip = true, sky = true, crack = true,
                arcAngle = (i + 1).toFloat() / n))   // reused field: when this one opens
        }
    }

    private fun addSun() {
        // Sun: fades out as night comes on.
        addSkyDisc(SUN, 95f, listOf(
            floatArrayOf(1.0f, 1.00f, 0.92f, 0.40f),   // golden core
            floatArrayOf(1.6f, 0.85f, 0.48f, 0.75f),   // inner purple
            floatArrayOf(2.5f, 0.55f, 0.22f, 0.72f),   // mid purple
            floatArrayOf(3.8f, 0.30f, 0.10f, 0.45f),   // outer glow
        ), night = false)
        // Moon: the night's key light, on its own fixed bearing, fading in as the
        // sun goes. Pale and cold, with a tight halo rather than a broad glow.
        addSkyDisc(MOON, 70f, listOf(
            floatArrayOf(1.0f, 0.93f, 0.95f, 1.00f),   // pale core
            floatArrayOf(1.35f, 0.62f, 0.70f, 0.92f),  // cold rim
            floatArrayOf(2.1f, 0.30f, 0.38f, 0.62f),   // faint halo
        ), night = true)
    }

    // A disc hung in the sky along `dir`, built around dir * SKY_DIST. The `sky`
    // flag re-centres it on the camera each frame, so it behaves as a body at
    // infinity: never nearer, always the same bearing, with the city passing in
    // front of it. Emissive - a light source must never be shaded.
    private fun addSkyDisc(dir: FloatArray, baseR: Float, rings: List<FloatArray>, night: Boolean) {
        val sx = dir[0] * SKY_DIST; val sy = dir[1] * SKY_DIST; val sz = dir[2] * SKY_DIST
        val segs = 24
        val pi = PI.toFloat()
        // The disc's face is normal to +Z. Both bodies sit off the player's
        // north/south axis, so each is seen close to face-on from the city.
        for (ring in rings) {
            val scale=ring[0]; val cr=ring[1]; val cg=ring[2]; val cb=ring[3]
            val r = baseR*scale
            val verts = mutableListOf<Float>()
            for (i in 0 until segs) {
                val a0 = i.toFloat()/segs*2f*pi
                val a1 = (i+1).toFloat()/segs*2f*pi
                verts += listOf(sx, sy, sz)
                verts += listOf(sx+r*cos(a0), sy+r*sin(a0), sz)
                verts += listOf(sx+r*cos(a1), sy+r*sin(a1), sz)
            }
            val arr = verts.toFloatArray()
            meshes.add(Mesh(arr.toFB(), GLES20.GL_TRIANGLES, arr.size/3, cr,cg,cb,1f, fog=0f,
                aerialSkip=true, sky=true, skyNight=night))
        }
    }

    // ── Building shadow ───────────────────────────────────────────────────────

    private fun addBuildingShadow(cx: Float, cz: Float, h: Float) {
        val sLen=h*0.50f; val sW=BW*1.15f
        addQ(cx-sW,0.15f,cz-BD, cx+sW,0.15f,cz-BD, cx+sW,0.15f,cz+BD, cx-sW,0.15f,cz+BD,
            0.96f,0.94f,0.88f, fog=0.0f)
        val z0=cz+BD+1f; val z1=z0+sLen
        addQ(cx-sW,0.15f,z0, cx+sW,0.15f,z0, cx+sW*0.6f,0.15f,z1, cx-sW*0.6f,0.15f,z1,
            0.96f,0.94f,0.88f, fog=0.0f)
    }
    // ── Debris barrier ────────────────────────────────────────────────────────
    // Instances one of debris1..10 at world position (cx, cz) sitting on the
    // ground. The model is uniformly scaled and rotated around Y. Per-instance
    // tinted color is applied via the per-material Kd modulation in addObjMesh —
    // here we just pre-multiply each group's Kd by the tint so the city's
    // existing palettes still drive the look.
    private fun addDebrisInstance(
        cx: Float, cz: Float, scale: Float, yawRad: Float,
        tintR: Float, tintG: Float, tintB: Float, fog: Float
    ) {
        if (debrisGroups.all { it.isEmpty() }) return
        // Pick a model deterministically from the position so the same chunk
        // appears in the same spot every scene rebuild.
        val pick = ((cx * 7.1f + cz * 13.7f + scale * 31f).toInt() and 0x7fffffff) % debrisGroups.size
        val groups = debrisGroups[pick]
        if (groups.isEmpty()) return
        val cy = cos(yawRad.toDouble()).toFloat()
        val sy = sin(yawRad.toDouble()).toFloat()
        for (g in groups) {
            val src = g.verts
            val out = FloatArray(src.size)
            var i = 0
            while (i < src.size) {
                val xs = src[i]     * scale
                val ys = src[i + 1] * scale
                val zs = src[i + 2] * scale
                // Yaw around +Y. New x = x*cosY + z*sinY ; new z = -x*sinY + z*cosY.
                out[i    ] =  xs * cy + zs * sy + cx
                // The icospheres are centered at y=0 with a half-height of 1 unit,
                // so lifting by `scale` lands the lowest vertex flush with ground.
                out[i + 1] = ys + scale * 1f
                out[i + 2] = -xs * sy + zs * cy + cz
                i += 3
            }
            // debris MTLs ship without Kd, so we drive the colour entirely from
            // the per-instance palette tint passed in by the caller.
            val r = tintR.coerceIn(0f, 1f)
            val gC = tintG.coerceIn(0f, 1f)
            val b = tintB.coerceIn(0f, 1f)
            // aerialSkip = true so the rubble doesn't clutter the high-up
            // aerial pose during the intro. Revealed at cut 3 along with the
            // sidewalks / lamps / cameras.
            meshes.add(Mesh(out.toFB(), GLES20.GL_TRIANGLES, out.size / 3, r, gC, b, 1f, fog, aerialSkip = true))
        }
    }

    private fun addDebris() {
        val rnd = java.util.Random(77L)

        // South face of each row E damaged building: rubble spilling outward
        val southDebrisColors = listOf(
            floatArrayOf(0.68f,0.65f,0.62f),  // concrete gray
            floatArrayOf(0.65f,0.32f,0.18f),  // rust-orange (matches DEL)
            floatArrayOf(0.54f,0.50f,0.45f),  // sandy rubble
            floatArrayOf(0.32f,0.28f,0.24f),  // charcoal dark
        )
        for (fp in DAMAGED_BLDGS) {
            val cx = fp[0]; val cz = fp[1]
            val z1 = cz + BD + 4f
            repeat(12) {
                val px = cx + rnd.nextFloat() * BW * 1.8f - BW * 0.9f
                val pz = z1 + rnd.nextFloat() * 80f
                val scale = 5f + rnd.nextFloat() * 12f
                val yaw = rnd.nextFloat() * 2f * PI.toFloat()
                val sc = southDebrisColors[rnd.nextInt(southDebrisColors.size)]
                val tintBase = 0.85f + rnd.nextFloat() * 0.30f
                addDebrisInstance(px, pz, scale, yaw, sc[0] * tintBase, sc[1] * tintBase, sc[2] * tintBase, fog = 0.30f)
            }
        }

        // Row E horizontal gaps (south boundary + between buildings)
        val gapZ0 = RE + BD + 2f; val gapZ1 = gapZ0 + 60f
        val rowEGaps = listOf(
            Pair(C1 + BW + 2f, C2 - BW - 2f),
            Pair(C2 + BW + 2f, C3 - BW - 2f),
            Pair(C3 + BW + 2f, C4 - BW - 2f),
            Pair(-600f, C1 - BW - 2f),
            Pair(C4 + BW + 2f, 600f),
        )
        for ((gx0, gx1) in rowEGaps) {
            if (gx1 <= gx0) continue
            repeat(8) {
                val px = gx0 + rnd.nextFloat() * (gx1 - gx0)
                val pz = gapZ0 + rnd.nextFloat() * (gapZ1 - gapZ0)
                val scale = 7f + rnd.nextFloat() * 14f
                val yaw = rnd.nextFloat() * 2f * PI.toFloat()
                val sc = southDebrisColors[rnd.nextInt(southDebrisColors.size)]
                val tintBase = 0.85f + rnd.nextFloat() * 0.30f
                addDebrisInstance(px, pz, scale, yaw, sc[0] * tintBase, sc[1] * tintBase, sc[2] * tintBase, fog = 0.30f)
            }
        }

        // Col 4 (east operator column) — between rows A–E
        val debrisColors = listOf(
            floatArrayOf(0.42f, 0.40f, 0.38f),
            floatArrayOf(0.55f, 0.30f, 0.22f),
            floatArrayOf(0.28f, 0.25f, 0.22f),
            floatArrayOf(0.50f, 0.46f, 0.38f),
        )
        val col4Gaps = listOf(
            Pair(RA + BD + 2f, RB - BD - 2f),
            Pair(RB + BD + 2f, RC - BD - 2f),
            Pair(RC + BD + 2f, RD - BD - 2f),
            Pair(RD + BD + 2f, RE - BD - 2f),
        )
        val rnd2 = java.util.Random(133L)
        for ((gz0, gz1) in col4Gaps) {
            if (gz1 <= gz0) continue
            val gapW = C4 + BW + 80f - (C4 - BW - 80f)
            repeat(14) {
                val px = C4 - BW - 75f + rnd2.nextFloat() * gapW
                val pz = gz0 + rnd2.nextFloat() * (gz1 - gz0)
                val scale = 8f + rnd2.nextFloat() * 18f
                val yaw = rnd2.nextFloat() * 2f * PI.toFloat()
                val col = debrisColors[rnd2.nextInt(debrisColors.size)]
                val tintBase = 0.85f + rnd2.nextFloat() * 0.30f
                addDebrisInstance(px, pz, scale, yaw, col[0] * tintBase, col[1] * tintBase, col[2] * tintBase, fog = 0.28f)
            }
        }
    }

    // ── Stickman figures (decorative, outside the city) ───────────────────────
    // Each stickman OBJ is Y-up and roughly centred on the XZ origin. We scale
    // the model so its full height maps to `targetH` world units, then drop the
    // model's lowest vertex onto `groundY` so the feet rest on the surface
    // (ground = 0, lava surface ≈ 22). Per-group Kd from the MTL drives the
    // colour (red body / black limbs), and aerialSkip hides them from the
    // aerial intro + post-building fly-over.
    private val STICKMAN_MIN_Y  = -4.305f
    private val STICKMAN_H      =  8.726f
    private val STICKMAN2_MIN_Y = -1.516f
    private val STICKMAN2_H     =  1.600f

    private fun addCharacterInstance(
        groups: List<ObjGroup>, modelMinY: Float, modelHeight: Float,
        cx: Float, cz: Float, groundY: Float, targetH: Float, yawRad: Float, fog: Float
    ) {
        if (groups.isEmpty()) return
        val scale = targetH / modelHeight
        val liftY = -modelMinY * scale + groundY
        val cy = cos(yawRad.toDouble()).toFloat()
        val sy = sin(yawRad.toDouble()).toFloat()
        for (g in groups) {
            val src = g.verts
            val out = FloatArray(src.size)
            var i = 0
            while (i < src.size) {
                val xs = src[i]     * scale
                val ys = src[i + 1] * scale
                val zs = src[i + 2] * scale
                out[i    ] =  xs * cy + zs * sy + cx
                out[i + 1] =  ys + liftY
                out[i + 2] = -xs * sy + zs * cy + cz
                i += 3
            }
            // Keep the model's authored per-material colour (Kd from the MTL).
            meshes.add(Mesh(out.toFB(), GLES20.GL_TRIANGLES, out.size / 3,
                g.r, g.g, g.b, 1f, fog, aerialSkip = true))
        }
    }

    // Target world height — about half the surrounding ruins (digit/damaged
    // buildings render ~350-400 units tall).
    private val STICKMAN_TARGET_H = 168f

    // Portrait: 3 of each stickman spread across the three "outside the city"
    // zones — tucked behind the top (row A) buildings, in the gaps between the
    // row-E damaged buildings, and out in the south rubble field below the city.
    private fun addStickmen() {
        if (stickmanGroups.isEmpty() && stickman2Groups.isEmpty()) return
        val h = STICKMAN_TARGET_H
        // Each figure is its own collapsible thing, and the kind that gets carried up
        // rather than knocked down (see collapseXform). A tight footprint, so no
        // camera or door on a nearby building can be mistaken for part of one.
        fun statue(groups: List<ObjGroup>, minY: Float, mh: Float,
                   x: Float, z: Float, yaw: Float, fog: Float) {
            collapsible(x, z, 20f, 20f, COLLAPSE_SWEPT) {
                addCharacterInstance(groups, minY, mh, x, z, 0f, h, yaw, fog)
            }
        }
        // ── Hiding behind the top row (just north of the row-A buildings) ────
        statue(stickmanGroups,  STICKMAN_MIN_Y,  STICKMAN_H,  C1, RA - BD - 30f, 0.5f,  0.32f)
        statue(stickman2Groups, STICKMAN2_MIN_Y, STICKMAN2_H, C3, RA - BD - 30f, -1.0f, 0.32f)
        // ── Between the damaged buildings (row E gaps) ───────────────────────
        statue(stickmanGroups,  STICKMAN_MIN_Y,  STICKMAN_H,  (C2 + C3) * 0.5f, RE, 2.3f,  0.30f)
        statue(stickman2Groups, STICKMAN2_MIN_Y, STICKMAN2_H, (C1 + C2) * 0.5f, RE, -0.4f, 0.30f)
        // ── Under the city (south rubble field) ──────────────────────────────
        statue(stickmanGroups,  STICKMAN_MIN_Y,  STICKMAN_H,  -150f, RE + BD + 90f,  3.4f, 0.30f)
        statue(stickman2Groups, STICKMAN2_MIN_Y, STICKMAN2_H,  175f, RE + BD + 120f, 1.2f, 0.30f)
    }

    // Landscape: same three zones in the rotated layout.
    private fun addStickmenLandscape(lC1: Float, lC2: Float, lC3: Float, lRA: Float, lRE: Float) {
        if (stickmanGroups.isEmpty() && stickman2Groups.isEmpty()) return
        val h = STICKMAN_TARGET_H
        // ── Hiding behind the top row (north of the row-A buildings) ─────────
        addCharacterInstance(stickmanGroups,  STICKMAN_MIN_Y,  STICKMAN_H,  lC1, lRA - BD - 30f, 0f, h, 0.4f,  fog = 0.32f)
        addCharacterInstance(stickman2Groups, STICKMAN2_MIN_Y, STICKMAN2_H, lC3, lRA - BD - 30f, 0f, h, -0.9f, fog = 0.32f)
        // ── Between the damaged buildings (row lRE gaps) ─────────────────────
        addCharacterInstance(stickmanGroups,  STICKMAN_MIN_Y,  STICKMAN_H,  (lC2 + lC3) * 0.5f, lRE, 0f, h, 2.1f,  fog = 0.30f)
        addCharacterInstance(stickman2Groups, STICKMAN2_MIN_Y, STICKMAN2_H, (lC1 + lC2) * 0.5f, lRE, 0f, h, -0.3f, fog = 0.30f)
        // ── South of the damaged row (rubble field) ──────────────────────────
        addCharacterInstance(stickmanGroups,  STICKMAN_MIN_Y,  STICKMAN_H,  lC2, lRE + BD + 90f,  0f, h, 3.2f, fog = 0.30f)
        addCharacterInstance(stickman2Groups, STICKMAN2_MIN_Y, STICKMAN2_H, lC3, lRE + BD + 120f, 0f, h, 1.0f, fog = 0.30f)
    }

    // ── Player orb ────────────────────────────────────────────────────────────

    private fun drawOrb(cx: Float, cy: Float, cz: Float, r: Float, mvp: FloatArray) {
        setLit(0f)
        val top=floatArrayOf(cx,cy+r,cz); val bot=floatArrayOf(cx,cy-r,cz)
        val n=floatArrayOf(cx,cy,cz-r);   val s=floatArrayOf(cx,cy,cz+r)
        val e=floatArrayOf(cx+r,cy,cz);   val w=floatArrayOf(cx-r,cy,cz)
        fun tri(a: FloatArray, b: FloatArray, c: FloatArray) =
            floatArrayOf(a[0],a[1],a[2],b[0],b[1],b[2],c[0],c[1],c[2])
        val verts = listOf(
            tri(top,e,n),tri(top,n,w),tri(top,w,s),tri(top,s,e),
            tri(bot,n,e),tri(bot,w,n),tri(bot,s,w),tri(bot,e,s)
        ).flatMap{it.toList()}.toFloatArray()
        val fb = verts.toFB()
        val top2=floatArrayOf(cx,cy+r*1.7f,cz); val bot2=floatArrayOf(cx,cy-r*1.7f,cz)
        val n2=floatArrayOf(cx,cy,cz-r*1.7f); val s2=floatArrayOf(cx,cy,cz+r*1.7f)
        val e2=floatArrayOf(cx+r*1.7f,cy,cz); val w2=floatArrayOf(cx-r*1.7f,cy,cz)
        val halo = listOf(
            tri(top2,e2,n2),tri(top2,n2,w2),tri(top2,w2,s2),tri(top2,s2,e2),
            tri(bot2,n2,e2),tri(bot2,w2,n2),tri(bot2,s2,w2),tri(bot2,e2,s2)
        ).flatMap{it.toList()}.toFloatArray()
        val hfb = halo.toFB()

        GLES20.glUniform4f(uCol, 0.55f,0.20f,0.90f,1f)
        GLES20.glUniform1f(uFog, 0.0f)
        hfb.position(0)
        GLES20.glVertexAttribPointer(aPos,3,GLES20.GL_FLOAT,false,12,hfb)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,24)

        GLES20.glUniform4f(uCol, 0.85f,0.92f,1.0f,1f)
        fb.position(0)
        GLES20.glVertexAttribPointer(aPos,3,GLES20.GL_FLOAT,false,12,fb)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,24)
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    // ── RAD button — large cylindrical button floating on the lava ───────────

    // How much bigger the city's (enterable) mute button is than the raw model.
    // Must stay in step with RAD_SCALE in CalculatorCityView, which derives the
    // player's collision shell and doorway from it.
    private val RAD_SCALE = 3f

    // Building 10's doorway — an opening the model itself carries in its drum wall,
    // measured from the mesh: it spans 90°..101° around the drum (0° = +X, 90° = +Z,
    // i.e. the south face, toward the bridge) and rises to 12.5 of the wall's 33.5.
    // Nothing is cut here; the hole is simply filled with a black panel (the door),
    // which stops being drawn once the player has given their rating (radDoorOpen).
    private val RAD_DOOR_DEG      = 95.5f   // centre of the model's opening
    private val RAD_DOOR_HALF_DEG = 5.75f   // ⇒ ~53 world units wide at r0 = 264
    private val RAD_DOOR_TOP_FRAC = 0.373f  // 12.5 / 33.5 of the drum's height
    @Volatile var radDoorOpen = false

    // The city's mute button is enterable (Building 10), so it's blown up to
    // RAD_SCALE — at 1× the props inside the model only come up to the player's
    // knee. The landscape decoration keeps 1×.
    private fun addRadButton(h: Float, bx: Float = 0f, bz: Float = -CELL * 5.75f,
                             scale: Float = 1f) {
        val baseY = 0f               // sits on ground level
        var topY  = baseY + h
        val r0    = 88f * scale      // outer radius
        val rimW  = 60f * scale      // rim width
        val rimH  =  6f * scale      // rim height above top face
        val sides = 16
        val fog   = 0.06f
        radBx = bx; radBz = bz; radOuterR = r0

        // Colors
        val tR=1.00f; val tG=1.00f; val tB=1.00f   // top face white
        val sR=0.65f; val sG=0.36f; val sB=0.05f   // side walls orange
        val rR=0.98f; val rG=0.62f; val rB=0.14f   // rim lighter orange
        val eR=0.30f; val eG=0.14f; val eB=0.01f   // dark outline

        val mute = muteButtonGroups
        if (mute.isNotEmpty()) {
            // Building 10 = the player's mutebutton model, scaled to radius r0.
            var mnX=Float.MAX_VALUE; var mxX=-Float.MAX_VALUE
            var mnY=Float.MAX_VALUE; var mxY=-Float.MAX_VALUE
            var mnZ=Float.MAX_VALUE; var mxZ=-Float.MAX_VALUE
            for (g in mute) { var k=0; while (k < g.verts.size) {
                val x=g.verts[k]; val y=g.verts[k+1]; val z=g.verts[k+2]
                if (x<mnX) mnX=x; if (x>mxX) mxX=x; if (y<mnY) mnY=y; if (y>mxY) mxY=y
                if (z<mnZ) mnZ=z; if (z>mxZ) mxZ=z; k+=3 } }
            val cxL=(mnX+mxX)*0.5f; val czL=(mnZ+mxZ)*0.5f
            val wdt=maxOf(mxX-mnX, mxZ-mnZ).coerceAtLeast(1e-3f)
            val scl=(2f*r0)/wdt
            topY = (mxY-mnY)*scl
            // Split each material group into the button's SHELL (the drum wall and
            // its lid) and its INTERIOR (everything standing on the floor — the
            // buttons the player walks in to reach). Only the shell is drawn
            // see-through from inside, so the city stays visible past it.
            //   · wall   → local radius rides the drum's outer edge
            //   · lid    → sits high above the floor clutter
            // The interior props all top out at ~23% of the model's height and
            // stay inside 0.92 of its radius, so these thresholds separate cleanly.
            val shellR = (wdt * 0.5f) * 0.94f
            val shellY = (mxY - mnY) * 0.45f
            radDoorTopY = (mxY - mnY) * RAD_DOOR_TOP_FRAC * scl
            for (g in mute) {
                // A textured group (the whiteboards' image planes) goes in whole. It
                // is interior by definition — nothing on the drum's wall carries an
                // image — and the shell/inner split below reorders triangles, which
                // would tear its UVs away from the vertices they belong to.
                val texName = g.texture
                if (texName != null && g.uvs.isNotEmpty()) {
                    val out = FloatArray(g.verts.size)
                    var j = 0
                    while (j + 2 < out.size) {
                        out[j]     = (g.verts[j]     - cxL) * scl + bx
                        out[j + 1] = (g.verts[j + 1] - mnY) * scl + baseY
                        out[j + 2] = (g.verts[j + 2] - czL) * scl + bz
                        j += 3
                    }
                    val texId = modelTexture(texName)
                    if (texId != 0) {
                        meshes.add(Mesh(out.toFB(), GLES20.GL_TRIANGLES, out.size / 3,
                            1f, 1f, 1f, 1f, fog, tex = texId, uv = g.uvs.toFB()))
                        continue
                    }
                    // No image on disk — fall through and draw it flat, as before.
                }
                val shell = ArrayList<Float>()
                val inner = ArrayList<Float>()
                var k = 0
                while (k + 8 < g.verts.size) {
                    // Classify by triangle centroid in the model's own coordinates.
                    var cX = 0f; var cY = 0f; var cZ = 0f
                    for (v in 0 until 3) {
                        cX += g.verts[k + v*3]; cY += g.verts[k + v*3 + 1]; cZ += g.verts[k + v*3 + 2]
                    }
                    cX /= 3f; cY /= 3f; cZ /= 3f
                    val rLoc = hypot(cX - cxL, cZ - czL)
                    val dst = if (rLoc > shellR || (cY - mnY) > shellY) shell else inner
                    for (v in 0 until 9) dst.add(g.verts[k + v])
                    k += 9
                }
                // Colours arrive gamma-corrected from ObjLoader; the lighting does
                // the rest of the work now. The exception is the monitors' panels:
                // they're painted blue in the model, but a screen only has a colour
                // when something is on it — the live feed is drawn over them (see
                // buildCctvScreens), so the glass underneath goes near-black.
                val isScreen = g.materialName == SCREEN_MTL
                val r  = if (isScreen) 0.03f  else g.r
                val gg = if (isScreen) 0.035f else g.g
                val b  = if (isScreen) 0.04f  else g.b
                for (isShell in listOf(true, false)) {
                    val src = if (isShell) shell else inner
                    if (src.isEmpty()) continue
                    val out = FloatArray(src.size)
                    var j = 0
                    while (j < src.size) {
                        out[j]     = (src[j]     - cxL) * scl + bx
                        out[j + 1] = (src[j + 1] - mnY) * scl + baseY
                        out[j + 2] = (src[j + 2] - czL) * scl + bz
                        j += 3
                    }
                    meshes.add(Mesh(out.toFB(), GLES20.GL_TRIANGLES, out.size/3, r, gg, b, 1f, fog,
                        radShell = isShell))
                }
            }

            // The door: a black panel filling the model's own opening, following the
            // drum's curve. Skipped entirely once the rating has been given
            // (radDoorOpen), leaving just the doorway the model already has.
            run {
                val segs = 6
                val a0 = RAD_DOOR_DEG - RAD_DOOR_HALF_DEG
                val a1 = RAD_DOOR_DEG + RAD_DOOR_HALF_DEG
                for (i in 0 until segs) {
                    val t0 = Math.toRadians((a0 + (a1 - a0) * i / segs).toDouble())
                    val t1 = Math.toRadians((a0 + (a1 - a0) * (i + 1) / segs).toDouble())
                    val x0 = bx + cos(t0).toFloat() * r0; val z0 = bz + sin(t0).toFloat() * r0
                    val x1 = bx + cos(t1).toFloat() * r0; val z1 = bz + sin(t1).toFloat() * r0
                    val v = floatArrayOf(
                        x0, baseY, z0,  x1, baseY, z1,  x1, baseY + radDoorTopY, z1,
                        x0, baseY, z0,  x1, baseY + radDoorTopY, z1,  x0, baseY + radDoorTopY, z0,
                    )
                    meshes.add(Mesh(v.toFB(), GLES20.GL_TRIANGLES, 6,
                        0.02f, 0.02f, 0.03f, 1f, 0f, noAO = true, radDoor = true))
                }
            }

            // The desk. Only the enterable Building 10 gets live monitors — the
            // landscape scene keeps the button as a 1x decoration you never walk into.
            if (scale > 1f) buildCctvScreens(mute, cxL, czL, mnY, scl, bx, baseY, bz)

            // Collision footprints for the furniture inside — anything tall enough to
            // walk into. Skips the drum itself and the floor-flat props (rugs, papers).
            val props = mutableListOf<FloatArray>()
            for (ob in muteButtonBounds) {
                if (ob.name.startsWith("Cylinder") && (ob.maxY - ob.minY) > (mxY - mnY) * 0.45f) continue
                if (ob.maxY - mnY < (mxY - mnY) * 0.03f) continue    // too flat to block
                val wx0 = (ob.minX - cxL) * scl + bx; val wx1 = (ob.maxX - cxL) * scl + bx
                val wz0 = (ob.minZ - czL) * scl + bz; val wz1 = (ob.maxZ - czL) * scl + bz
                props.add(floatArrayOf(
                    (wx0 + wx1) * 0.5f, (wz0 + wz1) * 0.5f,
                    abs(wx1 - wx0) * 0.5f, abs(wz1 - wz0) * 0.5f))
            }
            radPropFootprints = props
        } else for (i in 0 until sides) {
            val a0 = i.toFloat() / sides * 2f * PI.toFloat()
            val a1 = (i + 1).toFloat() / sides * 2f * PI.toFloat()
            val x0 = bx + cos(a0).toFloat() * r0;    val z0v = bz + sin(a0).toFloat() * r0
            val x1 = bx + cos(a1).toFloat() * r0;    val z1v = bz + sin(a1).toFloat() * r0
            val xi0 = bx + cos(a0).toFloat() * (r0-rimW); val zi0 = bz + sin(a0).toFloat() * (r0-rimW)
            val xi1 = bx + cos(a1).toFloat() * (r0-rimW); val zi1 = bz + sin(a1).toFloat() * (r0-rimW)

            // Top face triangle fan (inner, up to rim) — white
            addTri(bx, topY, bz,  xi0, topY, zi0,  xi1, topY, zi1,  tR, tG, tB, fog=fog)

            // Rim ring (flat annular segment on top) — orange
            addQ(xi0, topY, zi0,  xi1, topY, zi1,  x1, topY, z1v,  x0, topY, z0v,   rR, rG, rB, fog=fog)

            // Rim outer lip (raised band around top edge)
            addQ(x0, topY+rimH, z0v,  x1, topY+rimH, z1v,  x1, topY, z1v,  x0, topY, z0v,  rR, rG, rB, fog=fog)

            // Side wall — shade by normal direction
            val nz = ((sin(a0)+sin(a1))*0.5f).coerceIn(-1f, 1f)
            val shade = 0.65f + nz * 0.35f
            addQ(x0, topY+rimH, z0v,  x1, topY+rimH, z1v,  x1, baseY, z1v,  x0, baseY, z0v,
                 sR*shade, sG*shade, sB*shade, fog=fog)

            // Outline on top of rim
            addL(x0, topY+rimH+1f, z0v,  x1, topY+rimH+1f, z1v,  eR, eG, eB)
        }

        // Spinning teal arc — vertical cylindrical shell wrapping the full height of the button
        // 36 segments × 10°; lit/dark decided per-frame in onDrawFrame via radAngle
        val arcSegs = 36
        val arcR    = r0 + rimW + 8f    // just outside the cylinder rim
        val arcTopY = topY + rimH + 2f  // slightly above the rim top
        for (i in 0 until arcSegs) {
            val a0 = i.toFloat() / arcSegs * 2f * PI.toFloat()
            val a1 = (i + 1).toFloat() / arcSegs * 2f * PI.toFloat()
            val segCenterDeg = (i + 0.5f) / arcSegs * 360f
            val x0v = bx + cos(a0).toFloat() * arcR;  val z0v = bz + sin(a0).toFloat() * arcR
            val x1v = bx + cos(a1).toFloat() * arcR;  val z1v = bz + sin(a1).toFloat() * arcR
            // Vertical quad: bottom-left, bottom-right, top-right, top-left
            val v = floatArrayOf(
                x0v, baseY,  z0v,   x1v, baseY,  z1v,   x1v, arcTopY, z1v,
                x0v, baseY,  z0v,   x1v, arcTopY, z1v,   x0v, arcTopY, z0v
            )
            meshes.add(Mesh(v.toFB(), GLES20.GL_TRIANGLES, 6,
                0f, 0f, 0f, 1f, fog, radArc = true, arcAngle = segCenterDeg))
        }

        // The doorway is cut straight out of the drum above (and filled with the
        // black door panel) — there is no separate door model on this building.
    }

    // ── The monitors on the desk ────────────────────────────────────────────────
    // Finds the model's screen panels (SCREEN_MTL), maps each one into world space
    // with the same transform the rest of the button gets, and hands them to the
    // CCTV rig as a 2x2 grid of live feeds each. Nothing here is hard-coded to a
    // position: move the desk in Blender and the screens move with it. If the model
    // stops having those panels, [CityCctv] simply has nothing to draw.
    private fun buildCctvScreens(
        mute: List<ObjGroup>, cxL: Float, czL: Float, mnY: Float,
        scl: Float, bx: Float, baseY: Float, bz: Float,
    ) {
        val monitors = mutableListOf<CctvMonitor>()
        var cell = 0
        for (g in mute) {
            if (g.materialName != SCREEN_MTL) continue
            if (cell + 4 > CityCctv.CELLS) break
            val w = FloatArray(g.verts.size)
            var j = 0
            while (j + 2 < w.size) {
                w[j]     = (g.verts[j]     - cxL) * scl + bx
                w[j + 1] = (g.verts[j + 1] - mnY) * scl + baseY
                w[j + 2] = (g.verts[j + 2] - czL) * scl + bz
                j += 3
            }
            // A monitor faces whoever is standing in the room, i.e. away from the
            // wall it's backed against and toward the drum's axis.
            var sx = 0f; var sz = 0f; var n = 0
            j = 0
            while (j + 2 < w.size) { sx += w[j]; sz += w[j + 2]; n++; j += 3 }
            sx /= n; sz /= n
            var ix = bx - sx; var iz = bz - sz
            val il = hypot(ix, iz)
            if (il < 1e-3f) { ix = 0f; iz = 1f } else { ix /= il; iz /= il }

            val m = CityCctv.frontQuad(w, ix, iz, cell, 2, 2, 0.6f) ?: continue
            monitors.add(m)
            cell += m.cellCount
        }
        cctv.setMonitors(monitors)
    }

    // ── Bridge — the player's custom pieces (bridge1..9), one per completed
    // building, laid south→north across the lava. Piece 1 is the south approach,
    // piece 9 the north approach. Lamp bulbs (object "Icosphere") glow at night.
    private fun addBridge(pieces: Int) {
        if (bridgeGroups.size < 9) return
        val modelLen = 6.2f                        // approx local Z length of a piece
        val pieceLen = abs(LAVA_N - LAVA_S) / 9f   // ~40 world units per slot
        val lenScale = pieceLen / modelLen         // Z scale spans the lava
        val girth = lenScale * 1.55f               // wider + taller so the bridge reads bigger
        val yBase = LAVA_Y                         // model y=0 sits on the lava surface

        for (i in 0 until pieces.coerceAtMost(9)) {
            val groups = bridgeGroups[i]
            if (groups.isEmpty()) continue
            // Local centre (X,Z) of the whole piece → drop it in the middle of its slot.
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
            for (g in groups) { var k = 0; while (k < g.verts.size) {
                val x = g.verts[k]; val z = g.verts[k + 2]
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (z < minZ) minZ = z; if (z > maxZ) maxZ = z; k += 3 } }
            val cxLocal = (minX + maxX) * 0.5f
            val czLocal = (minZ + maxZ) * 0.5f
            val zSlot = LAVA_S - (i + 0.5f) * pieceLen

            // Each piece is its own collapsible thing, with its own footprint: when
            // the city goes, the bridge comes apart at the joints the player built it
            // at — nine planks, each picking its own moment, some tipping off their
            // end and some just dropping into the lava.
            collapsible(0f, zSlot, (maxX - minX) * girth * 0.5f, pieceLen * 0.5f) {
                for (g in groups) {
                    val src = g.verts
                    val out = FloatArray(src.size)
                    var k = 0
                    while (k < src.size) {
                        out[k]     = (src[k]     - cxLocal) * girth
                        out[k + 1] = src[k + 1] * girth + yBase
                        out[k + 2] = (src[k + 2] - czLocal) * lenScale + zSlot
                        k += 3
                    }
                    if (out.isEmpty()) continue
                    val lamp = g.materialName == "Icosphere"        // the lamp bulb
                    val post = g.materialName.startsWith("Cylinder") // lamp post
                    val r: Float; val gg: Float; val b: Float
                    when {
                        lamp -> { r = 1f; gg = 0.84f; b = 0.4f }     // warm bulb (glows at night)
                        post -> { r = 0.32f; gg = 0.30f; b = 0.28f }  // dark metal post
                        else -> { r = 0.62f; gg = 0.55f; b = 0.46f }  // stone/wood deck
                    }
                    meshes.add(Mesh(out.toFB(), GLES20.GL_TRIANGLES, out.size / 3,
                        r, gg, b, 1f, 0.04f, glow = lamp, noAO = lamp))
                }
            }
        }
    }

    // ── Atmospheric lighting (ground-mode only) ───────────────────────────────
    // Soft contact shadows, lit windows, streetlamps + halos. All meshes here
    // are tagged aerialSkip=true so the aerial intro / completion fly-over
    // remains the clean diagrammatic look. Glow elements fade in with
    // darknessLevel so the city slowly turns dusk-lit as buildings complete.

    private fun addAtmosphere(
        bldgs: Array<FloatArray>, funcs: Array<FloatArray>,
        damaged: Array<FloatArray>, ops: Array<FloatArray>,
        lampCols: List<Float>, lampRows: List<Float>
    ) {
        // Per-building lamps are anchored to each digit building's sidewalk
        // corner by addBuilding → addBuildingLamp; no intersection grid here.
    }

    // One lamp post anchored to a specific sidewalk corner of a digit
    // building. cornerNW = true → NW corner, false → SE corner.
    //
    // Lamp model Y geometry: 32 verts at y=-1 (invisible pedestal cap), 32 at
    // y=+1 (where the visible post actually starts), then sparse verts up to
    // y=+5.42. Anchoring y=-1 to the sidewalk top makes the post float ~44
    // world units above the slab; anchoring y=+1 instead lands the visible
    // post bottom exactly on the slab. The hidden -1..+1 portion sinks into
    // the sidewalk volume / ground, where the sidewalk slab and ground quad
    // occlude it.
    //
    // No halo rings — they're drawn at world y=0.5, the SAME plane as the
    // sidewalk's bottom face, and produced a z-fight along every corner.
    // Lamp glow still fades in with darknessLevel via the lamp head's own
    // glow flag.
    private fun addBuildingLamp(cx: Float, cz: Float, effH: Float, door: Int, cornerNW: Boolean) {
        if (lampOffGroups.isEmpty() && lampOnGroups.isEmpty()) return
        val swXHalf = if (door == 0 || door == 1) BW * (5.74f / 4.078f) else BD * (5.74f / 3.063f)
        val swZHalf = if (door == 0 || door == 1) BD * (5.74f / 3.063f) else BW * (5.74f / 4.078f)
        val inset = 8f
        val px = if (cornerNW) cx - swXHalf + inset else cx + swXHalf - inset
        val pz = if (cornerNW) cz - swZHalf + inset else cz + swZHalf - inset
        val yawDeg = if (cornerNW) 225f else 45f   // arm aims out into the street corner
        val sY = effH / (MB_Y_TOP - MB_Y_BOTTOM)
        val sidewalkTopY = (MB_SIDEWALK_Y_LIMIT - MB_Y_BOTTOM) * sY + 0.5f
        // Model y=+1 (visible post bottom) → world sidewalkTopY ⇒ baseY = sidewalkTopY - LAMP_SCALE.
        val baseY = sidewalkTopY - LAMP_Y_OFFSET
        val s = LAMP_SCALE
        val yawRad = Math.toRadians(yawDeg.toDouble()).toFloat()
        for (g in lampOffGroups) {
            addInstancedGroup(g, px, baseY, pz, s, yaw = yawRad, aerialSkip = true, noAO = true)
        }
        for (g in lampOnGroups) {
            if (g.materialName == "Material.002") {
                addInstancedGroup(g, px, baseY, pz, s, yaw = yawRad,
                    aerialSkip = true, glow = true, alpha = 1f,
                    glowColor = floatArrayOf(1.0f, 0.86f, 0.42f), lamp = true)
                registerLampLight(g, px, baseY, pz, s, yawRad)
            }
        }
        // Warm pool of light on the street under each building-corner lamp.
        addHaloRing(px, pz, 1f,  30f, 1.00f, 0.78f, 0.34f, 0.75f)
        addHaloRing(px, pz, 30f, 64f, 1.00f, 0.78f, 0.34f, 0.42f)
    }

    // Place the Blender-authored lamp at each position. The "off" model is
    // drawn fully; the "on" model contributes only its bright bulb material,
    // added additively in pass 2 so it fades in with darknessLevel.
    private fun addLampInstancesAt(positions: List<FloatArray>) {
        if (lampOffGroups.isEmpty() && lampOnGroups.isEmpty()) {
            // Fallback: keep the original procedural lamps if assets failed to load.
            addLampPostsAt(positions)
            addLampGlowsAt(positions)
            return
        }
        val s = LAMP_SCALE
        val yOff = LAMP_Y_OFFSET
        val yaw = Math.toRadians(LAMP_YAW_DEG.toDouble()).toFloat()
        for (lamp in positions) {
            val px = lamp[0]; val pz = lamp[1]
            for (g in lampOffGroups) {
                addInstancedGroup(g, px, yOff, pz, s, yaw = yaw,
                    aerialSkip = true, noAO = true)
            }
            for (g in lampOnGroups) {
                // Only the bright bulb material glows. Pole/body of lampon is identical
                // to lampoff so we skip it to avoid double-drawing. Forced to a warm
                // bright colour + full alpha so the bulb clearly reads as "on".
                if (g.materialName == "Material.002") {
                    addInstancedGroup(g, px, yOff, pz, s, yaw = yaw,
                        aerialSkip = true, glow = true, alpha = 1f,
                        glowColor = floatArrayOf(1.0f, 0.86f, 0.42f), lamp = true)
                    registerLampLight(g, px, yOff, pz, s, yaw)
                }
            }
            // Warm ground halo — fades in with darkness via glow flag. Boosted so
            // each lamp throws a visible pool of light onto the street at night.
            addHaloRing(px, pz, 1f,  30f, 1.00f, 0.78f, 0.34f, 0.75f)
            addHaloRing(px, pz, 30f, 64f, 1.00f, 0.78f, 0.34f, 0.42f)
        }
    }

    // Register a point light at a lamp's bulb. The bulb group's centroid is put
    // through exactly the same transform addInstancedGroup uses, so the light sits
    // where the glowing bulb is actually drawn.
    private fun registerLampLight(g: ObjGroup, tx: Float, ty: Float, tz: Float, s: Float, yaw: Float) {
        val src = g.verts
        if (src.isEmpty()) return
        var mx = 0f; var my = 0f; var mz = 0f; var n = 0
        var i = 0
        while (i + 2 < src.size) { mx += src[i]; my += src[i+1]; mz += src[i+2]; n++; i += 3 }
        if (n == 0) return
        mx /= n; my /= n; mz /= n
        val xs = mx * s; val ys = my * s; val zs = mz * s
        val cy = cos(yaw); val sy = sin(yaw)
        lampLights.add(floatArrayOf(
            xs * cy + zs * sy + tx,
            ys + ty,
            -xs * sy + zs * cy + tz,
            LAMP_LIGHT_RADIUS,
            -1f))                       // a lamp: always lit
    }

    // One point light per building, sitting at the centre of its window panes, so
    // an unexplored building actually throws its warm light onto the street. The
    // light is tagged with the digit and is dropped once that building is done —
    // matching the windows going black in the shader.
    private fun collectWindowLights() {
        val sums = HashMap<Int, FloatArray>()   // digit -> [x, y, z, count]
        for (m in meshes) {
            val d = m.windowDigit
            if (d !in 1..9) continue
            val acc = sums.getOrPut(d) { floatArrayOf(0f, 0f, 0f, 0f) }
            val buf = m.buf
            buf.position(0)
            var i = 0
            while (i + 2 < m.cnt * 3) {
                acc[0] += buf.get(i); acc[1] += buf.get(i + 1); acc[2] += buf.get(i + 2)
                acc[3] += 1f
                i += 3
            }
            buf.position(0)
        }
        for ((digit, acc) in sums) {
            if (acc[3] < 1f) continue
            lampLights.add(floatArrayOf(
                acc[0] / acc[3], acc[1] / acc[3], acc[2] / acc[3],
                WINDOW_LIGHT_RADIUS,
                digit.toFloat()))
        }
    }

    // Append one transformed copy of an ObjGroup to the static meshes list.
    // Applies scale, then a yaw around the model's local +Y axis, then translates.
    private fun addInstancedGroup(
        g: ObjGroup, tx: Float, ty: Float, tz: Float, s: Float,
        yaw: Float = 0f,
        aerialSkip: Boolean = false, glow: Boolean = false, alpha: Float = 1f,
        noAO: Boolean = false, glowColor: FloatArray? = null, lamp: Boolean = false
    ) {
        val src = g.verts
        val out = FloatArray(src.size)
        val cy = cos(yaw); val sy = sin(yaw)
        var i = 0
        while (i < src.size) {
            val xs = src[i]     * s
            val ys = src[i + 1] * s
            val zs = src[i + 2] * s
            out[i]     =  xs * cy + zs * sy + tx
            out[i + 1] =  ys + ty
            out[i + 2] = -xs * sy + zs * cy + tz
            i += 3
        }
        val cr = glowColor?.getOrNull(0) ?: g.r
        val cg = glowColor?.getOrNull(1) ?: g.g
        val cb = glowColor?.getOrNull(2) ?: g.b
        meshes.add(Mesh(out.toFB(), GLES20.GL_TRIANGLES, out.size / 3,
            cr, cg, cb, a = alpha, fog = if (aerialSkip) 0f else 0.05f,
            aerialSkip = aerialSkip, glow = glow, noAO = noAO, lamp = lamp))
    }

    private fun addLampPostsAt(positions: List<FloatArray>) {
        val pH = 120f       // taller pole
        val pW = 2.0f       // pole half-width
        val armLen = 26f    // horizontal arm length
        val armW = 1.4f     // arm half-width (perpendicular)
        val armT = 2.2f     // arm thickness (vertical half-extent)
        val droop = 4f      // downward droop at arm end
        val cR = 0.16f; val cG = 0.16f; val cB = 0.18f

        for (lamp in positions) {
            val px = lamp[0]; val pz = lamp[1]; val ad = lamp[2]

            // Vertical pole — 4 side faces
            addQ(px-pW, 0f, pz+pW,  px+pW, 0f, pz+pW,
                 px+pW, pH, pz+pW,  px-pW, pH, pz+pW,
                 cR, cG, cB, fog = 0.05f, aerialSkip = true)
            addQ(px+pW, 0f, pz-pW,  px-pW, 0f, pz-pW,
                 px-pW, pH, pz-pW,  px+pW, pH, pz-pW,
                 cR*0.65f, cG*0.65f, cB*0.65f, fog = 0.05f, aerialSkip = true)
            addQ(px+pW, 0f, pz+pW,  px+pW, 0f, pz-pW,
                 px+pW, pH, pz-pW,  px+pW, pH, pz+pW,
                 cR*0.85f, cG*0.85f, cB*0.85f, fog = 0.05f, aerialSkip = true)
            addQ(px-pW, 0f, pz-pW,  px-pW, 0f, pz+pW,
                 px-pW, pH, pz+pW,  px-pW, pH, pz-pW,
                 cR*0.80f, cG*0.80f, cB*0.80f, fog = 0.05f, aerialSkip = true)

            // Bent arm — horizontal box from pole top in direction ad, drooping at the end
            val ax = cos(ad.toDouble()).toFloat()
            val az = sin(ad.toDouble()).toFloat()
            val nx = -az;  val nz = ax           // perpendicular in XZ plane
            val pxw = nx * armW; val pzw = nz * armW
            val a0x = px;                val a0z = pz;                val a0y = pH
            val a1x = px + armLen * ax;  val a1z = pz + armLen * az;  val a1y = pH - droop

            // Top
            addQ(a0x - pxw, a0y + armT, a0z - pzw,
                 a0x + pxw, a0y + armT, a0z + pzw,
                 a1x + pxw, a1y + armT, a1z + pzw,
                 a1x - pxw, a1y + armT, a1z - pzw,
                 cR*1.10f, cG*1.10f, cB*1.10f, fog = 0.05f, aerialSkip = true)
            // Bottom
            addQ(a0x - pxw, a0y - armT, a0z - pzw,
                 a1x - pxw, a1y - armT, a1z - pzw,
                 a1x + pxw, a1y - armT, a1z + pzw,
                 a0x + pxw, a0y - armT, a0z + pzw,
                 cR*0.55f, cG*0.55f, cB*0.55f, fog = 0.05f, aerialSkip = true)
            // Side +n
            addQ(a0x + pxw, a0y + armT, a0z + pzw,
                 a0x + pxw, a0y - armT, a0z + pzw,
                 a1x + pxw, a1y - armT, a1z + pzw,
                 a1x + pxw, a1y + armT, a1z + pzw,
                 cR*0.85f, cG*0.85f, cB*0.85f, fog = 0.05f, aerialSkip = true)
            // Side -n
            addQ(a0x - pxw, a0y + armT, a0z - pzw,
                 a1x - pxw, a1y + armT, a1z - pzw,
                 a1x - pxw, a1y - armT, a1z - pzw,
                 a0x - pxw, a0y - armT, a0z - pzw,
                 cR*0.85f, cG*0.85f, cB*0.85f, fog = 0.05f, aerialSkip = true)
            // End cap
            addQ(a1x - pxw, a1y + armT, a1z - pzw,
                 a1x + pxw, a1y + armT, a1z + pzw,
                 a1x + pxw, a1y - armT, a1z + pzw,
                 a1x - pxw, a1y - armT, a1z - pzw,
                 cR*0.90f, cG*0.90f, cB*0.90f, fog = 0.05f, aerialSkip = true)
        }
    }

    private fun addLampGlowsAt(positions: List<FloatArray>) {
        val pH = 120f
        val armLen = 26f
        val droop = 4f
        val cW = 5.5f; val cH = 5f
        val capR = 1.00f; val capG = 0.82f; val capB = 0.38f
        val haloR = 1.00f; val haloG = 0.74f; val haloB = 0.28f

        for (lamp in positions) {
            val px = lamp[0]; val pz = lamp[1]; val ad = lamp[2]
            val ax = cos(ad.toDouble()).toFloat()
            val az = sin(ad.toDouble()).toFloat()
            val cx = px + armLen * ax
            val cz = pz + armLen * az
            // Cap hangs just below the drooped arm end
            val y0 = pH - droop - cH - 0.5f
            val y1 = y0 + cH

            addQ(cx-cW, y0, cz+cW, cx+cW, y0, cz+cW, cx+cW, y1, cz+cW, cx-cW, y1, cz+cW,
                 capR, capG, capB, a = 0.90f, fog = 0f, aerialSkip = true, glow = true)
            addQ(cx+cW, y0, cz-cW, cx-cW, y0, cz-cW, cx-cW, y1, cz-cW, cx+cW, y1, cz-cW,
                 capR, capG, capB, a = 0.90f, fog = 0f, aerialSkip = true, glow = true)
            addQ(cx+cW, y0, cz+cW, cx+cW, y0, cz-cW, cx+cW, y1, cz-cW, cx+cW, y1, cz+cW,
                 capR, capG, capB, a = 0.90f, fog = 0f, aerialSkip = true, glow = true)
            addQ(cx-cW, y0, cz-cW, cx-cW, y0, cz+cW, cx-cW, y1, cz+cW, cx-cW, y1, cz-cW,
                 capR, capG, capB, a = 0.90f, fog = 0f, aerialSkip = true, glow = true)
            addQ(cx-cW, y0, cz-cW, cx+cW, y0, cz-cW, cx+cW, y0, cz+cW, cx-cW, y0, cz+cW,
                 capR, capG, capB, a = 0.90f, fog = 0f, aerialSkip = true, glow = true)

            // Halo on ground beneath the cap (offset under the arm end, not the pole)
            addHaloRing(cx, cz, 1f,  28f, haloR, haloG, haloB, 0.45f)
            addHaloRing(cx, cz, 28f, 58f, haloR, haloG, haloB, 0.20f)
        }
    }

    private fun addHaloRing(cx: Float, cz: Float, rIn: Float, rOut: Float,
                             r: Float, g: Float, b: Float, alpha: Float) {
        val segs = 16
        val y = 0.5f
        val pi = PI.toFloat()
        for (i in 0 until segs) {
            val a0 = i.toFloat() / segs * 2f * pi
            val a1 = (i + 1).toFloat() / segs * 2f * pi
            val xi0 = cx + cos(a0).toFloat() * rIn;  val zi0 = cz + sin(a0).toFloat() * rIn
            val xi1 = cx + cos(a1).toFloat() * rIn;  val zi1 = cz + sin(a1).toFloat() * rIn
            val xo0 = cx + cos(a0).toFloat() * rOut; val zo0 = cz + sin(a0).toFloat() * rOut
            val xo1 = cx + cos(a1).toFloat() * rOut; val zo1 = cz + sin(a1).toFloat() * rOut
            addQ(xi0, y, zi0, xi1, y, zi1, xo1, y, zo1, xo0, y, zo0,
                 r, g, b, a = alpha, fog = 0f, aerialSkip = true, glow = true, lamp = true)
        }
    }

    private fun addBuildingWindowsAt(cx: Float, cz: Float, h: Float, seed: Long) {
        val cols = 3
        val rows = (h / 60f).toInt().coerceIn(3, 6)
        val ww = 5.5f
        val wh = 7.5f
        val cellY = (h - 50f) / rows.toFloat()
        val cellX = (2f * BW) / (cols + 1).toFloat()
        val cellZ = (2f * BD) / (cols + 1).toFloat()
        val eps = 0.6f
        val rnd = java.util.Random(seed)
        val warmR = 1.00f; val warmG = 0.84f; val warmB = 0.36f

        fun winQ(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float,
                 x2: Float, y2: Float, z2: Float, x3: Float, y3: Float, z3: Float) {
            // Per-window variety: some rooms dimmer, some brighter
            val a = 0.55f + rnd.nextFloat() * 0.35f
            addQ(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3,
                 warmR, warmG, warmB, a = a, fog = 0f, aerialSkip = true, glow = true)
        }

        // South face (z = cz+BD)
        for (c in 1..cols) for (r in 0 until rows) {
            if (rnd.nextFloat() > 0.62f) continue
            val wx = cx - BW + cellX * c
            val wy = 30f + cellY * (r + 0.5f)
            winQ(wx-ww, wy-wh, cz+BD+eps, wx+ww, wy-wh, cz+BD+eps,
                 wx+ww, wy+wh, cz+BD+eps, wx-ww, wy+wh, cz+BD+eps)
        }
        // North face (z = cz-BD)
        for (c in 1..cols) for (r in 0 until rows) {
            if (rnd.nextFloat() > 0.62f) continue
            val wx = cx - BW + cellX * c
            val wy = 30f + cellY * (r + 0.5f)
            winQ(wx+ww, wy-wh, cz-BD-eps, wx-ww, wy-wh, cz-BD-eps,
                 wx-ww, wy+wh, cz-BD-eps, wx+ww, wy+wh, cz-BD-eps)
        }
        // East face (x = cx+BW)
        for (c in 1..cols) for (r in 0 until rows) {
            if (rnd.nextFloat() > 0.62f) continue
            val wz = cz - BD + cellZ * c
            val wy = 30f + cellY * (r + 0.5f)
            winQ(cx+BW+eps, wy-wh, wz+ww, cx+BW+eps, wy-wh, wz-ww,
                 cx+BW+eps, wy+wh, wz-ww, cx+BW+eps, wy+wh, wz+ww)
        }
        // West face (x = cx-BW)
        for (c in 1..cols) for (r in 0 until rows) {
            if (rnd.nextFloat() > 0.62f) continue
            val wz = cz - BD + cellZ * c
            val wy = 30f + cellY * (r + 0.5f)
            winQ(cx-BW-eps, wy-wh, wz-ww, cx-BW-eps, wy-wh, wz+ww,
                 cx-BW-eps, wy+wh, wz+ww, cx-BW-eps, wy+wh, wz-ww)
        }
    }

    // ── Security cameras ──────────────────────────────────────────────────────
    // Mounted at each building corner at y=140 (just above lamp tops at y=120).
    // Drawn dynamically per-frame so the camera body yaws to track the player;
    // rotation is clamped to ±90° from the corner's outward diagonal so the
    // body never sweeps back through the building wall.

    private fun addCameras(
        bldgs: Array<FloatArray>, funcs: Array<FloatArray>,
        damaged: Array<FloatArray>, ops: Array<FloatArray>
    ) {
        val y = 170f
        val off = 10f
        val invR = 1f / sqrt(2f)
        // Fixed seed so the random subset of corners is the same on every run.
        val rng = java.util.Random(20260530L)
        val keepProb = 0.30f

        fun cornersFor(cx: Float, cz: Float) {
            val corners = arrayOf(
                floatArrayOf(cx + BW, cz - BD,  invR, -invR),  // NE
                floatArrayOf(cx + BW, cz + BD,  invR,  invR),  // SE
                floatArrayOf(cx - BW, cz + BD, -invR,  invR),  // SW
                floatArrayOf(cx - BW, cz - BD, -invR, -invR)   // NW
            )
            for (c in corners) {
                if (rng.nextFloat() > keepProb) continue
                val px = c[0] + c[2] * off
                val pz = c[1] + c[3] * off
                cameraMounts.add(floatArrayOf(px, y, pz, c[2], c[3]))
            }
        }

        for (b in bldgs)   cornersFor(b[1], b[2])
        for (f in funcs)   cornersFor(f[0], f[1])
        for (d in damaged) cornersFor(d[0], d[1])
        for (o in ops)     cornersFor(o[0], o[1])
    }

    // Tunables for the Blender camera model — adjust empirically after first build.
    // Model is assumed Y-up with default forward = -Z (Blender OBJ default).
    // If the camera ends up pointing the wrong direction after the first run,
    // bump CAMERA_MODEL_YAW_OFFSET_DEG by 90/180/-90 to spin it around.
    private val CAMERA_MODEL_YAW_OFFSET_DEG = 0f
    private val PLAYER_LOOK_Y = 40f  // matches the player orb center (drawOrb uses 32+8)

    // The night-mode monster. Drawn in world space each frame (it roams + spins),
    // exactly like the cameras: scale → yaw about +Y → translate, then upload.
    // Measure which way the model's face points in the horizontal plane: the
    // direction from the body centroid to the bright face-material centroid.
    // We then store the yaw offset that makes the model render +Z aligned with
    // a travel heading equal that face direction (so it always travels face-first).
    private fun computeMonsterFaceHeading() {
        var bodyX = 0f; var bodyZ = 0f; var bodyN = 0
        var faceX = 0f; var faceY = 0f; var faceZ = 0f; var faceN = 0
        for (g in monsterGroups) {
            val isFace = maxOf(g.r, g.g, g.b) > 0.25f
            var i = 0
            while (i < g.verts.size) {
                val x = g.verts[i]; val y = g.verts[i + 1]; val z = g.verts[i + 2]
                bodyX += x; bodyZ += z; bodyN++
                if (isFace) { faceX += x; faceY += y; faceZ += z; faceN++ }
                i += 3
            }
        }
        if (faceN == 0 || bodyN == 0) { monsterFaceYawOffsetDeg = 0f; monsterFaceModelY = 0f; return }
        monsterFaceModelY = faceY / faceN
        val dx = faceX / faceN - bodyX / bodyN
        val dz = faceZ / faceN - bodyZ / bodyN
        if (dx * dx + dz * dz < 1e-6f) { monsterFaceYawOffsetDeg = 0f; return }
        // Model heading of the face, then the yaw offset that cancels it.
        val faceHeading = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()
        monsterFaceYawOffsetDeg = -faceHeading
    }

    private fun drawMonster() {
        // Nothing to draw — make sure stale face-glow verts from a previous frame
        // don't linger in PASS 2 (otherwise a vanished monster leaves its face behind).
        if (aerialMode || monsterGroups.isEmpty() || (!monsterActive && monsterClones.isEmpty())) {
            if (monsterFaceArrs.isNotEmpty()) monsterFaceArrs = emptyList()
            return
        }
        GLES20.glUniform1f(uFog, 0f)
        GLES20.glUniform1f(uAerial, 1f)   // self-lit so it reads in the night dark
        val faces = mutableListOf<Pair<FloatArray, FloatArray>>()
        if (monsterActive) drawMonsterInstance(monsterX, monsterZ, monsterAngle, monsterScale,
            monsterTilt, monsterTiltX, monsterTiltZ, faces)
        for (c in monsterClones) drawMonsterInstance(c[0], c[1], c[2], c[3], 0f, 0f, 1f, faces)
        monsterFaceArrs = faces
    }

    // Draws one monster body (main or clone) at a world transform, appending its
    // bright face materials to [faces] for the additive PASS-2 illumination.
    // A non-zero tilt topples the body over its feet toward (tdx, tdz).
    private fun drawMonsterInstance(
        ox: Float, oz: Float, angleDeg: Float, s: Float,
        tilt: Float, tdx: Float, tdz: Float,
        faces: MutableList<Pair<FloatArray, FloatArray>>
    ) {
        setLit(1f)   // the monster's body takes the sun and the lamps it passes
        val yaw = Math.toRadians(angleDeg.toDouble()).toFloat()
        val cy = cos(yaw); val sy = sin(yaw)
        val oy = 4f * s
        // Tilt is a rotation about the horizontal axis k = (tdz, 0, -tdx) through
        // the feet (model y = -4·s), so the body falls toward (tdx, tdz) face-first.
        val hasTilt = tilt > 0.01f
        val tr = Math.toRadians(tilt.toDouble()).toFloat()
        val ct = cos(tr); val st = sin(tr)
        val kx = tdz; val kz = -tdx
        val fy = -4f * s
        for (g in monsterGroups) {
            val src = g.verts
            if (src.isEmpty()) continue
            val arr = FloatArray(src.size)
            var i = 0
            while (i < src.size) {
                val xs = src[i] * s; val ys = src[i + 1] * s; val zs = src[i + 2] * s
                // Yaw about +Y (offset from the model centre).
                var rx =  xs * cy + zs * sy
                var ry =  ys
                var rz = -xs * sy + zs * cy
                if (hasTilt) {
                    // Rodrigues rotation about unit axis k through the feet.
                    val vx = rx; val vy = ry - fy; val vz = rz
                    val dotKV = kx * vx + kz * vz
                    val crx = -kz * vy
                    val cry =  kz * vx - kx * vz
                    val crz =  kx * vy
                    rx = vx * ct + crx * st + kx * dotKV * (1f - ct)
                    ry = (vy * ct + cry * st) + fy
                    rz = vz * ct + crz * st + kz * dotKV * (1f - ct)
                }
                arr[i]     = rx + ox
                arr[i + 1] = ry + oy
                arr[i + 2] = rz + oz
                i += 3
            }
            val fb = arr.toFB()
            // Body is authored pure black — lift it just enough that it reads as
            // a dark shape. It's drawn before the overlay, so the night dims it
            // down to a shadow rather than a bright cut-out.
            GLES20.glUniform4f(uCol, g.r.coerceAtLeast(0.40f), g.g.coerceAtLeast(0.40f), g.b.coerceAtLeast(0.40f), 1f)
            fb.position(0)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, fb)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, src.size / 3)
            // The bright face materials (red / white) are captured so PASS 2 can
            // re-draw them additively — illuminating the faces through the night
            // while the dark body stays dimmed.
            if (maxOf(g.r, g.g, g.b) > 0.25f) faces.add(arr to floatArrayOf(g.r, g.g, g.b))
        }
    }

    // Additive pass that illuminates the monster's red/white faces through the
    // night overlay (drawn in PASS 2, after the MVP is restored).
    private fun drawMonsterFaces(dkLvl: Float) {
        setLit(0f)   // the face glows in the dark
        if (aerialMode || monsterFaceArrs.isEmpty()) return
        GLES20.glUniform1f(uFog, 0f)
        val k = (0.6f * dkLvl).coerceIn(0f, 1f)
        for ((arr, col) in monsterFaceArrs) {
            if (arr.isEmpty()) continue
            val fb = arr.toFB()
            GLES20.glUniform4f(uCol, col[0], col[1], col[2], k)
            fb.position(0)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, fb)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, arr.size / 3)
        }
    }

    private fun drawCameras() {
        if (aerialMode || cameraMounts.isEmpty()) return
        if (cameraOnGroups.isNotEmpty()) {
            drawCamerasModel()
        } else {
            drawCamerasProcedural()
        }
    }

    private fun drawCamerasModel() {
        setLit(1f)
        val s = CAMERA_SCALE
        val tx = playerX; val tz = playerZ
        val ty = PLAYER_LOOK_Y
        val offsetYaw = Math.toRadians(CAMERA_MODEL_YAW_OFFSET_DEG.toDouble()).toFloat()

        // A camera is bolted to a building corner, so when that building goes over,
        // the camera goes with it — otherwise the city falls and leaves a grid of
        // cameras hanging in the air, still watching.
        val col = collapse.coerceIn(0f, 1f)
        val xf = FloatArray(10)
        val pt = FloatArray(3)

        // One accumulator per material group — collapses N mounts × M materials
        // into M draw calls per frame instead of N*M.
        val perGroup = Array(cameraOnGroups.size) { mutableListOf<Float>() }

        for (mount in cameraMounts) {
            val mx = mount[0]; val my = mount[1]; val mz = mount[2]
            val outX = mount[3]; val outZ = mount[4]

            var falls = false
            if (col > 0f) {
                val g = collapseGroupNear(mx, mz)
                if (g != null) {
                    if (!collapseXform(g, col, xf)) continue   // its building is gone, and so is it
                    falls = true
                }
            }

            val tdx = tx - mx; val tdz = tz - mz
            val tdy = ty - my
            val hLen = sqrt(tdx * tdx + tdz * tdz)
            val hSafe = if (hLen < 0.001f) 1f else hLen
            var fx = tdx / hSafe
            var fz = tdz / hSafe

            // Same ±90° clamp as before — keeps camera body off the wall.
            val dot = fx * outX + fz * outZ
            if (dot < 0f) {
                val cross = outX * fz - outZ * fx
                if (cross >= 0f) { fx = -outZ; fz =  outX }
                else             { fx =  outZ; fz = -outX }
            }

            // Yaw: rotate around +Y so model's default forward (0,0,-1) aligns with (fx, fz).
            val yaw = atan2(-fx, -fz) + offsetYaw
            // Pitch: rotate around model's local X axis so forward also tilts vertically
            // toward the player. Negative pitch tilts the lens down (player below camera).
            // atan2(tdy, hLen) is positive when player is above, negative when below.
            val pitch = atan2(tdy, hLen.coerceAtLeast(0.001f))
            val cy = cos(yaw);   val sy = sin(yaw)
            val cp = cos(pitch); val sp = sin(pitch)

            for (gi in cameraOnGroups.indices) {
                val src = cameraOnGroups[gi].verts
                val dst = perGroup[gi]
                var i = 0
                while (i < src.size) {
                    val xs = src[i]     * s
                    val ys = src[i + 1] * s
                    val zs = src[i + 2] * s
                    // 1) Pitch around X (model space): tilts forward vector vertically.
                    val px = xs
                    val py = ys * cp - zs * sp
                    val pz = ys * sp + zs * cp
                    // 2) Yaw around Y: aligns horizontal forward with player direction.
                    val xr =  px * cy + pz * sy
                    val zr = -px * sy + pz * cy
                    // 3) …and, if its building is on its way down, the fall.
                    if (falls) {
                        applyXform(xf, xr + mx, py + my, zr + mz, pt)
                        dst.add(pt[0]); dst.add(pt[1]); dst.add(pt[2])
                    } else {
                        dst.add(xr + mx)
                        dst.add(py + my)
                        dst.add(zr + mz)
                    }
                    i += 3
                }
            }
        }

        GLES20.glUniform1f(uFog, 0f)
        GLES20.glUniform1f(uAerial, 1f)  // self-lit / metallic — skip the ground AO darkening
        val dk = darknessLevel.coerceIn(0f, 1f)
        val lights = mutableListOf<FloatArray>()
        for (gi in cameraOnGroups.indices) {
            val arr = perGroup[gi].toFloatArray()
            if (arr.isEmpty()) continue
            val fb = arr.toFB()
            val g = cameraOnGroups[gi]
            // Cross-fade to the night Kd for the same material, if cameranight provides one.
            val night = cameraNightColors[g.materialName]
            val r: Float; val gC: Float; val b: Float
            if (night != null) {
                r  = g.r + (night[0] - g.r) * dk
                gC = g.g + (night[1] - g.g) * dk
                b  = g.b + (night[2] - g.b) * dk
            } else { r = g.r; gC = g.g; b = g.b }
            GLES20.glUniform4f(uCol, r, gC, b, 1f)
            fb.position(0)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, fb)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, arr.size / 3)
            // Only the small recording light (Material.002, red at night) gets the
            // additive "defy the dark" pop. The larger lens (Material.003) stays
            // dimmed by the overlay, per design.
            if (g.materialName == "Material.002") {
                lights.add(arr)
            }
        }
        cameraLightArrs = lights
    }

    // Additive bright-red camera lights, drawn in PASS 2 after the MVP is restored.
    private fun drawCameraLights(dkLvl: Float) {
        setLit(0f)   // the lens dot is a light source
        if (aerialMode || cameraLightArrs.isEmpty()) return
        GLES20.glUniform1f(uFog, 0f)
        GLES20.glUniform4f(uCol, 1.0f, 0.06f, 0.07f, (0.95f * dkLvl).coerceIn(0f, 1f))
        for (arr in cameraLightArrs) {
            if (arr.isEmpty()) continue
            val fb = arr.toFB()
            fb.position(0)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, fb)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, arr.size / 3)
        }
    }

    private fun drawCamerasProcedural() {
        setLit(1f)
        val tx = playerX; val tz = playerZ
        val bodyVerts = mutableListOf<Float>()
        val lensVerts = mutableListOf<Float>()

        for (mount in cameraMounts) {
            val mx = mount[0]; val my = mount[1]; val mz = mount[2]
            val outX = mount[3]; val outZ = mount[4]

            val tdx = tx - mx; val tdz = tz - mz
            var len = sqrt(tdx * tdx + tdz * tdz)
            if (len < 0.001f) len = 1f
            var fx = tdx / len
            var fz = tdz / len

            val dot = fx * outX + fz * outZ
            if (dot < 0f) {
                val cross = outX * fz - outZ * fx
                if (cross >= 0f) { fx = -outZ; fz =  outX }
                else             { fx =  outZ; fz = -outX }
            }

            val sx = -fz; val sz = fx

            addCameraBox(bodyVerts, mx, my, mz, sx, sz, fx, fz, 5f, 4f, -1f, 11f)
            addCameraBox(lensVerts, mx, my, mz, sx, sz, fx, fz, 3f, 3f, 11f, 15f)
        }

        GLES20.glUniform1f(uFog, 0f)
        GLES20.glUniform1f(uAerial, aerialBlend)

        val bodyArr = bodyVerts.toFloatArray()
        val bodyFb  = bodyArr.toFB()
        GLES20.glUniform4f(uCol, 0.18f, 0.18f, 0.20f, 1f)
        bodyFb.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, bodyFb)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, bodyArr.size / 3)

        val lensArr = lensVerts.toFloatArray()
        val lensFb  = lensArr.toFB()
        GLES20.glUniform4f(uCol, 0.85f, 0.10f, 0.10f, 1f)
        lensFb.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, lensFb)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, lensArr.size / 3)
    }

    // ── Sliding doors ────────────────────────────────────────────────────────
    // Door model native bounds: X ±3.606, Y ±4.676, Z ±0.234.
    private val DOOR_HALF_W = 3.606f
    private val DOOR_HALF_H = 4.676f

    // Register a door instance to be drawn dynamically in drawDoors. doorCx/doorCz
    // is the center of the door's outward face; faceIdx ∈ {0=S, 1=N, 2=E, 3=W}
    // tells us which way the door faces in world space. dh = opening height.
    private fun registerDoor(label: String, doorCx: Float, doorCz: Float, faceIdx: Int, dw: Float, dh: Float) {
        val digit = label.toIntOrNull() ?: return
        if (digit !in 1..9) return
        // Non-uniform scale so the door fills the wall opening exactly.
        val sX = dw / DOOR_HALF_W                 // half-width → world half-width
        val sY = (dh * 0.5f) / DOOR_HALF_H        // half-height when fully closed
        val sZ = sX                               // keep depth proportional to width
        // mount layout: [cx, dh, cz, face, sX, sY_closed, sZ, _unused, digitIdx]
        doorMounts.add(floatArrayOf(doorCx, dh, doorCz, faceIdx.toFloat(), sX, sY, sZ, 0f, (digit - 1).toFloat()))
    }

    private fun drawDoors() {
        setLit(1f)
        if (aerialMode || doorMounts.isEmpty() || doorGroups.isEmpty()) return
        // Accumulate transformed verts per material group → one draw call per group.
        val perGroup = Array(doorGroups.size) { mutableListOf<Float>() }
        val openArr = doorOpenFraction
        // A door goes down with its building, for the same reason a camera does.
        val col = collapse.coerceIn(0f, 1f)
        val xf = FloatArray(10)
        val pt = FloatArray(3)
        for (mount in doorMounts) {
            val cx = mount[0]; val dh = mount[1]; val cz = mount[2]
            val face = mount[3].toInt()
            val sX = mount[4]; val sYClosed = mount[5]; val sZ = mount[6]
            val digitIdx = mount[8].toInt()

            var falls = false
            if (col > 0f) {
                val g = collapseGroupNear(cx, cz)
                if (g != null) {
                    if (!collapseXform(g, col, xf)) continue
                    falls = true
                }
            }
            val open = if (digitIdx in openArr.indices) openArr[digitIdx].coerceIn(0f, 1f) else 0f
            // Slide-up = the door's bottom rises while the top stays pinned to the
            // top of the opening. This retracts the panel cleanly into the wall
            // without it ever appearing above the opening (which would happen if we
            // simply translated upward, since the wall has no above-opening cutout).
            val sYEff = sYClosed * (1f - open)
            val centerY = dh * 0.5f * (1f + open)

            // Rotate model so its +Z (front) aligns with the wall's outward normal.
            // face: 0=S(+Z), 1=N(-Z), 2=E(+X), 3=W(-X).
            val (cosY, sinY) = when (face) {
                0    -> 1f to 0f
                1    -> -1f to 0f
                2    -> 0f to 1f
                else -> 0f to -1f
            }

            for (gi in doorGroups.indices) {
                val src = doorGroups[gi].verts
                val dst = perGroup[gi]
                var i = 0
                while (i < src.size) {
                    val xs = src[i]     * sX
                    val ys = src[i + 1] * sYEff
                    val zs = src[i + 2] * sZ
                    // Yaw around Y. New x = x*cosY + z*sinY ; new z = -x*sinY + z*cosY.
                    val xr =  xs * cosY + zs * sinY
                    val zr = -xs * sinY + zs * cosY
                    if (falls) {
                        applyXform(xf, xr + cx, ys + centerY, zr + cz, pt)
                        dst.add(pt[0]); dst.add(pt[1]); dst.add(pt[2])
                    } else {
                        dst.add(xr + cx)
                        dst.add(ys + centerY)
                        dst.add(zr + cz)
                    }
                    i += 3
                }
            }
        }

        GLES20.glUniform1f(uFog, 0f)
        GLES20.glUniform1f(uAerial, 1f)
        for (gi in doorGroups.indices) {
            val arr = perGroup[gi].toFloatArray()
            if (arr.isEmpty()) continue
            val fb = arr.toFB()
            val g = doorGroups[gi]
            // Doors are darker than their raw Kd in the MTL — the building exteriors
            // are already cream/beige so a near-black panel reads as a real door.
            val r = (g.r * 1.6f + 0.04f).coerceAtMost(0.30f)
            val gC = (g.g * 1.6f + 0.04f).coerceAtMost(0.30f)
            val b = (g.b * 1.6f + 0.04f).coerceAtMost(0.45f)
            GLES20.glUniform4f(uCol, r, gC, b, 1f)
            fb.position(0)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, fb)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, arr.size / 3)
        }
    }

    private fun addCameraBox(
        verts: MutableList<Float>,
        mx: Float, my: Float, mz: Float,
        sx: Float, sz: Float,
        fx: Float, fz: Float,
        hw: Float, hh: Float,
        z0: Float, z1: Float
    ) {
        fun p(lx: Float, ly: Float, lz: Float) = floatArrayOf(
            mx + lx * sx + lz * fx,
            my + ly,
            mz + lx * sz + lz * fz
        )
        val c000 = p(-hw, -hh, z0); val c100 = p( hw, -hh, z0)
        val c110 = p( hw,  hh, z0); val c010 = p(-hw,  hh, z0)
        val c001 = p(-hw, -hh, z1); val c101 = p( hw, -hh, z1)
        val c111 = p( hw,  hh, z1); val c011 = p(-hw,  hh, z1)

        fun q(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray) {
            verts.add(a[0]); verts.add(a[1]); verts.add(a[2])
            verts.add(b[0]); verts.add(b[1]); verts.add(b[2])
            verts.add(c[0]); verts.add(c[1]); verts.add(c[2])
            verts.add(a[0]); verts.add(a[1]); verts.add(a[2])
            verts.add(c[0]); verts.add(c[1]); verts.add(c[2])
            verts.add(d[0]); verts.add(d[1]); verts.add(d[2])
        }

        q(c001, c101, c111, c011)  // front
        q(c100, c000, c010, c110)  // back
        q(c011, c111, c110, c010)  // top
        q(c000, c100, c101, c001)  // bottom
        q(c101, c100, c110, c111)  // +side
        q(c000, c001, c011, c010)  // -side
    }

    private fun addTri(x0:Float,y0:Float,z0:Float, x1:Float,y1:Float,z1:Float,
                       x2:Float,y2:Float,z2:Float, r:Float,g:Float,b:Float, fog:Float=0.65f) {
        meshes.add(Mesh(floatArrayOf(x0,y0,z0,x1,y1,z1,x2,y2,z2).toFB(), GLES20.GL_TRIANGLES, 3, r,g,b,1f,fog))
    }
    private fun addQ(x0:Float,y0:Float,z0:Float, x1:Float,y1:Float,z1:Float,
                     x2:Float,y2:Float,z2:Float, x3:Float,y3:Float,z3:Float,
                     r:Float,g:Float,b:Float, a:Float=1f, fog:Float=0.65f,
                     aerialSkip: Boolean = false,
                     glow: Boolean = false,
                     softShadow: Boolean = false, lamp: Boolean = false) {
        val v = floatArrayOf(x0,y0,z0,x1,y1,z1,x2,y2,z2, x0,y0,z0,x2,y2,z2,x3,y3,z3)
        meshes.add(Mesh(v.toFB(), GLES20.GL_TRIANGLES, 6, r,g,b,a,fog,
            aerialSkip = aerialSkip, glow = glow, softShadow = softShadow, lamp = lamp))
    }
    private fun addL(x0:Float,y0:Float,z0:Float, x1:Float,y1:Float,z1:Float,
                     r:Float,g:Float,b:Float) {
        meshes.add(Mesh(floatArrayOf(x0,y0,z0,x1,y1,z1).toFB(), GLES20.GL_LINES, 2, r,g,b, fog=0.70f))
    }

    /**
     * Records the meshes a building adds, so the whole thing can be dropped as one
     * object when the city comes down. Each gets its own start time and its own
     * lean, from a hash of where it stands - so the collapse is ragged and
     * repeatable rather than random and synchronised.
     */
    // [hx]/[hz] are the thing's half-extents on the ground. They set the edge it
    // hinges on when it goes over, so they have to be its own size — a bridge plank
    // pivoting on a building-sized footprint would swing round a point out in the
    // lava beside it, and read as thrown rather than tipped.
    private inline fun collapsible(
        cx: Float, cz: Float, hx: Float = BW, hz: Float = BD,
        kind: Float = COLLAPSE_FALLS, add: () -> Unit,
    ) {
        val first = meshes.size
        add()
        if (meshes.size <= first) return
        // Deterministic per-position pseudo-random: same city, same collapse.
        val h = ((cx * 73.13f + cz * 31.7f).toInt() * 2654435761L.toInt())
        val r0 = ((h ushr 8) and 0xFFFF) / 65535f     // 0..1 - when it starts going
        val r1 = ((h ushr 20) and 0xFF) / 255f        // 0..1 - which way it leans
        // The buildings can take their time — they are right there, and the ragged
        // order is the point. The stickmen cannot: they go up, and the screen starts
        // fading to black at 0.72, so one that waited until 0.6 to leave the ground
        // would still be standing there when the lights went out. They go early.
        val startAt = if (kind == COLLAPSE_SWEPT) 0.03f + r0 * 0.17f else 0.05f + r0 * 0.55f
        collapseGroups.add(floatArrayOf(
            first.toFloat(), (meshes.size - 1).toFloat(), cx, cz,
            startAt, r1, hx, hz, kind
        ))
    }

    // What the collapse does to a thing: the city's buildings and the bridge come
    // DOWN, and the stickmen standing around watching it all go get picked up and
    // thrown into the sky. They were never really part of the city anyway.
    private val COLLAPSE_FALLS = 0f
    private val COLLAPSE_SWEPT = 1f

    /**
     * Where this building is, mid-collapse — the whole rigid transform:
     *
     *   out[0..2]  shift      world displacement (the drop, and the shaking)
     *   out[3..5]  axis       horizontal axis it is turning about, unit length
     *   out[6]     angle      radians it has turned through (0 = standing straight)
     *   out[7..9]  pivot      the world point it turns about — the base edge it
     *                         hinges on, so it swings over rather than spinning in
     *                         mid-air
     *
     * Returns false once it is gone, and then it simply stops being drawn.
     *
     * Roughly three in five buildings TIP OVER: they hinge on one base edge and come
     * down across the street, taking the lighting with them (the shader rotates the
     * position, and the normals fall out of that). The rest sink and lean, which is
     * what they all used to do — a whole skyline going over the same way looks
     * choreographed, and this is meant to look like it is coming apart.
     */
    private fun collapseXform(g: FloatArray, t: Float, out: FloatArray): Boolean {
        val seed = g[5]
        val cx = g[2]; val cz = g[3]
        out[3] = 0f; out[4] = 1f; out[5] = 0f     // a safe axis for angle == 0
        out[6] = 0f
        out[7] = cx; out[8] = 0f; out[9] = cz

        val startAt = g[4]
        if (t <= startAt) {
            // Not its turn yet - but the whole city is trembling by now.
            val tremble = (t / startAt).coerceIn(0f, 1f) * 2.5f
            out[0] = sin(t * 137f + cx) * tremble
            out[1] = 0f
            out[2] = cos(t * 119f + cz) * tremble
            return true
        }
        // Its own fall, over the remaining time.
        val p = ((t - startAt) / (1f - startAt).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
        if (p >= 1f) return false                      // gone
        val shake = (1f - p) * 6f

        if (g[8] == COLLAPSE_SWEPT) {
            // Whatever is taking the city apart takes the stickmen too, but it does
            // not drop them: it lifts them, turning them over and over, out and up
            // and away, until they are specks and then nothing. They go up while
            // everything else goes down, which is the whole point of them.
            //
            // Not p*p: something snatched off the ground leaves it NOW. A pure square
            // creeps for the first half of its life, and a stickman that creeps looks
            // like a stickman that is standing still.
            val lift = 0.35f * p + 0.65f * p * p
            val dir = seed * 43.7f
            val dx = cos(dir); val dz = sin(dir)
            out[3] = dz; out[4] = 0f; out[5] = -dx     // tumbling end over end
            out[6] = lift * 13f                        // a fast, loose spin — several turns
            out[7] = cx; out[8] = 0f; out[9] = cz      // about its own feet
            out[0] = dx * lift * 300f + sin(t * 149f + cx) * shake
            out[1] = lift * 1600f                      // straight up, and away
            out[2] = dz * lift * 300f + cos(t * 131f + cz) * shake
            return true
        }

        if (seed > 0.40f) {
            // A toppler. It goes over in the direction its seed picked, hinging on
            // the base edge on that side: rotating about the axis perpendicular to
            // the fall direction carries the roof over and down. Past 90° it is
            // lying in the street; it keeps turning a little past that, and sinks,
            // so it doesn't end up resting on the ground looking placed.
            val dir = seed * 43.7f                     // deterministic, and unrelated to when it starts
            val dx = cos(dir); val dz = sin(dir)
            out[3] = dz; out[4] = 0f; out[5] = -dx     // axis ⟂ fall direction: the roof swings toward (dx, dz)
            out[6] = p * p * 1.85f                     // → ~106°, accelerating like something that has lost its footing
            out[7] = cx + dx * g[6] * 0.85f            // the edge it hinges on — its own edge, not a building's
            out[8] = 0f
            out[9] = cz + dz * g[7] * 0.85f
            out[0] = sin(t * 149f + cx) * shake
            out[1] = -p * p * 90f                      // settles into its own rubble
            out[2] = cos(t * 131f + cz) * shake
            return true
        }

        // A sinker: straight down, with a lean, as before.
        val drop = p * p * 420f                        // accelerating, like gravity
        val lean = (seed - 0.5f) * 140f * p
        out[0] = lean + sin(t * 149f + cx) * shake
        out[1] = -drop
        out[2] = lean * 0.6f + cos(t * 131f + cz) * shake
        return true
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────
    // The cameras, the doors and the entrance lights are not in `meshes` — they are
    // rebuilt in world space every frame (they track the player, they slide open).
    // So the collapse has to be carried to them by hand, or they hang in the air
    // over the rubble of the building they were bolted to.

    /** The collapsing building a fixture at (x, z) belongs to, if any. */
    private fun collapseGroupNear(x: Float, z: Float): FloatArray? {
        var best: FloatArray? = null
        var bestD = Float.MAX_VALUE
        for (g in collapseGroups) {
            if (g[8] != COLLAPSE_FALLS) continue    // a camera does not fly off with a stickman
            val dx = x - g[2]; val dz = z - g[3]
            val d = dx * dx + dz * dz
            if (d < bestD) { bestD = d; best = g }
        }
        val g = best ?: return null
        // Corner cameras sit a little outside the footprint, so allow some slack —
        // but not so much that a fixture gets adopted by the building across the road.
        return if (abs(x - g[2]) <= g[6] + 45f && abs(z - g[3]) <= g[7] + 45f) g else null
    }

    /** Applies a [collapseXform] to one world-space point. */
    private fun applyXform(xf: FloatArray, x: Float, y: Float, z: Float, out: FloatArray) {
        var px = x; var py = y; var pz = z
        val ang = xf[6]
        if (ang != 0f) {
            val qx = x - xf[7]; val qy = y - xf[8]; val qz = z - xf[9]
            val kx = xf[3]; val ky = xf[4]; val kz = xf[5]
            val c = cos(ang); val s = sin(ang)
            val crx = ky * qz - kz * qy
            val cry = kz * qx - kx * qz
            val crz = kx * qy - ky * qx
            val kd = (kx * qx + ky * qy + kz * qz) * (1f - c)
            px = qx * c + crx * s + kx * kd + xf[7]
            py = qy * c + cry * s + ky * kd + xf[8]
            pz = qz * c + crz * s + kz * kd + xf[9]
        }
        out[0] = px + xf[0]
        out[1] = py + xf[1]
        out[2] = pz + xf[2]
    }

    // Surfaces with real geometry take the sun and the lamps; the self-lit and 2D
    // things (lines, lava, the spinning arcs, glows, shadow blobs, the sky quad,
    // the night overlay) stay flat. Uniform state persists with the program, so
    // the cached value is valid across frames.
    // The aerial intro is deliberately flat — it has to read as a calculator
    // keypad, not as a lit city — so lighting is forced off whenever it's running.
    // ── Model textures ───────────────────────────────────────────────────────
    // A material with a map_Kd (the whiteboards' image planes) paints with an image
    // instead of a flat colour. The exporter copies whatever images the .blend uses
    // into assets/models/textures/ and names them in the MTL, so a new textured
    // surface in any model needs nothing here — export it and it appears.

    /** Uploads [name] from assets/models/textures/, once. 0 if it isn't there. */
    private fun modelTexture(name: String): Int {
        modelTextures[name]?.let { return it }
        val a = assets ?: return 0
        val id = try {
            val bmp = a.open("models/textures/$name").use {
                android.graphics.BitmapFactory.decodeStream(it)
            } ?: return 0
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            bmp.recycle()
            ids[0]
        } catch (t: Throwable) {
            android.util.Log.w("CityGL", "texture missing: $name")
            0
        }
        modelTextures[name] = id
        return id
    }

    // Whether the last draw was textured. The bind itself is NOT cached — the CCTV
    // pass binds its atlas to the same texture unit, so a cached bind would come back
    // next frame with the feed atlas painted across the whiteboards.
    private var curTexOn = false
    private fun clearMeshTexture() {
        if (!curTexOn) return
        GLES20.glUniform1f(uTexOn, 0f)
        GLES20.glDisableVertexAttribArray(aUV)
        curTexOn = false
    }
    private fun bindMeshTexture(m: Mesh): Boolean {
        val uv = m.uv
        if (m.tex == 0 || uv == null) {
            clearMeshTexture()
            return false
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, m.tex)
        GLES20.glUniform1i(uTexSampler, 0)
        if (!curTexOn) { GLES20.glUniform1f(uTexOn, 1f); curTexOn = true }
        uv.position(0)
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 8, uv)
        GLES20.glEnableVertexAttribArray(aUV)
        return true
    }

    // The collapse transform currently uploaded. Every draw that isn't a falling
    // building passes null and gets the identity — and because the uniforms persist
    // with the program, the cache stays valid across frames.
    private val curXform = FloatArray(10)
    private var curXformSet = false
    private fun setXform(xf: FloatArray?) {
        if (xf == null) {
            if (!curXformSet) return                     // already identity
            GLES20.glUniform3f(uShift, 0f, 0f, 0f)
            GLES20.glUniform4f(uTip, 0f, 1f, 0f, 0f)
            GLES20.glUniform3f(uPivot, 0f, 0f, 0f)
            curXformSet = false
            return
        }
        if (curXformSet && xf.contentEquals(curXform)) return
        GLES20.glUniform3f(uShift, xf[0], xf[1], xf[2])
        GLES20.glUniform4f(uTip, xf[3], xf[4], xf[5], xf[6])
        GLES20.glUniform3f(uPivot, xf[7], xf[8], xf[9])
        xf.copyInto(curXform)
        curXformSet = true
    }

    private var curLitU = -1f
    private fun setLit(v: Float) {
        val want = if (aerialMode) 0f else v
        if (lightingOn && want != curLitU) { GLES20.glUniform1f(uLit, want); curLitU = want }
    }

    // The shader carries MAX_LIGHTS point lights; the city has far more lamps than
    // that, so each frame we hand it the ones nearest the camera. Lamps beyond
    // their own reach are dropped (radius 0 = off).
    // [px]/[pz] is the eye the set is chosen around — the player's camera for the
    // frame itself, and the security camera's own position when a CCTV feed is
    // being rendered (otherwise a feed of a distant street would be lit by the
    // lamps standing behind the player, back in Building 10).
    private fun uploadNearestLights(px: Float = camX, pz: Float = camZ) {
        var n = 0
        if (darknessLevel > 0.5f && !aerialMode && lampLights.isNotEmpty()) {
            val near = lampLights
                .filter { l ->
                    // A building's windows go dark once it has been explored; the
                    // lamps (digit -1) always burn.
                    val digit = l[4].toInt()
                    digit !in 1..9 ||
                        !((digit - 1) in buildingCompleted.indices && buildingCompleted[digit - 1])
                }
                .sortedBy { l ->
                    val dx = l[0] - px; val dz = l[2] - pz
                    dx * dx + dz * dz
                }
                .take(MAX_LIGHTS)
            for (l in near) {
                lightPosBuf[n * 3] = l[0]; lightPosBuf[n * 3 + 1] = l[1]; lightPosBuf[n * 3 + 2] = l[2]
                lightRadBuf[n] = l[3]
                n++
            }
        }
        while (n < MAX_LIGHTS) {
            lightPosBuf[n * 3] = 0f; lightPosBuf[n * 3 + 1] = 0f; lightPosBuf[n * 3 + 2] = 0f
            lightRadBuf[n] = 0f
            n++
        }
        GLES20.glUniform3fv(uLightPos, MAX_LIGHTS, lightPosBuf, 0)
        GLES20.glUniform1fv(uLightRad, MAX_LIGHTS, lightRadBuf, 0)
    }

    // Returns 0 if anything fails to compile or link, so callers can fall back
    // instead of binding a dead program (which just renders black).
    private fun buildProg(vs:String, fs:String): Int {
        val v=comp(GLES20.GL_VERTEX_SHADER,vs)
        val f=comp(GLES20.GL_FRAGMENT_SHADER,fs)
        if (v == 0 || f == 0) return 0
        val p=GLES20.glCreateProgram()
        GLES20.glAttachShader(p,v); GLES20.glAttachShader(p,f); GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            android.util.Log.w("CityGL", "link failed: " + GLES20.glGetProgramInfoLog(p))
            GLES20.glDeleteProgram(p)
            return 0
        }
        return p
    }
    private fun comp(t:Int, s:String): Int {
        val sh=GLES20.glCreateShader(t)
        GLES20.glShaderSource(sh,s); GLES20.glCompileShader(sh)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(sh, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            android.util.Log.w("CityGL", "shader compile failed: " + GLES20.glGetShaderInfoLog(sh))
            GLES20.glDeleteShader(sh)
            return 0
        }
        return sh
    }

    // Returns true if `angle` falls within the arc from `start` to `end` (degrees, wraps 360)
    private fun arcInRange(angle: Float, start: Float, end: Float): Boolean {
        val a = ((angle % 360f) + 360f) % 360f
        val s = ((start % 360f) + 360f) % 360f
        val e = ((end   % 360f) + 360f) % 360f
        return if (s <= e) a in s..e else a >= s || a <= e
    }

    private val PI = Math.PI
    private fun lerp(a:Float, b:Float, t:Float) = a+(b-a)*t
}

private fun FloatArray.toFB(): FloatBuffer =
    ByteBuffer.allocateDirect(size*4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().also { it.put(this); it.position(0) }
