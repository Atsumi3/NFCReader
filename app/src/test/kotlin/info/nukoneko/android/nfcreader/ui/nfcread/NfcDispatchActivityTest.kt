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
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
class NfcDispatchActivityTest {

    @Test
    fun coldStartNfcIntentIsForwardedToReadActivity() {
        val tag = ShadowNfcAdapter.createMockTag()
        val intent = Intent()
            .setAction(NfcAdapter.ACTION_TAG_DISCOVERED)
            .putExtra(NfcAdapter.EXTRA_TAG, tag)
        val controller = Robolectric.buildActivity(NfcDispatchActivity::class.java, intent)

        try {
            controller.create()

            val forwardedIntent = requireNotNull(Shadows.shadowOf(controller.get()).nextStartedActivity)
            assertEquals(NfcReadActivity::class.java.name, forwardedIntent.component?.className)
            assertEquals(intent.action, forwardedIntent.action)
            assertNotNull(forwardedIntent.extras?.get(NfcAdapter.EXTRA_TAG))
            assertTrue((forwardedIntent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0)
            assertTrue((forwardedIntent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0)
            assertTrue(controller.get().isFinishing)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun launcherAndLegacyNfcReceiverRemainUnprotectedBeforeAndroid17() {
        val application = RuntimeEnvironment.getApplication()
        val packageManager = application.packageManager
        val readActivityInfo = packageManager.getActivityInfo(
            ComponentName(application, NfcReadActivity::class.java),
            0,
        )
        assertTrue(readActivityInfo.exported)
        assertNull(readActivityInfo.permission)

        val launcherActivities = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        assertTrue(launcherActivities.any { it.activityInfo.name == NfcReadActivity::class.java.name })

        val legacyReceiverInfo = packageManager.getActivityInfo(
            ComponentName(application, NfcDispatchActivity::class.java),
            0,
        )
        assertTrue(legacyReceiverInfo.enabled)
        assertNull(legacyReceiverInfo.permission)

        val protectedReceiverInfo = packageManager.getActivityInfo(
            ComponentName(application, ProtectedNfcDispatchActivity::class.java),
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        assertFalse(protectedReceiverInfo.enabled)
        assertEquals("android.permission.DISPATCH_NFC_MESSAGE", protectedReceiverInfo.permission)
    }

    @Test
    fun legacyReceiverFiltersAllNfcDiscoveryActions() {
        val packageManager = RuntimeEnvironment.getApplication().packageManager
        val actions = listOf(
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
        )

        actions.forEach { action ->
            val matches = packageManager.queryIntentActivities(
                Intent(action).addCategory(Intent.CATEGORY_DEFAULT),
                PackageManager.MATCH_DEFAULT_ONLY,
            )
            assertTrue(matches.any { it.activityInfo.name == NfcDispatchActivity::class.java.name })
            assertFalse(matches.any { it.activityInfo.name == NfcReadActivity::class.java.name })
        }
    }
}
