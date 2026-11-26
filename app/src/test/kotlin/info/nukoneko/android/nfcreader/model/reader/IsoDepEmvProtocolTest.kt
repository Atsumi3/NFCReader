package info.nukoneko.android.nfcreader.model.reader

import android.os.Build
import info.nukoneko.android.nfcreader.extensions.hexToBytes
import info.nukoneko.android.nfcreader.extensions.toHexString
import info.nukoneko.android.nfcreader.model.entity.ReadIssueKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CancellationException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
class IsoDepEmvProtocolTest {
    @Test
    fun readerUsesOnlyKnownAidGetDataAndBoundedReadRecordCommands() {
        val visa = hexToBytes("A0000000031010")
        val unknown = hexToBytes("D2760000850101")
        val commands = mutableListOf<ByteArray>()
        val context = newContext()
        IsoDepEmvReader().readWithTransport(
            transport = ReadOnlyTransport { command ->
                commands += command.copyOf()
                when {
                    command.contentEquals(SELECT_PPSE) -> ppseResponse(visa, unknown)
                    (command[1].toInt() and 0xFF) == 0xA4 -> {
                        val aidLength = command[4].toInt() and 0xFF
                        val aid = command.copyOfRange(5, 5 + aidLength)
                        fciResponse(aid, if (aid.contentEquals(visa)) byteArrayOf(1, 2) else null)
                    }
                    (command[0].toInt() and 0xFF) == 0x80 && (command[1].toInt() and 0xFF) == 0xCA ->
                        getDataResponse(((command[2].toInt() and 0xFF) shl 8) or (command[3].toInt() and 0xFF))
                    (command[1].toInt() and 0xFF) == 0xB2 -> {
                        val record = command[2].toInt() and 0xFF
                        val sfi = (command[3].toInt() and 0xFF) ushr 3
                        when {
                            sfi == 1 && record == 1 -> status(hexToBytes("010203AABBCCDDEEFF0001"))
                            sfi == 1 && record == 2 -> status(hexToBytes("010203AABBCCDDEEFF0001FF"))
                            sfi == 1 && record == 3 -> hexToBytes("6A83")
                            else -> hexToBytes("6A82")
                        }
                    }
                    else -> error("Unexpected APDU: " + command.toHexString())
                }
            },
            maxTransceiveLength = 255,
            context = context,
        )

        val getDataCommands = commands.filter {
            it.size == 5 && (it[0].toInt() and 0xFF) == 0x80 && (it[1].toInt() and 0xFF) == 0xCA
        }
        assertEquals(listOf(0x9F36, 0x9F13, 0x9F17, 0x9F4F), getDataCommands.map {
            ((it[2].toInt() and 0xFF) shl 8) or (it[3].toInt() and 0xFF)
        })
        val readRecords = commands.filter { (it[1].toInt() and 0xFF) == 0xB2 }
        assertTrue(readRecords.isNotEmpty())
        assertTrue(readRecords.all {
            val sfi = (it[3].toInt() and 0xFF) ushr 3
            val record = it[2].toInt() and 0xFF
            sfi in 1..10 && record in 1..16
        })
        assertFalse(commands.any { it.size > 1 && (it[1].toInt() and 0xFF) == 0xA8 })
        assertEquals(
            "01 02 03",
            context.buildResult().decodedFields
                .first { it.label == "IsoDep.app[0].record[1,1].log.9A" }.value,
        )
        assertTrue(context.buildResult().decodedFields.any { it.label == "IsoDep.app[0].record[1,2].log.extraByteCount" && it.value == "1" })
        assertTrue(context.buildResult().rawChunks.any { it.label == "ISO-DEP log record bytes beyond 9F4F DOL" })

        val unknownSelectIndex = commands.indexOfFirst { it.contentEquals(buildSelect(unknown)) }
        assertTrue(unknownSelectIndex >= 0)
        assertTrue(commands.drop(unknownSelectIndex + 1).none {
            (it[1].toInt() and 0xFF) == 0xCA || (it[1].toInt() and 0xFF) == 0xB2
        })
    }

