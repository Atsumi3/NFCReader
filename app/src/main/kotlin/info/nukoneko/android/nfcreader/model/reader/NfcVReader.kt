package info.nukoneko.android.nfcreader.model.reader

import android.nfc.TagLostException
import android.nfc.tech.NfcV
import android.nfc.tech.TagTechnology
import info.nukoneko.android.nfcreader.extensions.toHexString
import info.nukoneko.android.nfcreader.model.entity.NfcField
import info.nukoneko.android.nfcreader.model.entity.ReadCoverage
import info.nukoneko.android.nfcreader.model.entity.ReadIssue
import info.nukoneko.android.nfcreader.model.entity.ReadIssueKind
import info.nukoneko.android.nfcreader.model.entity.ReadRange
import info.nukoneko.android.nfcreader.model.entity.UnreadRange
import java.io.IOException
import java.util.concurrent.CancellationException

class NfcVReader : TechReader {
    override val techName: String = "android.nfc.tech.NfcV"

    override fun read(tech: TagTechnology, context: TechReadContext) {
        val nfcV = tech as NfcV
        readWithTransport(
            uid = nfcV.tag.id.copyOf(),
            maxTransceiveLength = nfcV.maxTransceiveLength,
            transport = ReadOnlyTransport { command -> nfcV.transceive(command) },
            context = context,
        )
    }

    internal fun readWithTransport(
        uid: ByteArray,
        maxTransceiveLength: Int,
        transport: ReadOnlyTransport,
        context: TechReadContext,
    ) {
        val boundedTransport = context.budgetedTransport(transport, "NFC-V")
        if (uid.size != UID_SIZE) {
            context.markUnsupported(
                ReadIssue(
                    ReadIssueKind.UNSUPPORTED_TECHNOLOGY,
                    "NFC-V UID is not the expected 8-byte form; no addressed command was sent.",
                )
            )
            return
        }
        context.addMetadata(
            listOf(
                NfcField("NfcV.uid.transceiveOrder", uid.toHexString()),
                NfcField("NfcV.uidOrderNote", "Android Tag.id bytes are used unchanged; RF hardware behavior is device-dependent."),
                NfcField("NfcV.maxTransceiveLength", maxTransceiveLength.toString()),
            )
        )

        var systemInfo = readSystemInfo(maxTransceiveLength, boundedTransport, uid, extended = false, context = context)
        if (context.buildResult().issues.any { it.kind == ReadIssueKind.TAG_LOST }) {
            context.setCoverage(ReadCoverage.unknown("NFC-V System Information"))
            context.markPartial()
            return
        }
        if (systemInfo?.blockCount == null || systemInfo.blockCount == MAX_STANDARD_BLOCKS) {
            val extendedInfo = readSystemInfo(maxTransceiveLength, boundedTransport, uid, extended = true, context = context)
            if (extendedInfo?.blockCount != null) systemInfo = extendedInfo
        }
        if (context.buildResult().issues.any { it.kind == ReadIssueKind.TAG_LOST }) {
            context.setCoverage(ReadCoverage.unknown("NFC-V System Information"))
            context.markPartial()
            return
        }
        systemInfo?.let { info ->
            context.addMetadata(
                listOfNotNull(
                    info.dsfid?.let { NfcField("NfcV.DSFID", "0x%02X".format(it)) },
                    info.afi?.let { NfcField("NfcV.AFI", "0x%02X".format(it)) },
                    info.blockCount?.let { NfcField("NfcV.reportedBlockCount", it.toString()) },
                    info.blockSize?.let { NfcField("NfcV.reportedBlockSizeBytes", it.toString()) },
                    info.icReference?.let { NfcField("NfcV.ICReference", "0x%02X".format(it)) },
                    info.warning?.let { NfcField("NfcV.systemInformationWarning", it) },
                )
            )
            if (info.warning != null) {
                context.addIssue(ReadIssue(ReadIssueKind.MALFORMED_RESPONSE, info.warning, "System Information"))
            }
        }

        val knownBlockCount = systemInfo?.blockCount
        val scanLimit = minOf(knownBlockCount ?: MAX_UNKNOWN_BLOCKS, MAX_BLOCKS_PER_TECH)
        val completedBlocks = mutableListOf<ReadRange>()
        var unreadStart: Int? = null
        var stopReason: String? = null
        for (block in 0 until scanLimit) {
            context.checkpoint()
            val command = buildNfcVReadSingleBlock(uid, block)
            if (command == null || command.size > maxTransceiveLength) {
                unreadStart = block
                stopReason = "Read request exceeds Android's reported transmit limit."
                context.addIssue(ReadIssue(ReadIssueKind.UNSUPPORTED_TECHNOLOGY, stopReason, "block $block"))
                break
            }
            context.captureRaw("NFC-V READ SINGLE BLOCK request", command, "block $block")
            val response = try {
                boundedTransport.transceiveReadOnly(command)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: ReadBudgetExceededException) {
                throw exception
            } catch (exception: Exception) {
                val tagLost = exception is TagLostException
                val kind = when {
                    tagLost -> ReadIssueKind.TAG_LOST
                    exception is IOException -> ReadIssueKind.IO_ERROR
                    else -> ReadIssueKind.OTHER
                }
                unreadStart = block
                stopReason = if (tagLost) "Tag left the NFC field." else "NFC-V block read failed; remaining extent is unknown."
                context.addIssue(ReadIssue(kind, stopReason, "block $block"))
                break
            }
            context.captureRaw("NFC-V READ SINGLE BLOCK response", response, "block $block")
            if (response.isEmpty()) {
                unreadStart = block
                stopReason = "NFC-V response is empty."
                context.addIssue(ReadIssue(ReadIssueKind.MALFORMED_RESPONSE, stopReason, "block $block"))
                break
            }
            val responseFlags = response[0].toInt() and 0xFF
            if (responseFlags and ERROR_FLAG != 0) {
                val errorCode = response.getOrNull(1)?.toInt()?.and(0xFF)
                if (errorCode == PROTECTED_ERROR_CODE) {
                    context.addIssue(
                        ReadIssue(
                            ReadIssueKind.AUTHENTICATION_REQUIRED,
                            "Block access was denied by the tag; no authentication attempt was made.",
                            "block $block",
                        )
                    )
                    context.addUnreadRange(
                        UnreadRange("NFC-V blocks", "block", block.toLong(), block + 1L, "Tag reported a protected/unavailable block.")
                    )
                    continue
                }
                unreadStart = block
                stopReason = if (errorCode == BLOCK_NOT_AVAILABLE_ERROR_CODE) {
                    "Tag reported a block boundary candidate; total capacity is not proven."
                } else {
                    "Tag returned an NFC-V error response${errorCode?.let { " 0x%02X".format(it) } ?: ""}."
                }
                context.addIssue(
                    ReadIssue(
                        if (errorCode == BLOCK_NOT_AVAILABLE_ERROR_CODE) ReadIssueKind.COVERAGE_UNKNOWN else ReadIssueKind.IO_ERROR,
                        stopReason,
                        "block $block",
                    )
                )
                break
            }
            val data = response.copyOfRange(1, response.size)
            val expectedBlockSize = systemInfo?.blockSize
            if (data.isEmpty() || (expectedBlockSize != null && data.size != expectedBlockSize)) {
                unreadStart = block
                stopReason = "Block response length does not match the reported block size."
                context.addIssue(ReadIssue(ReadIssueKind.MALFORMED_RESPONSE, stopReason, "block $block"))
                break
            }
            context.addDecodedFields(listOf(NfcField("NfcV.block[$block]", data.toHexString())))
            completedBlocks += ReadRange(block.toLong(), block + 1L)
        }

        val expectedEnd = knownBlockCount?.toLong() ?: scanLimit.toLong()
        if (unreadStart != null) {
            context.addUnreadRange(
                UnreadRange(
                    scope = "NFC-V block range",
                    unit = "block",
                    startInclusive = unreadStart.toLong(),
                    endExclusive = expectedEnd,
                    reason = stopReason ?: "Read was not completed.",
                )
            )
        }
        if (knownBlockCount == null) {
            context.addIssue(
                ReadIssue(
                    ReadIssueKind.COVERAGE_UNKNOWN,
                    "No validated capacity was returned; the bounded scan is not a complete tag dump.",
                    "NFC-V capacity",
                )
            )
        } else if (knownBlockCount > MAX_BLOCKS_PER_TECH) {
            context.addUnreadRange(
                UnreadRange(
                    scope = "NFC-V block range",
                    unit = "block",
                    startInclusive = MAX_BLOCKS_PER_TECH.toLong(),
                    endExclusive = knownBlockCount.toLong(),
                    reason = "Application read limit reached.",
                )
            )
        }
        val coverageEnd = knownBlockCount?.coerceAtMost(MAX_BLOCKS_PER_TECH)?.toLong()
            ?: scanLimit.toLong()
        context.setCoverage(
            ReadCoverage(
                scope = if (knownBlockCount == null) "NFC-V bounded block scan (capacity unknown)" else "NFC-V reported user-memory blocks",
                unit = "block",
                expectedRange = ReadRange(0, coverageEnd),
                completedRanges = completedBlocks,
            )
        )
        if (knownBlockCount != null && knownBlockCount <= MAX_BLOCKS_PER_TECH &&
            completedBlocks.size == knownBlockCount && context.buildResult().issues.isEmpty()
        ) {
            context.markComplete()
        } else {
            context.markPartial()
        }
    }

