package com.example.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

/** [StartScreen] normalization: unknown/legacy keys must never break startup. */
class StartScreenTest {

    @Test
    fun `known keys resolve to themselves`() {
        assertEquals(StartScreen.HOME, StartScreen.fromKey("home"))
        assertEquals(StartScreen.EXPLORER, StartScreen.fromKey("explorer"))
        assertEquals(StartScreen.DOWNLOADS, StartScreen.fromKey("downloads"))
    }

    @Test
    fun `unknown key falls back to home`() {
        assertEquals(StartScreen.HOME, StartScreen.fromKey("somewhere-else"))
    }

    @Test
    fun `null key falls back to home`() {
        assertEquals(StartScreen.HOME, StartScreen.fromKey(null))
    }

    @Test
    fun `enum keys match navigation route names used by MainActivity`() {
        assertEquals("home", StartScreen.HOME.key)
        assertEquals("explorer", StartScreen.EXPLORER.key)
        assertEquals("downloads", StartScreen.DOWNLOADS.key)
    }
}
