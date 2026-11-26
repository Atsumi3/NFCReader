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
fun parseTlv(data: ByteArray): List<Tlv> = parseTlvBounded(data).nodes

data class TlvRemainder(
    val path: String,
    val bytes: ByteArray,
)

data class TlvParseResult(
    val nodes: List<Tlv>,
    val remainders: List<TlvRemainder>,
    val issues: List<String>,
)

fun parseTlvBounded(
    data: ByteArray,
    maxDepth: Int = DEFAULT_TLV_MAX_DEPTH,
    maxNodes: Int = DEFAULT_TLV_MAX_NODES,
    maxBytes: Int = DEFAULT_TLV_MAX_BYTES,
): TlvParseResult {
    require(maxDepth >= 0 && maxNodes >= 0 && maxBytes >= 0)
    if (data.size > maxBytes) {
        return TlvParseResult(
            nodes = emptyList(),
            remainders = listOf(TlvRemainder("root", data)),
            issues = listOf("TLV input exceeds the $maxBytes-byte parsing limit."),
        )
    }
    val parser = BoundedTlvParser(maxDepth, maxNodes)
    val nodes = parser.parseRegion(data, 0, data.size, 0, "root")
    return TlvParseResult(nodes, parser.remainders.toList(), parser.issues.toList())
}

private class BoundedTlvParser(
    private val maxDepth: Int,
    private val maxNodes: Int,
) {
    var visitedNodes = 0
        private set
    val remainders = mutableListOf<TlvRemainder>()
    val issues = mutableListOf<String>()

    fun parseRegion(data: ByteArray, from: Int, to: Int, depth: Int, path: String): List<Tlv> {
        val nodes = mutableListOf<Tlv>()
        var cursor = from
        while (cursor < to) {
            val current = data[cursor].toInt() and 0xFF
            if (current == 0x00 || current == 0xFF) {
                cursor++
                continue
            }
            if (visitedNodes >= maxNodes) {
                issue(path, "TLV node limit ($maxNodes) reached.", data.copyOfRange(cursor, to))
                break
            }

            val tagStart = cursor
            val firstTagByte = data[cursor++].toInt() and 0xFF
            val constructed = firstTagByte and 0x20 != 0
            if (firstTagByte and 0x1F == 0x1F) {
                var terminated = false
                while (cursor < to && cursor - tagStart < MAX_TLV_TAG_BYTES) {
                    val tagByte = data[cursor++].toInt() and 0xFF
                    if (tagByte and 0x80 == 0) {
                        terminated = true
                        break
                    }
                }
                if (!terminated) {
                    issue(path, "Malformed or overlong TLV tag.", data.copyOfRange(tagStart, to))
                    break
                }
            }
            if (cursor >= to) {
                issue(path, "TLV tag has no length byte.", data.copyOfRange(tagStart, to))
                break
            }

            val tag = data.copyOfRange(tagStart, cursor).toHexStringCompact()
            val nodePath = if (path == "root") tag else path + "/" + tag
            val lengthByte = data[cursor++].toInt() and 0xFF
            val length: Long
            if (lengthByte and 0x80 == 0) {
                length = lengthByte.toLong()
            } else {
                val lengthByteCount = lengthByte and 0x7F
                if (lengthByteCount == 0 || lengthByteCount > MAX_TLV_LENGTH_BYTES) {
                    issue(nodePath, "Indefinite or oversized BER-TLV length is unsupported.", data.copyOfRange(tagStart, to))
                    break
                }
                if (cursor + lengthByteCount > to) {
                    issue(nodePath, "Truncated BER-TLV length field.", data.copyOfRange(tagStart, to))
                    break
                }
                var decodedLength = 0L
                repeat(lengthByteCount) {
                    decodedLength = (decodedLength shl 8) or (data[cursor++].toLong() and 0xFF)
                }
                length = decodedLength
            }

            val available = to - cursor
            if (length > Int.MAX_VALUE || length > available.toLong()) {
                val partialValue = data.copyOfRange(cursor, to)
                visitedNodes++
                nodes += Tlv(tag, partialValue, constructed, emptyList())
                issue(nodePath, "TLV value length exceeds remaining input.", data.copyOfRange(tagStart, to))
                break
            }

            val valueEnd = cursor + length.toInt()
            val value = data.copyOfRange(cursor, valueEnd)
            visitedNodes++
            val children = if (!constructed || value.isEmpty()) {
                emptyList()
            } else if (depth >= maxDepth) {
                issue(nodePath, "TLV depth limit ($maxDepth) reached.", value.copyOf())
                emptyList()
            } else {
                parseRegion(value, 0, value.size, depth + 1, nodePath)
            }
            nodes += Tlv(tag, value, constructed, children)
            cursor = valueEnd
        }
        return nodes
    }

    private fun issue(path: String, message: String, remainder: ByteArray) {
        issues += "$path: $message"
        if (remainder.isNotEmpty()) remainders += TlvRemainder(path, remainder)
    }
}

