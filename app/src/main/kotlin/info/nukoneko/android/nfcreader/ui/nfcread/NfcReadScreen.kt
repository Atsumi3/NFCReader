package info.nukoneko.android.nfcreader.ui.nfcread

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import info.nukoneko.android.nfcreader.R
import info.nukoneko.android.nfcreader.model.entity.NfcDump
import info.nukoneko.android.nfcreader.model.entity.NfcEntity
import info.nukoneko.android.nfcreader.model.entity.NfcField
import info.nukoneko.android.nfcreader.model.entity.ReadIssue
import info.nukoneko.android.nfcreader.model.entity.ReadStatus
import info.nukoneko.android.nfcreader.model.entity.UnreadRange
import info.nukoneko.android.nfcreader.model.reader.NfcDumpExportSnapshot
import info.nukoneko.android.nfcreader.ui.theme.NfcReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun NfcReadScreen(
    state: ReadStatus,
    readTime: String,
    modifier: Modifier = Modifier,
    onStopReading: () -> Unit = {},
    onPrepareDumpFileExport: suspend (String) -> Boolean = { false },
    onCompleteDumpFileExport: (Uri?) -> Unit = {},
    dumpFileExportResult: DumpFileExportResult? = null,
    onAcknowledgeDumpFileExportResult: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var infoPage by rememberSaveable { mutableStateOf(0) }
    var infoMenuExpanded by remember { mutableStateOf(false) }
    BackHandler(enabled = infoPage != 0) { infoPage = 0 }

    fun openNfcSettings() {
        val opened = runCatching {
            context.startActivity(createNfcSettingsIntent())
        }.isSuccess
        if (!opened) Toast.makeText(context, R.string.settings_open_failed, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(dumpFileExportResult?.id) {
        val result = dumpFileExportResult ?: return@LaunchedEffect
        Toast.makeText(
            context,
            if (result.saved) R.string.dump_saved else R.string.dump_save_failed,
            Toast.LENGTH_SHORT,
        ).show()
        onAcknowledgeDumpFileExportResult(result.id)
    }

    fun writeToSaf(uri: Uri?) {
        onCompleteDumpFileExport(uri)
    }

    val textExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
        writeToSaf(it)
    }
    val jsonExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        writeToSaf(it)
    }

    fun save(content: String, fileName: String, launcher: (String) -> Unit) {
        scope.launch {
            if (onPrepareDumpFileExport(content)) {
                launcher(fileName)
            } else {
                Toast.makeText(context, R.string.dump_save_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val saveText: (String) -> Unit = { save(it, "nfc-dump.txt", textExporter::launch) }
    val saveJson: (String) -> Unit = { save(it, "nfc-dump.json", jsonExporter::launch) }
    val isReading = state.isReadingInProgress()
    val displayDump = state.dumpForDisplay()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (infoPage == 1) R.string.help_title else if (infoPage == 2) R.string.privacy_title else R.string.app_name)) },
                navigationIcon = {
                    if (infoPage != 0) {
                        val backDescription = stringResource(R.string.back)
                        IconButton(onClick = { infoPage = 0 }, modifier = Modifier.semantics { contentDescription = backDescription }) {
                            Text("‹", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                },
                actions = {
                    if (infoPage == 0) {
                        val menuDescription = stringResource(R.string.info_menu)
                        Box {
                            IconButton(onClick = { infoMenuExpanded = true }, modifier = Modifier.semantics { contentDescription = menuDescription }) {
                                Text("⋮", style = MaterialTheme.typography.titleLarge)
                            }
                            DropdownMenu(expanded = infoMenuExpanded, onDismissRequest = { infoMenuExpanded = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.help_title)) }, onClick = { infoMenuExpanded = false; infoPage = 1 })
                                DropdownMenuItem(text = { Text(stringResource(R.string.privacy_title)) }, onClick = { infoMenuExpanded = false; infoPage = 2 })
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (isReading) {
                BottomAppBar {
                    TextButton(
                        onClick = onStopReading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.stop_reading))
                    }
                }
            }
        },
    ) { innerPadding ->
        if (infoPage != 0) {
            NfcInformationScreen(infoPage == 2, Modifier.padding(innerPadding))
        } else Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (readTime.isNotEmpty()) {
                Text(
                    text = readTime,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (displayDump != null) {
                    key(displayDump.capturedAtEpochMillis) {
                        NfcDumpContent(
                            dump = displayDump,
                            statusMessage = when (state) {
                                is ReadStatus.ReadingWithPrevious -> stringResource(R.string.reading_new_tag)
                                is ReadStatus.Failure.NfcDisabledWithDump -> stringResource(R.string.nfc_disabled)
                                is ReadStatus.Failure.IntentUnsupportedWithDump -> stringResource(R.string.intent_is_not_supported)
                                else -> null
                            },
                            saveText = saveText,
                            saveJson = saveJson,
                            statusDescription = if (state is ReadStatus.Failure.NfcDisabledWithDump) {
                                stringResource(R.string.nfc_disabled_help)
                            } else null,
                            statusActionLabel = if (state is ReadStatus.Failure.NfcDisabledWithDump) {
                                stringResource(R.string.open_nfc_settings)
                            } else null,
                            onStatusAction = if (state is ReadStatus.Failure.NfcDisabledWithDump) {
                                ::openNfcSettings
                            } else null,
                        )
                    }
                } else {
                    when (state) {
                        ReadStatus.Idle -> ReaderStateCard(
                            status = stringResource(R.string.state_waiting),
                            title = stringResource(R.string.please_hold_up_device),
                            description = stringResource(R.string.idle_read_only_description),
                        )
                        ReadStatus.Reading -> ReadingMessage()
                        is ReadStatus.Failure.NfcDisabled -> ReaderStateCard(
                            status = stringResource(R.string.state_nfc_disabled),
                            title = state.toMessage(),
                            description = stringResource(R.string.nfc_disabled_help),
                            actionLabel = stringResource(R.string.open_nfc_settings),
                            onAction = ::openNfcSettings,
                        )
                        is ReadStatus.Failure -> ReaderStateCard(
                            status = stringResource(R.string.state_attention),
                            title = state.toMessage(),
                            description = stringResource(R.string.read_error_description),
                        )
                        else -> Unit
                    }
                }
            }
        }
    }
}

internal fun ReadStatus.isReadingInProgress(): Boolean =
    this == ReadStatus.Reading || this is ReadStatus.ReadingWithPrevious

internal fun ReadStatus.dumpForDisplay(): NfcDump? = when (this) {
    is ReadStatus.Success -> dump
    is ReadStatus.ReadingWithPrevious -> dump
    is ReadStatus.Failure -> when (this) {
        is ReadStatus.Failure.NfcDisabledWithDump -> dump
        is ReadStatus.Failure.IntentUnsupportedWithDump -> dump
        ReadStatus.Failure.NfcDisabled,
        ReadStatus.Failure.IntentUnsupported,
        is ReadStatus.Failure.Error -> null
    }
    ReadStatus.Idle,
    ReadStatus.Reading -> null
}

@Composable
private fun ReadingMessage() {
    ReaderStateCard(
        status = stringResource(R.string.state_reading),
        title = stringResource(R.string.reading_title),
        description = stringResource(R.string.reading_description),
        content = {
            CircularProgressIndicator()
        },
    )
}

@Composable
private fun ReaderStateCard(
    status: String,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
            Text(status, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
            }
            content()
        }
    }
}

