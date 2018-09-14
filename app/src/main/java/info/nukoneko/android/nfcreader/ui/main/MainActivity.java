package info.nukoneko.android.nfcreader.ui.main;

import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProviders;
import info.nukoneko.android.nfcreader.R;
import info.nukoneko.android.nfcreader.databinding.ActivityMainBinding;
import info.nukoneko.android.nfcreader.ui.nfclist.NfcListFragment;

public final class MainActivity extends AppCompatActivity {
    @Nullable
    private NfcAdapter mNfcAdapter;
    private PendingIntent mPendingIntent;
    private MainViewModel mViewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityMainBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        mViewModel = ViewModelProviders.of(this).get(MainViewModel.class);
        binding.setViewModel(mViewModel);
        binding.setLifecycleOwner(this);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, NfcListFragment.newInstance())
                .commit();

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        mPendingIntent =
                PendingIntent.getActivity(this, 0, new Intent(this, this.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        mViewModel.onNewIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mNfcAdapter != null) {
            mNfcAdapter.enableForegroundDispatch(this, mPendingIntent, null, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mNfcAdapter != null)
            mNfcAdapter.disableForegroundDispatch(this);
    }
}
