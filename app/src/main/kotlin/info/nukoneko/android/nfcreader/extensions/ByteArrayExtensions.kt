package info.nukoneko.android.nfcreader.extensions

private const val HEX_SEPARATOR = " "

fun ByteArray.toHexString(): String =
    joinToString(HEX_SEPARATOR) { "%02X".format(it) }

fun Byte.toHexString(): String = "%02X".format(this)

fun Short.toHexString(): String = "%04X".format(this)

fun Int.toHexString(): String = "%08X".format(this)

fun Long.toHexString(): String = "%016X".format(this)

fun ShortArray.toHexString(): String =
    joinToString(HEX_SEPARATOR) { it.toHexString() }

fun IntArray.toHexString(): String =
    joinToString(HEX_SEPARATOR) { it.toHexString() }

fun LongArray.toHexString(): String =
    joinToString(HEX_SEPARATOR) { it.toHexString() }