internal fun createNfcSettingsIntent(): Intent = Intent(Settings.ACTION_NFC_SETTINGS)

@Composable
private fun NfcDumpContent(
    dump: NfcDump,
    statusMessage: String? = null,
    saveText: (String) -> Unit,
    saveJson: (String) -> Unit,
    statusDescription: String? = null,
    statusActionLabel: String? = null,
    onStatusAction: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val shareChooserTitle = stringResource(R.string.share_dump_title)
    val scope = rememberCoroutineScope()
    val clipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val formattedSnapshot by produceState<NfcDumpExportSnapshot?>(initialValue = null, key1 = dump) {
        value = withContext(Dispatchers.Default) { NfcDumpExportSnapshot.create(dump) }
    }
    val export = formattedSnapshot?.takeIf { it.dump === dump }
    val expandedTechnologies = remember(dump) { mutableStateMapOf<String, Boolean>() }
    val expandedRawChunks = remember(dump) { mutableStateMapOf<String, Boolean>() }
    var actionsExpanded by remember(dump) { mutableStateOf(false) }

    fun copy(label: String, value: String) {
        if (!isClipboardCopyAllowed(value)) {
            Toast.makeText(context, R.string.dump_copy_too_large, Toast.LENGTH_LONG).show()
            return
        }
        val copied = clipboard?.let { manager ->
            runCatching {
                manager.setPrimaryClip(createSensitivePlainTextClip(label, value))
                true
            }.getOrDefault(false)
        } ?: false
        Toast.makeText(
            context,
            if (copied) R.string.dump_copied else R.string.dump_copy_failed,
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun share(content: String, extension: String, mimeType: String) {
        scope.launch {
            val shared = runCatching {
                val shareIntent = withContext(Dispatchers.IO) {
                    val file = NfcDumpFileExporter.writeCacheFile(context, extension, content)
                    NfcDumpFileExporter.createShareIntent(context, file, mimeType)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, shareChooserTitle)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                )
            }.isSuccess
            if (!shared) Toast.makeText(context, R.string.dump_share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        statusMessage?.let { message ->
            item(key = "status-banner") { ReaderStatusBanner(message, statusDescription, statusActionLabel, onStatusAction) }
        }
        item(key = "sensitive-warning") {
            Text(stringResource(R.string.dump_sensitive_warning), style = MaterialTheme.typography.bodyMedium)
        }
        item(key = "dump-actions") {
            Box {
                Button(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = export != null,
                    onClick = { actionsExpanded = true },
                ) {
                    Text(stringResource(R.string.dump_actions))
                }
                DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.copy_dump)) },
                        onClick = { actionsExpanded = false; export?.let { copy("NFC dump", it.text) } },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.copy_json_dump)) },
                        onClick = { actionsExpanded = false; export?.let { copy("NFC dump JSON", it.json) } },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_dump)) },
                        onClick = {
                            actionsExpanded = false
                            export?.let { share(it.text, "txt", "text/plain") }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_json_dump)) },
                        onClick = {
                            actionsExpanded = false
                            export?.let { share(it.json, "json", "application/json") }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.save_text_dump)) },
                        onClick = {
                            actionsExpanded = false
                            export?.let { saveText(it.text) }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.save_json_dump)) },
                        onClick = {
                            actionsExpanded = false
                            export?.let { saveJson(it.json) }
                        },
                    )
                }
            }
        }
        item(key = "dump-summary") {
            DumpSummaryCard(
                dump = dump,
                capturedAt = remember(dump.capturedAtEpochMillis) {
                    DateFormat.getDateTimeInstance().format(Date(dump.capturedAtEpochMillis))
                },
                onCopy = ::copy,
            )
        }
        if (dump.tag.fields.isNotEmpty()) {
            item(key = "tag-metadata-title") { SectionTitle(stringResource(R.string.metadata_section)) }
            itemsIndexed(dump.tag.fields, key = { index, _ -> "tag-field-$index" }) { _, field ->
                ValueRow(field, ::copy)
            }
        }
        dump.technologies.forEach { entity ->
            val expanded = expandedTechnologies[entity.techName] == true
            item(key = "tech-header:${entity.techName}") {
                TechnologyHeader(
                    entity = entity,
                    expanded = expanded,
                    onToggle = { expandedTechnologies[entity.techName] = !expanded },
                    onCopy = {
                        export?.technologyText?.get(entity.techName)?.let {
                            copy(entity.techName, it)
                        }
                    },
                )
            }
            if (expanded) addTechnologyDetails(
                entity = entity,
                expandedRawChunks = expandedRawChunks,
                copyValue = ::copy,
            )
        }
        if (dump.issues.isNotEmpty()) {
            item(key = "dump-issues-title") { SectionTitle(stringResource(R.string.dump_issues)) }
            itemsIndexed(dump.issues, key = { index, _ -> "dump-issue-$index" }) { _, issue ->
                IssueRow(issue)
            }
        }
    }
}

