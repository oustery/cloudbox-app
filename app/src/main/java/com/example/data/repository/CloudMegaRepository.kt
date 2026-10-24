package com.example.data.repository

import android.util.Log
import com.example.data.api.NetworkModule
import com.example.data.api.mega.MegaApi
import com.example.data.api.mega.MegaCrypto
import com.example.data.api.mega.MegaDecryptInputStream
import com.example.data.api.mega.MegaLinks
import com.example.data.model.CloudItem
import com.example.data.model.FolderCount
import com.example.data.model.FolderTotal
import java.io.File
import java.io.InputStream

/**
 * Repository for MEGA public links. Produces the same [FolderResult] /
 * [CloudItem] models as [CloudMailRuRepository] so the explorer, pool and
 * download pipeline work without provider-specific UI logic.
 *
 * Item identity ([CloudItem.weblink]) uses composite keys from [MegaLinks]:
 *  - "mega:{folderHandle}:{folderKey}"                      — folder root
 *  - "mega:{folderHandle}:{folderKey}:{nodeHandle}"         — subfolder node
 *  - "mega:{folderHandle}:{folderKey}:{nodeHandle}:{keyB64}"— file node (key embedded)
 *  - "megafile:{handle}:{keyB64}"                           — standalone file link
 *
 * Embedded node keys are client-side decryption keys of PUBLIC shared content —
 * the same trust level as the link itself (this is how MEGA public links work).
 */
