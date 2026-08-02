package com.fictioncutshort.justacalculator.gl

/**
 * Little-endian reads over a ByteArray, replacing
 * `java.nio.ByteBuffer.order(LITTLE_ENDIAN)`.
 *
 * glTF stores every numeric field little-endian regardless of host byte order,
 * so this is fixed rather than native-order — using the platform's order would
 * happen to work on both current targets and break on a big-endian one.
 *
 * [base] offsets every read, which is how the BIN chunk is addressed without
 * copying it out of the file.
 */
class LittleEndian(private val bytes: ByteArray, private val base: Int = 0) {

    var position: Int = 0

    private fun u(i: Int): Int = bytes[base + i].toInt() and 0xFF

    fun getByte(offset: Int): Byte = bytes[base + offset]

    fun getShort(offset: Int): Short =
        (u(offset) or (u(offset + 1) shl 8)).toShort()

    fun getInt(offset: Int): Int =
        u(offset) or (u(offset + 1) shl 8) or (u(offset + 2) shl 16) or (u(offset + 3) shl 24)

    fun getFloat(offset: Int): Float = Float.fromBits(getInt(offset))

    /** Sequential read of the next Int, advancing [position]. */
    fun nextInt(): Int = getInt(position).also { position += 4 }

    /** Sequential read of [count] bytes, advancing [position]. */
    fun nextBytes(count: Int): ByteArray =
        bytes.copyOfRange(base + position, base + position + count).also { position += count }

    /** A view starting at the current position — replaces ByteBuffer.slice(). */
    fun slice(): LittleEndian = LittleEndian(bytes, base + position)
}
