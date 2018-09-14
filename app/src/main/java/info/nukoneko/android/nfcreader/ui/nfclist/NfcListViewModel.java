package info.nukoneko.android.nfcreader.ui.nfclist;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public final class NfcListViewModel extends AndroidViewModel {
    private MutableLiveData<String> mReadTime = new MutableLiveData<>();

    public NfcListViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<String> getReadTime() {
        return mReadTime;
    }

    void onUpdateReadTime(String readTime) {
        mReadTime.postValue(readTime);
    }
}
