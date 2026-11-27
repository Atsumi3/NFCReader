package info.nukoneko.android.nfcreader.model.reader

import android.nfc.TagLostException
import android.nfc.tech.MifareClassic
import info.nukoneko.android.nfcreader.extensions.hexToBytes
import info.nukoneko.android.nfcreader.model.entity.DEFAULT_MAX_FIELD_VALUE_CHARS_PER_DUMP
import info.nukoneko.android.nfcreader.model.entity.DEFAULT_MAX_RAW_BYTES_PER_DUMP
import info.nukoneko.android.nfcreader.model.entity.ReadIssueKind
import info.nukoneko.android.nfcreader.model.entity.ReadRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
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
    fun felicaCancellationRetainsReadHistoryAndMarksRemainingBlocksAndSystems() {
        val idm = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        var cancelled = false
        val sent = mutableListOf<ByteArray>()
        val context = context(cancellationCheck = {
            if (cancelled) throw CancellationException("cancelled")
        })
        try {
            FelicaReader().readWithTransport(
                discoveredSystemCode = byteArrayOf(0, 3),
                maxTransceiveLength = 255,
                transport = ReadOnlyTransport { command ->
                    sent += command.copyOf()
                    when (command[1].toInt() and 0xFF) {
                        0x00 -> byteArrayOf(18, 0x01) + idm + byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte(), 0x00, 0x11)
                        0x0C -> byteArrayOf(13, 0x0D) + idm + byteArrayOf(1, 0, 3)
                        0x06 -> {
                            cancelled = true
                            byteArrayOf(77, 0x07) + idm + byteArrayOf(0, 0, 4) +
                                hexToBytes("1601000030B401020304DC0500002A00") + ByteArray(48)
                        }
                        else -> error("Unexpected FeliCa command")
                    }
                },
                context = context,
            )
        } catch (_: CancellationException) {
        }

        val result = context.buildResult()
        assertEquals(3, sent.size)
        assertTrue(result.decodedFields.any { it.label.contains("history[0]") })
        assertTrue(result.unreadRanges.any { it.scope.contains("service 0x090F") && it.startInclusive == 4L })
        assertTrue(result.unreadRanges.any { it.scope == "FeliCa systems" })
        assertTrue(result.coverageDetails.any { it.scope.startsWith("Suica history") })
    }

    @Test
    fun mifareClassicCancellationKeepsCompletedAndUnreadBlockRanges() {
        var cancelled = false
        val reads = mutableListOf<Int>()
        val readContext = context(cancellationCheck = {
            if (cancelled) throw CancellationException("cancelled")
        })
        val transport = object : MifareClassicTransport {
            override val sectorCount: Int = 1
            override fun sectorToBlock(sector: Int): Int = 0
            override fun blockCountInSector(sector: Int): Int = 4
            override fun authenticateKeyA(sector: Int, key: ByteArray): Boolean = true
            override fun authenticateKeyB(sector: Int, key: ByteArray): Boolean = false
            override fun readBlock(block: Int): ByteArray {
                reads += block
                cancelled = true
                return ByteArray(16) { block.toByte() }
            }
        }

        try {
            MifareClassicReader().readWithTransport(transport, readContext)
        } catch (_: CancellationException) {
        }

        val result = readContext.buildResult()
        assertEquals(listOf(0), reads)
        assertEquals(listOf(ReadRange(0, 1)), result.coverage.completedRanges)
        assertTrue(result.unreadRanges.any { it.startInclusive == 1L && it.endExclusive == 4L })
    }

    @Test
    fun mifareClassicRestoresSuccessfulAuthenticationAfterFailedKeyBProbes() {
        var authenticated = false
        var keyACalls = 0
        val keyAKeys = mutableListOf<ByteArray>()
        val readBlocks = mutableListOf<Int>()
        val transport = object : MifareClassicTransport {
            override val sectorCount: Int = 1
            override fun sectorToBlock(sector: Int): Int = 0
            override fun blockCountInSector(sector: Int): Int = 4
            override fun authenticateKeyA(sector: Int, key: ByteArray): Boolean {
                keyACalls++
                keyAKeys += key.copyOf()
                authenticated = true
                return true
            }
            override fun authenticateKeyB(sector: Int, key: ByteArray): Boolean {
                authenticated = false
                return false
            }
            override fun readBlock(block: Int): ByteArray {
                readBlocks += block
                if (!authenticated || block == 1) throw IOException("read failed")
                return ByteArray(16) { block.toByte() }
            }
        }
        val readContext = context()

        MifareClassicReader().readWithTransport(transport, readContext)

        val result = readContext.buildResult()
        assertEquals(listOf(0, 1, 2, 3), readBlocks)
        assertEquals(2, keyACalls)
        assertTrue(keyAKeys[0].contentEquals(keyAKeys[1]))
        assertTrue(result.decodedFields.any { it.label == "Classic.block[2]" })
        assertTrue(result.coverage.completedRanges.any { it.startInclusive == 2L && it.endExclusive == 3L })
        assertTrue(!result.coverage.isComplete)
    }

    @Test
    fun mifareClassicRestoresPreviouslySuccessfulKeyBAfterLaterReadFailure() {
        var authenticatedAs: String? = null
        var keyACalls = 0
        var successfulKeyBCalls = 0
        val readAttempts = mutableMapOf<Int, Int>()
        val readBlocks = mutableListOf<Int>()
        val transport = object : MifareClassicTransport {
            override val sectorCount: Int = 1
            override fun sectorToBlock(sector: Int): Int = 0
            override fun blockCountInSector(sector: Int): Int = 4
            override fun authenticateKeyA(sector: Int, key: ByteArray): Boolean {
                keyACalls++
                authenticatedAs = "A"
                return true
            }
            override fun authenticateKeyB(sector: Int, key: ByteArray): Boolean {
                if (key.contentEquals(MifareClassic.KEY_DEFAULT)) {
                    successfulKeyBCalls++
                    authenticatedAs = "B"
                    return true
                }
                authenticatedAs = null
                return false
            }
            override fun readBlock(block: Int): ByteArray {
                readBlocks += block
                val attempt = (readAttempts[block] ?: 0) + 1
                readAttempts[block] = attempt
                if ((block == 0 && attempt == 1) || (block == 1 && attempt <= 2)) {
                    authenticatedAs = null
                    throw IOException("force a later Key B restoration")
                }
                if (authenticatedAs != "B") throw IOException("previous Key B authentication was not restored")
                return ByteArray(16) { block.toByte() }
            }
        }
        val readContext = context()

        MifareClassicReader().readWithTransport(transport, readContext)

        val result = readContext.buildResult()
        assertEquals(listOf(0, 0, 1, 1, 2, 3), readBlocks)
        assertEquals(1, keyACalls)
        assertEquals(3, successfulKeyBCalls)
        assertTrue(result.coverage.completedRanges.any { it.startInclusive == 0L && it.endExclusive == 1L })
        assertTrue(result.coverage.completedRanges.any { it.startInclusive == 2L && it.endExclusive == 3L })
        assertTrue(result.coverage.completedRanges.any { it.startInclusive == 3L && it.endExclusive == 4L })
        assertTrue(result.unreadRanges.any {
            it.scope == "MIFARE Classic blocks" && it.startInclusive == 1L && it.endExclusive == 2L
        })
        assertTrue(!result.coverage.isComplete)
    }

    @Test
    fun mifareClassicBudgetDuringAuthenticationRestoreKeepsCoverageBeforeRethrowing() {
        var keyACalls = 0
        var keyBCalls = 0
        val readBlocks = mutableListOf<Int>()
        val transport = object : MifareClassicTransport {
            override val sectorCount: Int = 1
            override fun sectorToBlock(sector: Int): Int = 0
            override fun blockCountInSector(sector: Int): Int = 4
            override fun authenticateKeyA(sector: Int, key: ByteArray): Boolean {
                keyACalls++
                return true
            }
            override fun authenticateKeyB(sector: Int, key: ByteArray): Boolean {
                keyBCalls++
                return false
            }
            override fun readBlock(block: Int): ByteArray {
                readBlocks += block
                if (block == 0) return ByteArray(16)
                throw IOException("force Key B probes")
            }
        }
        val readContext = context(limits = TechReadLimits(timeoutMillis = 30_000, maxTransceives = 7))
        var budgetExceeded = false

        try {
            MifareClassicReader().readWithTransport(transport, readContext)
        } catch (_: ReadBudgetExceededException) {
            budgetExceeded = true
        }

        val result = readContext.buildResult()
        assertTrue(budgetExceeded)
        assertEquals(listOf(0, 1), readBlocks)
        assertEquals(1, keyACalls)
        assertEquals(4, keyBCalls)
        assertEquals(ReadRange(0, 4), result.coverage.expectedRange)
        assertEquals(listOf(ReadRange(0, 1)), result.coverage.completedRanges)
        val unreadTail = result.unreadRanges.single { it.scope == "MIFARE Classic blocks" }
        assertEquals(1L, unreadTail.startInclusive)
        assertEquals(4L, unreadTail.endExclusive)
        assertTrue(unreadTail.reason.contains("not obtained"))
        assertTrue(!unreadTail.reason.contains("not requested"))
        assertTrue(result.issues.any { it.kind == ReadIssueKind.BUDGET_EXHAUSTED })
    }

    @Test
    fun mifareClassicTagLossDuringAuthenticationRestoreStopsTheWholeScan() {
        var sectorZeroKeyACalls = 0
        var laterSectorAuthenticationCalls = 0
        val readBlocks = mutableListOf<Int>()
        val transport = object : MifareClassicTransport {
            override val sectorCount: Int = 2
            override fun sectorToBlock(sector: Int): Int = sector * 2
            override fun blockCountInSector(sector: Int): Int = 2
            override fun authenticateKeyA(sector: Int, key: ByteArray): Boolean {
                if (sector == 0) {
                    sectorZeroKeyACalls++
                    if (sectorZeroKeyACalls == 1) return true
                    throw TagLostException("tag left during authentication restore")
                }
                laterSectorAuthenticationCalls++
                return true
            }
            override fun authenticateKeyB(sector: Int, key: ByteArray): Boolean = false
            override fun readBlock(block: Int): ByteArray {
                readBlocks += block
                if (block == 1) throw IOException("force authentication restore")
                return ByteArray(16)
            }
        }
        val readContext = context()

        MifareClassicReader().readWithTransport(transport, readContext)

        val result = readContext.buildResult()
        assertEquals(2, sectorZeroKeyACalls)
        assertEquals(0, laterSectorAuthenticationCalls)
        assertEquals(listOf(0, 1), readBlocks)
        assertEquals(ReadRange(0, 4), result.coverage.expectedRange)
        assertEquals(listOf(ReadRange(0, 1)), result.coverage.completedRanges)
        val unreadTail = result.unreadRanges.single { it.scope == "MIFARE Classic blocks" }
        assertEquals(1L, unreadTail.startInclusive)
        assertEquals(4L, unreadTail.endExclusive)
        assertTrue(unreadTail.reason.contains("not obtained"))
        assertTrue(!unreadTail.reason.contains("not requested"))
        assertTrue(result.issues.any { it.kind == ReadIssueKind.TAG_LOST })
        assertTrue(result.issues.none { it.kind == ReadIssueKind.AUTHENTICATION_REQUIRED })
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
    fun suicaBalanceIsAvailableAsAStandaloneFieldAlongsideHistorySummary() {
        val idm = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        var historyReadCount = 0
        val context = context()
        FelicaReader().readWithTransport(
            discoveredSystemCode = byteArrayOf(0, 3),
            maxTransceiveLength = 255,
            transport = ReadOnlyTransport { command ->
                when (command[1].toInt() and 0xFF) {
                    0x00 -> byteArrayOf(18, 0x01) + idm + byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte(), 0x00, 0x11)
                    0x0C -> byteArrayOf(13, 0x0D) + idm + byteArrayOf(1, 0, 3)
                    0x06 -> {
                        val count = command[13].toInt() and 0xFF
                        val blocks = if (historyReadCount++ == 0) {
                            hexToBytes("1601000030B401020304DC0500002A00") + ByteArray((count - 1) * 16)
                        } else {
                            ByteArray(count * 16)
                        }
                        byteArrayOf((13 + blocks.size).toByte(), 0x07) + idm + byteArrayOf(0, 0, count.toByte()) + blocks
                    }
                    0x02 -> throw TagLostException("stop after history")
                    else -> error("Unexpected FeliCa command")
                }
            },
            context = context,
        )

        val fields = context.buildResult().decodedFields
        assertTrue(fields.any { it.label.contains("history[0]") })
        assertTrue(fields.any { it.label == "Suica.balance" && it.value == "1500" })
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
                    when (command[1].toInt() and 0xFF) {
                        0x2B -> byteArrayOf(0x00, 0x04) + byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8) + byteArrayOf(3, 3)
                        0x20 -> {
                            cancelled = true
                            byteArrayOf(0, 1, 2, 3, 4)
                        }
                        else -> error("Unexpected NFC-V command")
                    }
                },
                context = cancellationContext,
            )
        } catch (_: CancellationException) {
        }
        assertEquals(2, sentAfterCancel.size)
        val cancelledResult = cancellationContext.buildResult()
        assertEquals(listOf(ReadRange(0, 1)), cancelledResult.coverage.completedRanges)
        assertTrue(cancelledResult.unreadRanges.any { it.startInclusive == 1L && it.endExclusive == 4L })

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

    @Test
    fun nfcVBlockBudgetKeepsCompletedAndUnreadCoverageBeforeStopping() {
        val uid = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val sent = mutableListOf<ByteArray>()
        val readContext = context(limits = TechReadLimits(timeoutMillis = 30_000, maxTransceives = 2))

        try {
            NfcVReader().readWithTransport(
                uid = uid,
                maxTransceiveLength = 255,
                transport = ReadOnlyTransport { command ->
                    sent += command.copyOf()
                    when (command[1].toInt() and 0xFF) {
                        0x2B -> byteArrayOf(0x00, 0x04) + uid + byteArrayOf(3, 3)
                        0x20 -> byteArrayOf(0, 1, 2, 3, 4)
                        else -> error("Unexpected NFC-V command")
                    }
                },
                context = readContext,
            )
        } catch (_: ReadBudgetExceededException) {
        }

        val result = readContext.buildResult()
        assertEquals(2, sent.size)
        assertEquals(1, sent.count { (it[1].toInt() and 0xFF) == 0x20 })
        assertEquals(listOf(ReadRange(0, 1)), result.coverage.completedRanges)
        assertTrue(result.unreadRanges.any { it.startInclusive == 1L && it.endExclusive == 4L })
        assertTrue(result.issues.any { it.kind == ReadIssueKind.BUDGET_EXHAUSTED })
    }

    @Test
    fun nfcVBudgetSeparatesScannedTailFromCapacityBeyondLimit() {
        val uid = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val sent = mutableListOf<ByteArray>()
        val readContext = context(limits = TechReadLimits(timeoutMillis = 30_000, maxTransceives = 4))

        try {
            NfcVReader().readWithTransport(
                uid = uid,
                maxTransceiveLength = 255,
                transport = ReadOnlyTransport { command ->
                    sent += command.copyOf()
                    when (command[1].toInt() and 0xFF) {
                        0x2B -> byteArrayOf(0, 0x04) + uid + byteArrayOf(0xFF.toByte(), 0x03)
                        0x3B -> byteArrayOf(0, 0x04) + uid + byteArrayOf(0xFF.toByte(), 0x0F, 0x03)
                        0x20 -> byteArrayOf(0, 1, 2, 3, 4)
                        else -> error("Unexpected NFC-V command")
                    }
                },
                context = readContext,
            )
        } catch (_: ReadBudgetExceededException) {
        }

        val result = readContext.buildResult()
        val blockUnreadRanges = result.unreadRanges.filter { it.scope == "NFC-V block range" }
        assertEquals(4, sent.size)
        assertEquals(listOf(ReadRange(0, 1), ReadRange(1, 2)), result.coverage.completedRanges)
        assertEquals(ReadRange(0, 4094), result.coverage.expectedRange)
        assertTrue(blockUnreadRanges.any { it.startInclusive == 2L && it.endExclusive == 4094L })
        assertTrue(blockUnreadRanges.any { it.startInclusive == 4094L && it.endExclusive == 4096L })
        assertTrue(blockUnreadRanges.none { it.startInclusive!! < 4094L && it.endExclusive!! > 4094L })
    }

    @Test
    fun nfcVCancellationSeparatesScannedTailFromCapacityBeyondLimit() {
        val uid = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val sent = mutableListOf<ByteArray>()
        var cancelled = false
        val readContext = context(
            limits = TechReadLimits(timeoutMillis = 30_000, maxTransceives = 10),
            cancellationCheck = { if (cancelled) throw CancellationException("cancelled") },
        )

        try {
            NfcVReader().readWithTransport(
                uid = uid,
                maxTransceiveLength = 255,
                transport = ReadOnlyTransport { command ->
                    sent += command.copyOf()
                    when (command[1].toInt() and 0xFF) {
                        0x2B -> byteArrayOf(0, 0x04) + uid + byteArrayOf(0xFF.toByte(), 0x03)
                        0x3B -> byteArrayOf(0, 0x04) + uid + byteArrayOf(0xFF.toByte(), 0x0F, 0x03)
                        0x20 -> {
                            if ((command.last().toInt() and 0xFF) == 1) cancelled = true
                            byteArrayOf(0, 1, 2, 3, 4)
                        }
                        else -> error("Unexpected NFC-V command")
                    }
                },
                context = readContext,
            )
        } catch (_: CancellationException) {
        }

        val result = readContext.buildResult()
        val blockUnreadRanges = result.unreadRanges.filter { it.scope == "NFC-V block range" }
        assertEquals(4, sent.size)
        assertEquals(listOf(ReadRange(0, 1), ReadRange(1, 2)), result.coverage.completedRanges)
        assertEquals(ReadRange(0, 4094), result.coverage.expectedRange)
        assertTrue(blockUnreadRanges.any { it.startInclusive == 2L && it.endExclusive == 4094L })
        assertTrue(blockUnreadRanges.any { it.startInclusive == 4094L && it.endExclusive == 4096L })
        assertTrue(blockUnreadRanges.none { it.startInclusive!! < 4094L && it.endExclusive!! > 4094L })
    }

    @Test
    fun felicaDistributesTheUnchangedBatchLimitAcrossAdvertisedSystems() {
        val idm = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val commands = mutableListOf<ByteArray>()
        val context = context()
        FelicaReader().readWithTransport(
            discoveredSystemCode = byteArrayOf(0, 0x10),
            maxTransceiveLength = 255,
            transport = ReadOnlyTransport { command ->
                commands += command.copyOf()
                when (command[1].toInt() and 0xFF) {
                    0x00 -> {
                        val code = ((command[2].toInt() and 0xFF) shl 8) or (command[3].toInt() and 0xFF)
                        byteArrayOf(20, 0x01) + idm + byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte(), 0x00, 0x11, (code ushr 8).toByte(), code.toByte())
                    }
                    0x0C -> byteArrayOf(15, 0x0D) + idm + byteArrayOf(2, 0, 0x10, 0, 0x20)
                    0x02 -> {
                        val count = command[10].toInt() and 0xFF
                        byteArrayOf((11 + count * 2).toByte(), 0x03) + idm + byteArrayOf(count.toByte()) + ByteArray(count * 2) { 0xFF.toByte() }
                    }
                    else -> error("Unexpected FeliCa command")
                }
            },
            context = context,
        )

        val serviceCommands = commands.filter { (it[1].toInt() and 0xFF) == 0x02 }
        val secondSystemPoll = commands.indexOfFirst {
            (it[1].toInt() and 0xFF) == 0x00 && (((it[2].toInt() and 0xFF) shl 8) or (it[3].toInt() and 0xFF)) == 0x20
        }
        assertEquals(64, serviceCommands.size)
        assertTrue(secondSystemPoll >= 0)
        assertEquals(32, serviceCommands.count { command -> commands.indexOf(command) < secondSystemPoll })
        assertEquals(32, serviceCommands.count { command -> commands.indexOf(command) > secondSystemPoll })
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
