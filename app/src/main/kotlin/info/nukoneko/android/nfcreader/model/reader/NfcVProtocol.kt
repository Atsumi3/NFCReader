package info.nukoneko.android.nfcreader.model.reader

data class NfcVSystemInfo(
    val responseFlags: Int,
    val informationFlags: Int,
    val uid: ByteArray,
    val dsfid: Int? = null,
    val afi: Int? = null,
    val blockCount: Int? = null,
    val blockSize: Int? = null,
    val icReference: Int? = null,
    val warning: String? = null,
)

data class NfcVSystemInfoParseResult(
    val systemInfo: NfcVSystemInfo? = null,
    val error: String? = null,
)

fun buildNfcVGetSystemInformation(uid: ByteArray, extended: Boolean): ByteArray? {
    if (uid.size != UID_SIZE) return null
    return if (extended) {
        byteArrayOf(ADDRESSED_HIGH_DATA_RATE, GET_EXTENDED_SYSTEM_INFORMATION, EXTENDED_INFO_FIELDS) + uid
    } else {
        byteArrayOf(ADDRESSED_HIGH_DATA_RATE, GET_SYSTEM_INFORMATION) + uid
    }
}

fun buildNfcVReadSingleBlock(uid: ByteArray, block: Int): ByteArray? {
    if (uid.size != UID_SIZE || block < 0 || block > MAX_EXTENDED_BLOCK) return null
    return if (block <= MAX_STANDARD_BLOCK) {
        byteArrayOf(ADDRESSED_HIGH_DATA_RATE, READ_SINGLE_BLOCK) + uid + byteArrayOf(block.toByte())
    } else {
        byteArrayOf(ADDRESSED_HIGH_DATA_RATE, EXTENDED_READ_SINGLE_BLOCK) + uid +
            byteArrayOf(block.toByte(), (block ushr 8).toByte())
    }
}

fun parseNfcVSystemInformation(
    response: ByteArray,
    expectedUid: ByteArray,
    extended: Boolean,
): NfcVSystemInfoParseResult {
    if (expectedUid.size != UID_SIZE) return NfcVSystemInfoParseResult(error = "UID must be 8 bytes.")
    if (response.size < SYSTEM_INFO_HEADER_SIZE) {
        return NfcVSystemInfoParseResult(error = "System Information response is shorter than its header.")
    }
    val responseFlags = response[0].toInt() and 0xFF
    if (responseFlags and ERROR_FLAG != 0) {
        val code = response.getOrNull(1)?.toInt()?.and(0xFF)
        return NfcVSystemInfoParseResult(error = "System Information error response${code?.let { " 0x%02X".format(it) } ?: ""}.")
    }
    val informationFlags = response[1].toInt() and 0xFF
    val uid = response.copyOfRange(2, 2 + UID_SIZE)
    if (!uid.contentEquals(expectedUid)) {
        return NfcVSystemInfoParseResult(
            error = "System Information UID does not match the discovered tag UID."
        )
    }

    var offset = 2 + UID_SIZE
    fun takeByte(): Int? = response.getOrNull(offset++)?.toInt()?.and(0xFF)
    val dsfid = if (informationFlags and DSFID_PRESENT != 0) takeByte() else null
    if (informationFlags and DSFID_PRESENT != 0 && dsfid == null) {
        return NfcVSystemInfoParseResult(error = "DSFID field is truncated.")
    }
    val afi = if (informationFlags and AFI_PRESENT != 0) takeByte() else null
    if (informationFlags and AFI_PRESENT != 0 && afi == null) {
        return NfcVSystemInfoParseResult(error = "AFI field is truncated.")
    }
    var blockCount: Int? = null
    var blockSize: Int? = null
    var warning: String? = null
    if (informationFlags and MEMORY_INFO_PRESENT != 0) {
        val countLow = takeByte() ?: return NfcVSystemInfoParseResult(error = "Block count field is truncated.")
        val countHigh = if (extended) {
            takeByte() ?: return NfcVSystemInfoParseResult(error = "Extended block count field is truncated.")
        } else {
            0
        }
        val sizeByte = takeByte() ?: return NfcVSystemInfoParseResult(error = "Block size field is truncated.")
        blockCount = (if (extended) countLow or (countHigh shl 8) else countLow) + 1
        blockSize = (sizeByte and BLOCK_SIZE_MASK) + 1
        if (sizeByte and BLOCK_SIZE_RESERVED_MASK != 0) {
            warning = "Reserved high bits are set in the reported block-size byte."
        }
    }
    val icReference = if (informationFlags and IC_REFERENCE_PRESENT != 0) takeByte() else null
    if (informationFlags and IC_REFERENCE_PRESENT != 0 && icReference == null) {
        return NfcVSystemInfoParseResult(error = "IC reference field is truncated.")
    }
    if (offset < response.size) {
        warning = listOfNotNull(warning, "${response.size - offset} trailing System Information bytes were retained as unparsed data.")
            .joinToString(" ")
    }
    return NfcVSystemInfoParseResult(
        systemInfo = NfcVSystemInfo(
            responseFlags = responseFlags,
            informationFlags = informationFlags,
            uid = uid,
            dsfid = dsfid,
            afi = afi,
            blockCount = blockCount,
            blockSize = blockSize,
            icReference = icReference,
            warning = warning,
        )
    )
}

private const val UID_SIZE = 8
private const val SYSTEM_INFO_HEADER_SIZE = 2 + UID_SIZE
private const val ADDRESSED_HIGH_DATA_RATE = 0x22
private const val GET_SYSTEM_INFORMATION = 0x2B
private const val GET_EXTENDED_SYSTEM_INFORMATION = 0x3B
private const val EXTENDED_INFO_FIELDS = 0x1F
private const val READ_SINGLE_BLOCK = 0x20
private const val EXTENDED_READ_SINGLE_BLOCK = 0x30
private const val ERROR_FLAG = 0x01
private const val DSFID_PRESENT = 0x01
private const val AFI_PRESENT = 0x02
private const val MEMORY_INFO_PRESENT = 0x04
private const val IC_REFERENCE_PRESENT = 0x08
private const val BLOCK_SIZE_MASK = 0x1F
private const val BLOCK_SIZE_RESERVED_MASK = 0xE0
private const val MAX_STANDARD_BLOCK = 0xFF
private const val MAX_EXTENDED_BLOCK = 0xFFFF