@Composable
private fun ReaderStatusBanner(
    title: String,
    description: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun DumpSummaryCard(
    dump: NfcDump,
    capturedAt: String,
    onCopy: (String, String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.result_summary_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(readOutcomeLabelResource(dump.outcome)),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.technology_count, dump.technologies.size), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.dump_summary, dump.rawBytesCaptured, dump.rawByteLimit), style = MaterialTheme.typography.bodyMedium)
            dump.tag.idHex?.let { ValueRow(NfcField(stringResource(R.string.tag_id), it), onCopy) }
            Text(stringResource(R.string.dump_captured_at, capturedAt), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun LazyListScope.addTechnologyDetails(
    entity: NfcEntity,
    expandedRawChunks: MutableMap<String, Boolean>,
    copyValue: (String, String) -> Unit,
) {
    val result = entity.readResult
    if (result.decodedFields.isNotEmpty()) {
        item(key = "decoded-title:${entity.techName}") { SectionTitle(stringResource(R.string.decoded_section)) }
        itemsIndexed(result.decodedFields, key = { index, _ -> "decoded:${entity.techName}:$index" }) { _, field ->
            ValueRow(field, copyValue)
        }
    }
    if (result.metadata.isNotEmpty()) {
        item(key = "metadata-title:${entity.techName}") { SectionTitle(stringResource(R.string.metadata_section)) }
        itemsIndexed(result.metadata, key = { index, _ -> "metadata:${entity.techName}:$index" }) { _, field ->
            ValueRow(field, copyValue)
        }
    }
    item(key = "coverage:${entity.techName}") {
        SectionTitle(stringResource(R.string.coverage_section))
        result.coverageDetails.dropLast(1).forEach { detail ->
            Text(
                stringResource(R.string.coverage_detail, detail.scope, detail.unit, stringResource(if (detail.isComplete) R.string.complete else R.string.partial)),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            detail.expectedRange?.let {
                Text(
                    stringResource(R.string.expected_range, it.startInclusive, it.endExclusive, detail.unit),
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
        Text(
            stringResource(R.string.coverage_detail, result.coverage.scope, result.coverage.unit, stringResource(if (result.coverage.isComplete) R.string.complete else R.string.partial)),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        result.coverage.expectedRange?.let {
            Text(
                stringResource(R.string.expected_range, it.startInclusive, it.endExclusive, result.coverage.unit),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
    if (result.rawChunks.isNotEmpty()) {
        item(key = "raw-title:${entity.techName}") { SectionTitle(stringResource(R.string.raw_section)) }
        itemsIndexed(result.rawChunks, key = { index, _ -> "raw:${entity.techName}:$index" }) { index, chunk ->
            val rawKey = "${entity.techName}:$index"
            RawChunkRow(
                label = chunk.label,
                location = chunk.location,
                value = chunk.hex,
                capturedBytes = chunk.capturedByteCount,
                sourceBytes = chunk.sourceByteCount,
                expanded = expandedRawChunks[rawKey] == true,
                onToggle = { expandedRawChunks[rawKey] = expandedRawChunks[rawKey] != true },
                onCopy = { copyValue(chunk.label, chunk.hex) },
            )
        }
    }
    if (result.issues.isNotEmpty()) {
        item(key = "issues-title:${entity.techName}") { SectionTitle(stringResource(R.string.issues_section)) }
        itemsIndexed(result.issues, key = { index, _ -> "issue:${entity.techName}:$index" }) { _, issue ->
            IssueRow(issue)
        }
    }
    if (result.unreadRanges.isNotEmpty()) {
        item(key = "unread-title:${entity.techName}") { SectionTitle(stringResource(R.string.unread_section)) }
        itemsIndexed(result.unreadRanges, key = { index, _ -> "unread:${entity.techName}:$index" }) { _, range ->
            UnreadRangeRow(range)
        }
    }
    item(key = "tech-bottom-space:${entity.techName}") { Spacer(Modifier.height(8.dp)) }
}

@Composable
private fun TechnologyHeader(
    entity: NfcEntity,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Text(entity.shortName, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.technology_outcome, stringResource(readOutcomeLabelResource(entity.readResult.outcome))), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(R.string.coverage_summary, entity.readResult.coverage.scope, stringResource(if (entity.readResult.coverage.isComplete) R.string.complete else R.string.partial)),
                style = MaterialTheme.typography.bodySmall,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(if (expanded) R.string.collapse_details else R.string.expand_details))
                }
                TextButton(onClick = onCopy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.copy_technology))
                }
            }
            Text(entity.techName, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, modifier = Modifier.padding(start = 8.dp, top = 8.dp), style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun ValueRow(field: NfcField, onCopy: (String, String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(field.label, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.Top) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(
                    field.value ?: "-",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = { onCopy(field.label, field.value ?: "") }) {
                Text(stringResource(R.string.copy_value))
            }
        }
    }
}

@Composable
private fun RawChunkRow(
    label: String,
    location: String?,
    value: String,
    capturedBytes: Int,
    sourceBytes: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            "$label${location?.let { " [$it]" }.orEmpty()} — $capturedBytes/$sourceBytes bytes",
            style = MaterialTheme.typography.labelMedium,
        )
        val preview = value.take(RAW_PREVIEW_HEX_CHARS)
        SelectionContainer {
            Text(
                if (expanded) value else preview + if (value.length > preview.length) "…" else "",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = if (expanded) {
                    Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())
                } else {
                    Modifier.fillMaxWidth()
                },
            )
        }
        val toggleLabel = stringResource(if (expanded) R.string.hide_raw_chunk else R.string.show_raw_chunk)
        val copyLabel = stringResource(R.string.copy_raw_chunk)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = toggleLabel },
            ) {
                Text(toggleLabel, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            }
            TextButton(
                onClick = onCopy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = copyLabel },
            ) {
                Text(copyLabel, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
            }
        }
    }
}