    @Test
    fun follows61ContinuationAndCorrects6cLeThroughTheReaderSeam() {
        val commands = mutableListOf<ByteArray>()
        val context = newContext()
        val ppseBody = ppseResponse().let { it.copyOfRange(0, it.size - 2) }
        val splitAt = ppseBody.size / 2
        IsoDepEmvReader().readWithTransport(
            transport = ReadOnlyTransport { command ->
                commands += command.copyOf()
                when {
                    command.contentEquals(SELECT_PPSE) ->
                        ppseBody.copyOfRange(0, splitAt) + hexToBytes("6108")
                    command.contentEquals(hexToBytes("00C0000008")) -> hexToBytes("6C04")
                    command.contentEquals(hexToBytes("00C0000004")) ->
                        ppseBody.copyOfRange(splitAt, ppseBody.size) + hexToBytes("9000")
                    else -> error("Unexpected APDU: " + command.toHexString())
                }
            },
            maxTransceiveLength = 255,
            context = context,
        )

        assertEquals(3, commands.size)
        assertArrayEquals(SELECT_PPSE, commands[0])
        assertArrayEquals(hexToBytes("00C0000008"), commands[1])
        assertArrayEquals(hexToBytes("00C0000004"), commands[2])
        assertTrue(context.buildResult().rawChunks.any { it.hex.endsWith("6108") })
        assertTrue(context.buildResult().rawChunks.any { it.hex.endsWith("6C04") })
        assertFalse(commands.any { it.size > 1 && (it[1].toInt() and 0xFF) == 0xA8 })
    }

    @Test
    fun malformedShortResponseIsRetainedAndStopsFurtherSends() {
        val commands = mutableListOf<ByteArray>()
        val context = newContext()
        IsoDepEmvReader().readWithTransport(
            transport = ReadOnlyTransport { command ->
                commands += command.copyOf()
                byteArrayOf(0x90.toByte())
            },
            maxTransceiveLength = 255,
            context = context,
        )

        assertEquals(1, commands.size)
        assertTrue(context.buildResult().issues.any { it.kind == ReadIssueKind.MALFORMED_RESPONSE })
        assertTrue(context.buildResult().rawChunks.any { it.label.contains("APDU response") && it.hex == "90" })
    }

    @Test
    fun cancellationAfterPpseKeepsRawAndMarksRemainingApdusUnread() {
        val commands = mutableListOf<ByteArray>()
        var cancelled = false
        val context = newContext { if (cancelled) throw CancellationException("cancelled") }

        try {
            IsoDepEmvReader().readWithTransport(
                transport = ReadOnlyTransport { command ->
                    commands += command.copyOf()
                    if (command.contentEquals(SELECT_PPSE)) {
                        cancelled = true
                        ppseResponse(hexToBytes("A0000000031010"))
                    } else {
                        error("No APDU may be sent after cancellation")
                    }
                },
                maxTransceiveLength = 255,
                context = context,
            )
        } catch (_: CancellationException) {
        }

        val result = context.buildResult()
        assertEquals(1, commands.size)
        assertTrue(result.rawChunks.any { it.label == "ISO-DEP APDU response (data and status word)" })
        assertTrue(result.unreadRanges.any { it.reason.contains("cancelled", ignoreCase = true) })
        assertTrue(result.coverage.expectedRange == null)
    }

