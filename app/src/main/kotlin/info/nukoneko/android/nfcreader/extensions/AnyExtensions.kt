package info.nukoneko.android.nfcreader.extensions

import kotlin.reflect.full.functions

fun Any.allGetterResults(): Map<String, Any?> {
    return this::class.functions
            .filter { it.name.startsWith("get") && it.name.length > 3 }
            .map {
                val methodName = it.name.substring(3)
                val result = it.call(this)
                methodName to when (result) {
                    is Byte -> byteArrayOf(result).toHexString()
                    is ByteArray -> result.toHexString()
                    is Short -> result.toString()
                    else -> result
                }
            }
            .toMap()
}