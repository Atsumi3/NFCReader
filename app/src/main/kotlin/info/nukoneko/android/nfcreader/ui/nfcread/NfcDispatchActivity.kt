package info.nukoneko.android.nfcreader.ui.nfcread

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

open class NfcDispatchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchIntent(intent)
    }

    private fun dispatchIntent(intent: Intent) {
        val readIntent = Intent(intent)
            .setClass(this, NfcReadActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(readIntent)
        finish()
    }
}

class ProtectedNfcDispatchActivity : NfcDispatchActivity()
