package com.fictioncutshort.justacalculator

import com.fictioncutshort.justacalculator.ui.screens.GltfStaticModel
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Parses the actual key props, not a synthetic stand-in.
 *
 * [GltfStaticModelTest] pins the parser's arithmetic against a .glb built by
 * hand; this pins it against what the artist actually exported — two primitives
 * per mesh, a TEXCOORD_0 the loader ignores, and whatever the exporter decides
 * to do with strides next time the models are regenerated.
 *
 * Android-only because it reaches the files through the filesystem. The shared
 * loader goes through the Assets seam at runtime, which no unit test has.
 */
class RealKeyModelTest {

    private val keysDir = File("src/commonMain/assets/models/keys")

    private fun keyFiles(): List<File> {
        val files = keysDir.listFiles { f: File -> f.name.endsWith(".glb") }?.sorted().orEmpty()
        if (files.isEmpty()) fail("no .glb keys found at ${keysDir.absolutePath}")
        return files
    }

    /** Every key the maze can show must load — one bad file is a blank panel. */
    @Test
    fun everyKeyModelParses() {
        for (f in keyFiles()) {
            val m = try {
                GltfStaticModel.parse(f.readBytes())
            } catch (e: Throwable) {
                fail("${f.name} failed to parse: $e")
            }
            assertTrue(m.primitives.isNotEmpty(), "${f.name}: no primitives")
            assertTrue(m.extent > 0f, "${f.name}: zero extent would divide by zero when framing")
            for (p in m.primitives) {
                assertTrue(p.vertexCount > 0, "${f.name}: empty primitive")
                assertTrue(p.indices.isNotEmpty(), "${f.name}: no indices")
                assertTrue(
                    p.indices.all { it in 0 until p.vertexCount },
                    "${f.name}: index out of range — would read past the vertex buffer",
                )
            }
        }
    }

    /** Known-good numbers for one file, so a silently wrong accessor walk shows up. */
    @Test
    fun mazeKey1MatchesItsExportedCounts() {
        val m = GltfStaticModel.parse(File(keysDir, "MazeKey1.glb").readBytes())
        val verts = m.primitives.sumOf { it.vertexCount }
        val idx = m.primitives.sumOf { it.indices.size }
        assertTrue(m.primitives.size == 2, "expected 2 primitives, got ${m.primitives.size}")
        assertTrue(verts == 731, "expected 731 vertices, got $verts")
        assertTrue(idx == 1176, "expected 1176 indices, got $idx")
    }

    /** The two materials differ; identical colours would mean the material index is ignored. */
    @Test
    fun primitivesKeepTheirOwnColours() {
        val m = GltfStaticModel.parse(File(keysDir, "MazeKey1.glb").readBytes())
        val a = m.primitives[0].baseColor
        val b = m.primitives[1].baseColor
        assertTrue(
            !a.contentEquals(b),
            "both primitives came back ${a.toList()} — the material index is being dropped",
        )
    }

    /** Normals must be unit length, or the lighting goes dark or blows out. */
    @Test
    fun normalsAreNormalised() {
        for (f in keyFiles()) {
            val m = GltfStaticModel.parse(f.readBytes())
            for (p in m.primitives) {
                for (i in 0 until p.vertexCount) {
                    val x = p.interleaved[i * 6 + 3]
                    val y = p.interleaved[i * 6 + 4]
                    val z = p.interleaved[i * 6 + 5]
                    val len = kotlin.math.sqrt(x * x + y * y + z * z)
                    assertTrue(
                        kotlin.math.abs(len - 1f) < 1e-3f,
                        "${f.name} vertex $i: normal length $len",
                    )
                }
            }
        }
    }
}
