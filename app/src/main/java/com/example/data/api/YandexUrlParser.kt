package com.example.data.api

import java.net.URI

/**
 * Parser for public Yandex.Disk links.
 *
 * Supported public link shapes (with or without scheme, query, fragment):
 *  - https://disk.yandex.ru/d/<id>          — public folder
 *  - https://disk.yandex.ru/i/<id>          — public single file
 *  - https://disk.yandex.ru/d/<id>/sub/dir  — deep link into a public folder
 *  - https://yadi.sk/d/<id>[...]            — short domain, normalized to disk.yandex.ru
 *
 * The canonical internal key format is the normalized full URL:
 *   "https://disk.yandex.ru/d/<id>[/<subpath>]"
 * It doubles as the public_key for the Yandex API when the subpath is empty,
 * and everything after the "/d/<id>" (or "/i/<id>") prefix is treated as the
 * resource path INSIDE the public folder.
 */
object YandexUrlParser {

    private const val CANONICAL_HOST = "disk.yandex.ru"
    private val SHORT_HOSTS = setOf("yadi.sk", "disk.yandex.com", "disk.yandex.com.tr")
    private val PUBLIC_PATH_PREFIXES = setOf("d", "i")

    /** Hosts that may carry Yandex.Disk public links. */
    private fun isYandexHost(host: String?): Boolean {
        if (host == null) return false
        val lower = host.lowercase()
        return lower == CANONICAL_HOST || lower in SHORT_HOSTS || lower.endsWith(".yandex.ru")
    }

    /**
     * Parses a Yandex public link into its canonical parts.
     *
     * @return [YandexPublicLink] or null when the input is not a Yandex.Disk
     *         public link. Query parameters and fragments are stripped.
     */
    fun parse(input: String): YandexPublicLink? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val withoutQuery = trimmed.substringBefore('?').substringBefore('#')
        val withScheme = if (withoutQuery.startsWith("http://") || withoutQuery.startsWith("https://")) {
            withoutQuery
        } else {
            "https://$withoutQuery"
        }

        val uri = try {
            URI(withScheme)
        } catch (_: Exception) {
            return null
        }
        if (!isYandexHost(uri.host)) return null

        val segments = uri.path
            ?.trim('/')
            ?.split('/')
            ?.filter { it.isNotEmpty() }
            ?: return null
        if (segments.size < 2) return null
        if (segments[0].lowercase() !in PUBLIC_PATH_PREFIXES) return null

        val kind = segments[0].lowercase()
        val keyId = segments[1]
        val subPath = if (segments.size > 2) {
            "/" + segments.drop(2).joinToString("/")
        } else {
            ""
        }

        return YandexPublicLink(
            publicKey = "https://$CANONICAL_HOST/$kind/$keyId",
            isSingleFile = kind == "i",
            subPath = subPath
        )
    }

    /**
     * Normalizes any supported Yandex public link to the canonical internal
     * weblink key (public URL + optional subpath, query/fragment stripped).
     */
    fun normalize(input: String): String? = parse(input)?.let { it.publicKey + it.subPath }

    /**
     * True when the string looks like a Yandex.Disk public link.
     */
    fun isYandexLink(input: String): Boolean = parse(input) != null

    /**
     * Splits an internal Yandex weblink (which may point at the public root or
     * at a nested item) back into the public key and the sub path.
     * The sub path starts with "/" or is empty for the root resource.
     */
    fun splitWeblink(weblink: String): Pair<String, String>? {
        val parsed = parse(weblink) ?: return null
        return parsed.publicKey to parsed.subPath
    }

    /**
     * Builds the internal weblink of a resource inside a public folder.
     */
    fun buildItemWeblink(publicKey: String, itemPath: String): String {
        val key = publicKey.trimEnd('/')
        val path = if (itemPath.startsWith("/")) itemPath else "/$itemPath"
        return key + path
    }
}

/**
 * Canonical parts of a parsed Yandex public link.
 *
 * @param publicKey   normalized public URL usable as the API `public_key`
 * @param isSingleFile true for /i/ links (public single file)
 * @param subPath     path inside the public folder ("/a/b.jpg") or "" for the root
 */
data class YandexPublicLink(
    val publicKey: String,
    val isSingleFile: Boolean,
    val subPath: String
)
