package info.nukoneko.android.nfcreader.extensions

private const val HEX_SEPARATOR = " "

fun Byte.toHexString(): String = formatHex(2)
fun Short.toHexString(): String = formatHex(4)
fun Int.toHexString(): String = formatHex(8)
fun Long.toHexString(): String = formatHex(16)

fun ByteArray.toHexString(): String = joinToString(HEX_SEPARATOR) { it.toHexString() }
fun ShortArray.toHexString(): String = joinToString(HEX_SEPARATOR) { it.toHexString() }
fun IntArray.toHexString(): String = joinToString(HEX_SEPARATOR) { it.toHexString() }
fun LongArray.toHexString(): String = joinToString(HEX_SEPARATOR) { it.toHexString() }

/** Hex string with no separators, e.g. for AIDs and identifiers. */
fun ByteArray.toHexStringCompact(): String = joinToString("") { it.toHexString() }

/** Reads [length] bytes from [offset] as a little-endian unsigned integer. */
fun ByteArray.readUIntLe(offset: Int = 0, length: Int = size - offset): Long {
    var result = 0L
    for (i in 0 until length) {
        result = result or ((this[offset + i].toLong() and 0xFF) shl (8 * i))
    }
    return result
}

/** Reads [length] bytes from [offset] as a big-endian unsigned integer. */
fun ByteArray.readUIntBe(offset: Int = 0, length: Int = size - offset): Long {
    var result = 0L
    for (i in 0 until length) {
        result = (result shl 8) or (this[offset + i].toLong() and 0xFF)
    }
    return result
}

/** Parses a hex string with no separators into bytes. */
fun hexToBytes(hex: String): ByteArray =
    hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

private fun Number.formatHex(width: Int): String = "%0${width}X".format(this)
