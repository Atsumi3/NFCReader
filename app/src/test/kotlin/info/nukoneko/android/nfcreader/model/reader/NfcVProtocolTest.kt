package info.nukoneko.android.nfcreader.model.reader

import info.nukoneko.android.nfcreader.extensions.hexToBytes
import info.nukoneko.android.nfcreader.extensions.toHexStringCompact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcVProtocolTest {
    private val uid = hexToBytes("E004015023456789")

    @Test
    fun extendedSystemInformationUsesParameterBeforeUid() {
        val command = buildNfcVGetSystemInformation(uid, extended = true)
        assertEquals("223b1fE004015023456789".lowercase(), command?.toHexStringCompact())
    }

    @Test
    fun extendedBlockAddressIsLittleEndian() {
        val command = buildNfcVReadSingleBlock(uid, 256)
        assertEquals("2230e0040150234567890001", command?.toHexStringCompact())
    }

    @Test
    fun extendedMemoryCountIsLittleEndianAndUsesCountMinusOne() {
        val response = hexToBytes("0004E004015023456789FF0703")
        val result = parseNfcVSystemInformation(response, uid, extended = true)
        assertEquals(2048, result.systemInfo?.blockCount)
        assertEquals(4, result.systemInfo?.blockSize)
    }

    @Test
    fun standardMemoryInformationAndMalformedResponsesAreDistinguished() {
        val response = hexToBytes("0004E0040150234567897F03")
        assertEquals(128, parseNfcVSystemInformation(response, uid, extended = false).systemInfo?.blockCount)
        assertFalse(parseNfcVSystemInformation(hexToBytes("0004"), uid, false).error.isNullOrEmpty())
        assertFalse(parseNfcVSystemInformation(response, hexToBytes("0000000000000000"), false).error.isNullOrEmpty())
        assertNull(buildNfcVReadSingleBlock(uid, -1))
    }

    @Test
    fun coverageCannotBeCompleteWhenARangeExceedsExpectedScope() {
        val coverage = info.nukoneko.android.nfcreader.model.entity.ReadCoverage(
            "blocks", "block", info.nukoneko.android.nfcreader.model.entity.ReadRange(0, 10),
            listOf(info.nukoneko.android.nfcreader.model.entity.ReadRange(0, 11)),
        )
        assertFalse(coverage.isComplete)
    }
}