    private fun readSystemInfo(
        maxTransceiveLength: Int,
        transport: ReadOnlyTransport,
        uid: ByteArray,
        extended: Boolean,
        context: TechReadContext,
    ): NfcVSystemInfo? {
        val command = buildNfcVGetSystemInformation(uid, extended) ?: return null
        if (command.size > maxTransceiveLength) return null
        val label = if (extended) "GET EXTENDED SYSTEM INFORMATION 3B" else "GET SYSTEM INFORMATION 2B"
        context.captureRaw("NFC-V $label request", command, label)
        val response = try {
            transport.transceiveReadOnly(command)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: ReadBudgetExceededException) {
            throw exception
        } catch (exception: Exception) {
            if (exception is TagLostException) {
                context.addIssue(ReadIssue(ReadIssueKind.TAG_LOST, "Tag left the NFC field during System Information.", label))
            } else if (!extended) {
                context.addIssue(ReadIssue(ReadIssueKind.IO_ERROR, "System Information request failed; capacity remains unknown.", label))
            }
            return null
        }
        context.captureRaw("NFC-V $label response", response, label)
        val parsed = parseNfcVSystemInformation(response, uid, extended)
        if (parsed.error != null) {
            if (!extended || parsed.error.contains("UID does not match")) {
                context.addIssue(ReadIssue(ReadIssueKind.MALFORMED_RESPONSE, parsed.error, label))
            }
            return null
        }
        return parsed.systemInfo
    }

    private companion object {
        const val UID_SIZE = 8
        const val ERROR_FLAG = 0x01
        const val PROTECTED_ERROR_CODE = 0x15
        const val BLOCK_NOT_AVAILABLE_ERROR_CODE = 0x10
        const val MAX_STANDARD_BLOCKS = 256
        const val MAX_UNKNOWN_BLOCKS = MAX_STANDARD_BLOCKS
        const val MAX_BLOCKS_PER_TECH = 4094
    }
}
