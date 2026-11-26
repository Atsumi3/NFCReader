package info.nukoneko.android.nfcreader.model.reader

import info.nukoneko.android.nfcreader.model.entity.NfcDump
import info.nukoneko.android.nfcreader.model.entity.NfcEntity
import info.nukoneko.android.nfcreader.model.entity.NfcField
import info.nukoneko.android.nfcreader.model.entity.NfcTagMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcDumpExportSnapshotTest {
    @Test
    fun formattedOutputsKeepTheSelectedDumpAndCaptureTime() {
        val selected = NfcDump(
            tag = NfcTagMetadata(idHex = "0102", techNames = listOf("Tech")),
            technologies = listOf(NfcEntity("Tech", listOf(NfcField("value", "first")))),
            capturedAtEpochMillis = 1234L,
        )
        val snapshot = NfcDumpExportSnapshot.create(selected)
        val newer = selected.copy(capturedAtEpochMillis = 5678L)

        assertEquals(selected, snapshot.dump)
        assertTrue(snapshot.text.contains("Captured at (epoch ms): 1234"))
        assertTrue(snapshot.json.contains("\"capturedAtEpochMillis\":1234"))
        assertTrue(snapshot.technologyText.getValue("Tech").contains("Decoded.value: first"))
        assertFalse(snapshot.text.contains("5678"))
        assertFalse(NfcDumpExportSnapshot.create(newer).text == snapshot.text)
    }
}
