package info.nukoneko.android.nfcreader.model.reader

import info.nukoneko.android.nfcreader.extensions.hexToBytes
import info.nukoneko.android.nfcreader.extensions.toHexStringCompact
import org.junit.Assert.assertEquals
import org.junit.Test

class SuicaHistoryTest {

    @Test
    fun pollingCommandFrameIsLengthPrefixed() {
        // len(06) code(00) systemCode(0003) requestCode(01) timeSlot(00)
        assertEquals("060000030100", buildPollingCommand(SUICA_SYSTEM_CODE).toHexStringCompact())
    }

    @Test
    fun readWithoutEncryptionCommandFrame() {
        val idm = hexToBytes("0102030405060708")
        val command = buildReadWithoutEncryptionCommand(idm, SUICA_HISTORY_SERVICE, listOf(0, 1))
        // len 06 IDm svcCount serviceCodeLE blockCount blockList...
        assertEquals("12060102030405060708010f090280008001", command.toHexStringCompact().lowercase())
    }

    @Test
    fun pollingAndSystemCodeResponsesKeepEachSystemsIdm() {
        val idm = hexToBytes("0102030405060708")
        val pollResponse = hexToBytes("1201" + "0102030405060708" + "AABBCCDDEEFF0011")
        assertEquals("0102030405060708", parseFelicaPollingResponse(pollResponse, 0x0003)?.idm?.toHexStringCompact())

        val request = buildRequestSystemCodesCommand(idm)
        assertEquals("0A0C0102030405060708", request.toHexStringCompact())
        val systemCodesResponse = hexToBytes("0F0D0102030405060708020003090F")
        assertEquals(listOf(0x0003, 0x090F), parseFelicaSystemCodesResponse(systemCodesResponse, idm))
    }

    @Test
    fun requestServiceUsesLittleEndianServiceCodesAndKeyVersions() {
        val idm = hexToBytes("0102030405060708")
        val serviceCode = buildFelicaServiceCode(2, 0x09)
        assertEquals(0x89, serviceCode)
        assertEquals("0D020102030405060708018900", buildRequestServiceCommand(idm, listOf(serviceCode)).toHexStringCompact())
        val response = hexToBytes("0D030102030405060708013412")
        val service = parseRequestServiceResponse(response, idm, listOf(serviceCode))!!.single()
        assertEquals(0x1234, service.keyVersion)
        assertEquals(false, service.requiresAuthentication)
    }

    @Test
    fun largeBlockListAddressUsesThreeByteElementWithoutRollover() {
        val command = buildReadWithoutEncryptionCommand(
            hexToBytes("0102030405060708"),
            SUICA_HISTORY_SERVICE,
            listOf(256),
        )
        assertEquals("11060102030405060708010f0901000001", command.toHexStringCompact())
    }

    @Test
    fun parsesHistoryBlockFields() {
        // terminal 0x16, process 0x01, date 2024-05-20, balance 1500, seq 42
        val block = hexToBytes("1601000030B401020304DC0500002A00")
        val entry = parseSuicaHistoryBlock(block)
        assertEquals("改札機", entry.terminal)
        assertEquals("運賃支払", entry.process)
        assertEquals("2024-05-20", entry.date)
        assertEquals(1500, entry.balanceYen)
        assertEquals(42L, entry.sequence)
        assertEquals(true, entry.dateValid)
    }

    @Test
    fun invalidDateIsNotNormalizedAsAValidCalendarDate() {
        val entry = parseSuicaHistoryBlock(hexToBytes("1601000030B401020304DC0500002A00").also {
            it[4] = 0x00
            it[5] = 0x00
        })
        assertEquals(false, entry.dateValid)
    }
}
