package com.example.data.api.mega

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * MEGA client-side cryptography (public links only, no account).
 *
 * The scheme mirrors the reference implementations (megajs 1.3.x, mega.py 1.0.8)
 * and was validated against live links:
 *
 *  - Node keys are AES-128-ECB encrypted with the shared folder key (16 bytes,
 *    taken verbatim from the folder link) or, for standalone file links, the
 *    43-char URL key already IS the merged 32-byte node key.
 *  - A merged 32-byte file key splits as:
 *        content AES key = key[0..15] XOR key[16..31]
 *        CTR nonce       = key[16..23]
 *        integrity MAC   = key[24..31]
 *  - File content is AES-128-CTR: IV block = nonce(8) || 8 zero bytes, the
 *    64-bit tail acts as the block counter (big-endian, wraps into the nonce).
 *  - Attributes are AES-128-CBC with an all-zero IV, zero padded, prefixed
 *    with the literal "MEGA", and contain JSON such as {"n": "name"}.
 *  - Integrity MAC: per-chunk CBC chains (chunk sizes 128K, 256K ... 1M,
 *    seeded with nonce||nonce) folded together through a CBC(0) chain and
 *    word-folded to 8 bytes.
 */
object MegaCrypto {

    const val BLOCK = 16

    /** First MEGA chunk size; every following chunk grows by this up to 1 MiB. */
    private const val CHUNK_STEP = 131072
    private const val MAX_CHUNK = 1048576

    /** Decrypted content key components of a file node. */
    data class ContentKey(
        val aesKey: ByteArray,   // 16 bytes
        val nonce: ByteArray,    // 8 bytes
        val mac: ByteArray      // 8 bytes (may be zeros for ancient 16-byte keys)
    )

    // ---------- base64url ----------

    private val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        .map { it.code }.toIntArray()

    fun base64UrlDecode(data: String): ByteArray {
        val clean = data.trim().replace(",", "")
        val out = ByteArray(clean.length * 3 / 4 + 3)
        var bits = 0
        var acc = 0
        var len = 0
        for (ch in clean) {
            if (ch == '=') break
            val v = charToValue(ch)
            acc = (acc shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out[len++] = ((acc shr bits) and 0xFF).toByte()
            }
        }
        return out.copyOf(len)
    }

    fun base64UrlEncode(data: ByteArray): String {
        val sb = StringBuilder((data.size * 4 + 2) / 3)
        var bits = 0
        var acc = 0
        for (b in data) {
            acc = (acc shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 6) {
                bits -= 6
                sb.append(valueToChar((acc shr bits) and 0x3F))
            }
        }
        if (bits > 0) {
            sb.append(valueToChar((acc shl (6 - bits)) and 0x3F))
        }
        return sb.toString()
    }

    private fun charToValue(c: Char): Int {
        val v = if (c in 'A'..'Z') c - 'A'
        else if (c in 'a'..'z') c - 'a' + 26
        else if (c in '0'..'9') c - '0' + 52
        else if (c == '-') 62
        else if (c == '_') 63
        else -1
        require(v >= 0) { "Invalid base64url character: $c" }
        return v
    }

    private fun valueToChar(v: Int): Char = B64[v].toChar()

    // ---------- key derivation ----------

    /**
     * Derives the content key from a merged 32-byte node key. Legacy 16-byte
     * keys are used verbatim with a zero nonce/mac (megajs-compatible).
     */
    fun deriveContentKey(mergedKey: ByteArray): ContentKey {
        require(mergedKey.size == 16 || mergedKey.size == 32) {
            "Merged MEGA key must be 16 or 32 bytes, got ${mergedKey.size}"
        }
        return if (mergedKey.size == 32) {
            val aes = ByteArray(16) { i -> (mergedKey[i].toInt() xor mergedKey[i + 16].toInt()).toByte() }
            ContentKey(aes, mergedKey.copyOfRange(16, 24), mergedKey.copyOfRange(24, 32))
        } else {
            ContentKey(mergedKey.copyOf(), ByteArray(8), ByteArray(8))
        }
    }

    /**
     * Extracts the 16-byte shared AES key from a MEGA link key: folder keys
     * (22 chars) are used directly, 32-byte keys are XOR-folded halves.
     */
    fun folderAesKey(keyBytes: ByteArray): ByteArray {
        require(keyBytes.size == 16 || keyBytes.size == 32) {
            "MEGA link key must be 16 or 32 bytes, got ${keyBytes.size}"
        }
        return if (keyBytes.size == 32) {
            ByteArray(16) { i -> (keyBytes[i].toInt() xor keyBytes[i + 16].toInt()).toByte() }
        } else {
            keyBytes.copyOf()
        }
    }

    // ---------- ciphers ----------

    private fun aesKey(key: ByteArray): SecretKeySpec =
        SecretKeySpec(key, "AES")

