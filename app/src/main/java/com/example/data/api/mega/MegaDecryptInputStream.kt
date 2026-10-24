package com.example.data.api.mega

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import javax.crypto.Cipher

/**
 * Decrypting stream for MEGA file content.
 *
 * The upstream stream yields raw CIPHERTEXT (AES-128-CTR); this stream yields
 * plaintext of exactly [size] bytes (CTR adds no padding, so ciphertext length
 * equals plaintext length). At EOF the MEGA integrity MAC is verified when
 * [verifyMac] is set; a mismatch throws [MacMismatchException].
 */
class MegaDecryptInputStream(
    source: InputStream,
    contentKey: MegaCrypto.ContentKey,
    private val size: Long,
    private val verifyMac: Boolean = true
) : FilterInputStream(source) {

    class MacMismatchException : IOException("Проверка целостности Mega не пройдена (неверный ключ или повреждённые данные)")

    private val cipher: Cipher = MegaCrypto.contentCipher(contentKey.aesKey, contentKey.nonce)
    private val mac: MegaCrypto.MegaMac? = if (verifyMac) MegaCrypto.MegaMac(contentKey) else null

    private val cipherBuffer = ByteArray(BUFFER)
    private val outBuffer = ByteArray(BUFFER)
    private var outPos = 0
    private var outLen = 0
    private var consumedPlaintext = 0L
    private var sourceExhausted = false
    private var macVerified = false

    init {
        require(size >= 0) { "size must be non-negative" }
        if (size == 0L) {
            sourceExhausted = true
            macVerified = true
        }
    }

    override fun read(): Int {
        if (!ensureAvailable()) return -1
        return outBuffer[outPos++].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (!ensureAvailable()) return -1
        val n = minOf(len, outLen - outPos)
        System.arraycopy(outBuffer, outPos, b, off, n)
        outPos += n
        return n
    }

    override fun available(): Int = outLen - outPos

    /** Returns true when decrypted bytes are buffered; false at EOF. */
    private fun ensureAvailable(): Boolean {
        if (outPos < outLen) return true
        if (sourceExhausted || consumedPlaintext >= size) {
            finishIfNeeded()
            return false
        }
        while (outPos >= outLen) {
            if (sourceExhausted || consumedPlaintext >= size) break
            val want = minOf(BUFFER, (size - consumedPlaintext).toInt().coerceAtLeast(0))
            if (want == 0) break
            val got = readAtLeast(want)
            if (got == 0) {
                sourceExhausted = true
                break
            }
            val produced = cipher.update(cipherBuffer, 0, got, outBuffer, 0)
            if (produced < 0) throw IOException("Ошибка расшифровки Mega (CTR)")
            outPos = 0
            outLen = produced
            consumedPlaintext += produced
            mac?.update(outBuffer, 0, produced)
        }
        if (outPos >= outLen) {
            finishIfNeeded()
            return false
        }
        return true
    }

    /** Reads up to [want] bytes blocking until at least one byte is available. */
    private fun readAtLeast(want: Int): Int {
        var total = 0
        while (total < want) {
            val n = super.read(cipherBuffer, total, want - total)
            if (n == -1) break
            total += n
        }
        return total
    }

    private fun finishIfNeeded() {
        if (macVerified) return
        macVerified = true
        try {
            cipher.doFinal()
        } catch (_: Exception) {
        }
        if (mac != null && !mac.verify()) {
            throw MacMismatchException()
        }
    }

    override fun skip(n: Long): Long {
        var remaining = n
        val skipBuffer = ByteArray(4096)
        while (remaining > 0) {
            val read = read(skipBuffer, 0, minOf(skipBuffer.size.toLong(), remaining).toInt())
            if (read == -1) break
            remaining -= read
        }
        return n - remaining
    }

    override fun markSupported(): Boolean = false

    companion object {
        private const val BUFFER = 64 * 1024
    }
}
