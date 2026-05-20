package info.nukoneko.android.nfcreader.model.reader

import android.nfc.tech.IsoDep
import android.nfc.tech.TagTechnology
import info.nukoneko.android.nfcreader.extensions.hexToBytes
import info.nukoneko.android.nfcreader.extensions.toHexString
import info.nukoneko.android.nfcreader.model.entity.NfcField

/**
 * Reads contactless EMV payment cards over ISO-DEP: discovers applications via
 * the PPSE, then dumps each application's FCI, GPO response and records.
 */
class IsoDepEmvReader : TechReader {
    override val techName: String = "android.nfc.tech.IsoDep"

    override fun read(tech: TagTechnology): List<NfcField> {
        val isoDep = tech as IsoDep

        val ppse = transceive(isoDep, SELECT_PPSE)
            ?: return listOf(NfcField("EMV", "PPSE 応答なし（EMVカードではない可能性）"))
        if (!ppse.isSuccess) {
            return listOf(NfcField("EMV", "PPSE 選択不可 (SW=${ppse.statusWord})"))
        }

        val ppseTlv = parseTlv(ppse.data)
        val aids = extractAids(ppseTlv)
        if (aids.isEmpty()) {
            return listOf(NfcField("EMV", "AID が見つかりません")) +
                dumpTlv("EMV.PPSE", ppseTlv)
        }

        val fields = mutableListOf<NfcField>()
        aids.forEachIndexed { index, aid ->
            val path = "EMV.app[$index]"
            fields += NfcField("$path.aid", aid.toHexString())
            fields += NfcField("$path.scheme", cardScheme(aid))
            fields += readApplication(isoDep, path, aid)
        }
        return fields
    }

    private fun readApplication(isoDep: IsoDep, path: String, aid: ByteArray): List<NfcField> {
        val select = transceive(isoDep, buildSelect(aid))
        if (select == null || !select.isSuccess) {
            return listOf(NfcField("$path.select", "選択不可"))
        }
        val fields = dumpTlv("$path.FCI", parseTlv(select.data)).toMutableList()

        val gpo = transceive(isoDep, GET_PROCESSING_OPTIONS)
        if (gpo == null || !gpo.isSuccess) {
            fields += NfcField("$path.GPO", "取得不可（PDOL要求カードの可能性, best-effort）")
            return fields
        }
        val gpoTlv = parseTlv(gpo.data)
        fields += dumpTlv("$path.GPO", gpoTlv)

        readRecordsFromAfl(isoDep, gpoTlv).forEachIndexed { index, record ->
            fields += dumpTlv("$path.record[$index]", parseTlv(record))
        }
        return fields
    }

    /** Walks the Application File Locator from the GPO response and reads records. */
    private fun readRecordsFromAfl(isoDep: IsoDep, gpoTlv: List<Tlv>): List<ByteArray> {
        val flat = gpoTlv.flatten()
        // AFL is tag 94, or — with the format-1 template (tag 80) — the bytes
        // after the 2-byte AIP.
        val afl = flat.firstOrNull { it.second.tag == "94" }?.second?.value
            ?: flat.firstOrNull { it.second.tag == "80" }?.second?.value
                ?.let { if (it.size > 2) it.copyOfRange(2, it.size) else null }
            ?: return emptyList()

        val records = mutableListOf<ByteArray>()
        var i = 0
        while (i + 4 <= afl.size) {
            val sfi = (afl[i].toInt() and 0xFF) shr 3
            val firstRecord = afl[i + 1].toInt() and 0xFF
            val lastRecord = afl[i + 2].toInt() and 0xFF
            for (record in firstRecord..lastRecord) {
                val response = transceive(isoDep, buildReadRecord(sfi, record))
                if (response != null && response.isSuccess) records += response.data
            }
            i += 4
        }
        return records
    }

    /** Renders the primitive (leaf) TLV nodes as labelled fields. */
    private fun dumpTlv(prefix: String, tlv: List<Tlv>): List<NfcField> =
        tlv.flatten()
            .filterNot { it.second.isConstructed }
            .map { (path, node) ->
                NfcField("$prefix/$path${emvTagName(node.tag)}", node.value.asReadable())
            }

    private fun ByteArray.asReadable(): String {
        if (isEmpty()) return "-"
        val ascii = String(this, Charsets.US_ASCII)
        val isText = size > 1 && all { (it.toInt() and 0xFF) in 0x20..0x7E }
        return if (isText) "$ascii  (${toHexString()})" else toHexString()
    }

    private fun transceive(isoDep: IsoDep, apdu: ByteArray): ApduResponse? =
        runCatching { ApduResponse(isoDep.transceive(apdu)) }.getOrNull()

    private companion object {
        // SELECT "2PAY.SYS.DDF01" (the contactless PPSE).
        val SELECT_PPSE: ByteArray = hexToBytes("00A404000E325041592E5359532E444446303100")

        // GET PROCESSING OPTIONS with an empty PDOL (83 00).
        val GET_PROCESSING_OPTIONS: ByteArray = hexToBytes("80A8000002830000")
    }
}
