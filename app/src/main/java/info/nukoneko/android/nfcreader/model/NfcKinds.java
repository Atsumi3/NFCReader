package info.nukoneko.android.nfcreader.model;

public enum NfcKinds {
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

    private final String mTag;
    private final String mName;

    NfcKinds(String tag, String name) {
        mTag = tag;
        mName = name;
    }

    public static NfcKinds tagOf(String tag) {
        for (NfcKinds kinds : NfcKinds.values()) {
            if (kinds.mTag.equals(tag)) return kinds;
        }
        return UNKNOWN;
    }

    public String getTag() {
        return mTag;
    }

    public String getName() {
        return mName;
    }
}
