package com.example.data.api.mega

import android.util.Log
import com.example.data.api.NetworkModule
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Minimal MEGA API client for anonymous public-link access.
 *
 * Endpoint: POST https://g.api.mega.co.nz/cs?id={seq}[&n={folderHandle}]
 * Body: a JSON array with a single command. A scalar negative integer in the
 * response array position is an API error; -3 (EAGAIN) is retried with
 * exponential backoff. The server may answer with an X-Hashcash challenge
 * header instead of a payload — the proof-of-work is solved in [MegaCrypto.solveHashcash].
 */
class MegaApi(
    private val client: OkHttpClient = NetworkModule.okHttpClient
) {

    companion object {
        private const val TAG = "MegaApi"
        private const val GATEWAY = "https://g.api.mega.co.nz/cs"
        private const val MAX_API_RETRIES = 3
        private const val MAX_POW_ATTEMPTS = 3

        @Volatile
        private var singleton: MegaApi? = null

        /** Process-wide instance sharing the app's OkHttp connection pool. */
        fun get(): MegaApi = singleton ?: synchronized(this) {
            singleton ?: MegaApi().also { singleton = it }
        }
    }

    class MegaApiException(val code: Int, message: String) : RuntimeException(message)

    private val moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter<Map<String, Any>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )
    private val listAdapter = moshi.adapter<List<Any>>(
        Types.newParameterizedType(List::class.java, Any::class.java)
    )
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val seqCounter = java.util.concurrent.atomic.AtomicLong(0)

    /**
     * Executes one CS command. Returns the command payload (first element of
     * the response array) or throws [MegaApiException].
     *
     * @param command the command map, e.g. {"a":"g","p":handle,"g":1}
     * @param query extra URL query parameters (e.g. n=folderHandle for public folders)
     */
    suspend fun request(command: Map<String, Any>, query: Map<String, String> = emptyMap()): Any =
        withContext(Dispatchers.IO) {
            var attempt = 0
            var powAttempt = 0
            var hashcashHeader: String? = null
            var result: Any? = null
            while (result == null) {
                val seq = seqCounter.incrementAndGet()
                val url = buildString {
                    append(GATEWAY)
                    append("?id=").append(seq)
                    query.forEach { (k, v) ->
                        append('&').append(k).append('=').append(java.net.URLEncoder.encode(v, "UTF-8"))
                    }
                }
                val body = listAdapter.toJson(listOf(command))
                val builder = Request.Builder()
                    .url(url)
                    .post(body.toRequestBody(jsonMediaType))
                    .header("Content-Type", "application/json")
                hashcashHeader?.let { builder.header("X-Hashcash", it) }

                val response = try {
                    client.newCall(builder.build()).execute()
                } catch (e: Exception) {
                    throw MegaApiException(-1000, mapNetworkError(e))
                }
                response.use { resp ->
                    val challenge = resp.header("X-Hashcash")
                    if (challenge != null) {
                        if (powAttempt >= MAX_POW_ATTEMPTS) {
                            throw MegaApiException(-1001, "Mega запрашивает проверку защиты от злоупотреблений; повторите позже")
                        }
                        powAttempt++
                        val token = MegaCrypto.solveHashcash(challenge)
                        if (token == null) {
                            throw MegaApiException(-1001, "Не удалось пройти проверку Mega (hashcash)")
                        }
                        hashcashHeader = token
                        return@use // retry with the proof attached
                    }
                    if (!resp.isSuccessful) {
                        throw MegaApiException(-1000, "HTTP ${resp.code}")
                    }
                    val text = resp.body?.string() ?: throw MegaApiException(-1000, "Пустой ответ Mega API")
                    val parsed = try {
                        listAdapter.fromJson(text)
                    } catch (e: Exception) {
                        throw MegaApiException(-1000, "Некорректный ответ Mega API")
                    }
                    val payload = parsed?.firstOrNull() ?: throw MegaApiException(-1000, "Пустой ответ Mega API")
                    if (payload is Number) {
                        val code = payload.toInt()
                        if (code < 0) {
                            if (code == -3 && attempt < MAX_API_RETRIES) {
                                attempt++
                                delay(1000L * (1 shl attempt)) // 2s, 4s, 8s
                                return@use // EAGAIN — retry
                            }
                            throw MegaApiException(code, describeError(code))
                        }
                    }
                    result = payload
                }
            }
            result!!
        }

    // ---------- typed operations ----------

    data class MegaFileMeta(
        val name: String?,
        val size: Long,
        val timestamp: Long,
        val attributesRaw: String?,
        val tempUrl: String?
    )

    /** Metadata + one-time download URL of a standalone public file (a=g&p=handle). */
    suspend fun getPublicFileMeta(handle: String): MegaFileMeta {
        val payload = request(linkedMapOf("a" to "g", "p" to handle, "g" to 1))
        return parseFileMeta(payload)
    }

    /** Metadata + one-time download URL of a file inside a public folder (a=g&n=file, query n=folder). */
    suspend fun getFolderFileMeta(folderHandle: String, fileHandle: String): MegaFileMeta {
        val payload = request(
            linkedMapOf("a" to "g", "n" to fileHandle, "g" to 1),
            query = mapOf("n" to folderHandle)
        )
        return parseFileMeta(payload)
    }

    /** Raw node list of a public folder (a=f, query n=folderHandle). */
    suspend fun getPublicFolderNodes(folderHandle: String): List<Map<String, Any>> {
        val payload = request(
            linkedMapOf("a" to "f", "c" to 1, "r" to 1),
            query = mapOf("n" to folderHandle)
        )
        val map = payload as? Map<*, *> ?: throw MegaApiException(-1000, "Некорректный ответ Mega API")
        @Suppress("UNCHECKED_CAST")
        val f = map["f"] as? List<Map<String, Any>> ?: emptyList()
        return f
    }

    private fun parseFileMeta(payload: Any): MegaFileMeta {
        val map = payload as? Map<*, *> ?: throw MegaApiException(-1000, "Некорректный ответ Mega API")
        val size = (map["s"] as? Number)?.toLong() ?: 0L
        val ts = (map["ts"] as? Number)?.toLong() ?: 0L
        val at = map["at"] as? String
        val url = map["g"] as? String
        if (url == null) {
            // "g" is absent when the file is temporarily unavailable
            throw MegaApiException(-18, "Файл временно недоступен на серверах Mega")
        }
        return MegaFileMeta(name = null, size = size, timestamp = ts, attributesRaw = at, tempUrl = url)
    }

    // ---------- errors ----------

    private fun describeError(code: Int): String = when (code) {
        -1 -> "Внутренняя ошибка Mega"
        -2 -> "Mega отклонил запрос (недопустимые аргументы)"
        -3 -> "Mega перегружен, повторите попытку"
        -4 -> "Превышен лимит запросов к Mega"
        -9 -> "Ссылка Mega не найдена или удалена (либо неверный ключ)"
        -11 -> "Доступ к объекту Mega запрещён"
        -14 -> "Неверный ключ расшифровки Mega"
        -16 -> "Ссылка Mega заблокирована (нарушение правил)"
        -17 -> "Превышена квота передачи Mega"
        -18 -> "Файл временно недоступен на серверах Mega"
        -19 -> "Превышена квота передачи Mega, попробуйте позже"
        else -> "Ошибка Mega API ($code)"
    }

    private fun mapNetworkError(e: Exception): String {
        val msg = e.message ?: e.javaClass.simpleName
        return when {
            msg.contains("Unable to resolve host") || msg.contains("UnknownHostException") ->
                "Отсутствует подключение к интернету"
            msg.contains("timeout", ignoreCase = true) ->
                "Превышено время ожидания ответа от серверов Mega"
            else -> "Ошибка сети Mega: $msg"
        }
    }
}
