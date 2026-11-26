package info.nukoneko.android.nfcreader.ui.nfcread

import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import info.nukoneko.android.nfcreader.model.entity.ReadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowNfcAdapter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
class NfcReadActivityTest {

    @Test
    fun coldStartNdefIntentIsProcessed() = assertColdStartNfcAction(NfcAdapter.ACTION_NDEF_DISCOVERED)

    @Test
    fun coldStartTechIntentIsProcessed() = assertColdStartNfcAction(NfcAdapter.ACTION_TECH_DISCOVERED)

    @Test
    fun coldStartTagIntentIsProcessed() = assertColdStartNfcAction(NfcAdapter.ACTION_TAG_DISCOVERED)

    @Test
    fun coldStartTagIntentWithTagStartsRead() {
        val intent = Intent()
            .setAction(NfcAdapter.ACTION_TAG_DISCOVERED)
            .putExtra(NfcAdapter.EXTRA_TAG, ShadowNfcAdapter.createMockTag())

        withResumedActivity(intent) { controller ->
            assertTrue(viewModel(controller.get()).readTime.value.isNotBlank())
        }
    }

    @Test
    fun nonNfcColdStartIntentIsIgnored() {
        withResumedActivity(Intent().setAction(Intent.ACTION_MAIN)) { controller ->
            assertEquals(ReadStatus.Idle, viewModel(controller.get()).state.value)
        }
    }

    @Test
    fun newNfcIntentIsProcessedAndStored() {
        val intent = Intent().setAction(NfcAdapter.ACTION_TAG_DISCOVERED)

        withResumedActivity(Intent().setAction(Intent.ACTION_MAIN)) { controller ->
            controller.newIntent(intent)

            assertEquals(intent.action, controller.get().intent.action)
            assertEquals(ReadStatus.Failure.IntentUnsupported, viewModel(controller.get()).state.value)
        }
    }

    @Test
    fun newNonNfcIntentIsStoredButIgnored() {
        val intent = Intent().setAction(Intent.ACTION_MAIN)

        withResumedActivity(Intent().setAction(NfcAdapter.ACTION_TAG_DISCOVERED)) { controller ->
            controller.newIntent(intent)

            assertEquals(intent.action, controller.get().intent.action)
            assertEquals(ReadStatus.Failure.IntentUnsupported, viewModel(controller.get()).state.value)
        }
    }

    @Test
    fun restoredActivityDoesNotReprocessLaunchIntent() {
        withResumedActivity(
            Intent().setAction(NfcAdapter.ACTION_TAG_DISCOVERED),
            Bundle(),
        ) { controller ->
            assertEquals(ReadStatus.Idle, viewModel(controller.get()).state.value)
        }
    }

    private fun assertColdStartNfcAction(action: String) {
        withResumedActivity(Intent().setAction(action)) { controller ->
            assertEquals(ReadStatus.Failure.IntentUnsupported, viewModel(controller.get()).state.value)
        }
    }

    private fun withResumedActivity(
        intent: Intent,
        savedInstanceState: Bundle? = null,
        assertion: (ActivityController<NfcReadActivity>) -> Unit,
    ) {
        prepareNfcEnvironment()
        val controller = Robolectric.buildActivity(NfcReadActivity::class.java, intent)
        try {
            if (savedInstanceState == null) controller.create() else controller.create(savedInstanceState)
            controller.start().resume()
            assertion(controller)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun prepareNfcEnvironment() {
        val application = RuntimeEnvironment.getApplication()
        val packageManager = application.packageManager
        Shadows.shadowOf(packageManager).setSystemFeature(PackageManager.FEATURE_NFC, true)
        assertTrue(
            "Robolectric should expose FEATURE_NFC before Activity creation",
            packageManager.hasSystemFeature(PackageManager.FEATURE_NFC),
        )

        ShadowNfcAdapter.setNfcHardwareExists(true)
        val adapter = NfcAdapter.getDefaultAdapter(application)
        assertNotNull("Robolectric should provide an NFC adapter before Activity creation", adapter)
        val enabledAdapter = requireNotNull(adapter)
        Shadows.shadowOf(enabledAdapter).setEnabled(true)
        assertTrue(
            "Robolectric NFC adapter should be enabled before Activity creation",
            enabledAdapter.isEnabled,
        )
    }

    private fun viewModel(activity: NfcReadActivity) =
        ViewModelProvider(activity)[NfcReadViewModel::class.java]
}
