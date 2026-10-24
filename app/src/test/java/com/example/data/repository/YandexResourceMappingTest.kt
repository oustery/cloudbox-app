package com.example.data.repository

import com.example.data.model.YandexPublicResource
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Mapping tests for Yandex public resources: Moshi DTO parsing (reflective,
 * same as production) plus the CloudItem conversion and preview URL rewriting.
 */
class YandexResourceMappingTest {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val repo = YandexDiskRepository()

    private fun parseItem(json: String): YandexPublicResource {
        val adapter = moshi.adapter(YandexPublicResource::class.java)
        return adapter.fromJson(json)!!
    }

    @Test
    fun `maps a file item with preview and timestamps`() {
        val resource = parseItem(
            """
            {
              "name": "photo.jpg",
              "type": "file",
              "mime_type": "image/jpeg",
              "media_type": "image",
              "size": 1234567,
              "created": "2026-01-01T00:00:00Z",
              "modified": "2026-01-01T00:00:00Z",
              "preview": "https://downloader.disk.yandex.ru/preview/abc?size=XL",
              "file": "https://downloader.disk.yandex.ru/disk/abc"
            }
            """.trimIndent()
        )

        val item = repo.mapResource(
            resource = resource,
            publicKey = "https://disk.yandex.ru/d/abc123",
            parentPath = ""
        )

        assertEquals("file", item.kind)
        assertEquals("photo.jpg", item.name)
        assertEquals(1234567L, item.size)
        assertEquals("image", item.type)
        assertEquals(1767225600L, item.mtime)
        assertEquals("https://disk.yandex.ru/d/abc123/photo.jpg", item.weblink)
        assertEquals("https://downloader.disk.yandex.ru/preview/abc?size=XL", item.previewUrl)
    }

    @Test
    fun `maps a directory item and nests the weblink under parent path`() {
        val resource = parseItem(
            """
            {
              "name": "Отпуск 2026",
              "type": "dir",
              "modified": "2026-01-01T00:00:00Z"
            }
            """.trimIndent()
        )

        val item = repo.mapResource(
            resource = resource,
            publicKey = "https://disk.yandex.ru/d/abc123",
            parentPath = "/sub"
        )

        assertEquals("folder", item.kind)
        assertEquals("https://disk.yandex.ru/d/abc123/sub/Отпуск 2026", item.weblink)
        assertNull(item.previewUrl)
        assertEquals(1767225600L, item.mtime)
    }

    @Test
    fun `falls back to mime type and unknown media yields null type`() {
        val video = repo.mapResource(
            parseItem("""{"name":"v.mkv","type":"file","mime_type":"video/x-matroska"}"""),
            "https://disk.yandex.ru/d/abc123", ""
        )
        assertEquals("video", video.type)

        val doc = repo.mapResource(
            parseItem("""{"name":"doc.pdf","type":"file","mime_type":"application/pdf"}"""),
            "https://disk.yandex.ru/d/abc123", ""
        )
        assertNull(doc.type)
    }

    @Test
    fun `unknown timestamps produce zero mtime`() {
        val item = repo.mapResource(
            parseItem("""{"name":"f.bin","type":"file","modified":"not-a-date"}"""),
            "https://disk.yandex.ru/d/abc123", ""
        )
        assertEquals(0L, item.mtime)
    }

    @Test
    fun `thumbnail rewrites size token`() {
        val base = "https://downloader.disk.yandex.ru/preview/abc?uid=1&size=XL"
        assertEquals(
            "https://downloader.disk.yandex.ru/preview/abc?uid=1&size=M",
            repo.thumbnailUrl(base, ThumbnailSize.SMALL)
        )
        assertEquals(
            "https://downloader.disk.yandex.ru/preview/abc?uid=1&size=XL",
            repo.thumbnailUrl(base, ThumbnailSize.HIGH_RES)
        )
    }

    @Test
    fun `thumbnail appends size when missing and null for empty preview`() {
        assertEquals(
            "https://downloader.disk.yandex.ru/preview/abc?size=XL",
            repo.thumbnailUrl("https://downloader.disk.yandex.ru/preview/abc", ThumbnailSize.HIGH_RES)
        )
        assertNull(repo.thumbnailUrl(null, ThumbnailSize.SMALL))
        assertNull(repo.thumbnailUrl("", ThumbnailSize.SMALL))
    }
}
