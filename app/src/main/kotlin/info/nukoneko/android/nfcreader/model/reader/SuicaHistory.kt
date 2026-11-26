package info.nukoneko.android.nfcreader.model.reader

import info.nukoneko.android.nfcreader.extensions.readUIntBe
import info.nukoneko.android.nfcreader.extensions.readUIntLe
import info.nukoneko.android.nfcreader.extensions.toHexString

private const val REQUEST_SYSTEM_CODE_RESPONSE: Int = 0x0D
private const val FELICA_IDM_SIZE: Int = 8
private const val MAX_SYSTEM_CODES: Int = 32
private const val MAX_SERVICE_BLOCKS: Int = 16
private const val MAX_REQUEST_SERVICE_CODES: Int = 32

/** FeliCa Polling command code. */
private const val FELICA_POLLING: Int = 0x00
private const val FELICA_POLLING_RESPONSE: Int = 0x01

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
).also { require(systemCode in 0..0xFFFF) }

data class FelicaPollingResult(
    val idm: ByteArray,
    val pmm: ByteArray,
    val returnedSystemCode: Int? = null,
)

fun parseFelicaPollingResponse(response: ByteArray, requestedSystemCode: Int): FelicaPollingResult? {
    if (requestedSystemCode !in 0..0xFFFF || response.size !in setOf(18, 20)) return null
    if ((response[0].toInt() and 0xFF) != response.size ||
        (response[1].toInt() and 0xFF) != FELICA_POLLING_RESPONSE
    ) return null
    val returnedSystemCode = if (response.size == 20) {
        ((response[18].toInt() and 0xFF) shl 8) or (response[19].toInt() and 0xFF)
    } else {
        null
    }
    if (returnedSystemCode != null && returnedSystemCode != requestedSystemCode) return null
    return FelicaPollingResult(
        idm = response.copyOfRange(2, 10),
        pmm = response.copyOfRange(10, 18),
        returnedSystemCode = returnedSystemCode,
    )
}

fun buildRequestSystemCodesCommand(idm: ByteArray): ByteArray {
    require(idm.size == FELICA_IDM_SIZE)
    return byteArrayOf(0x0A, 0x0C) + idm
}

fun parseFelicaSystemCodesResponse(response: ByteArray, expectedIdm: ByteArray): List<Int>? {
    if (expectedIdm.size != FELICA_IDM_SIZE || response.size < 11) return null
    if ((response[0].toInt() and 0xFF) != response.size ||
        (response[1].toInt() and 0xFF) != REQUEST_SYSTEM_CODE_RESPONSE
    ) return null
    if (!response.copyOfRange(2, 10).contentEquals(expectedIdm)) return null
    val count = response[10].toInt() and 0xFF
    if (count > MAX_SYSTEM_CODES || response.size != 11 + count * 2) return null
    return List(count) { index ->
        ((response[11 + index * 2].toInt() and 0xFF) shl 8) or
            (response[12 + index * 2].toInt() and 0xFF)
    }
}

data class FelicaServiceKeyVersion(
    val serviceCode: Int,
    val keyVersion: Int,
) {
    val serviceNumber: Int
        get() = serviceCode ushr 6

    val serviceAttribute: Int
        get() = serviceCode and 0x3F

    val requiresAuthentication: Boolean?
        get() = serviceAttribute.takeIf { it in 0x08..0x17 }?.let { (it and 1) == 0 }

    val isPresent: Boolean
        get() = keyVersion != 0xFFFF
}

fun buildFelicaServiceCode(serviceNumber: Int, serviceAttribute: Int): Int {
    require(serviceNumber in 0..0x3FF)
    require(serviceAttribute in 0..0x3F)
    return (serviceNumber shl 6) or serviceAttribute
}

fun buildRequestServiceCommand(idm: ByteArray, serviceCodes: List<Int>): ByteArray {
    require(idm.size == FELICA_IDM_SIZE)
    require(serviceCodes.isNotEmpty() && serviceCodes.size <= MAX_REQUEST_SERVICE_CODES)
    require(serviceCodes.all { it in 0..0xFFFF })
    val body = ArrayList<Byte>()
    body += 0x02
    body += idm.toList()
    body += serviceCodes.size.toByte()
    serviceCodes.forEach { code ->
        body += code.toByte()
        body += (code ushr 8).toByte()
    }
    return withLengthPrefix(body.toByteArray())
}

