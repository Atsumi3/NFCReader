package info.nukoneko.android.nfcreader.extensions

import info.nukoneko.android.nfcreader.extensions.toHexString
import junit.framework.TestCase.assertEquals
import org.junit.Test

class ByteArrayExtensionsTest {
    @Test
    fun byteArrayToHexString() {
        val arr = byteArrayOf(0xFF.toByte(), 0x00, 0x50, 0x7F)
        assertEquals(arr.toHexString(), "FF 00 50 7F")
    }
}