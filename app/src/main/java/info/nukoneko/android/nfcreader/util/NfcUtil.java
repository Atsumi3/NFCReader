package info.nukoneko.android.nfcreader.util;

import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcBarcode;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Build;

import java.util.HashMap;
import java.util.Map;

import info.nukoneko.android.nfcreader.model.NfcKinds;

public final class NfcUtil {
    public static Map<String, Object> getData(NfcKinds kinds, Tag tag) {
        Map<String, Object> data = new HashMap<>();
        try {
            switch (kinds) {
                case NFC_A:
                    NfcA nfcA = NfcA.get(tag);
                    nfcA.connect();
                    if (nfcA.isConnected()) {
                        data.put("ACQA", MiscUtil.bytesToHex(nfcA.getAtqa()));
                        data.put("SAK", String.valueOf(nfcA.getSak()));
                        nfcA.close();
                    }
                    break;
                case NFC_B:
                    NfcB nfcB = NfcB.get(tag);
                    nfcB.connect();
                    if (nfcB.isConnected()) {
                        data.put("ApplicationData", MiscUtil.bytesToHex(nfcB.getApplicationData()));
                        data.put("ProtocolInfo", MiscUtil.bytesToHex(nfcB.getProtocolInfo()));
                        nfcB.close();
                    }
                    break;
                case NFC_F:
                    NfcF nfcF = NfcF.get(tag);
                    nfcF.connect();
                    if (nfcF.isConnected()) {
                        data.put("SystemCode", MiscUtil.bytesToHex(nfcF.getSystemCode()));
                        data.put("Manufacturer", MiscUtil.bytesToHex(nfcF.getManufacturer()));
                        nfcF.close();
                    }
                    break;
                case NFC_V:
                    NfcV nfcV = NfcV.get(tag);
                    nfcV.connect();
                    if (nfcV.isConnected()) {
                        data.put("DsfId", String.valueOf(nfcV.getDsfId()));
                        data.put("ResponseFlags", String.valueOf(nfcV.getResponseFlags()));
                        nfcV.close();
                    }
                    break;
                case NFC_BARCODE:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        NfcBarcode nfcBarcode = NfcBarcode.get(tag);
                        nfcBarcode.connect();
                        if (nfcBarcode.isConnected()) {
                            data.put("Barcode", MiscUtil.bytesToHex(nfcBarcode.getBarcode()));
                            nfcBarcode.close();
                        }
                    } else {
                        data.put("Barcode", "Error: requre APILevel > 17");
                    }
                    break;
                case MIFARE_CLASSIC:
                    MifareClassic mifareClassic = MifareClassic.get(tag);
                    mifareClassic.connect();
                    if (mifareClassic.isConnected()) {
                        data.put("BlockCount", mifareClassic.getBlockCount());
                        mifareClassic.close();
                    }
                    break;
                case MIFARE_ULTRALIGHT:
                    MifareUltralight mifareUltralight = MifareUltralight.get(tag);
                    mifareUltralight.connect();
                    if (mifareUltralight.isConnected()) {
                        data.put("Type", mifareUltralight.getType());
                        mifareUltralight.close();
                    }
                    break;
                case NDEF_FORMATABLE:
                    NdefFormatable ndefFormatable = NdefFormatable.get(tag);
                    ndefFormatable.connect();
                    if (ndefFormatable.isConnected()) {
                        ndefFormatable.close();
                    }
                    break;
                case ISO_DEP:
                    IsoDep isoDep = IsoDep.get(tag);
                    isoDep.connect();
                    if (isoDep.isConnected()) {
                        data.put("HistoricalBytes", MiscUtil.bytesToHex(isoDep.getHistoricalBytes()));
                        data.put("HiLayerResponse", MiscUtil.bytesToHex(isoDep.getHiLayerResponse()));
                        isoDep.close();
                    }
                    break;
                case NDEF:
                    Ndef ndef = Ndef.get(tag);
                    ndef.connect();
                    if (ndef.isConnected()) {
                        data.put("CachedNdefMessage", ndef.getCachedNdefMessage() != null ? ndef.getCachedNdefMessage().toString() : "");
                        data.put("NdefMessage", ndef.getNdefMessage() != null ? ndef.getNdefMessage().toString() : "");
                        data.put("Type", ndef.getType());
                        ndef.close();
                    }
                    break;
                case BASIC_TAG_TECHNOLOGY:
                    break;
            }
        } catch (final Exception e) {
            e.printStackTrace();
            return new HashMap<String, Object>() {{
                put("Error", e.getLocalizedMessage());
            }};
        }
        return data;
    }
}
