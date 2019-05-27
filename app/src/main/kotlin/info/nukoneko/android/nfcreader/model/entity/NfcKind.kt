package info.nukoneko.android.nfcreader.model.entity

import android.util.Log

enum class NfcKind(val tagClassPath: String, val tagName: String) {
    ISO_DEP("android.nfc.tech.IsoDep", "ISO-DEP (ISO 14443-4)"),
    NFC_A("android.nfc.tech.NfcA", "NFC-A (ISO 14443-3A)"),
    NFC_B("android.nfc.tech.NfcB", "NFC-B (ISO 14443-3B)"),
    NFC_F("android.nfc.tech.NfcF", "NFC-F (JIS 6319-4)"),
    NFC_V("android.nfc.tech.NfcV", "NFC-V (ISO 15693)"),
    NDEF("android.nfc.tech.Ndef", "NDEF"),
    NDEF_FORMATABLE("android.nfc.tech.NdefFormatable", "NdefFormatable"),
    MIFARE_CLASSIC("android.nfc.tech.MifareClassic", "MifareClassic"),
    MIFARE_ULTRALIGHT("android.nfc.tech.MifareUltralight", "MifareUltralight"),
    NFC_BARCODE("android.nfc.tech.NfcBarcode", "NfcBarcode"),
    BASIC_TAG_TECHNOLOGY("android.nfc.tech.BasicTagTechnology", "BasicTagTechnology"),
    UNKNOWN("", "UNKNOWN");

    companion object {
        fun techOf(tag: String?): NfcKind {
            return values().singleOrNull { it.tagClassPath == tag } ?: kotlin.run {
                if (tag != null && tag.isNotEmpty()) {
                    Log.w("NfcKind", "$tag is not handled.")
                }
                UNKNOWN
            }
        }
    }
}
