package info.nukoneko.android.nfcreader.model.reader

import android.nfc.TagLostException
import info.nukoneko.android.nfcreader.model.entity.DEFAULT_MAX_FIELD_VALUE_CHARS_PER_DUMP
import info.nukoneko.android.nfcreader.model.entity.DEFAULT_MAX_RAW_BYTES_PER_DUMP
import info.nukoneko.android.nfcreader.model.entity.ReadIssueKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CancellationException

@RunWith(RobolectricTestRunner::class)
class ReaderTransportBudgetTest {
    @Test
    fun felicaReaderRoutesPollingThroughFakeTransportAndStopsOnTagLoss() {
        val sent = mutableListOf<ByteArray>()
        val context = context()
        FelicaReader().readWithTransport(
            discoveredSystemCode = byteArrayOf(0x00, 0x03),
            maxTransceiveLength = 255,
            transport = ReadOnlyTransport { command ->
                sent += command.copyOf()
                throw TagLostException("gone")
            },
            context = context,
        )

        assertEquals(1, sent.size)
        assertEquals(0x00, sent.single()[1].toInt() and 0xFF)
        assertTrue(context.buildResult().issues.any { it.kind == ReadIssueKind.TAG_LOST })
    }

    @Test
    fun nfcVReaderRoutesSystemInformationThroughFakeTransportAndStopsOnTagLoss() {
        val sent = mutableListOf<ByteArray>()
        val context = context()
        NfcVReader().readWithTransport(
            uid = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            maxTransceiveLength = 255,
            transport = ReadOnlyTransport { command ->
                sent += command.copyOf()
                throw TagLostException("gone")
            },
            context = context,
        )

        assertEquals(1, sent.size)
        assertTrue(context.buildResult().issues.any { it.kind == ReadIssueKind.TAG_LOST })
    }

    @Test
    fun cancelledAndExpiredReadersDoNotSendAnotherCommand() {
        var cancelled = false
        val sentAfterCancel = mutableListOf<ByteArray>()
        val cancellationContext = context(cancellationCheck = {
            if (cancelled) throw CancellationException("cancelled")
        })
        try {
            NfcVReader().readWithTransport(
                uid = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
                maxTransceiveLength = 255,
                transport = ReadOnlyTransport { command ->
                    sentAfterCancel += command.copyOf()
                    cancelled = true
                    byteArrayOf(0x00)
                },
                context = cancellationContext,
            )
        } catch (_: CancellationException) {
        }
        assertEquals(1, sentAfterCancel.size)

        val sentAfterCountLimit = mutableListOf<ByteArray>()
        val countContext = context(limits = TechReadLimits(timeoutMillis = 30_000, maxTransceives = 1))
        try {
            NfcVReader().readWithTransport(
                uid = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
                maxTransceiveLength = 255,
                transport = ReadOnlyTransport { command ->
                    sentAfterCountLimit += command.copyOf()
                    byteArrayOf(0x00)
                },
                context = countContext,
            )
        } catch (_: ReadBudgetExceededException) {
        }
        assertEquals(1, sentAfterCountLimit.size)
        assertTrue(countContext.buildResult().issues.any { it.kind == ReadIssueKind.BUDGET_EXHAUSTED })

        var nowNanos = 0L
        val sentAfterDeadline = mutableListOf<ByteArray>()
        val deadlineContext = context(
            limits = TechReadLimits(timeoutMillis = 1, maxTransceives = 10),
            clockNanos = { nowNanos },
        )
        try {
            NfcVReader().readWithTransport(
                uid = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
                maxTransceiveLength = 255,
                transport = ReadOnlyTransport { command ->
                    sentAfterDeadline += command.copyOf()
                    nowNanos = 1_000_000
                    byteArrayOf(0x00)
                },
                context = deadlineContext,
            )
        } catch (_: ReadBudgetExceededException) {
        }
        assertEquals(1, sentAfterDeadline.size)
        assertTrue(deadlineContext.buildResult().issues.any { it.kind == ReadIssueKind.BUDGET_EXHAUSTED })
    }

    private fun context(
        limits: TechReadLimits = TechReadLimits(timeoutMillis = 30_000, maxTransceives = 4096),
        clockNanos: () -> Long = System::nanoTime,
        cancellationCheck: () -> Unit = {},
    ) = TechReadContext(
        RawCaptureBudget(DEFAULT_MAX_RAW_BYTES_PER_DUMP),
        FieldValueBudget(DEFAULT_MAX_FIELD_VALUE_CHARS_PER_DUMP),
        cancellationCheck,
        limits,
        clockNanos,
    )
}
