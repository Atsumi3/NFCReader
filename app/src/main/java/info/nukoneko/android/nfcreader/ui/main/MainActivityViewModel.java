package info.nukoneko.android.nfcreader.ui.main;

import android.databinding.ObservableField;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivityViewModel {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());

    private ObservableField<String> mNfcNames = new ObservableField<>();
    private ObservableField<String> mDate = new ObservableField<>();

    public ObservableField<String> getNfcNames() {
        return mNfcNames;
    }

    public void setNfcNames(String nfcNames) {
        mNfcNames.set(nfcNames);
        mDate.set(dateFormat.format(new Date()));
    }

    public ObservableField<String> getDate() {
        return mDate;
    }
}
