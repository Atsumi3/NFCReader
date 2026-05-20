package info.nukoneko.android.nfcreader.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnyExtensionsTest {

    @Suppress("unused")
    class Sample {
        fun getByteValue(): Byte = 0xAB.toByte()
        fun getShortValue(): Short = 0x1234
        fun getIntValue(): Int = 0x0000ABCD
        fun getText(): String = "hello"
        fun getFlag(): Boolean = true
        fun getBytes(): ByteArray = byteArrayOf(0x01, 0x0F)
        fun getInts(): IntArray = intArrayOf(1, 255)
        fun getNothing(): String? = null
        fun getWithArgument(value: Int): Int = value
        fun compute(): Int = 0
        fun isReady(): Boolean = true
        fun canWrite(): Boolean = false
        fun issueCount(): Int = 7
    }

    private val results = Sample().allGetterResults()

    @Test
    fun numbersAreHexFormatted() {
        assertEquals("AB", results["ByteValue"])
        assertEquals("1234", results["ShortValue"])
        assertEquals("0000ABCD", results["IntValue"])
    }

    @Test
    fun arraysAreHexFormatted() {
        assertEquals("01 0F", results["Bytes"])
        assertEquals("00000001 000000FF", results["Ints"])
    }

    @Test
    fun stringsAndBooleansPassThrough() {
        assertEquals("hello", results["Text"])
        assertEquals("true", results["Flag"])
    }

    @Test
    fun nullGetterValueIsKept() {
        assertTrue(results.containsKey("Nothing"))
        assertNull(results["Nothing"])
    }

    @Test
    fun parameterizedGettersAreExcluded() {
        assertFalse(results.containsKey("WithArgument"))
    }

    @Test
    fun nonGetterMethodsAreExcluded() {
        assertFalse(results.containsKey("compute"))
    }

    @Test
    fun isAndCanGettersAreIncludedWithPredicateNames() {
        assertEquals("true", results["isReady"])
        assertEquals("false", results["canWrite"])
    }

    @Test
    fun prefixWithoutUpperCaseIsNotAGetter() {
        // "issueCount" starts with "is" but the next char is lower-case.
        assertFalse(results.containsKey("issueCount"))
        assertFalse(results.containsKey("sueCount"))
    }
}