    @Test
    fun deadlineAfterPpseKeepsRawAndStopsFurtherApdus() {
        val visa = hexToBytes("A0000000031010")
        val commands = mutableListOf<ByteArray>()
        var nowNanos = 0L
        var budgetThrown = false
        val context = TechReadContext(
            RawCaptureBudget(),
            FieldValueBudget(),
            cancellationCheck = {},
            limits = TechReadLimits(timeoutMillis = 1, maxTransceives = 16),
            clockNanos = { nowNanos },
        )

        try {
            IsoDepEmvReader().readWithTransport(
                transport = ReadOnlyTransport { command ->
                    commands += command.copyOf()
                    if (command.contentEquals(SELECT_PPSE)) {
                        nowNanos = 1_000_000L
                        ppseResponse(visa)
                    } else {
                        error("No APDU may be sent after the deadline.")
                    }
                },
                maxTransceiveLength = 255,
                context = context,
            )
        } catch (_: ReadBudgetExceededException) {
            budgetThrown = true
        }

        val result = context.buildResult()
        assertTrue(budgetThrown)
        assertEquals(1, commands.size)
        assertTrue(result.rawChunks.any { it.label.startsWith("ISO-DEP APDU response") })
        assertTrue(result.issues.any { it.kind == ReadIssueKind.BUDGET_EXHAUSTED })
        assertTrue(result.unreadRanges.any { it.reason.contains("budget", ignoreCase = true) })
        assertTrue(result.coverage.expectedRange == null)
    }

    @Test
    fun advertisedLogSfi30ReadsOnlyItsAdvertisedRecordCount() {
        val visa = hexToBytes("A0000000031010")
        val commands = mutableListOf<ByteArray>()
        val context = newContext()
        IsoDepEmvReader().readWithTransport(
            transport = ReadOnlyTransport { command ->
                commands += command.copyOf()
                when {
                    command.contentEquals(SELECT_PPSE) -> ppseResponse(visa)
                    (command[1].toInt() and 0xFF) == 0xA4 -> fciResponse(visa, byteArrayOf(30, 17))
                    (command[0].toInt() and 0xFF) == 0x80 && (command[1].toInt() and 0xFF) == 0xCA ->
                        getDataResponse(((command[2].toInt() and 0xFF) shl 8) or (command[3].toInt() and 0xFF))
                    (command[1].toInt() and 0xFF) == 0xB2 -> {
                        val record = command[2].toInt() and 0xFF
                        val sfi = (command[3].toInt() and 0xFF) ushr 3
                        if (sfi == 30 && record in 1..17) hexToBytes("9000") else hexToBytes("6A82")
                    }
                    else -> error("Unexpected APDU")
                }
            },
            maxTransceiveLength = 255,
            context = context,
        )

        val recordCommands = commands.filter { (it[1].toInt() and 0xFF) == 0xB2 }
        assertTrue(recordCommands.isNotEmpty())
        assertTrue(recordCommands.any {
            ((it[3].toInt() and 0xFF) ushr 3) == 30 &&
                (it[2].toInt() and 0xFF) == 1 &&
                (it[3].toInt() and 0xFF) == 0xF4
        })
        assertEquals(
            (1..17).toList(),
            recordCommands.filter { ((it[3].toInt() and 0xFF) ushr 3) == 30 }
                .map { it[2].toInt() and 0xFF },
        )
        assertTrue(recordCommands.none { ((it[3].toInt() and 0xFF) ushr 3) in 11..29 })
        assertTrue(context.buildResult().issues.none { it.message.contains("outside the bounded SFI") })
    }

