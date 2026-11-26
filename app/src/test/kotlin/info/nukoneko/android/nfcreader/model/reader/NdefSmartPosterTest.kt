package info.nukoneko.android.nfcreader.model.reader

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
class NdefSmartPosterTest {
    @Test
    fun androidNdefParserDecodesUriTextAndSmartPosterLocalFields() {
        val nested = NdefMessage(
            arrayOf(
                wellKnown("U", byteArrayOf(0x04) + "example.com".toByteArray(Charsets.UTF_8)),
                wellKnown("T", byteArrayOf(0x02, 'e'.code.toByte(), 'n'.code.toByte()) + "Shop".toByteArray(Charsets.UTF_8)),
                wellKnown("act", byteArrayOf(0x01)),
                wellKnown("s", byteArrayOf(0, 0, 0, 5)),
                wellKnown("t", "image/png".toByteArray(Charsets.US_ASCII)),
                NdefRecord(NdefRecord.TNF_MIME_MEDIA, "image/png".toByteArray(Charsets.US_ASCII), byteArrayOf(), byteArrayOf(1, 2, 3)),
            )
        )
        val smartPoster = wellKnown("Sp", nested.toByteArray())
        val wireMessage = NdefMessage(arrayOf(smartPoster)).toByteArray()
        val parsedRecord = NdefMessage(wireMessage).records.single()
        assertTrue(isSmartPosterRecord(parsedRecord))

        val decoded = decodeSmartPosterPayload(parsedRecord.payload)
        val fields = decoded.fields.associate { it.key to it.value }
        assertEquals("https://example.com", fields["record[0].decoded"])
        assertEquals("Shop", fields["record[1].decoded"])
        assertEquals("en", fields["record[1].language"])
        assertEquals("1 (save; not executed)", fields["record[2].action"])
        assertEquals("5", fields["record[3].referenceSizeBytes"])
        assertEquals("image/png", fields["record[4].mimeType"])
        assertEquals("3", fields["record[5].payloadByteCount"])
        assertTrue(decoded.warnings.isEmpty())
    }

    @Test
    fun nestedSmartPosterStopsAtDepthAndRecordLimits() {
        val child = NdefMessage(arrayOf(wellKnown("U", byteArrayOf(0, 'x'.code.toByte()))))
        val nestedPoster = wellKnown("Sp", child.toByteArray())
        val payload = NdefMessage(arrayOf(nestedPoster, wellKnown("U", byteArrayOf(0, 'y'.code.toByte())))).toByteArray()

        val depthLimited = decodeSmartPosterPayload(payload, maxDepth = 0)
        assertTrue(depthLimited.warnings.any { it.contains("depth limit") })
        assertFalse(depthLimited.fields.any { it.key.startsWith("record[0].smartPoster") })

        val recordLimited = decodeSmartPosterPayload(payload, maxRecords = 1)
        assertTrue(recordLimited.warnings.any { it.contains("record limit") })
    }

    @Test
    fun malformedAndOversizedNestedMessagesRemainDiagnosed() {
        val malformed = decodeSmartPosterPayload(byteArrayOf(0x01, 0x02))
        assertTrue(malformed.warnings.any { it.contains("malformed") || it.contains("could not be parsed") })

        val valid = NdefMessage(arrayOf(wellKnown("U", byteArrayOf(0, 'x'.code.toByte())))).toByteArray()
        val oversized = decodeSmartPosterPayload(valid, maxPayloadBytes = 1)
        assertTrue(oversized.warnings.any { it.contains("decode limit") })
    }

    private fun wellKnown(type: String, payload: ByteArray) = NdefRecord(
        NdefRecord.TNF_WELL_KNOWN,
        type.toByteArray(Charsets.US_ASCII),
        byteArrayOf(),
        payload,
    )
}
