package info.nukoneko.android.nfcreader.ui.nfcread

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

internal object NfcDumpFileExporter {
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    fun writeCacheFile(context: Context, extension: String, content: String): File {
        require(extension == "txt" || extension == "json")
        val directory = File(context.cacheDir, "nfc-dumps")
        check(directory.exists() || directory.mkdirs()) { "Unable to create NFC dump cache directory." }
        return File.createTempFile("nfc-dump-", ".$extension", directory).apply {
            writeText(content, Charsets.UTF_8)
        }
    }

    fun createShareIntent(context: Context, file: File, mimeType: String): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + FILE_PROVIDER_SUFFIX,
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "NFC dump", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
