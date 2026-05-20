package info.nukoneko.android.nfcreader.model.reader

import info.nukoneko.android.nfcreader.extensions.toHexStringCompact

/** A parsed BER-TLV node. [isConstructed] reflects the tag's class bit. */
class Tlv(
    val tag: String,            // hex, e.g. "6F", "9F38"
    val value: ByteArray,
    val isConstructed: Boolean,
    val children: List<Tlv>,
)

/** Parses a BER-TLV encoded byte array into a tree of [Tlv] nodes. */
fun parseTlv(data: ByteArray): List<Tlv> = parseTlv(data, 0, data.size)

private fun parseTlv(data: ByteArray, from: Int, to: Int): List<Tlv> {
    val result = mutableListOf<Tlv>()
    var i = from
    while (i < to) {
        // Skip 0x00 / 0xFF padding bytes between TLVs.
        val current = data[i].toInt() and 0xFF
        if (current == 0x00 || current == 0xFF) {
            i++
            continue
        }
        val tagStart = i
        val firstTagByte = data[i].toInt() and 0xFF
        i++
        val constructed = firstTagByte and 0x20 != 0
        if (firstTagByte and 0x1F == 0x1F) {
            // Multi-byte tag: subsequent bytes continue while bit 8 is set.
            while (i < to && data[i].toInt() and 0x80 != 0) i++
            if (i < to) i++
        }
        if (i >= to) break
        val tag = data.copyOfRange(tagStart, i).toHexStringCompact()

        var length = data[i].toInt() and 0xFF
        i++
        if (length and 0x80 != 0) {
            val lengthByteCount = length and 0x7F
            length = 0
            repeat(lengthByteCount) {
                if (i < to) {
                    length = (length shl 8) or (data[i].toInt() and 0xFF)
                    i++
                }
            }
        }
        val valueEnd = minOf(i + length, to)
        val value = data.copyOfRange(i, valueEnd)
        val children = if (constructed) parseTlv(value, 0, value.size) else emptyList()
        result += Tlv(tag, value, constructed, children)
        i = valueEnd
    }
    return result
}

/** Flattens a TLV tree into "path" -> node pairs, depth-first. */
fun List<Tlv>.flatten(prefix: String = ""): List<Pair<String, Tlv>> {
    val out = mutableListOf<Pair<String, Tlv>>()
    for (node in this) {
        val path = if (prefix.isEmpty()) node.tag else "$prefix/${node.tag}"
        out += path to node
        out += node.children.flatten(path)
    }
    return out
}

/** Collects every AID (tag 4F) found anywhere in the TLV tree. */
fun extractAids(tlv: List<Tlv>): List<ByteArray> =
    tlv.flatten().filter { it.second.tag == "4F" }.map { it.second.value }

/** An ISO 7816 response APDU split into data and status word. */
class ApduResponse(raw: ByteArray) {
    val statusWord: String =
        if (raw.size >= 2) raw.copyOfRange(raw.size - 2, raw.size).toHexStringCompact() else "----"
    val data: ByteArray =
        if (raw.size >= 2) raw.copyOfRange(0, raw.size - 2) else ByteArray(0)
    val isSuccess: Boolean get() = statusWord == "9000"
}

/** Builds a SELECT (by name) APDU for [aid]. */
fun buildSelect(aid: ByteArray): ByteArray =
    byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte(), *aid, 0x00)

/** Builds a READ RECORD APDU for [record] within [sfi]. */
fun buildReadRecord(sfi: Int, record: Int): ByteArray =
    byteArrayOf(0x00, 0xB2.toByte(), record.toByte(), ((sfi shl 3) or 0x04).toByte(), 0x00)

/** Resolves the payment scheme from an AID prefix. */
fun cardScheme(aid: ByteArray): String {
    val hex = aid.toHexStringCompact()
    return when {
        hex.startsWith("A000000003") -> "Visa"
        hex.startsWith("A000000004") -> "Mastercard"
        hex.startsWith("A000000025") -> "American Express"
        hex.startsWith("A000000065") -> "JCB"
        hex.startsWith("A000000152") -> "Discover"
        hex.startsWith("A000000333") -> "UnionPay"
        hex.startsWith("A000000277") -> "Interac"
        else -> "不明"
    }
}

/** Human-readable name for a known EMV TLV tag. */
fun emvTagName(tag: String): String = when (tag) {
    "4F" -> "(AID)"
    "50" -> "(アプリ名)"
    "57" -> "(Track2 相当データ)"
    "5A" -> "(PAN)"
    "5F20" -> "(カード名義)"
    "5F24" -> "(有効期限)"
    "5F25" -> "(利用開始日)"
    "5F28" -> "(発行国)"
    "5F2A" -> "(取引通貨)"
    "5F30" -> "(サービスコード)"
    "5F34" -> "(PAN連番)"
    "82" -> "(AIP)"
    "84" -> "(DF名)"
    "87" -> "(優先度)"
    "88" -> "(SFI)"
    "8C" -> "(CDOL1)"
    "8D" -> "(CDOL2)"
    "8E" -> "(CVMリスト)"
    "94" -> "(AFL)"
    "9F07" -> "(アプリ用途管理)"
    "9F08" -> "(アプリ版数)"
    "9F0D" -> "(IAC-Default)"
    "9F0E" -> "(IAC-Denial)"
    "9F0F" -> "(IAC-Online)"
    "9F10" -> "(発行者アプリデータ)"
    "9F11" -> "(発行者コードテーブル)"
    "9F12" -> "(アプリ優先名)"
    "9F1F" -> "(Track1 任意データ)"
    "9F26" -> "(アプリ暗号文)"
    "9F36" -> "(ATC)"
    "9F38" -> "(PDOL)"
    "9F42" -> "(アプリ通貨)"
    "9F4D" -> "(ログエントリ)"
    else -> ""
}