data class DolEntry(
    val tag: String,
    val requestedLength: Int,
)

data class DolParseResult(
    val entries: List<DolEntry>,
    val remainder: ByteArray,
    val issues: List<String>,
) {
    val totalRequestedLength: Int
        get() = entries.sumOf(DolEntry::requestedLength)
}

fun parseDolBounded(
    data: ByteArray,
    maxEntries: Int = DEFAULT_DOL_MAX_ENTRIES,
    maxBytes: Int = DEFAULT_DOL_MAX_BYTES,
): DolParseResult {
    require(maxEntries >= 0 && maxBytes >= 0)
    if (data.size > maxBytes) {
        return DolParseResult(emptyList(), data, listOf("DOL input exceeds the $maxBytes-byte parsing limit."))
    }
    val entries = mutableListOf<DolEntry>()
    val issues = mutableListOf<String>()
    var cursor = 0
    while (cursor < data.size) {
        if (entries.size >= maxEntries) {
            issues += "DOL entry limit ($maxEntries) reached."
            break
        }
        val tagStart = cursor
        val firstTagByte = data[cursor++].toInt() and 0xFF
        if (firstTagByte and 0x1F == 0x1F) {
            var terminated = false
            while (cursor < data.size && cursor - tagStart < MAX_TLV_TAG_BYTES) {
                if (data[cursor++].toInt() and 0x80 == 0) {
                    terminated = true
                    break
                }
            }
            if (!terminated) {
                issues += "Malformed or overlong DOL tag."
                cursor = tagStart
                break
            }
        }
        if (cursor >= data.size) {
            issues += "DOL tag has no requested-length byte."
            cursor = tagStart
            break
        }
        val tag = data.copyOfRange(tagStart, cursor).toHexStringCompact()
        val requestedLength = data[cursor++].toInt() and 0xFF
        entries += DolEntry(tag, requestedLength)
    }
    return DolParseResult(entries, data.copyOfRange(cursor, data.size), issues)
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
    val raw: ByteArray = raw.copyOf()
    val hasStatusWord: Boolean = raw.size >= 2
    val sw1: Int? = if (hasStatusWord) raw[raw.size - 2].toInt() and 0xFF else null
    val sw2: Int? = if (hasStatusWord) raw[raw.size - 1].toInt() and 0xFF else null
    val statusWord: String =
        if (hasStatusWord) raw.copyOfRange(raw.size - 2, raw.size).toHexStringCompact() else "----"
    val data: ByteArray =
        if (hasStatusWord) raw.copyOfRange(0, raw.size - 2) else ByteArray(0)
    val isSuccess: Boolean get() = hasStatusWord && statusWord == "9000"
}

/** Builds a SELECT (by name) APDU for [aid]. */
fun buildSelect(aid: ByteArray): ByteArray {
    require(aid.isNotEmpty() && aid.size <= 255)
    return byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte(), *aid, 0x00)
}

/** Builds a READ RECORD APDU for [record] within [sfi]. */
fun buildReadRecord(sfi: Int, record: Int): ByteArray {
    require(sfi in 1..30 && record in 1..255)
    return byteArrayOf(0x00, 0xB2.toByte(), record.toByte(), ((sfi shl 3) or 0x04).toByte(), 0x00)
}

fun buildGetData(tag: Int): ByteArray {
    require(tag in 0..0xFFFF)
    return byteArrayOf(0x80.toByte(), 0xCA.toByte(), (tag ushr 8).toByte(), tag.toByte(), 0x00)
}

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
    "9F13" -> "(最終オンラインATC)"
    "9F1F" -> "(Track1 任意データ)"
    "9F26" -> "(アプリ暗号文)"
    "9F36" -> "(ATC)"
    "9F38" -> "(PDOL)"
    "9F42" -> "(アプリ通貨)"
    "9F4D" -> "(ログエントリ)"
    "9F4F" -> "(ログ形式)"
    "9F17" -> "(PIN残回数)"
    else -> ""
}

private const val DEFAULT_TLV_MAX_DEPTH = 8
private const val DEFAULT_TLV_MAX_NODES = 256
private const val DEFAULT_TLV_MAX_BYTES = 64 * 1024
private const val DEFAULT_DOL_MAX_ENTRIES = 32
private const val DEFAULT_DOL_MAX_BYTES = 256
private const val MAX_TLV_TAG_BYTES = 4
private const val MAX_TLV_LENGTH_BYTES = 4
