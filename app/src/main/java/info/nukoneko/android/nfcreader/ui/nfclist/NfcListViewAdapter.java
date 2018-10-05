package info.nukoneko.android.nfcreader.ui.nfclist;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;
import info.nukoneko.android.nfcreader.R;
import info.nukoneko.android.nfcreader.databinding.ItemNfcListContentBinding;
import info.nukoneko.android.nfcreader.model.NfcEntity;

public final class NfcListViewAdapter extends RecyclerView.Adapter {
    @NonNull
    private List<NfcEntity> mData;

    private Listener mListener;

    NfcListViewAdapter() {
        mData = Collections.emptyList();
    }

    void setData(@NonNull List<NfcEntity> data) {
        mData = data;
    }

    void setListener(Listener listener) {
        mListener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNfcListContentBinding binding =
                DataBindingUtil.inflate(
                        LayoutInflater.from(parent.getContext()),
                        R.layout.item_nfc_list_content,
                        parent,
                        false
                );

        return new ContentViewHolder(binding, new ContentViewHolder.Listener() {
            @Override
            public void onEntitySelected(NfcEntity entity) {
                if (mListener != null) mListener.onEntitySelected(entity);
            }
        });
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ContentViewHolder) {
            ((ContentViewHolder) holder).bindEntity(mData.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return mData.size();
    }

    public interface Listener {
        void onEntitySelected(NfcEntity entity);
    }

    static class ContentViewHolder extends RecyclerView.ViewHolder {
        private final ItemNfcListContentBinding mBinding;
        private final ItemNfcListContentViewModel.Listener mListener;

        ContentViewHolder(ItemNfcListContentBinding binding, final Listener listener) {
            super(binding.item);
            mBinding = binding;
            mListener = new ItemNfcListContentViewModel.Listener() {
                @Override
                public void onEntitySelected(NfcEntity entity) {
                    if (listener != null) listener.onEntitySelected(entity);
                }
            };
        }

        void bindEntity(NfcEntity entity) {
            if (mBinding.getViewModel() == null) {
                final ItemNfcListContentViewModel viewModel = new ItemNfcListContentViewModel(entity);
                viewModel.setListener(mListener);
                mBinding.setViewModel(viewModel);
            } else {
                mBinding.getViewModel().setEntity(entity);
            }
        }

        interface Listener {
            void onEntitySelected(NfcEntity entity);
        }
    }
}
