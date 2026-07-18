package com.fictioncutshort.justacalculator.ui.screens

import android.content.res.AssetManager
import android.opengl.Matrix
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

// ═══════════════════════════════════════════════════════════════════════════════
//  GLTF / GLB SKINNED MODEL  (Building 6 — 3D crowd-runner)
//
//  A minimal loader for a single skinned glTF 2.0 binary (.glb): one skeleton,
//  one or more material primitives, and a set of named animation clips. It does
//  NOT aim to be a general glTF importer — just enough to drive `character.glb`
//  (65 joints, clips: run / boxing / stairs_walk / crush_death / …).
//
//  Split in two: parsing + per-frame skinning math live here (no OpenGL); the
//  GLES3 upload + skinning shader live in Building6Runner.kt. `jointMatrices()`
//  returns a flat column-major FloatArray(jointCount*16) ready for the bone UBO.
//
//  Matrices are column-major (OpenGL convention) so android.opengl.Matrix and
//  the GL UBO consume them directly.
// ═══════════════════════════════════════════════════════════════════════════════

/** One material primitive: interleaved-free attribute arrays + index buffer. */
class GltfPrimitive(
    val positions: FloatArray,   // vec3 * vertCount
    val normals: FloatArray,     // vec3 * vertCount
    val joints: FloatArray,      // vec4 * vertCount (joint indices as floats)
    val weights: FloatArray,     // vec4 * vertCount
    val indices: IntArray,
    val baseColor: FloatArray,   // rgba
)

