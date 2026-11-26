package info.nukoneko.android.nfcreader.model.reader

import android.nfc.NdefRecord
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.TagTechnology
import info.nukoneko.android.nfcreader.extensions.toHexString
import info.nukoneko.android.nfcreader.model.entity.NfcField
import info.nukoneko.android.nfcreader.model.entity.ReadCoverage
import info.nukoneko.android.nfcreader.model.entity.ReadIssue
import info.nukoneko.android.nfcreader.model.entity.ReadIssueKind
import info.nukoneko.android.nfcreader.model.entity.ReadRange
import kotlinx.coroutines.CancellationException
import java.io.IOException

/** Parses the NDEF message into individual records with decoded payloads. */
class NdefReader : TechReader {
    override val techName: String = "android.nfc.tech.Ndef"

    override fun read(tech: TagTechnology, context: TechReadContext) {
        val ndef = tech as Ndef
        context.addMetadata(
            listOf(
                NfcField("NDEF.type", ndef.type),
                NfcField("NDEF.maxMessageSizeBytes", ndef.maxSize.toString()),
                NfcField("NDEF.writable", ndef.isWritable.toString()),
                NfcField("NDEF.canMakeReadOnly", ndef.canMakeReadOnly().toString()),
            )
        )

        var liveFailure: Throwable? = null
        val liveMessage = try {
            context.reserveTransceive("NDEF live message")
            ndef.ndefMessage
        } catch (exception: CancellationException) {
            context.addUnreadRange(info.nukoneko.android.nfcreader.model.entity.UnreadRange("NDEF message", "record", reason = "Read cancelled before the live message was obtained."))
            context.setCoverage(ReadCoverage.unknown("NDEF live message read"))
            throw exception
        } catch (exception: ReadBudgetExceededException) {
            throw exception
        } catch (exception: Exception) {
            liveFailure = exception
            null
        }
        val cachedMessage = ndef.cachedNdefMessage
        val message = liveMessage ?: cachedMessage
        val source = when {
            liveMessage != null -> "live"
            cachedMessage != null -> "discovery cache"
            else -> "unavailable"
        }
        context.addDecodedFields(listOf(NfcField("NDEF.messageSource", source)))
        if (liveFailure != null) {
            context.addIssue(issueFor(liveFailure, "NDEF live read"))
            context.markPartial()
        }
        if (message == null) {
            context.setCoverage(ReadCoverage.unknown("NDEF message unavailable"))
            context.addIssue(
                ReadIssue(
                    kind = ReadIssueKind.COVERAGE_UNKNOWN,
                    message = "No live or cached NDEF message is available.",
                    operation = "NDEF",
                )
            )
            context.markPartial()
            return
        }
        if (liveMessage == null) {
            context.addIssue(
                ReadIssue(
                    kind = ReadIssueKind.COVERAGE_UNKNOWN,
                    message = "Discovery-cache data is not a current live read.",
                    operation = "NDEF",
                )
            )
            context.markPartial()
        }

        val records = message.records
        val includedRecords = records.take(MAX_RECORDS)
        var completedRecordCount = 0
        context.addDecodedFields(
            listOf(
                NfcField("NDEF.recordCount", records.size.toString()),
                NfcField("NDEF.captureScope", "Android-provided NDEF message records only"),
            )
        )
        try {
        includedRecords.forEachIndexed { index, record ->
            context.checkpoint()
            val tag = "NDEF[$index]"
            val payload = record.payload
            val payloadDecode = decodeNdefPayload(record.tnf.toInt(), record.type, payload)
            context.addDecodedFields(
                listOf(
                    NfcField("$tag.tnf", tnfName(record.tnf)),
                    NfcField("$tag.type", record.type.asPrintable()),
                    NfcField("$tag.typeHex", record.type.toHexString()),
                    NfcField("$tag.idHex", record.id.toHexString()),
                    NfcField("$tag.payloadByteCount", payload.size.toString()),
                    NfcField("$tag.payloadPreview", payloadDecode.decodedText ?: payload.previewHex()),
                    payloadDecode.language?.let { NfcField("$tag.language", it) },
                ).filterNotNull()
            )
            context.captureRaw("$tag.logical payload", payload, "record $index payload")
            context.captureRaw("$tag.type", record.type, "record $index type")
            context.captureRaw("$tag.id", record.id, "record $index id")
            if (isSmartPosterRecord(record)) {
                val nested = decodeSmartPosterPayload(payload)
                context.addDecodedFields(
                    nested.fields.map { field ->
                        NfcField("$tag.smartPoster." + field.label, field.value)
                    }
                )
                nested.warnings.forEach { warning ->
                    context.addIssue(
                        ReadIssue(
                            kind = ReadIssueKind.MALFORMED_RESPONSE,
                            message = warning,
                            operation = "$tag Smart Poster",
                        )
                    )
                    context.markPartial()
                }
            }
            if (payloadDecode.previewTruncated ||
                (payload.size > MAX_RECORD_PREVIEW_BYTES && payloadDecode.decodedText == null)
            ) {
                context.addDecodedFields(
                    listOf(NfcField("$tag.payloadPreviewLimit", "$MAX_RECORD_PREVIEW_BYTES bytes; raw capture is separate"))
                )
            }
            payloadDecode.warnings.forEach { warning ->
                context.addIssue(
                    ReadIssue(
                        kind = ReadIssueKind.MALFORMED_RESPONSE,
                        message = warning,
                        operation = tag,
                    )
                )
                context.markPartial()
            }
            completedRecordCount = index + 1
        }
        } catch (cancelled: CancellationException) {
            if (completedRecordCount < records.size) {
                context.addUnreadRange(
                    info.nukoneko.android.nfcreader.model.entity.UnreadRange(
                        "NDEF records",
                        "record",
                        completedRecordCount.toLong(),
                        records.size.toLong(),
                        "Read cancelled; remaining records were not decoded.",
                    )
                )
            }
            context.setCoverage(
                ReadCoverage(
                    scope = "Android-provided NDEF message records",
                    unit = "record",
                    expectedRange = ReadRange(0, records.size.toLong()),
                    completedRanges = listOf(ReadRange(0, completedRecordCount.toLong())),
                )
            )
            throw cancelled
        }
        if (records.size > includedRecords.size) {
            context.addUnreadRange(
                info.nukoneko.android.nfcreader.model.entity.UnreadRange(
                    scope = "NDEF records",
                    unit = "record",
                    startInclusive = includedRecords.size.toLong(),
                    endExclusive = records.size.toLong(),
                    reason = "Application record limit reached.",
                )
            )
        }
        val serializedMessage = runCatching { message.toByteArray() }.getOrNull()
        if (serializedMessage == null) {
            context.addIssue(
                ReadIssue(
                    kind = ReadIssueKind.MALFORMED_RESPONSE,
                    message = "Android could not reserialize the NDEF message.",
                    operation = "NDEF message",
                )
            )
            context.markPartial()
        } else {
            context.captureRaw(
                "Android reserialized NDEF message (not RF bytes)",
                serializedMessage,
                "message",
            )
        }
        context.setCoverage(
            ReadCoverage(
                scope = "Android-provided NDEF message records",
                unit = "record",
                expectedRange = ReadRange(0, records.size.toLong()),
                completedRanges = listOf(ReadRange(0, includedRecords.size.toLong())),
            )
        )
        if (liveMessage != null && records.size == includedRecords.size) context.markComplete()
        else context.markPartial()
    }

