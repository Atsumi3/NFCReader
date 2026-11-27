package info.nukoneko.android.nfcreader.ui.nfcread

import androidx.annotation.StringRes
import info.nukoneko.android.nfcreader.R
import info.nukoneko.android.nfcreader.model.entity.ReadOutcome

@StringRes
internal fun readOutcomeLabelResource(outcome: ReadOutcome): Int = when (outcome) {
    ReadOutcome.COMPLETE -> R.string.outcome_complete
    ReadOutcome.PARTIAL -> R.string.outcome_partial
    ReadOutcome.METADATA_ONLY -> R.string.outcome_metadata_only
    ReadOutcome.UNSUPPORTED -> R.string.outcome_unsupported
    ReadOutcome.FAILED -> R.string.outcome_failed
    ReadOutcome.NOT_ATTEMPTED -> R.string.outcome_not_attempted
}
