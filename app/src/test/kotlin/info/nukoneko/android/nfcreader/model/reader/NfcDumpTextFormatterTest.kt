package info.nukoneko.android.nfcreader.model.reader

import info.nukoneko.android.nfcreader.model.entity.NfcDump
import info.nukoneko.android.nfcreader.model.entity.NfcEntity
import info.nukoneko.android.nfcreader.model.entity.NfcField
import info.nukoneko.android.nfcreader.model.entity.NfcTagMetadata
import info.nukoneko.android.nfcreader.model.entity.ReadCoverage
import info.nukoneko.android.nfcreader.model.entity.ReadIssue
import info.nukoneko.android.nfcreader.model.entity.ReadIssueKind
import info.nukoneko.android.nfcreader.model.entity.ReadOutcome
import info.nukoneko.android.nfcreader.model.entity.RawChunk
import info.nukoneko.android.nfcreader.model.entity.ReadRange
import info.nukoneko.android.nfcreader.model.entity.TechReadResult
import info.nukoneko.android.nfcreader.model.entity.UnreadRange
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcDumpTextFormatterTest {
    @Test
    fun includesRawDecodedCoverageIssuesAndUnreadRanges() {
        val dump = NfcDump(
            tag = NfcTagMetadata(idHex = "0102", techNames = listOf("android.nfc.tech.NfcA")),
            technologies = listOf(
                NfcEntity(
                    techName = "android.nfc.tech.NfcA",
                    fields = listOf(NfcField("example", "decoded")),
                    readResult = TechReadResult(
                        rawChunks = listOf(RawChunk("response", "block 1", "AABB", 2, 2)),
                        decodedFields = listOf(NfcField("example", "decoded")),
                        issues = listOf(ReadIssue(ReadIssueKind.IO_ERROR, "exchange failed")),
                        coverage = ReadCoverage("blocks", "block", ReadRange(0, 2), listOf(ReadRange(0, 1))),
                        unreadRanges = listOf(UnreadRange("blocks", "block", 1, 2, "not read")),
                        outcome = ReadOutcome.PARTIAL,
                    ),
                )
            ),
        )
        val text = NfcDumpTextFormatter.format(dump)

        assertTrue(text.contains("AABB"))
        assertTrue(text.contains("Captured at (epoch ms): ${dump.capturedAtEpochMillis}"))
        assertTrue(text.contains("Decoded.example: decoded"))
        assertTrue(text.contains("Coverage: blocks (block); complete=false"))
        assertTrue(text.contains("Issue [IO_ERROR]"))
        assertTrue(text.contains("Unread blocks block 1..2 exclusive: not read"))
    }
}
