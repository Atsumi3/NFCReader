package info.nukoneko.android.nfcreader.model.entity

data class NfcEntity(
    val techName: String,
    val fields: List<NfcField>
) {
    val shortName: String get() = techName.substringAfterLast('.')
}
