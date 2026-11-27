package info.nukoneko.android.nfcreader.model.reader

import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.TagTechnology
import android.nfc.TagLostException
import info.nukoneko.android.nfcreader.extensions.toHexString
import info.nukoneko.android.nfcreader.model.entity.NfcField
import info.nukoneko.android.nfcreader.model.entity.ReadCoverage
import info.nukoneko.android.nfcreader.model.entity.ReadIssue
import info.nukoneko.android.nfcreader.model.entity.ReadIssueKind
import info.nukoneko.android.nfcreader.model.entity.ReadOutcome
import info.nukoneko.android.nfcreader.model.entity.ReadRange
import info.nukoneko.android.nfcreader.model.entity.UnreadRange
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Dumps every readable page of a MIFARE Ultralight / NTAG tag. */
class MifareUltralightReader : TechReader {
    override val techName: String = "android.nfc.tech.MifareUltralight"

    override fun read(tech: TagTechnology, context: TechReadContext) {
        val mful = tech as MifareUltralight
        context.addMetadata(
            listOf(NfcField("Ultralight.typeHint", mful.type.toString()))
        )
        MifareUltralightProtocol.read(
            context.budgetedTransport(ReadOnlyTransport { command -> mful.transceive(command) }, "MIFARE Ultralight"),
            context,
        )
    }
}

/** Dumps MIFARE Classic sectors that open with a well-known default key. */
class MifareClassicReader : TechReader {
    private enum class KeyType(val label: String) {
        A("Key A"),
        B("Key B"),
    }

    private data class Authentication(
        val key: ByteArray,
        val type: KeyType,
        val candidateName: String,
    )

    override val techName: String = "android.nfc.tech.MifareClassic"

    override fun read(tech: TagTechnology, context: TechReadContext) {
        val mfc = tech as MifareClassic
        readWithTransport(
            object : MifareClassicTransport {
                override val sectorCount: Int get() = mfc.sectorCount
                override fun sectorToBlock(sector: Int): Int = mfc.sectorToBlock(sector)
                override fun blockCountInSector(sector: Int): Int = mfc.getBlockCountInSector(sector)
                override fun authenticateKeyA(sector: Int, key: ByteArray): Boolean = mfc.authenticateSectorWithKeyA(sector, key)
                override fun authenticateKeyB(sector: Int, key: ByteArray): Boolean = mfc.authenticateSectorWithKeyB(sector, key)
                override fun readBlock(block: Int): ByteArray = mfc.readBlock(block)
            },
            context,
        )
    }

