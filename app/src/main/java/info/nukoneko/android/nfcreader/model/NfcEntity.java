package info.nukoneko.android.nfcreader.model;

public final class NfcEntity {
    private final NfcKinds mNfcKind;
    private final String mName;
    private final String mDescription;
    private final String mData;

    public NfcEntity(NfcKinds nfcKind, String data) {
        mNfcKind = nfcKind;
        mName = nfcKind.getName();
        mDescription = nfcKind.getTag();
        mData = data;
    }

    public NfcKinds getNfcKind() {
        return mNfcKind;
    }

    public String getName() {
        return mName;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getData() {
        return mData;
    }
}
