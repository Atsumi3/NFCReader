package info.nukoneko.android.nfcreader.model.reader

import info.nukoneko.android.nfcreader.model.entity.NfcDump
import info.nukoneko.android.nfcreader.model.entity.NfcEntity

object NfcDumpTextFormatter {
    fun format(dump: NfcDump): String = buildString {
        appendLine("NFC dump")
        appendLine("Captured at (epoch ms): ${dump.capturedAtEpochMillis}")
        appendLine("Outcome: ${dump.outcome}")
        appendLine("Captured raw bytes: ${dump.rawBytesCaptured}/${dump.rawByteLimit}")
        dump.tag.idHex?.let { appendLine("Tag ID: $it") }
        dump.tag.fields.forEach { appendLine("Tag.${it.label}: ${it.value ?: "-"}") }
        appendLine("Tag technologies: ${dump.tag.techNames.joinToString()}")
        dump.issues.forEach { appendLine("Dump issue [${it.kind}] ${it.operation.orEmpty()}: ${it.message}") }
        dump.technologies.forEach { appendLine().append(formatTechnology(it)) }
    }.trimEnd()

    fun formatTechnology(entity: NfcEntity): String = buildString {
        val result = entity.readResult
        appendLine("Technology: ${entity.techName}")
        appendLine("Outcome: ${result.outcome}")
        appendLine("Coverage: ${result.coverage.scope} (${result.coverage.unit}); complete=${result.coverage.isComplete}")
        result.coverage.expectedRange?.let { appendLine("Expected range: ${it.startInclusive}..${it.endExclusive} exclusive") }
        result.coverageDetails.drop(1).forEach { detail ->
            appendLine("Additional coverage: ${detail.scope} (${detail.unit}); complete=${detail.isComplete}")
            detail.expectedRange?.let { appendLine("Expected range: ${it.startInclusive}..${it.endExclusive} exclusive") }
        }
        result.metadata.forEach { appendLine("Metadata.${it.label}: ${it.value ?: "-"}") }
        result.rawChunks.forEach { chunk ->
            appendLine("Raw.${chunk.label}${chunk.location?.let { " [$it]" }.orEmpty()}: ${chunk.hex}")
            if (chunk.isTruncated) {
                appendLine("Raw truncated: captured ${chunk.capturedByteCount} of ${chunk.sourceByteCount} bytes")
            }
        }
        result.decodedFields.forEach { appendLine("Decoded.${it.label}: ${it.value ?: "-"}") }
        result.issues.forEach { appendLine("Issue [${it.kind}] ${it.operation.orEmpty()}: ${it.message}") }
        result.unreadRanges.forEach { range ->
            appendLine(
                "Unread ${range.scope} ${range.unit}" +
                    (range.startInclusive?.let { " ${it}..${range.endExclusive} exclusive" } ?: " (range unknown)") +
                    ": ${range.reason}"
            )
        }
    }.trimEnd()
}