    private fun ByteArray.asPrintable(): String {
        if (isEmpty()) return "-"
        val text = String(this, Charsets.UTF_8)
        return if (text.none { it.isISOControl() && it != '\t' && it != '\n' }) text
        else toHexString()
    }

    private fun tnfName(tnf: Short): String = when (tnf) {
        NdefRecord.TNF_EMPTY -> "EMPTY"
        NdefRecord.TNF_WELL_KNOWN -> "WELL_KNOWN"
        NdefRecord.TNF_MIME_MEDIA -> "MIME_MEDIA"
        NdefRecord.TNF_ABSOLUTE_URI -> "ABSOLUTE_URI"
        NdefRecord.TNF_EXTERNAL_TYPE -> "EXTERNAL_TYPE"
        NdefRecord.TNF_UNKNOWN -> "UNKNOWN"
        NdefRecord.TNF_UNCHANGED -> "UNCHANGED"
        else -> "0x%02X".format(tnf.toInt())
    }

    private fun ByteArray.previewHex(): String =
        copyOf(minOf(size, MAX_RECORD_PREVIEW_BYTES)).toHexString()

    private fun issueFor(exception: Throwable, operation: String) = ReadIssue(
        kind = if (exception is TagLostException) ReadIssueKind.TAG_LOST else if (exception is IOException) {
            ReadIssueKind.IO_ERROR
        } else {
            ReadIssueKind.OTHER
        },
        message = when (exception) {
            is TagLostException -> "Tag left the NFC field during NDEF reading."
            is IOException -> "NDEF message read failed; cached data may be shown if available."
            else -> "NDEF reader failed: ${exception::class.simpleName ?: "Exception"}."
        },
        operation = operation,
    )

    private companion object {
        const val MAX_RECORDS = 1024
        const val MAX_RECORD_PREVIEW_BYTES = 4096
    }
}
