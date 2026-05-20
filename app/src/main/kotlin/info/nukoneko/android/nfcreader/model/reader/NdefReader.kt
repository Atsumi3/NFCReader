package info.nukoneko.android.nfcreader.model.reader

import android.nfc.NdefRecord
import android.nfc.tech.Ndef
import android.nfc.tech.TagTechnology
import info.nukoneko.android.nfcreader.extensions.toHexString
import info.nukoneko.android.nfcreader.model.entity.NfcField

/** Parses the NDEF message into individual records with decoded payloads. */
class NdefReader : TechReader {
    override val techName: String = "android.nfc.tech.Ndef"

    override fun read(tech: TagTechnology): List<NfcField> {
        val ndef = tech as Ndef
        val message = runCatching { ndef.ndefMessage }.getOrNull()
            ?: ndef.cachedNdefMessage
            ?: return listOf(NfcField("NDEF", "メッセージなし"))

        val records = message.records
        val fields = mutableListOf(NfcField("NDEF.recordCount", records.size.toString()))
        records.forEachIndexed { index, record ->
            val tag = "NDEF[$index]"
            fields += NfcField("$tag.tnf", tnfName(record.tnf))
            fields += NfcField("$tag.type", record.type.asPrintable())
            fields += NfcField("$tag.payload", decodeRecord(record))
        }
        return fields
    }

    private fun decodeRecord(record: NdefRecord): String {
        val isWellKnown = record.tnf == NdefRecord.TNF_WELL_KNOWN
        return when {
            isWellKnown && record.type.contentEquals(NdefRecord.RTD_URI) ->
                decodeNdefUriPayload(record.payload)

            record.tnf == NdefRecord.TNF_ABSOLUTE_URI ->
                record.type.asPrintable()

            isWellKnown && record.type.contentEquals(NdefRecord.RTD_TEXT) ->
                decodeNdefTextPayload(record.payload)

            else -> record.payload.toHexString()
        }
    }

    private fun ByteArray.asPrintable(): String {
        if (isEmpty()) return "-"
        val text = String(this, Charsets.UTF_8)
        return if (text.none { it.isISOControl() && it != '\t' && it != '\n' }) text
        else toHexString()
    }

    private fun tnfName(tnf: Short): String = when (tnf) {
        NdefRecord.TNF_EMPTY -> "EMPTY"
        NdefRecord.TNF_WELL_KNOWN -> "WELL_KNOWN"
        NdefRecord.TNF_MIME_MEDIA -> "MIME_MEDIA"
        NdefRecord.TNF_ABSOLUTE_URI -> "ABSOLUTE_URI"
        NdefRecord.TNF_EXTERNAL_TYPE -> "EXTERNAL_TYPE"
        NdefRecord.TNF_UNKNOWN -> "UNKNOWN"
        NdefRecord.TNF_UNCHANGED -> "UNCHANGED"
        else -> "0x%02X".format(tnf.toInt())
    }
}
