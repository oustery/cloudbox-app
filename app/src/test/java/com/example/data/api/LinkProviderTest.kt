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
    fun `detects mega domains and composite keys`() {
        assertEquals(LinkProvider.MEGA, LinkProvider.detect("https://mega.nz/folder/6ap10KRI#1Hy41Yv2pcJmYhdJjmPJpw"))
        assertEquals(LinkProvider.MEGA, LinkProvider.detect("https://mega.nz/file/DlggBbTY#fbKFn_qhA3hbthETgkipy_zV6RDarBgfnjp43cxpTzk"))
        assertEquals(LinkProvider.MEGA, LinkProvider.detect("mega.nz/#!oMhDAAqC!XQqMb9u7oAKvYMYYRHYI_s870Y2wXR4bzlL4aQI1eps"))
        // Composite identity keys produced by MegaLinks
        assertEquals(LinkProvider.MEGA, LinkProvider.detect("mega:6ap10KRI:1Hy41Yv2pcJmYhdJjmPJpw"))
        assertEquals(LinkProvider.MEGA, LinkProvider.detect("megafile:DlggBbTY:fbKFn_qhA3hbthETgkipy_zV6RDarBgfnjp43cxpTzk"))
    }

    @Test
    fun `extractCloudKey normalizes mega urls to identity keys`() {
        assertEquals(
            "mega:6ap10KRI:1Hy41Yv2pcJmYhdJjmPJpw",
            CloudUrlParser.extractCloudKey("https://mega.nz/folder/6ap10KRI#1Hy41Yv2pcJmYhdJjmPJpw")
        )
        assertEquals(
            "megafile:DlggBbTY:fbKFn_qhA3hbthETgkipy_zV6RDarBgfnjp43cxpTzk",
            CloudUrlParser.extractCloudKey("https://mega.nz/file/DlggBbTY#fbKFn_qhA3hbthETgkipy_zV6RDarBgfnjp43cxpTzk")
        )
        // Composite keys pass through unchanged
        assertEquals(
            "mega:6ap10KRI:1Hy41Yv2pcJmYhdJjmPJpw",
            CloudUrlParser.extractCloudKey("mega:6ap10KRI:1Hy41Yv2pcJmYhdJjmPJpw")
        )
        // Scheme-less mega URLs are not swallowed by the Mail.ru fallback
        assertEquals(
            "mega:6ap10KRI:1Hy41Yv2pcJmYhdJjmPJpw",
            CloudUrlParser.extractCloudKey("mega.nz/folder/6ap10KRI#1Hy41Yv2pcJmYhdJjmPJpw")
        )
    }

    @Test
    fun `provider labels are non-blank`() {
        LinkProvider.values().forEach { provider ->
            assertEquals(false, provider.label.isBlank())
            assertEquals(false, provider.shortLabel.isBlank())
        }
    }
}
