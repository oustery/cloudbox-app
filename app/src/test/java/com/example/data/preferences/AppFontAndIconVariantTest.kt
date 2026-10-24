package com.example.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class AppFontAndIconVariantTest {

    @Test
    fun `font parses known keys`() {
        assertEquals(AppFont.MANROPE, AppFont.fromKey("manrope"))
        assertEquals(AppFont.SYSTEM, AppFont.fromKey("system"))
        assertEquals(AppFont.NUNITO, AppFont.fromKey("nunito"))
    }

    @Test
    fun `font falls back to inter for unknown or missing key`() {
        assertEquals(AppFont.INTER, AppFont.fromKey(null))
        assertEquals(AppFont.INTER, AppFont.fromKey("nope"))
        assertEquals(AppFont.INTER, AppFont.fromKey(""))
    }

    @Test
    fun `icon variant parses known keys`() {
        assertEquals(AppIconVariant.GRAPHITE, AppIconVariant.fromKey("graphite"))
        assertEquals(AppIconVariant.FROST, AppIconVariant.fromKey("frost"))
    }

    @Test
    fun `icon variant falls back to standard`() {
        assertEquals(AppIconVariant.STANDARD, AppIconVariant.fromKey(null))
        assertEquals(AppIconVariant.STANDARD, AppIconVariant.fromKey("classic"))
    }

    @Test
    fun `every variant key is unique and non-blank`() {
        val keys = AppFont.entries.map { it.key } + AppIconVariant.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        keys.forEach { key -> assert(key.isNotBlank()) }
    }
}
