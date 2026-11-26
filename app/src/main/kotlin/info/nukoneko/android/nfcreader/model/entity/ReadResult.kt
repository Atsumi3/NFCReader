package info.nukoneko.android.nfcreader.model.entity

const val DEFAULT_MAX_RAW_BYTES_PER_DUMP = 256 * 1024
const val DEFAULT_MAX_FIELD_VALUE_CHARS_PER_DUMP = 256 * 1024

enum class ReadOutcome {
    COMPLETE,
    PARTIAL,
    METADATA_ONLY,
    UNSUPPORTED,
    FAILED,
    NOT_ATTEMPTED,
}

enum class ReadIssueKind {
    UNSUPPORTED_TECHNOLOGY,
    COVERAGE_UNKNOWN,
    AUTHENTICATION_REQUIRED,
    IO_ERROR,
    TAG_LOST,
    INTERRUPTED,
    RAW_LIMIT_REACHED,
    FIELD_LIMIT_REACHED,
    DATA_REDACTED,
    BUDGET_EXHAUSTED,
    MALFORMED_RESPONSE,
    OTHER,
}

data class ReadIssue(
    val kind: ReadIssueKind,
    val message: String,
    val operation: String? = null,
)

data class ReadRange(
    val startInclusive: Long,
    val endExclusive: Long,
) {
    init {
        require(startInclusive >= 0)
        require(endExclusive >= startInclusive)
    }
}

data class UnreadRange(
    val scope: String,
    val unit: String,
    val startInclusive: Long? = null,
    val endExclusive: Long? = null,
    val reason: String,
) {
    init {
        require((startInclusive == null) == (endExclusive == null))
        require(startInclusive == null || startInclusive >= 0)
        require(endExclusive == null || endExclusive >= startInclusive!!)
    }
}

data class ReadCoverage(
    val scope: String,
    val unit: String,
    val expectedRange: ReadRange? = null,
    val completedRanges: List<ReadRange> = emptyList(),
) {
    val isComplete: Boolean
        get() {
            val expected = expectedRange ?: return false
            if (completedRanges.any {
                    it.startInclusive < expected.startInclusive || it.endExclusive > expected.endExclusive
                }
            ) return false
            if (expected.startInclusive == expected.endExclusive) {
                return completedRanges.all {
                    it.startInclusive >= expected.startInclusive && it.endExclusive <= expected.endExclusive
                }
            }

            var coveredUntil = expected.startInclusive
            for (range in completedRanges.sortedBy(ReadRange::startInclusive)) {
                if (range.endExclusive <= coveredUntil) continue
                if (range.startInclusive > coveredUntil) return false
                coveredUntil = maxOf(coveredUntil, range.endExclusive)
                if (coveredUntil >= expected.endExclusive) return true
            }
            return false
        }

    companion object {
        fun unknown(scope: String, unit: String = "unknown") =
            ReadCoverage(scope = scope, unit = unit)
    }
}

data class RawChunk(
    val label: String,
    val location: String? = null,
    val hex: String,
    val sourceByteCount: Int,
    val capturedByteCount: Int,
) {
    val isTruncated: Boolean
        get() = capturedByteCount < sourceByteCount

    init {
        require(sourceByteCount >= 0)
        require(capturedByteCount in 0..sourceByteCount)
        require(hex.length == capturedByteCount * 2)
    }
}

data class TechReadResult(
    val metadata: List<NfcField> = emptyList(),
    val rawChunks: List<RawChunk> = emptyList(),
    val decodedFields: List<NfcField> = emptyList(),
    val issues: List<ReadIssue> = emptyList(),
    val coverage: ReadCoverage = ReadCoverage.unknown("No reader coverage reported"),
    val unreadRanges: List<UnreadRange> = emptyList(),
    val outcome: ReadOutcome = ReadOutcome.METADATA_ONLY,
    val coverageDetails: List<ReadCoverage> = listOf(coverage),
) {
    init {
        require(
            outcome != ReadOutcome.COMPLETE ||
                (coverage.isComplete && coverageDetails.all(ReadCoverage::isComplete) &&
                    issues.isEmpty() && unreadRanges.isEmpty() &&
                    rawChunks.none(RawChunk::isTruncated))
        ) {
            "A complete result requires known, fully covered scope with no issues or truncation."
        }
    }

    companion object {
        fun fromLegacyFields(fields: List<NfcField>): TechReadResult =
            if (fields.isEmpty()) {
                TechReadResult(outcome = ReadOutcome.METADATA_ONLY)
            } else {
                TechReadResult(
                    decodedFields = fields,
                    issues = listOf(
                        ReadIssue(
                            kind = ReadIssueKind.COVERAGE_UNKNOWN,
                            message = "Reader does not report the complete read range.",
                        )
                    ),
                    coverage = ReadCoverage.unknown("Legacy reader output"),
                    outcome = ReadOutcome.PARTIAL,
                )
            }

        fun notAttempted(issue: ReadIssue) = TechReadResult(
            issues = listOf(issue),
            coverage = ReadCoverage.unknown("Technology read was not attempted"),
            outcome = ReadOutcome.NOT_ATTEMPTED,
        )
    }
}
