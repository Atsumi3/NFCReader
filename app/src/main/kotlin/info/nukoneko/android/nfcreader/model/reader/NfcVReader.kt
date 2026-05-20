package info.nukoneko.android.nfcreader.model.reader

import android.nfc.tech.NfcV
import android.nfc.tech.TagTechnology
import info.nukoneko.android.nfcreader.extensions.toHexString
import info.nukoneko.android.nfcreader.model.entity.NfcField

/** Dumps readable blocks of an ISO 15693 (NfcV) tag via READ SINGLE BLOCK. */
class NfcVReader : TechReader {
    override val techName: String = "android.nfc.tech.NfcV"

    override fun read(tech: TagTechnology): List<NfcField> {
        val nfcV = tech as NfcV
        val fields = mutableListOf<NfcField>()
        for (block in 0 until MAX_BLOCKS) {
            val command = byteArrayOf(
                0x02,            // flags: high data rate, not addressed
                0x20,            // READ SINGLE BLOCK
                block.toByte(),
            )
            val response = runCatching { nfcV.transceive(command) }.getOrNull() ?: break
            // Response: flags byte then block data; bit 0 of the flags is the error flag.
            if (response.isEmpty() || response[0].toInt() and 0x01 != 0) break
            fields += NfcField(
                "NfcV.block[$block]",
                response.copyOfRange(1, response.size).toHexString(),
            )
        }
        if (fields.isEmpty()) fields += NfcField("NfcV", "ブロック読み取り不可")
        return fields
    }

    private companion object {
        const val MAX_BLOCKS = 64
    }
}
