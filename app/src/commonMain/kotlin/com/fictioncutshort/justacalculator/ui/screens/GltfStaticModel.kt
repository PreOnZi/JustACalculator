package com.fictioncutshort.justacalculator.ui.screens

import com.fictioncutshort.justacalculator.gl.JsonObj
import com.fictioncutshort.justacalculator.gl.LittleEndian
import com.fictioncutshort.justacalculator.gl.Matrix
import com.fictioncutshort.justacalculator.platform.Assets
import kotlin.math.max
import kotlin.math.sqrt

/**
 * A rigid .glb — meshes only, no skin and no animation.
 *
 * [GltfSkinnedModel] exists next door and reads the same format, but it demands
 * a `skins` array and throws without one. The maze key props have no skin, so
 * they need this instead. The two are kept apart on purpose: the skinned loader
 * is what Building 6's runner depends on, and widening it to cover static
 * models would put that at risk for no gain.
 *
 * Node transforms are baked into the vertices at load. These props are a single
 * node in practice, but a .glb exported with the mesh parented under an armature
 * or an empty would otherwise render at the wrong place, and that failure looks
 * like a missing model rather than a wrong matrix.
 */
class StaticPrimitive(
    /** px,py,pz,nx,ny,nz per vertex — one interleaved buffer per draw call. */
    val interleaved: FloatArray,
    val indices: IntArray,
    /** rgba, straight from pbrMetallicRoughness.baseColorFactor. */
    val baseColor: FloatArray,
) {
    val vertexCount: Int get() = interleaved.size / 6
}

