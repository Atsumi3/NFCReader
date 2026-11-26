package info.nukoneko.android.nfcreader.model.reader

import info.nukoneko.android.nfcreader.model.entity.NfcDump
import info.nukoneko.android.nfcreader.model.entity.NfcEntity
import info.nukoneko.android.nfcreader.model.entity.NfcField
import info.nukoneko.android.nfcreader.model.entity.NfcTagMetadata
import info.nukoneko.android.nfcreader.model.entity.ReadCoverage
import info.nukoneko.android.nfcreader.model.entity.ReadIssue
import info.nukoneko.android.nfcreader.model.entity.ReadIssueKind
import info.nukoneko.android.nfcreader.model.entity.ReadOutcome
import info.nukoneko.android.nfcreader.model.entity.ReadRange
import info.nukoneko.android.nfcreader.model.entity.RawChunk
import info.nukoneko.android.nfcreader.model.entity.TechReadResult
import info.nukoneko.android.nfcreader.model.entity.UnreadRange
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcDumpJsonFormatterTest {
    @Test
    fun exportsRawDecodedCoverageIssuesAndUnreadRangesAsEscapedJson() {
        val coverage = ReadCoverage("page\"data", "page", ReadRange(0, 1), listOf(ReadRange(0, 1)))
        val dump = NfcDump(
            tag = NfcTagMetadata(idHex = "0102", techNames = listOf("Tech"), fields = listOf(NfcField("tag", "line\nbreak"))),
            technologies = listOf(
                NfcEntity(
                    techName = "Tech",
                    readResult = TechReadResult(
                        rawChunks = listOf(RawChunk("response", hex = "00FF", sourceByteCount = 2, capturedByteCount = 2)),
                        decodedFields = listOf(NfcField("decoded", "value\"quoted")),
                        issues = listOf(ReadIssue(ReadIssueKind.IO_ERROR, "timeout", "read")),
                        coverage = coverage,
                        unreadRanges = listOf(UnreadRange("block", "block", 1, 2, "not read")),
                        outcome = ReadOutcome.PARTIAL,
                    ),
                )
            ),
        )

        val json = NfcDumpJsonFormatter.format(dump)

        assertTrue(json.startsWith("{\"schemaVersion\":1"))
        assertTrue(json.contains("\"capturedAtEpochMillis\":${dump.capturedAtEpochMillis}"))
        assertTrue(json.contains("\"hex\":\"00FF\""))
        assertTrue(json.contains("\"value\":\"value\\\"quoted\""))
        assertTrue(json.contains("\"scope\":\"page\\\"data\""))
        assertTrue(json.contains("\"kind\":\"IO_ERROR\""))
        assertTrue(json.contains("\"unreadRanges\":[{\"scope\":\"block\""))
        assertTrue(json.contains("line\\nbreak"))
        assertTrue(json.endsWith("}"))
    }
}
