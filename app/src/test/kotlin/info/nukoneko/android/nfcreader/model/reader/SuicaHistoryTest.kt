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
    fun parsesHistoryBlockFields() {
        // terminal 0x16, process 0x01, date 2024-05-20, balance 1500, seq 42
        val block = hexToBytes("1601000030B401020304DC0500002A00")
        val entry = parseSuicaHistoryBlock(block)
        assertEquals("改札機", entry.terminal)
        assertEquals("運賃支払", entry.process)
        assertEquals("2024-05-20", entry.date)
        assertEquals(1500, entry.balanceYen)
        assertEquals(42L, entry.sequence)
    }
}
