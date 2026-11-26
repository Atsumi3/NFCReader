package info.nukoneko.android.nfcreader.model.entity

sealed interface ReadStatus {
    data object Idle : ReadStatus
    data object Reading : ReadStatus
    data class ReadingWithPrevious(val dump: NfcDump) : ReadStatus
    data class Success(
        val entities: List<NfcEntity>,
        val dump: NfcDump = NfcDump(
            tag = NfcTagMetadata(),
            technologies = entities,
        ),
    ) : ReadStatus
    sealed interface Failure : ReadStatus {
        data object NfcDisabled : Failure
        data class NfcDisabledWithDump(val dump: NfcDump) : Failure
        data object IntentUnsupported : Failure
        data class IntentUnsupportedWithDump(val dump: NfcDump) : Failure
        data class Error(val cause: Throwable) : Failure
    }
}
