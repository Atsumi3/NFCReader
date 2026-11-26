package info.nukoneko.android.nfcreader.model.reader

fun interface ReadOnlyTransport {
    fun transceiveReadOnly(command: ByteArray): ByteArray
}

fun interface ReadOnlyProtocolReader {
    fun read(transport: ReadOnlyTransport, context: TechReadContext)
}
