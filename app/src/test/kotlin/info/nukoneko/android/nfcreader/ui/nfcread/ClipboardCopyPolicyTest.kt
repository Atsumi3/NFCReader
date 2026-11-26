package info.nukoneko.android.nfcreader.ui.nfcread

import android.content.ClipDescription
import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ClipboardCopyPolicyTest {
    @Test
    fun acceptsPayloadAtLimitAndRejectsLargerPayloadWithoutTruncation() {
        assertTrue(isClipboardCopyAllowed("a".repeat(MAX_DUMP_CLIPBOARD_BYTES)))
        assertFalse(isClipboardCopyAllowed("a".repeat(MAX_DUMP_CLIPBOARD_BYTES + 1)))
        assertFalse(isClipboardCopyAllowed("あ".repeat(MAX_DUMP_CLIPBOARD_BYTES / 3 + 1)))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun marksClipboardContentSensitiveOnAndroid13AndLater() {
        val clip = createSensitivePlainTextClip("NFC dump", "card data")

        assertTrue(clip.description.extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun keepsLegacyClipboardBehaviorBeforeAndroid13() {
        val clip = createSensitivePlainTextClip("NFC dump", "card data")

        assertFalse(clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true)
    }
}
