package info.nukoneko.android.nfcreader.ui.nfcread

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import info.nukoneko.android.nfcreader.R
import info.nukoneko.android.nfcreader.databinding.ActivityNfcReadBinding
import info.nukoneko.android.nfcreader.model.event.safetyObserve

class NfcReadActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNfcReadBinding

    private val viewModel: NfcReadViewModel by lazy {
        ViewModelProviders.of(this)[NfcReadViewModel::class.java]
    }

    private val adapter: NfcReadResultListAdapter by lazy {
        NfcReadResultListAdapter()
    }

    private val nfcAdapter: NfcAdapter? by lazy {
        NfcAdapter.getDefaultAdapter(this)
    }

    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getActivity(this, 0,
                Intent(this, this.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_nfc_read)
        binding.lifecycleOwner = this
        binding.viewModel = viewModel
        setupList(binding.list)
        setupEventSubscriber()
    }

    private fun setupEventSubscriber() {
        viewModel.data.safetyObserve(this) { data ->
            adapter.data = data
        }
    }

    private fun setupList(list: RecyclerView) {
        list.adapter = adapter
        list.layoutManager = LinearLayoutManager(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        viewModel.onNewIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter == null) {
            viewModel.onNfcDisabled()
        } else {
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }
}