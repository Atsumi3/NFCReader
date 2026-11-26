package info.nukoneko.android.nfcreader.ui.nfcread

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcBarcode
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.nfc.tech.TagTechnology
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.nukoneko.android.nfcreader.extensions.toHexString
import info.nukoneko.android.nfcreader.extensions.toHexStringCompact
import info.nukoneko.android.nfcreader.model.entity.NfcDump
import info.nukoneko.android.nfcreader.model.entity.NfcEntity
import info.nukoneko.android.nfcreader.model.entity.NfcField
import info.nukoneko.android.nfcreader.model.entity.NfcTagMetadata
import info.nukoneko.android.nfcreader.model.entity.ReadIssue
import info.nukoneko.android.nfcreader.model.entity.ReadIssueKind
import info.nukoneko.android.nfcreader.model.entity.ReadStatus
import info.nukoneko.android.nfcreader.model.reader.NfcDumpCollector
import info.nukoneko.android.nfcreader.model.reader.ReadBudgetExceededException
import info.nukoneko.android.nfcreader.model.reader.TechReadContext
import info.nukoneko.android.nfcreader.model.reader.TechReaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NfcReadViewModel : ViewModel() {

    private val _state = MutableStateFlow<ReadStatus>(ReadStatus.Idle)
    val state: StateFlow<ReadStatus> = _state.asStateFlow()

    private val _readTime = MutableStateFlow("")
    val readTime: StateFlow<String> = _readTime.asStateFlow()

    private val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
    private val scanMutex = Mutex()
    private val closeExecutor = ScheduledThreadPoolExecutor(2) { runnable ->
        Thread(runnable, "NfcReadWatchdog").apply { isDaemon = true }
    }
    private val readGenerations = ReadGenerationGate()
    private var readJob: Job? = null
    @Volatile private var activeTechnology: TagTechnology? = null

    fun stopReading() {
        stopActiveRead()
    }

    private var pendingDumpFileExport: Pair<String, String>? = null

    @Synchronized
    fun setPendingDumpFileExport(fileName: String, content: String) {
        pendingDumpFileExport = fileName to content
    }

    @Synchronized
    fun takePendingDumpFileExport(): Pair<String, String>? =
        pendingDumpFileExport.also { pendingDumpFileExport = null }

    fun onNfcDisabled() {
        val previousDump = _state.value.currentDump()
        val generation = readGenerations.advance()
        stopActiveRead()
        _state.value = previousDump?.let { ReadStatus.Failure.NfcDisabledWithDump(it) }
            ?: ReadStatus.Failure.NfcDisabled
        retainStoppedDump(generation, previousDump) { ReadStatus.Failure.NfcDisabledWithDump(it) }
    }

    fun onNewIntent(intent: Intent?) {
        if (_state.value == ReadStatus.Failure.NfcDisabled ||
            _state.value is ReadStatus.Failure.NfcDisabledWithDump
        ) return

        val tag = intent?.let {
            IntentCompat.getParcelableExtra(it, NfcAdapter.EXTRA_TAG, Tag::class.java)
        }
        if (tag == null) {
            val previousDump = _state.value.currentDump()
            val generation = readGenerations.advance()
            stopActiveRead()
            _state.value = previousDump?.let { ReadStatus.Failure.IntentUnsupportedWithDump(it) }
                ?: ReadStatus.Failure.IntentUnsupported
            retainStoppedDump(generation, previousDump) { ReadStatus.Failure.IntentUnsupportedWithDump(it) }
            return
        }

        val previousDump = _state.value.currentDump()
        _readTime.value = dateFormat.format(Date())
        val generation = readGenerations.advance()
        stopActiveRead()
        val techNamesResult = runCatching { tag.techList.distinct() }
        val techNames = techNamesResult.getOrDefault(emptyList())
        val initialIssues = techNamesResult.exceptionOrNull()?.let { listOf(issueFor(it, "Tag technologies")) }
            .orEmpty()
        val collector = NfcDumpCollector(
            tagMetadata = NfcTagMetadata(techNames = techNames),
            initialIssues = initialIssues,
        )
        _state.value = previousDump?.let { ReadStatus.ReadingWithPrevious(it) } ?: ReadStatus.Reading
        readJob = viewModelScope.launch {
            val thisJob = currentCoroutineContext()[Job]
            try {
                scanMutex.withLock {
                    if (!readGenerations.isCurrent(generation)) return@withLock
                    val stoppedDump = readGenerations.takePendingDump()
                    if (stoppedDump != null) _state.value = ReadStatus.ReadingWithPrevious(stoppedDump)
                    try {
                        withContext(Dispatchers.IO) {
                            val (tagMetadata, metadataIssues) = readTagMetadata(tag, techNames)
                            collector.updateTagMetadata(tagMetadata, metadataIssues)
                            val job = currentCoroutineContext()[Job]
                            collector.readAll(
                                cancellationCheck = { job?.ensureActive() },
                                readTechnology = { techName, context -> readTech(tag, techName, context) },
                            )
                        }
                        publishSuccess(generation, collector.snapshot())
                    } catch (exception: CancellationException) {
                        collector.markInterrupted()
                        publishInterruptedDump(generation, collector.snapshot())
                        throw exception
                    } catch (exception: Exception) {
                        Log.w(LOG_TAG, "Failed to read NFC tag.", exception)
                        collector.addIssue(
                            ReadIssue(
                                kind = ReadIssueKind.OTHER,
                                message = "Read failed: ${exception::class.simpleName ?: "Exception"}.",
                            )
                        )
                        val dump = collector.snapshot()
                        if (dump.technologies.isNotEmpty() || dump.tag.techNames.isNotEmpty()) {
                            publishSuccess(generation, dump)
                        } else if (readGenerations.isCurrent(generation)) {
                            _state.value = ReadStatus.Failure.Error(exception)
                        }
                    }
                }
            } finally {
                if (readJob === thisJob) readJob = null
            }
        }
    }

    private fun publishSuccess(generation: Long, dump: NfcDump) {
        if (readGenerations.publish(generation, dump)) {
            _state.value = ReadStatus.Success(dump.technologies, dump)
        }
    }

    private fun publishInterruptedDump(generation: Long, dump: NfcDump) {
        publishSuccess(generation, dump)
    }

    private fun retainStoppedDump(
        generation: Long,
        fallback: NfcDump?,
        status: (NfcDump) -> ReadStatus,
    ) {
        viewModelScope.launch {
            scanMutex.withLock {
                if (!readGenerations.isCurrent(generation)) return@withLock
                val dump = readGenerations.takePendingDump() ?: fallback
                if (dump != null) _state.value = status(dump)
            }
        }
    }

    private fun ReadStatus.currentDump(): NfcDump? = when (this) {
        is ReadStatus.Success -> dump
        is ReadStatus.ReadingWithPrevious -> dump
        is ReadStatus.Failure.NfcDisabledWithDump -> dump
        is ReadStatus.Failure.IntentUnsupportedWithDump -> dump
        else -> null
    }

    private fun readTagMetadata(
        tag: Tag,
        techNames: List<String>,
    ): Pair<NfcTagMetadata, List<ReadIssue>> {
        val idHex = runCatching { tag.id.toHexStringCompact() }.getOrNull()
        val fields = listOfNotNull(idHex?.let { NfcField("Tag.id", it) })
        return NfcTagMetadata(idHex = idHex, techNames = techNames, fields = fields) to emptyList()
    }

    private fun readTech(tag: Tag, techName: String, context: TechReadContext) {
        context.checkpoint()
        try {
            val tech = tag.technology(techName)
            if (tech == null) {
                context.markUnsupported(unsupportedIssue(techName))
                return
            }
            context.addMetadata(tech.discoveryFields())
            val reader = TechReaders.forTech(techName)
            if (reader == null) {
                context.markUnsupported(unsupportedIssue(techName))
                return
            }
            activeTechnology = tech
            val deadlineClose = closeExecutor.schedule({
                context.expireDeadline()
                runCatching { tech.close() }
            }, context.remainingTimeoutMillis(), TimeUnit.MILLISECONDS)
            try {
                tech.use {
                    context.checkpoint()
                    it.connect()
                    if (!it.isConnected) {
                        context.markFailed(
                            ReadIssue(
                                kind = ReadIssueKind.IO_ERROR,
                                message = "Technology connection did not become active.",
                                operation = techName,
                            )
                        )
                        return
                    }
                    reader.read(it, context)
                }
            } finally {
                deadlineClose.cancel(false)
                if (activeTechnology === tech) activeTechnology = null
            }
        } catch (exception: ReadBudgetExceededException) {
            throw exception
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: ClassNotFoundException) {
            context.markUnsupported(unsupportedIssue(techName))
        } catch (exception: Exception) {
            val cause = exception.rootCause()
            Log.w(LOG_TAG, "Failed to read $techName.", cause)
            context.markFailed(issueFor(cause, techName))
        }
    }

    private fun Tag.technology(techName: String): TagTechnology? = when (techName) {
        NfcA::class.java.name -> NfcA.get(this)
        NfcB::class.java.name -> NfcB.get(this)
        NfcF::class.java.name -> NfcF.get(this)
        NfcV::class.java.name -> NfcV.get(this)
        IsoDep::class.java.name -> IsoDep.get(this)
        Ndef::class.java.name -> Ndef.get(this)
        NdefFormatable::class.java.name -> NdefFormatable.get(this)
        MifareClassic::class.java.name -> MifareClassic.get(this)
        MifareUltralight::class.java.name -> MifareUltralight.get(this)
        NfcBarcode::class.java.name -> NfcBarcode.get(this)
        else -> null
    }

    private fun TagTechnology.discoveryFields(): List<NfcField> = when (this) {
        is NfcA -> listOf(
            NfcField("NfcA.atqa", atqa.toHexString()),
            NfcField("NfcA.sak", "0x%04X".format(sak.toInt() and 0xFFFF)),
            NfcField("NfcA.maxTransceiveLength", maxTransceiveLength.toString()),
        )
        is NfcB -> listOf(
            NfcField("NfcB.applicationData", applicationData.toHexString()),
            NfcField("NfcB.protocolInfo", protocolInfo.toHexString()),
            NfcField("NfcB.maxTransceiveLength", maxTransceiveLength.toString()),
        )
        is NfcF -> listOf(
            NfcField("NfcF.manufacturer", manufacturer.toHexString()),
            NfcField("NfcF.systemCode", systemCode.toHexString()),
            NfcField("NfcF.maxTransceiveLength", maxTransceiveLength.toString()),
        )
        is NfcV -> listOf(
            NfcField("NfcV.dsfId", "0x%02X".format(dsfId.toInt() and 0xFF)),
            NfcField("NfcV.responseFlags", "0x%02X".format(responseFlags.toInt() and 0xFF)),
            NfcField("NfcV.maxTransceiveLength", maxTransceiveLength.toString()),
        )
        is IsoDep -> listOfNotNull(
            historicalBytes?.let { NfcField("IsoDep.historicalBytes", it.toHexString()) },
            hiLayerResponse?.let { NfcField("IsoDep.hiLayerResponse", it.toHexString()) },
            NfcField("IsoDep.maxTransceiveLength", maxTransceiveLength.toString()),
        )
        is MifareClassic -> listOf(
            NfcField("MifareClassic.type", type.toString()),
            NfcField("MifareClassic.sizeBytes", size.toString()),
            NfcField("MifareClassic.sectorCount", sectorCount.toString()),
            NfcField("MifareClassic.blockCount", blockCount.toString()),
            NfcField("MifareClassic.maxTransceiveLength", maxTransceiveLength.toString()),
        )
        is MifareUltralight -> listOf(
            NfcField("MifareUltralight.type", type.toString()),
            NfcField("MifareUltralight.maxTransceiveLength", maxTransceiveLength.toString()),
        )
        is Ndef -> listOf(
            NfcField("NDEF.type", type),
            NfcField("NDEF.maxMessageSizeBytes", maxSize.toString()),
            NfcField("NDEF.writable", isWritable.toString()),
            NfcField("NDEF.canMakeReadOnly", canMakeReadOnly().toString()),
        )
        is NdefFormatable -> listOf(NfcField("NdefFormatable.connection", "not opened"))
        else -> emptyList()
    }

    private fun stopActiveRead() {
        readJob?.cancel()
        val technology = activeTechnology
        activeTechnology = null
        if (technology != null) {
            runCatching {
                closeExecutor.execute { runCatching { technology.close() } }
            }
        }
    }

    override fun onCleared() {
        readGenerations.advance()
        stopActiveRead()
        closeExecutor.shutdown()
        super.onCleared()
    }

    private fun unsupportedIssue(techName: String) = ReadIssue(
        kind = ReadIssueKind.UNSUPPORTED_TECHNOLOGY,
        message = "No Android technology reader is available for this tag technology.",
        operation = techName,
    )

    private fun issueFor(exception: Throwable, operation: String) = ReadIssue(
        kind = when (exception) {
            is TagLostException -> ReadIssueKind.TAG_LOST
            is IOException -> ReadIssueKind.IO_ERROR
            else -> ReadIssueKind.OTHER
        },
        message = when (exception) {
            is TagLostException -> "Tag left the NFC field during reading."
            is IOException -> "I/O failed while reading the tag."
            else -> "Reader failed: ${exception::class.simpleName ?: "Exception"}."
        },
        operation = operation,
    )

    private fun Throwable.rootCause(): Throwable {
        var cause = this
        while (cause.cause != null && cause.cause !== cause) cause = cause.cause!!
        return cause
    }

    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"
        private const val LOG_TAG = "NfcReadViewModel"
    }
}

internal class ReadGenerationGate {
    private var generation = 0L
    private var pendingDump: NfcDump? = null

    @Synchronized
    fun advance(): Long = ++generation

    @Synchronized
    fun isCurrent(candidate: Long): Boolean = candidate == generation

    @Synchronized
    fun publish(candidate: Long, dump: NfcDump): Boolean {
        if (candidate == generation) return true
        pendingDump = dump
        return false
    }

    @Synchronized
    fun takePendingDump(): NfcDump? = pendingDump.also { pendingDump = null }
}
