package info.nukoneko.android.nfcreader.model.entity

sealed class ReadStatus<T> {
    class IDLE<T> : ReadStatus<T>()
    class READING<T> : ReadStatus<T>()
    data class SUCCESS<T>(val value: T) : ReadStatus<T>()
    data class FAILED<T>(val error: Throwable) : ReadStatus<T>()
}