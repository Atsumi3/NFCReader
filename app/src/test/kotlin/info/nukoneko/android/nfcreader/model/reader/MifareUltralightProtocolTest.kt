package info.nukoneko.android.nfcreader.model.reader

import info.nukoneko.android.nfcreader.model.entity.ReadOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MifareUltralightProtocolTest {
    @Test
    fun recognizesOnlyExactEightByteVersionProfiles() {
        val profiles = listOf(
            byteArrayOf(0, 4, 4, 2, 1, 0, 0x0F, 3) to "NTAG213",
            byteArrayOf(0, 4, 4, 2, 1, 0, 0x11, 3) to "NTAG215",
            byteArrayOf(0, 4, 4, 2, 1, 0, 0x13, 3) to "NTAG216",
            byteArrayOf(0, 4, 3, 1, 1, 0, 0x0B, 3) to "Ultralight EV1 MF0UL11",
            byteArrayOf(0, 4, 3, 2, 1, 0, 0x0E, 3) to "Ultralight EV1 MF0UL21",
        )
        profiles.forEach { (version, name) ->
            assertEquals(name, MifareUltralightProtocol.parseVersion(version)?.name)
        }
        assertNull(MifareUltralightProtocol.parseVersion(byteArrayOf(0, 4, 4, 2, 1, 0, 0x0F)))
        assertNull(MifareUltralightProtocol.parseVersion(byteArrayOf(0, 4, 4, 2, 1, 0, 0x0F, 2)))
    }

    @Test
    fun ntag213UsesFourPageFastReadChunksThroughItsLastPage() {
        val commands = mutableListOf<ByteArray>()
        val transport = ReadOnlyTransport { command ->
            commands += command.copyOf()
            when (command[0].toInt() and 0xFF) {
                0x60 -> byteArrayOf(0, 4, 4, 2, 1, 0, 0x0F, 3)
                0x3A -> {
                    val start = command[1].toInt() and 0xFF
                    val end = command[2].toInt() and 0xFF
                    ByteArray((end - start + 1) * 4) { index -> (start * 4 + index).toByte() }
                }
                else -> error("Unexpected READ command")
            }
        }
        val context = newContext()

        MifareUltralightProtocol.read(transport, context)

        val fastReads = commands.filter { (it[0].toInt() and 0xFF) == 0x3A }
        assertEquals(12, fastReads.size)
        assertEquals(0x2C, fastReads.last()[2].toInt() and 0xFF)
        assertEquals(ReadOutcome.COMPLETE, context.buildResult().outcome)
        assertTrue(context.buildResult().decodedFields.any { it.label == "Ultralight.page[44]" })
    }

    @Test
    fun malformedFastReadChunkFallsBackToSinglePageReads() {
        val commands = mutableListOf<ByteArray>()
        var firstFastRead = true
        val transport = ReadOnlyTransport { command ->
            commands += command.copyOf()
            when (command[0].toInt() and 0xFF) {
                0x60 -> byteArrayOf(0, 4, 4, 2, 1, 0, 0x0F, 3)
                0x3A -> {
                    if (firstFastRead) {
                        firstFastRead = false
                        byteArrayOf(0x00)
                    } else {
                        val start = command[1].toInt() and 0xFF
                        val end = command[2].toInt() and 0xFF
                        ByteArray((end - start + 1) * 4) { index -> (start * 4 + index).toByte() }
                    }
                }
                0x30 -> ByteArray(16) { index -> (command[1] + index).toByte() }
                else -> error("Unexpected command")
            }
        }
        val context = newContext()

        MifareUltralightProtocol.read(transport, context)

        val firstChunkCommands = commands.drop(1).take(5).map { it[0].toInt() and 0xFF }
        assertEquals(listOf(0x3A, 0x30, 0x30, 0x30, 0x30), firstChunkCommands)
        assertEquals(4, commands.drop(1).take(5).drop(1).map { it[1].toInt() and 0xFF }.toSet().size)
        assertEquals(ReadOutcome.COMPLETE, context.buildResult().outcome)
    }

    @Test
    fun unknownVersionUsesBoundedReadAndNeverFastRead() {
        val commands = mutableListOf<ByteArray>()
        val transport = ReadOnlyTransport { command ->
            commands += command.copyOf()
            when (command[0].toInt() and 0xFF) {
                0x60 -> byteArrayOf(0, 4, 9, 9, 9, 9, 9, 9)
                0x30 -> ByteArray(16) { command[1] }
                else -> error("Unknown profiles must not use FAST_READ")
            }
        }
        val context = newContext()

        MifareUltralightProtocol.read(transport, context)

        assertEquals(256, commands.count { (it[0].toInt() and 0xFF) == 0x30 })
        assertTrue(commands.none { (it[0].toInt() and 0xFF) == 0x3A })
        assertEquals(ReadOutcome.PARTIAL, context.buildResult().outcome)
    }

    private fun newContext() = TechReadContext(
        rawBudget = RawCaptureBudget(64 * 1024),
        fieldBudget = FieldValueBudget(64 * 1024),
        cancellationCheck = {},
    )
}
