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

private fun Number.formatHex(width: Int): String = "%0${width}X".format(this)
