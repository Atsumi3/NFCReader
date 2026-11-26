package info.nukoneko.android.nfcreader.ui.nfcread

import android.provider.Settings
import info.nukoneko.android.nfcreader.R
import info.nukoneko.android.nfcreader.model.entity.ReadOutcome
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReadOutcomeLabelsTest {

    @Test
    fun allReadOutcomesHaveUserFacingLabels() {
        val expected = mapOf(
            ReadOutcome.COMPLETE to R.string.outcome_complete,
            ReadOutcome.PARTIAL to R.string.outcome_partial,
            ReadOutcome.METADATA_ONLY to R.string.outcome_metadata_only,
            ReadOutcome.UNSUPPORTED to R.string.outcome_unsupported,
            ReadOutcome.FAILED to R.string.outcome_failed,
            ReadOutcome.NOT_ATTEMPTED to R.string.outcome_not_attempted,
        )

        assertEquals(ReadOutcome.values().toSet(), expected.keys)
        expected.forEach { (outcome, resource) ->
            assertEquals(resource, readOutcomeLabelResource(outcome))
        }
    }

    @Test
    fun nfcSettingsIntentUsesAndroidSystemAction() {
        assertEquals(Settings.ACTION_NFC_SETTINGS, createNfcSettingsIntent().action)
    }
}
