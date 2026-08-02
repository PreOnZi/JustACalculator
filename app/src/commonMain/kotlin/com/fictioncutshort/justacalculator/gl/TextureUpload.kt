package com.fictioncutshort.justacalculator.gl

import com.fictioncutshort.justacalculator.platform.Assets

/** A decoded image as tightly-packed RGBA8888 rows, top row first. */
class DecodedImage(val width: Int, val height: Int, val rgba: ByteArray)

/**
 * Decodes a bundled image asset, or null if it is missing or undecodable.
 *
 * Android's `GLUtils.texImage2D` uploads a Bitmap straight to GL; iOS has no
 * equivalent, so both platforms decode to raw RGBA here and the upload itself
 * lives in shared code.
 */
expect fun decodeImageAsset(path: String): DecodedImage?

/**
 * Decodes [path] and uploads it as a 2D texture, returning the texture name, or
 * 0 if the asset is missing — the renderers treat 0 as "draw untextured", so a
 * missing texture must not throw.
 */
fun uploadTextureFromAsset(path: String): Int {
    val image = decodeImageAsset(path) ?: return 0
    val ids = IntArray(1)
    Gl.glGenTextures(1, ids, 0)
    Gl.glBindTexture(Gl.GL_TEXTURE_2D, ids[0])
    Gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MIN_FILTER, Gl.GL_LINEAR)
    Gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MAG_FILTER, Gl.GL_LINEAR)
    Gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_WRAP_S, Gl.GL_CLAMP_TO_EDGE)
    Gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_WRAP_T, Gl.GL_CLAMP_TO_EDGE)
    Gl.glTexImage2DRgba(image.width, image.height, image.rgba)
    return ids[0]
}

/** True if [path] resolves to a bundled asset. */
fun textureAssetExists(path: String): Boolean = Assets.exists(path)
