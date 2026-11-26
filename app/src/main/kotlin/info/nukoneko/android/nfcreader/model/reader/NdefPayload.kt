package info.nukoneko.android.nfcreader.model.reader

import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** URI prefix abbreviations defined by the NFC Forum URI Record Type Definition. */
private val URI_PREFIXES = arrayOf(
    "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
    "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://",
    "nfs://", "ftp://", "dav://", "news:", "telnet://", "imap:", "rtsp://",
    "urn:", "pop:", "sip:", "sips:", "tftp:", "btspp://", "btl2cap://",
    "btgoep://", "tcpobex://", "irdaobex://", "file://", "urn:epc:id:",
    "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:",
)

/** Decodes an NFC Forum well-known URI record payload into a readable URI. */
fun decodeNdefUriPayload(payload: ByteArray): String =
    decodeNdefPayload(NDEF_TNF_WELL_KNOWN, URI_RECORD_TYPE, payload).decodedText.orEmpty()

/** Decodes an NFC Forum well-known Text record payload into its text content. */
fun decodeNdefTextPayload(payload: ByteArray): String =
    decodeNdefPayload(NDEF_TNF_WELL_KNOWN, TEXT_RECORD_TYPE, payload).decodedText.orEmpty()

data class NdefPayloadDecode(
    val decodedText: String? = null,
    val language: String? = null,
    val warnings: List<String> = emptyList(),
    val previewTruncated: Boolean = false,
)

fun decodeNdefPayload(tnf: Int, type: ByteArray, payload: ByteArray): NdefPayloadDecode {
    if (tnf == NDEF_TNF_WELL_KNOWN && type.contentEquals(URI_RECORD_TYPE)) {
        if (payload.isEmpty()) return NdefPayloadDecode(warnings = listOf("URI payload is empty."))
        val prefixCode = payload[0].toInt() and 0xFF
        val prefix = URI_PREFIXES.getOrNull(prefixCode)
            ?: return NdefPayloadDecode(warnings = listOf("URI prefix code $prefixCode is unsupported."))
        val suffixLength = minOf(payload.size - 1, MAX_TEXT_PREVIEW_BYTES)
        val suffix = decodePreview(payload, 1, suffixLength, Charsets.UTF_8, suffixLength < payload.size - 1)
            ?: return NdefPayloadDecode(warnings = listOf("URI suffix is not valid UTF-8."))
        return NdefPayloadDecode(
            decodedText = prefix + suffix,
            previewTruncated = suffixLength < payload.size - 1,
        )
    }

    if (tnf == NDEF_TNF_WELL_KNOWN && type.contentEquals(TEXT_RECORD_TYPE)) {
        if (payload.isEmpty()) return NdefPayloadDecode(warnings = listOf("Text payload is empty."))
        val status = payload[0].toInt() and 0xFF
        val languageLength = status and 0x3F
        val textStart = 1 + languageLength
        if (textStart > payload.size) {
            return NdefPayloadDecode(warnings = listOf("Text language length exceeds payload size."))
        }
        val language = decodeText(payload, 1, languageLength, Charsets.US_ASCII)
            ?: return NdefPayloadDecode(warnings = listOf("Text language code is malformed."))
        val charset = if (status and 0x80 != 0) Charsets.UTF_16 else Charsets.UTF_8
        val contentLength = payload.size - textStart
        val previewLength = minOf(contentLength, MAX_TEXT_PREVIEW_BYTES)
        val text = decodePreview(payload, textStart, previewLength, charset, previewLength < contentLength)
            ?: return NdefPayloadDecode(
                language = language,
                warnings = listOf("Text content is not valid ${charset.name()}.")
            )
        return NdefPayloadDecode(
            decodedText = text,
            language = language,
            previewTruncated = previewLength < contentLength,
        )
    }

    if (tnf == NDEF_TNF_MIME_MEDIA) {
        val mediaType = String(type, Charsets.US_ASCII).lowercase()
        if (mediaType.startsWith("text/") || mediaType == "application/json") {
            val preview = payload.copyOf(minOf(payload.size, MAX_TEXT_PREVIEW_BYTES))
            val text = decodePreview(preview, 0, preview.size, Charsets.UTF_8, preview.size < payload.size)
                ?: return NdefPayloadDecode(warnings = listOf("Text MIME payload is not valid UTF-8."))
            return NdefPayloadDecode(
                decodedText = text,
                previewTruncated = preview.size < payload.size,
            )
        }
    }

    if (tnf == NDEF_TNF_ABSOLUTE_URI) {
        val uri = decodeText(type, 0, type.size, Charsets.UTF_8)
            ?: return NdefPayloadDecode(warnings = listOf("Absolute URI type is not valid UTF-8."))
        return NdefPayloadDecode(decodedText = uri)
    }
    return NdefPayloadDecode()
}

private fun decodeText(bytes: ByteArray, offset: Int, length: Int, charset: Charset): String? =
    runCatching {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes, offset, length))
            .toString()
    }.getOrNull()

private fun decodePreview(bytes: ByteArray, offset: Int, length: Int, charset: Charset, truncated: Boolean): String? {
    decodeText(bytes, offset, length, charset)?.let { return it }
    if (!truncated) return null
    for (suffixBytes in 1..4) {
        val safeLength = length - suffixBytes
        if (safeLength < 0) break
        decodeText(bytes, offset, safeLength, charset)?.let { return it }
    }
    return null
}

private const val NDEF_TNF_WELL_KNOWN = 1
private const val NDEF_TNF_MIME_MEDIA = 2
private const val NDEF_TNF_ABSOLUTE_URI = 3
private const val MAX_TEXT_PREVIEW_BYTES = 4096
private val URI_RECORD_TYPE = byteArrayOf('U'.code.toByte())
private val TEXT_RECORD_TYPE = byteArrayOf('T'.code.toByte())
