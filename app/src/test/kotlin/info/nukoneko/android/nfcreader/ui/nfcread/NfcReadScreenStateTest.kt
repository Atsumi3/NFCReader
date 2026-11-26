package info.nukoneko.android.nfcreader.ui.nfcread

import info.nukoneko.android.nfcreader.model.entity.NfcDump
import info.nukoneko.android.nfcreader.model.entity.NfcTagMetadata
import info.nukoneko.android.nfcreader.model.entity.ReadStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcReadScreenStateTest {
    @Test
    fun stopControlIsShownOnlyWhileReading() {
        assertTrue(ReadStatus.Reading.isReadingInProgress())
        assertTrue(
            ReadStatus.ReadingWithPrevious(
                NfcDump(tag = NfcTagMetadata(), technologies = emptyList()),
            ).isReadingInProgress(),
        )
        assertFalse(ReadStatus.Idle.isReadingInProgress())
        assertFalse(ReadStatus.Failure.NfcDisabled.isReadingInProgress())
        assertFalse(ReadStatus.Success(emptyList()).isReadingInProgress())
    }
}
