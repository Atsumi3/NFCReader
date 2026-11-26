package info.nukoneko.android.nfcreader.model.reader

import android.nfc.TagLostException
import android.nfc.tech.NfcF
import android.nfc.tech.TagTechnology
import info.nukoneko.android.nfcreader.extensions.toHexString
import info.nukoneko.android.nfcreader.model.entity.NfcField
import info.nukoneko.android.nfcreader.model.entity.ReadCoverage
import info.nukoneko.android.nfcreader.model.entity.ReadIssue
import info.nukoneko.android.nfcreader.model.entity.ReadIssueKind
import info.nukoneko.android.nfcreader.model.entity.ReadRange
import info.nukoneko.android.nfcreader.model.entity.UnreadRange
import java.io.IOException

class FelicaReader : TechReader {
    override val techName: String = "android.nfc.tech.NfcF"

    override fun read(tech: TagTechnology, context: TechReadContext) {
        val nfcF = tech as NfcF
        readWithTransport(
            discoveredSystemCode = nfcF.systemCode,
            maxTransceiveLength = nfcF.maxTransceiveLength,
            transport = ReadOnlyTransport { command -> nfcF.transceive(command) },
            context = context,
        )
    }

    internal fun readWithTransport(
        discoveredSystemCode: ByteArray,
        maxTransceiveLength: Int,
        transport: ReadOnlyTransport,
        context: TechReadContext,
    ) {
        val boundedTransport = context.budgetedTransport(transport, "FeliCa")
        val discoveredSystem = discoveredSystemCode.toSystemCode()
        if (discoveredSystem == null) {
            context.addIssue(
                ReadIssue(
                    ReadIssueKind.COVERAGE_UNKNOWN,
                    "Android did not provide a two-byte discovered FeliCa system code.",
                    "FeliCa system discovery",
                )
            )
            context.markPartial()
            return
        }

        context.addMetadata(
            listOf(
                NfcField("Felica.discoveredSystemCode", "0x%04X".format(discoveredSystem)),
                NfcField("Felica.systemCodeByteOrder", "Big-endian on the command/response boundary"),
            )
        )
        val firstPoll = poll(boundedTransport, maxTransceiveLength, discoveredSystem, context)
        if (firstPoll == null) {
            context.setCoverage(ReadCoverage.unknown("FeliCa system polling"))
            context.markPartial()
            return
        }
        val systemListResponse = try {
            val request = buildRequestSystemCodesCommand(firstPoll.idm)
            if (request.size > maxTransceiveLength) null else {
                context.captureRaw("FeliCa Request System Code request", request, "discovered system")
                boundedTransport.transceiveReadOnly(request).also {
                    context.captureRaw("FeliCa Request System Code response", it, "IDm ${firstPoll.idm.toHexString()}")
                }
            }
        } catch (exception: kotlinx.coroutines.CancellationException) {
            throw exception
        } catch (exception: ReadBudgetExceededException) {
            throw exception
        } catch (exception: Exception) {
            context.addIssue(felicaIssue(exception, "Request System Code"))
            null
        }
        val returnedSystems = systemListResponse?.let { parseFelicaSystemCodesResponse(it, firstPoll.idm) }
        if (context.buildResult().issues.any { it.kind == ReadIssueKind.TAG_LOST }) {
            context.setCoverage(ReadCoverage.unknown("FeliCa system polling"))
            context.markPartial()
            return
        }
        if (returnedSystems == null) {
            context.addIssue(
                ReadIssue(
                    ReadIssueKind.COVERAGE_UNKNOWN,
                    "System-code enumeration was unavailable or malformed; only Android's discovered system is attempted.",
                    "Request System Code",
                )
            )
        }
        val systems = (listOf(discoveredSystem) + returnedSystems.orEmpty())
            .distinct()
            .take(MAX_SYSTEMS)
        val serviceBudget = ServiceBudget()
        val completedSystems = mutableListOf<ReadRange>()
        var index = 0
        while (index < systems.size) {
            context.checkpoint()
            val systemCode = systems[index]
            val polling = if (systemCode == discoveredSystem) firstPoll else poll(boundedTransport, maxTransceiveLength, systemCode, context)
            if (polling == null) {
                context.addUnreadRange(
                    UnreadRange("FeliCa systems", "system", index.toLong(), index + 1L, "System polling failed." )
                )
                index += 1
                if (context.buildResult().issues.any { it.kind == ReadIssueKind.TAG_LOST }) break
                continue
            }
            val prefix = "Felica.system[0x%04X]".format(systemCode)
            context.addDecodedFields(
                listOf(
                    NfcField("$prefix.IDm", polling.idm.toHexString()),
                    NfcField("$prefix.PMm", polling.pmm.toHexString()),
                    NfcField("$prefix.returnedSystemCode", polling.returnedSystemCode?.let { "0x%04X".format(it) } ?: "not returned"),
                )
            )
            completedSystems += ReadRange(index.toLong(), index + 1L)
            if (systemCode == SUICA_SYSTEM_CODE) {
                readSuicaHistory(boundedTransport, maxTransceiveLength, polling.idm, prefix, context)
            }
            scanStandardServices(boundedTransport, maxTransceiveLength, polling.idm, systemCode, prefix, context, serviceBudget)
            if (context.buildResult().issues.any { it.kind == ReadIssueKind.TAG_LOST }) break
            index += 1
        }
        if (systems.size == MAX_SYSTEMS && returnedSystems.orEmpty().size > MAX_SYSTEMS) {
            context.addUnreadRange(
                UnreadRange("FeliCa systems", "system", MAX_SYSTEMS.toLong(), returnedSystems.orEmpty().size.toLong(), "System enumeration limit reached.")
            )
        }
        context.setCoverage(
            ReadCoverage(
                scope = "FeliCa systems advertised by Request System Code",
                unit = "system",
                expectedRange = ReadRange(0, systems.size.toLong()),
                completedRanges = completedSystems,
            )
        )
        context.addIssue(
            ReadIssue(
                ReadIssueKind.COVERAGE_UNKNOWN,
                "Service discovery is bounded to 2048 standard-attribute candidates, 64 batches, 512 exchanges, 4096 blocks, and 30 seconds; physical capacity remains unknown.",
                "FeliCa service coverage",
            )
        )
        context.markPartial()
    }

