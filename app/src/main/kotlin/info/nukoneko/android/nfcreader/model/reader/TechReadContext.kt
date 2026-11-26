package info.nukoneko.android.nfcreader.model.reader

import info.nukoneko.android.nfcreader.model.entity.NfcField
import info.nukoneko.android.nfcreader.model.entity.DEFAULT_MAX_FIELD_VALUE_CHARS_PER_DUMP
import info.nukoneko.android.nfcreader.model.entity.DEFAULT_MAX_RAW_BYTES_PER_DUMP
import info.nukoneko.android.nfcreader.model.entity.ReadCoverage
import info.nukoneko.android.nfcreader.model.entity.ReadIssue
import info.nukoneko.android.nfcreader.model.entity.ReadIssueKind
import info.nukoneko.android.nfcreader.model.entity.ReadOutcome
import info.nukoneko.android.nfcreader.model.entity.RawChunk
import info.nukoneko.android.nfcreader.model.entity.TechReadResult
import info.nukoneko.android.nfcreader.model.entity.UnreadRange

class RawCaptureBudget(val maxBytes: Int = DEFAULT_MAX_RAW_BYTES_PER_DUMP) {
    private var capturedBytes = 0

    val remainingBytes: Int
        @Synchronized get() = maxBytes - capturedBytes

    val totalCapturedBytes: Int
        @Synchronized get() = capturedBytes

    init {
        require(maxBytes >= 0)
    }

    @Synchronized
    fun capture(label: String, bytes: ByteArray, location: String? = null): RawChunk {
        val capturedCount = minOf(bytes.size, maxBytes - capturedBytes)
        val digits = "0123456789ABCDEF"
        val hexChars = CharArray(capturedCount * 2)
        for (index in 0 until capturedCount) {
            val value = bytes[index].toInt() and 0xFF
            hexChars[index * 2] = digits[value ushr 4]
            hexChars[index * 2 + 1] = digits[value and 0x0F]
        }
        capturedBytes += capturedCount
        return RawChunk(
            label = label,
            location = location,
            hex = String(hexChars),
            sourceByteCount = bytes.size,
            capturedByteCount = capturedCount,
        )
    }
}

class FieldValueBudget(val maxChars: Int = DEFAULT_MAX_FIELD_VALUE_CHARS_PER_DUMP) {
    private var capturedChars = 0

    val remainingChars: Int
        @Synchronized get() = maxChars - capturedChars

    init {
        require(maxChars >= 0)
    }

    @Synchronized
    fun capture(fields: List<NfcField>): FieldCapture {
        var truncated = false
        val bounded = fields.map { field ->
            val value = field.value ?: return@map field
            val remaining = maxChars - capturedChars
            if (value.length <= remaining) {
                capturedChars += value.length
                field
            } else {
                truncated = true
                val marker = "…[truncated]"
                val boundedValue = when {
                    remaining <= 0 -> null
                    remaining <= marker.length -> value.take(remaining)
                    else -> value.take(remaining - marker.length) + marker
                }
                capturedChars += boundedValue?.length ?: 0
                field.copy(value = boundedValue)
            }
        }
        return FieldCapture(bounded, truncated)
    }
}

data class FieldCapture(
    val fields: List<NfcField>,
    val truncated: Boolean,
)

