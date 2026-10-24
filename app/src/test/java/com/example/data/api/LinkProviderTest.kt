package com.example.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkProviderTest {

    @Test
    fun `detects yandex domains`() {
        assertEquals(LinkProvider.YANDEX, LinkProvider.detect("https://disk.yandex.ru/d/abc123"))
        assertEquals(LinkProvider.YANDEX, LinkProvider.detect("https://yadi.sk/i/xyz"))
        assertEquals(LinkProvider.YANDEX, LinkProvider.detect("yadi.sk/d/abc"))
    }

    @Test
    fun `detects mailru domains`() {
        assertEquals(LinkProvider.MAILRU, LinkProvider.detect("https://cloud.mail.ru/public/KVy7/S1hRRPVUE"))
        assertEquals(LinkProvider.MAILRU, LinkProvider.detect("cloud.mail.ru/public/a/b"))
    }

    @Test
    fun `bare weblink keys are unknown until defaulted`() {
        assertNull(LinkProvider.detect("KVy7/S1hRRPVUE"))
        assertNull(LinkProvider.detect("KVy7/S1hRRPVUE/sub/file.jpg"))
        assertEquals(
            LinkProvider.MAILRU,
            LinkProvider.detectOrDefault("KVy7/S1hRRPVUE/sub/file.jpg")
        )
    }

    @Test
    fun `yandex item weblinks stay detectable`() {
        val itemWeblink = YandexUrlParser.buildItemWeblink(
            "https://disk.yandex.ru/d/abc123",
            "/sub/file.jpg"
        )
        assertEquals(LinkProvider.YANDEX, LinkProvider.detect(itemWeblink))
    }

    @Test
    fun `provider labels are non-blank`() {
        LinkProvider.values().forEach { provider ->
            assertEquals(false, provider.label.isBlank())
            assertEquals(false, provider.shortLabel.isBlank())
        }
    }
}