class GltfStaticModel private constructor(
    val primitives: List<StaticPrimitive>,
    /** Centre of the bounding box, so the viewer can frame the model. */
    val center: FloatArray,
    /** Largest bounding-box dimension, for scale-to-units framing. */
    val extent: Float,
) {
    companion object {

        fun load(path: String): GltfStaticModel = parse(Assets.readBytes(path))

        /**
         * Split from [load] so the parser can be exercised without an asset
         * bundle — the transform baking and the accessor walk are the parts
         * that would fail quietly rather than loudly.
         */
        fun parse(bytes: ByteArray): GltfStaticModel {
            val bb = LittleEndian(bytes)
            bb.nextInt() /* magic */; bb.nextInt() /* version */; bb.nextInt() /* length */
            val jsonLen = bb.nextInt(); bb.nextInt() /* JSON chunk type */
            val json = JsonObj.parse(bb.nextBytes(jsonLen).decodeToString())
            bb.nextInt() /* BIN length */; bb.nextInt() /* BIN chunk type */
            val bin = bb.slice()

            val accessors = json.getJSONArray("accessors")
            val views = json.getJSONArray("bufferViews")

            fun compSize(ct: Int) = when (ct) { 5120, 5121 -> 1; 5122, 5123 -> 2; else -> 4 }
            fun ncompOf(type: String) = when (type) {
                "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; "MAT4" -> 16; else -> 1
            }

            // Same accessor walk as the skinned loader: byteStride may be zero
            // (meaning tightly packed) and component types vary per attribute.
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
                            5121 -> (bin.getByte(o).toInt() and 0xFF).toFloat()
                            5120 -> bin.getByte(o).toFloat()
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

            // ── Node world transforms ────────────────────────────────────────
            val nodesArr = json.getJSONArray("nodes")
            val nodeCount = nodesArr.length()
            val parent = IntArray(nodeCount) { -1 }
            val local = Array(nodeCount) { FloatArray(16) }

            for (n in 0 until nodeCount) {
                val node = nodesArr.getJSONObject(n)
                node.optJSONArray("children")?.let { ch ->
                    for (k in 0 until ch.length()) parent[ch.getInt(k)] = n
                }
                val m = local[n]
                val explicit = node.optJSONArray("matrix")
                if (explicit != null) {
                    for (k in 0 until 16) m[k] = explicit.getDouble(k).toFloat()
                } else {
                    val t = FloatArray(3)
                    val r = floatArrayOf(0f, 0f, 0f, 1f)
                    val s = floatArrayOf(1f, 1f, 1f)
                    node.optJSONArray("translation")?.let { for (k in 0 until 3) t[k] = it.getDouble(k).toFloat() }
                    node.optJSONArray("rotation")?.let { for (k in 0 until 4) r[k] = it.getDouble(k).toFloat() }
                    node.optJSONArray("scale")?.let { for (k in 0 until 3) s[k] = it.getDouble(k).toFloat() }
                    val rot = FloatArray(16)
                    quatToMatrix(r, rot)
                    Matrix.setIdentityM(m, 0)
                    Matrix.translateM(m, 0, t[0], t[1], t[2])
                    val tr = FloatArray(16)
                    Matrix.multiplyMM(tr, 0, m, 0, rot, 0)
                    Matrix.scaleM(tr, 0, s[0], s[1], s[2])
                    tr.copyInto(m)
                }
            }

            // Parents before children, so a world matrix is always available.
            val world = Array(nodeCount) { FloatArray(16) }
            val done = BooleanArray(nodeCount)
            fun resolve(n: Int) {
                if (done[n]) return
                val p = parent[n]
                if (p >= 0) {
                    resolve(p)
                    Matrix.multiplyMM(world[n], 0, world[p], 0, local[n], 0)
                } else {
                    local[n].copyInto(world[n])
                }
                done[n] = true
            }
            for (n in 0 until nodeCount) resolve(n)

            // ── Primitives, transformed into model space ─────────────────────
            val meshes = json.getJSONArray("meshes")
            val materials = json.optJSONArray("materials")
            val prims = ArrayList<StaticPrimitive>()

            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

            for (n in 0 until nodeCount) {
                val node = nodesArr.getJSONObject(n)
                if (!node.has("mesh")) continue
                val w = world[n]
                val parr = meshes.getJSONObject(node.getInt("mesh")).getJSONArray("primitives")

                for (pi in 0 until parr.length()) {
                    val prim = parr.getJSONObject(pi)
                    // 4 == TRIANGLES. Points and lines would need a different
                    // draw call and no key model uses them.
                    if (prim.optInt("mode", 4) != 4) continue

                    val attr = prim.getJSONObject("attributes")
                    val pos = readFloats(attr.getInt("POSITION"))
                    val vcount = pos.size / 3
                    val nrm = if (attr.has("NORMAL")) readFloats(attr.getInt("NORMAL")) else FloatArray(pos.size)

                    val inter = FloatArray(vcount * 6)
                    val v = FloatArray(4)
                    val o = FloatArray(4)
                    for (i in 0 until vcount) {
                        v[0] = pos[i * 3]; v[1] = pos[i * 3 + 1]; v[2] = pos[i * 3 + 2]; v[3] = 1f
                        Matrix.multiplyMV(o, 0, w, 0, v, 0)
                        inter[i * 6] = o[0]; inter[i * 6 + 1] = o[1]; inter[i * 6 + 2] = o[2]
                        minX = minOf(minX, o[0]); maxX = max(maxX, o[0])
                        minY = minOf(minY, o[1]); maxY = max(maxY, o[1])
                        minZ = minOf(minZ, o[2]); maxZ = max(maxZ, o[2])

                        // w = 0 so translation is ignored; good enough for the
                        // uniform scales these props use.
                        v[0] = nrm[i * 3]; v[1] = nrm[i * 3 + 1]; v[2] = nrm[i * 3 + 2]; v[3] = 0f
                        Matrix.multiplyMV(o, 0, w, 0, v, 0)
                        val len = sqrt(o[0] * o[0] + o[1] * o[1] + o[2] * o[2])
                        if (len > 1e-6f) {
                            inter[i * 6 + 3] = o[0] / len; inter[i * 6 + 4] = o[1] / len; inter[i * 6 + 5] = o[2] / len
                        } else {
                            inter[i * 6 + 4] = 1f  // degenerate normal — point it up rather than leave it zero
                        }
                    }

                    val indices = if (prim.has("indices")) {
                        val f = readFloats(prim.getInt("indices"))
                        IntArray(f.size) { f[it].toInt() }
                    } else {
                        IntArray(vcount) { it }
                    }

                    val color = floatArrayOf(0.85f, 0.85f, 0.88f, 1f)
                    if (materials != null && prim.has("material")) {
                        materials.getJSONObject(prim.getInt("material"))
                            .optJSONObject("pbrMetallicRoughness")
                            ?.optJSONArray("baseColorFactor")
                            ?.let { bc -> for (k in 0 until minOf(4, bc.length())) color[k] = bc.getDouble(k).toFloat() }
                    }

                    prims.add(StaticPrimitive(inter, indices, color))
                }
            }

            // An empty model would divide by zero when framing; a unit extent
            // just renders nothing rather than crashing the maze.
            val center = if (prims.isEmpty()) FloatArray(3) else
                floatArrayOf((minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f)
            val extent = if (prims.isEmpty()) 1f else
                max(maxX - minX, max(maxY - minY, maxZ - minZ)).coerceAtLeast(1e-4f)

            return GltfStaticModel(prims, center, extent)
        }

        /** Column-major rotation matrix from a glTF (x, y, z, w) quaternion. */
        private fun quatToMatrix(q: FloatArray, m: FloatArray) {
            val x = q[0]; val y = q[1]; val z = q[2]; val w = q[3]
            m[0] = 1f - 2f * (y * y + z * z); m[4] = 2f * (x * y - z * w);       m[8] = 2f * (x * z + y * w);        m[12] = 0f
            m[1] = 2f * (x * y + z * w);      m[5] = 1f - 2f * (x * x + z * z);  m[9] = 2f * (y * z - x * w);        m[13] = 0f
            m[2] = 2f * (x * z - y * w);      m[6] = 2f * (y * z + x * w);       m[10] = 1f - 2f * (x * x + y * y);  m[14] = 0f
            m[3] = 0f;                        m[7] = 0f;                         m[11] = 0f;                         m[15] = 1f
        }
    }
}
