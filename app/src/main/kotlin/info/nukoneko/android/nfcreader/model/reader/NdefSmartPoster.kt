package info.nukoneko.android.nfcreader.model.reader

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import info.nukoneko.android.nfcreader.extensions.toHexString
import info.nukoneko.android.nfcreader.model.entity.NfcField

internal data class SmartPosterDecode(
    val fields: List<NfcField>,
    val warnings: List<String>,
)

internal fun isSmartPosterRecord(record: NdefRecord): Boolean =
    record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(SMART_POSTER_TYPE)

internal fun decodeSmartPosterPayload(
    payload: ByteArray,
    maxDepth: Int = MAX_SMART_POSTER_DEPTH,
    maxRecords: Int = MAX_SMART_POSTER_RECORDS,
    maxPayloadBytes: Int = MAX_SMART_POSTER_BYTES,
): SmartPosterDecode {
    require(maxDepth >= 0)
    require(maxRecords >= 0)
    require(maxPayloadBytes >= 0)
    val fields = mutableListOf<NfcField>()
    val warnings = mutableListOf<String>()
    parseMessage(payload, "record", 0, maxDepth, maxPayloadBytes, SmartPosterTraversal(maxRecords), fields, warnings)
    return SmartPosterDecode(fields, warnings)
}

private fun parseMessage(
    bytes: ByteArray,
    path: String,
    depth: Int,
    maxDepth: Int,
    maxPayloadBytes: Int,
    state: SmartPosterTraversal,
    fields: MutableList<NfcField>,
    warnings: MutableList<String>,
) {
    if (bytes.size > maxPayloadBytes) {
        warnings += "$path nested NDEF payload exceeds the $maxPayloadBytes-byte decode limit."
        return
    }
    val records = try {
        NdefMessage(bytes).records
    } catch (exception: FormatException) {
        warnings += path + " nested NDEF payload is malformed: " + (exception.message ?: "invalid record encoding") + "."
        return
    } catch (exception: RuntimeException) {
        warnings += path + " nested NDEF payload could not be parsed: " + (exception::class.simpleName ?: "Exception") + "."
        return
    }
    fields += NfcField("$path.recordCount", records.size.toString())
    records.forEachIndexed { index, record ->
        if (state.visited >= state.maxRecords) {
            if (!state.limitReported) {
                warnings += "Smart Poster nested record limit %d reached.".format(state.maxRecords)
                state.limitReported = true
            }
            return@forEachIndexed
        }
        state.visited += 1
        val childPath = "$path[$index]"
        val type = record.type
        val recordPayload = record.payload
        fields += NfcField("$childPath.tnf", record.tnf.toInt().toString())
        fields += NfcField("$childPath.type", String(type, Charsets.US_ASCII))
        fields += NfcField("$childPath.typeHex", type.toHexString())
        fields += NfcField("$childPath.idHex", record.id.toHexString())
        fields += NfcField("$childPath.payloadByteCount", recordPayload.size.toString())

        val decoded = decodeNdefPayload(record.tnf.toInt(), type, recordPayload)
        decoded.decodedText?.let { fields += NfcField("$childPath.decoded", it) }
        decoded.language?.let { fields += NfcField("$childPath.language", it) }
        decoded.warnings.forEach { warnings += "$childPath: $it" }

        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(ACTION_TYPE)) {
            if (recordPayload.size == 1) {
                val action = recordPayload[0].toInt() and 0xFF
                val name = when (action) {
                    0 -> "do"
                    1 -> "save"
                    2 -> "edit"
                    else -> "reserved"
                }
                fields += NfcField("$childPath.action", "$action ($name; not executed)")
            } else {
                warnings += "$childPath Smart Poster action must be one byte."
            }
        }
        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(SIZE_TYPE)) {
            if (recordPayload.size == 4) {
                val size = recordPayload.fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 0xFF) }
                fields += NfcField("$childPath.referenceSizeBytes", size.toString())
            } else {
                warnings += "$childPath Smart Poster reference size must be four bytes."
            }
        }
        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(MIME_TYPE)) {
            if (recordPayload.all { (it.toInt() and 0xFF) in 0x20..0x7E }) {
                fields += NfcField("$childPath.mimeType", String(recordPayload, Charsets.US_ASCII))
            } else {
                warnings += "$childPath Smart Poster MIME type is not printable ASCII."
            }
        }
        if (isSmartPosterRecord(record)) {
            if (depth >= maxDepth) {
                warnings += "$childPath Smart Poster nesting depth limit ($maxDepth) reached."
            } else {
                parseMessage(recordPayload, "$childPath.smartPoster", depth + 1, maxDepth, maxPayloadBytes, state, fields, warnings)
            }
        }
    }
}

private class SmartPosterTraversal(
    val maxRecords: Int,
    var visited: Int = 0,
    var limitReported: Boolean = false,
)

private const val MAX_SMART_POSTER_DEPTH = 4
private const val MAX_SMART_POSTER_RECORDS = 64
private const val MAX_SMART_POSTER_BYTES = 64 * 1024
private val SMART_POSTER_TYPE = byteArrayOf('S'.code.toByte(), 'p'.code.toByte())
private val ACTION_TYPE = byteArrayOf('a'.code.toByte(), 'c'.code.toByte(), 't'.code.toByte())
private val SIZE_TYPE = byteArrayOf('s'.code.toByte())
private val MIME_TYPE = byteArrayOf('t'.code.toByte())
