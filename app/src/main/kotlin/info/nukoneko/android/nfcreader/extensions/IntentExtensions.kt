package info.nukoneko.android.nfcreader.extensions

import android.content.Intent
import android.os.Build
import android.os.Parcelable

inline fun <reified T : Parcelable> Intent.getParcelize(key: String, clazz: Class<T>): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        this.getParcelableExtra(key, clazz)
    } else {
        @Suppress("DEPRECATION")
        this.getParcelableExtra(key) as? T
    }
}
