package info.nukoneko.android.nfcreader.ui.nfclist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import info.nukoneko.android.nfcreader.R;
import info.nukoneko.android.nfcreader.databinding.FragmentNfcListBinding;
import info.nukoneko.android.nfcreader.model.NfcEntity;
import info.nukoneko.android.nfcreader.ui.main.MainViewModel;

public final class NfcListFragment extends Fragment {
    private FragmentNfcListBinding mBinding;
    private MainViewModel mMainViewModel;
    private NfcListViewModel mViewModel;

    private final NfcListViewAdapter.Listener mAdapterListener = new NfcListViewAdapter.Listener() {
        @Override
        public void onEntitySelected(NfcEntity entity) {

        }
    };

    public static NfcListFragment newInstance() {
        return new NfcListFragment();
    }

    @SuppressWarnings("ConstantConditions")
    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding =
                DataBindingUtil.inflate(inflater, R.layout.fragment_nfc_list, container, false);
        mMainViewModel = ViewModelProviders.of(getActivity()).get(MainViewModel.class);
        mViewModel = ViewModelProviders.of(this).get(NfcListViewModel.class);
        mBinding.setViewModel(mViewModel);
        mBinding.setLifecycleOwner(this);
        setupRecyclerView(mBinding.list);
        setupEventObserver();
        return mBinding.getRoot();
    }

    @SuppressWarnings("ConstantConditions")
    private void setupRecyclerView(RecyclerView recyclerView) {
        NfcListViewAdapter adapter = new NfcListViewAdapter();
        adapter.setListener(mAdapterListener);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    private void setupEventObserver() {
        mMainViewModel.getReadTime().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                mViewModel.onUpdateReadTime(s);
            }
        });
        mMainViewModel.getData().observe(this, new Observer<List<NfcEntity>>() {
            @Override
            public void onChanged(@Nullable List<NfcEntity> data) {
                NfcListViewAdapter adapter = (NfcListViewAdapter) mBinding.list.getAdapter();
                if (adapter != null) {
                    adapter.setData(data == null ? Collections.<NfcEntity>emptyList() : data);
                    adapter.notifyDataSetChanged();
                }
            }
        });
    }
}