    @Test
    fun advertisedLogRecordCountStopsAtPerApplicationRequestBudget() {
        val visa = hexToBytes("A0000000031010")
        val commands = mutableListOf<ByteArray>()
        val context = newContext()
        IsoDepEmvReader().readWithTransport(
            transport = ReadOnlyTransport { command ->
                commands += command.copyOf()
                when {
                    command.contentEquals(SELECT_PPSE) -> ppseResponse(visa)
                    (command[1].toInt() and 0xFF) == 0xA4 -> fciResponse(visa, byteArrayOf(30, 0xFF.toByte()))
                    (command[0].toInt() and 0xFF) == 0x80 && (command[1].toInt() and 0xFF) == 0xCA ->
                        getDataResponse(((command[2].toInt() and 0xFF) shl 8) or (command[3].toInt() and 0xFF))
                    (command[1].toInt() and 0xFF) == 0xB2 -> {
                        val sfi = (command[3].toInt() and 0xFF) ushr 3
                        if (sfi == 30) hexToBytes("9000") else hexToBytes("6A82")
                    }
                    else -> error("Unexpected APDU")
                }
            },
            maxTransceiveLength = 255,
            context = context,
        )

        val recordCommands = commands.filter { (it[1].toInt() and 0xFF) == 0xB2 }
        val logRecords = recordCommands.filter { ((it[3].toInt() and 0xFF) ushr 3) == 30 }
        val result = context.buildResult()
        assertEquals(160, recordCommands.size)
        assertEquals((1..160).toList(), logRecords.map { it[2].toInt() and 0xFF })
        assertTrue(recordCommands.all { ((it[3].toInt() and 0xFF) ushr 3) == 30 })
        assertTrue(result.issues.any { it.kind == ReadIssueKind.BUDGET_EXHAUSTED })
        assertTrue(result.unreadRanges.any {
            it.scope == "IsoDep.app[0] log SFI 30 records" &&
                it.startInclusive == 161L && it.endExclusive == 256L
        })
        val genericUnreadRanges = result.unreadRanges.filter { it.reason.contains("bounded exploratory records") }
        assertEquals(10, genericUnreadRanges.size)
        for (sfi in 1..10) {
            assertTrue(genericUnreadRanges.any {
                it.scope == "IsoDep.app[0] SFI $sfi records" &&
                    it.startInclusive == 1L && it.endExclusive == 17L &&
                    it.reason.contains("physical record presence is not established")
            })
        }
    }

    @Test
    fun advertisedLogSfiIsReadBeforeSuccessfulGenericScans() {
        val visa = hexToBytes("A0000000031010")
        val commands = mutableListOf<ByteArray>()
        val context = newContext()
        IsoDepEmvReader().readWithTransport(
            transport = ReadOnlyTransport { command ->
                commands += command.copyOf()
                when {
                    command.contentEquals(SELECT_PPSE) -> ppseResponse(visa)
                    (command[1].toInt() and 0xFF) == 0xA4 -> fciResponse(visa, byteArrayOf(30, 17))
                    (command[0].toInt() and 0xFF) == 0x80 && (command[1].toInt() and 0xFF) == 0xCA ->
                        getDataResponse(((command[2].toInt() and 0xFF) shl 8) or (command[3].toInt() and 0xFF))
                    (command[1].toInt() and 0xFF) == 0xB2 -> hexToBytes("9000")
                    else -> error("Unexpected APDU")
                }
            },
            maxTransceiveLength = 255,
            context = context,
        )

        val recordCommands = commands.filter { (it[1].toInt() and 0xFF) == 0xB2 }
        val firstRecords = recordCommands.take(17)
        val logRecords = recordCommands.filter { ((it[3].toInt() and 0xFF) ushr 3) == 30 }
        val result = context.buildResult()
        assertEquals(160, recordCommands.size)
        assertTrue(firstRecords.all { ((it[3].toInt() and 0xFF) ushr 3) == 30 })
        assertEquals((1..17).toList(), logRecords.map { it[2].toInt() and 0xFF })
        assertEquals(143, recordCommands.count { ((it[3].toInt() and 0xFF) ushr 3) != 30 })
        assertTrue(result.issues.any { it.kind == ReadIssueKind.BUDGET_EXHAUSTED })
        assertTrue(result.unreadRanges.none { it.scope == "IsoDep.app[0] log SFI 30 records" })
        val genericUnreadRanges = result.unreadRanges.filter { it.reason.contains("bounded exploratory records") }
        assertEquals(2, genericUnreadRanges.size)
        assertTrue(genericUnreadRanges.any {
            it.scope == "IsoDep.app[0] SFI 9 records" &&
                it.startInclusive == 16L && it.endExclusive == 17L
        })
        assertTrue(genericUnreadRanges.any {
            it.scope == "IsoDep.app[0] SFI 10 records" &&
                it.startInclusive == 1L && it.endExclusive == 17L
        })
        assertTrue(genericUnreadRanges.all { it.reason.contains("physical record presence is not established") })
    }

