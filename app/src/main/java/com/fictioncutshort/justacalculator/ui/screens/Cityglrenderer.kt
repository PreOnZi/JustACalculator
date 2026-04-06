package com.fictioncutshort.justacalculator.ui.screens

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

class CityGLRenderer : GLSurfaceView.Renderer {

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

    private var prog=0; private var aPos=0; private var uMVP=0
    private var uCol=0; private var uFog=0; private var uAerial=0

    private data class Mesh(
        val buf: FloatBuffer, val mode: Int, val cnt: Int,
        val r: Float, val g: Float, val b: Float, val a: Float = 1f,
        val fog: Float = 0.65f, val lava: Boolean = false,
        val aerialSkip: Boolean = false,   // skip during aerial intro
        val radArc: Boolean = false, val arcAngle: Float = 0f  // spinning arc ring segment
    )
    private val meshes = mutableListOf<Mesh>()

    // ── Shaders ───────────────────────────────────────────────────────────────
    private val VS = """
        uniform mat4 uMVP;
        attribute vec4 aPosition;
        varying float vDepth;
        varying float vY;
        void main(){
            gl_Position = uMVP * aPosition;
            vDepth = gl_Position.z / gl_Position.w;
            vY = aPosition.y;
        }""".trimIndent()

    private val FS = """
        precision mediump float;
        uniform vec4  uColor;
        uniform float uFog;
        uniform float uAerial;
        varying float vDepth;
        varying float vY;
        void main(){
            float fog = clamp(vDepth * uFog, 0.0, 0.55);
            float ao  = mix(0.76 + clamp(vY/300.0,0.0,1.0)*0.24, 1.0, uAerial);
            vec3 fogC = vec3(0.29, 0.22, 0.16);
            vec3 col  = uColor.rgb * ao;
            col = mix(col, fogC, fog);
            gl_FragColor = vec4(col, uColor.a);
        }""".trimIndent()

    // ── Grid constants ────────────────────────────────────────────────────────
    @Volatile var BW    =  85f    // building half-width X
    @Volatile var BD    =  72f    // building half-depth Z  (rectangular: 170×144)
    @Volatile var CELL  = 200f

    private val C1 = -CELL * 1.5f  // -300
    private val C2 = -CELL * 0.5f  // -100
    private val C3 =  CELL * 0.5f  // +100
    private val C4 =  CELL * 1.5f  // +300
    private val RA = -CELL * 2f    // -400
    private val RB = -CELL * 1f    // -200
    private val RC =  0f
    private val RD =  CELL * 1f    // +200
    private val RE =  CELL * 2f    // +400

    // City boundary
    private val CITY_N = RA - BD         // -460
    private val CITY_S = RE + BD         // +460
    private val CITY_W = C1 - BW         // -380
    private val CITY_E = C4 + BW         // +380

    // Features
    private val LAVA_S  = CITY_N - 40f   // -500
    private val LAVA_N  = LAVA_S - 360f  // -860  (360 deep — larger lava screen)
    private val WALL_X  = CITY_W - 20f   // -400

    private val BH = 280f

    // Interactive buildings: [digit, cx, cz, height, doorFace (0=S,1=N,2=E,3=W)]
    // Door faces chosen so no two adjacent buildings ever face each other across a street.
    private val BUILDINGS = arrayOf(
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

    private val FUNCTION_BLDGS = arrayOf(
        floatArrayOf(C1, RA, BH*0.65f),
        floatArrayOf(C2, RA, BH*0.70f),
        floatArrayOf(C3, RA, BH*0.62f),
    )
    private val FUNCTION_LABELS = arrayOf("C", "()", "%")

    private val DAMAGED_BLDGS = arrayOf(
        floatArrayOf(C1, RE, BH*0.72f),
        floatArrayOf(C2, RE, BH*0.68f),
        floatArrayOf(C3, RE, BH*0.65f),
    )
    private val DAMAGED_LABELS = arrayOf("DEL", "0", ".")

    private val OPERATOR_BLDGS = arrayOf(
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
        prog    = buildProg(VS, FS)
        aPos    = GLES20.glGetAttribLocation(prog, "aPosition")
        uMVP    = GLES20.glGetUniformLocation(prog, "uMVP")
        uCol    = GLES20.glGetUniformLocation(prog, "uColor")
        uFog    = GLES20.glGetUniformLocation(prog, "uFog")
        uAerial = GLES20.glGetUniformLocation(prog, "uAerial")
        buildScene()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h); sw = w; sh = h
    }
    private var sw = 1; private var sh = 1

