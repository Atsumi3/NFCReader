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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcDumpTextFormatterTest {
    @Test
    fun preservesTrailingWhitespaceInDecodedValues() {
        listOf("decoded value  ", "decoded line\n", "decoded line\r\n").forEach { value ->
            val entity = entityWith(value)
            val emptyEntity = entityWith("")

            assertEquals(
                NfcDumpTextFormatter.formatTechnology(emptyEntity) + value,
                NfcDumpTextFormatter.formatTechnology(entity),
            )
            assertEquals(
                NfcDumpTextFormatter.format(dumpWith(emptyEntity)) + value,
                NfcDumpTextFormatter.format(dumpWith(entity)),
            )
        }
    }

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

    @Test
    fun preservesEarlierCoverageDetailsAndPrintsLatestCoverageOnce() {
        val suica = ReadCoverage("Suica history", "block", ReadRange(0, 20), listOf(ReadRange(0, 20)))
        val service = ReadCoverage("standard service", "candidate", ReadRange(0, 2048), listOf(ReadRange(0, 32)))
        val systems = ReadCoverage("FeliCa systems", "system", ReadRange(0, 2), listOf(ReadRange(0, 2)))
        val entity = NfcEntity(
            techName = "android.nfc.tech.NfcF",
            fields = emptyList(),
            readResult = TechReadResult(
                coverage = systems,
                coverageDetails = listOf(suica, service, systems),
                outcome = ReadOutcome.PARTIAL,
            ),
        )

        val text = NfcDumpTextFormatter.formatTechnology(entity)

        assertEquals(1, text.countOccurrences("Additional coverage: Suica history"))
        assertEquals(1, text.countOccurrences("Additional coverage: standard service"))
        assertEquals(1, text.countOccurrences("Coverage: FeliCa systems"))
        assertEquals(0, text.countOccurrences("Additional coverage: FeliCa systems"))
    }

    private fun String.countOccurrences(value: String): Int = split(value).size - 1

    private fun entityWith(value: String) = NfcEntity(
        techName = "android.nfc.tech.NfcA",
        fields = emptyList(),
        readResult = TechReadResult(
            decodedFields = listOf(NfcField("example", value)),
            coverage = ReadCoverage("example values", "field", ReadRange(0, 1), listOf(ReadRange(0, 1))),
            outcome = ReadOutcome.COMPLETE,
        ),
    )

    private fun dumpWith(entity: NfcEntity) = NfcDump(
        tag = NfcTagMetadata(techNames = listOf(entity.techName)),
        technologies = listOf(entity),
        capturedAtEpochMillis = 1L,
    )
}
