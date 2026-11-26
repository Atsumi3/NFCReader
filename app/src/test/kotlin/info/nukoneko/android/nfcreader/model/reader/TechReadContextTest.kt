package info.nukoneko.android.nfcreader.model.reader

import info.nukoneko.android.nfcreader.model.entity.NfcField
import info.nukoneko.android.nfcreader.model.entity.ReadCoverage
import info.nukoneko.android.nfcreader.model.entity.ReadIssueKind
import info.nukoneko.android.nfcreader.model.entity.ReadOutcome
import info.nukoneko.android.nfcreader.model.entity.ReadRange
import info.nukoneko.android.nfcreader.model.entity.DEFAULT_MAX_FIELD_VALUE_CHARS_PER_DUMP
import info.nukoneko.android.nfcreader.model.entity.DEFAULT_MAX_RAW_BYTES_PER_DUMP
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TechReadContextTest {

    @Test
    fun rawCaptureStopsAtBudgetAndReportsTheUnreadTail() {
        val context = context(rawLimit = 2)

        val chunk = context.captureRaw("response", byteArrayOf(0x01, 0x02, 0x03), "block 4")
        val result = context.buildResult()

        assertEquals("0102", chunk.hex)
        assertEquals(3, chunk.sourceByteCount)
        assertEquals(2, chunk.capturedByteCount)
        assertTrue(chunk.isTruncated)
        assertEquals(ReadOutcome.PARTIAL, result.outcome)
        assertEquals(ReadIssueKind.RAW_LIMIT_REACHED, result.issues.single().kind)
        assertEquals(2L, result.unreadRanges.single().startInclusive)
        assertEquals(3L, result.unreadRanges.single().endExclusive)
    }

    @Test
    fun fieldValuesAreBoundedAndTruncationIsReported() {
        val context = context(fieldLimit = 15)

        context.addDecodedFields(listOf(NfcField("payload", "abcdefghijklmnopqr")))
        val result = context.buildResult()

        assertEquals("abc…[truncated]", result.decodedFields.single().value)
        assertEquals(ReadIssueKind.FIELD_LIMIT_REACHED, result.issues.single().kind)
        assertEquals(ReadOutcome.PARTIAL, result.outcome)
    }

    @Test
    fun completeRequiresKnownRangeAndFullCoverage() {
        val context = context()
        context.setCoverage(
            ReadCoverage(
                scope = "known test range",
                unit = "block",
                expectedRange = ReadRange(4, 6),
                completedRanges = listOf(ReadRange(4, 5), ReadRange(5, 6)),
            )
        )
        context.captureRaw("blocks", byteArrayOf(0x01, 0x02))
        context.markComplete()

        assertEquals(ReadOutcome.COMPLETE, context.buildResult().outcome)
    }

    @Test
    fun pureProtocolReaderAcceptsAReadOnlyFakeTransport() {
        var sentCommand = byteArrayOf()
        val transport = ReadOnlyTransport { command ->
            sentCommand = command.copyOf()
            byteArrayOf(0x90.toByte(), 0x00)
        }
        val context = context()
        val reader = ReadOnlyProtocolReader { readOnlyTransport, readContext ->
            val response = readOnlyTransport.transceiveReadOnly(byteArrayOf(0x00, 0xB2.toByte()))
            readContext.captureRaw("response", response)
            readContext.addDecodedFields(listOf(NfcField("status", response[0].toInt().toString())))
            readContext.setCoverage(
                ReadCoverage(
                    scope = "one requested response",
                    unit = "command",
                    expectedRange = ReadRange(0, 1),
                    completedRanges = listOf(ReadRange(0, 1)),
                )
            )
            readContext.markComplete()
        }

        reader.read(transport, context)
        val result = context.buildResult()

        assertArrayEquals(byteArrayOf(0x00, 0xB2.toByte()), sentCommand)
        assertEquals("9000", result.rawChunks.single().hex)
        assertEquals(ReadOutcome.COMPLETE, result.outcome)
    }

    @Test
    fun unknownCoverageCannotBeMarkedComplete() {
        val context = context()
        context.addDecodedFields(listOf(NfcField("value", "read")))
        context.markComplete()

        assertEquals(ReadOutcome.PARTIAL, context.buildResult().outcome)
        assertFalse(context.buildResult().coverage.isComplete)
    }

    private fun context(
        rawLimit: Int = DEFAULT_MAX_RAW_BYTES_PER_DUMP,
        fieldLimit: Int = DEFAULT_MAX_FIELD_VALUE_CHARS_PER_DUMP,
    ) = TechReadContext(RawCaptureBudget(rawLimit), FieldValueBudget(fieldLimit)) {}
}
