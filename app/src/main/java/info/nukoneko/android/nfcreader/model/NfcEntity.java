package info.nukoneko.android.nfcreader.model;

public final class NfcEntity {
    private final String mName;
    private final String mDescription;
    private final String mData;

    public NfcEntity(String name, String description, String data) {
        mName = name;
        mDescription = description;
        mData = data;
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