class GltfSkinnedModel private constructor(
    val primitives: List<GltfPrimitive>,
    val jointCount: Int,
    private val nodeCount: Int,
    private val parent: IntArray,
    private val order: IntArray,                 // node indices, parents before children
    private val baseT: Array<FloatArray>,        // [node] -> vec3
    private val baseR: Array<FloatArray>,        // [node] -> quat xyzw
    private val baseS: Array<FloatArray>,        // [node] -> vec3
    private val hasMatrix: BooleanArray,
    private val baseMatrix: Array<FloatArray?>,  // [node] -> mat4 or null
    private val skinJoints: IntArray,            // [jointSlot] -> node index
    private val invBind: Array<FloatArray>,      // [jointSlot] -> mat4
    private val meshNodeInvGlobal: FloatArray,   // inverse(global of the skinned mesh node)
    val clips: List<Clip>,
) {
    val clipNames: List<String> get() = clips.map { it.name }
    fun clipIndex(name: String): Int = clips.indexOfFirst { it.name == name }.coerceAtLeast(0)
    fun clipDuration(i: Int): Float = clips[i].duration

    class Sampler(val times: FloatArray, val values: FloatArray, val ncomp: Int, val step: Boolean)
    class Channel(val node: Int, val path: Int, val sampler: Sampler) // path 0=T,1=R,2=S
    class Clip(val name: String, val channels: List<Channel>, val duration: Float)

    // ── Scratch buffers reused every frame (single-threaded GL use). ──
    private val curT = Array(nodeCount) { FloatArray(3) }
    private val curR = Array(nodeCount) { FloatArray(4) }
    private val curS = Array(nodeCount) { FloatArray(3) }
    private val local = Array(nodeCount) { FloatArray(16) }
    private val global = Array(nodeCount) { FloatArray(16) }
    private val out = FloatArray(jointCount * 16)
    private val tmp = FloatArray(16)

    /** Column-major FloatArray(jointCount*16) of skinning matrices for [clipIdx]
     *  sampled at [timeSec] (looped over the clip duration). */
    fun jointMatrices(clipIdx: Int, timeSec: Float): FloatArray {
        // 1. reset every node to its base pose
        for (n in 0 until nodeCount) {
            System.arraycopy(baseT[n], 0, curT[n], 0, 3)
            System.arraycopy(baseR[n], 0, curR[n], 0, 4)
            System.arraycopy(baseS[n], 0, curS[n], 0, 3)
        }
        // 2. apply the clip's channels at this time
        val clip = clips[clipIdx]
        val t = if (clip.duration > 0f) timeSec % clip.duration else 0f
        for (ch in clip.channels) {
            val s = ch.sampler
            when (ch.path) {
                0 -> sampleVec(s, t, curT[ch.node], 3)
                2 -> sampleVec(s, t, curS[ch.node], 3)
                1 -> sampleQuat(s, t, curR[ch.node])
            }
        }
        // 3. local matrices
        for (n in 0 until nodeCount) {
            if (hasMatrix[n]) {
                System.arraycopy(baseMatrix[n]!!, 0, local[n], 0, 16)
            } else {
                composeTRS(local[n], curT[n], curR[n], curS[n])
            }
        }
        // 4. global matrices (parents already processed thanks to `order`)
        for (n in order) {
            val p = parent[n]
            if (p < 0) System.arraycopy(local[n], 0, global[n], 0, 16)
            else Matrix.multiplyMM(global[n], 0, global[p], 0, local[n], 0)
        }
        // 5. skinning matrices = meshInvGlobal * jointGlobal * inverseBind
        for (j in 0 until jointCount) {
            Matrix.multiplyMM(tmp, 0, global[skinJoints[j]], 0, invBind[j], 0)
            Matrix.multiplyMM(out, j * 16, meshNodeInvGlobal, 0, tmp, 0)
        }
        return out
    }

    private fun composeTRS(dst: FloatArray, t: FloatArray, q: FloatArray, s: FloatArray) {
        quatToMatrix(dst, q)
        // scale columns
        for (i in 0 until 3) { dst[i] *= s[0]; dst[4 + i] *= s[1]; dst[8 + i] *= s[2] }
        dst[12] = t[0]; dst[13] = t[1]; dst[14] = t[2]
    }

    private fun sampleVec(s: Sampler, t: Float, dst: FloatArray, n: Int) {
        val seg = segment(s.times, t)
        val i0 = seg shr 16; val i1 = seg and 0xFFFF
        val a = i0 * s.ncomp; val b = i1 * s.ncomp
        if (s.step || i0 == i1) { for (c in 0 until n) dst[c] = s.values[a + c]; return }
        val f = lerpFactor(s.times, i0, i1, t)
        for (c in 0 until n) dst[c] = s.values[a + c] + (s.values[b + c] - s.values[a + c]) * f
    }

    private fun sampleQuat(s: Sampler, t: Float, dst: FloatArray) {
        val seg = segment(s.times, t)
        val i0 = seg shr 16; val i1 = seg and 0xFFFF
        val a = i0 * 4; val b = i1 * 4
        if (s.step || i0 == i1) { for (c in 0 until 4) dst[c] = s.values[a + c]; return }
        val f = lerpFactor(s.times, i0, i1, t)
        slerp(s.values, a, s.values, b, f, dst)
    }

    companion object {
        // ── Loading ──────────────────────────────────────────────────────────
        fun load(assets: AssetManager, path: String): GltfSkinnedModel {
            val bytes = assets.open(path).use { it.readBytes() }
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            bb.int /* magic */; bb.int /* version */; bb.int /* length */
            val jsonLen = bb.int; bb.int /* JSON type */
            val jsonBytes = ByteArray(jsonLen); bb.get(jsonBytes)
            val json = JSONObject(String(jsonBytes, Charsets.UTF_8))
            bb.int /* BIN length */; bb.int /* BIN type */
            val binStart = bb.position()
            val bin = ByteBuffer.wrap(bytes, binStart, bytes.size - binStart)
                .slice().order(ByteOrder.LITTLE_ENDIAN)

            val accessors = json.getJSONArray("accessors")
            val views = json.getJSONArray("bufferViews")

            fun compSize(ct: Int) = when (ct) { 5120, 5121 -> 1; 5122, 5123 -> 2; else -> 4 }
            fun ncompOf(type: String) = when (type) {
                "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; "MAT4" -> 16; else -> 1
            }

            fun readFloats(ai: Int): FloatArray {
                val acc = accessors.getJSONObject(ai)
                val ct = acc.getInt("componentType")
                val nc = ncompOf(acc.getString("type"))
                val count = acc.getInt("count")
                val normalized = acc.optBoolean("normalized", false)
                val bv = views.getJSONObject(acc.getInt("bufferView"))
                val base = bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
                val cs = compSize(ct)
                val stride = bv.optInt("byteStride", 0).let { if (it > 0) it else nc * cs }
                val out = FloatArray(count * nc)
                for (e in 0 until count) {
                    val p = base + e * stride
                    for (c in 0 until nc) {
                        val o = p + c * cs
                        var v = when (ct) {
                            5126 -> bin.getFloat(o)
                            5125 -> (bin.getInt(o).toLong() and 0xFFFFFFFFL).toFloat()
                            5123 -> (bin.getShort(o).toInt() and 0xFFFF).toFloat()
                            5122 -> bin.getShort(o).toFloat()
                            5121 -> (bin.get(o).toInt() and 0xFF).toFloat()
                            5120 -> bin.get(o).toFloat()
                            else -> 0f
                        }
                        if (normalized) v = when (ct) {
                            5121 -> v / 255f; 5123 -> v / 65535f
                            5120 -> (v / 127f).coerceAtLeast(-1f); 5122 -> (v / 32767f).coerceAtLeast(-1f)
                            else -> v
                        }
                        out[e * nc + c] = v
                    }
                }
                return out
            }
            fun readInts(ai: Int): IntArray {
                val f = readFloats(ai); return IntArray(f.size) { f[it].toInt() }
            }

            // ── Primitives (combine the mesh node's mesh) ──
            val meshes = json.getJSONArray("meshes")
            val materials = json.optJSONArray("materials")
            val prims = ArrayList<GltfPrimitive>()
            for (mi in 0 until meshes.length()) {
                val parr = meshes.getJSONObject(mi).getJSONArray("primitives")
                for (pi in 0 until parr.length()) {
                    val prim = parr.getJSONObject(pi)
                    val attr = prim.getJSONObject("attributes")
                    val pos = readFloats(attr.getInt("POSITION"))
                    val nrm = if (attr.has("NORMAL")) readFloats(attr.getInt("NORMAL")) else FloatArray(pos.size)
                    val jnt = if (attr.has("JOINTS_0")) readFloats(attr.getInt("JOINTS_0")) else FloatArray(pos.size / 3 * 4)
                    val wgt = if (attr.has("WEIGHTS_0")) readFloats(attr.getInt("WEIGHTS_0")) else FloatArray(pos.size / 3 * 4)
                    val idx = readInts(prim.getInt("indices"))
                    val color = floatArrayOf(0.85f, 0.85f, 0.88f, 1f)
                    if (materials != null && prim.has("material")) {
                        val mat = materials.getJSONObject(prim.getInt("material"))
                        mat.optJSONObject("pbrMetallicRoughness")?.optJSONArray("baseColorFactor")?.let { bc ->
                            for (k in 0 until minOf(4, bc.length())) color[k] = bc.getDouble(k).toFloat()
                        }
                    }
                    prims.add(GltfPrimitive(pos, nrm, jnt, wgt, idx, color))
                }
            }

            // ── Node hierarchy ──
            val nodesArr = json.getJSONArray("nodes")
            val nodeCount = nodesArr.length()
            val parent = IntArray(nodeCount) { -1 }
            val baseT = Array(nodeCount) { floatArrayOf(0f, 0f, 0f) }
            val baseR = Array(nodeCount) { floatArrayOf(0f, 0f, 0f, 1f) }
            val baseS = Array(nodeCount) { floatArrayOf(1f, 1f, 1f) }
            val hasMatrix = BooleanArray(nodeCount)
            val baseMatrix = arrayOfNulls<FloatArray>(nodeCount)
            for (n in 0 until nodeCount) {
                val node = nodesArr.getJSONObject(n)
                node.optJSONArray("children")?.let { ch ->
                    for (k in 0 until ch.length()) parent[ch.getInt(k)] = n
                }
                node.optJSONArray("matrix")?.let { m ->
                    hasMatrix[n] = true
                    baseMatrix[n] = FloatArray(16) { m.getDouble(it).toFloat() }
                }
                node.optJSONArray("translation")?.let { for (k in 0 until 3) baseT[n][k] = it.getDouble(k).toFloat() }
                node.optJSONArray("rotation")?.let { for (k in 0 until 4) baseR[n][k] = it.getDouble(k).toFloat() }
                node.optJSONArray("scale")?.let { for (k in 0 until 3) baseS[n][k] = it.getDouble(k).toFloat() }
            }
            // parents-before-children ordering
            val order = IntArray(nodeCount)
            run {
                val visited = BooleanArray(nodeCount); var w = 0
                fun visit(n: Int) {
                    if (visited[n]) return
                    val p = parent[n]; if (p >= 0 && !visited[p]) visit(p)
                    visited[n] = true; order[w++] = n
                }
                for (n in 0 until nodeCount) visit(n)
            }

            // ── Skin ──
            val skin = json.getJSONArray("skins").getJSONObject(0)
            val jointsArr = skin.getJSONArray("joints")
            val jointCount = jointsArr.length()
            val skinJoints = IntArray(jointCount) { jointsArr.getInt(it) }
            val ibmFlat = readFloats(skin.getInt("inverseBindMatrices"))
            val invBind = Array(jointCount) { j -> FloatArray(16) { ibmFlat[j * 16 + it] } }

            // mesh node global inverse — find the node that references a mesh and
            // is the skinned one. Assume identity if it resolves to identity.
            val meshInv = FloatArray(16); Matrix.setIdentityM(meshInv, 0)

            // ── Animations ──
            val clips = ArrayList<Clip>()
            json.optJSONArray("animations")?.let { anims ->
                for (ai in 0 until anims.length()) {
                    val anim = anims.getJSONObject(ai)
                    val name = anim.optString("name", "clip$ai")
                    val samplersJson = anim.getJSONArray("samplers")
                    val samplers = ArrayList<Sampler>()
                    for (si in 0 until samplersJson.length()) {
                        val sj = samplersJson.getJSONObject(si)
                        val times = readFloats(sj.getInt("input"))
                        val outAcc = sj.getInt("output")
                        val nc = ncompOf(accessors.getJSONObject(outAcc).getString("type"))
                        var vals = readFloats(outAcc)
                        val interp = sj.optString("interpolation", "LINEAR")
                        if (interp == "CUBICSPLINE") {
                            // keep only the middle (value) of each in/value/out triple
                            val keys = times.size
                            val packed = FloatArray(keys * nc)
                            for (k in 0 until keys) for (c in 0 until nc)
                                packed[k * nc + c] = vals[(k * 3 + 1) * nc + c]
                            vals = packed
                        }
                        samplers.add(Sampler(times, vals, nc, interp == "STEP"))
                    }
                    val channelsJson = anim.getJSONArray("channels")
                    val channels = ArrayList<Channel>()
                    var dur = 0f
                    for (ci in 0 until channelsJson.length()) {
                        val cj = channelsJson.getJSONObject(ci)
                        val target = cj.getJSONObject("target")
                        if (!target.has("node")) continue
                        val node = target.getInt("node")
                        val path = when (target.getString("path")) {
                            "translation" -> 0; "rotation" -> 1; "scale" -> 2; else -> -1
                        }
                        if (path < 0) continue
                        val s = samplers[cj.getInt("sampler")]
                        if (s.times.isNotEmpty()) dur = maxOf(dur, s.times.last())
                        channels.add(Channel(node, path, s))
                    }
                    clips.add(Clip(name, channels, dur))
                }
            }

            return GltfSkinnedModel(
                prims, jointCount, nodeCount, parent, order,
                baseT, baseR, baseS, hasMatrix, baseMatrix,
                skinJoints, invBind, meshInv, clips,
            )
        }

        // ── Small math helpers ────────────────────────────────────────────────
        private fun segment(times: FloatArray, t: Float): Int {
            if (times.isEmpty()) return 0
            if (t <= times[0]) return 0
            if (t >= times[times.size - 1]) { val l = times.size - 1; return (l shl 16) or l }
            var i = 0
            while (i < times.size - 1 && times[i + 1] < t) i++
            return (i shl 16) or (i + 1)
        }
        private fun lerpFactor(times: FloatArray, i0: Int, i1: Int, t: Float): Float {
            val span = times[i1] - times[i0]
            return if (span <= 1e-6f) 0f else ((t - times[i0]) / span).coerceIn(0f, 1f)
        }
        private fun quatToMatrix(m: FloatArray, q: FloatArray) {
            val x = q[0]; val y = q[1]; val z = q[2]; val w = q[3]
            val xx = x * x; val yy = y * y; val zz = z * z
            val xy = x * y; val xz = x * z; val yz = y * z
            val wx = w * x; val wy = w * y; val wz = w * z
            m[0] = 1 - 2 * (yy + zz); m[1] = 2 * (xy + wz);     m[2] = 2 * (xz - wy);     m[3] = 0f
            m[4] = 2 * (xy - wz);     m[5] = 1 - 2 * (xx + zz); m[6] = 2 * (yz + wx);     m[7] = 0f
            m[8] = 2 * (xz + wy);     m[9] = 2 * (yz - wx);     m[10] = 1 - 2 * (xx + yy); m[11] = 0f
            m[12] = 0f; m[13] = 0f; m[14] = 0f; m[15] = 1f
        }
        private fun slerp(a: FloatArray, ao: Int, b: FloatArray, bo: Int, t: Float, dst: FloatArray) {
            var ax = a[ao]; var ay = a[ao + 1]; var az = a[ao + 2]; var aw = a[ao + 3]
            val bx = b[bo]; val by = b[bo + 1]; val bz = b[bo + 2]; val bw = b[bo + 3]
            var dot = ax * bx + ay * by + az * bz + aw * bw
            var nbx = bx; var nby = by; var nbz = bz; var nbw = bw
            if (dot < 0f) { dot = -dot; nbx = -bx; nby = -by; nbz = -bz; nbw = -bw }
            if (dot > 0.9995f) {
                dst[0] = ax + (nbx - ax) * t; dst[1] = ay + (nby - ay) * t
                dst[2] = az + (nbz - az) * t; dst[3] = aw + (nbw - aw) * t
            } else {
                val theta0 = Math.acos(dot.toDouble())
                val theta = theta0 * t
                val sin0 = Math.sin(theta0)
                val s0 = (Math.sin(theta0 - theta) / sin0).toFloat()
                val s1 = (Math.sin(theta) / sin0).toFloat()
                dst[0] = ax * s0 + nbx * s1; dst[1] = ay * s0 + nby * s1
                dst[2] = az * s0 + nbz * s1; dst[3] = aw * s0 + nbw * s1
            }
            val len = Math.sqrt((dst[0] * dst[0] + dst[1] * dst[1] + dst[2] * dst[2] + dst[3] * dst[3]).toDouble()).toFloat()
            if (len > 1e-6f) { dst[0] /= len; dst[1] /= len; dst[2] /= len; dst[3] /= len }
        }
    }
}
