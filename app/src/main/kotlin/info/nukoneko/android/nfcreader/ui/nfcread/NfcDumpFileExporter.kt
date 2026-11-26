package info.nukoneko.android.nfcreader.ui.nfcread

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream
import java.util.UUID

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

    fun writePendingExportFile(cacheDir: File, token: String, content: String): File {
        val file = pendingExportFile(cacheDir, token)
        val directory = file.parentFile ?: error("Missing pending export directory.")
        check(directory.exists() || directory.mkdirs()) { "Unable to create NFC dump cache directory." }
        try {
            file.writeText(content, Charsets.UTF_8)
        } catch (exception: Exception) {
            file.delete()
            throw exception
        }
        return file
    }

    fun pendingExportFile(cacheDir: File, token: String): File {
        require(runCatching { UUID.fromString(token).toString() == token }.getOrDefault(false))
        return File(File(cacheDir, "nfc-dumps"), "pending-$token")
    }

    fun copyPendingExportToStream(cacheDir: File, token: String, output: OutputStream) {
        val file = pendingExportFile(cacheDir, token)
        check(file.isFile) { "Pending NFC dump export is missing." }
        file.inputStream().use { input -> input.copyTo(output) }
    }

    fun deletePendingExportFile(cacheDir: File, token: String): Boolean {
        val file = pendingExportFile(cacheDir, token)
        return !file.exists() || file.delete()
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
