package info.nukoneko.android.nfcreader.extensions

import kotlin.reflect.full.functions

fun Any.allGetterResults(): Map<String, String?> {
    return this::class.functions
        // Only zero-arg accessors (parameters[0] is the receiver).
        .filter { it.parameters.size == 1 }
        .mapNotNull { fn ->
            val label = fn.name.getterLabelOrNull() ?: return@mapNotNull null
            val value = try {
                fn.call(this).formatForDisplay()
            } catch (t: Throwable) {
                "<error: ${t::class.simpleName}>"
            }
            label to value
        }
        .toMap()
}

/**
 * Returns the display label for a JavaBean-style accessor name, or null if the
 * name is not an accessor. `getX` drops the prefix; `isX`/`canX` keep their
 * predicate form. The char after the prefix must be upper-case so that names
 * like `island` or `cancel` are not mistaken for accessors.
 */
private fun String.getterLabelOrNull(): String? = when {
    startsWith("get") && hasUpperCaseAt(3) -> substring(3)
    startsWith("is") && hasUpperCaseAt(2) -> this
    startsWith("can") && hasUpperCaseAt(3) -> this
    else -> null
}

private fun String.hasUpperCaseAt(index: Int): Boolean =
    length > index && this[index].isUpperCase()

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
    is Array<*> -> asIterable().formatElements()
    is Iterable<*> -> formatElements()
    is Boolean -> toString()
    is String -> this
    else -> toString()
}

private fun Iterable<*>.formatElements(): String =
    joinToString(" ") { it.formatForDisplay() ?: "null" }
