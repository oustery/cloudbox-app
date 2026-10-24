package com.example.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {

    @Test
    fun `controls visible at top with content`() {
        assertTrue(
            homeControlsVisible(atTop = true, searchQuery = "", searchFieldFocused = false, hasContent = true)
        )
    }

    @Test
    fun `controls hidden when scrolled away`() {
        assertFalse(
            homeControlsVisible(atTop = false, searchQuery = "", searchFieldFocused = false, hasContent = true)
        )
    }

    @Test
    fun `controls stay visible while searching`() {
        assertTrue(
            homeControlsVisible(atTop = false, searchQuery = "отчёт", searchFieldFocused = false, hasContent = true)
        )
        assertTrue(
            homeControlsVisible(atTop = false, searchQuery = "", searchFieldFocused = true, hasContent = true)
        )
    }

    @Test
    fun `controls never visible on empty library`() {
        assertFalse(
            homeControlsVisible(atTop = true, searchQuery = "", searchFieldFocused = true, hasContent = false)
        )
    }

    @Test
    fun `scroll to top appears after first screens`() {
        assertFalse(showScrollToTop(0))
        assertFalse(showScrollToTop(2))
        assertTrue(showScrollToTop(3))
        assertTrue(showScrollToTop(10))
    }

    @Test
    fun `links pluralization covers russian cases`() {
        assertTrue(linksPlural(1).contains("ссылка"))
        assertTrue(linksPlural(2).contains("ссылки"))
        assertTrue(linksPlural(5).contains("ссылок"))
        assertTrue(linksPlural(11).contains("ссылок"))
        assertTrue(linksPlural(21).contains("ссылка"))
    }
}
