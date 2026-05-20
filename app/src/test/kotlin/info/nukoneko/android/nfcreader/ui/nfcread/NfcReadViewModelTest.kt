package info.nukoneko.android.nfcreader.ui.nfcread

import info.nukoneko.android.nfcreader.model.entity.ReadStatus
import org.junit.Assert.assertEquals
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
}
