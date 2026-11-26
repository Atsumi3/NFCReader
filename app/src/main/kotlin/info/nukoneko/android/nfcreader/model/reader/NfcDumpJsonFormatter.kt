package info.nukoneko.android.nfcreader.model.reader

import info.nukoneko.android.nfcreader.model.entity.NfcDump
import info.nukoneko.android.nfcreader.model.entity.NfcEntity
import info.nukoneko.android.nfcreader.model.entity.NfcField
import info.nukoneko.android.nfcreader.model.entity.ReadCoverage
import info.nukoneko.android.nfcreader.model.entity.ReadIssue
import info.nukoneko.android.nfcreader.model.entity.ReadRange
import info.nukoneko.android.nfcreader.model.entity.RawChunk
import info.nukoneko.android.nfcreader.model.entity.UnreadRange

object NfcDumpJsonFormatter {
    fun format(dump: NfcDump): String = buildString {
        append('{')
        append("\"schemaVersion\":1")
        append(",\"capturedAtEpochMillis\":").append(dump.capturedAtEpochMillis)
        append(",\"outcome\":").append(jsonString(dump.outcome.name))
        append(",\"rawByteLimit\":").append(dump.rawByteLimit)
        append(",\"rawBytesCaptured\":").append(dump.rawBytesCaptured)
        append(",\"tag\":{")
        append("\"idHex\":").append(jsonString(dump.tag.idHex))
        append(",\"techNames\":").append(dump.tag.techNames.joinToString(prefix = "[", postfix = "]") { jsonString(it) })
        append(",\"fields\":").append(dump.tag.fields.fieldsJson())
        append('}')
        append(",\"issues\":").append(dump.issues.issuesJson())
        append(",\"technologies\":")
        append(dump.technologies.joinToString(prefix = "[", postfix = "]") { it.toJson() })
        append('}')
    }

    private fun NfcEntity.toJson(): String = buildString {
        append('{')
        append("\"techName\":").append(jsonString(techName))
        append(",\"metadata\":").append(readResult.metadata.fieldsJson())
        append(",\"rawChunks\":").append(readResult.rawChunks.rawChunksJson())
        append(",\"decodedFields\":").append(readResult.decodedFields.fieldsJson())
        append(",\"issues\":").append(readResult.issues.issuesJson())
        append(",\"coverage\":").append(readResult.coverage.toJson())
        append(",\"coverageDetails\":")
        append(readResult.coverageDetails.joinToString(prefix = "[", postfix = "]") { it.toJson() })
        append(",\"unreadRanges\":").append(readResult.unreadRanges.unreadRangesJson())
        append(",\"outcome\":").append(jsonString(readResult.outcome.name))
        append('}')
    }

    private fun List<NfcField>.fieldsJson(): String =
        joinToString(prefix = "[", postfix = "]") { field ->
            "{\"label\":${jsonString(field.label)},\"value\":${jsonString(field.value)}}"
        }

    private fun List<RawChunk>.rawChunksJson(): String =
        joinToString(prefix = "[", postfix = "]") { chunk ->
            buildString {
                append('{')
                append("\"label\":").append(jsonString(chunk.label))
                append(",\"location\":").append(jsonString(chunk.location))
                append(",\"hex\":").append(jsonString(chunk.hex))
                append(",\"sourceByteCount\":").append(chunk.sourceByteCount)
                append(",\"capturedByteCount\":").append(chunk.capturedByteCount)
                append(",\"truncated\":").append(chunk.isTruncated)
                append('}')
            }
        }

    private fun List<ReadIssue>.issuesJson(): String =
        joinToString(prefix = "[", postfix = "]") { issue ->
            "{\"kind\":${jsonString(issue.kind.name)},\"message\":${jsonString(issue.message)},\"operation\":${jsonString(issue.operation)}}"
        }

    private fun ReadCoverage.toJson(): String = buildString {
        append('{')
        append("\"scope\":").append(jsonString(scope))
        append(",\"unit\":").append(jsonString(unit))
        append(",\"expectedRange\":").append(expectedRange?.toJson() ?: "null")
        append(",\"completedRanges\":")
        append(completedRanges.joinToString(prefix = "[", postfix = "]") { it.toJson() })
        append(",\"complete\":").append(isComplete)
        append('}')
    }

    private fun ReadRange.toJson(): String =
        "{\"startInclusive\":$startInclusive,\"endExclusive\":$endExclusive}"

    private fun List<UnreadRange>.unreadRangesJson(): String =
        joinToString(prefix = "[", postfix = "]") { range ->
            buildString {
                append('{')
                append("\"scope\":").append(jsonString(range.scope))
                append(",\"unit\":").append(jsonString(range.unit))
                append(",\"startInclusive\":").append(range.startInclusive?.toString() ?: "null")
                append(",\"endExclusive\":").append(range.endExclusive?.toString() ?: "null")
                append(",\"reason\":").append(jsonString(range.reason))
                append('}')
            }
        }

    private fun jsonString(value: String?): String {
        if (value == null) return "null"
        return buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (character.code < 0x20) {
                        append("\\u%04x".format(character.code))
                    } else {
                        append(character)
                    }
                }
            }
            append('"')
        }
    }
}
