package info.nukoneko.android.nfcreader.model.reader

import info.nukoneko.android.nfcreader.model.entity.NfcField
import info.nukoneko.android.nfcreader.model.entity.NfcTagMetadata
import info.nukoneko.android.nfcreader.model.entity.ReadCoverage
import info.nukoneko.android.nfcreader.model.entity.ReadIssue
import info.nukoneko.android.nfcreader.model.entity.ReadIssueKind
import info.nukoneko.android.nfcreader.model.entity.ReadOutcome
import info.nukoneko.android.nfcreader.model.entity.ReadRange
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcDumpCollectorTest {

    @Test
    fun interruptionPreservesCurrentDataAndEveryRemainingTechnology() {
        val collector = collector("TechA", "TechB", "TechC")

        collector.readAll(readTechnology = { techName, context ->
            when (techName) {
                "TechA" -> {
                    context.addDecodedFields(listOf(NfcField("TechA.value", "kept")))
                    context.markPartial()
                }

                "TechB" -> {
                    context.addDecodedFields(listOf(NfcField("TechB.value", "partial")))
                    throw CancellationException("stop between reads")
                }

                else -> error("TechC must not be read after interruption")
            }
        })

        val dump = collector.snapshot()

        assertEquals(listOf("TechA", "TechB", "TechC"), dump.technologies.map { it.techName })
        assertEquals("kept", dump.technologies[0].readResult.decodedFields.single().value)
        assertEquals(ReadOutcome.PARTIAL, dump.technologies[1].readResult.outcome)
        assertEquals("partial", dump.technologies[1].readResult.decodedFields.single().value)
        assertEquals(ReadIssueKind.INTERRUPTED, dump.technologies[1].readResult.issues.single().kind)
        assertEquals(ReadOutcome.NOT_ATTEMPTED, dump.technologies[2].readResult.outcome)
        assertEquals(ReadIssueKind.INTERRUPTED, dump.technologies[2].readResult.issues.single().kind)
    }

    @Test
    fun tagLossKeepsEarlierReadsAndMarksLaterTechnologiesNotAttempted() {
        val collector = collector("TechA", "TechB", "TechC")

        collector.readAll(readTechnology = { techName, context ->
            if (techName == "TechA") {
                context.addDecodedFields(listOf(NfcField("TechA.value", "kept")))
                context.setCoverage(completeCoverage())
                context.markComplete()
            } else if (techName == "TechB") {
                context.addDecodedFields(listOf(NfcField("TechB.value", "before loss")))
                context.markFailed(
                    ReadIssue(ReadIssueKind.TAG_LOST, "Tag left the NFC field.", techName)
                )
            } else {
                error("TechC must not be read after tag loss")
            }
        })

        val dump = collector.snapshot()

        assertEquals(ReadOutcome.COMPLETE, dump.technologies[0].readResult.outcome)
        assertEquals("before loss", dump.technologies[1].readResult.decodedFields.single().value)
        assertEquals(ReadIssueKind.TAG_LOST, dump.technologies[1].readResult.issues.single().kind)
        assertEquals(ReadOutcome.NOT_ATTEMPTED, dump.technologies[2].readResult.outcome)
        assertTrue(dump.issues.any { it.kind == ReadIssueKind.TAG_LOST })
        assertEquals(ReadOutcome.PARTIAL, dump.outcome)
    }

    @Test
    fun unexpectedReaderFailureDoesNotDropOtherTechnologies() {
        val collector = collector("TechA", "TechB", "TechC")

        collector.readAll(readTechnology = { techName, context ->
            when (techName) {
                "TechB" -> error("reader failure")
                else -> {
                    context.addMetadata(listOf(NfcField("$techName.meta", "visible")))
                    context.markMetadataOnly()
                }
            }
        })

        val dump = collector.snapshot()

        assertEquals(listOf("TechA", "TechB", "TechC"), dump.technologies.map { it.techName })
        assertEquals(ReadOutcome.FAILED, dump.technologies[1].readResult.outcome)
        assertEquals(ReadIssueKind.OTHER, dump.technologies[1].readResult.issues.single().kind)
        assertEquals(ReadOutcome.METADATA_ONLY, dump.technologies[2].readResult.outcome)
    }

    @Test
    fun rawBudgetIsSharedAcrossTechnologiesAndTruncationIsVisible() {
        val collector = NfcDumpCollector(
            tagMetadata = NfcTagMetadata(techNames = listOf("TechA", "TechB")),
            rawByteLimit = 4,
        )

        collector.readAll(readTechnology = { techName, context ->
            context.captureRaw(techName, byteArrayOf(0x01, 0x02, 0x03))
            context.markPartial()
        })

        val dump = collector.snapshot()

        assertEquals(4, dump.rawBytesCaptured)
        assertEquals(4, dump.rawByteLimit)
        assertEquals("010203", dump.technologies[0].readResult.rawChunks.single().hex)
        assertEquals("01", dump.technologies[1].readResult.rawChunks.single().hex)
        assertTrue(dump.technologies[1].readResult.rawChunks.single().isTruncated)
        assertTrue(dump.technologies[1].fields.any { it.label == "Dump.truncated" })
    }

    @Test
    fun captureTimestampIsStableAcrossSnapshots() {
        val collector = collector("TechA")
        val first = collector.snapshot()

        assertEquals(first.capturedAtEpochMillis, collector.snapshot().capturedAtEpochMillis)
    }

    private fun collector(vararg techNames: String) = NfcDumpCollector(
        tagMetadata = NfcTagMetadata(
            idHex = "0102",
            techNames = techNames.toList(),
            fields = listOf(NfcField("Tag.Id", "01 02")),
        )
    )

    private fun completeCoverage() = ReadCoverage(
        scope = "one test unit",
        unit = "block",
        expectedRange = ReadRange(0, 1),
        completedRanges = listOf(ReadRange(0, 1)),
    )
}
