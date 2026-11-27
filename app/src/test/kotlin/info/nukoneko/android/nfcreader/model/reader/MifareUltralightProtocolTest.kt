package info.nukoneko.android.nfcreader.model.reader

import info.nukoneko.android.nfcreader.model.entity.ReadOutcome
import info.nukoneko.android.nfcreader.model.entity.ReadIssueKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

class MifareUltralightProtocolTest {
    @Test
    fun recognizesOnlyExactEightByteVersionProfiles() {
        val profiles = listOf(
            byteArrayOf(0, 4, 4, 2, 1, 0, 0x0F, 3) to "NTAG213",
            byteArrayOf(0, 4, 4, 2, 1, 0, 0x11, 3) to "NTAG215",
            byteArrayOf(0, 4, 4, 2, 1, 0, 0x13, 3) to "NTAG216",
            byteArrayOf(0, 4, 3, 1, 1, 0, 0x0B, 3) to "Ultralight EV1 MF0UL11",
            byteArrayOf(0, 4, 3, 2, 1, 0, 0x0E, 3) to "Ultralight EV1 MF0UL21",
        )
        profiles.forEach { (version, name) ->
            assertEquals(name, MifareUltralightProtocol.parseVersion(version)?.name)
        }
        assertNull(MifareUltralightProtocol.parseVersion(byteArrayOf(0, 4, 4, 2, 1, 0, 0x0F)))
        assertNull(MifareUltralightProtocol.parseVersion(byteArrayOf(0, 4, 4, 2, 1, 0, 0x0F, 2)))
    }

    @Test
    fun ntag213UsesFourPageFastReadChunksThroughItsLastPage() {
        val commands = mutableListOf<ByteArray>()
        val transport = ReadOnlyTransport { command ->
            commands += command.copyOf()
            when (command[0].toInt() and 0xFF) {
                0x60 -> byteArrayOf(0, 4, 4, 2, 1, 0, 0x0F, 3)
                0x3A -> {
                    val start = command[1].toInt() and 0xFF
                    val end = command[2].toInt() and 0xFF
                    ByteArray((end - start + 1) * 4) { index -> (start * 4 + index).toByte() }
                }
                else -> error("Unexpected READ command")
            }
        }
        val context = newContext()

        MifareUltralightProtocol.read(transport, context)

        val fastReads = commands.filter { (it[0].toInt() and 0xFF) == 0x3A }
        assertEquals(12, fastReads.size)
        assertEquals(0x2C, fastReads.last()[2].toInt() and 0xFF)
        assertEquals(ReadOutcome.PARTIAL, context.buildResult().outcome)
        assertTrue(context.buildResult().decodedFields.any { it.label == "Ultralight.page[44]" })
    }

    @Test
    fun pwdAndPackPagesRemainRawButAreReportedAsUnknownForKnownProfiles() {
        val profiles = listOf(
            Triple(byteArrayOf(0, 4, 4, 2, 1, 0, 0x0F, 3), 0x2B, 0x2C),
            Triple(byteArrayOf(0, 4, 4, 2, 1, 0, 0x11, 3), 0x85, 0x86),
            Triple(byteArrayOf(0, 4, 4, 2, 1, 0, 0x13, 3), 0xE5, 0xE6),
            Triple(byteArrayOf(0, 4, 3, 1, 1, 0, 0x0B, 3), 0x12, 0x13),
            Triple(byteArrayOf(0, 4, 3, 2, 1, 0, 0x0E, 3), 0x27, 0x28),
        )

        profiles.forEach { (version, pwdPage, packPage) ->
            val context = newContext()
            val transport = ReadOnlyTransport { command ->
                when (command[0].toInt() and 0xFF) {
                    0x60 -> version
                    0x3A -> {
                        val start = command[1].toInt() and 0xFF
                        val end = command[2].toInt() and 0xFF
                        ByteArray((end - start + 1) * MifareUltralightProtocol.PAGE_SIZE) { index ->
                            val page = start + index / MifareUltralightProtocol.PAGE_SIZE
                            if (page == pwdPage || page == packPage) 0.toByte() else (page + 1).toByte()
                        }
                    }
                    else -> error("Unexpected command")
                }
            }

            MifareUltralightProtocol.read(transport, context)

            val result = context.buildResult()
            assertEquals(ReadOutcome.PARTIAL, result.outcome)
            assertFalse(result.coverage.isComplete)
            listOf(pwdPage, packPage).forEach { page ->
                assertTrue(result.unreadRanges.any {
                    it.startInclusive == page.toLong() && it.endExclusive == page + 1L
                })
                assertTrue(result.coverage.completedRanges.none {
                    it.startInclusive <= page.toLong() && it.endExclusive > page.toLong()
                })
                assertTrue(result.rawChunks.any {
                    it.label == "MIFARE masked PWD/PACK page response" &&
                        it.location == "page $page" && it.hex == "00000000"
                })
                assertTrue(result.decodedFields.any {
                    it.label == "Ultralight.page[$page]" && it.value?.contains("unknown") == true
                })
            }
            assertTrue(result.issues.any { it.kind == ReadIssueKind.DATA_REDACTED })
        }
    }

