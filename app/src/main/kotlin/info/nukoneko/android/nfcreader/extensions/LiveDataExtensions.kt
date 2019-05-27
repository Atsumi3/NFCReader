package info.nukoneko.android.nfcreader.extensions

import androidx.annotation.MainThread
import androidx.lifecycle.MutableLiveData

fun <T : Any?> mutableLiveDataOf(): MutableLiveData<T> = MutableLiveData()

@MainThread
fun <T : Any?> mutableLiveDataOf(defaultValue: T): MutableLiveData<T> {
    return MutableLiveData<T>().apply {
        value = defaultValue
    }
}