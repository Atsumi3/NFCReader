package info.nukoneko.android.nfcreader.ui.main;

import android.app.PendingIntent;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcBarcode;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import info.nukoneko.android.nfcreader.R;
import info.nukoneko.android.nfcreader.adapter.NfcViewAdapter;
import info.nukoneko.android.nfcreader.databinding.ActivityMainBinding;
import info.nukoneko.android.nfcreader.model.NfcEntity;
import info.nukoneko.android.nfcreader.model.NfcKinds;

public final class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private NfcAdapter mNfcAdapter;
    private PendingIntent mPendingIntent;
    private NfcViewAdapter mViewAdapter;
    private MainActivityViewModel mViewModel = new MainActivityViewModel();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ActivityMainBinding mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        mBinding.setViewModel(mViewModel);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        mPendingIntent =
                PendingIntent.getActivity(this, 0, new Intent(this, this.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        mViewAdapter = new NfcViewAdapter(this);
        mBinding.recyclerView.setAdapter(mViewAdapter);
        mBinding.recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        mBinding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void resolveIntent(@NonNull final Intent intent) {
        final Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        final List<NfcKinds> tagList = new ArrayList<>();
        for (final String action : tag.getTechList()) {
            tagList.add(NfcKinds.tagOf(action));
        }
        setTitle(tagList);
        setEntity(tagList, tag);
    }

    private void setTitle(final List<NfcKinds> tagList) {
        final StringBuilder builder = new StringBuilder();

        for (int i = 0; i < tagList.size(); i++) {
            builder.append(tagList.get(i));
            if (i != tagList.size() - 1) builder.append("\n");
        }

        mViewModel.setNfcNames(builder.toString());
    }

    private void setEntity(final List<NfcKinds> tagList, Tag tag) {
        final List<NfcEntity> entities = new ArrayList<>();

        for (NfcKinds kinds : tagList) {
            entities.add(new NfcEntity(kinds.getName(), kinds.getTag(), getData(kinds, tag)));
        }

        mViewAdapter.updateList(entities);
    }

    private String getData(NfcKinds kinds, Tag tag) {
        String responseData = "";
        try {
            switch (kinds) {
                case NFC_A:
                    NfcA nfcA = NfcA.get(tag);
                    nfcA.connect();
                    if (nfcA.isConnected()) {
                        responseData = "【ACQA】" +
                                "\n" + Arrays.toString(nfcA.getAtqa()) +
                                "\n" + "【SAK】" +
                                "\n" + String.valueOf(nfcA.getSak());
                        nfcA.close();
                    }
                    break;
                case NFC_B:
                    NfcB nfcB = NfcB.get(tag);
                    nfcB.connect();
                    if (nfcB.isConnected()) {
                        responseData = "【ApplicationData】" +
                                "\n" + Arrays.toString(nfcB.getApplicationData()) +
                                "\n" + "【ProtocolInfo】" +
                                "\n" + Arrays.toString(nfcB.getProtocolInfo());
                        nfcB.close();
                    }
                    break;
                case NFC_F:
                    NfcF nfcF = NfcF.get(tag);
                    nfcF.connect();
                    if (nfcF.isConnected()) {
                        responseData = "【SystemCode】" +
                                "\n" + Arrays.toString(nfcF.getSystemCode()) +
                                "\n" + "【Manufacturer】" +
                                "\n" + Arrays.toString(nfcF.getManufacturer());
                        nfcF.close();
                    }
                    break;
                case NFC_V:
                    NfcV nfcV = NfcV.get(tag);
                    nfcV.connect();
                    if (nfcV.isConnected()) {
                        responseData = "【DsfId】" +
                                "\n" + String.valueOf(nfcV.getDsfId()) +
                                "\n" + "【ResponseFlags】" +
                                "\n" + String.valueOf(nfcV.getResponseFlags());
                        nfcV.close();
                    }
                    break;
                case NFC_BARCODE:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        NfcBarcode nfcBarcode = NfcBarcode.get(tag);
                        nfcBarcode.connect();
                        if (nfcBarcode.isConnected()) {
                            responseData = "【barcode】" + "\n" + Arrays.toString(nfcBarcode.getBarcode());
                            nfcBarcode.close();
                        }
                    } else {
                        responseData = "【barcode】" + "\n" + "Error";
                    }
                    break;
                case MIFARE_CLASSIC:
                    MifareClassic mifareClassic = MifareClassic.get(tag);
                    mifareClassic.connect();
                    if (mifareClassic.isConnected()) {
                        responseData = "【blockCount】" +
                                "\n" + mifareClassic.getBlockCount();
                        mifareClassic.close();
                    }
                    break;
                case MIFARE_ULTRALIGHT:
                    MifareUltralight mifareUltralight = MifareUltralight.get(tag);
                    mifareUltralight.connect();
                    if (mifareUltralight.isConnected()) {
                        responseData = "【type】" + "\n" + mifareUltralight.getType();
                        mifareUltralight.close();
                    }
                    break;
                case NDEF_FORMATABLE:
                    NdefFormatable ndefFormatable = NdefFormatable.get(tag);
                    ndefFormatable.connect();
                    if (ndefFormatable.isConnected()) {
                        responseData = "";
                        ndefFormatable.close();
                    }
                    break;
                case ISO_DEP:
                    IsoDep isoDep = IsoDep.get(tag);
                    isoDep.connect();
                    if (isoDep.isConnected()) {
                        responseData = "【HistoricalBytes】" +
                                "\n" + Arrays.toString(isoDep.getHistoricalBytes()) +
                                "\n" + "【HiLayerResponse】" +
                                "\n" + Arrays.toString(isoDep.getHiLayerResponse());
                        isoDep.close();
                    }
                    break;
                case NDEF:
                    Ndef ndef = Ndef.get(tag);
                    ndef.connect();
                    if (ndef.isConnected()) {
                        responseData = "【CachedNdefMessage】" +
                                "\n" + (ndef.getCachedNdefMessage() != null ? ndef.getCachedNdefMessage().toString() : "") +
                                "\n" + "【NdefMessage】" +
                                "\n" + (ndef.getNdefMessage() != null ? ndef.getNdefMessage().toString() : "") +
                                "\n" + "【Type】" +
                                "\n" + ndef.getType();
                        ndef.close();
                    }
                    break;
                case BASIC_TAG_TECHNOLOGY:
                    return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return e.getLocalizedMessage();
        }
        return responseData;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) {
            Log.d(TAG, "onNewIntent: intent is null.");
        } else {
            resolveIntent(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mNfcAdapter != null)
            mNfcAdapter.enableForegroundDispatch(this, mPendingIntent, null, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mNfcAdapter != null) mNfcAdapter.disableForegroundDispatch(this);
    }
}
