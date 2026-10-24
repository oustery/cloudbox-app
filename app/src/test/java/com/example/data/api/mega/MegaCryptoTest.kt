package com.example.data.api.mega

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Known-answer tests generated from a validated Python prototype
 * (megajs 1.3.10 / mega.py 1.0.8 semantics) against LIVE MEGA links:
 * folder https://mega.nz/folder/6ap10KRI#1Hy41Yv2pcJmYhdJjmPJpw
 * (file node eKBBDRrC, 807-byte .m3u playlist).
 */
class MegaCryptoTest {

    // ---------- base64url ----------

    @Test
    fun `base64url round trip`() {
        val data = byteArrayOf(0, 1, 2, 250.toByte(), 251.toByte(), 252.toByte(), 127, 128.toByte(), 255.toByte())
        assertEquals(data.toList(), MegaCrypto.base64UrlDecode(MegaCrypto.base64UrlEncode(data)).toList())
    }

    @Test
    fun `base64url decode known vector`() {
        val decoded = MegaCrypto.base64UrlDecode("XQqMb9u7oAKvYMYYRHYI_s870Y2wXR4bzlL4aQI1eps")
        assertEquals(
            "5d0a8c6fdbbba002af60c618447608fecf3bd18db05d1e1bce52f86902357a9b",
            decoded.joinToString("") { "%02x".format(it) }
        )
    }

    // ---------- key derivation ----------

    @Test
    fun `folder key used directly for 16 bytes`() {
        val key = MegaCrypto.base64UrlDecode("1Hy41Yv2pcJmYhdJjmPJpw")
        assertEquals(16, key.size)
        val aes = MegaCrypto.folderAesKey(key)
        assertEquals(
            "d47cb8d58bf6a5c2666217498e63c9a7",
            aes.joinToString("") { "%02x".format(it) }
        )
    }

    @Test
    fun `merged node key derives content key nonce and mac`() {
        val merged = hex("2cf3a6d4bbecc6148173841a644b76f2394bdc4d4844c38ae75b76d3ba61b1bb")
        val contentKey = MegaCrypto.deriveContentKey(merged)
        assertEquals("15b87a99f3a8059e6628f2c9de2ac749", hex(contentKey.aesKey))
        assertEquals("394bdc4d4844c38a", hex(contentKey.nonce))
        assertEquals("e75b76d3ba61b1bb", hex(contentKey.mac))
    }

    // ---------- attributes ----------

    @Test
    fun `attributes decrypt to node name`() {
        val contentKey = MegaCrypto.deriveContentKey(
            hex("2cf3a6d4bbecc6148173841a644b76f2394bdc4d4844c38ae75b76d3ba61b1bb")
        )
        val name = MegaCrypto.decryptAttrName(
            contentKey.aesKey,
            "Yf87mrRx7yCMzAU1pnYFyf25c8OxSXTlxB6dZ-BZtkePlX69FFp7QKNYA_oLvOTU96Jj31CJT5gFSjhqM7Ci2QJ2x9DoTrEL2ODgJNEYNZFZRGolSVKCsVu9SGcDQVS8b3bYRy4fWlqxEMScfSo85je9Jhx8sua4O-PFxL7gW6LHBdB_bq2Edeh93myrYBf8"
        )
        assertEquals(
            "Robert Forster - 2024-11-15 - Brunswick Ballroom, Melbourne, Australia - AUD (CH Master).m3u",
            name
        )
    }

    @Test
    fun `attributes with wrong key return null`() {
        val wrongKey = ByteArray(16) { it.toByte() }
        val name = MegaCrypto.decryptAttrName(wrongKey, "Yf87mrRx7yCMzAU1pnYFyf25c8OxSXTlxB6dZ-BZtkePlX69FFp7QKNYA_oLvOTU96Jj31CJT5gFSjhqM7Ci2QJ2x9DoTrEL2ODgJNEYNZFZRGolSVKCsVu9SGcDQVS8b3bYRy4fWlqxEMScfSo85je9Jhx8sua4O-PFxL7gW6LHBdB_bq2Edeh93myrYBf8")
        assertTrue(name == null || !name.startsWith("Robert"))
    }

    // ---------- content (CTR) ----------

