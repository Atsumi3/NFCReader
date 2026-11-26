package info.nukoneko.android.nfcreader.model.reader

import android.nfc.TagLostException
import info.nukoneko.android.nfcreader.extensions.toHexString
import info.nukoneko.android.nfcreader.model.entity.NfcField
import info.nukoneko.android.nfcreader.model.entity.ReadCoverage
import info.nukoneko.android.nfcreader.model.entity.ReadIssue
import info.nukoneko.android.nfcreader.model.entity.ReadIssueKind
import info.nukoneko.android.nfcreader.model.entity.ReadRange
import info.nukoneko.android.nfcreader.model.entity.UnreadRange
import java.io.IOException
import kotlinx.coroutines.CancellationException

internal data class UltralightVersionProfile(
    val name: String,
    val lastPage: Int,
    val version: ByteArray,
)

internal object MifareUltralightProtocol {
    const val MAX_UNKNOWN_PAGES = 256
    const val MAX_FAST_READ_PAGES = 4
    const val PAGE_SIZE = 4
    const val READ_RESPONSE_SIZE = 16

    private val profiles = listOf(
        UltralightVersionProfile("NTAG213", 0x2C, byteArrayOf(0, 4, 4, 2, 1, 0, 0x0F, 3)),
        UltralightVersionProfile("NTAG215", 0x86, byteArrayOf(0, 4, 4, 2, 1, 0, 0x11, 3)),
        UltralightVersionProfile("NTAG216", 0xE6, byteArrayOf(0, 4, 4, 2, 1, 0, 0x13, 3)),
        UltralightVersionProfile("Ultralight EV1 MF0UL11", 0x13, byteArrayOf(0, 4, 3, 1, 1, 0, 0x0B, 3)),
        UltralightVersionProfile("Ultralight EV1 MF0UL21", 0x28, byteArrayOf(0, 4, 3, 2, 1, 0, 0x0E, 3)),
    )

    fun getVersionCommand(): ByteArray = byteArrayOf(0x60)

    fun fastReadCommand(startPage: Int, endPage: Int): ByteArray {
        require(startPage in 0..0xFF && endPage in startPage..0xFF)
        return byteArrayOf(0x3A, startPage.toByte(), endPage.toByte())
    }

    fun readCommand(page: Int): ByteArray {
        require(page in 0..0xFF)
        return byteArrayOf(0x30, page.toByte())
    }

    fun parseVersion(response: ByteArray): UltralightVersionProfile? =
        profiles.singleOrNull { it.version.contentEquals(response) }

    fun read(transport: ReadOnlyTransport, context: TechReadContext) {
        val versionRequest = getVersionCommand()
        context.captureRaw("MIFARE GET_VERSION request", versionRequest)
        val version = try {
            context.checkpoint()
            transport.transceiveReadOnly(versionRequest)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: ReadBudgetExceededException) {
            throw exception
        } catch (exception: Exception) {
            context.addIssue(issueFor(exception, "GET_VERSION"))
            if (exception is TagLostException) {
                context.setCoverage(ReadCoverage.unknown("MIFARE Ultralight GET_VERSION"))
                context.markPartial()
                return
            }
            null
        }
        val profile = version?.also { context.captureRaw("MIFARE GET_VERSION response", it) }?.let(::parseVersion)
        if (version != null && version.size != 8) {
            context.addIssue(ReadIssue(ReadIssueKind.MALFORMED_RESPONSE, "GET_VERSION must return exactly eight bytes; preserved as unknown profile.", "GET_VERSION"))
        }
        if (profile == null) {
            context.addMetadata(listOf(NfcField("Ultralight.versionProfile", "unknown; bounded READ fallback")))
            context.addIssue(ReadIssue(ReadIssueKind.COVERAGE_UNKNOWN, "No exact known eight-byte GET_VERSION profile matched; capacity is not inferred.", "MIFARE Ultralight"))
            readUnknownCapacity(transport, context)
        } else {
            context.addMetadata(listOf(NfcField("Ultralight.versionProfile", profile.name)))
            readKnownProfile(transport, context, profile)
        }
    }

