package com.example.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexUrlParserTest {

    @Test
    fun `parses public folder link`() {
        val parsed = YandexUrlParser.parse("https://disk.yandex.ru/d/abc123")!!
        assertEquals("https://disk.yandex.ru/d/abc123", parsed.publicKey)
        assertEquals("", parsed.subPath)
        assertFalse(parsed.isSingleFile)
    }

    @Test
    fun `strips query and fragment and keeps subpath`() {
        val parsed = YandexUrlParser.parse("https://disk.yandex.ru/d/abc123/sub/file.jpg?lr=5#anchor")!!
        assertEquals("https://disk.yandex.ru/d/abc123", parsed.publicKey)
        assertEquals("/sub/file.jpg", parsed.subPath)
    }

    @Test
    fun `normalizes short yadi sk domain and single-file kind`() {
        val parsed = YandexUrlParser.parse("https://yadi.sk/i/XYZ987")!!
        assertEquals("https://disk.yandex.ru/i/XYZ987", parsed.publicKey)
        assertTrue(parsed.isSingleFile)
    }

    @Test
    fun `accepts scheme-less input`() {
        val parsed = YandexUrlParser.parse("disk.yandex.ru/d/abc123/photos")!!
        assertEquals("https://disk.yandex.ru/d/abc123", parsed.publicKey)
        assertEquals("/photos", parsed.subPath)
    }

    @Test
    fun `rejects non-yandex hosts and wrong shapes`() {
        assertNull(YandexUrlParser.parse("https://ya.ru/d/abc"))
        assertNull(YandexUrlParser.parse("https://cloud.mail.ru/public/KVy7/S1h"))
        assertNull(YandexUrlParser.parse("https://disk.yandex.ru/client/abc"))
        assertNull(YandexUrlParser.parse("https://disk.yandex.ru/d"))
        assertNull(YandexUrlParser.parse(""))
        assertNull(YandexUrlParser.parse("just some text"))
    }

    @Test
    fun `normalize returns canonical key`() {
        assertEquals(
            "https://disk.yandex.ru/d/abc123/sub/dir",
            YandexUrlParser.normalize("yadi.sk/d/abc123/sub/dir/")
        )
    }

    @Test
    fun `split and build roundtrip`() {
        val original = "https://disk.yandex.ru/d/abc123/sub/file.jpg"
        val (publicKey, subPath) = YandexUrlParser.splitWeblink(original)!!
        assertEquals("https://disk.yandex.ru/d/abc123", publicKey)
        assertEquals("/sub/file.jpg", subPath)
        assertEquals(original, YandexUrlParser.buildItemWeblink(publicKey, subPath))
    }

    @Test
    fun `buildItemWeblink tolerates missing leading slash`() {
        assertEquals(
            "https://disk.yandex.ru/d/abc123/f.jpg",
            YandexUrlParser.buildItemWeblink("https://disk.yandex.ru/d/abc123", "f.jpg")
        )
    }

    @Test
    fun `isYandexLink matches only valid links`() {
        assertTrue(YandexUrlParser.isYandexLink("https://disk.yandex.ru/i/abc123"))
        assertFalse(YandexUrlParser.isYandexLink("https://disk.yandex.ru/"))
    }
}
