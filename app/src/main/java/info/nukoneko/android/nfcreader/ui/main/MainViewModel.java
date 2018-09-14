package info.nukoneko.android.nfcreader.ui.main;

import android.app.Application;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.util.Log;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import info.nukoneko.android.nfcreader.model.NfcEntity;
import info.nukoneko.android.nfcreader.model.NfcKinds;
import info.nukoneko.android.nfcreader.util.NfcUtil;

public final class MainViewModel extends AndroidViewModel {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.JAPAN);
    private final MutableLiveData<List<NfcEntity>> mData = new MutableLiveData<>();
    private MutableLiveData<String> mReadTime = new MutableLiveData<>();
    private MutableLiveData<Integer> mContentViewVisibility = new MutableLiveData<>();
    private MutableLiveData<Integer> mEmptyViewVisibility = new MutableLiveData<>();
    private MutableLiveData<String> mEmptyText = new MutableLiveData<>();

    public MainViewModel(@NonNull Application application) {
        super(application);
        mContentViewVisibility.setValue(View.GONE);
        mEmptyViewVisibility.setValue(View.VISIBLE);
        mEmptyText.postValue("端末をかざしてください");
    }

    public LiveData<List<NfcEntity>> getData() {
        return mData;
    }

    public LiveData<String> getReadTime() {
        return mReadTime;
    }

    public LiveData<Integer> getContentViewVisibility() {
        return mContentViewVisibility;
    }

    public LiveData<Integer> getEmptyViewVisibility() {
        return mEmptyViewVisibility;
    }

    public LiveData<String> getEmptyText() {
        return mEmptyText;
    }

    private void updateReadTime() {
        mReadTime.postValue(DATE_FORMAT.format(new Date()));
    }

    void onNfcDisabled() {
        mContentViewVisibility.postValue(View.GONE);
        mEmptyViewVisibility.postValue(View.VISIBLE);
        mEmptyText.postValue("NFCを利用できません");
    }

    void onNewIntent(Intent intent) {
        if (intent == null) {
            Log.d(getClass().getCanonicalName(), "onNewIntent: intent is null.");
        } else {
            resolveIntent(intent);
        }
    }

    private void resolveIntent(@NonNull final Intent intent) {
        final Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        final List<NfcKinds> tagList = new ArrayList<>();
        for (final String action : tag.getTechList()) {
            tagList.add(NfcKinds.tagOf(action));
        }
        setEntity(tagList, tag);
        updateReadTime();
    }

    private void setEntity(final List<NfcKinds> tagList, Tag tag) {
        final List<NfcEntity> entities = new ArrayList<>();

        for (NfcKinds kinds : tagList) {
            entities.add(new NfcEntity(kinds.getName(), kinds.getTag(), NfcUtil.getData(kinds, tag)));
        }

        if (entities.isEmpty()) {
            mContentViewVisibility.postValue(View.GONE);
            mEmptyViewVisibility.postValue(View.VISIBLE);
            mEmptyText.postValue("端末をかざしてください");
        } else {
            mContentViewVisibility.postValue(View.VISIBLE);
            mEmptyViewVisibility.postValue(View.GONE);
        }

        mData.postValue(entities);
    }
}
