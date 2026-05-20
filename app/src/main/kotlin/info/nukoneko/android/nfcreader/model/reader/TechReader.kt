package info.nukoneko.android.nfcreader.model.reader

import android.nfc.tech.TagTechnology
import info.nukoneko.android.nfcreader.model.entity.NfcField

/**
 * Reads tag-type-specific content by exchanging protocol commands with the card,
 * beyond the metadata exposed by the generic reflection-based getter dump.
 */
interface TechReader {
    /** Fully-qualified tech class name handled, e.g. "android.nfc.tech.Ndef". */
    val techName: String

    /** [tech] is already connected. Returns extra fields, or empty if nothing applies. */
    fun read(tech: TagTechnology): List<NfcField>
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
