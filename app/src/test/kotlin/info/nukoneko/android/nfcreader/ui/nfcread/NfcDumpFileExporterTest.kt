package info.nukoneko.android.nfcreader.ui.nfcread

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.core.content.IntentCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
class NfcDumpFileExporterTest {
    @Test
    fun shareUsesFileProviderAttachmentAndReadGrantWithoutExtraText() {
        val context: Context = RuntimeEnvironment.getApplication()
        val content = "{\"capturedAtEpochMillis\":1234}"
        val file = NfcDumpFileExporter.writeCacheFile(context, "json", content)
        val intent = NfcDumpFileExporter.createShareIntent(context, file, "application/json")
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/json", intent.type)
        assertNotNull(uri)
        assertEquals("content", uri?.scheme)
        assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertFalse(intent.hasExtra(Intent.EXTRA_TEXT))
        assertEquals(content, file.readText())
    }

    @Test
    fun pendingSnapshotIsFileBackedRestorableAndCleanedAfterCopyOrCancel() = runBlocking {
        val cacheDir = Files.createTempDirectory("nfc-dump-pending").toFile()
        val handle = SavedStateHandle()
        val viewModel = NfcReadViewModel(handle)
        val content = "sensitive dump ".repeat(4_000)

        assertTrue(viewModel.preparePendingDumpFileExport(cacheDir, content))
        val token = viewModel.pendingDumpFileExportToken()!!
        assertEquals(setOf(NfcReadViewModel.PENDING_EXPORT_TOKEN_KEY), handle.keys())
        val recreated = NfcReadViewModel(SavedStateHandle(mapOf(NfcReadViewModel.PENDING_EXPORT_TOKEN_KEY to token)))
        val pendingFile = NfcDumpFileExporter.pendingExportFile(cacheDir, recreated.pendingDumpFileExportToken()!!)
        assertEquals(content, pendingFile.readText())

        val output = ByteArrayOutputStream()
        NfcDumpFileExporter.copyPendingExportToStream(cacheDir, token, output)
        assertEquals(content, output.toString(Charsets.UTF_8.name()))
        assertTrue(NfcDumpFileExporter.deletePendingExportFile(cacheDir, token))
        assertFalse(pendingFile.exists())

        assertTrue(viewModel.preparePendingDumpFileExport(cacheDir, content))
        val cancelToken = viewModel.pendingDumpFileExportToken()!!
        val cancelFile = NfcDumpFileExporter.pendingExportFile(cacheDir, cancelToken)
        viewModel.discardPendingDumpFileExport(cacheDir, cancelToken)
        assertFalse(cancelFile.exists())
    }
}
