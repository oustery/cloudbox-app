package com.example.data.api

object CloudUrlParser {

    /**
     * Extracts the weblink key from a cloud.mail.ru public URL or raw string.
     * Examples:
     * - "https://cloud.mail.ru/public/KVy7/S1hRRPVUE" -> "KVy7/S1hRRPVUE"
     * - "cloud.mail.ru/public/KVy7/S1hRRPVUE/folder1" -> "KVy7/S1hRRPVUE/folder1"
     * - "KVy7/S1hRRPVUE" -> "KVy7/S1hRRPVUE"
     */
    fun extractWeblink(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // Strip query params or fragments if present
        val withoutQuery = trimmed.substringBefore('?').substringBefore('#')

        // Look for "/public/"
        val publicIndex = withoutQuery.indexOf("/public/")
        if (publicIndex != -1) {
            val key = withoutQuery.substring(publicIndex + "/public/".length).trim('/')
            return if (key.isNotEmpty()) key else null
        }

        // Look for "public/" at the start
        if (withoutQuery.startsWith("public/")) {
            val key = withoutQuery.substring("public/".length).trim('/')
            return if (key.isNotEmpty()) key else null
        }

        // Check if format is "XXXX/YYYY" or "XXXX/YYYY/..."
        val parts = withoutQuery.split('/')
        if (parts.size >= 2 && parts[0].length in 2..16 && parts[1].length in 2..32) {
            return withoutQuery.trim('/')
        }

        return null
    }

    /**
     * Checks if a string looks like a valid Cloud Mail.ru public link.
     */
    fun isValidMailRuUrl(input: String): Boolean {
        return extractWeblink(input) != null
    }

    /**
     * Converts a weblink back to the full web address.
     */
    fun toFullPublicUrl(weblink: String): String {
        return "https://cloud.mail.ru/public/${weblink.trim('/')}"
    }

    /**
     * Converts an internal weblink key back to the full web address,
     * taking the provider into account:
     *  - Mail.ru key "KVy7/S1hRRPVUE" -> "https://cloud.mail.ru/public/KVy7/S1hRRPVUE"
     *  - Yandex key is already a normalized URL and is returned as-is.
     */
    fun toFullUrl(provider: LinkProvider, weblink: String): String = when (provider) {
        LinkProvider.YANDEX -> weblink
        LinkProvider.MAILRU -> toFullPublicUrl(weblink)
        LinkProvider.MEGA -> {
            val identity = com.example.data.api.mega.MegaLinks.parseIdentity(weblink)
                ?: com.example.data.api.mega.MegaLinks.parse(weblink)
                    ?.let { com.example.data.api.mega.MegaLinks.parseIdentity(it.identityKey) }
            identity?.let { com.example.data.api.mega.MegaLinks.toPublicUrl(it) } ?: weblink
        }
    }

    /**
     * Provider-aware intake parser: accepts a Mail.ru public link, a Yandex
     * public link, or a bare Mail.ru weblink key, and returns the canonical
     * internal key ready for [com.example.data.repository.CloudMailRuRepository.fetchFolderContent].
     * Returns null when the input matches neither provider.
     */
    fun extractCloudKey(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        return when (LinkProvider.detect(trimmed)) {
            LinkProvider.YANDEX -> YandexUrlParser.normalize(trimmed)
            LinkProvider.MAILRU -> extractWeblink(trimmed)
            LinkProvider.MEGA -> {
                // Full mega URL -> composite identity key; composite keys pass through.
                if (com.example.data.api.mega.MegaLinks.parseIdentity(trimmed) != null) {
                    trimmed
                } else {
                    com.example.data.api.mega.MegaLinks.parse(trimmed)?.identityKey
                }
            }
            null -> {
                // Bare Mail.ru key ("KVy7/S1hRRPVUE") carries no host hints.
                if (extractWeblink(trimmed) != null) trimmed else null
            }
        }
    }

    /**
     * Extracts the root public folder key (first two segments: "key1/key2")
     */
    fun getRootWeblink(weblink: String): String {
        val segments = weblink.trim('/').split('/')
        return if (segments.size >= 2) {
            "${segments[0]}/${segments[1]}"
        } else {
            weblink
        }
    }
}