    /** AES-128-ECB decryptor used for node keys (no padding — keys are block sized). */
    fun ecbDecryptCipher(key: ByteArray): Cipher =
        Cipher.getInstance("AES/ECB/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, aesKey(key))
        }

    /**
     * Builds the AES-128-CTR content cipher positioned at [byteOffset]
     * (must be a multiple of 16 — matches megajs megaDecrypt(start)).
     */
    fun contentCipher(key: ByteArray, nonce: ByteArray, byteOffset: Long = 0L): Cipher {
        require(key.size == 16) { "CTR key must be 16 bytes" }
        require(nonce.size == 8) { "CTR nonce must be 8 bytes" }
        val iv = ByteArray(16)
        nonce.copyInto(iv, 0)
        if (byteOffset > 0) {
            require(byteOffset % BLOCK == 0L) { "CTR offset must be block aligned" }
            addCounter(iv, byteOffset / BLOCK)
        }
        return Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, aesKey(key), IvParameterSpec(iv))
        }
    }

    /** Big-endian increment of the full 16-byte counter block (megajs semantics). */
    private fun addCounter(iv: ByteArray, blocks: Long) {
        var carry = blocks
        var i = 15
        while (carry > 0 && i >= 0) {
            val sum = (iv[i].toLong() and 0xFF) + (carry and 0xFF)
            iv[i] = (sum and 0xFF).toByte()
            carry = (carry shr 8) + (sum shr 8)
            i--
        }
    }

    /**
     * Decrypts an attributes blob ("at" / node "a" field) and returns the raw
     * plaintext bytes (the leading "MEGA" marker and zero padding are stripped).
     */
    fun decryptAttributesRaw(key: ByteArray, attributesB64: String): ByteArray? {
        return try {
            val encrypted = base64UrlDecode(attributesB64)
            if (encrypted.isEmpty() || encrypted.size % BLOCK != 0) return null
            val cipher = Cipher.getInstance("AES/CBC/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, aesKey(key), IvParameterSpec(ByteArray(BLOCK)))
            }
            val plain = cipher.doFinal(encrypted)
            var end = plain.size
            while (end > 0 && plain[end - 1] == 0.toByte()) end--
            var start = 0
            if (end >= 4 && plain[0] == 'M'.code.toByte() && plain[1] == 'E'.code.toByte() &&
                plain[2] == 'G'.code.toByte() && plain[3] == 'A'.code.toByte()
            ) {
                start = 4
            }
            if (end <= start) null else plain.copyOfRange(start, end)
        } catch (_: Exception) {
            null
        }
    }

    private val moshi = Moshi.Builder().build()
    private val attrsAdapter = moshi.adapter<Map<String, Any>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    /**
     * Decrypts an attributes blob and parses its JSON. Returns null when the
     * key is wrong or the payload is malformed.
     */
    fun decryptAttributes(key: ByteArray, attributesB64: String): Map<String, Any>? {
        val raw = decryptAttributesRaw(key, attributesB64) ?: return null
        return try {
            attrsAdapter.fromJson(String(raw, Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    /** Convenience: decrypted display name of a node. */
    fun decryptAttrName(key: ByteArray, attributesB64: String): String? =
        decryptAttributes(key, attributesB64)?.get("n") as? String

    // ---------- integrity MAC ----------

    /**
     * Streaming MEGA content MAC (OLYMPIA-style). Feed DECRYPTED bytes in any
     * chunking via [update]; [verify] must be called once the full plaintext
     * has been consumed.
     *
     * Mechanics: plaintext is processed block by block through a CBC chain
     * seeded with nonce||nonce. At every canonical MEGA chunk boundary
     * (128K, 384K, 768K, ...) the chain output X is folded into an outer
     * CBC(0) chain (state = E(state ^ X)) and the inner chain resets.
     * The final 8 bytes are the word-fold of the outer state; they must equal
     * key[24..31].
     */
    class MegaMac(contentKey: ContentKey) {

        private val aesKeyBytes = contentKey.aesKey
        private val nonce = contentKey.nonce
        private val expected = contentKey.mac

        private val ecb = Cipher.getInstance("AES/ECB/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, aesKey(aesKeyBytes))
        }

        private var chunkCipher = newChunkCipher()
        private val carry = ByteArray(BLOCK)
        private var carryLen = 0

        private var foldState = ByteArray(BLOCK)
        private var pendingX: ByteArray? = null

        private var pos = 0L
        private var chunkEnd = CHUNK_STEP.toLong()
        private var step = CHUNK_STEP.toLong()

        private fun newChunkCipher(): Cipher =
            Cipher.getInstance("AES/CBC/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, aesKey(aesKeyBytes), IvParameterSpec(nonce + nonce))
            }

        /** Processes decrypted plaintext bytes. */
        fun update(buffer: ByteArray, offset: Int, length: Int) {
            var off = offset
            var len = length
            while (len > 0) {
                val take = minOf(len, BLOCK - carryLen)
                System.arraycopy(buffer, off, carry, carryLen, take)
                carryLen += take
                off += take
                len -= take
                if (carryLen == BLOCK) {
                    processBlock(carry)
                    carryLen = 0
                }
            }
        }

        private fun processBlock(block: ByteArray) {
            val out = chunkCipher.update(block, 0, BLOCK)
            requireNotNull(out) { "CBC cipher buffered an unexpected partial block" }
            pos += BLOCK
            pendingX = out
            if (pos == chunkEnd) {
                foldPending()
                if (step < MAX_CHUNK) step += CHUNK_STEP
                chunkEnd += step
                chunkCipher = newChunkCipher()
            }
        }

        private fun foldPending() {
            val x = pendingX ?: return
            pendingX = null
            val combined = ByteArray(BLOCK) { i -> (foldState[i].toInt() xor x[i].toInt()).toByte() }
            foldState = ecb.doFinal(combined)
        }

        /** Verifies the MAC once all plaintext has been fed; flushes a pending tail. */
        fun verify(): Boolean {
            if (carryLen > 0) {
                // zero-pad the final partial block (server-side convention)
                for (i in carryLen until BLOCK) carry[i] = 0
                processBlock(carry)
                carryLen = 0
            }
            foldPending()
            var a = 0
            var b = 0
            for (i in 0 until 4) {
                a = a or ((foldState[i].toInt() and 0xFF) shl (24 - i * 8))
                b = b or ((foldState[i + 8].toInt() and 0xFF) shl (24 - i * 8))
            }
            val c = ((foldState[4].toInt() and 0xFF) shl 24) or ((foldState[5].toInt() and 0xFF) shl 16) or
                    ((foldState[6].toInt() and 0xFF) shl 8) or (foldState[7].toInt() and 0xFF)
            val d = ((foldState[12].toInt() and 0xFF) shl 24) or ((foldState[13].toInt() and 0xFF) shl 16) or
                    ((foldState[14].toInt() and 0xFF) shl 8) or (foldState[15].toInt() and 0xFF)
            val wa = a xor c
            val wb = b xor d
            var diff = 0
            for (i in 0 until 4) {
                diff = diff or ((wa ushr (24 - i * 8)) and 0xFF xor (expected[i].toInt() and 0xFF))
                diff = diff or ((wb ushr (24 - i * 8)) and 0xFF xor (expected[i + 4].toInt() and 0xFF))
            }
            return diff == 0
        }
    }

    /** Canonical cumulative MEGA chunk boundary sizes: 128K, 256K, 384K, ... capped at 1M steps. */
    fun chunkBoundary(index: Int): Long {
        var boundary = 0L
        var step = CHUNK_STEP.toLong()
        repeat(index) {
            boundary += step
            if (step < MAX_CHUNK) step += CHUNK_STEP
        }
        return boundary
    }

    private fun chunkAt(index: Int): Long = chunkBoundary(index)

    // ---------- hashcash (anti-abuse PoW, mirrors megajs) ----------

    /**
     * Solves an X-Hashcash challenge. Challenge format: "1:{easiness}:{?}:{token}".
     * Returns the "1:{token}:{counter}" proof or null when unsupported.
     */
    fun solveHashcash(challenge: String, maxIterations: Long = 8_000_000L): String? {
        val parts = challenge.trim().split(":")
        if (parts.size < 4 || parts[0] != "1") return null
        val easiness = parts[1].toIntOrNull() ?: return null
        val tokenStr = parts[3]
        val token = base64UrlDecode(tokenStr)
        if (token.isEmpty()) return null

        val base = ((easiness and 63) shl 1) + 1
        val shifts = (easiness shr 6) * 7 + 3
        val threshold = (base.toLong() shl shifts)

        val buffer = ByteArray(4 + 262144 * 48)
        for (i in 0 until 262144) {
            token.copyInto(buffer, 4 + i * 48)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var counter = 0L
        while (counter < maxIterations) {
            val h = digest.digest(buffer)
            val first = ((h[0].toLong() and 0xFF) shl 24) or
                    ((h[1].toLong() and 0xFF) shl 16) or
                    ((h[2].toLong() and 0xFF) shl 8) or
                    (h[3].toLong() and 0xFF)
            if (first <= threshold) {
                val counterBytes = byteArrayOf(
                    (counter ushr 24).toByte(),
                    (counter ushr 16).toByte(),
                    (counter ushr 8).toByte(),
                    counter.toByte()
                )
                return "1:$tokenStr:${base64UrlEncode(counterBytes)}"
            }
            // megajs increments buffer[0] with carry towards higher indexes
            var j = 0
            while (true) {
                buffer[j] = (buffer[j].toInt() + 1).toByte()
                if (buffer[j].toInt() != 0) break
                j++
                if (j >= 4) break
            }
            counter++
        }
        return null
    }
}