    private fun scanStandardServices(
        transport: ReadOnlyTransport,
        maxTransceiveLength: Int,
        idm: ByteArray,
        systemCode: Int,
        prefix: String,
        context: TechReadContext,
        budget: ServiceBudget,
    ) {
        val candidates = (0..127).flatMap { number ->
            (0x08..0x17).map { attribute -> buildFelicaServiceCode(number, attribute) }
        }
        val readable = mutableListOf<FelicaServiceKeyVersion>()
        val candidateCoverage = mutableListOf<ReadRange>()
        var offset = 0
        while (offset < candidates.size) {
            context.checkpoint()
            if (budget.batches >= 64 || !reserveServiceExchange(context, budget, prefix)) break
            val batch = candidates.subList(offset, minOf(offset + 32, candidates.size))
            val location = "$prefix Request Service offset %d".format(offset)
            val request = buildRequestServiceCommand(idm, batch)
            if (request.size > maxTransceiveLength) {
                context.addIssue(ReadIssue(ReadIssueKind.UNSUPPORTED_TECHNOLOGY, "Request Service exceeds Android's transmit limit.", location))
                break
            }
            budget.batches += 1
            context.captureRaw("FeliCa Request Service request", request, location)
            val response = try {
                transport.transceiveReadOnly(request)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (exception: ReadBudgetExceededException) {
                throw exception
            } catch (exception: Exception) {
                context.addIssue(felicaIssue(exception, location))
                context.addUnreadRange(UnreadRange("FeliCa service candidates system 0x%04X".format(systemCode), "candidate", offset.toLong(), candidates.size.toLong(), "Request Service failed."))
                return
            }
            context.captureRaw("FeliCa Request Service response", response, location)
            val versions = parseRequestServiceResponse(response, idm, batch)
            if (versions == null) {
                context.addIssue(ReadIssue(ReadIssueKind.MALFORMED_RESPONSE, "Request Service response was malformed.", location))
                break
            }
            candidateCoverage += ReadRange(offset.toLong(), (offset + batch.size).toLong())
            versions.forEach { service ->
                if (!service.isPresent) return@forEach
                context.addDecodedFields(listOf(NfcField("$prefix.service[0x%04X].keyVersion".format(service.serviceCode), "0x%04X".format(service.keyVersion))))
                when (service.requiresAuthentication) {
                    true -> {
                        context.addIssue(ReadIssue(ReadIssueKind.AUTHENTICATION_REQUIRED, "Authentication-required service was not read.", "service 0x%04X".format(service.serviceCode)))
                        context.addUnreadRange(UnreadRange("system 0x%04X service 0x%04X".format(systemCode, service.serviceCode), "block", reason = "Authentication required; no authentication attempted."))
                    }
                    false -> if (service.serviceCode != SUICA_HISTORY_SERVICE) readable += service
                    null -> context.addIssue(ReadIssue(ReadIssueKind.UNSUPPORTED_TECHNOLOGY, "Unknown service attribute was not read.", "service 0x%04X".format(service.serviceCode)))
                }
            }
            offset += batch.size
        }
        if (offset < candidates.size) {
            context.addUnreadRange(UnreadRange("FeliCa service candidates system 0x%04X".format(systemCode), "candidate", offset.toLong(), candidates.size.toLong(), "Bounded candidate search stopped."))
        }
        context.setCoverage(ReadCoverage("$prefix standard service candidates", "candidate", ReadRange(0, candidates.size.toLong()), candidateCoverage))

        for (service in readable) {
            var block = 0
            while (block < 256 && budget.blocks < 4096) {
                context.checkpoint()
                val location = "$prefix service 0x%04X block $block".format(service.serviceCode)
                if (!reserveServiceExchange(context, budget, location)) {
                    context.addUnreadRange(UnreadRange("system 0x%04X service 0x%04X".format(systemCode, service.serviceCode), "block", block.toLong(), 256L, "Exchange/time budget exhausted."))
                    return
                }
                budget.blocks += 1
                val request = buildReadWithoutEncryptionCommand(idm, service.serviceCode, listOf(block))
                context.captureRaw("FeliCa Read Without Encryption request", request, location)
                val response = try {
                    transport.transceiveReadOnly(request)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (exception: ReadBudgetExceededException) {
                    throw exception
                } catch (exception: Exception) {
                    context.addIssue(felicaIssue(exception, location))
                    context.addUnreadRange(UnreadRange("system 0x%04X service 0x%04X".format(systemCode, service.serviceCode), "block", block.toLong(), 256L, "Read exchange failed."))
                    if (exception is TagLostException) return
                    break
                }
                context.captureRaw("FeliCa Read Without Encryption response", response, location)
                val parsed = parseFelicaReadResponse(response, idm, 1)
                if (parsed.error != null || parsed.status1 != 0 || parsed.status2 != 0 || parsed.blocks.size != 1) {
                    context.addIssue(ReadIssue(if (parsed.error == null) ReadIssueKind.IO_ERROR else ReadIssueKind.MALFORMED_RESPONSE, parsed.error ?: "Read status %02X/%02X or no block returned.".format(parsed.status1, parsed.status2), location))
                    context.addUnreadRange(UnreadRange("system 0x%04X service 0x%04X".format(systemCode, service.serviceCode), "block", block.toLong(), 256L, "Read returned no validated block."))
                    break
                }
                val data = parsed.blocks.single()
                context.captureRaw("FeliCa service block", data, location)
                context.addDecodedFields(listOf(NfcField("$prefix.service[0x%04X].block[$block].raw".format(service.serviceCode), data.toHexString())))
                block += 1
            }
            if (block == 256) {
                context.addIssue(ReadIssue(ReadIssueKind.COVERAGE_UNKNOWN, "The 256-block application cap was reached; service capacity is unknown.", "service 0x%04X".format(service.serviceCode)))
            }
        }
    }

    private fun reserveServiceExchange(context: TechReadContext, budget: ServiceBudget, operation: String): Boolean {
        if (budget.exchanges >= 512 || System.nanoTime() - budget.startedAt >= 30_000_000_000L) {
            context.addIssue(ReadIssue(ReadIssueKind.BUDGET_EXHAUSTED, "FeliCa stopped at the exchange/time budget.", operation))
            return false
        }
        budget.exchanges += 1
        return true
    }

    private data class ServiceBudget(val startedAt: Long = System.nanoTime(), var exchanges: Int = 0, var batches: Int = 0, var blocks: Int = 0)

    private fun poll(
        transport: ReadOnlyTransport,
        maxTransceiveLength: Int,
        systemCode: Int,
        context: TechReadContext,
    ): FelicaPollingResult? {
        val request = buildPollingCommand(systemCode)
        if (request.size > maxTransceiveLength) {
            context.addIssue(ReadIssue(ReadIssueKind.UNSUPPORTED_TECHNOLOGY, "Polling request exceeds the Android transmit limit.", "system 0x%04X".format(systemCode)))
            return null
        }
        context.captureRaw("FeliCa Polling request", request, "system 0x%04X".format(systemCode))
        val response = try {
            transport.transceiveReadOnly(request)
        } catch (exception: kotlinx.coroutines.CancellationException) {
            throw exception
        } catch (exception: ReadBudgetExceededException) {
            throw exception
        } catch (exception: Exception) {
            context.addIssue(felicaIssue(exception, "Polling system 0x%04X".format(systemCode)))
            return null
        }
        context.captureRaw("FeliCa Polling response", response, "system 0x%04X".format(systemCode))
        val parsed = parseFelicaPollingResponse(response, systemCode)
        if (parsed == null) {
            context.addIssue(
                ReadIssue(
                    ReadIssueKind.MALFORMED_RESPONSE,
                    "Polling response length, command, or returned system code did not match the request.",
                    "system 0x%04X".format(systemCode),
                )
            )
        }
        return parsed
    }

    private fun readSuicaHistory(
        transport: ReadOnlyTransport,
        maxTransceiveLength: Int,
        idm: ByteArray,
        prefix: String,
        context: TechReadContext,
    ) {
        val completed = mutableListOf<ReadRange>()
        var nextBlock = 0
        while (nextBlock < HISTORY_BLOCKS) {
            context.checkpoint()
            val requested = (nextBlock until minOf(nextBlock + READ_CHUNK, HISTORY_BLOCKS)).toList()
            val request = buildReadWithoutEncryptionCommand(idm, SUICA_HISTORY_SERVICE, requested)
            if (request.size > maxTransceiveLength) {
                context.addIssue(ReadIssue(ReadIssueKind.UNSUPPORTED_TECHNOLOGY, "History read request exceeds the Android transmit limit.", prefix))
                break
            }
            context.captureRaw("FeliCa Read Without Encryption request", request, "$prefix service 0x%04X blocks %s".format(SUICA_HISTORY_SERVICE, requested))
            val response = try {
                transport.transceiveReadOnly(request)
            } catch (exception: kotlinx.coroutines.CancellationException) {
                throw exception
            } catch (exception: ReadBudgetExceededException) {
                throw exception
            } catch (exception: Exception) {
                context.addIssue(felicaIssue(exception, "$prefix history blocks $nextBlock"))
                context.addUnreadRange(
                    UnreadRange("FeliCa service 0x%04X".format(SUICA_HISTORY_SERVICE), "block", nextBlock.toLong(), HISTORY_BLOCKS.toLong(), "Read exchange failed.")
                )
                break
            }
            context.captureRaw("FeliCa Read Without Encryption response", response, "$prefix blocks $requested")
            val parsed = parseFelicaReadResponse(response, idm, requested.size)
            if (parsed.error != null) {
                context.addIssue(ReadIssue(ReadIssueKind.MALFORMED_RESPONSE, parsed.error, "$prefix history blocks $nextBlock"))
                context.addUnreadRange(
                    UnreadRange("FeliCa service 0x%04X".format(SUICA_HISTORY_SERVICE), "block", nextBlock.toLong(), HISTORY_BLOCKS.toLong(), "Response could not be validated." )
                )
                break
            }
            if (parsed.status1 != 0 || parsed.status2 != 0) {
                context.addIssue(
                    ReadIssue(
                        ReadIssueKind.IO_ERROR,
                        "FeliCa service returned status %02X/%02X; access/authentication reason is not inferred.".format(parsed.status1, parsed.status2),
                        "$prefix history blocks $nextBlock",
                    )
                )
                context.addUnreadRange(
                    UnreadRange("FeliCa service 0x%04X".format(SUICA_HISTORY_SERVICE), "block", nextBlock.toLong(), HISTORY_BLOCKS.toLong(), "Service status was nonzero." )
                )
                break
            }
            parsed.blocks.forEachIndexed { offset, block ->
                val blockNumber = nextBlock + offset
                context.captureRaw("FeliCa block", block, "$prefix service 0x%04X block $blockNumber".format(SUICA_HISTORY_SERVICE))
                context.addDecodedFields(listOf(NfcField("$prefix.block[$blockNumber].raw", block.toHexString())))
                if (block.all { it.toInt() == 0 }) {
                    context.addDecodedFields(listOf(NfcField("$prefix.block[$blockNumber].decode", "all-zero block; preserved without semantic interpretation")))
                } else {
                    val entry = parseSuicaHistoryBlock(block)
                    context.addDecodedFields(listOf(NfcField("$prefix.history[$blockNumber]", entry.summary())))
                    if (!entry.dateValid) {
                        context.addIssue(ReadIssue(ReadIssueKind.MALFORMED_RESPONSE, "History date fields are invalid; raw block is retained.", "$prefix block $blockNumber"))
                    }
                }
                completed += ReadRange(blockNumber.toLong(), blockNumber + 1L)
            }
            nextBlock += parsed.blocks.size
            if (parsed.blocks.size < requested.size) {
                context.addUnreadRange(
                    UnreadRange("FeliCa service 0x%04X".format(SUICA_HISTORY_SERVICE), "block", nextBlock.toLong(), HISTORY_BLOCKS.toLong(), "Response contained fewer blocks than requested." )
                )
                break
            }
        }
        context.setCoverage(
            ReadCoverage(
                scope = "Suica history service 0x%04X first %d blocks".format(SUICA_HISTORY_SERVICE, HISTORY_BLOCKS),
                unit = "block",
                expectedRange = ReadRange(0, HISTORY_BLOCKS.toLong()),
                completedRanges = completed,
            )
        )
        if (completed.size < HISTORY_BLOCKS) {
            context.addUnreadRange(
                UnreadRange("FeliCa service 0x%04X".format(SUICA_HISTORY_SERVICE), "block", completed.size.toLong(), HISTORY_BLOCKS.toLong(), "Remaining history blocks were not confirmed." )
            )
        }
    }

    private fun parseFelicaReadResponse(response: ByteArray, idm: ByteArray, requestedCount: Int): FelicaReadResult {
        if (response.size < 12 || (response[0].toInt() and 0xFF) != response.size ||
            (response[1].toInt() and 0xFF) != READ_RESPONSE_CODE
        ) {
            return FelicaReadResult(error = "FeliCa read response header or declared length is malformed.")
        }
        if (!response.copyOfRange(2, 10).contentEquals(idm)) {
            return FelicaReadResult(error = "FeliCa read response IDm does not match the polled system IDm.")
        }
        val status1 = response[10].toInt() and 0xFF
        val status2 = response[11].toInt() and 0xFF
        if (status1 != 0 || status2 != 0) {
            return if (response.size == 12) FelicaReadResult(status1 = status1, status2 = status2) else {
                FelicaReadResult(error = "FeliCa error response contains unexpected block data.")
            }
        }
        if (response.size < 13) return FelicaReadResult(error = "FeliCa success response omitted the block count.")
        val count = response[12].toInt() and 0xFF
        if (count > requestedCount || response.size != 13 + count * BLOCK_SIZE) {
            return FelicaReadResult(error = "FeliCa block count does not match the response length or request.")
        }
        return FelicaReadResult(
            status1 = status1,
            status2 = status2,
            blocks = List(count) { index ->
                response.copyOfRange(13 + index * BLOCK_SIZE, 13 + (index + 1) * BLOCK_SIZE)
            },
        )
    }

    private fun ByteArray.toSystemCode(): Int? =
        if (size != 2) null else ((this[0].toInt() and 0xFF) shl 8) or (this[1].toInt() and 0xFF)

    private fun felicaIssue(exception: Exception, operation: String) = ReadIssue(
        kind = when (exception) {
            is TagLostException -> ReadIssueKind.TAG_LOST
            is IOException -> ReadIssueKind.IO_ERROR
            else -> ReadIssueKind.OTHER
        },
        message = if (exception is TagLostException) "Tag left the NFC field." else "FeliCa exchange failed: ${exception::class.simpleName ?: "Exception"}.",
        operation = operation,
    )

    private data class FelicaReadResult(
        val status1: Int = 0,
        val status2: Int = 0,
        val blocks: List<ByteArray> = emptyList(),
        val error: String? = null,
    )

    private companion object {
        const val MAX_SYSTEMS = 32
        const val HISTORY_BLOCKS = 20
        const val READ_CHUNK = 4
        const val BLOCK_SIZE = 16
        const val READ_RESPONSE_CODE = 0x07
    }
}
