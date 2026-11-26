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
    override val techName: String = "android.nfc.tech.MifareClassic"

    override fun read(tech: TagTechnology, context: TechReadContext) {
        val mfc = tech as MifareClassic
        val totalBlocks = (0 until mfc.sectorCount).sumOf(mfc::getBlockCountInSector)
        val completedBlocks = mutableListOf<ReadRange>()
        sectorLoop@ for (sector in 0 until mfc.sectorCount) {
            context.checkpoint()
            val firstBlock = mfc.sectorToBlock(sector)
            val blockCount = mfc.getBlockCountInSector(sector)
            val key = try {
                DEFAULT_KEYS.firstOrNull { authenticate(mfc, sector, it.first, context) }
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
                continue
            }
            if (key == null) {
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
                continue
            }
            context.addDecodedFields(listOf(NfcField("Classic.sector[$sector].authentication", "existing built-in candidate ${key.second} accepted")))
            for (block in firstBlock until firstBlock + blockCount) {
                context.checkpoint()
                val data = try {
                    context.reserveTransceive("MIFARE Classic block read")
                    mfc.readBlock(block)
                } catch (exception: ReadBudgetExceededException) {
                    throw exception
                } catch (exception: Exception) {
                    recordIoFailure(context, exception, "block $block")
                    if (exception is TagLostException) break@sectorLoop
                    var recovered: ByteArray? = null
                    for ((candidate, candidateName) in DEFAULT_KEYS) {
                        context.checkpoint()
                        try {
                            context.reserveTransceive("MIFARE Classic Key B authentication")
                            if (mfc.authenticateSectorWithKeyB(sector, candidate)) {
                                context.reserveTransceive("MIFARE Classic block read")
                                recovered = mfc.readBlock(block)
                                if (recovered.size == 16) {
                                    context.addDecodedFields(listOf(NfcField("Classic.block[$block].recoveredWith", "Key B / $candidateName")))
                                    break
                                }
                            }
                        } catch (retryException: ReadBudgetExceededException) {
                            throw retryException
                        } catch (retryException: Exception) {
                            recordIoFailure(context, retryException, "block $block Key B $candidateName")
                            if (retryException is TagLostException) break@sectorLoop
                        }
                    }
                    if (recovered == null || recovered.size != 16) {
                        context.addUnreadRange(UnreadRange("MIFARE Classic blocks", "block", block.toLong(), (block + 1).toLong(), "Read failed with the existing Key B candidates."))
                        continue
                    }
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
            }
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

    private fun authenticate(mfc: MifareClassic, sector: Int, key: ByteArray, context: TechReadContext): Boolean {
        context.reserveTransceive("MIFARE Classic Key A authentication")
        if (mfc.authenticateSectorWithKeyA(sector, key)) return true
        context.reserveTransceive("MIFARE Classic Key B authentication")
        return mfc.authenticateSectorWithKeyB(sector, key)
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
