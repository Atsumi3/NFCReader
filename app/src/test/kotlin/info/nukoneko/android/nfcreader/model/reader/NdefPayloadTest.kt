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
}
