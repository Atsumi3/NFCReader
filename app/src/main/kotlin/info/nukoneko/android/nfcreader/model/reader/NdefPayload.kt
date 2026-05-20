package info.nukoneko.android.nfcreader.model.reader

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
fun decodeNdefUriPayload(payload: ByteArray): String {
    if (payload.isEmpty()) return ""
    val prefixCode = payload[0].toInt() and 0xFF
    val prefix = URI_PREFIXES.getOrElse(prefixCode) { "" }
    val rest = String(payload, 1, payload.size - 1, Charsets.UTF_8)
    return prefix + rest
}

/** Decodes an NFC Forum well-known Text record payload into its text content. */
fun decodeNdefTextPayload(payload: ByteArray): String {
    if (payload.isEmpty()) return ""
    val status = payload[0].toInt() and 0xFF
    val isUtf16 = status and 0x80 != 0
    val languageLength = status and 0x3F
    val textStart = 1 + languageLength
    if (textStart > payload.size) return ""
    val charset = if (isUtf16) Charsets.UTF_16 else Charsets.UTF_8
    return String(payload, textStart, payload.size - textStart, charset)
}
