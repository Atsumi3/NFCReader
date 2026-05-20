package info.nukoneko.android.nfcreader.ui.nfcread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import info.nukoneko.android.nfcreader.R
import info.nukoneko.android.nfcreader.model.entity.NfcEntity
import info.nukoneko.android.nfcreader.model.entity.NfcField
import info.nukoneko.android.nfcreader.model.entity.ReadStatus
import info.nukoneko.android.nfcreader.ui.theme.NfcReaderTheme

@Composable
fun NfcReadScreen(
    state: ReadStatus,
    readTime: String,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (readTime.isNotEmpty()) {
                Text(
                    text = readTime,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (state) {
                    ReadStatus.Idle ->
                        CenteredMessage(stringResource(R.string.please_hold_up_device))

                    ReadStatus.Reading ->
                        CircularProgressIndicator()

                    is ReadStatus.Success ->
                        if (state.entities.isEmpty()) {
                            CenteredMessage(stringResource(R.string.readable_nfc_is_not_found))
                        } else {
                            NfcEntityList(state.entities)
                        }

                    is ReadStatus.Failure ->
                        CenteredMessage(state.toMessage())
                }
            }
        }
    }
}

@Composable
private fun ReadStatus.Failure.toMessage(): String = when (this) {
    ReadStatus.Failure.NfcDisabled ->
        stringResource(R.string.nfc_disabled)

    ReadStatus.Failure.IntentUnsupported ->
        stringResource(R.string.intent_is_not_supported)

    is ReadStatus.Failure.Error ->
        cause.localizedMessage ?: stringResource(R.string.intent_is_not_supported)
}

@Composable
private fun CenteredMessage(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        modifier = modifier.padding(16.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.headlineSmall
    )
}

@Composable
private fun NfcEntityList(entities: List<NfcEntity>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(entities, key = { it.techName }) { entity ->
            NfcEntityCard(entity)
        }
    }
}

@Composable
private fun NfcEntityCard(entity: NfcEntity, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = entity.shortName,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(4.dp))
            for (field in entity.fields) {
                Text(
                    text = "◇ ${field.label}",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = field.value ?: "-",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = entity.techName,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NfcReadScreenPreview() {
    NfcReaderTheme {
        NfcReadScreen(
            state = ReadStatus.Success(
                listOf(
                    NfcEntity(
                        techName = "android.nfc.tech.NfcA",
                        fields = listOf(
                            NfcField("Tag.Id", "04 1A 2B 3C 5D 6E 70"),
                            NfcField("Sak", "0008"),
                            NfcField("Atqa", "44 00")
                        )
                    )
                )
            ),
            readTime = "2026-05-20 12:34:56.789"
        )
    }
}
