package com.example.data.api.mega

/**
 * Parsing of public MEGA links and stable identity keys.
 *
 * Supported URL forms (host: mega.nz, mega.co.nz, mega.io):
 *  - new file:    https://mega.nz/file/{handle}#{key}
 *  - old file:    https://mega.nz/#!{handle}!{key}
 *  - new folder:  https://mega.nz/folder/{handle}#{key}
 *  - old folder:  https://mega.nz/#F!{handle}!{key}
 *
 * Because a MEGA folder has no per-subfolder public URL (unlike Mail.ru),
 * navigation inside the app uses composite identity keys that carry all the
 * crypto material needed to re-fetch and decrypt a node later:
 *
 *  - "mega:{folderHandle}:{folderKey}"                        — shared folder root
 *  - "mega:{folderHandle}:{folderKey}:{node}"                 — subfolder node
 *  - "mega:{folderHandle}:{folderKey}:{node}:{nodeKeyB64}"    — file node (key embedded)
 *  - "megafile:{handle}:{key}"                                — standalone shared file
 *
 * Keys are stored as base64url exactly as they appear in the link, so the
 * original URL can always be reconstructed losslessly.
 */
object MegaLinks {

    const val PROVIDER_MEGA = "mega"
    const val PROVIDER_MAILRU = "mailru"

    private const val PREFIX_FOLDER = "mega"
    private const val PREFIX_FILE = "megafile"

    /** Number of characters in base64url for a 32-byte (file) key. */
    private const val KEY32_LEN = 43
    /** Number of characters in base64url for a 16-byte (folder) key. */
    private const val KEY16_LEN = 22

    data class MegaLink(
        val handle: String,
        val keyB64: String,
        val isFolder: Boolean
    ) {
        /** "mega:H:K" or "megafile:H:K" — stable identity of the shared root. */
        val identityKey: String
            get() = if (isFolder) {
                "$PREFIX_FOLDER:$handle:$keyB64"
            } else {
                "$PREFIX_FILE:$handle:$keyB64"
            }

        /** Canonical public URL for this link. */
        val publicUrl: String
            get() = if (isFolder) {
                "https://mega.nz/folder/$handle#$keyB64"
            } else {
                "https://mega.nz/file/$handle#$keyB64"
            }
    }

    /**
     * A node reference decoded from a composite identity key.
     * [nodeHandle] is null for the folder root itself. [nodeKeyB64] carries the
     * decrypted merged node key when the node is a file (download fast-path).
     */
    data class MegaIdentity(
        val folderHandle: String,
        val folderKeyB64: String,
        val nodeHandle: String? = null,
        val nodeKeyB64: String? = null,
        val isFileLink: Boolean = false
    )

    // Note: {43} must come FIRST in the alternation — otherwise {22}
    // eagerly matches the first 22 characters of a 43-char key.
    private val hostRegex = Regex(
        pattern = "(?:mega\\.nz|mega\\.co\\.nz|mega\\.io)/(?:file|folder)/([A-Za-z0-9_-]{6,12})#([A-Za-z0-9_-]{43}|[A-Za-z0-9_-]{22})",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val legacyRegex = Regex(
        pattern = "(?:mega\\.nz|mega\\.co\\.nz|mega\\.io)/#(F?)!([A-Za-z0-9_-]{6,12})!([A-Za-z0-9_-]{43}|[A-Za-z0-9_-]{22})",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    /** Parses any supported MEGA public URL, with or without scheme. */
    fun parse(url: String): MegaLink? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        hostRegex.find(trimmed)?.let { m ->
            val handle = m.groupValues[1]
            val key = m.groupValues[2]
            val isFolder = m.value.contains("/folder/", ignoreCase = true)
            return MegaLink(handle, key, isFolder)
        }
        legacyRegex.find(trimmed)?.let { m ->
            val isFolder = m.groupValues[1] == "F"
            val handle = m.groupValues[2]
            val key = m.groupValues[3]
            return MegaLink(handle, key, isFolder)
        }
        return null
    }

    /** True when the string is a MEGA composite identity key produced by this parser family. */
    fun isMegaWeblink(value: String): Boolean =
        value.startsWith("$PREFIX_FOLDER:") || value.startsWith("$PREFIX_FILE:")

    /** Decodes a composite identity key; returns null for non-mega strings. */
    fun parseIdentity(value: String): MegaIdentity? {
        if (!isMegaWeblink(value)) return null
        val parts = value.split(":")
        return when {
            parts[0] == PREFIX_FILE && parts.size == 3 ->
                MegaIdentity(parts[1], parts[2], isFileLink = true)
            parts[0] == PREFIX_FOLDER && parts.size == 3 ->
                MegaIdentity(parts[1], parts[2])
            parts[0] == PREFIX_FOLDER && parts.size == 4 ->
                MegaIdentity(parts[1], parts[2], nodeHandle = parts[3])
            parts[0] == PREFIX_FOLDER && parts.size == 5 ->
                MegaIdentity(parts[1], parts[2], nodeHandle = parts[3], nodeKeyB64 = parts[4])
            else -> null
        }
    }

    /**
     * Builds the composite identity for a node that lives inside a shared
     * folder. [nodeHandle] must not be blank.
     */
    fun folderNodeIdentity(folderHandle: String, folderKeyB64: String, nodeHandle: String): String =
        "$PREFIX_FOLDER:$folderHandle:$folderKeyB64:$nodeHandle"

    /** Builds the composite identity for a standalone shared file. */
    fun fileIdentity(handle: String, keyB64: String): String =
        "$PREFIX_FILE:$handle:$keyB64"

    /** Reconstructs a shareable public URL from any composite identity. */
    fun toPublicUrl(identity: MegaIdentity): String {
        return if (identity.isFileLink) {
            "https://mega.nz/file/${identity.folderHandle}#${identity.folderKeyB64}"
        } else {
            "https://mega.nz/folder/${identity.folderHandle}#${identity.folderKeyB64}"
        }
    }

    /** Quick sanity check of the key length in a parsed link. */
    fun hasValidKeyLength(link: MegaLink): Boolean =
        link.keyB64.length == KEY16_LEN || link.keyB64.length == KEY32_LEN
}
