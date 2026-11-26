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
        assertArrayEquals(hexToBytes("80CA9F3600"), buildGetData(0x9F36))
    }

    @Test
    fun parsesMultiByteTag() {
        // 9F38 (PDOL) is a primitive tag with a multi-byte tag number.
        val tlv = parseTlv(hexToBytes("9F3802AABB"))
        assertEquals(1, tlv.size)
        assertEquals("9F38", tlv[0].tag)
        assertArrayEquals(hexToBytes("AABB"), tlv[0].value)
    }

    @Test
    fun truncatedTlvRetainsTheUnparsedWireBytes() {
        val raw = hexToBytes("5A04AABB")
        val parsed = parseTlvBounded(raw)

        assertEquals("5A", parsed.nodes.single().tag)
        assertTrue(parsed.issues.any { it.contains("exceeds remaining input") })
        assertArrayEquals(raw, parsed.remainders.single().bytes)
    }

    @Test
    fun overflowingBerLengthIsRejectedWithoutDroppingItsRawBytes() {
        val raw = hexToBytes("5A84FFFFFFFF")
        val parsed = parseTlvBounded(raw)

        assertTrue(parsed.issues.any { it.contains("exceeds remaining input") })
        assertArrayEquals(raw, parsed.remainders.single().bytes)
    }

    @Test
    fun tlvDepthAndNodeLimitsStopParsingWithRemainders() {
        val nested = parseTlvBounded(hexToBytes("E1035A0101"), maxDepth = 0)
        assertEquals(emptyList<Tlv>(), nested.nodes.single().children)
        assertTrue(nested.issues.any { it.contains("depth limit") })
        assertArrayEquals(hexToBytes("5A0101"), nested.remainders.single().bytes)

        val tooMany = parseTlvBounded(hexToBytes("5A01AA5F2401BB"), maxNodes = 1)
        assertEquals(1, tooMany.nodes.size)
        assertTrue(tooMany.issues.any { it.contains("node limit") })
        assertArrayEquals(hexToBytes("5F2401BB"), tooMany.remainders.single().bytes)
    }

    @Test
    fun logDolUsesTagLengthPairsAndBoundsMalformedInput() {
        val dol = parseDolBounded(hexToBytes("9A039F02065F2A02"))
        assertEquals(listOf("9A", "9F02", "5F2A"), dol.entries.map { it.tag })
        assertEquals(listOf(3, 6, 2), dol.entries.map { it.requestedLength })
        assertEquals(11, dol.totalRequestedLength)

        val malformed = parseDolBounded(hexToBytes("9F"))
        assertTrue(malformed.issues.isNotEmpty())
        assertArrayEquals(hexToBytes("9F"), malformed.remainder)
    }

    @Test
    fun shortApduResponseIsNotTreatedAsAStatusWord() {
        val response = ApduResponse(hexToBytes("90"))
        assertEquals(false, response.hasStatusWord)
        assertEquals("----", response.statusWord)
        assertEquals(false, response.isSuccess)
        assertArrayEquals(hexToBytes("90"), response.raw)
    }
}
