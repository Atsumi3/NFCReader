package info.nukoneko.android.nfcreader.ui.nfclist;

import android.view.View;

import androidx.databinding.BaseObservable;
import info.nukoneko.android.nfcreader.model.NfcEntity;

public final class ItemNfcListContentViewModel extends BaseObservable {
    private NfcEntity mEntity;
    private Listener mListener;

    ItemNfcListContentViewModel(NfcEntity entity) {
        mEntity = entity;
    }

    void setEntity(NfcEntity entity) {
        mEntity = entity;
        notifyChange();
    }

    void setListener(Listener listener) {
        mListener = listener;
    }

    public String getTagTitle() {
        return mEntity.getName();
    }

    public String getData() {
        return mEntity.getData();
    }

    public String getDescription() {
        return mEntity.getDescription();
    }

    public void onViewClick(@SuppressWarnings("unused") View view) {
        if (mListener != null) mListener.onEntitySelected(mEntity);
    }

    public interface Listener {
        void onEntitySelected(NfcEntity entity);
    }
}
