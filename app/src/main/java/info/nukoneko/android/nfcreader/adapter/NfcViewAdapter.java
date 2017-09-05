package info.nukoneko.android.nfcreader.adapter;

import android.content.Context;
import android.databinding.DataBindingUtil;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import info.nukoneko.android.nfcreader.R;
import info.nukoneko.android.nfcreader.databinding.ItemNfcBinding;
import info.nukoneko.android.nfcreader.model.NfcEntity;

public final class NfcViewAdapter extends RecyclerView.Adapter<NfcViewAdapter.ViewHolder> {

    private final Context mContext;
    private final List<NfcEntity> mEntities = new ArrayList<>();

    public NfcViewAdapter(@NonNull Context context) {
        mContext = context;
    }

    public void updateList(@NonNull Collection<NfcEntity> entities) {
        mEntities.clear();
        mEntities.addAll(entities);
        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(mContext).inflate(R.layout.item_nfc, parent, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final ItemNfcBinding binding = holder.getBinding();
        final NfcEntity entity = mEntities.get(position);
        binding.setEntity(entity);
    }

    @Override
    public int getItemCount() {
        return mEntities.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemNfcBinding mBinding;

        ViewHolder(View itemView) {
            super(itemView);
            mBinding = DataBindingUtil.bind(itemView);
        }

        ItemNfcBinding getBinding() {
            return mBinding;
        }
    }
}
