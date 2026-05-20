package info.nukoneko.android.nfcreader.model.reader

import android.nfc.tech.NfcF
import android.nfc.tech.TagTechnology
import info.nukoneko.android.nfcreader.extensions.toHexString
import info.nukoneko.android.nfcreader.model.entity.NfcField

/** Reads Suica-family FeliCa cards: IDm/PMm and the ride/usage history. */
class FelicaReader : TechReader {
    override val techName: String = "android.nfc.tech.NfcF"

    override fun read(tech: TagTechnology): List<NfcField> {
        val nfcF = tech as NfcF

        val polling = runCatching {
            nfcF.transceive(buildPollingCommand(SUICA_SYSTEM_CODE))
        }.getOrNull()
        // Polling response: len, 0x01, IDm[8], PMm[8], ...
        if (polling == null || polling.size < 18 || polling[1].toInt() != 0x01) {
            return listOf(NfcField("Suica", "Suica系(系統0003)の応答なし"))
        }

        val idm = polling.copyOfRange(2, 10)
        val fields = mutableListOf(
            NfcField("Felica.IDm", idm.toHexString()),
            NfcField("Felica.PMm", polling.copyOfRange(10, 18).toHexString()),
        )

        val blocks = readHistoryBlocks(nfcF, idm)
            .filter { block -> block.any { it.toInt() != 0 } }
        if (blocks.isEmpty()) {
            fields += NfcField("Suica.history", "履歴の読み取り不可、または履歴なし")
            return fields
        }

        val entries = blocks.map { parseSuicaHistoryBlock(it) }
        // Block 0 is the most recent transaction, so its post-transaction
        // balance is the card's current balance.
        fields += NfcField("Suica.balance", "¥${entries.first().balanceYen}")
        fields += NfcField("Suica.historyCount", entries.size.toString())
        entries.forEachIndexed { index, entry ->
            fields += NfcField("Suica.history[$index]", entry.summary())
        }
        return fields
    }

    /** Reads up to [HISTORY_BLOCKS] history blocks in small chunks. */
    private fun readHistoryBlocks(nfcF: NfcF, idm: ByteArray): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        var block = 0
        while (block < HISTORY_BLOCKS) {
            val chunk = (block until minOf(block + CHUNK, HISTORY_BLOCKS)).toList()
            val response = runCatching {
                nfcF.transceive(
                    buildReadWithoutEncryptionCommand(idm, SUICA_HISTORY_SERVICE, chunk),
                )
            }.getOrNull() ?: break
            // Response: len, 0x07, IDm[8], status1, status2, count, data[16*count]
            if (response.size < 13 || response[10].toInt() != 0x00) break
            val count = response[12].toInt() and 0xFF
            if (count == 0) break
            for (i in 0 until count) {
                val start = 13 + i * 16
                if (start + 16 > response.size) break
                result += response.copyOfRange(start, start + 16)
            }
            block += CHUNK
        }
        return result
    }

    private companion object {
        const val HISTORY_BLOCKS = 20
        const val CHUNK = 4
    }
}