class CloudMegaRepository(
    private val api: MegaApi = MegaApi.get()
) {

    companion object {
        private const val TAG = "CloudMegaRepo"
        private const val CACHE_DIR = "mega_media"
    }

    /**
     * Fetches and decrypts the content behind a MEGA link or identity key.
     * Accepts full URLs (https://mega.nz/...) and composite identity keys.
     */
    suspend fun fetchFolderContent(rawUrlOrIdentity: String): Result<FolderResult> {
        val identity = resolveIdentity(rawUrlOrIdentity)
            ?: return Result.failure(IllegalArgumentException("Неверный формат ссылки Mega.nz"))
        return try {
            if (identity.isFileLink) fetchStandaloneFile(identity) else fetchFolder(identity)
        } catch (e: MegaApi.MegaApiException) {
            Log.w(TAG, "MEGA api error: ${e.message}")
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Log.w(TAG, "MEGA fetch failed", e)
            Result.failure(Exception("Не удалось загрузить содержимое Mega: ${e.message ?: e.javaClass.simpleName}"))
        }
    }

    /** Parses both full URLs and composite identity keys. */
    private fun resolveIdentity(raw: String): MegaLinks.MegaIdentity? {
        MegaLinks.parseIdentity(raw)?.let { return it }
        val link = MegaLinks.parse(raw) ?: return null
        return MegaLinks.parseIdentity(link.identityKey)
    }

    // ---------- standalone file link ----------

    private suspend fun fetchStandaloneFile(identity: MegaLinks.MegaIdentity): Result<FolderResult> {
        val meta = api.getPublicFileMeta(identity.folderHandle)
        val merged = MegaCrypto.base64UrlDecode(identity.folderKeyB64)
        val contentKey = MegaCrypto.deriveContentKey(merged)
        val name = meta.attributesRaw?.let { MegaCrypto.decryptAttrName(contentKey.aesKey, it) }
            ?: "Файл Mega"
        val weblink = MegaLinks.fileIdentity(identity.folderHandle, identity.folderKeyB64)
        val item = CloudItem(
            kind = "file",
            name = name,
            size = meta.size,
            mtime = meta.timestamp,
            weblink = weblink
        )
        return Result.success(FolderResult(weblink, name, listOf(item), null, null))
    }

    // ---------- folder link ----------

    private suspend fun fetchFolder(identity: MegaLinks.MegaIdentity): Result<FolderResult> {
        val folderKey = MegaCrypto.folderAesKey(MegaCrypto.base64UrlDecode(identity.folderKeyB64))
        val nodes = api.getPublicFolderNodes(identity.folderHandle)
        if (nodes.isEmpty()) {
            return Result.failure(Exception("Папка Mega пуста или недоступна"))
        }

        data class Node(
            val handle: String,
            val parent: String,
            val isFolder: Boolean,
            val size: Long,
            val timestamp: Long,
            val name: String?,
            val mergedKey: ByteArray?,
            val attributesB64: String?
        )

        // Root node: megajs convention — the node whose owner prefix in "k"
        // equals its own handle; fall back to the first folder node.
        val rootHandle: String = nodes.firstOrNull { node ->
            val h = node["h"] as? String
            val k = node["k"] as? String
            h != null && k != null && k.substringBefore(':') == h
        }?.get("h") as? String
            ?: nodes.firstOrNull { ((it["t"] as? Number)?.toInt() ?: 0) == 1 }?.get("h") as? String
            ?: nodes.firstOrNull()?.get("h") as? String
            ?: return Result.failure(Exception("Папка Mega повреждена"))

        val byHandle = LinkedHashMap<String, Node>()
        val childrenOf = HashMap<String, MutableList<Node>>()
        val folderKeys = HashMap<String, ByteArray>()   // decrypted subfolder keys

        for (node in nodes) {
            val handle = node["h"] as? String ?: continue
            val parent = node["p"] as? String ?: ""
            val type = (node["t"] as? Number)?.toInt() ?: 0
            val size = (node["s"] as? Number)?.toLong() ?: 0L
            val ts = (node["ts"] as? Number)?.toLong() ?: 0L
            val attrsB64 = node["a"] as? String
            val kField = node["k"] as? String ?: ""

            var merged: ByteArray? = null
            var name: String? = null
            if (kField.isNotBlank()) {
                // Primary candidate: the shared folder key. Fallback: the
                // parent's own key (for trees keyed per-level).
                val candidates = ArrayList<ByteArray>(2)
                candidates.add(folderKey)
                folderKeys[parent]?.let { candidates.add(it) }
                val encrypted = try {
                    MegaCrypto.base64UrlDecode(kField.substringAfterLast(':').substringAfterLast('/'))
                } catch (_: Exception) {
                    null
                }
                if (encrypted != null && encrypted.size % MegaCrypto.BLOCK == 0) {
                    for (key in candidates) {
                        val decrypted = try {
                            MegaCrypto.ecbDecryptCipher(key).doFinal(encrypted)
                        } catch (_: Exception) {
                            continue
                        }
                        val contentKey = MegaCrypto.deriveContentKey(decrypted)
                        val decryptedName = attrsB64?.let { MegaCrypto.decryptAttrName(contentKey.aesKey, it) }
                        if (decryptedName != null) {
                            merged = decrypted
                            name = decryptedName
                            break
                        }
                    }
                }
                if (type == 1 && merged != null) {
                    folderKeys[handle] = merged
                }
            }
            val parsed = Node(handle, parent, type == 1, size, ts, name, merged, attrsB64)
            byHandle[handle] = parsed
            childrenOf.getOrPut(parent) { mutableListOf() }.add(parsed)
        }

        // Which node are we listing? Root or a nested subfolder.
        val targetHandle = identity.nodeHandle ?: rootHandle
        val target = byHandle[targetHandle]
            ?: return Result.failure(Exception("Узел Mega не найден в папке"))

        if (!target.isFolder) {
            // A file node opened as a folder — surface it as a single-file view.
            val weblink = fileNodeIdentity(identity, targetHandle, target.mergedKey)
            val item = CloudItem(
                kind = "file",
                name = target.name ?: "Файл Mega",
                size = target.size,
                mtime = target.timestamp,
                weblink = weblink
            )
            return Result.success(FolderResult(weblink, item.name, listOf(item), null, null))
        }

        // Aggregate subtree stats for folder badges.
        val subtreeFiles = HashMap<String, Int>()
        val subtreeFolders = HashMap<String, Int>()
        val subtreeSize = HashMap<String, Long>()

        fun collect(handle: String): Triple<Int, Int, Long> {
            val cached = subtreeFiles[handle]
            if (cached != null) {
                return Triple(cached, subtreeFolders[handle] ?: 0, subtreeSize[handle] ?: 0L)
            }
            var files = 0
            var folders = 0
            var size = 0L
            childrenOf[handle]?.forEach { child ->
                if (child.isFolder) {
                    val (f, fo, s) = collect(child.handle)
                    files += f
                    folders += fo + 1
                    size += s
                } else {
                    files++
                    size += child.size
                }
            }
            subtreeFiles[handle] = files
            subtreeFolders[handle] = folders
            subtreeSize[handle] = size
            return Triple(files, folders, size)
        }

        val items = (childrenOf[targetHandle] ?: emptyList()).map { child ->
            if (child.isFolder) {
                val (files, folders, _) = collect(child.handle)
                CloudItem(
                    kind = "folder",
                    name = child.name ?: "Папка Mega",
                    size = 0L,
                    mtime = child.timestamp,
                    weblink = MegaLinks.folderNodeIdentity(identity.folderHandle, identity.folderKeyB64, child.handle),
                    count = FolderCount(folders = folders, files = files)
                )
            } else {
                CloudItem(
                    kind = "file",
                    name = child.name ?: "Файл Mega",
                    size = child.size,
                    mtime = child.timestamp,
                    weblink = fileNodeIdentity(identity, child.handle, child.mergedKey)
                )
            }
        }

        val (files, folders, size) = collect(targetHandle)
        val displayName = target.name
            ?: byHandle[rootHandle]?.name
            ?: "Папка Mega"

        return Result.success(
            FolderResult(
                weblinkKey = MegaLinks.folderNodeIdentity(identity.folderHandle, identity.folderKeyB64, targetHandle),
                name = displayName,
                items = items,
                count = FolderCount(folders = folders, files = files),
                total = FolderTotal(folders = folders, files = files, size = size)
            )
        )
    }

    private fun fileNodeIdentity(
        folderIdentity: MegaLinks.MegaIdentity,
        nodeHandle: String,
        mergedKey: ByteArray?
    ): String {
        return if (mergedKey != null) {
            "mega:${folderIdentity.folderHandle}:${folderIdentity.folderKeyB64}:$nodeHandle:" +
                    MegaCrypto.base64UrlEncode(mergedKey)
        } else {
            MegaLinks.folderNodeIdentity(folderIdentity.folderHandle, folderIdentity.folderKeyB64, nodeHandle)
        }
    }

    // ---------- downloads ----------

    data class MegaDownloadTarget(
        val mergedKey: ByteArray,
        val size: Long,
        val tempUrl: String
    )

    /**
     * Resolves a fresh one-time download URL for a node. Uses the key embedded
     * in the composite weblink, so no extra folder request is needed.
     * MEGA temp URLs expire, so this is called once per download attempt.
     */
    suspend fun resolveDownloadTarget(weblink: String): MegaDownloadTarget {
        val identity = MegaLinks.parseIdentity(weblink)
            ?: throw IllegalArgumentException("Неверный ключ Mega для загрузки")
        val merged: ByteArray = when {
            identity.isFileLink -> MegaCrypto.base64UrlDecode(identity.folderKeyB64)
            identity.nodeKeyB64 != null -> MegaCrypto.base64UrlDecode(identity.nodeKeyB64)
            else -> throw IllegalArgumentException("Это папка Mega, а не файл")
        }
        val meta = if (identity.isFileLink) {
            api.getPublicFileMeta(identity.folderHandle)
        } else {
            api.getFolderFileMeta(identity.folderHandle, identity.nodeHandle!!)
        }
        return MegaDownloadTarget(
            mergedKey = merged,
            size = meta.size,
            tempUrl = meta.tempUrl
                ?: throw MegaApi.MegaApiException(-18, "Файл временно недоступен на серверах Mega")
        )
    }

    /** Wraps an HTTP stream of the temp URL into a decrypting stream. */
    fun decryptStream(source: InputStream, target: MegaDownloadTarget): InputStream {
        val contentKey = MegaCrypto.deriveContentKey(target.mergedKey)
        return MegaDecryptInputStream(source, contentKey, target.size, verifyMac = true)
    }

    /**
     * Downloads a file into the app cache (used for in-app image previews).
     * Cached files are named "{nodeHandle}_{originalName}" and reused.
     */
    suspend fun downloadToCache(weblink: String, fileName: String, cacheDir: File): Result<File> {
        return try {
            val dir = File(cacheDir, CACHE_DIR).apply { mkdirs() }
            val identity = MegaLinks.parseIdentity(weblink)
                ?: return Result.failure(IllegalArgumentException("Неверный ключ Mega"))
            val handle = identity.nodeHandle ?: identity.folderHandle
            val existing = dir.listFiles()?.firstOrNull { it.name.startsWith("${handle}_") }
            if (existing != null && existing.length() > 0) {
                return Result.success(existing)
            }
            val target = resolveDownloadTarget(weblink)
            val request = okhttp3.Request.Builder().url(target.tempUrl).build()
            val response = NetworkModule.okHttpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    return Result.failure(Exception("HTTP ${resp.code} при загрузке Mega"))
                }
                val body = resp.body
                    ?: return Result.failure(Exception("Пустой ответ Mega"))
                val temp = File.createTempFile("mega_", ".bin", dir)
                try {
                    body.byteStream().use { raw ->
                        decryptStream(raw, target).use { decrypted ->
                            temp.outputStream().use { output ->
                                decrypted.copyTo(output, 64 * 1024)
                            }
                        }
                    }
                    val safe = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        .ifBlank { "file_${handle}" }
                    val finalFile = File(dir, "${handle}_$safe")
                    if (!temp.renameTo(finalFile)) {
                        temp.copyTo(finalFile, overwrite = true)
                        temp.delete()
                    }
                    Result.success(finalFile)
                } catch (e: Exception) {
                    temp.delete()
                    throw e
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Mega cache download failed: ${e.message}")
            Result.failure(Exception(e.message ?: "Не удалось загрузить файл Mega"))
        }
    }
}
