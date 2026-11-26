package info.nukoneko.android.nfcreader.ui.nfcread

import info.nukoneko.android.nfcreader.model.entity.ReadStatus
import info.nukoneko.android.nfcreader.model.entity.NfcTagMetadata
import info.nukoneko.android.nfcreader.model.reader.NfcDumpCollector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcReadViewModelTest {

    private val viewModel = NfcReadViewModel()

    @Test
    fun initialStateIsIdle() {
        assertEquals(ReadStatus.Idle, viewModel.state.value)
    }

    @Test
    fun nullIntentReportsUnsupported() {
        viewModel.onNewIntent(null)
        assertEquals(ReadStatus.Failure.IntentUnsupported, viewModel.state.value)
    }

    @Test
    fun nfcDisabledIsReported() {
        viewModel.onNfcDisabled()
        assertEquals(ReadStatus.Failure.NfcDisabled, viewModel.state.value)
    }

    @Test
    fun intentIsIgnoredOnceNfcDisabled() {
        viewModel.onNfcDisabled()
        viewModel.onNewIntent(null)
        assertEquals(ReadStatus.Failure.NfcDisabled, viewModel.state.value)
    }

    @Test
    fun pendingSafSnapshotIsRetainedUntilTheResultConsumesIt() {
        val snapshot = "a sufficiently large immutable export snapshot"
        viewModel.setPendingDumpFileExport("nfc-dump.json", snapshot)
        viewModel.onNfcDisabled()

        assertEquals("nfc-dump.json" to snapshot, viewModel.takePendingDumpFileExport())
        assertNull(viewModel.takePendingDumpFileExport())
    }

    @Test
    fun staleGenerationCannotPublishOverCurrentReadAndItsSnapshotIsRetained() {
        val gate = ReadGenerationGate()
        val oldGeneration = gate.advance()
        val currentGeneration = gate.advance()
        val oldDump = NfcDumpCollector(NfcTagMetadata(techNames = listOf("old"))).snapshot()
        val currentDump = NfcDumpCollector(NfcTagMetadata(techNames = listOf("new"))).snapshot()

        assertFalse(gate.publish(oldGeneration, oldDump))
        assertTrue(gate.publish(currentGeneration, currentDump))
        assertEquals(oldDump, gate.takePendingDump())
        assertNull(gate.takePendingDump())
    }
}
