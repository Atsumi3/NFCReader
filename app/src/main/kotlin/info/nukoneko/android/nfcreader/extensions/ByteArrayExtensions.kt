package info.nukoneko.android.nfcreader.extensions

import java.util.*

fun ByteArray.toHexString(): String {
    return joinToString(" ") { "%02x".format(it).uppercase(Locale.getDefault()) }
}
