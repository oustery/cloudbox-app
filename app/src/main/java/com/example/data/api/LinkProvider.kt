package com.example.data.api

/**
 * Cloud storage providers supported by CloudBox.
 *
 * Every public link saved in the library belongs to exactly one provider.
 * The provider is derived from the link/key string itself, so no extra
 * database column is required:
 *  - Mail.ru links are stored as bare weblink keys ("KVy7/S1hRRPVUE/sub/file.jpg");
 *  - Yandex links are stored as normalized public URLs
 *    ("https://disk.yandex.ru/d/abc/sub/file.jpg"), which also makes the
 *    provider trivially detectable from any item weblink.
 */
enum class LinkProvider(val label: String, val shortLabel: String) {
    MAILRU("Облако Mail.ru", "М"),
    YANDEX("Яндекс Диск", "Я");

    companion object {
        /**
         * Detects the provider from a user-provided URL or an internal weblink key.
         * Returns null when neither provider is recognizable (e.g. a random string).
         */
        fun detect(input: String): LinkProvider? {
            val lower = input.lowercase()
            return when {
                lower.contains("yadi.sk") || lower.contains("yandex") -> YANDEX
                lower.contains("cloud.mail.ru") || lower.contains("mail.ru") -> MAILRU
                else -> null
            }
        }

        /**
         * Same as [detect] but falls back to [MAILRU]: bare weblink keys
         * ("KVy7/S1hRRPVUE/...") are the historical Mail.ru format and remain
         * the default storage format.
         */
        fun detectOrDefault(input: String): LinkProvider = detect(input) ?: MAILRU
    }
}
