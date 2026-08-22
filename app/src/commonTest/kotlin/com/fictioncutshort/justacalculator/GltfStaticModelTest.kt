package com.fictioncutshort.justacalculator

import com.fictioncutshort.justacalculator.ui.screens.GltfStaticModel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The maze key inspector reads .glb props with this.
 *
 * Every failure mode here is silent: a wrong accessor stride reads plausible
 * garbage, an un-baked node transform puts the key just off camera, and a
 * missed material leaves it default grey. All of those look like "the model did
 * not load" rather than like a parser bug, so they are pinned here against a
 * .glb built byte by byte with known answers.
 */
class GltfStaticModelTest {

    /** One triangle under a node that translates by (1,0,0) and scales by 2. */
    private fun buildGlb(baseColour: String = "[0.25,0.5,0.75,1]"): ByteArray {
        val positions = floatArrayOf(
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f,
        )
        val normals = floatArrayOf(
            0f, 0f, 1f,
            0f, 0f, 1f,
            0f, 0f, 1f,
        )
        val indices = intArrayOf(0, 1, 2)

        val bin = ArrayList<Byte>()
        fun putFloat(v: Float) {
            val b = v.toRawBits()
            bin.add((b and 0xFF).toByte()); bin.add(((b shr 8) and 0xFF).toByte())
            bin.add(((b shr 16) and 0xFF).toByte()); bin.add(((b shr 24) and 0xFF).toByte())
        }
        fun putShort(v: Int) {
            bin.add((v and 0xFF).toByte()); bin.add(((v shr 8) and 0xFF).toByte())
        }
        for (f in positions) putFloat(f)          // offset  0, 36 bytes
        for (f in normals) putFloat(f)            // offset 36, 36 bytes
        for (i in indices) putShort(i)            // offset 72,  6 bytes — unsigned short on purpose
        while (bin.size % 4 != 0) bin.add(0)

        // Node has translation + scale, so the test fails if either is dropped.
        val json = """
            {"asset":{"version":"2.0"},
             "scenes":[{"nodes":[0]}],
             "nodes":[{"mesh":0,"translation":[1,0,0],"scale":[2,2,2]}],
             "meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1},"indices":2,"material":0}]}],
             "materials":[{"pbrMetallicRoughness":{"baseColorFactor":$baseColour}}],
             "accessors":[
               {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
               {"bufferView":1,"componentType":5126,"count":3,"type":"VEC3"},
               {"bufferView":2,"componentType":5123,"count":3,"type":"SCALAR"}],
             "bufferViews":[
               {"buffer":0,"byteOffset":0,"byteLength":36},
               {"buffer":0,"byteOffset":36,"byteLength":36},
               {"buffer":0,"byteOffset":72,"byteLength":6}],
             "buffers":[{"byteLength":${bin.size}}]}
        """.trimIndent().replace("\n", "").replace(" ", "")

        val jsonBytes = json.encodeToByteArray().toMutableList()
        while (jsonBytes.size % 4 != 0) jsonBytes.add(' '.code.toByte())  // JSON pads with spaces

        val out = ArrayList<Byte>()
        fun putInt(v: Int) {
            out.add((v and 0xFF).toByte()); out.add(((v shr 8) and 0xFF).toByte())
            out.add(((v shr 16) and 0xFF).toByte()); out.add(((v shr 24) and 0xFF).toByte())
        }
        putInt(0x46546C67)                                   // "glTF"
        putInt(2)                                            // version
        putInt(12 + 8 + jsonBytes.size + 8 + bin.size)       // total length
        putInt(jsonBytes.size); putInt(0x4E4F534A)           // JSON chunk
        out.addAll(jsonBytes)
        putInt(bin.size); putInt(0x004E4942)                 // BIN chunk
        out.addAll(bin)
        return out.toByteArray()
    }

    private fun assertNear(expected: Float, actual: Float, tag: String, tol: Float = 1e-4f) {
        assertTrue(abs(expected - actual) < tol, "$tag: expected ~$expected, was $actual")
    }

    @Test
    fun parsesGeometry() {
        val m = GltfStaticModel.parse(buildGlb())
        assertEquals(1, m.primitives.size, "primitive count")
        val p = m.primitives[0]
        assertEquals(3, p.vertexCount, "vertex count")
        assertEquals(3, p.indices.size, "index count")
        assertEquals(listOf(0, 1, 2), p.indices.toList(), "unsigned-short indices")
    }

    /** Node translation and scale must be baked in, or the key renders off-centre. */
    @Test
    fun bakesTheNodeTransform() {
        val p = GltfStaticModel.parse(buildGlb()).primitives[0]
        // (0,0,0) scaled by 2 then moved +1 in x
        assertNear(1f, p.interleaved[0], "v0.x"); assertNear(0f, p.interleaved[1], "v0.y")
        // (1,0,0) -> x = 1*2 + 1 = 3
        assertNear(3f, p.interleaved[6], "v1.x")
        // (0,1,0) -> y = 1*2 = 2
        assertNear(1f, p.interleaved[12], "v2.x"); assertNear(2f, p.interleaved[13], "v2.y")
    }

    /** Normals must be rotated but not translated, and must stay unit length. */
    @Test
    fun normalsSurviveTheTransform() {
        val p = GltfStaticModel.parse(buildGlb()).primitives[0]
        for (i in 0 until 3) {
            assertNear(0f, p.interleaved[i * 6 + 3], "n$i.x")
            assertNear(0f, p.interleaved[i * 6 + 4], "n$i.y")
            assertNear(1f, p.interleaved[i * 6 + 5], "n$i.z — translation must not leak in")
        }
    }

    /** The viewer frames the model from these two, so a wrong box mis-scales it. */
    @Test
    fun computesBoundsInBakedSpace() {
        val m = GltfStaticModel.parse(buildGlb())
        assertNear(2f, m.center[0], "centre x")   // (1 + 3) / 2
        assertNear(1f, m.center[1], "centre y")   // (0 + 2) / 2
        assertNear(0f, m.center[2], "centre z")
        assertNear(2f, m.extent, "longest side")  // x spans 1..3, y spans 0..2
    }

    @Test
    fun readsBaseColour() {
        // glTF stores baseColorFactor in LINEAR space, and the renderer writes
        // straight to a non-sRGB framebuffer, so the loader converts on the way
        // out — the same thing ObjLoader does to an MTL's Kd.
        //
        // This test previously asserted the raw 0.25/0.5/0.75 passed through
        // untouched, which is what left the maze keys near-black: their darkest
        // materials are authored around 0.004-0.055 linear, and unconverted they
        // arrive on screen at that value instead of roughly four times it.
        //
        // Expected values are srgb(x) = 1.055·x^(1/2.4) − 0.055.
        val c = GltfStaticModel.parse(buildGlb()).primitives[0].baseColor
        assertNear(0.537099f, c[0], "r"); assertNear(0.735357f, c[1], "g")
        assertNear(0.880825f, c[2], "b")
        // Alpha is not a colour and must survive unconverted.
        assertNear(1f, c[3], "a")
    }

    @Test
    fun baseColourConversionIsMonotonicAndClamped() {
        // Guards the two ends: black stays black, white stays white. A conversion
        // that shifted either would wash out every model in the game.
        val black = GltfStaticModel.parse(buildGlb(baseColour = "[0,0,0,1]")).primitives[0].baseColor
        assertNear(0f, black[0], "black r")
        val white = GltfStaticModel.parse(buildGlb(baseColour = "[1,1,1,1]")).primitives[0].baseColor
        assertNear(1f, white[0], "white r")
    }
}
