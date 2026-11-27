package info.nukoneko.android.nfcreader.ui.nfcread

import info.nukoneko.android.nfcreader.model.entity.NfcDump
import info.nukoneko.android.nfcreader.model.entity.NfcTagMetadata
import info.nukoneko.android.nfcreader.model.entity.ReadStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    @Test
    fun dumpForDisplayKeepsTheSamePreviousDumpWhenReadingStarts() {
        val previousDump = NfcDump(
            tag = NfcTagMetadata(),
            technologies = emptyList(),
            capturedAtEpochMillis = 1L,
        )

        val successDump = ReadStatus.Success(emptyList(), previousDump).dumpForDisplay()
        val readingDump = ReadStatus.ReadingWithPrevious(previousDump).dumpForDisplay()

        assertSame(previousDump, successDump)
        assertSame(successDump, readingDump)
        assertSame(previousDump, ReadStatus.Failure.NfcDisabledWithDump(previousDump).dumpForDisplay())
        assertSame(previousDump, ReadStatus.Failure.IntentUnsupportedWithDump(previousDump).dumpForDisplay())
        assertNull(ReadStatus.Reading.dumpForDisplay())
    }
}
