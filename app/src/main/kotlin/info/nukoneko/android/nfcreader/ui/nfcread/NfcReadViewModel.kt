package info.nukoneko.android.nfcreader.ui.nfcread

import android.app.Application
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.TagTechnology
import android.util.Log
import android.view.View
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
import java.util.*
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
        if (!nfcDisabled) {
            if (intent != null) {
                resolveIntent(intent)
            } else {
                readStatus = ReadStatus.FAILED(RuntimeException(messageIntentIsNotSupported))
            }
        } else {
            readStatus = ReadStatus.FAILED(RuntimeException(messageNfcDisabled))
        }
    }

    private fun resolveIntent(intent: Intent) {
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        if (tag == null) {
            readStatus = ReadStatus.FAILED(RuntimeException(messageIntentIsNotSupported))
            return
        }

        readStatus = ReadStatus.READING()

        try {
            val entities: List<NfcEntity> = tag.techList.distinct()
                    .map { techName ->
                        // 読み込まれたNFCタグのクラス情報を取得
                        val clazz = Class.forName(techName).newInstance()

                        // NFCタグクラスには static function で get が生えているはずなので見つける
                        val getMethod = clazz::class.staticFunctions.singleOrNull { it.name == "get" }

                        if (getMethod == null) {
                            // get メソッドが見つからなかった
                            Log.w("tag", "$techName has't get method.")
                            return@map null
                        }

                        // インスタンス化 TagClass.get(tag)
                        val instance = getMethod.call(tag)

                        if (instance == null) {
                            // インスタンス生成ができなかった
                            Log.w("tag", "Can't instantiate $techName by Clazz.get(tag).")
                            return@map null
                        }

                        if (instance is TagTechnology) {
                            // 生成されたインスタンスが TagTechnology を継承していた
                            instance.connect()
                            if (instance.isConnected) {
                                val result = tag.allGetterResults()
                                instance.close()

                                val formattedData = StringBuilder()
                                for (entry in result.entries) {
                                    formattedData.append("◇ ${entry.key}\n").append("${entry.value
                                            ?: "-"} \n")
                                }
                                NfcEntity(techName, formattedData.toString())
                            } else {
                                Log.w("tag", "Can't connect to $techName.")
                                return@map null
                            }
                        } else {
                            // 生成されたインスタンスが TagTechnology を継承していなかった
                            Log.w("tag", "$techName type is not TagTechnology.")
                            return@map null
                        }
                    }
                    .filter { it != null }
                    .mapNotNull { it }
            readStatus = ReadStatus.SUCCESS(entities)

        } catch (e: Throwable) {
            readStatus = ReadStatus.FAILED(e)
        }
    }

    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
    }
}