    @Test
    fun `ctr decrypt known ciphertext`() {
        val contentKey = MegaCrypto.deriveContentKey(
            hex("2cf3a6d4bbecc6148173841a644b76f2394bdc4d4844c38ae75b76d3ba61b1bb")
        )
        val cipher = MegaCrypto.contentCipher(contentKey.aesKey, contentKey.nonce)
        val ciphertext = hex(
            "0aee42f0b8409d6fda11fdd8ac1047278cda1e79461ff851529a3946d4ad657904bb9e2006d129e4ce0a911f2516db14fe315cf63c851d5fa9e6a88fef5d3a2c"
        )
        val plain = cipher.doFinal(ciphertext)
        val expected = "01 Introduction.flac\r\n02 Always.flac\r\n03 Let Me Imagine You.flac"
        assertEquals(expected, String(plain, Charsets.US_ASCII).substring(0, expected.length))
    }

    // ---------- integrity MAC ----------

    @Test
    fun `mac verifies real 807-byte plaintext`() {
        val contentKey = MegaCrypto.deriveContentKey(
            hex("2cf3a6d4bbecc6148173841a644b76f2394bdc4d4844c38ae75b76d3ba61b1bb")
        )
        val plaintext = hex(PLAYLIST_PLAINTEXT_HEX)
        assertEquals(807, plaintext.size)
        val mac = MegaCrypto.MegaMac(contentKey)
        // feed in awkward chunk sizes to exercise carry and boundary logic
        var off = 0
        val sizes = listOf(1, 15, 17, 64 * 1024, 3, 130000, plaintext.size)
        for (size in sizes) {
            if (off >= plaintext.size) break
            val take = minOf(size, plaintext.size - off)
            mac.update(plaintext, off, take)
            off += take
        }
        if (off < plaintext.size) mac.update(plaintext, off, plaintext.size - off)
        assertTrue(mac.verify())
    }

    /**
     * Multi-chunk vectors computed with the validated mega.py MAC algorithm
     * (its full-file MAC matched a live MEGA file): deterministic 400 000-byte
     * and 150 000-byte byte streams (LCG 1103515245/12345, byte = (seed>>16)&0xFF,
     * seed starting at 0x5eed) under key 15b87a99.. nonce 394bdc4d...
     * The 400K stream crosses the chunk boundaries at 128K and 384K.
     */
    @Test
    fun `mac matches reference over multi-chunk synthetic streams`() {
        for ((length, expected) in listOf(400_000L to "cb197c0633f0ebf9", 150_000L to "75308834d37e291f")) {
            val stream = lcgBytes(length)
            val verifying = MegaCrypto.ContentKey(
                aesKey = hex("15b87a99f3a8059e6628f2c9de2ac749"),
                nonce = hex("394bdc4d4844c38a"),
                mac = hex(expected)
            )
            val mac = MegaCrypto.MegaMac(verifying)
            // odd chunking: 65 539-byte reads cross boundaries misaligned to reads
            var off = 0
            while (off < stream.size) {
                val take = minOf(65_539, stream.size - off)
                mac.update(stream, off, take)
                off += take
            }
            assertTrue("MAC mismatch for length $length", mac.verify())
        }
    }

    /** Deterministic byte stream matching the Python vector generator. */
    private fun lcgBytes(length: Long): ByteArray {
        var seed = 0x5eedL
        val out = ByteArray(length.toInt())
        for (i in out.indices) {
            seed = (seed * 1103515245L + 12345L) and 0xFFFFFFFFL
            out[i] = ((seed shr 16) and 0xFF).toByte()
        }
        return out
    }

    @Test
    fun `mac rejects tampered plaintext`() {
        val contentKey = MegaCrypto.deriveContentKey(
            hex("2cf3a6d4bbecc6148173841a644b76f2394bdc4d4844c38ae75b76d3ba61b1bb")
        )
        val plaintext = hex(PLAYLIST_PLAINTEXT_HEX)
        plaintext[100] = (plaintext[100].toInt() xor 0x41).toByte()
        val mac = MegaCrypto.MegaMac(contentKey)
        mac.update(plaintext, 0, plaintext.size)
        assertFalse(mac.verify())
    }

    @Test
    fun `mac passes for empty file with zero mac`() {
        // Legacy 16-byte keys carry a zero mac; empty files must verify.
        val contentKey = MegaCrypto.ContentKey(
            aesKey = ByteArray(16) { 1 },
            nonce = ByteArray(8),
            mac = ByteArray(8)
        )
        val mac = MegaCrypto.MegaMac(contentKey)
        assertTrue(mac.verify())
    }

    // ---------- hashcash ----------

    @Test
    fun `hashcash rejects malformed challenge`() {
        assertNull(MegaCrypto.solveHashcash("bogus"))
        assertNull(MegaCrypto.solveHashcash("2:20:0:AAAA"))
    }

