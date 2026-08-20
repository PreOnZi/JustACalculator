package com.fictioncutshort.justacalculator.ui.screens

import kotlin.math.pow
import com.fictioncutshort.justacalculator.platform.Assets
import com.fictioncutshort.justacalculator.platform.readLines


// Minimal Wavefront OBJ + MTL loader.
//
// Returns the mesh grouped by material so the renderer can apply a different
// uniform color per group (e.g. dark camera housing vs red lens dot). Faces
// are triangulated as a simple fan; vertex normals are ignored.
//
// UVs and textures come through for the materials that have them: a material with
// a `map_Kd` gets its image name and its faces' UVs, and the renderer draws that
// group textured instead of in a flat colour. Everything else behaves exactly as
// before — [uvs] is empty and [texture] is null, and those groups never touch the
// textured path.
//
// Output: list of ObjGroup, each with the material's diffuse color (Kd), a flat
// float array of triangle positions ready to upload to GL, and (for textured
// materials) the matching UVs.

data class ObjGroup(
    val r: Float, val g: Float, val b: Float,
    val materialName: String,
    val verts: FloatArray,
    /** 2 floats per vertex of [verts], or empty if this material has no image. */
    val uvs: FloatArray = FloatArray(0),
    /** The `map_Kd` image file name (no path), or null for a flat-coloured material. */
    val texture: String? = null,
)

// Axis-aligned bounds of one `o` object, in the model's own coordinates.
data class ObjBounds(
    val name: String,
    val minX: Float, val maxX: Float,
    val minY: Float, val maxY: Float,
    val minZ: Float, val maxZ: Float,
)

/**
 * Whitespace splitter, compiled once.
 *
 * This used to be written inline as `line.split(Regex("\\s+"))`, which builds and
 * compiles a fresh pattern for every line read — 232,741 of them for
 * mutebutton.obj alone. Hoisting it keeps the matching behaviour byte-for-byte
 * identical (runs of spaces and tabs both still split) while compiling once.
 */
private val WS = Regex("\\s+")

/**
 * Growable primitive float buffer.
 *
 * `MutableList<Float>` boxes every coordinate into a heap object; a city load
 * pushed roughly 1.1 million of them through the fan-triangulation path, all of
 * it garbage the moment `toFloatArray()` copied it out. This holds the floats
 * flat and grows by doubling.
 */
private class FloatList(initialCapacity: Int = 1024) {
    private var buf = FloatArray(initialCapacity)
    var size = 0
        private set

    fun add(v: Float) {
        if (size == buf.size) buf = buf.copyOf(if (buf.size == 0) 16 else buf.size * 2)
        buf[size++] = v
    }

    operator fun get(i: Int): Float = buf[i]
    fun isEmpty(): Boolean = size == 0
    fun isNotEmpty(): Boolean = size != 0
    fun clear() { size = 0 }
    fun toFloatArray(): FloatArray = buf.copyOf(size)
}

object ObjLoader {

    fun load(objPath: String, mtlPath: String? = null): List<ObjGroup> {
        val materials = if (mtlPath != null) parseMtl(mtlPath) else emptyMap()
        return parseObj(objPath, materials, collectBounds = false).groups
    }

    // Per-object bounding boxes, keyed off the OBJ's `o` markers. [load] groups by
    // material and so loses object identity; this keeps it, which is what the
    // renderer needs to turn a scene model's props into collision footprints.
    fun loadBounds(objPath: String): List<ObjBounds> =
        parseObj(objPath, emptyMap(), collectBounds = true).bounds

    /**
     * Geometry and per-object bounds from a **single** read and parse.
     *
     * Calling [load] and [loadBounds] separately re-reads, re-decodes and
     * re-parses the whole file — 8.6 MB twice over for mutebutton.obj, on the
     * city's critical path. The two want different groupings (material vs `o`
     * marker), so neither output can be derived from the other after the fact;
     * collecting both in one pass is what removes the duplicate work.
     */
    fun loadWithBounds(objPath: String, mtlPath: String? = null): Pair<List<ObjGroup>, List<ObjBounds>> {
        val materials = if (mtlPath != null) parseMtl(mtlPath) else emptyMap()
        val parsed = parseObj(objPath, materials, collectBounds = true)
        return parsed.groups to parsed.bounds
    }

