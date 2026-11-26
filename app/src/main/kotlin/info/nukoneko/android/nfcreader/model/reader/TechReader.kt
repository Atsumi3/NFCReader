package info.nukoneko.android.nfcreader.model.reader

import android.nfc.tech.TagTechnology
import info.nukoneko.android.nfcreader.model.entity.NfcField
import info.nukoneko.android.nfcreader.model.entity.ReadCoverage
import info.nukoneko.android.nfcreader.model.entity.ReadIssue
import info.nukoneko.android.nfcreader.model.entity.ReadIssueKind
import info.nukoneko.android.nfcreader.model.entity.ReadOutcome

/**
 * Reads tag-type-specific content beyond metadata from typed Android discovery getters.
 */
interface TechReader {
    /** Fully-qualified tech class name handled, e.g. "android.nfc.tech.Ndef". */
    val techName: String

    /** [tech] is already connected. Readers append data as it is acquired. */
    fun read(tech: TagTechnology, context: TechReadContext)
}

abstract class LegacyFieldTechReader : TechReader {
    protected abstract fun readLegacy(tech: TagTechnology): List<NfcField>

    final override fun read(tech: TagTechnology, context: TechReadContext) {
        val fields = readLegacy(tech)
        context.addDecodedFields(fields)
        if (fields.isEmpty()) {
            context.markMetadataOnly()
        } else {
            context.setCoverage(ReadCoverage.unknown("Legacy reader output"))
            context.addIssue(
                ReadIssue(
                    kind = ReadIssueKind.COVERAGE_UNKNOWN,
                    message = "Legacy reader does not report the complete read range.",
                )
            )
            context.markPartial()
        }
    }
}

/** Registry that resolves the [TechReader] for a given technology, if any. */
object TechReaders {
    private val readers: List<TechReader> = listOf(
        NdefReader(),
        FelicaReader(),
        MifareUltralightReader(),
        MifareClassicReader(),
        NfcVReader(),
        IsoDepEmvReader(),
    )

    fun forTech(techName: String): TechReader? =
        readers.firstOrNull { it.techName == techName }
}
