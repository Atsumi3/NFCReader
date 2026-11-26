package info.nukoneko.android.nfcreader.model.reader

import info.nukoneko.android.nfcreader.model.entity.NfcDump
import info.nukoneko.android.nfcreader.model.entity.NfcEntity
import info.nukoneko.android.nfcreader.model.entity.NfcField
import info.nukoneko.android.nfcreader.model.entity.NfcTagMetadata
import info.nukoneko.android.nfcreader.model.entity.DEFAULT_MAX_FIELD_VALUE_CHARS_PER_DUMP
import info.nukoneko.android.nfcreader.model.entity.DEFAULT_MAX_RAW_BYTES_PER_DUMP
import info.nukoneko.android.nfcreader.model.entity.ReadIssue
import info.nukoneko.android.nfcreader.model.entity.ReadIssueKind
import info.nukoneko.android.nfcreader.model.entity.ReadOutcome
import java.util.concurrent.CancellationException

class NfcDumpCollector(
    tagMetadata: NfcTagMetadata,
    initialIssues: List<ReadIssue> = emptyList(),
    rawByteLimit: Int = DEFAULT_MAX_RAW_BYTES_PER_DUMP,
    fieldValueCharLimit: Int = DEFAULT_MAX_FIELD_VALUE_CHARS_PER_DUMP,
) {
    private val capturedAtEpochMillis = System.currentTimeMillis()
    private val rawBudget = RawCaptureBudget(rawByteLimit)
    private val fieldBudget = FieldValueBudget(fieldValueCharLimit)
    private val technologies = mutableListOf<NfcEntity>()
    private val issues = initialIssues.toMutableList()
    private var stoppedBy: ReadIssue? = null
    private var readingStarted = false
    private var metadata: NfcTagMetadata

    init {
        val boundedFields = fieldBudget.capture(tagMetadata.fields)
        metadata = tagMetadata.copy(
            techNames = tagMetadata.techNames.distinct(),
            fields = boundedFields.fields,
        )
        if (boundedFields.truncated) addFieldLimitIssue()
    }

    fun updateTagMetadata(tagMetadata: NfcTagMetadata, newIssues: List<ReadIssue> = emptyList()) {
        check(!readingStarted)
        val boundedFields = fieldBudget.capture(tagMetadata.fields)
        metadata = tagMetadata.copy(
            techNames = tagMetadata.techNames.ifEmpty { metadata.techNames }.distinct(),
            fields = boundedFields.fields,
        )
        issues += newIssues
        if (boundedFields.truncated) addFieldLimitIssue()
    }

    fun addIssue(issue: ReadIssue) {
        issues += issue
    }

    fun readAll(
        cancellationCheck: () -> Unit = {},
        limitsForTechnology: (String) -> TechReadLimits = { TechReadLimits.forTechnology(it) },
        readTechnology: (String, TechReadContext) -> Unit,
    ) {
        check(!readingStarted)
        readingStarted = true
        for (techName in metadata.techNames) {
            val context = TechReadContext(rawBudget, fieldBudget, cancellationCheck, limitsForTechnology(techName))
            val stoppedIssue = stoppedBy
            if (stoppedIssue != null) {
                context.markNotAttempted(stoppedIssue.forNotAttempted(techName))
            } else {
                try {
                    cancellationCheck()
                    context.checkpoint()
                    readTechnology(techName, context)
                    cancellationCheck()
                    context.checkpoint()
                } catch (exception: CancellationException) {
                    val issue = ReadIssue(
                        kind = ReadIssueKind.INTERRUPTED,
                        message = "Read was interrupted before all requested technology data was collected.",
                        operation = techName,
                    )
                    context.markInterrupted(issue)
                    stoppedBy = issue
                    issues += issue
                } catch (exception: ReadBudgetExceededException) {
                    context.markPartial()
                } catch (exception: Exception) {
                    context.markFailed(
                        ReadIssue(
                            kind = ReadIssueKind.OTHER,
                            message = "Reader failed: ${exception::class.simpleName ?: "Exception"}.",
                            operation = techName,
                        )
                    )
                }
            }

            val result = context.buildResult()
            val readStop = result.issues.firstOrNull {
                it.kind == ReadIssueKind.TAG_LOST || it.kind == ReadIssueKind.INTERRUPTED
            }
            if (stoppedBy == null && readStop != null) {
                stoppedBy = readStop
                issues += readStop
            }
            technologies += NfcEntity(
                techName = techName,
                fields = metadata.fields + result.metadata + result.decodedFields + result.visibleIssues(),
                readResult = result,
            )
        }
    }

    fun markInterrupted(message: String = "Read was interrupted before results were published.") {
        val issue = ReadIssue(ReadIssueKind.INTERRUPTED, message)
        if (issues.none { it.kind == ReadIssueKind.INTERRUPTED }) issues += issue
        if (stoppedBy == null) stoppedBy = issue
    }

    fun snapshot(): NfcDump = NfcDump(
        tag = metadata,
        technologies = technologies.toList(),
        issues = issues.toList(),
        rawByteLimit = rawBudget.maxBytes,
        capturedAtEpochMillis = capturedAtEpochMillis,
    )

    private fun addFieldLimitIssue() {
        if (issues.none { it.kind == ReadIssueKind.FIELD_LIMIT_REACHED }) {
            issues += ReadIssue(
                kind = ReadIssueKind.FIELD_LIMIT_REACHED,
                message = "Displayed field values were truncated at the per-dump limit.",
            )
        }
    }

    private fun ReadIssue.forNotAttempted(techName: String) = copy(
        message = "Not attempted because reading stopped: $message",
        operation = techName,
    )

    private fun List<ReadIssue>.visibleIssues(): List<NfcField> =
        filterNot { it.kind == ReadIssueKind.COVERAGE_UNKNOWN }.map { issue ->
            val label = when (issue.kind) {
                ReadIssueKind.RAW_LIMIT_REACHED,
                ReadIssueKind.FIELD_LIMIT_REACHED -> "Dump.truncated"

                ReadIssueKind.DATA_REDACTED -> "Dump.redacted"

                else -> "Read.error"
            }
            NfcField(label, issue.message)
        }
}
