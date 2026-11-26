package info.nukoneko.android.nfcreader.ui.nfcread

import info.nukoneko.android.nfcreader.model.entity.ReadStatus
import info.nukoneko.android.nfcreader.model.entity.NfcTagMetadata
import info.nukoneko.android.nfcreader.model.reader.NfcDumpCollector
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcReadViewModelTest {

    private val savedStateHandle = SavedStateHandle()
    private val viewModel = NfcReadViewModel(savedStateHandle)

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
    fun pendingSafTokenSurvivesSavedStateHandleRecreation() {
        val token = "00000000-0000-0000-0000-000000000001"
        savedStateHandle[NfcReadViewModel.PENDING_EXPORT_TOKEN_KEY] = token
        viewModel.onNfcDisabled()

        assertEquals(token, viewModel.pendingDumpFileExportToken())
        val recreated = NfcReadViewModel(SavedStateHandle(mapOf(NfcReadViewModel.PENDING_EXPORT_TOKEN_KEY to token)))
        assertEquals(token, recreated.pendingDumpFileExportToken())
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
