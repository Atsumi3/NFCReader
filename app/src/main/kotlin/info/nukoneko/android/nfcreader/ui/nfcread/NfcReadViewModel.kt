package info.nukoneko.android.nfcreader.ui.nfcread

import android.app.Application
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.TagTechnology
import android.util.Log
import android.view.View
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import info.nukoneko.android.nfcreader.R
import info.nukoneko.android.nfcreader.extensions.allGetterResults
import info.nukoneko.android.nfcreader.extensions.mutableLiveDataOf
import info.nukoneko.android.nfcreader.model.entity.NfcEntity
import info.nukoneko.android.nfcreader.model.entity.ReadStatus
import info.nukoneko.android.nfcreader.model.event.VMEvent
import info.nukoneko.android.nfcreader.model.event.postValue
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.reflect.full.staticFunctions

class NfcReadViewModel(application: Application) : AndroidViewModel(application) {
    private val messagePleaseHoldUpDevice = application.getString(R.string.please_hold_up_device)
    private val messageNfcDisabled = application.getString(R.string.nfc_disabled)
    private val messageReadableNfcIsNotFound = application.getString(R.string.readable_nfc_is_not_found)
    private val messageIntentIsNotSupported = application.getString(R.string.intent_is_not_supported)

    private var nfcDisabled = false
    fun onNfcDisabled() {
        readStatus = ReadStatus.FAILED(RuntimeException(messageNfcDisabled))
        nfcDisabled = true
    }

    private val dateFormat: DateFormat by lazy {
        SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
    }

    private val _data: MutableLiveData<VMEvent<List<NfcEntity>>> = mutableLiveDataOf()
    val data: LiveData<VMEvent<List<NfcEntity>>> = _data

    private val _readTime: MutableLiveData<String> = mutableLiveDataOf("")
    val readTime: LiveData<String> = _readTime

    private val _readResultListVisibility: MutableLiveData<Int> = mutableLiveDataOf(View.GONE)
    val readResultListVisibility: LiveData<Int> = _readResultListVisibility

    private val _progressViewVisibility: MutableLiveData<Int> = mutableLiveDataOf(View.GONE)
    val progressViewVisibility: LiveData<Int> = _progressViewVisibility

    private val _messageViewVisibility: MutableLiveData<Int> = mutableLiveDataOf(View.VISIBLE)
    val messageViewVisibility: LiveData<Int> = _messageViewVisibility

    private val _message: MutableLiveData<String> = mutableLiveDataOf(messagePleaseHoldUpDevice)
    val message: LiveData<String> = _message

    private var readStatus: ReadStatus<List<NfcEntity>> = ReadStatus.IDLE()
        set(value) {
            field = value
            when (value) {
                is ReadStatus.IDLE -> {
                    _readResultListVisibility.postValue(View.GONE)
                    _messageViewVisibility.postValue(View.VISIBLE)
                    _message.postValue(messagePleaseHoldUpDevice)
                    _progressViewVisibility.postValue(View.GONE)
                }
                is ReadStatus.READING -> {
                    _readTime.postValue(dateFormat.format(Date()))
                    _data.postValue(emptyList())
                    _readResultListVisibility.postValue(View.GONE)
                    _messageViewVisibility.postValue(View.GONE)
                    _message.postValue("")
                    _progressViewVisibility.postValue(View.VISIBLE)
                }
                is ReadStatus.SUCCESS -> {
                    _data.postValue(value.value)
                    if (value.value.isEmpty()) {
                        _readResultListVisibility.postValue(View.GONE)
                        _messageViewVisibility.postValue(View.VISIBLE)
                        _message.postValue(messageReadableNfcIsNotFound)
                    } else {
                        _readResultListVisibility.postValue(View.VISIBLE)
                        _messageViewVisibility.postValue(View.GONE)
                    }
                    _progressViewVisibility.postValue(View.GONE)
                }
                is ReadStatus.FAILED -> {
                    _readResultListVisibility.postValue(View.GONE)
                    _messageViewVisibility.postValue(View.VISIBLE)
                    _message.postValue(value.error.localizedMessage)
                    _progressViewVisibility.postValue(View.GONE)
                }
            }
        }

    fun onNewIntent(intent: Intent?) {
        if (nfcDisabled) {
            readStatus = ReadStatus.FAILED(RuntimeException(messageNfcDisabled))
            return
        }
        if (intent == null) {
            readStatus = ReadStatus.FAILED(RuntimeException(messageIntentIsNotSupported))
            return
        }
        resolveIntent(intent)
    }

    private fun resolveIntent(intent: Intent) {
        val tag: Tag? = IntentCompat.getParcelableExtra(
            intent,
            NfcAdapter.EXTRA_TAG,
            Tag::class.java
        )
        if (tag == null) {
            readStatus = ReadStatus.FAILED(RuntimeException(messageIntentIsNotSupported))
            return
        }

        readStatus = ReadStatus.READING()

        try {
            val tagResult = tag.allGetterResults()
            val entities: List<NfcEntity> = tag.techList.distinct()
                .mapNotNull { techName -> readTech(tag, techName, tagResult) }
            readStatus = ReadStatus.SUCCESS(entities)
        } catch (e: Throwable) {
            readStatus = ReadStatus.FAILED(e)
        }
    }

    private fun readTech(tag: Tag, techName: String, tagResult: Map<String, String?>): NfcEntity? {
        val tagClass = try {
            Class.forName(techName).kotlin
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "Cannot load class $techName.", t)
            return null
        }

        val getMethod = tagClass.staticFunctions.singleOrNull { it.name == "get" }
        if (getMethod == null) {
            Log.w(LOG_TAG, "$techName has no static get method.")
            return null
        }

        val instance = try {
            getMethod.call(tag)
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "Cannot instantiate $techName via get(tag).", t)
            return null
        }

        if (instance !is TagTechnology) {
            Log.w(LOG_TAG, "$techName is not a TagTechnology.")
            return null
        }

        return try {
            instance.connect()
            if (!instance.isConnected) {
                Log.w(LOG_TAG, "Cannot connect to $techName.")
                return null
            }
            val techResult = instance.allGetterResults()
            val formattedData = buildString {
                for ((key, value) in tagResult) {
                    append("◇ Tag.$key\n")
                    append("${value ?: "-"}\n")
                }
                for ((key, value) in techResult) {
                    append("◇ $key\n")
                    append("${value ?: "-"}\n")
                }
            }
            NfcEntity(techName, formattedData)
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "Failed to read $techName.", t)
            null
        } finally {
            runCatching { instance.close() }
        }
    }

    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
        private const val LOG_TAG = "NfcReadViewModel"
    }
}