    // ---------- decrypt stream ----------

    @Test
    fun `decrypt stream yields plaintext and verifies mac`() {
        val contentKey = MegaCrypto.deriveContentKey(
            hex("2cf3a6d4bbecc6148173841a644b76f2394bdc4d4844c38ae75b76d3ba61b1bb")
        )
        // reconstruct ciphertext by re-encrypting with the CTR cipher (CTR is symmetric)
        val plaintext = hex(PLAYLIST_PLAINTEXT_HEX)
        val encCipher = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(
                javax.crypto.Cipher.ENCRYPT_MODE,
                javax.crypto.spec.SecretKeySpec(contentKey.aesKey, "AES"),
                javax.crypto.spec.IvParameterSpec(contentKey.nonce + ByteArray(8))
            )
        }
        val ciphertext = encCipher.doFinal(plaintext)

        val stream = MegaDecryptInputStream(ciphertext.inputStream(), contentKey, plaintext.size.toLong())
        val out = stream.readBytes()
        assertArrayEquals(plaintext, out)
    }

    @Test
    fun `decrypt stream throws on mac mismatch`() {
        val contentKey = MegaCrypto.deriveContentKey(
            hex("2cf3a6d4bbecc6148173841a644b76f2394bdc4d4844c38ae75b76d3ba61b1bb")
        )
        val plaintext = hex(PLAYLIST_PLAINTEXT_HEX)
        val encCipher = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(
                javax.crypto.Cipher.ENCRYPT_MODE,
                javax.crypto.spec.SecretKeySpec(contentKey.aesKey, "AES"),
                javax.crypto.spec.IvParameterSpec(contentKey.nonce + ByteArray(8))
            )
        }
        val ciphertext = encCipher.doFinal(plaintext)
        ciphertext[7] = (ciphertext[7].toInt() xor 0x55).toByte()

        val stream = MegaDecryptInputStream(ciphertext.inputStream(), contentKey, plaintext.size.toLong())
        try {
            stream.readBytes()
            throw AssertionError("expected MacMismatchException")
        } catch (e: MegaDecryptInputStream.MacMismatchException) {
            assertNotNull(e.message)
        }
    }

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { i ->
            ((Character.digit(s[i * 2], 16) shl 4) + Character.digit(s[i * 2 + 1], 16)).toByte()
        }

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    companion object {
        /** Decrypted content of the 807-byte m3u from the live folder (validated against the MEGA MAC). */
        private const val PLAYLIST_PLAINTEXT_HEX =
            "303120496e74726f64756374696f6e2e666c61630d0a303220416c776179732e666c61630d0a3033204c6574204d6520496d6167696e6520596f752e666c61630d0a303420426162792053746f6e65732e666c61630d0a3035204c6f76652049732041205369676e2e666c61630d0a30362050656f706c65205361792e666c61630d0a3037205768656e204920576173204120596f756e67204d616e2e666c61630d0a303820492043616e20446f2028775f204164656c65205069636b76616e6365292e666c61630d0a3039205370697269742028775f204164656c65205069636b76616e6365292e666c61630d0a3130203132312028775f204164656c65205069636b76616e6365292e666c61630d0a3131205768656e205368652053616e672041626f757420416e67656c73202861626f72746564292e666c61630d0a3132205768656e205368652053616e672041626f757420416e676c656c732028775f204164656c65205069636b76616e6365292e666c61630d0a313320323534312e666c61630d0a31342049732054686973205768617420596f752043616c6c204368616e67652e666c61630d0a313520426f7720446f776e2e666c61630d0a313620546f6f204d756368204f66204f6e65205468696e67202861626f72746564292e666c61630d0a313720546f6f204d756368204f66204f6e65205468696e672028775f204164656c65205069636b76616e6365292e666c61630d0a313820526f636b20274e2720526f6c6c20467269656e642028775f204164656c65205069636b76616e6365292e666c61630d0a31392054656e6465722059656172732028775f204164656c65205069636b76616e6365292e666c61630d0a3230204865204c69766573204d79204c6966652e666c61630d0a323120537072696e67205261696e202861626f72746564292e666c61630d0a323220537072696e67205261696e2e666c61630d0a3233204765726d616e204661726d686f7573652028775f204164656c65205069636b76616e6365292e666c61630d0a32342053757266696e67204d6167617a696e65732028775f204164656c65205069636b76616e6365292e666c61630d0a"
    }
}
