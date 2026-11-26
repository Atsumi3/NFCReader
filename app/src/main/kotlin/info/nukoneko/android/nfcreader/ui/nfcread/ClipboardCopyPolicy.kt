package info.nukoneko.android.nfcreader.ui.nfcread

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle

internal const val MAX_DUMP_CLIPBOARD_BYTES = 64 * 1024

internal fun isClipboardCopyAllowed(value: String): Boolean =
    value.toByteArray(Charsets.UTF_8).size <= MAX_DUMP_CLIPBOARD_BYTES

internal fun createSensitivePlainTextClip(label: String, value: String): ClipData =
    ClipData.newPlainText(label, value).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
    }
