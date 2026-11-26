package info.nukoneko.android.nfcreader.model.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class NdefPayloadTest {

    @Test
    fun uriPayloadExpandsAbbreviatedPrefix() {
        // prefix code 0x04 -> "https://"
        val payload = byteArrayOf(0x04) + "example.com".toByteArray(Charsets.UTF_8)
        assertEquals("https://example.com", decodeNdefUriPayload(payload))
    }

    @Test
    fun uriPayloadWithNoPrefix() {
        val payload = byteArrayOf(0x00) + "http://nukoneko.info".toByteArray(Charsets.UTF_8)
        assertEquals("http://nukoneko.info", decodeNdefUriPayload(payload))
    }

    @Test
    fun emptyUriPayloadIsEmptyString() {
        assertEquals("", decodeNdefUriPayload(ByteArray(0)))
    }

    @Test
    fun textPayloadDecodesUtf8AfterLanguageCode() {
        // status 0x02: UTF-8 encoding, language code length 2 ("en").
        val payload = byteArrayOf(0x02) + "enHello".toByteArray(Charsets.UTF_8)
        assertEquals("Hello", decodeNdefTextPayload(payload))
    }

    @Test
    fun textPayloadHandlesMultiByteCharacters() {
        val payload = byteArrayOf(0x02) + "jaこんにちは".toByteArray(Charsets.UTF_8)
        assertEquals("こんにちは", decodeNdefTextPayload(payload))
    }

    @Test
    fun longTextIsPreviewTruncatedWithoutMalformedWarning() {
        val payload = byteArrayOf(0x00) + "a".repeat(5000).toByteArray(Charsets.UTF_8)
        val result = decodeNdefPayload(1, byteArrayOf('T'.code.toByte()), payload)
        assertEquals(4096, result.decodedText?.length)
        assertEquals(true, result.previewTruncated)
        assertEquals(emptyList<String>(), result.warnings)
    }

    @Test
    fun utf8PreviewBoundaryDoesNotTurnValidTextIntoMalformedData() {
        val payload = byteArrayOf(0x00) + ("a".repeat(4095) + "💩tail").toByteArray(Charsets.UTF_8)
        val result = decodeNdefPayload(1, byteArrayOf('T'.code.toByte()), payload)
        assertEquals(4095, result.decodedText?.length)
        assertEquals(true, result.previewTruncated)
        assertEquals(emptyList<String>(), result.warnings)
    }
}
