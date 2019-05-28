package info.nukoneko.android.nfcreader.extensions

fun ByteArray.toHexString(): String {
    return joinToString(" ") { "%02x".format(it).toUpperCase() }
}