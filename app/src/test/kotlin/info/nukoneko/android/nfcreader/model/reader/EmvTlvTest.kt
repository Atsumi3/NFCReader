package info.nukoneko.android.nfcreader.model.reader

import info.nukoneko.android.nfcreader.extensions.hexToBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmvTlvTest {

    // A representative PPSE FCI response carrying a single Visa application.
    private val ppse = hexToBytes(
        "6F23840E325041592E5359532E4444463031A511BF0C0E610C4F07A0000000031010870101",
    )

    @Test
    fun parsesNestedConstructedTags() {
        val root = parseTlv(ppse)
        assertEquals(1, root.size)
        assertEquals("6F", root[0].tag)
        assertTrue(root[0].isConstructed)
    }

    @Test
    fun flattenExposesFullPaths() {
        val paths = parseTlv(ppse).flatten().map { it.first }
        assertTrue(paths.contains("6F/A5/BF0C/61/4F"))
        assertTrue(paths.contains("6F/A5/BF0C/61/87"))
    }

    @Test
    fun extractsAid() {
        val aids = extractAids(parseTlv(ppse))
        assertEquals(1, aids.size)
        assertArrayEquals(hexToBytes("A0000000031010"), aids[0])
    }

    @Test
    fun resolvesCardScheme() {
        assertEquals("Visa", cardScheme(hexToBytes("A0000000031010")))
        assertEquals("JCB", cardScheme(hexToBytes("A0000000651010")))
        assertEquals("不明", cardScheme(hexToBytes("D2760000850101")))
    }

    @Test
    fun buildsSelectAndReadRecordApdus() {
        assertArrayEquals(
            hexToBytes("00A4040007A000000003101000"),
            buildSelect(hexToBytes("A0000000031010")),
        )
        // SFI 1, record 1 -> P2 = (1 shl 3) or 0x04 = 0x0C
        assertArrayEquals(hexToBytes("00B2010C00"), buildReadRecord(1, 1))
    }

    @Test
    fun parsesMultiByteTag() {
        // 9F38 (PDOL) is a primitive tag with a multi-byte tag number.
        val tlv = parseTlv(hexToBytes("9F3802AABB"))
        assertEquals(1, tlv.size)
        assertEquals("9F38", tlv[0].tag)
        assertArrayEquals(hexToBytes("AABB"), tlv[0].value)
    }
}
