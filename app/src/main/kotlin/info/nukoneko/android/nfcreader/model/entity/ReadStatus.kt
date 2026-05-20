package info.nukoneko.android.nfcreader.model.entity

sealed interface ReadStatus {
    data object Idle : ReadStatus
    data object Reading : ReadStatus
    data class Success(val entities: List<NfcEntity>) : ReadStatus
    sealed interface Failure : ReadStatus {
        data object NfcDisabled : Failure
        data object IntentUnsupported : Failure
        data class Error(val cause: Throwable) : Failure
    }
}
