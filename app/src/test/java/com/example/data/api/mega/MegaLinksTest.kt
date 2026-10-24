package com.example.data.api.mega

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MegaLinksTest {

    @Test
    fun `parses new file link`() {
        val link = MegaLinks.parse("https://mega.nz/file/DlggBbTY#fbKFn_qhA3hbthETgkipy_zV6RDarBgfnjp43cxpTzk")
        assertNotNull(link)
        assertEquals("DlggBbTY", link!!.handle)
        assertEquals("fbKFn_qhA3hbthETgkipy_zV6RDarBgfnjp43cxpTzk", link.keyB64)
        assertFalse(link.isFolder)
    }

    @Test
    fun `parses legacy file link`() {
        val link = MegaLinks.parse("https://mega.nz/#!oMhDAAqC!XQqMb9u7oAKvYMYYRHYI_s870Y2wXR4bzlL4aQI1eps")
        assertNotNull(link)
        assertEquals("oMhDAAqC", link!!.handle)
        assertFalse(link.isFolder)
    }

    @Test
    fun `parses new folder link with scheme`() {
        val link = MegaLinks.parse("https://mega.nz/folder/6ap10KRI#1Hy41Yv2pcJmYhdJjmPJpw")
        assertNotNull(link)
        assertEquals("6ap10KRI", link!!.handle)
        assertEquals("1Hy41Yv2pcJmYhdJjmPJpw", link.keyB64)
        assertTrue(link.isFolder)
    }

    @Test
    fun `parses legacy folder link`() {
        val link = MegaLinks.parse("mega.nz/#F!6ap10KRI!1Hy41Yv2pcJmYhdJjmPJpw")
        assertNotNull(link)
        assertTrue(link!!.isFolder)
        assertEquals("6ap10KRI", link.handle)
    }

    @Test
    fun `rejects non-mega urls`() {
        assertNull(MegaLinks.parse("https://cloud.mail.ru/public/KVy7/S1hRRPVUE"))
        assertNull(MegaLinks.parse("just some text"))
        assertNull(MegaLinks.parse(""))
        assertNull(MegaLinks.parse("https://mega.nz/unsupported/AbCdEf12#short"))
    }

    @Test
    fun `identity key round trip for folder`() {
        val link = MegaLinks.parse("https://mega.nz/folder/6ap10KRI#1Hy41Yv2pcJmYhdJjmPJpw")!!
        val identity = MegaLinks.parseIdentity(link.identityKey)
        assertNotNull(identity)
        assertEquals("6ap10KRI", identity!!.folderHandle)
        assertEquals("1Hy41Yv2pcJmYhdJjmPJpw", identity.folderKeyB64)
        assertNull(identity.nodeHandle)
        assertFalse(identity.isFileLink)
    }

    @Test
    fun `identity key round trip for file`() {
        val link = MegaLinks.parse("https://mega.nz/file/DlggBbTY#fbKFn_qhA3hbthETgkipy_zV6RDarBgfnjp43cxpTzk")!!
        val identity = MegaLinks.parseIdentity(link.identityKey)
        assertNotNull(identity)
        assertTrue(identity!!.isFileLink)
        assertEquals("DlggBbTY", identity.folderHandle)
    }

    @Test
    fun `node identity with embedded key parses`() {
        val identity = MegaLinks.parseIdentity("mega:6ap10KRI:1Hy41Yv2pcJmYhdJjmPJpw:eKBBDRrC:VHCG7KbPkQ3J7vGweyLoTVk6qADphk28Txsmm1kizzs")
        assertNotNull(identity)
        assertEquals("eKBBDRrC", identity!!.nodeHandle)
        assertEquals("VHCG7KbPkQ3J7vGweyLoTVk6qADphk28Txsmm1kizzs", identity.nodeKeyB64)
        assertFalse(identity.isFileLink)
    }

    @Test
    fun `isMegaWeblink detects composite keys only`() {
        assertTrue(MegaLinks.isMegaWeblink("mega:AbCdEf12:SomeKeyB64Value22chars"))
        assertTrue(MegaLinks.isMegaWeblink("megafile:AbCdEf12:SomeKeyB64Value43chars_000000000000000"))
        assertFalse(MegaLinks.isMegaWeblink("KVy7/S1hRRPVUE"))
        assertFalse(MegaLinks.isMegaWeblink("mega.nz/folder/X#Y"))
        assertFalse(MegaLinks.isMegaWeblink(""))
    }

    @Test
    fun `public url reconstruction`() {
        val identity = MegaLinks.parseIdentity("mega:6ap10KRI:1Hy41Yv2pcJmYhdJjmPJpw")!!
        assertEquals("https://mega.nz/folder/6ap10KRI#1Hy41Yv2pcJmYhdJjmPJpw", MegaLinks.toPublicUrl(identity))
        val fileIdentity = MegaLinks.parseIdentity("megafile:DlggBbTY:fbKFn_qhA3hbthETgkipy_zV6RDarBgfnjp43cxpTzk")!!
        assertEquals(
            "https://mega.nz/file/DlggBbTY#fbKFn_qhA3hbthETgkipy_zV6RDarBgfnjp43cxpTzk",
            MegaLinks.toPublicUrl(fileIdentity)
        )
    }

    @Test
    fun `mailru parser must not swallow mega urls`() {
        // The loose Mail.ru "XXXX/YYYY" fallback would match scheme-less mega
        // URLs, so the app must always test MegaLinks first.
        assertNull(com.example.data.api.CloudUrlParser.extractWeblink("https://mega.nz/folder/6ap10KRI#1Hy41Yv2pcJmYhdJjmPJpw"))
        assertNotNull(com.example.data.api.CloudUrlParser.extractWeblink("mega.nz/folder/6ap10KRI#1Hy41Yv2pcJmYhdJjmPJpw"))
    }
}