class TechReadContext internal constructor(
    private val rawBudget: RawCaptureBudget,
    private val fieldBudget: FieldValueBudget,
    private val cancellationCheck: () -> Unit,
    limits: TechReadLimits = TechReadLimits(timeoutMillis = 30_000, maxTransceives = 4096),
    clockNanos: () -> Long = System::nanoTime,
) {
    private val readBudget = TechReadBudget(limits, clockNanos)
    private val metadata = mutableListOf<NfcField>()
    private val rawChunks = mutableListOf<RawChunk>()
    private val decodedFields = mutableListOf<NfcField>()
    private val issues = mutableListOf<ReadIssue>()
    private val unreadRanges = mutableListOf<UnreadRange>()
    private val coverageDetails = mutableListOf<ReadCoverage>()
    private var coverage = ReadCoverage.unknown("Reader did not report coverage")
    private var outcome = ReadOutcome.METADATA_ONLY

    fun checkpoint() {
        cancellationCheck()
        readBudget.failureReason()?.let(::exhaustBudget)
    }

    fun budgetedTransport(delegate: ReadOnlyTransport, operation: String): ReadOnlyTransport =
        ReadOnlyTransport { command ->
            reserveTransceive(operation)
            delegate.transceiveReadOnly(command)
        }

    fun reserveTransceive(operation: String) {
        cancellationCheck()
        readBudget.reserveTransceive()?.let { exhaustBudget("$operation: $it") }
    }

    fun expireDeadline() {
        readBudget.expire()
    }

    fun remainingTimeoutMillis(): Long = readBudget.remainingTimeoutMillis()

    fun addMetadata(fields: List<NfcField>) {
        val capture = fieldBudget.capture(fields)
        metadata += capture.fields
        if (capture.truncated) markFieldLimitReached()
    }

    fun addDecodedFields(fields: List<NfcField>) {
        val capture = fieldBudget.capture(fields)
        decodedFields += capture.fields
        if (capture.truncated) markFieldLimitReached()
        if (capture.fields.isNotEmpty() && outcome == ReadOutcome.METADATA_ONLY) {
            outcome = ReadOutcome.PARTIAL
        }
    }

    fun captureRaw(label: String, bytes: ByteArray, location: String? = null): RawChunk {
        val chunk = rawBudget.capture(label, bytes, location)
        rawChunks += chunk
        if (chunk.isTruncated) {
            if (issues.none { it.kind == ReadIssueKind.RAW_LIMIT_REACHED }) {
                issues += ReadIssue(
                    kind = ReadIssueKind.RAW_LIMIT_REACHED,
                    message = "Raw capture limit reached; only ${chunk.capturedByteCount} of ${chunk.sourceByteCount} bytes were retained.",
                    operation = label,
                )
            }
            unreadRanges += UnreadRange(
                scope = location ?: label,
                unit = "byte",
                startInclusive = chunk.capturedByteCount.toLong(),
                endExclusive = chunk.sourceByteCount.toLong(),
                reason = "Raw capture limit reached.",
            )
            outcome = ReadOutcome.PARTIAL
        }
        return chunk
    }

    fun setCoverage(value: ReadCoverage) {
        coverage = value
        if (coverageDetails.lastOrNull() != value) coverageDetails += value
    }

    fun addUnreadRange(range: UnreadRange) {
        unreadRanges += range
        if (outcome != ReadOutcome.UNSUPPORTED && outcome != ReadOutcome.FAILED) {
            outcome = ReadOutcome.PARTIAL
        }
    }

    fun addIssue(issue: ReadIssue) {
        issues += issue
    }

    fun markComplete() {
        if (coverage.isComplete && issues.isEmpty() && unreadRanges.isEmpty() &&
            rawChunks.none { it.isTruncated }
        ) {
            outcome = ReadOutcome.COMPLETE
        } else {
            outcome = ReadOutcome.PARTIAL
            if (!coverage.isComplete) {
                issues += ReadIssue(
                    kind = ReadIssueKind.COVERAGE_UNKNOWN,
                    message = "Read cannot be marked complete without a known, fully covered range.",
                )
            }
        }
    }

    fun markPartial() {
        outcome = ReadOutcome.PARTIAL
    }

    fun markMetadataOnly() {
        if (outcome == ReadOutcome.METADATA_ONLY) outcome = ReadOutcome.METADATA_ONLY
    }

    fun markUnsupported(issue: ReadIssue) {
        issues += issue
        outcome = ReadOutcome.UNSUPPORTED
    }

    fun markFailed(issue: ReadIssue) {
        issues += issue
        outcome = if (hasReadData()) ReadOutcome.PARTIAL else ReadOutcome.FAILED
    }

    fun markNotAttempted(issue: ReadIssue) {
        issues += issue
        outcome = ReadOutcome.NOT_ATTEMPTED
    }

    fun markInterrupted(issue: ReadIssue) {
        issues += issue
        outcome = if (hasReadData()) ReadOutcome.PARTIAL else ReadOutcome.NOT_ATTEMPTED
    }

    fun buildResult(): TechReadResult {
        val finalOutcome = if (outcome == ReadOutcome.COMPLETE &&
            (!coverage.isComplete || coverageDetails.any { !it.isComplete } ||
                issues.isNotEmpty() || unreadRanges.isNotEmpty() ||
                rawChunks.any { it.isTruncated })
        ) {
            ReadOutcome.PARTIAL
        } else {
            outcome
        }
        return TechReadResult(
            metadata = metadata.toList(),
            rawChunks = rawChunks.toList(),
            decodedFields = decodedFields.toList(),
            issues = issues.toList(),
            coverage = coverage,
            unreadRanges = unreadRanges.toList(),
            outcome = finalOutcome,
            coverageDetails = coverageDetails.toList().ifEmpty { listOf(coverage) },
        )
    }

    private fun markFieldLimitReached() {
        if (issues.none { it.kind == ReadIssueKind.FIELD_LIMIT_REACHED }) {
            issues += ReadIssue(
                kind = ReadIssueKind.FIELD_LIMIT_REACHED,
                message = "Displayed field values were truncated at the per-dump limit.",
            )
        }
        if (outcome != ReadOutcome.UNSUPPORTED && outcome != ReadOutcome.FAILED) {
            outcome = ReadOutcome.PARTIAL
        }
    }

    private fun hasReadData(): Boolean =
        metadata.isNotEmpty() || decodedFields.isNotEmpty() || rawChunks.any { it.capturedByteCount > 0 }

    private fun exhaustBudget(message: String): Nothing {
        if (issues.none { it.kind == ReadIssueKind.BUDGET_EXHAUSTED }) {
            issues += ReadIssue(ReadIssueKind.BUDGET_EXHAUSTED, message)
            unreadRanges += UnreadRange(
                scope = "Technology read",
                unit = "transceive",
                startInclusive = readBudget.transceiveCount().toLong(),
                endExclusive = readBudget.limits.maxTransceives.toLong(),
                reason = message,
            )
        }
        outcome = ReadOutcome.PARTIAL
        throw ReadBudgetExceededException(message)
    }
}
