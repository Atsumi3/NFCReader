package info.nukoneko.android.nfcreader.ui.nfcread

internal const val MAX_DUMP_CLIPBOARD_BYTES = 64 * 1024

internal fun isClipboardCopyAllowed(value: String): Boolean =
    value.toByteArray(Charsets.UTF_8).size <= MAX_DUMP_CLIPBOARD_BYTES
