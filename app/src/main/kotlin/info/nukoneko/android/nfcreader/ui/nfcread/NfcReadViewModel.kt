package info.nukoneko.android.nfcreader.ui.nfcread

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.TagTechnology
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.nukoneko.android.nfcreader.extensions.allGetterResults
import info.nukoneko.android.nfcreader.model.entity.NfcEntity
import info.nukoneko.android.nfcreader.model.entity.NfcField
import info.nukoneko.android.nfcreader.model.entity.ReadStatus
import info.nukoneko.android.nfcreader.model.reader.TechReaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.reflect.full.staticFunctions

class NfcReadViewModel : ViewModel() {

    private val _state = MutableStateFlow<ReadStatus>(ReadStatus.Idle)
    val state: StateFlow<ReadStatus> = _state.asStateFlow()

    private val _readTime = MutableStateFlow("")
    val readTime: StateFlow<String> = _readTime.asStateFlow()

    private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())

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

        _readTime.value = dateFormat.format(Date())
        _state.value = ReadStatus.Reading
        // TagTechnology#connect is blocking I/O — keep it off the main thread.
        viewModelScope.launch {
            _state.value = runCatching {
                withContext(Dispatchers.IO) { readAllTechs(tag) }
            }.fold(
                onSuccess = { ReadStatus.Success(it) },
                onFailure = { ReadStatus.Failure.Error(it) }
            )
        }
    }

    private fun readAllTechs(tag: Tag): List<NfcEntity> {
        val tagFields = tag.allGetterResults().toFields(prefix = "Tag")
        return tag.techList.distinct().mapNotNull { readTech(tag, it, tagFields) }
    }

    private fun readTech(
        tag: Tag,
        techName: String,
        tagFields: List<NfcField>
    ): NfcEntity? = runCatching {
        val tagClass = Class.forName(techName).kotlin
        val getMethod = tagClass.staticFunctions.firstOrNull { it.name == "get" }
            ?: return@runCatching null
        val instance = getMethod.call(tag) as? TagTechnology
            ?: return@runCatching null
        instance.use { tech ->
            tech.connect()
            if (!tech.isConnected) return@runCatching null
            val genericFields = tech.allGetterResults().toFields()
            // Generic getter dump first; then append protocol-level content if a
            // tag-type-specific reader exists. A reader failure must not drop the
            // generic fields, so it is caught and surfaced as one field.
            val readerFields = TechReaders.forTech(techName)?.let { reader ->
                runCatching { reader.read(tech) }.getOrElse {
                    Log.w(LOG_TAG, "Specialized read failed for $techName.", it)
                    listOf(NfcField("Read.error", it.message ?: it::class.simpleName.orEmpty()))
                }
            }.orEmpty()
            NfcEntity(techName, tagFields + genericFields + readerFields)
        }
    }.onFailure { Log.w(LOG_TAG, "Failed to read $techName.", it) }
        .getOrNull()

    private fun Map<String, String?>.toFields(prefix: String = ""): List<NfcField> =
        map { (key, value) ->
            NfcField(if (prefix.isEmpty()) key else "$prefix.$key", value)
        }

    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
        private const val LOG_TAG = "NfcReadViewModel"
    }
}
