package info.nukoneko.android.nfcreader.model.reader

import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.TagTechnology
import info.nukoneko.android.nfcreader.extensions.toHexString
import info.nukoneko.android.nfcreader.model.entity.NfcField

/** Dumps every readable page of a MIFARE Ultralight / NTAG tag. */
class MifareUltralightReader : TechReader {
    override val techName: String = "android.nfc.tech.MifareUltralight"

    override fun read(tech: TagTechnology): List<NfcField> {
        val mful = tech as MifareUltralight
        val fields = mutableListOf<NfcField>()
        var page = 0
        while (page < MAX_PAGES) {
            // readPages returns 4 pages (16 bytes); it throws past the last page.
            val data = runCatching { mful.readPages(page) }.getOrNull() ?: break
            for (i in 0 until data.size / PAGE_SIZE) {
                val start = i * PAGE_SIZE
                fields += NfcField(
                    "Ultralight.page[${page + i}]",
                    data.copyOfRange(start, start + PAGE_SIZE).toHexString(),
                )
            }
            page += 4
        }
        if (fields.isEmpty()) fields += NfcField("Ultralight", "ページ読み取り不可")
        return fields
    }

    private companion object {
        const val PAGE_SIZE = 4
        const val MAX_PAGES = 256
    }
}

/** Dumps MIFARE Classic sectors that open with a well-known default key. */
class MifareClassicReader : TechReader {
    override val techName: String = "android.nfc.tech.MifareClassic"

    override fun read(tech: TagTechnology): List<NfcField> {
        val mfc = tech as MifareClassic
        val fields = mutableListOf<NfcField>()
        for (sector in 0 until mfc.sectorCount) {
            val key = DEFAULT_KEYS.firstOrNull { authenticate(mfc, sector, it.first) }
            if (key == null) {
                fields += NfcField("Classic.sector[$sector]", "認証失敗（既定鍵で開かず）")
                // Sector 0 failing every default key means a custom-keyed card;
                // probing the rest would only add slow, certain-to-fail auths.
                if (sector == 0) {
                    fields += NfcField("Classic", "既定鍵で開けないカードのため探索を打ち切り")
                    break
                }
                continue
            }
            fields += NfcField("Classic.sector[$sector]", "認証成功 (${key.second})")
            val first = mfc.sectorToBlock(sector)
            for (block in first until first + mfc.getBlockCountInSector(sector)) {
                val data = runCatching { mfc.readBlock(block) }.getOrNull()
                fields += NfcField("Classic.block[$block]", data?.toHexString() ?: "読み取り不可")
            }
        }
        if (fields.isEmpty()) fields += NfcField("Classic", "セクタ情報なし")
        return fields
    }

    private fun authenticate(mfc: MifareClassic, sector: Int, key: ByteArray): Boolean =
        runCatching { mfc.authenticateSectorWithKeyA(sector, key) }.getOrDefault(false) ||
            runCatching { mfc.authenticateSectorWithKeyB(sector, key) }.getOrDefault(false)

    private companion object {
        val DEFAULT_KEYS: List<Pair<ByteArray, String>> = listOf(
            MifareClassic.KEY_DEFAULT to "KEY_DEFAULT",
            MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY to "KEY_MAD",
            MifareClassic.KEY_NFC_FORUM to "KEY_NFC_FORUM",
            ByteArray(6) to "ZERO",
        )
    }
}
