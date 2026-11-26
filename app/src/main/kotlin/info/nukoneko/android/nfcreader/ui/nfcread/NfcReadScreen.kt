package info.nukoneko.android.nfcreader.ui.nfcread

import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
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
                when (state) {
                    ReadStatus.Idle -> CenteredMessage(stringResource(R.string.please_hold_up_device))
                    ReadStatus.Reading -> ReadingMessage(onStopReading)
                    is ReadStatus.ReadingWithPrevious ->
                        NfcDumpContent(
                            state.dump,
                            stringResource(R.string.reading_new_tag),
                            onStopReading,
                            saveText,
                            saveJson,
                        )

                    is ReadStatus.Success -> NfcDumpContent(state.dump, saveText = saveText, saveJson = saveJson)
                    is ReadStatus.Failure.NfcDisabledWithDump ->
                        NfcDumpContent(
                            state.dump,
                            stringResource(R.string.nfc_disabled),
                            saveText = saveText,
                            saveJson = saveJson,
                        )

                    is ReadStatus.Failure.IntentUnsupportedWithDump ->
                        NfcDumpContent(
                            state.dump,
                            stringResource(R.string.intent_is_not_supported),
                            saveText = saveText,
                            saveJson = saveJson,
                        )

                    is ReadStatus.Failure -> CenteredMessage(state.toMessage())
                }
            }
        }
    }
}

@Composable
private fun ReadingMessage(onStopReading: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.please_hold_up_device))
        TextButton(onClick = onStopReading) {
            Text(stringResource(R.string.stop_reading))
        }
    }
}

@Composable
private fun NfcDumpContent(
    dump: NfcDump,
    statusMessage: String? = null,
    onStopReading: (() -> Unit)? = null,
    saveText: (String) -> Unit,
    saveJson: (String) -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
    ) {
        statusMessage?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        onStopReading?.let {
            TextButton(onClick = it) { Text(stringResource(R.string.stop_reading)) }
        }
        Text(stringResource(R.string.dump_sensitive_warning), style = MaterialTheme.typography.bodySmall)
        Box {
            Button(
                modifier = Modifier.fillMaxWidth(),
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

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "dump-summary") {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    Text("Outcome: ${dump.outcome}; raw ${dump.rawBytesCaptured}/${dump.rawByteLimit} bytes")
                    Text(
                        stringResource(
                            R.string.dump_captured_at,
                            remember(dump.capturedAtEpochMillis) {
                                DateFormat.getDateTimeInstance().format(Date(dump.capturedAtEpochMillis))
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            dump.tag.idHex?.let { idHex ->
                item(key = "tag-id") {
                    ValueRow(NfcField("Tag ID", idHex), ::copy)
                }
            }
            if (dump.tag.fields.isNotEmpty()) {
                item(key = "tag-metadata-title") { SectionTitle(stringResource(R.string.metadata_section)) }
                itemsIndexed(dump.tag.fields, key = { index, _ -> "tag-field-$index" }) { _, field ->
                    ValueRow(field, ::copy)
                }
            }
            if (dump.issues.isNotEmpty()) {
                item(key = "dump-issues-title") { SectionTitle(stringResource(R.string.dump_issues)) }
                itemsIndexed(dump.issues, key = { index, _ -> "dump-issue-$index" }) { _, issue ->
                    IssueRow(issue)
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
        }
    }
}

private fun LazyListScope.addTechnologyDetails(
    entity: NfcEntity,
    expandedRawChunks: MutableMap<String, Boolean>,
    copyValue: (String, String) -> Unit,
) {
    val result = entity.readResult
    if (result.metadata.isNotEmpty()) {
        item(key = "metadata-title:${entity.techName}") { SectionTitle(stringResource(R.string.metadata_section)) }
        itemsIndexed(result.metadata, key = { index, _ -> "metadata:${entity.techName}:$index" }) { _, field ->
            ValueRow(field, copyValue)
        }
    }
    if (result.decodedFields.isNotEmpty()) {
        item(key = "decoded-title:${entity.techName}") { SectionTitle(stringResource(R.string.decoded_section)) }
        itemsIndexed(result.decodedFields, key = { index, _ -> "decoded:${entity.techName}:$index" }) { _, field ->
            ValueRow(field, copyValue)
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
    item(key = "coverage:${entity.techName}") {
        SectionTitle(stringResource(R.string.coverage_section))
        Text(
            "${result.coverage.scope} (${result.coverage.unit}) — complete=${result.coverage.isComplete}",
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        result.coverage.expectedRange?.let {
            Text(
                "Expected: ${it.startInclusive}..${it.endExclusive} exclusive (${result.coverage.unit})",
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        result.coverageDetails.drop(1).forEach { detail ->
            Text(
                "${detail.scope} (${detail.unit}) — complete=${detail.isComplete}",
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            detail.expectedRange?.let {
                Text(
                    "Expected: ${it.startInclusive}..${it.endExclusive} exclusive (${detail.unit})",
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
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
            Text("Outcome: ${entity.readResult.outcome}", style = MaterialTheme.typography.bodySmall)
            Text(
                "${entity.readResult.coverage.scope} — complete=${entity.readResult.coverage.isComplete}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onToggle) {
                    Text(stringResource(if (expanded) R.string.collapse_details else R.string.expand_details))
                }
                TextButton(onClick = onCopy) { Text(stringResource(R.string.copy_technology)) }
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onToggle) {
                Text(stringResource(if (expanded) R.string.hide_raw_chunk else R.string.show_raw_chunk))
            }
            TextButton(onClick = onCopy) { Text(stringResource(R.string.copy_raw_chunk)) }
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
        "${range.scope}: $bounds — ${range.reason}",
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
    is ReadStatus.Failure.Error -> cause.localizedMessage ?: stringResource(R.string.intent_is_not_supported)
}

@Composable
private fun CenteredMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(16.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.headlineSmall,
    )
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