    override fun onDrawFrame(gl: GL10?) {
        if (needsRebuild) { needsRebuild = false; buildScene() }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(prog)

        val proj = FloatArray(16); val view = FloatArray(16); val mvp = FloatArray(16)
        Matrix.perspectiveM(proj, 0, fov, sw.toFloat() / sh.toFloat(), 0.5f, 3000f)

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
            Matrix.rotateM(view, 0, camPitch, 1f, 0f, 0f)
            Matrix.rotateM(view, 0, camYaw, 0f, 1f, 0f)
            Matrix.translateM(view, 0, -camX, -camY, -camZ)
        }

        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0)
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvp, 0)
        GLES20.glUniform1f(uAerial, aerialBlend)

        val radA = ((radAngle % 360f) + 360f) % 360f
        for (m in meshes) {
            if (m.aerialSkip && aerialMode) continue
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
            } else { r = m.r; g = m.g; b = m.b }
            GLES20.glUniform4f(uCol, r, g, b, m.a)
            GLES20.glUniform1f(uFog, if (aerialMode) m.fog else 0f)
            m.buf.position(0)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, m.buf)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glDrawArrays(m.mode, 0, m.cnt)
        }

        if (showPlayer) {
            val ox = playerX; val oy = 32f + 8f; val oz = playerZ
            drawOrb(ox, oy, oz, 8f, mvp)
        }
    }

    // ── Scene ─────────────────────────────────────────────────────────────────

    fun rebuildScene() { buildScene() }

    private fun buildScene() {
        meshes.clear()
        if (isLandscape) { buildSceneLandscape(); return }
        addGround()
        for (b in BUILDINGS)      addBuildingShadow(b[1], b[2], b[3]*buildingHeightScale)
        for (f in FUNCTION_BLDGS) addBuildingShadow(f[0], f[1], f[2]*buildingHeightScale)
        for (i in DAMAGED_BLDGS.indices) addBuildingShadow(DAMAGED_BLDGS[i][0], DAMAGED_BLDGS[i][1], DAMAGED_BLDGS[i][2]*buildingHeightScale)
        for (o in OPERATOR_BLDGS) addBuildingShadow(o[0], o[1], o[2]*buildingHeightScale)
        for (b in BUILDINGS) addBuilding(b[1], b[2], b[3]*buildingHeightScale, b[4].toInt(), b[0].toInt().toString())
        for (i in FUNCTION_BLDGS.indices) addFunctionBuilding(FUNCTION_BLDGS[i][0], FUNCTION_BLDGS[i][1], FUNCTION_BLDGS[i][2]*buildingHeightScale, FUNCTION_LABELS[i])
        for (i in DAMAGED_BLDGS.indices) addDamagedBuilding(DAMAGED_BLDGS[i][0], DAMAGED_BLDGS[i][1], DAMAGED_BLDGS[i][2]*buildingHeightScale, DAMAGED_LABELS[i])
        for (i in OPERATOR_BLDGS.indices) addOperatorBuilding(OPERATOR_BLDGS[i][0], OPERATOR_BLDGS[i][1], OPERATOR_BLDGS[i][2]*buildingHeightScale, OPERATOR_LABELS[i])
        addWestWall()
        addLava()
        addGreenNorth()
        addRadButton(80f * buildingHeightScale)
        addDebris()
        if (bridgePieces > 0) addBridge(bridgePieces)
    }

    // ── Landscape scene (horizontal orientation) ──────────────────────────────
    // Buildings on the RIGHT (+X side, matching the keyboard panel):
    //   C1=200, C2=400, C3=600, C4=800  — same Z rows as portrait
    // Lava on the LEFT-BOTTOM (-X, +Z corner): X=-680..85, Z=275..580
    // RAD button on the LEFT-MIDDLE: bx=-350, bz=-100
    // Wall at X=95 with gap Z=272..328 (between "1" RD=200 and "DEL" RE=400)

    private fun buildSceneLandscape() {
        val lC1 = 200f; val lC2 = 400f; val lC3 = 600f; val lC4 = 800f
        val lRA = -400f; val lRB = -200f; val lRC = 0f; val lRD = 200f; val lRE = 400f

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
        for (i in lFUNCTION_BLDGS.indices)  addFunctionBuilding(lFUNCTION_BLDGS[i][0],  lFUNCTION_BLDGS[i][1],  lFUNCTION_BLDGS[i][2]*buildingHeightScale,  FUNCTION_LABELS[i])
        for (i in lDAMAGED_BLDGS.indices)   addDamagedBuilding(lDAMAGED_BLDGS[i][0],    lDAMAGED_BLDGS[i][1],   lDAMAGED_BLDGS[i][2]*buildingHeightScale,   DAMAGED_LABELS[i])
        for (i in lOPERATOR_BLDGS.indices)  addOperatorBuilding(lOPERATOR_BLDGS[i][0],  lOPERATOR_BLDGS[i][1],  lOPERATOR_BLDGS[i][2]*buildingHeightScale,  OPERATOR_LABELS[i])

        // West wall at X=95 with gap between "1" (lRD+BD=272) and "DEL" (lRE-BD=328)
        addLandscapeWall(lRD + BD, lRE - BD)

        // Lava — south-west corner (left-bottom in aerial view)
        addRoundedRectFill(-680f, 20f, 275f, 85f, 580f, 12f, 1.0f, 0.32f, 0.04f, fog=0.05f)

        // RAD spinning button — left-middle in aerial view
        addRadButton(80f * buildingHeightScale, bx = -350f, bz = -100f)

        // Debris on east operator column gaps
        addDebrisLandscape(lC4, lRA, lRB, lRC, lRD, lRE)
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
        val dY = 0.2f
        val debrisColors = listOf(
            floatArrayOf(0.42f,0.40f,0.38f), floatArrayOf(0.55f,0.30f,0.22f),
            floatArrayOf(0.28f,0.25f,0.22f), floatArrayOf(0.50f,0.46f,0.38f),
        )
        val col4Gaps = listOf(
            Pair(rA+BD+2f, rB-BD-2f), Pair(rB+BD+2f, rC-BD-2f),
            Pair(rC+BD+2f, rD-BD-2f), Pair(rD+BD+2f, rE-BD-2f),
        )
        val rnd = java.util.Random(133L)
        for ((gz0, gz1) in col4Gaps) {
            if (gz1 <= gz0) continue
            val gapW = c4+BW+80f - (c4-BW-80f)
            repeat(14) {
                val px = c4-BW-75f + rnd.nextFloat()*gapW
                val pz = gz0 + rnd.nextFloat()*(gz1-gz0)
                val rr = 9f+rnd.nextFloat()*22f; val hh = 12f+rnd.nextFloat()*55f
                val col = debrisColors[rnd.nextInt(debrisColors.size)]
                addQ(px-rr,dY,pz-rr, px+rr,dY,pz-rr, px+rr,hh,pz-rr, px-rr,hh,pz-rr, col[0],col[1],col[2], fog=0.28f)
                addQ(px-rr,hh,pz-rr, px+rr,hh,pz-rr, px+rr,hh,pz+rr*0.4f, px-rr,hh,pz+rr*0.4f, col[0]*0.85f,col[1]*0.85f,col[2]*0.85f, fog=0.28f)
                addQ(px+rr,dY,pz-rr, px+rr,dY,pz+rr*0.4f, px+rr,hh,pz+rr*0.4f, px+rr,hh,pz-rr, col[0]*0.75f,col[1]*0.75f,col[2]*0.75f, fog=0.28f)
                addL(px-rr,hh+1f,pz-rr, px+rr,hh+1f,pz-rr, 0.04f,0.04f,0.04f)
                addL(px+rr,hh+1f,pz-rr, px+rr,dY,pz-rr,    0.04f,0.04f,0.04f)
            }
        }
        // South debris from damaged row (DEL/0/.) at lRE
        val lC1L = c4-600f; val lC2L = c4-400f; val lC3L = c4-200f
        val southColors = listOf(
            floatArrayOf(0.68f,0.65f,0.62f), floatArrayOf(0.65f,0.32f,0.18f),
            floatArrayOf(0.54f,0.50f,0.45f), floatArrayOf(0.32f,0.28f,0.24f),
        )
        val rnd2 = java.util.Random(77L)
        for (cx in listOf(lC1L, lC2L, lC3L)) {
            val h = BH * 0.70f; val z1 = rE + BD + 4f
            repeat(12) {
                val px = cx + rnd2.nextFloat()*BW*1.8f - BW*0.9f
                val pz = z1 + rnd2.nextFloat()*80f
                val rr = 8f+rnd2.nextFloat()*20f; val hh = 4f+rnd2.nextFloat()*(h*0.18f)
                val sc = southColors[rnd2.nextInt(southColors.size)]
                addQ(px-rr,dY,pz-rr, px+rr,dY,pz-rr, px+rr,hh,pz-rr, px-rr,hh,pz-rr, sc[0],sc[1],sc[2], fog=0.30f)
                addQ(px-rr,hh,pz-rr, px+rr,hh,pz-rr, px+rr,hh,pz+rr*0.5f, px-rr,hh,pz+rr*0.5f, sc[0]*0.85f,sc[1]*0.85f,sc[2]*0.85f, fog=0.30f)
                addL(px-rr-1f,hh+1f,pz-rr, px+rr+1f,hh+1f,pz-rr, 0.04f,0.04f,0.04f)
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

    private fun addBuilding(cx: Float, cz: Float, h: Float, door: Int, label: String) {
        val x0=cx-BW; val x1=cx+BW; val z0=cz-BD; val z1=cz+BD
        addChamfWalls(x0,z0,x1,z1,h,
            0.91f,0.89f,0.85f, // south — number button cream
            0.56f,0.55f,0.52f, // north  (×0.615)
            0.76f,0.74f,0.71f, // east   (×0.835)
            0.67f,0.65f,0.63f, // west   (×0.736)
            0.87f,0.85f,0.81f, // top    (×0.956)
        fog = 0.0f)




        // Door recess — dark purple void (LCD green for Building 1 after TD complete)
        val dw = BW*0.30f; val dh = h*0.18f
        val dr = if (label == "1" && b1DoorGreen) 0.10f else 0.12f
        val dg = if (label == "1" && b1DoorGreen) 0.55f else 0.06f
        val db = if (label == "1" && b1DoorGreen) 0.22f else 0.18f
        when (door) {
            0 -> addQ(cx-dw,0f,z1+0.5f, cx+dw,0f,z1+0.5f, cx+dw,dh,z1+0.5f, cx-dw,dh,z1+0.5f, dr,dg,db, fog=0.05f)
            1 -> addQ(cx+dw,0f,z0-0.5f, cx-dw,0f,z0-0.5f, cx-dw,dh,z0-0.5f, cx+dw,dh,z0-0.5f, dr,dg,db, fog=0.05f)
            2 -> addQ(x1+0.5f,0f,cz+dw, x1+0.5f,0f,cz-dw, x1+0.5f,dh,cz-dw, x1+0.5f,dh,cz+dw, dr,dg,db, fog=0.05f)
            3 -> addQ(x0-0.5f,0f,cz-dw, x0-0.5f,0f,cz+dw, x0-0.5f,dh,cz+dw, x0-0.5f,dh,cz-dw, dr,dg,db, fog=0.05f)
        }

        // Roof: dark brown background + 7-segment digit
        val ly = h + 2f; val ds = 40f

        addDigit(cx, ly+0.4f, cz, ds, label)
        val eo2 = ds+6f; val bk = 0.04f

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

    // ── Function building (row A: C  ()  %) ──────────────────────────────────

    private fun addFunctionBuilding(cx: Float, cz: Float, h: Float, label: String) {
        val x0=cx-BW; val x1=cx+BW; val z0=cz-BD; val z1=cz+BD
        // C building uses the same dark red as DEL; others use gray-beige
        if (label == "C") {
            addChamfWalls(x0,z0,x1,z1,h,
                0.79f,0.27f,0.24f, // south
                0.49f,0.17f,0.15f, // north  (×0.615)
                0.66f,0.23f,0.20f, // east   (×0.835)
                0.58f,0.20f,0.18f, // west   (×0.736)
                0.76f,0.26f,0.23f, // top    (×0.956)
                fog=0.0f)
        } else {
            addChamfWalls(x0,z0,x1,z1,h,
                0.42f,0.42f,0.42f, // south
                0.26f,0.26f,0.26f, // north  (×0.615)
                0.35f,0.35f,0.35f, // east   (×0.835)
                0.31f,0.31f,0.31f, // west   (×0.736)
                0.40f,0.40f,0.40f, // top    (×0.956)
                fog=0.0f)
        }
        val iset = BW*0.18f
        // Roof label — C uses bright gold on dark background like DEL, others standard
        val ly = h + 4f; val ds = 32f
        addDigit(cx, ly+0.4f, cz, ds, label, colR=1.0f, colG=1.0f, colB=1.0f)
    }

    // ── Damaged building (row E: DEL  0  .) ──────────────────────────────────

    private fun addDamagedBuilding(cx: Float, cz: Float, h: Float, label: String) {
        val x0=cx-BW; val x1=cx+BW; val z0=cz-BD; val z1=cz+BD
        val (fS,fN,fE,fW,fT) = when (label) {
            "DEL" -> arrayOf(
                floatArrayOf(0.83f,0.47f,0.24f), floatArrayOf(0.51f,0.29f,0.15f),
                floatArrayOf(0.69f,0.39f,0.20f), floatArrayOf(0.61f,0.35f,0.18f),
                floatArrayOf(0.79f,0.45f,0.23f))
            "0" -> arrayOf(
                floatArrayOf(0.91f,0.89f,0.85f), floatArrayOf(0.56f,0.55f,0.52f),
                floatArrayOf(0.76f,0.74f,0.71f), floatArrayOf(0.67f,0.65f,0.63f),
                floatArrayOf(0.87f,0.85f,0.81f))
            else -> arrayOf(  // "."
                floatArrayOf(0.83f,0.82f,0.77f), floatArrayOf(0.51f,0.50f,0.47f),
                floatArrayOf(0.69f,0.68f,0.64f), floatArrayOf(0.61f,0.60f,0.57f),
                floatArrayOf(0.79f,0.78f,0.74f))
        }
        addChamfWalls(x0,z0,x1,z1,h,
            fS[0],fS[1],fS[2], fN[0],fN[1],fN[2],
            fE[0],fE[1],fE[2], fW[0],fW[1],fW[2],
            fT[0],fT[1],fT[2], fog=0.0f)


        // Damage cracks on ALL visible faces
        val rnd = java.util.Random((cx*7+cz*13).toLong())
        val crackCol = if (label=="DEL") floatArrayOf(0.14f,0.06f,0.05f) else floatArrayOf(0.18f,0.14f,0.22f)
        repeat(6) {
            val cky = rnd.nextFloat()*h*0.85f; val len = 18f + rnd.nextFloat()*55f
            val face = it % 4
            when (face) {
                0 -> addL(cx+rnd.nextFloat()*40f-20f, cky, z1+0.6f, cx+rnd.nextFloat()*25f-12f, cky+len, z1+0.6f, crackCol[0],crackCol[1],crackCol[2])
                1 -> addL(cx+rnd.nextFloat()*40f-20f, cky, z0-0.6f, cx+rnd.nextFloat()*25f-12f, cky+len, z0-0.6f, crackCol[0],crackCol[1],crackCol[2])
                2 -> addL(x1+0.6f, cky, cz+rnd.nextFloat()*40f-20f, x1+0.6f, cky+len, cz+rnd.nextFloat()*25f-12f, crackCol[0],crackCol[1],crackCol[2])
                3 -> addL(x0-0.6f, cky, cz+rnd.nextFloat()*40f-20f, x0-0.6f, cky+len, cz+rnd.nextFloat()*25f-12f, crackCol[0],crackCol[1],crackCol[2])
            }
        }

        // Rubble at base of damaged building
        repeat(6) {
            val rpx = cx + rnd.nextFloat()*BW*1.6f - BW*0.8f
            val rpz = cz + rnd.nextFloat()*BD*1.6f - BD*0.8f
            val rr = 5f + rnd.nextFloat()*12f; val rh = 3f + rnd.nextFloat()*12f
            addQ(rpx-rr,0.2f,rpz-rr, rpx+rr,0.2f,rpz-rr, rpx+rr,rh,rpz, rpx-rr,rh,rpz,
                fS[0]*0.8f,fS[1]*0.8f,fS[2]*0.8f, fog=0.35f)
        }

        // Roof: dim label
        val ly = h + 2f
        when (label) {
            "DEL" -> {
                val ds=20f; val spacing=ds*2.2f; val startX=cx-spacing

                addDigit(startX,       ly+0.4f, cz, ds, "d", colR=1.0f, colG=1.0f, colB=1.0f)
                addDigit(startX+spacing,   ly+0.4f, cz, ds, "E", colR=1.0f, colG=1.0f, colB=1.0f)
                addDigit(startX+spacing*2f, ly+0.4f, cz, ds, "L", colR=1.0f, colG=1.0f, colB=1.0f)
            }
            else -> {
                val ds=28f

                addDigit(cx, ly+0.4f, cz, ds, label)
            }
        }
    }

    // ── Operator building (col 4: /  *  −  +  =) ──────────────────────────────

    private fun addOperatorBuilding(cx: Float, cz: Float, h: Float, label: String) {
        val x0=cx-BW; val x1=cx+BW; val z0=cz-BD; val z1=cz+BD
        addChamfWalls(x0,z0,x1,z1,h,
            0.42f,0.42f,0.42f,
            0.26f,0.26f,0.26f,
            0.35f,0.35f,0.35f,
            0.31f,0.31f,0.31f,
            0.40f,0.40f,0.40f,
            fog=0.0f)


        val ly = h + 2f; val ds = 34f
        addDigit(cx, ly+0.4f, cz, ds, label, colR=1.0f, colG=1.0f, colB=1.0f)
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
        addRoundedRectFill(lxW, 20f, LAVA_N, lxE, LAVA_S - 20f,
            12f, 1.0f, 0.32f, 0.04f, fog=0.05f)
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

    // ── Sun — vertical disc in sky, only visible during gameplay ─────────────

    private fun addSun() {
        val sx=450f; val sy=520f; val sz=-1400f; val segs=24
        val pi=PI.toFloat()
        val rings = listOf(
            floatArrayOf(1.0f, 1.00f, 0.92f, 0.40f),   // golden core
            floatArrayOf(1.6f, 0.85f, 0.48f, 0.75f),   // inner purple
            floatArrayOf(2.5f, 0.55f, 0.22f, 0.72f),   // mid purple
            floatArrayOf(3.8f, 0.30f, 0.10f, 0.45f),   // outer glow
        )
        for (ring in rings) {
            val scale=ring[0]; val cr=ring[1]; val cg=ring[2]; val cb=ring[3]
            val r = 140f*scale
            val verts = mutableListOf<Float>()
            for (i in 0 until segs) {
                val a0 = i.toFloat()/segs*2f*pi
                val a1 = (i+1).toFloat()/segs*2f*pi
                // Vertical disc in XY plane (normal facing +Z, visible when looking north)
                verts += listOf(sx, sy, sz)
                verts += listOf(sx+r*cos(a0), sy+r*sin(a0), sz)
                verts += listOf(sx+r*cos(a1), sy+r*sin(a1), sz)
            }
            val arr = verts.toFloatArray()
            meshes.add(Mesh(arr.toFB(), GLES20.GL_TRIANGLES, arr.size/3, cr,cg,cb,1f, fog=0f, aerialSkip=true))
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

    private fun addDebris() {
        val dY=0.2f
        val rnd = java.util.Random(77L)

        // South face of each row E damaged building: rubble spilling outward (varied colors)
        val southDebrisColors = listOf(
            floatArrayOf(0.68f,0.65f,0.62f),  // concrete gray
            floatArrayOf(0.65f,0.32f,0.18f),  // rust-orange (matches DEL)
            floatArrayOf(0.54f,0.50f,0.45f),  // sandy rubble
            floatArrayOf(0.32f,0.28f,0.24f),  // charcoal dark
        )
        for (fp in DAMAGED_BLDGS) {
            val cx=fp[0]; val cz=fp[1]; val h=fp[2]
            val z1=cz+BD+4f
            repeat(12) {
                val px=cx+rnd.nextFloat()*BW*1.8f-BW*0.9f
                val pz=z1+rnd.nextFloat()*80f
                val rr=8f+rnd.nextFloat()*20f; val hh=4f+rnd.nextFloat()*(h*0.18f)
                val sc=southDebrisColors[rnd.nextInt(southDebrisColors.size)]
                // Front face
                addQ(px-rr,dY,pz-rr, px+rr,dY,pz-rr, px+rr,hh,pz-rr, px-rr,hh,pz-rr, sc[0],sc[1],sc[2], fog=0.30f)
                // Top face
                addQ(px-rr,hh,pz-rr, px+rr,hh,pz-rr, px+rr,hh,pz+rr*0.5f, px-rr,hh,pz+rr*0.5f, sc[0]*0.85f,sc[1]*0.85f,sc[2]*0.85f, fog=0.30f)
                // Side face
                addQ(px+rr,dY,pz-rr, px+rr,dY,pz+rr*0.5f, px+rr,hh,pz+rr*0.5f, px+rr,hh,pz-rr, sc[0]*0.75f,sc[1]*0.75f,sc[2]*0.75f, fog=0.30f)
                addL(px-rr-1f,hh+1f,pz-rr, px+rr+1f,hh+1f,pz-rr, 0.04f,0.04f,0.04f)
                addL(px+rr+1f,hh+1f,pz-rr, px+rr+1f,dY,pz-rr,    0.04f,0.04f,0.04f)
            }
        }

        // Row E horizontal gaps (south boundary + between buildings)
        val gapZ0=RE+BD+2f; val gapZ1=gapZ0+60f
        val rowEGaps = listOf(
            Pair(C1+BW+2f, C2-BW-2f),
            Pair(C2+BW+2f, C3-BW-2f),
            Pair(C3+BW+2f, C4-BW-2f),
            Pair(-600f, C1-BW-2f),
            Pair(C4+BW+2f, 600f),
        )
        for ((gx0,gx1) in rowEGaps) {
            if (gx1<=gx0) continue
            repeat(8) {
                val px=gx0+rnd.nextFloat()*(gx1-gx0)
                val pz=gapZ0+rnd.nextFloat()*(gapZ1-gapZ0)
                val rr=10f+rnd.nextFloat()*18f; val hh=6f+rnd.nextFloat()*30f
                addQ(px-rr,dY,pz-rr, px+rr,dY,pz-rr, px+rr,hh,pz, px-rr,hh,pz,
                    0.68f,0.67f,0.66f, fog=0.30f)
                addL(px-rr-1f,hh,pz-rr, px+rr+1f,hh,pz-rr, 0.04f,0.04f,0.04f)
                addL(px+rr+1f,hh,pz-rr, px+rr+1f,hh,pz+1f,  0.04f,0.04f,0.04f)
            }
        }

        // Col 4 (east operator column) — 3D debris between each pair of buildings
        // Gaps are in Z direction between rows A–E at x~C4
        val debrisColors = listOf(
            floatArrayOf(0.42f,0.40f,0.38f),  // dark concrete
            floatArrayOf(0.55f,0.30f,0.22f),  // rust-red chunk
            floatArrayOf(0.28f,0.25f,0.22f),  // charcoal
            floatArrayOf(0.50f,0.46f,0.38f),  // sandy gray
        )
        val col4Gaps = listOf(
            Pair(RA+BD+2f, RB-BD-2f),  // between / and *
            Pair(RB+BD+2f, RC-BD-2f),  // between * and -
            Pair(RC+BD+2f, RD-BD-2f),  // between - and +
            Pair(RD+BD+2f, RE-BD-2f),  // between + and =
        )
        val rnd2 = java.util.Random(133L)
        for ((gz0,gz1) in col4Gaps) {
            if (gz1<=gz0) continue
            val gapW = C4+BW+80f - (C4-BW-80f)  // wide enough to block passage
            repeat(14) {
                val px=C4-BW-75f+rnd2.nextFloat()*gapW
                val pz=gz0+rnd2.nextFloat()*(gz1-gz0)
                val rr=9f+rnd2.nextFloat()*22f
                // Vary height: some tall, some wide, some medium — 3D feel
                val hh=12f+rnd2.nextFloat()*55f
                val col=debrisColors[rnd2.nextInt(debrisColors.size)]
                // Front face (south-facing slab)
                addQ(px-rr,dY,pz-rr, px+rr,dY,pz-rr, px+rr,hh,pz-rr, px-rr,hh,pz-rr, col[0],col[1],col[2], fog=0.28f)
                // Top face
                addQ(px-rr,hh,pz-rr, px+rr,hh,pz-rr, px+rr,hh,pz+rr*0.4f, px-rr,hh,pz+rr*0.4f, col[0]*0.85f,col[1]*0.85f,col[2]*0.85f, fog=0.28f)
                // Side face
                addQ(px+rr,dY,pz-rr, px+rr,dY,pz+rr*0.4f, px+rr,hh,pz+rr*0.4f, px+rr,hh,pz-rr, col[0]*0.75f,col[1]*0.75f,col[2]*0.75f, fog=0.28f)
                // Edges
                addL(px-rr,hh+1f,pz-rr, px+rr,hh+1f,pz-rr, 0.04f,0.04f,0.04f)
                addL(px+rr,hh+1f,pz-rr, px+rr,dY,pz-rr,    0.04f,0.04f,0.04f)
            }
        }
    }

    // ── Player orb ────────────────────────────────────────────────────────────

    private fun drawOrb(cx: Float, cy: Float, cz: Float, r: Float, mvp: FloatArray) {
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

    private fun addRadButton(h: Float, bx: Float = 0f, bz: Float = -1150f) {
        val baseY = 0f               // sits on ground level
        val topY  = baseY + h
        val r0    = 88f              // outer radius
        val rimW  = 60f              // rim width
        val rimH  =  6f              // rim height above top face
        val sides = 16
        val fog   = 0.06f

        // Colors
        val tR=1.00f; val tG=1.00f; val tB=1.00f   // top face white
        val sR=0.65f; val sG=0.36f; val sB=0.05f   // side walls orange
        val rR=0.98f; val rG=0.62f; val rB=0.14f   // rim lighter orange
        val eR=0.30f; val eG=0.14f; val eB=0.01f   // dark outline

        for (i in 0 until sides) {
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

        // Door — dark purple void on the south face (toward the city)
        val dw = r0 * 0.30f
        val dh = topY * 0.40f
        val doorZ = bz + r0 + 0.5f   // just outside the south pole of the cylinder
        addQ(bx-dw, 0f, doorZ,  bx+dw, 0f, doorZ,  bx+dw, dh, doorZ,  bx-dw, dh, doorZ,
             0.12f, 0.06f, 0.18f, fog=fog)
    }

    // ── Bridge — one segment per completed building, spanning lava ───────────
    // Starts at LAVA_S (just south of north buildings) and extends north in 9 pieces.
    private fun addBridge(pieces: Int) {
        val bw        = 22f          // half-width
        val yDeck     = 5f           // deck height (above lava surface)
        val yRail     = yDeck + 12f  // railing top
        val zStart    = LAVA_S       // -512 (south edge of lava)
        val pieceLen  = abs(LAVA_N - LAVA_S) / 9f  // ~40 units each

        for (i in 0 until pieces.coerceAtMost(9)) {
            val z0 = zStart - i * pieceLen          // more negative = further north
            val z1 = z0 - pieceLen
            val fog = 0.04f

            // Deck (warm wood placeholder)
            addQ(-bw, yDeck, z0,  bw, yDeck, z0,  bw, yDeck, z1,  -bw, yDeck, z1,
                 0.68f, 0.52f, 0.28f, fog = fog)

            // Left side beam
            addQ(-bw, yDeck, z0,  -bw, yDeck, z1,  -bw, yRail, z1,  -bw, yRail, z0,
                 0.50f, 0.38f, 0.18f, fog = fog)
            // Right side beam
            addQ(bw, yDeck, z1,  bw, yDeck, z0,  bw, yRail, z0,  bw, yRail, z1,
                 0.50f, 0.38f, 0.18f, fog = fog)

            // Plank seam lines across the deck
            val planks = 5
            for (p in 0..planks) {
                val zp = z0 - (pieceLen / planks) * p
                addL(-bw, yDeck + 0.8f, zp,  bw, yDeck + 0.8f, zp,  0.35f, 0.25f, 0.10f)
            }
            // Railing lines along the sides
            addL(-bw, yRail, z0,  -bw, yRail, z1,  0.35f, 0.25f, 0.10f)
            addL( bw, yRail, z0,   bw, yRail, z1,  0.35f, 0.25f, 0.10f)
        }
    }

    private fun addTri(x0:Float,y0:Float,z0:Float, x1:Float,y1:Float,z1:Float,
                       x2:Float,y2:Float,z2:Float, r:Float,g:Float,b:Float, fog:Float=0.65f) {
        meshes.add(Mesh(floatArrayOf(x0,y0,z0,x1,y1,z1,x2,y2,z2).toFB(), GLES20.GL_TRIANGLES, 3, r,g,b,1f,fog))
    }
    private fun addQ(x0:Float,y0:Float,z0:Float, x1:Float,y1:Float,z1:Float,
                     x2:Float,y2:Float,z2:Float, x3:Float,y3:Float,z3:Float,
                     r:Float,g:Float,b:Float, a:Float=1f, fog:Float=0.65f) {
        val v = floatArrayOf(x0,y0,z0,x1,y1,z1,x2,y2,z2, x0,y0,z0,x2,y2,z2,x3,y3,z3)
        meshes.add(Mesh(v.toFB(), GLES20.GL_TRIANGLES, 6, r,g,b,a,fog))
    }
    private fun addL(x0:Float,y0:Float,z0:Float, x1:Float,y1:Float,z1:Float,
                     r:Float,g:Float,b:Float) {
        meshes.add(Mesh(floatArrayOf(x0,y0,z0,x1,y1,z1).toFB(), GLES20.GL_LINES, 2, r,g,b, fog=0.70f))
    }

    private fun buildProg(vs:String, fs:String): Int {
        val v=comp(GLES20.GL_VERTEX_SHADER,vs)
        val f=comp(GLES20.GL_FRAGMENT_SHADER,fs)
        val p=GLES20.glCreateProgram()
        GLES20.glAttachShader(p,v); GLES20.glAttachShader(p,f); GLES20.glLinkProgram(p)
        return p
    }
    private fun comp(t:Int, s:String): Int {
        val sh=GLES20.glCreateShader(t)
        GLES20.glShaderSource(sh,s); GLES20.glCompileShader(sh); return sh
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