    internal fun readWithTransport(mfc: MifareClassicTransport, context: TechReadContext) {
        val totalBlocks = (0 until mfc.sectorCount).sumOf(mfc::blockCountInSector)
        val completedBlocks = mutableListOf<ReadRange>()
        var nextBlock = 0
        try {
        sectorLoop@ for (sector in 0 until mfc.sectorCount) {
            val firstBlock = mfc.sectorToBlock(sector)
            val blockCount = mfc.blockCountInSector(sector)
            nextBlock = firstBlock
            context.checkpoint()
            val authentication = try {
                authenticateAnyDefaultKey(mfc, sector, context)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: ReadBudgetExceededException) {
                throw exception
            } catch (exception: Exception) {
                recordIoFailure(context, exception, "sector $sector")
                context.addUnreadRange(
                    UnreadRange(
                        scope = "MIFARE Classic blocks",
                        unit = "block",
                        startInclusive = firstBlock.toLong(),
                        endExclusive = (firstBlock + blockCount).toLong(),
                        reason = "Sector access result is unknown because authentication I/O failed.",
                    )
                )
                if (exception is TagLostException) break@sectorLoop
                nextBlock = firstBlock + blockCount
                continue
            }
            if (authentication == null) {
                context.addDecodedFields(listOf(NfcField("Classic.sector[$sector]", "既存の既定鍵では認証できません")))
                context.addIssue(
                    ReadIssue(
                        kind = ReadIssueKind.AUTHENTICATION_REQUIRED,
                        message = "Sector was not read; no additional key candidates or bypass are attempted.",
                        operation = "sector $sector",
                    )
                )
                context.addUnreadRange(
                    UnreadRange(
                        scope = "MIFARE Classic blocks",
                        unit = "block",
                        startInclusive = firstBlock.toLong(),
                        endExclusive = (firstBlock + blockCount).toLong(),
                        reason = "Existing default-key authentication did not grant access.",
                    )
                )
                nextBlock = firstBlock + blockCount
                continue
            }
            context.addDecodedFields(listOf(NfcField("Classic.sector[$sector].authentication", "${authentication.type.label} / existing built-in candidate ${authentication.candidateName} accepted")))
            var activeAuthentication = authentication
            for (block in firstBlock until firstBlock + blockCount) {
                nextBlock = block
                context.checkpoint()
                val data = try {
                    context.reserveTransceive("MIFARE Classic block read")
                    mfc.readBlock(block)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: ReadBudgetExceededException) {
                    throw exception
                } catch (exception: Exception) {
                    recordIoFailure(context, exception, "block $block")
                    if (exception is TagLostException) {
                        context.addUnreadRange(UnreadRange("MIFARE Classic blocks", "block", block.toLong(), totalBlocks.toLong(), "Tag was lost before remaining blocks were read."))
                        break@sectorLoop
                    }
                    var recovered: ByteArray? = null
                    var recoveredAuthentication: Authentication? = null
                    for ((candidate, candidateName) in DEFAULT_KEYS) {
                        context.checkpoint()
                        try {
                            context.reserveTransceive("MIFARE Classic Key B authentication")
                            if (mfc.authenticateKeyB(sector, candidate)) {
                                context.reserveTransceive("MIFARE Classic block read")
                                recovered = mfc.readBlock(block)
                                if (recovered.size == 16) {
                                    recoveredAuthentication = Authentication(candidate, KeyType.B, candidateName)
                                    context.addDecodedFields(listOf(NfcField("Classic.block[$block].recoveredWith", "Key B / $candidateName")))
                                    break
                                }
                            }
                        } catch (retryException: CancellationException) {
                            throw retryException
                        } catch (retryException: ReadBudgetExceededException) {
                            throw retryException
                        } catch (retryException: Exception) {
                            recordIoFailure(context, retryException, "block $block Key B $candidateName")
                            if (retryException is TagLostException) {
                                context.addUnreadRange(UnreadRange("MIFARE Classic blocks", "block", block.toLong(), totalBlocks.toLong(), "Tag was lost before remaining blocks were read."))
                                break@sectorLoop
                            }
                        }
                    }
                    if (recovered == null || recovered.size != 16) {
                        context.addUnreadRange(UnreadRange("MIFARE Classic blocks", "block", block.toLong(), (block + 1).toLong(), "Read failed with the existing Key B candidates."))
                        val restored = try {
                            authenticate(mfc, sector, activeAuthentication, context)
                        } catch (restoreException: CancellationException) {
                            throw restoreException
                        } catch (restoreException: ReadBudgetExceededException) {
                            throw restoreException
                        } catch (restoreException: Exception) {
                            recordIoFailure(context, restoreException, "sector $sector authentication restore")
                            false
                        }
                        if (!restored) {
                            context.addIssue(
                                ReadIssue(
                                    ReadIssueKind.AUTHENTICATION_REQUIRED,
                                    "The successful sector authentication could not be restored; remaining blocks were not requested.",
                                    "sector $sector",
                                )
                            )
                            val sectorEnd = firstBlock + blockCount
                            if (block + 1 < sectorEnd) {
                                context.addUnreadRange(
                                    UnreadRange(
                                        "MIFARE Classic blocks",
                                        "block",
                                        (block + 1).toLong(),
                                        sectorEnd.toLong(),
                                        "The original successful authentication was not restored.",
                                    )
                                )
                            }
                            nextBlock = sectorEnd
                            break
                        }
                        nextBlock = block + 1
                        continue
                    }
                    activeAuthentication = recoveredAuthentication ?: activeAuthentication
                    recovered
                }
                val isTrailer = block == firstBlock + blockCount - 1
                if (data.size != 16) {
                    context.addIssue(
                        ReadIssue(
                            kind = ReadIssueKind.MALFORMED_RESPONSE,
                            message = "Expected a 16-byte Classic block; received ${data.size} bytes.",
                            operation = "block $block",
                        )
                    )
                    context.addUnreadRange(
                        UnreadRange("MIFARE Classic blocks", "block", block.toLong(), (block + 1).toLong(), "Malformed block length.")
                    )
                    nextBlock = block + 1
                    continue
                }
                context.captureRaw(if (isTrailer) "Classic sector trailer as returned by Android" else "Classic block", data, "block $block")
                context.addDecodedFields(
                    listOf(
                        NfcField(
                            "Classic.block[$block]",
                            data.toHexString(),
                        )
                    )
                )
                if (isTrailer) {
                    context.addDecodedFields(
                        listOf(NfcField("Classic.block[$block].layout", "Bytes 0-5 Key A; 6-8 access bits; 9 GPB; 10-15 Key B or readable data according to access bits. Android may mask key bytes in its response."))
                    )
                }
                completedBlocks += ReadRange(block.toLong(), block + 1L)
                nextBlock = block + 1
            }
            nextBlock = firstBlock + blockCount
        }
        } catch (cancelled: CancellationException) {
            if (nextBlock < totalBlocks) {
                context.addUnreadRange(UnreadRange("MIFARE Classic blocks", "block", nextBlock.toLong(), totalBlocks.toLong(), "Read cancelled; remaining blocks were not requested."))
            }
            context.setCoverage(
                ReadCoverage(
                    scope = "MIFARE Classic blocks as returned by Android",
                    unit = "block",
                    expectedRange = ReadRange(0, totalBlocks.toLong()),
                    completedRanges = completedBlocks,
                )
            )
            throw cancelled
        }
        context.setCoverage(
            ReadCoverage(
                scope = "MIFARE Classic blocks as returned by Android",
                unit = "block",
                expectedRange = ReadRange(0, totalBlocks.toLong()),
                completedRanges = completedBlocks,
            )
        )
        if (completedBlocks.size == totalBlocks) context.markComplete() else context.markPartial()
    }