    private class ParsedObj(val groups: List<ObjGroup>, val bounds: List<ObjBounds>)

    private data class Mtl(val r: Float, val g: Float, val b: Float, val texture: String? = null)

    private fun linearToSrgb(c: Float): Float {
        val x = c.coerceIn(0f, 1f)
        return if (x <= 0.0031308f) x * 12.92f
               else 1.055f * x.toDouble().pow(1.0 / 2.4).toFloat() - 0.055f
    }

    private fun parseMtl(path: String): Map<String, Mtl> {
        val out = mutableMapOf<String, Mtl>()
        var cur: String? = null
        var r = 1f; var g = 1f; var b = 1f
        var tex: String? = null
        Assets.readLines(path).let { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split(Regex("\\s+"))
                when (parts[0]) {
                    "newmtl" -> {
                        cur?.let { out[it] = Mtl(r, g, b, tex) }
                        cur = parts.getOrNull(1)
                        r = 1f; g = 1f; b = 1f; tex = null
                    }
                    // The image the material paints with. Exported with path_mode
                    // STRIP, so it's a bare file name — but tolerate a path anyway,
                    // since a hand-edited MTL will happily carry one.
                    "map_Kd" -> if (parts.size >= 2) {
                        tex = parts.last().substringAfterLast('/').substringAfterLast('\\')
                    }
                    // Blender writes Kd in LINEAR space. Handing those numbers
                    // straight to GL treats them as sRGB, which is why authored
                    // colours came out far darker in game than in Blender (a mid
                    // brown of 0.05 reads as near-black). Convert on the way in, so
                    // every model matches what was painted.
                    "Kd" -> if (parts.size >= 4) {
                        r = linearToSrgb(parts[1].toFloat())
                        g = linearToSrgb(parts[2].toFloat())
                        b = linearToSrgb(parts[3].toFloat())
                    }
                }
            }
        }
        cur?.let { out[it] = Mtl(r, g, b, tex) }
        return out
    }

    private fun parseObj(
        path: String,
        materials: Map<String, Mtl>,
        collectBounds: Boolean,
    ): ParsedObj {
        val positions = FloatList()
        val texCoords = FloatList()
        val groups = mutableListOf<ObjGroup>()
        val curVerts = FloatList()
        val curUvs = FloatList()
        var curR = 0.5f; var curG = 0.5f; var curB = 0.5f
        var curName = ""
        var curTex: String? = null

        // Per-`o` bounds, accumulated alongside the geometry when asked for.
        val boundsOut = mutableListOf<ObjBounds>()
        var bName = ""
        var mnX = Float.MAX_VALUE; var mxX = -Float.MAX_VALUE
        var mnY = Float.MAX_VALUE; var mxY = -Float.MAX_VALUE
        var mnZ = Float.MAX_VALUE; var mxZ = -Float.MAX_VALUE
        fun flushBounds() {
            if (mnX <= mxX) boundsOut.add(ObjBounds(bName, mnX, mxX, mnY, mxY, mnZ, mxZ))
            mnX = Float.MAX_VALUE; mxX = -Float.MAX_VALUE
            mnY = Float.MAX_VALUE; mxY = -Float.MAX_VALUE
            mnZ = Float.MAX_VALUE; mxZ = -Float.MAX_VALUE
        }

        fun flush() {
            if (curVerts.isNotEmpty()) {
                // Only hand back UVs if there is an image to map with them, and only
                // if every vertex got one — a half-filled UV array would misalign the
                // whole group.
                val uvs = if (curTex != null && curUvs.size == curVerts.size / 3 * 2)
                    curUvs.toFloatArray() else FloatArray(0)
                groups.add(ObjGroup(curR, curG, curB, curName, curVerts.toFloatArray(),
                    uvs, if (uvs.isEmpty()) null else curTex))
                curVerts.clear()
                curUvs.clear()
            }
        }

        // lineSequence() rather than readLines(): the latter materialises every
        // line of the file as a live String at once — 232,741 of them for
        // mutebutton.obj, on top of the raw bytes and the decoded string. Streaming
        // lets each line become garbage as soon as it has been parsed.
        Assets.readText(path).lineSequence().let { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split(WS)
                when (parts[0]) {
                    "v" -> {
                        val x = parts[1].toFloat()
                        val y = parts[2].toFloat()
                        val z = parts[3].toFloat()
                        positions.add(x)
                        positions.add(y)
                        positions.add(z)
                        if (collectBounds) {
                            if (x < mnX) mnX = x; if (x > mxX) mxX = x
                            if (y < mnY) mnY = y; if (y > mxY) mxY = y
                            if (z < mnZ) mnZ = z; if (z > mxZ) mxZ = z
                        }
                    }
                    "vt" -> {
                        texCoords.add(parts[1].toFloat())
                        texCoords.add(parts.getOrElse(2) { "0" }.toFloat())
                    }
                    // Object boundary — start a new group tagged with the object name.
                    // For models with materials, a following `usemtl` overwrites the
                    // name (and sets colour); models without materials keep the object
                    // name here (used to pick out e.g. bridge lamp "Icosphere" meshes).
                    "o", "g" -> {
                        flush()
                        curName = parts.getOrNull(1) ?: curName
                        // Bounds break on `o` only, never `g` — matching what the
                        // separate loadBounds pass did. The name is taken from the
                        // raw line so an object name containing spaces survives.
                        if (collectBounds && parts[0] == "o") {
                            flushBounds()
                            bName = line.substring(2).trim()
                        }
                    }
                    "usemtl" -> {
                        flush()
                        val name = parts.getOrNull(1) ?: ""
                        curName = name
                        val m = materials[name]
                        curTex = m?.texture
                        if (m != null) { curR = m.r; curG = m.g; curB = m.b }
                    }
                    "f" -> {
                        // Face tokens are 1-based "v", "v/vt", "v//vn", or "v/vt/vn".
                        val idx = IntArray(parts.size - 1) { i ->
                            val tok = parts[i + 1]
                            val slash = tok.indexOf('/')
                            (if (slash >= 0) tok.substring(0, slash) else tok).toInt()
                        }
                        // The UV index of each corner, 0 when the face carries none.
                        val uvIdx = IntArray(parts.size - 1) { i ->
                            val tok = parts[i + 1]
                            val s1 = tok.indexOf('/')
                            if (s1 < 0) return@IntArray 0
                            val rest = tok.substring(s1 + 1)
                            val s2 = rest.indexOf('/')
                            val t = if (s2 >= 0) rest.substring(0, s2) else rest
                            t.toIntOrNull() ?: 0            // "v//vn" has an empty slot
                        }
                        // Triangulate as a fan around the first vertex.
                        for (i in 1 until idx.size - 1) {
                            pushVert(curVerts, positions, idx[0])
                            pushVert(curVerts, positions, idx[i])
                            pushVert(curVerts, positions, idx[i + 1])
                            pushUv(curUvs, texCoords, uvIdx[0])
                            pushUv(curUvs, texCoords, uvIdx[i])
                            pushUv(curUvs, texCoords, uvIdx[i + 1])
                        }
                    }
                }
            }
        }
        flush()
        if (collectBounds) flushBounds()
        return ParsedObj(groups, boundsOut)
    }

    // V is flipped on the way in. OBJ puts v=0 at the BOTTOM of the image; the
    // renderer uploads its textures with GLUtils.texImage2D, which hands the bitmap
    // over top row first, putting the image's TOP at v=0. Without the flip every
    // texture in the game renders upside down.
    private fun pushUv(dst: FloatList, uvs: FloatList, oneBasedIdx: Int) {
        if (oneBasedIdx == 0 || uvs.isEmpty()) { dst.add(0f); dst.add(0f); return }
        val zeroIdx = if (oneBasedIdx > 0) oneBasedIdx - 1 else uvs.size / 2 + oneBasedIdx
        val base = zeroIdx * 2
        if (base < 0 || base + 1 >= uvs.size) { dst.add(0f); dst.add(0f); return }
        dst.add(uvs[base])
        dst.add(1f - uvs[base + 1])
    }

    private fun pushVert(dst: FloatList, positions: FloatList, oneBasedIdx: Int) {
        // Negative indices in OBJ are relative to the end of the position list.
        val zeroIdx = if (oneBasedIdx > 0) oneBasedIdx - 1 else positions.size / 3 + oneBasedIdx
        val base = zeroIdx * 3
        dst.add(positions[base])
        dst.add(positions[base + 1])
        dst.add(positions[base + 2])
    }
}
