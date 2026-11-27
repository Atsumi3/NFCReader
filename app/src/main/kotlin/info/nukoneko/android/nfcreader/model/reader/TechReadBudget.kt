package info.nukoneko.android.nfcreader.model.reader

data class TechReadLimits(
    val timeoutMillis: Long,
    val maxTransceives: Int,
) {
    init {
        require(timeoutMillis > 0)
        require(maxTransceives > 0)
    }

    companion object {
        fun forTechnology(techName: String): TechReadLimits = when (techName) {
            "android.nfc.tech.NfcF" -> TechReadLimits(timeoutMillis = 30_000, maxTransceives = 4096)
            "android.nfc.tech.NfcV" -> TechReadLimits(timeoutMillis = 30_000, maxTransceives = 4096)
            "android.nfc.tech.IsoDep" -> TechReadLimits(timeoutMillis = 30_000, maxTransceives = 4096)
            "android.nfc.tech.MifareClassic" -> TechReadLimits(timeoutMillis = 30_000, maxTransceives = 2048)
            "android.nfc.tech.MifareUltralight" -> TechReadLimits(timeoutMillis = 20_000, maxTransceives = 512)
            "android.nfc.tech.Ndef" -> TechReadLimits(timeoutMillis = 10_000, maxTransceives = 16)
            else -> TechReadLimits(timeoutMillis = 5_000, maxTransceives = 64)
        }
    }
}

internal class TechReadBudget(
    val limits: TechReadLimits,
    private val clockNanos: () -> Long = System::nanoTime,
) {
    private val deadlineNanos = clockNanos() + limits.timeoutMillis * NANOS_PER_MILLI
    private var transceives = 0
    private var forcedExpired = false

    @Synchronized
    fun deadlineFailureReason(): String? =
        if (deadlineExpired()) "Technology read deadline reached." else null

    @Synchronized
    fun failureReason(): String? = when {
        deadlineExpired() -> "Technology read deadline reached."
        transceives >= limits.maxTransceives -> "Technology transceive limit reached."
        else -> null
    }

    @Synchronized
    fun reserveTransceive(): String? {
        val failure = failureReason()
        if (failure == null) transceives += 1
        return failure
    }

    @Synchronized
    fun expire() {
        forcedExpired = true
    }

    @Synchronized
    fun transceiveCount(): Int = transceives

    private fun deadlineExpired(): Boolean = forcedExpired || clockNanos() >= deadlineNanos

    fun remainingTimeoutMillis(): Long =
        ((deadlineNanos - clockNanos()).coerceAtLeast(0L) / NANOS_PER_MILLI)

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

internal class ReadBudgetExceededException(message: String) : RuntimeException(message)