    @Test
    fun malformedFastReadChunkFallsBackToSinglePageReads() {
        val commands = mutableListOf<ByteArray>()
        var firstFastRead = true
        val transport = ReadOnlyTransport { command ->
            commands += command.copyOf()
            when (command[0].toInt() and 0xFF) {
                0x60 -> byteArrayOf(0, 4, 4, 2, 1, 0, 0x0F, 3)
                0x3A -> {
                    if (firstFastRead) {
                        firstFastRead = false
                        byteArrayOf(0x00)
                    } else {
                        val start = command[1].toInt() and 0xFF
                        val end = command[2].toInt() and 0xFF
                        ByteArray((end - start + 1) * 4) { index -> (start * 4 + index).toByte() }
                    }
                }
                0x30 -> ByteArray(16) { index -> (command[1] + index).toByte() }
                else -> error("Unexpected command")
            }
        }
        val context = newContext()

        MifareUltralightProtocol.read(transport, context)

        val firstChunkCommands = commands.drop(1).take(5).map { it[0].toInt() and 0xFF }
        assertEquals(listOf(0x3A, 0x30, 0x30, 0x30, 0x30), firstChunkCommands)
        assertEquals(4, commands.drop(1).take(5).drop(1).map { it[1].toInt() and 0xFF }.toSet().size)
        assertEquals(ReadOutcome.PARTIAL, context.buildResult().outcome)
    }

    @Test
    fun unknownVersionUsesBoundedReadAndNeverFastRead() {
        val commands = mutableListOf<ByteArray>()
        val transport = ReadOnlyTransport { command ->
            commands += command.copyOf()
            when (command[0].toInt() and 0xFF) {
                0x60 -> byteArrayOf(0, 4, 9, 9, 9, 9, 9, 9)
                0x30 -> ByteArray(16) { command[1] }
                else -> error("Unknown profiles must not use FAST_READ")
            }
        }
        val context = newContext()

        MifareUltralightProtocol.read(transport, context)

        assertEquals(256, commands.count { (it[0].toInt() and 0xFF) == 0x30 })
        assertTrue(commands.none { (it[0].toInt() and 0xFF) == 0x3A })
        assertEquals(ReadOutcome.PARTIAL, context.buildResult().outcome)
    }

    @Test
    fun cancelledKnownProfileRetainsFastReadPagesAndUnreadTail() {
        var cancelled = false
        val commands = mutableListOf<ByteArray>()
        val context = newContext { if (cancelled) throw CancellationException("cancelled") }
        val transport = ReadOnlyTransport { command ->
            commands += command.copyOf()
            when (command[0].toInt() and 0xFF) {
                0x60 -> byteArrayOf(0, 4, 4, 2, 1, 0, 0x0F, 3)
                0x3A -> {
                    cancelled = true
                    ByteArray(16)
                }
                else -> error("Unexpected command")
            }
        }

        try {
            MifareUltralightProtocol.read(transport, context)
        } catch (_: CancellationException) {
        }

        val result = context.buildResult()
        assertEquals(2, commands.size)
        assertEquals(4, result.coverage.completedRanges.size)
        assertEquals(0L, result.coverage.completedRanges.first().startInclusive)
        assertEquals(4L, result.coverage.completedRanges.last().endExclusive)
        assertTrue(result.unreadRanges.any { it.startInclusive == 4L && it.endExclusive == 45L })
    }