    private fun authenticateAnyDefaultKey(mfc: MifareClassicTransport, sector: Int, context: TechReadContext): Authentication? {
        for ((key, candidateName) in DEFAULT_KEYS) {
            val keyA = Authentication(key, KeyType.A, candidateName)
            if (authenticate(mfc, sector, keyA, context)) return keyA
            val keyB = Authentication(key, KeyType.B, candidateName)
            if (authenticate(mfc, sector, keyB, context)) return keyB
        }
        return null
    }

    private fun authenticate(mfc: MifareClassicTransport, sector: Int, authentication: Authentication, context: TechReadContext): Boolean {
        return when (authentication.type) {
            KeyType.A -> {
                context.reserveTransceive("MIFARE Classic Key A authentication")
                mfc.authenticateKeyA(sector, authentication.key)
            }
            KeyType.B -> {
                context.reserveTransceive("MIFARE Classic Key B authentication")
                mfc.authenticateKeyB(sector, authentication.key)
            }
        }
    }

    private fun recordIoFailure(context: TechReadContext, exception: Exception, operation: String) {
        val tagLost = exception is TagLostException
        context.addIssue(
            ReadIssue(
                kind = if (tagLost) ReadIssueKind.TAG_LOST else ReadIssueKind.IO_ERROR,
                message = if (tagLost) "Tag left the NFC field during Classic reading." else "Classic I/O failed; unread blocks are retained as unknown.",
                operation = operation,
            )
        )
        context.markPartial()
    }

    private companion object {
        val DEFAULT_KEYS: List<Pair<ByteArray, String>> = listOf(
            MifareClassic.KEY_DEFAULT to "KEY_DEFAULT",
            MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY to "KEY_MAD",
            MifareClassic.KEY_NFC_FORUM to "KEY_NFC_FORUM",
            ByteArray(6) to "ZERO",
        )
    }
}

internal interface MifareClassicTransport {
    val sectorCount: Int
    fun sectorToBlock(sector: Int): Int
    fun blockCountInSector(sector: Int): Int
    fun authenticateKeyA(sector: Int, key: ByteArray): Boolean
    fun authenticateKeyB(sector: Int, key: ByteArray): Boolean
    fun readBlock(block: Int): ByteArray
}
