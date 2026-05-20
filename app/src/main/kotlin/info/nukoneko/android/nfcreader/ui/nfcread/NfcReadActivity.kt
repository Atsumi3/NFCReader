package info.nukoneko.android.nfcreader.ui.nfcread

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.app.PendingIntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.nukoneko.android.nfcreader.ui.theme.NfcReaderTheme

class NfcReadActivity : ComponentActivity() {

    private val viewModel: NfcReadViewModel by viewModels()

    private val nfcAdapter: NfcAdapter? by lazy {
        NfcAdapter.getDefaultAdapter(this)
    }

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(this, javaClass)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        // Foreground dispatch needs the system to inject the Tag extra into the
        // Intent, so the PendingIntent must be mutable on API 31+.
        PendingIntentCompat.getActivity(this, 0, intent, 0, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NfcReaderTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val readTime by viewModel.readTime.collectAsStateWithLifecycle()
                NfcReadScreen(state = state, readTime = readTime)
            }
        }
        // Tapping a tag while the app is closed cold-starts the Activity and
        // delivers the Tag through the launch Intent — onNewIntent is not
        // called. The savedInstanceState guard avoids re-reading an already
        // removed tag when the Activity is recreated on configuration change.
        if (savedInstanceState == null) {
            handleNfcIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent) {
        if (intent.action in NFC_DISCOVERY_ACTIONS) {
            viewModel.onNewIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter
        if (adapter == null) {
            viewModel.onNfcDisabled()
        } else {
            adapter.enableForegroundDispatch(this, pendingIntent, null, null)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    companion object {
        private val NFC_DISCOVERY_ACTIONS = setOf(
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
        )
    }
}
