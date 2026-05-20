package info.nukoneko.android.nfcreader.model.reader

import info.nukoneko.android.nfcreader.extensions.readUIntBe
import info.nukoneko.android.nfcreader.extensions.readUIntLe
import info.nukoneko.android.nfcreader.extensions.toHexString

/** FeliCa Polling command code. */
private const val FELICA_POLLING: Int = 0x00

/** FeliCa "Read Without Encryption" command code. */
private const val FELICA_READ_WITHOUT_ENCRYPTION: Int = 0x06

/** Suica / common transit FeliCa system code. */
const val SUICA_SYSTEM_CODE: Int = 0x0003

/** Suica ride/usage history service code (read without encryption). */
const val SUICA_HISTORY_SERVICE: Int = 0x090F

/** Builds a FeliCa Polling command frame for [systemCode]. */
fun buildPollingCommand(systemCode: Int): ByteArray = withLengthPrefix(
    byteArrayOf(
        FELICA_POLLING.toByte(),
        (systemCode shr 8).toByte(), systemCode.toByte(),
        0x01, // request code: request system code
        0x00, // time slot
    ),
)

/**
 * Builds a FeliCa Read Without Encryption command for [serviceCode] covering
 * [blockNumbers], addressed to [idm].
 */
fun buildReadWithoutEncryptionCommand(
    idm: ByteArray,
    serviceCode: Int,
    blockNumbers: List<Int>,
): ByteArray {
    val body = ArrayList<Byte>()
    body += FELICA_READ_WITHOUT_ENCRYPTION.toByte()
    body += idm.toList()
    body += 0x01.toByte() // number of services
    body += serviceCode.toByte()          // service code list, little-endian
    body += (serviceCode shr 8).toByte()
    body += blockNumbers.size.toByte()
    for (block in blockNumbers) {
        body += 0x80.toByte() // 2-byte block list element, service index 0
        body += block.toByte()
    }
    return withLengthPrefix(body.toByteArray())
}

/** Prepends the FeliCa length byte, which counts itself. */
private fun withLengthPrefix(body: ByteArray): ByteArray =
    byteArrayOf((body.size + 1).toByte(), *body)

/** One parsed Suica history record (16 bytes). */
data class SuicaHistoryEntry(
    val terminal: String,
    val process: String,
    val date: String,
    val balanceYen: Int,
    val sequence: Long,
    val raw: String,
) {
    fun summary(): String = "$date  ¥$balanceYen  $process @ $terminal  (seq $sequence)"
}

/** Parses a single 16-byte Suica history block. */
fun parseSuicaHistoryBlock(block: ByteArray): SuicaHistoryEntry {
    require(block.size >= 16) { "Suica history block must be 16 bytes" }
    val packedDate = block.readUIntBe(4, 2).toInt()
    val year = 2000 + (packedDate shr 9 and 0x7F)
    val month = packedDate shr 5 and 0x0F
    val day = packedDate and 0x1F
    return SuicaHistoryEntry(
        terminal = suicaTerminalName(block[0].toInt() and 0xFF),
        process = suicaProcessName(block[1].toInt() and 0xFF),
        date = "%04d-%02d-%02d".format(year, month, day),
        balanceYen = block.readUIntLe(10, 2).toInt(),
        sequence = block.readUIntBe(12, 3),
        raw = block.copyOf(16).toHexString(),
    )
}

private fun suicaTerminalName(code: Int): String = when (code) {
    0x03 -> "精算機"
    0x04 -> "携帯端末"
    0x05 -> "車載端末"
    0x07, 0x08, 0x12 -> "券売機"
    0x09 -> "入金機"
    0x16 -> "改札機"
    0x17 -> "簡易改札機"
    0x18, 0x19 -> "窓口端末"
    0x1A -> "改札端末"
    0x1B -> "携帯電話"
    0x1C -> "乗継精算機"
    0x1D -> "連絡改札機"
    0x1F -> "簡易入金機"
    0x46, 0x48 -> "ビューアルッテ端末"
    0xC7 -> "物販端末"
    0xC8 -> "自販機"
    else -> "不明(0x%02X)".format(code)
}

private fun suicaProcessName(code: Int): String = when (code) {
    0x01 -> "運賃支払"
    0x02 -> "チャージ"
    0x03 -> "券購"
    0x04 -> "精算"
    0x05 -> "入場精算"
    0x06 -> "窓口精算"
    0x07 -> "新規発行"
    0x08 -> "控除"
    0x0D -> "バス/路面(均一)"
    0x0F -> "バス/路面"
    0x11 -> "再発行"
    0x13 -> "新幹線利用"
    0x14 -> "入場・改札出場"
    0x1F -> "バスチャージ"
    0x23 -> "オートチャージ"
    0x33 -> "バスチャージ"
    0x46 -> "物販"
    0x48 -> "自販機"
    0x49 -> "レジ入金"
    0xC6 -> "物販(現金併用)"
    else -> "不明(0x%02X)".format(code)
}
