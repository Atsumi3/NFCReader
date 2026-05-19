package info.nukoneko.android.nfcreader.extensions

import kotlin.reflect.full.functions

fun Any.allGetterResults(): Map<String, String?> {
    return this::class.functions
        .filter {
            it.name.startsWith("get") &&
                it.name.length > 3 &&
                // Only zero-arg getters (parameters[0] is the receiver).
                it.parameters.size == 1
        }
        .map { fn ->
            val methodName = fn.name.substring(3)
            val value = try {
                fn.call(this).formatForDisplay()
            } catch (t: Throwable) {
                "<error: ${t::class.simpleName}>"
            }
            methodName to value
        }
        .toMap()
}

private fun Any?.formatForDisplay(): String? = when (this) {
    null -> null
    is Byte -> toHexString()
    is Short -> toHexString()
    is Int -> toHexString()
    is Long -> toHexString()
    is ByteArray -> toHexString()
    is ShortArray -> toHexString()
    is IntArray -> toHexString()
    is LongArray -> toHexString()
    is BooleanArray -> joinToString(" ") { it.toString() }
    is Array<*> -> joinToString(" ") { it.formatForDisplay() ?: "null" }
    is Iterable<*> -> joinToString(" ") { it.formatForDisplay() ?: "null" }
    is Boolean -> toString()
    is String -> this
    else -> toString()
}
