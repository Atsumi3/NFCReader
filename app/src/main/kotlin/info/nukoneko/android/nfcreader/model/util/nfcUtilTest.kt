package info.nukoneko.android.nfcreader.model.util

import android.nfc.Tag
import android.nfc.tech.*
import android.os.Build
import info.nukoneko.android.nfcreader.extensions.allGetterResults
import info.nukoneko.android.nfcreader.model.entity.NfcKind

private fun allGetterResults(tag: TagTechnology): Map<String, Any?> {
    tag.connect()
    return if (tag.isConnected) {
        val result = tag.allGetterResults()
        tag.close()
        result
    } else {
        emptyMap()
    }
}

object NfcUtil {
    fun getData(kinds: NfcKind, tag: Tag): Map<String, Any?> {
        return try {
            when (kinds) {
                NfcKind.NFC_A -> {
                    allGetterResults(NfcA.get(tag))
                }
                NfcKind.NFC_B -> {
                    allGetterResults(NfcB.get(tag))
                }
                NfcKind.NFC_F -> {
                    allGetterResults(NfcF.get(tag))
                }
                NfcKind.NFC_V -> {
                    allGetterResults(NfcV.get(tag))
                }
                NfcKind.NFC_BARCODE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    allGetterResults(NfcBarcode.get(tag))
                } else {
                    mapOf("Barcode" to "Error: require APILevel > 17")
                }
                NfcKind.MIFARE_CLASSIC -> {
                    allGetterResults(MifareClassic.get(tag))
                }
                NfcKind.MIFARE_ULTRALIGHT -> {
                    allGetterResults(MifareUltralight.get(tag))
                }
                NfcKind.NDEF_FORMATABLE -> {
                    allGetterResults(NdefFormatable.get(tag))
                }
                NfcKind.ISO_DEP -> {
                    allGetterResults(IsoDep.get(tag))
                }
                NfcKind.NDEF -> {
                    allGetterResults(Ndef.get(tag))
                }
                else -> {
                    mapOf("Unknown" to "")
                }
            }
        } catch (e: Exception) {
            mapOf("Error" to e)
        }
    }
}
