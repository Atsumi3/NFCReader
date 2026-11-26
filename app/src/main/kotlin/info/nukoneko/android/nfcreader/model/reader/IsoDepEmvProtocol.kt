package info.nukoneko.android.nfcreader.model.reader

import android.nfc.TagLostException
import info.nukoneko.android.nfcreader.extensions.hexToBytes
import info.nukoneko.android.nfcreader.extensions.toHexString
import info.nukoneko.android.nfcreader.extensions.toHexStringCompact
import info.nukoneko.android.nfcreader.model.entity.NfcField
import info.nukoneko.android.nfcreader.model.entity.ReadCoverage
import info.nukoneko.android.nfcreader.model.entity.ReadIssue
import info.nukoneko.android.nfcreader.model.entity.ReadIssueKind
import info.nukoneko.android.nfcreader.model.entity.UnreadRange
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException

internal class IsoDepEmvProtocol(
    private val maxTransceiveLength: Int,
) {
    fun read(transport: ReadOnlyTransport, context: TechReadContext) {
        val session = ApduSession(transport, context, maxTransceiveLength)
        try {
        val ppse = session.exchange(SELECT_PPSE, "SELECT PPSE")
        if (ppse == null) {
            finish(context)
            return
        }
        context.addDecodedFields(listOf(NfcField("IsoDep.PPSE.statusWord", ppse.statusWord)))
        if (!ppse.isSuccess) {
            addStatusIssue(context, "SELECT PPSE", ppse.statusWord)
            finish(context)
            return
        }
        val ppseTlv = parseResponseTlv("IsoDep.PPSE", ppse.data, context)
        context.addDecodedFields(dumpTlv("IsoDep.PPSE", ppseTlv.nodes))
        if (ppseTlv.issues.isNotEmpty()) {
            finish(context)
            return
        }
        val advertisedAids = extractAids(ppseTlv.nodes).distinctBy { it.toHexStringCompact() }
        val aids = advertisedAids.filter { it.size in MIN_AID_BYTES..MAX_AID_BYTES }
        if (aids.size != advertisedAids.size) {
            context.addIssue(
                ReadIssue(
                    ReadIssueKind.MALFORMED_RESPONSE,
                    "PPSE contained an AID outside the five-to-sixteen-byte application-selection bound.",
                    "SELECT PPSE",
                )
            )
            context.addUnreadRange(UnreadRange("ISO-DEP PPSE AIDs", "AID", reason = "Invalid AID length; not selected."))
        }
        if (aids.isEmpty()) {
            context.addIssue(ReadIssue(ReadIssueKind.COVERAGE_UNKNOWN, "PPSE did not advertise a valid AID.", "SELECT PPSE"))
            finish(context)
            return
        }
        if (aids.size > MAX_AIDS) {
            context.addIssue(ReadIssue(ReadIssueKind.BUDGET_EXHAUSTED, "Only the first $MAX_AIDS advertised AIDs are selected.", "SELECT PPSE AID list"))
            context.addUnreadRange(UnreadRange("ISO-DEP PPSE AIDs", "AID", MAX_AIDS.toLong(), aids.size.toLong(), "Per-tag application limit reached."))
        }

        for ((index, aid) in aids.take(MAX_AIDS).withIndex()) {
            if (session.stopped) break
            context.checkpoint()
            val prefix = "IsoDep.app[$index]"
            val scheme = cardScheme(aid)
            context.addDecodedFields(
                listOf(
                    NfcField("$prefix.AID", aid.toHexString()),
                    NfcField("$prefix.scheme", scheme),
                )
            )
            val select = session.exchange(buildSelect(aid), "SELECT AID " + aid.toHexString())
            if (select == null) {
                if (session.stopped) break
                continue
            }
            context.addDecodedFields(listOf(NfcField("$prefix.selectStatusWord", select.statusWord)))
            if (!select.isSuccess) {
                addStatusIssue(context, "$prefix SELECT", select.statusWord)
                context.addUnreadRange(UnreadRange(prefix, "application", reason = "SELECT status word " + select.statusWord + "."))
                continue
            }

            val fci = parseResponseTlv("$prefix.FCI", select.data, context)
            context.addDecodedFields(dumpTlv("$prefix.FCI", fci.nodes))
            val logEntry = parseLogEntry(fci.nodes, prefix, context)
            if (scheme == UNKNOWN_SCHEME) {
                context.addDecodedFields(
                    listOf(NfcField("$prefix.readScope", "FCI metadata only; unknown AID, no GET DATA or READ RECORD sent"))
                )
                continue
            }

            var logDol: DolParseResult? = null
            for (tag in GET_DATA_TAGS) {
                if (session.stopped) break
                val tagHex = "%04X".format(tag)
                val response = session.exchange(buildGetData(tag), "$prefix GET DATA $tagHex")
                if (response == null) {
                    if (session.stopped) break
                    continue
                }
                context.addDecodedFields(listOf(NfcField("$prefix.GET_DATA_$tagHex.statusWord", response.statusWord)))
                if (!response.isSuccess) {
                    addStatusIssue(context, "$prefix GET DATA $tagHex", response.statusWord)
                    context.addUnreadRange(UnreadRange("$prefix GET DATA $tagHex", "APDU response", reason = "Status word " + response.statusWord + "."))
                    continue
                }
                val parsed = parseResponseTlv("$prefix.GET_DATA_$tagHex", response.data, context)
                context.addDecodedFields(dumpTlv("$prefix.GET_DATA_$tagHex", parsed.nodes))
                if (tag == LOG_FORMAT_TAG) {
                    logDol = parseLogFormat(parsed.nodes, prefix, context)
                }
            }
            if (session.stopped) break
            readRecords(session, prefix, logEntry, logDol, context)
        }
        finish(context)
        } catch (cancelled: CancellationException) {
            context.addUnreadRange(UnreadRange("ISO-DEP read-only exploration", "APDU", reason = "Read cancelled; no further APDUs were requested."))
            finish(context)
            throw cancelled
        }
    }

    private fun readRecords(
        session: ApduSession,
        prefix: String,
        logEntry: LogEntry?,
        logDol: DolParseResult?,
        context: TechReadContext,
    ) {
        var sentRecords = 0
        val sfis = (MIN_SFI..MAX_SFI).toMutableSet()
        logEntry?.sfi?.let(sfis::add)
        for (sfi in sfis.sorted()) {
            val maxRecord = if (sfi > MAX_SFI) logEntry?.maxRecords ?: 0 else MAX_RECORD
            for (record in MIN_RECORD..maxRecord) {
                if (session.stopped) return
                if (sentRecords >= MAX_RECORD_REQUESTS_PER_AID) {
                    context.addIssue(ReadIssue(ReadIssueKind.BUDGET_EXHAUSTED, "READ RECORD range cap reached for this application.", prefix))
                    context.addUnreadRange(UnreadRange(prefix + " SFI scan", "record request", sentRecords.toLong(), MAX_RECORD_REQUESTS_PER_AID.toLong(), "Per-application READ RECORD limit reached."))
                    return
                }
                context.checkpoint()
                val recordPath = prefix + ".record[" + sfi + "," + record + "]"
                val response = session.exchange(buildReadRecord(sfi, record), "READ RECORD SFI $sfi record $record")
                if (response == null) {
                    if (session.stopped) return
                    continue
                }
                sentRecords++
                context.addDecodedFields(listOf(NfcField("$recordPath.statusWord", response.statusWord)))
                if (!response.isSuccess) {
                    addStatusIssue(context, recordPath, response.statusWord)
                    context.addUnreadRange(UnreadRange(recordPath, "APDU response", reason = "Status word " + response.statusWord + "."))
                    if (response.statusWord == "6A82" || response.statusWord == "6A83") {
                        if (record < maxRecord) {
                            context.addUnreadRange(
                                UnreadRange(
                                    prefix + " SFI " + sfi + " records",
                                    "record",
                                    record.toLong() + 1L,
                                    maxRecord.toLong() + 1L,
                                    "Bounded SFI scan stopped after the card reported no file or record.",
                                )
                            )
                        }
                        break
                    }
                    continue
                }

                val isLogRecord = logEntry?.sfi == sfi && record <= logEntry.maxRecords
                if (isLogRecord) {
                    if (logDol == null) {
                        context.addIssue(ReadIssue(ReadIssueKind.COVERAGE_UNKNOWN, "Log record format is unavailable; record raw response retained.", recordPath))
                        context.addDecodedFields(listOf(NfcField("$recordPath.logFormat", "unknown; see raw APDU response")))
                    } else {
                        decodeLogRecord(recordPath, response.data, logDol, context)
                    }
                } else {
                    val parsed = parseResponseTlv(recordPath, response.data, context)
                    context.addDecodedFields(dumpTlv(recordPath, parsed.nodes))
                }
            }
        }
    }

    private fun parseLogEntry(nodes: List<Tlv>, prefix: String, context: TechReadContext): LogEntry? {
        val entry = nodes.flatten().firstOrNull { it.second.tag == LOG_ENTRY_TAG }?.second ?: return null
        context.addDecodedFields(listOf(NfcField("$prefix.logEntry.raw", entry.value.toHexString())))
        if (entry.value.size != LOG_ENTRY_LENGTH) {
            context.addIssue(ReadIssue(ReadIssueKind.MALFORMED_RESPONSE, "Log Entry 9F4D must contain exactly two bytes.", "$prefix FCI 9F4D"))
            context.addUnreadRange(UnreadRange("$prefix log records", "record", reason = "Malformed 9F4D prevents targeted log decoding."))
            return null
        }
        val sfi = entry.value[0].toInt() and 0xFF
        val recordCount = entry.value[1].toInt() and 0xFF
        context.addDecodedFields(
            listOf(
                NfcField("$prefix.logEntry.sfi", sfi.toString()),
                NfcField("$prefix.logEntry.maxRecords", recordCount.toString()),
            )
        )
        if (sfi !in MIN_SFI..MAX_EMV_SFI || recordCount == 0) {
            context.addIssue(ReadIssue(ReadIssueKind.MALFORMED_RESPONSE, "Log Entry SFI or record count is outside EMV bounds.", "$prefix FCI 9F4D"))
            context.addUnreadRange(UnreadRange("$prefix log records", "record", reason = "Invalid log SFI or record count; no log-targeted read."))
            return null
        }
        if (recordCount > MAX_RECORD) {
            context.addIssue(ReadIssue(ReadIssueKind.BUDGET_EXHAUSTED, "Log record count exceeds the bounded record 1..16 profile.", "$prefix FCI 9F4D"))
            context.addUnreadRange(UnreadRange("$prefix log SFI $sfi", "record", MAX_RECORD.toLong() + 1L, recordCount.toLong() + 1L, "Per-SFI record limit reached."))
        }
        return LogEntry(sfi, minOf(recordCount, MAX_RECORD))
    }

    private fun parseLogFormat(nodes: List<Tlv>, prefix: String, context: TechReadContext): DolParseResult? {
        val format = nodes.flatten().firstOrNull { it.second.tag == LOG_FORMAT_TAG_HEX }?.second
        if (format == null) {
            context.addIssue(ReadIssue(ReadIssueKind.COVERAGE_UNKNOWN, "GET DATA 9F4F did not return a 9F4F log-format object; raw response is retained.", "$prefix GET DATA 9F4F"))
            return null
        }
        val dol = parseDolBounded(format.value)
        dol.issues.forEach { issue ->
            context.addIssue(ReadIssue(ReadIssueKind.MALFORMED_RESPONSE, issue, "$prefix GET DATA 9F4F"))
            context.markPartial()
        }
        if (dol.remainder.isNotEmpty()) {
            context.captureRaw("ISO-DEP unparsed log DOL bytes", dol.remainder, "$prefix GET DATA 9F4F")
        }
        context.addDecodedFields(
            dol.entries.mapIndexed { index, entry ->
                NfcField("$prefix.logFormat.entry[$index]", entry.tag + " length " + entry.requestedLength)
            } + NfcField("$prefix.logFormat.totalExpectedBytes", dol.totalRequestedLength.toString())
        )
        return dol.takeIf { it.issues.isEmpty() && it.remainder.isEmpty() && it.entries.isNotEmpty() }
    }

    private fun decodeLogRecord(path: String, data: ByteArray, dol: DolParseResult, context: TechReadContext) {
        var cursor = 0
        dol.entries.forEach { entry ->
            val available = minOf(entry.requestedLength, data.size - cursor)
            val bytes = if (available > 0) data.copyOfRange(cursor, cursor + available) else ByteArray(0)
            context.addDecodedFields(
                listOf(
                    NfcField(path + ".log." + entry.tag, bytes.toHexString()),
                    NfcField(path + ".log." + entry.tag + ".expectedBytes", entry.requestedLength.toString()),
                )
            )
            if (available < entry.requestedLength) {
                context.addIssue(ReadIssue(ReadIssueKind.MALFORMED_RESPONSE, "Log record is shorter than the 9F4F DOL requires.", path + " log " + entry.tag))
                context.addUnreadRange(
                    UnreadRange(
                        path + " log " + entry.tag,
                        "byte",
                        (cursor + available).toLong(),
                        (cursor + entry.requestedLength).toLong(),
                        "Log record bytes required by 9F4F are missing.",
                    )
                )
                context.markPartial()
            }
            cursor += available
        }
        if (cursor < data.size) {
            val extra = data.copyOfRange(cursor, data.size)
            context.captureRaw("ISO-DEP log record bytes beyond 9F4F DOL", extra, path)
            context.addDecodedFields(listOf(NfcField(path + ".log.extraByteCount", extra.size.toString())))
            context.addIssue(ReadIssue(ReadIssueKind.MALFORMED_RESPONSE, "Log record contains bytes beyond the 9F4F DOL length.", path))
            context.markPartial()
        }
    }

    private fun parseResponseTlv(prefix: String, data: ByteArray, context: TechReadContext): TlvParseResult {
        val parsed = parseTlvBounded(data)
        parsed.issues.forEach { issue ->
            context.addIssue(ReadIssue(ReadIssueKind.MALFORMED_RESPONSE, issue, prefix))
            context.markPartial()
        }
        parsed.remainders.forEachIndexed { index, remainder ->
            context.captureRaw("ISO-DEP unparsed TLV remainder", remainder.bytes, prefix + " " + remainder.path + "[$index]")
        }
        return parsed
    }

    private fun dumpTlv(prefix: String, nodes: List<Tlv>): List<NfcField> =
        nodes.flatten()
            .filterNot { it.second.isConstructed }
            .map { (path, node) ->
                NfcField(prefix + "/" + path + emvTagName(node.tag), node.value.toHexString())
            }

    private fun addStatusIssue(context: TechReadContext, operation: String, statusWord: String) {
        context.addIssue(
            ReadIssue(
                ReadIssueKind.UNSUPPORTED_TECHNOLOGY,
                "APDU returned status word " + statusWord + ".",
                operation,
            )
        )
    }

    private fun finish(context: TechReadContext) {
        context.setCoverage(ReadCoverage.unknown("ISO-DEP application-specific read-only commands"))
        context.addIssue(
            ReadIssue(
                ReadIssueKind.COVERAGE_UNKNOWN,
                "ISO-DEP applications define independent protocols; this bounded SELECT/GET DATA/READ RECORD profile is not a complete card dump.",
            )
        )
        context.addUnreadRange(
            UnreadRange("ISO-DEP protocol coverage", "application data", reason = "GPO and commands outside this bounded profile are not attempted.")
        )
        context.markPartial()
    }

    private data class LogEntry(
        val sfi: Int,
        val maxRecords: Int,
    )

    private class ApduSession(
        private val transport: ReadOnlyTransport,
        private val context: TechReadContext,
        private val maxTransceiveLength: Int,
    ) {
        var stopped: Boolean = false
            private set
        private var sentExchanges = 0

        fun exchange(command: ByteArray, operation: String): ApduResponse? {
            if (stopped) return null
            var currentCommand = command.copyOf()
            var currentOperation = operation
            var correctedLe = false
            var continuationCount = 0
            var chainExchangeCount = 0
            val accumulatedData = ByteArrayOutputStream()
            while (true) {
                context.checkpoint()
                if (currentCommand.size > maxTransceiveLength) {
                    context.addIssue(ReadIssue(ReadIssueKind.UNSUPPORTED_TECHNOLOGY, "APDU exceeds Android's reported transmit limit.", currentOperation))
                    context.addUnreadRange(UnreadRange("ISO-DEP exchange", "APDU", reason = "Not sent because it exceeds the reported transmit limit."))
                    stopped = true
                    return null
                }
                if (sentExchanges >= MAX_APDU_EXCHANGES || chainExchangeCount >= MAX_CHAIN_EXCHANGES) {
                    context.addIssue(ReadIssue(ReadIssueKind.BUDGET_EXHAUSTED, "ISO-DEP APDU exchange limit reached.", currentOperation))
                    context.addUnreadRange(UnreadRange("ISO-DEP APDU exchange", "APDU", reason = "No further APDUs were sent after the exchange limit."))
                    stopped = true
                    return null
                }
                context.captureRaw("ISO-DEP APDU request", currentCommand, currentOperation)
                val rawResponse = try {
                    transport.transceiveReadOnly(currentCommand)
                } catch (exception: ReadBudgetExceededException) {
                    throw exception
                } catch (exception: CancellationException) {
                    context.addUnreadRange(UnreadRange("ISO-DEP exchange", "APDU", reason = "Read cancelled during $currentOperation; no further APDUs were requested."))
                    throw exception
                } catch (exception: Exception) {
                    context.addIssue(
                        ReadIssue(
                            kind = when (exception) {
                                is TagLostException -> ReadIssueKind.TAG_LOST
                                is IOException -> ReadIssueKind.IO_ERROR
                                else -> ReadIssueKind.OTHER
                            },
                            message = if (exception is TagLostException) "Tag left the NFC field." else "ISO-DEP APDU exchange failed: " + (exception::class.simpleName ?: "Exception") + ".",
                            operation = currentOperation,
                        )
                    )
                    context.addUnreadRange(UnreadRange("ISO-DEP exchange", "APDU", reason = "No validated response for " + currentOperation + "."))
                    context.markPartial()
                    stopped = true
                    return null
                }
                sentExchanges++
                chainExchangeCount++
                context.captureRaw("ISO-DEP APDU response (data and status word)", rawResponse, currentOperation)
                if (rawResponse.size > MAX_APDU_RESPONSE_BYTES) {
                    context.addIssue(ReadIssue(ReadIssueKind.BUDGET_EXHAUSTED, "ISO-DEP response exceeds the bounded APDU response size.", currentOperation))
                    context.addUnreadRange(UnreadRange("ISO-DEP response", "byte", MAX_APDU_RESPONSE_BYTES.toLong(), rawResponse.size.toLong(), "Response data was not decoded beyond the per-response bound."))
                    context.markPartial()
                    stopped = true
                    return null
                }
                if (rawResponse.size < 2) {
                    context.addIssue(ReadIssue(ReadIssueKind.MALFORMED_RESPONSE, "APDU response is shorter than the two-byte status word.", currentOperation))
                    context.addUnreadRange(UnreadRange("ISO-DEP response", "byte", 0, rawResponse.size.toLong(), "Malformed response retained as raw bytes."))
                    context.markPartial()
                    stopped = true
                    return null
                }
                val response = ApduResponse(rawResponse)
                val sw1 = response.sw1 ?: return null
                val sw2 = response.sw2 ?: return null
                if (sw1 == 0x6C) {
                    if (!correctedLe && currentCommand.size >= MIN_SHORT_APDU_WITH_LE) {
                        currentCommand = currentCommand.copyOf().also { it[it.lastIndex] = sw2.toByte() }
                        currentOperation += " (corrected Le)"
                        correctedLe = true
                        continue
                    }
                    context.addIssue(ReadIssue(ReadIssueKind.MALFORMED_RESPONSE, "Card repeated 6Cxx after the one allowed Le correction.", currentOperation))
                    context.addUnreadRange(UnreadRange("ISO-DEP response", "APDU", reason = "Le correction did not produce a final response."))
                    context.markPartial()
                    return ApduResponse(accumulatedData.toByteArray() + response.raw.copyOfRange(response.raw.size - 2, response.raw.size))
                }
                if (response.data.size > MAX_APDU_CHAIN_DATA_BYTES - accumulatedData.size()) {
                    context.addIssue(ReadIssue(ReadIssueKind.BUDGET_EXHAUSTED, "ISO-DEP response chain exceeds the bounded data size.", currentOperation))
                    context.addUnreadRange(UnreadRange("ISO-DEP response chain", "byte", MAX_APDU_CHAIN_DATA_BYTES.toLong(), reason = "Remaining continuation data was not requested."))
                    context.markPartial()
                    stopped = true
                    return null
                }
                accumulatedData.write(response.data)
                if (sw1 == 0x61) {
                    if (continuationCount >= MAX_GET_RESPONSE_CONTINUATIONS) {
                        context.addIssue(ReadIssue(ReadIssueKind.BUDGET_EXHAUSTED, "ISO-DEP GET RESPONSE continuation limit reached.", operation))
                        context.addUnreadRange(UnreadRange("ISO-DEP response chain", "byte", reason = "Additional 61xx data was not requested."))
                        context.markPartial()
                        stopped = true
                        return null
                    }
                    continuationCount++
                    currentCommand = buildGetResponse(sw2)
                    currentOperation = operation + " GET RESPONSE " + continuationCount
                    correctedLe = false
                    continue
                }
                return ApduResponse(accumulatedData.toByteArray() + byteArrayOf(sw1.toByte(), sw2.toByte()))
            }
        }
    }

    private companion object {
        val SELECT_PPSE = hexToBytes("00A404000E325041592E5359532E444446303100")
        val GET_DATA_TAGS = listOf(0x9F36, 0x9F13, 0x9F17, 0x9F4F)
        const val LOG_ENTRY_TAG = "9F4D"
        const val LOG_FORMAT_TAG = 0x9F4F
        const val LOG_FORMAT_TAG_HEX = "9F4F"
        const val LOG_ENTRY_LENGTH = 2
        const val UNKNOWN_SCHEME = "不明"
        const val MIN_AID_BYTES = 5
        const val MAX_AID_BYTES = 16
        const val MAX_AIDS = 16
        const val MIN_SFI = 1
        const val MAX_SFI = 10
        const val MAX_EMV_SFI = 30
        const val MIN_RECORD = 1
        const val MAX_RECORD = 16
        const val MAX_RECORD_REQUESTS_PER_AID = 160
        const val MAX_APDU_EXCHANGES = 4096
        const val MAX_CHAIN_EXCHANGES = 12
        const val MAX_GET_RESPONSE_CONTINUATIONS = 4
        const val MAX_APDU_RESPONSE_BYTES = 64 * 1024
        const val MAX_APDU_CHAIN_DATA_BYTES = 64 * 1024
        const val MIN_SHORT_APDU_WITH_LE = 5
    }
}

private fun buildGetResponse(expectedLength: Int): ByteArray =
    byteArrayOf(0x00, 0xC0.toByte(), 0x00, 0x00, expectedLength.toByte())