@Composable
private fun IssueRow(issue: ReadIssue) {
    Text(
        "${issue.kind}${issue.operation?.let { " ($it)" }.orEmpty()}: ${issue.message}",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun UnreadRangeRow(range: UnreadRange) {
    val bounds = if (range.startInclusive != null && range.endExclusive != null) {
        "${range.startInclusive}..${range.endExclusive} exclusive ${range.unit}"
    } else {
        stringResource(R.string.unread_range_unknown, range.unit)
    }
    Text(
        stringResource(R.string.unread_range_detail, range.scope, bounds, range.reason),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ReadStatus.Failure.toMessage(): String = when (this) {
    ReadStatus.Failure.NfcDisabled -> stringResource(R.string.nfc_disabled)
    is ReadStatus.Failure.NfcDisabledWithDump -> stringResource(R.string.nfc_disabled)
    ReadStatus.Failure.IntentUnsupported -> stringResource(R.string.intent_is_not_supported)
    is ReadStatus.Failure.IntentUnsupportedWithDump -> stringResource(R.string.intent_is_not_supported)
    is ReadStatus.Failure.Error -> stringResource(R.string.read_failed)
}

@Composable
private fun NfcInformationScreen(isPrivacy: Boolean, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val contactUrl = stringResource(R.string.privacy_contact_url)
    val contactFailure = stringResource(R.string.privacy_link_failed)
    val headings = if (isPrivacy) {
        listOf(R.string.privacy_overview_title, R.string.privacy_network_title, R.string.privacy_retention_title, R.string.privacy_sharing_title, R.string.privacy_deletion_title, R.string.privacy_contact_title)
    } else {
        listOf(R.string.help_read_title, R.string.help_result_title, R.string.help_coverage_title, R.string.help_export_title, R.string.help_safety_title)
    }
    val paragraphs = if (isPrivacy) {
        listOf(R.string.privacy_overview, R.string.privacy_network, R.string.privacy_retention, R.string.privacy_sharing, R.string.privacy_deletion, R.string.privacy_contact)
    } else {
        listOf(R.string.help_read, R.string.help_result, R.string.help_coverage, R.string.help_export, R.string.help_safety)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(headings) { index, heading ->
            Text(stringResource(heading), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(paragraphs[index]), style = MaterialTheme.typography.bodyLarge)
        }
        if (isPrivacy) {
            item {
                TextButton(
                    onClick = {
                        if (runCatching { uriHandler.openUri(contactUrl) }.isFailure) {
                            Toast.makeText(context, contactFailure, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.privacy_contact_link)) }
            }
        }
    }
}

private const val RAW_PREVIEW_HEX_CHARS = 256

@Preview(showBackground = true)
@Composable
private fun NfcReadScreenPreview() {
    NfcReaderTheme {
        NfcReadScreen(
            state = ReadStatus.Success(
                listOf(
                    NfcEntity(
                        techName = "android.nfc.tech.NfcA",
                        fields = listOf(
                            NfcField("Tag.Id", "04 1A 2B 3C 5D 6E 70"),
                            NfcField("Sak", "0008"),
                            NfcField("Atqa", "44 00"),
                        ),
                    ),
                ),
            ),
            readTime = "2026-05-20 12:34:56.789",
        )
    }
}
