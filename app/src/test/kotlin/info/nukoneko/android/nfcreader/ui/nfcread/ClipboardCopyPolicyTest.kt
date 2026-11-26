package info.nukoneko.android.nfcreader.ui.nfcread

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardCopyPolicyTest {
    @Test
    fun acceptsPayloadAtLimitAndRejectsLargerPayloadWithoutTruncation() {
        assertTrue(isClipboardCopyAllowed("a".repeat(MAX_DUMP_CLIPBOARD_BYTES)))
        assertFalse(isClipboardCopyAllowed("a".repeat(MAX_DUMP_CLIPBOARD_BYTES + 1)))
        assertFalse(isClipboardCopyAllowed("あ".repeat(MAX_DUMP_CLIPBOARD_BYTES / 3 + 1)))
    }
}