    @Test
    fun cancelledAfterMaskedPageResponseStartsUnreadTailAfterAcquiredPages() {
        var cancelled = false
        val commands = mutableListOf<ByteArray>()
        val context = newContext { if (cancelled) throw CancellationException("cancelled") }
        val transport = ReadOnlyTransport { command ->
            commands += command.copyOf()
            when (command[0].toInt() and 0xFF) {
                0x60 -> byteArrayOf(0, 4, 4, 2, 1, 0, 0x0F, 3)
                0x3A -> {
                    val start = command[1].toInt() and 0xFF
                    val end = command[2].toInt() and 0xFF
                    if (end == 43) cancelled = true
                    ByteArray((end - start + 1) * MifareUltralightProtocol.PAGE_SIZE) { index ->
                        val page = start + index / MifareUltralightProtocol.PAGE_SIZE
                        if (page == 43) 0.toByte() else (page + 1).toByte()
                    }
                }
                else -> error("Unexpected command")
            }
        }

        try {
            MifareUltralightProtocol.read(transport, context)
        } catch (_: CancellationException) {
        }

        val result = context.buildResult()
        assertEquals(12, commands.size)
        assertTrue(result.rawChunks.any {
            it.label == "MIFARE masked PWD/PACK page response" && it.location == "page 43"
        })
        assertTrue(result.unreadRanges.any {
            it.scope == "NTAG213 PWD/PACK pages" && it.startInclusive == 43L && it.endExclusive == 44L
        })
        assertTrue(result.unreadRanges.any {
            it.scope == "NTAG213 pages" && it.startInclusive == 44L && it.endExclusive == 45L
        })
        assertTrue(result.unreadRanges.none {
            it.scope == "NTAG213 pages" && it.startInclusive == 43L && it.endExclusive == 45L
        })
        assertTrue(result.coverage.completedRanges.none {
            it.startInclusive <= 43L && it.endExclusive > 43L
        })
    }

    @Test
    fun budgetAfterMaskedPageResponseKeepsCoverageAndUnreadTailBeforeRethrowing() {
        val commands = mutableListOf<ByteArray>()
        val context = newContext(limits = TechReadLimits(timeoutMillis = 30_000, maxTransceives = 12))
        val transport = ReadOnlyTransport { command ->
            commands += command.copyOf()
            when (command[0].toInt() and 0xFF) {
                0x60 -> byteArrayOf(0, 4, 4, 2, 1, 0, 0x0F, 3)
                0x3A -> {
                    val start = command[1].toInt() and 0xFF
                    val end = command[2].toInt() and 0xFF
                    ByteArray((end - start + 1) * MifareUltralightProtocol.PAGE_SIZE) { index ->
                        val page = start + index / MifareUltralightProtocol.PAGE_SIZE
                        if (page == 43) 0.toByte() else (page + 1).toByte()
                    }
                }
                else -> error("Unexpected command")
            }
        }
        var budgetExceeded = false

        try {
            MifareUltralightProtocol.read(context.budgetedTransport(transport, "Ultralight test"), context)
        } catch (_: ReadBudgetExceededException) {
            budgetExceeded = true
        }

        val result = context.buildResult()
        assertTrue(budgetExceeded)
        assertEquals(12, commands.size)
        assertTrue(result.rawChunks.any {
            it.label == "MIFARE masked PWD/PACK page response" && it.location == "page 43"
        })
        assertTrue(result.unreadRanges.any {
            it.scope == "NTAG213 PWD/PACK pages" && it.startInclusive == 43L && it.endExclusive == 44L
        })
        assertTrue(result.unreadRanges.any {
            it.scope == "NTAG213 pages" && it.startInclusive == 44L && it.endExclusive == 45L
        })
        assertTrue(result.coverage.completedRanges.any { it.startInclusive == 42L && it.endExclusive == 43L })
        assertTrue(result.coverage.completedRanges.none {
            it.startInclusive <= 43L && it.endExclusive > 43L
        })
        assertTrue(result.issues.any { it.kind == ReadIssueKind.BUDGET_EXHAUSTED })
    }

    @Test
    fun cancelledUnknownCapacityRemainsPartialAndRecordsUnreadPages() {
        var cancelled = false
        val context = newContext { if (cancelled) throw CancellationException("cancelled") }
        val transport = ReadOnlyTransport { command ->
            when (command[0].toInt() and 0xFF) {
                0x60 -> byteArrayOf(0, 4, 9, 9, 9, 9, 9, 9)
                0x30 -> {
                    cancelled = true
                    ByteArray(16)
                }
                else -> error("Unexpected command")
            }
        }

        try {
            MifareUltralightProtocol.read(transport, context)
        } catch (_: CancellationException) {
        }

        val result = context.buildResult()
        assertTrue(result.coverage.expectedRange != null)
        assertTrue(!result.coverage.isComplete)
        assertTrue(result.unreadRanges.any { it.startInclusive == 1L && it.endExclusive == 256L })
        assertTrue(result.issues.any { it.kind == info.nukoneko.android.nfcreader.model.entity.ReadIssueKind.COVERAGE_UNKNOWN })
    }

    private fun newContext(
        limits: TechReadLimits = TechReadLimits(timeoutMillis = 30_000, maxTransceives = 4096),
        cancellationCheck: () -> Unit = {},
    ) = TechReadContext(
        rawBudget = RawCaptureBudget(64 * 1024),
        fieldBudget = FieldValueBudget(64 * 1024),
        cancellationCheck = cancellationCheck,
        limits = limits,
    )
}
