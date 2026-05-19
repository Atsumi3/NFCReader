package info.nukoneko.android.nfcreader.extensions

import junit.framework.TestCase.assertEquals
import org.junit.Test

class ByteArrayExtensionsTest {
    @Test
    fun byteArrayToHexString() {
        val arr = byteArrayOf(0xFF.toByte(), 0x00, 0x50, 0x7F)
        assertEquals("FF 00 50 7F", arr.toHexString())
    }

    @Test
    fun byteToHexString() {
        assertEquals("FF", 0xFF.toByte().toHexString())
        assertEquals("00", 0.toByte().toHexString())
        assertEquals("7F", 0x7F.toByte().toHexString())
    }

    @Test
    fun shortToHexString() {
        assertEquals("00FF", 0xFF.toShort().toHexString())
        assertEquals("FFFF", (-1).toShort().toHexString())
        assertEquals("0000", 0.toShort().toHexString())
    }

    @Test
    fun intToHexString() {
        assertEquals("000000FF", 0xFF.toHexString())
        assertEquals("FFFFFFFF", (-1).toHexString())
        assertEquals("12345678", 0x12345678.toHexString())
    }

    @Test
    fun longToHexString() {
        assertEquals("00000000000000FF", 0xFFL.toHexString())
        assertEquals("FFFFFFFFFFFFFFFF", (-1L).toHexString())
    }

    @Test
    fun shortArrayToHexString() {
        val arr = shortArrayOf(0xFF, 0x00, 0x1234)
        assertEquals("00FF 0000 1234", arr.toHexString())
    }

    @Test
    fun intArrayToHexString() {
        val arr = intArrayOf(0xFF, 0x12345678)
        assertEquals("000000FF 12345678", arr.toHexString())
    }

    @Test
    fun longArrayToHexString() {
        val arr = longArrayOf(0xFFL, 0x1234567890ABCDEFL)
        assertEquals("00000000000000FF 1234567890ABCDEF", arr.toHexString())
    }
}
