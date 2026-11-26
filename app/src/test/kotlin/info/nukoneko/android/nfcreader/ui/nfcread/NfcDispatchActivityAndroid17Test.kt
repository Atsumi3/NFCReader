package info.nukoneko.android.nfcreader.ui.nfcread

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNfcAdapter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.CINNAMON_BUN])
class NfcDispatchActivityAndroid17Test {

    @Test
    fun android17EnablesProtectedReceiverAndLeavesLauncherUnprotected() {
        val application = RuntimeEnvironment.getApplication()
        val packageManager = application.packageManager
        val legacyReceiverInfo = packageManager.getActivityInfo(
            ComponentName(application, NfcDispatchActivity::class.java),
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        val protectedReceiverInfo = packageManager.getActivityInfo(
            ComponentName(application, ProtectedNfcDispatchActivity::class.java),
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        assertFalse(legacyReceiverInfo.enabled)
        assertTrue(protectedReceiverInfo.enabled)
        assertEquals("android.permission.DISPATCH_NFC_MESSAGE", protectedReceiverInfo.permission)

        val launcherActivities = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0,
        )
        val launcher = launcherActivities.firstOrNull {
            it.activityInfo.name == NfcReadActivity::class.java.name
        }
        assertNotNull(launcher)
        assertNull(requireNotNull(launcher).activityInfo.permission)
    }

    @Test
    fun protectedReceiverFiltersAllNfcDiscoveryActions() {
        val packageManager = RuntimeEnvironment.getApplication().packageManager
        val actions = listOf(
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
        )

        actions.forEach { action ->
            val matches = packageManager.queryIntentActivities(
                Intent(action).addCategory(Intent.CATEGORY_DEFAULT),
                0,
            )
            assertTrue(matches.any { it.activityInfo.name == ProtectedNfcDispatchActivity::class.java.name })
            assertFalse(matches.any { it.activityInfo.name == NfcDispatchActivity::class.java.name })
        }
    }

    @Test
    fun protectedReceiverForwardsColdStartIntentWithTag() {
        val intent = Intent()
            .setAction(NfcAdapter.ACTION_TAG_DISCOVERED)
            .putExtra(NfcAdapter.EXTRA_TAG, ShadowNfcAdapter.createMockTag())
        val controller = Robolectric.buildActivity(ProtectedNfcDispatchActivity::class.java, intent)

        try {
            controller.create()

            val forwardedIntent = requireNotNull(Shadows.shadowOf(controller.get()).nextStartedActivity)
            assertEquals(NfcReadActivity::class.java.name, forwardedIntent.component?.className)
            assertEquals(intent.action, forwardedIntent.action)
            assertNotNull(forwardedIntent.extras?.get(NfcAdapter.EXTRA_TAG))
        } finally {
            controller.destroy()
        }
    }
}
