package info.nukoneko.android.nfcreader.model.entity

data class NfcTagMetadata(
    val idHex: String? = null,
    val techNames: List<String> = emptyList(),
    val fields: List<NfcField> = emptyList(),
)

data class NfcDump(
    val tag: NfcTagMetadata,
    val technologies: List<NfcEntity>,
    val issues: List<ReadIssue> = emptyList(),
    val rawByteLimit: Int = DEFAULT_MAX_RAW_BYTES_PER_DUMP,
    val capturedAtEpochMillis: Long = System.currentTimeMillis(),
) {
    val rawBytesCaptured: Int
        get() = technologies.sumOf { entity ->
            entity.readResult.rawChunks.sumOf(RawChunk::capturedByteCount)
        }

    val outcome: ReadOutcome
        get() = when {
            technologies.isEmpty() -> ReadOutcome.METADATA_ONLY
            issues.isNotEmpty() -> ReadOutcome.PARTIAL
            technologies.map(NfcEntity::techName) != tag.techNames.distinct() -> ReadOutcome.PARTIAL
            technologies.all { it.readResult.outcome == ReadOutcome.COMPLETE } -> ReadOutcome.COMPLETE
            else -> ReadOutcome.PARTIAL
        }
}