fun parseRequestServiceResponse(
    response: ByteArray,
    expectedIdm: ByteArray,
    requestedServiceCodes: List<Int>,
): List<FelicaServiceKeyVersion>? {
    if (expectedIdm.size != FELICA_IDM_SIZE || requestedServiceCodes.isEmpty() ||
        requestedServiceCodes.size > MAX_REQUEST_SERVICE_CODES
    ) return null
    val expectedLength = 11 + requestedServiceCodes.size * 2
    if (response.size != expectedLength || (response[0].toInt() and 0xFF) != response.size ||
        (response[1].toInt() and 0xFF) != 0x03 ||
        !response.copyOfRange(2, 10).contentEquals(expectedIdm) ||
        (response[10].toInt() and 0xFF) != requestedServiceCodes.size
    ) return null
    return requestedServiceCodes.mapIndexed { index, serviceCode ->
        val offset = 11 + index * 2
        FelicaServiceKeyVersion(
            serviceCode = serviceCode,
            keyVersion = (response[offset].toInt() and 0xFF) or
                ((response[offset + 1].toInt() and 0xFF) shl 8),
        )
    }
}

data class FelicaReadResponse(
    val status1: Int = 0,
    val status2: Int = 0,
    val blocks: List<ByteArray> = emptyList(),
    val error: String? = null,
)

fun parseFelicaReadResponse(response: ByteArray, idm: ByteArray, requestedCount: Int): FelicaReadResponse {
    if (idm.size != FELICA_IDM_SIZE || requestedCount !in 1..MAX_SERVICE_BLOCKS ||
        response.size < 12 || (response[0].toInt() and 0xFF) != response.size ||
        (response[1].toInt() and 0xFF) != 0x07
    ) return FelicaReadResponse(error = "FeliCa read response header or declared length is malformed.")
    if (!response.copyOfRange(2, 10).contentEquals(idm)) {
        return FelicaReadResponse(error = "FeliCa read response IDm does not match the polled system IDm.")
    }
    val status1 = response[10].toInt() and 0xFF
    val status2 = response[11].toInt() and 0xFF
    if (status1 != 0 || status2 != 0) {
        return if (response.size == 12) {
            FelicaReadResponse(status1 = status1, status2 = status2)
        } else {
            FelicaReadResponse(error = "FeliCa error response contains unexpected block data.")
        }
    }
    if (response.size < 13) return FelicaReadResponse(error = "FeliCa success response omitted the block count.")
    val count = response[12].toInt() and 0xFF
    if (count > requestedCount || response.size != 13 + count * 16) {
        return FelicaReadResponse(error = "FeliCa block count does not match status, response length, or request.")
    }
    return FelicaReadResponse(
        status1 = status1,
        status2 = status2,
        blocks = List(count) { index ->
            response.copyOfRange(13 + index * 16, 13 + (index + 1) * 16)
        },
    )
}

/**
 * Builds a FeliCa Read Without Encryption command for [serviceCode] covering
 * [blockNumbers], addressed to [idm].
 */
fun buildReadWithoutEncryptionCommand(
    idm: ByteArray,
    serviceCode: Int,
    blockNumbers: List<Int>,
): ByteArray {
    require(idm.size == FELICA_IDM_SIZE)
    require(serviceCode in 0..0xFFFF)
    require(blockNumbers.isNotEmpty() && blockNumbers.size <= MAX_SERVICE_BLOCKS)
    require(blockNumbers.all { it in 0..0xFFFF })
    val body = ArrayList<Byte>()
    body += FELICA_READ_WITHOUT_ENCRYPTION.toByte()
    body += idm.toList()
    body += 0x01.toByte() // number of services
    body += serviceCode.toByte()          // service code list, little-endian
    body += (serviceCode shr 8).toByte()
    body += blockNumbers.size.toByte()
    for (block in blockNumbers) {
        if (block <= 0xFF) {
            body += 0x80.toByte()
            body += block.toByte()
        } else {
            body += 0x00.toByte()
            body += block.toByte()
            body += (block shr 8).toByte()
        }
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
    val dateValid: Boolean = true,
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
    val dateValid = isValidDate(year, month, day)
    return SuicaHistoryEntry(
        terminal = suicaTerminalName(block[0].toInt() and 0xFF),
        process = suicaProcessName(block[1].toInt() and 0xFF),
        date = if (dateValid) "%04d-%02d-%02d".format(year, month, day) else "invalid(%04d-%02d-%02d)".format(year, month, day),
        balanceYen = block.readUIntLe(10, 2).toInt(),
        sequence = block.readUIntBe(12, 3),
        raw = block.copyOf(16).toHexString(),
        dateValid = dateValid,
    )
}

private fun isValidDate(year: Int, month: Int, day: Int): Boolean {
    if (month !in 1..12) return false
    val leapYear = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    val daysInMonth = when (month) {
        2 -> if (leapYear) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    return day in 1..daysInMonth
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