    private fun readKnownProfile(transport: ReadOnlyTransport, context: TechReadContext, profile: UltralightVersionProfile) {
        val pageCount = profile.lastPage + 1
        val completed = mutableListOf<ReadRange>()
        var page = 0
        var stoppedAt: Int? = null
        var fastReadUsedFallback = false
        while (page < pageCount) {
            context.checkpoint()
            val end = minOf(page + MAX_FAST_READ_PAGES - 1, profile.lastPage)
            val request = fastReadCommand(page, end)
            context.captureRaw("MIFARE FAST_READ request", request, "pages $page..$end")
            val response = try {
                transport.transceiveReadOnly(request)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: ReadBudgetExceededException) {
                throw exception
            } catch (exception: Exception) {
                if (exception is TagLostException) {
                    context.addIssue(issueFor(exception, "FAST_READ pages $page..$end"))
                    stoppedAt = page
                    break
                }
                fastReadUsedFallback = true
                context.addDecodedFields(listOf(NfcField("Ultralight.FAST_READ.pages[$page..$end]", "exchange failed; retried with single-page READ")))
                null
            }
            val expectedSize = (end - page + 1) * PAGE_SIZE
            if (response != null) context.captureRaw("MIFARE FAST_READ response", response, "pages $page..$end")
            if (response != null && response.size == expectedSize) {
                for (offset in 0..(end - page)) {
                    val currentPage = page + offset
                    val bytes = response.copyOfRange(offset * PAGE_SIZE, (offset + 1) * PAGE_SIZE)
                    recordPage(context, currentPage, bytes)
                    completed += ReadRange(currentPage.toLong(), currentPage + 1L)
                }
                page = end + 1
                continue
            }
            if (response != null) {
                fastReadUsedFallback = true
                context.addDecodedFields(listOf(NfcField("Ultralight.FAST_READ.pages[$page..$end]", "invalid response length %d; retried with single-page READ".format(response.size))))
            }
            for (fallbackPage in page..end) {
                val bytes = readSinglePage(transport, context, fallbackPage)
                if (bytes == null) {
                    stoppedAt = fallbackPage
                    break
                }
                recordPage(context, fallbackPage, bytes)
                completed += ReadRange(fallbackPage.toLong(), fallbackPage + 1L)
            }
            if (stoppedAt != null) break
            page = end + 1
        }
        context.setCoverage(
            ReadCoverage(
                scope = "%s pages 0..%d".format(profile.name, profile.lastPage),
                unit = "page",
                expectedRange = ReadRange(0, pageCount.toLong()),
                completedRanges = completed,
            )
        )
        if (fastReadUsedFallback) {
            context.addDecodedFields(listOf(NfcField("Ultralight.readMethod", "FAST_READ chunks of at most four pages; failed chunks used single-page READ")))
        }
        if (stoppedAt != null) {
            context.addUnreadRange(UnreadRange("%s pages".format(profile.name), "page", stoppedAt.toLong(), pageCount.toLong(), "FAST_READ and single-page READ did not obtain this page."))
            context.markPartial()
        } else {
            context.markComplete()
        }
    }

    private fun readUnknownCapacity(transport: ReadOnlyTransport, context: TechReadContext) {
        val completed = mutableListOf<ReadRange>()
        var stoppedAt: Int? = null
        for (page in 0 until MAX_UNKNOWN_PAGES) {
            context.checkpoint()
            val bytes = readSinglePage(transport, context, page)
            if (bytes == null) {
                stoppedAt = page
                break
            }
            recordPage(context, page, bytes)
            completed += ReadRange(page.toLong(), page + 1L)
        }
        if (stoppedAt != null) {
            context.addUnreadRange(UnreadRange("Unknown MIFARE Ultralight pages", "page", stoppedAt.toLong(), MAX_UNKNOWN_PAGES.toLong(), "Bounded READ stopped at the first unsuccessful page; capacity remains unknown."))
        } else {
            context.addIssue(ReadIssue(ReadIssueKind.COVERAGE_UNKNOWN, "The bounded 256-page READ scan ended; physical capacity remains unknown."))
        }
        context.setCoverage(ReadCoverage("Unknown-capacity MIFARE Ultralight bounded READ", "page", ReadRange(0, MAX_UNKNOWN_PAGES.toLong()), completed))
        context.markPartial()
    }

    private fun readSinglePage(transport: ReadOnlyTransport, context: TechReadContext, page: Int): ByteArray? {
        context.checkpoint()
        val request = readCommand(page)
        context.captureRaw("MIFARE READ request", request, "page $page")
        val response = try {
            transport.transceiveReadOnly(request)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: ReadBudgetExceededException) {
            throw exception
        } catch (exception: Exception) {
            context.addIssue(issueFor(exception, "READ page $page"))
            return null
        }
        context.captureRaw("MIFARE READ response (four-page response)", response, "requested page $page; only first page attributed")
        if (response.size != READ_RESPONSE_SIZE) {
            context.addIssue(ReadIssue(ReadIssueKind.MALFORMED_RESPONSE, "READ expected 16 response bytes; received %d.".format(response.size), "page $page"))
            return null
        }
        return response.copyOfRange(0, PAGE_SIZE)
    }

    private fun recordPage(context: TechReadContext, page: Int, bytes: ByteArray) {
        context.captureRaw("MIFARE page", bytes, "page $page")
        context.addDecodedFields(listOf(NfcField("Ultralight.page[$page]", bytes.toHexString())))
    }

    private fun issueFor(exception: Exception, operation: String) = ReadIssue(
        kind = when (exception) {
            is TagLostException -> ReadIssueKind.TAG_LOST
            is IOException -> ReadIssueKind.IO_ERROR
            else -> ReadIssueKind.OTHER
        },
        message = if (exception is TagLostException) "Tag left the NFC field." else "MIFARE Ultralight " + operation + " failed: " + (exception::class.simpleName ?: "Exception") + ".",
        operation = operation,
    )
}
