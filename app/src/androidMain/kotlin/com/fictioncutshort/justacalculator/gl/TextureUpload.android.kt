package com.fictioncutshort.justacalculator.gl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.fictioncutshort.justacalculator.platform.Assets
import java.nio.ByteBuffer

actual fun decodeImageAsset(path: String): DecodedImage? = try {
    val bmp = BitmapFactory.decodeByteArray(
        Assets.readBytes(path), 0, Assets.readBytes(path).size,
    ) ?: return null
    // ARGB_8888 is Android's in-memory order; copyPixelsToBuffer writes it as
    // RGBA bytes on little-endian, which is what GL_RGBA expects.
    val argb = if (bmp.config == Bitmap.Config.ARGB_8888) bmp
    else bmp.copy(Bitmap.Config.ARGB_8888, false)
    val out = ByteArray(argb.width * argb.height * 4)
    argb.copyPixelsToBuffer(ByteBuffer.wrap(out))
    val result = DecodedImage(argb.width, argb.height, out)
    if (argb !== bmp) argb.recycle()
    bmp.recycle()
    result
} catch (t: Throwable) {
    null
}
