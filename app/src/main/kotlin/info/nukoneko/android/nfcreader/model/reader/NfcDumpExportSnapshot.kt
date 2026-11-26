package info.nukoneko.android.nfcreader.model.reader

import info.nukoneko.android.nfcreader.model.entity.NfcDump

data class NfcDumpExportSnapshot(
    val dump: NfcDump,
    val text: String,
    val json: String,
    val technologyText: Map<String, String>,
) {
    companion object {
        fun create(dump: NfcDump) = NfcDumpExportSnapshot(
            dump = dump,
            text = NfcDumpTextFormatter.format(dump),
            json = NfcDumpJsonFormatter.format(dump),
            technologyText = dump.technologies.associate {
                it.techName to NfcDumpTextFormatter.formatTechnology(it)
            },
        )
    }
}
