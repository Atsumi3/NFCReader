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
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import info.nukoneko.android.nfcreader.R
import info.nukoneko.android.nfcreader.extensions.allGetterResults
import info.nukoneko.android.nfcreader.model.entity.NfcEntity
import info.nukoneko.android.nfcreader.model.entity.ReadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.reflect.full.staticFunctions

class NfcReadViewModel(application: Application) : AndroidViewModel(application) {

    private val messagePleaseHoldUpDevice =
        application.getString(R.string.please_hold_up_device)
    private val messageNfcDisabled =
        application.getString(R.string.nfc_disabled)
    private val messageReadableNfcIsNotFound =
        application.getString(R.string.readable_nfc_is_not_found)
    private val messageIntentIsNotSupported =
        application.getString(R.string.intent_is_not_supported)

    private val dateFormat: DateFormat by lazy {
        SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
    }

    private val _state = MutableLiveData<ReadStatus>(ReadStatus.Idle)
    val state: LiveData<ReadStatus> = _state

    val readTime: LiveData<String> = _state
        .map { status -> if (status is ReadStatus.Reading) dateFormat.format(Date()) else "" }
        .distinctUntilChanged()

    val readResultListVisibility: LiveData<Int> = _state
        .map { status -> if (status is ReadStatus.Success && status.entities.isNotEmpty()) View.VISIBLE else View.GONE }
        .distinctUntilChanged()

    val progressViewVisibility: LiveData<Int> = _state
        .map { status -> if (status is ReadStatus.Reading) View.VISIBLE else View.GONE }
        .distinctUntilChanged()

    val messageViewVisibility: LiveData<Int> = _state
        .map { status -> if (status.shouldShowMessage()) View.VISIBLE else View.GONE }
        .distinctUntilChanged()

    val message: LiveData<String> = _state
        .map { status -> status.toMessage() }
        .distinctUntilChanged()

    fun onNfcDisabled() {
        _state.value = ReadStatus.Failure.NfcDisabled
    }

    fun onNewIntent(intent: Intent?) {
        if (_state.value == ReadStatus.Failure.NfcDisabled) return

        val tag = intent?.let {
            IntentCompat.getParcelableExtra(it, NfcAdapter.EXTRA_TAG, Tag::class.java)
        }
        if (tag == null) {
            _state.value = ReadStatus.Failure.IntentUnsupported
            return
        }

        _state.value = ReadStatus.Reading
        // TagTechnology#connect is blocking I/O — keep it off the main thread.
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { readAllTechs(tag) }
            }
            _state.value = result.fold(
                onSuccess = { ReadStatus.Success(it) },
                onFailure = { ReadStatus.Failure.Error(it) }
            )
        }
    }

    private fun readAllTechs(tag: Tag): List<NfcEntity> {
        val tagResult = tag.allGetterResults()
        return tag.techList.distinct().mapNotNull { readTech(tag, it, tagResult) }
    }

    private fun readTech(
        tag: Tag,
        techName: String,
        tagResult: Map<String, String?>
    ): NfcEntity? = runCatching {
        val tagClass = Class.forName(techName).kotlin
        val getMethod = tagClass.staticFunctions.firstOrNull { it.name == "get" }
            ?: return@runCatching null
        val instance = getMethod.call(tag) as? TagTechnology
            ?: return@runCatching null
        instance.use { tech ->
            tech.connect()
            if (!tech.isConnected) return@runCatching null
            val techResult = tech.allGetterResults()
            NfcEntity(techName, formatEntries(tagResult, techResult))
        }
    }.onFailure { Log.w(LOG_TAG, "Failed to read $techName.", it) }
        .getOrNull()

    private fun formatEntries(
        tagResult: Map<String, String?>,
        techResult: Map<String, String?>
    ): String = buildString {
        appendSection(tagResult, prefix = "Tag")
        appendSection(techResult)
    }

    private fun StringBuilder.appendSection(
        entries: Map<String, String?>,
        prefix: String = ""
    ) {
        val labelPrefix = if (prefix.isEmpty()) "" else "$prefix."
        for ((key, value) in entries) {
            appendLine("◇ $labelPrefix$key")
            appendLine(value ?: "-")
        }
    }

    private fun ReadStatus.shouldShowMessage(): Boolean = when (this) {
        ReadStatus.Idle -> true
        ReadStatus.Reading -> false
        is ReadStatus.Success -> entities.isEmpty()
        is ReadStatus.Failure -> true
    }

    private fun ReadStatus.toMessage(): String = when (this) {
        ReadStatus.Idle -> messagePleaseHoldUpDevice
        ReadStatus.Reading -> ""
        is ReadStatus.Success ->
            if (entities.isEmpty()) messageReadableNfcIsNotFound else ""
        ReadStatus.Failure.NfcDisabled -> messageNfcDisabled
        ReadStatus.Failure.IntentUnsupported -> messageIntentIsNotSupported
        is ReadStatus.Failure.Error -> cause.localizedMessage.orEmpty()
    }

    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
        private const val LOG_TAG = "NfcReadViewModel"
    }
}