    @Test
    fun advertisedLogSfiWithinGeneralRangeUsesAdvertisedRecordCount() {
        val visa = hexToBytes("A0000000031010")
        val commands = mutableListOf<ByteArray>()
        IsoDepEmvReader().readWithTransport(
            transport = ReadOnlyTransport { command ->
                commands += command.copyOf()
                when {
                    command.contentEquals(SELECT_PPSE) -> ppseResponse(visa)
                    (command[1].toInt() and 0xFF) == 0xA4 -> fciResponse(visa, byteArrayOf(5, 2))
                    (command[0].toInt() and 0xFF) == 0x80 && (command[1].toInt() and 0xFF) == 0xCA ->
                        getDataResponse(((command[2].toInt() and 0xFF) shl 8) or (command[3].toInt() and 0xFF))
                    (command[1].toInt() and 0xFF) == 0xB2 -> {
                        val record = command[2].toInt() and 0xFF
                        val sfi = (command[3].toInt() and 0xFF) ushr 3
                        if (sfi == 5 && record in 1..2) hexToBytes("9000") else hexToBytes("6A83")
                    }
                    else -> error("Unexpected APDU")
                }
            },
            maxTransceiveLength = 255,
            context = newContext(),
        )

        val advertisedRecords = commands.filter {
            (it[1].toInt() and 0xFF) == 0xB2 && ((it[3].toInt() and 0xFF) ushr 3) == 5
        }
        assertEquals(listOf(1, 2), advertisedRecords.map { it[2].toInt() and 0xFF })
    }

    @Test
    fun transmitLimitRejectsPpseBeforeSending() {
        val commands = mutableListOf<ByteArray>()
        val context = newContext()
        IsoDepEmvReader().readWithTransport(
            transport = ReadOnlyTransport { command ->
                commands += command.copyOf()
                hexToBytes("9000")
            },
            maxTransceiveLength = 4,
            context = context,
        )

        assertTrue(commands.isEmpty())
        assertTrue(context.buildResult().issues.any { it.kind == ReadIssueKind.UNSUPPORTED_TECHNOLOGY })
    }

    private fun newContext(cancellationCheck: () -> Unit = {}) = TechReadContext(
        RawCaptureBudget(),
        FieldValueBudget(),
        cancellationCheck = cancellationCheck,
    )

    private fun status(data: ByteArray = byteArrayOf()) = data + hexToBytes("9000")

    private fun getDataResponse(tag: Int): ByteArray = when (tag) {
        0x9F36 -> status(tlv("9F36", hexToBytes("0001")))
        0x9F13 -> status(tlv("9F13", hexToBytes("0000")))
        0x9F17 -> status(tlv("9F17", hexToBytes("03")))
        0x9F4F -> status(tlv("9F4F", hexToBytes("9A039F02065F2A02")))
        else -> hexToBytes("6A88")
    }

    private fun ppseResponse(vararg aids: ByteArray): ByteArray {
        val entries = aids.map { aid -> tlv("61", tlv("4F", aid) + tlv("87", byteArrayOf(1))) }
            .fold(ByteArray(0)) { all, entry -> all + entry }
        val fciValue = tlv("84", "2PAY.SYS.DDF01".toByteArray(Charsets.US_ASCII)) + tlv("A5", tlv("BF0C", entries))
        return status(tlv("6F", fciValue))
    }

    private fun fciResponse(aid: ByteArray, logEntry: ByteArray?): ByteArray {
        val proprietary = logEntry?.let { tlv("9F4D", it) } ?: ByteArray(0)
        val fciValue = tlv("84", aid) + tlv("A5", proprietary)
        return status(tlv("6F", fciValue))
    }

    private fun tlv(tag: String, value: ByteArray): ByteArray {
        require(value.size < 0x80)
        return hexToBytes(tag) + byteArrayOf(value.size.toByte()) + value
    }

    private companion object {
        val SELECT_PPSE = hexToBytes("00A404000E325041592E5359532E444446303100")
    }
}
