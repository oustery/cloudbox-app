package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdaptiveChipGroupTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allChoicesWrapInside320DpViewportAtLargeFontScale() {
        assertChipLayout(width = 320.dp, fontScale = 2f, expectWrap = true)
    }

    @Test
    fun allChoicesStayInside360DpViewportAtIncreasedFontScale() {
        assertChipLayout(width = 360.dp, fontScale = 1.3f, expectWrap = false)
    }

    private fun assertChipLayout(width: Dp, fontScale: Float, expectWrap: Boolean) {
        val labels = listOf("Все", "В процессе", "Завершено", "С ошибками")

        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = currentDensity.density,
                    fontScale = fontScale
                )
            ) {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .width(width)
                            .testTag("chip_viewport")
                    ) {
                        AdaptiveChipGroup(
                            items = labels.indices.toList(),
                            modifier = Modifier.fillMaxWidth()
                        ) { index ->
                            FilterChip(
                                selected = index == 0,
                                onClick = {},
                                label = { Text(labels[index], maxLines = 1, softWrap = false) },
                                modifier = Modifier.testTag("adaptive_chip_$index")
                            )
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()
        val viewport = composeRule.onNodeWithTag("chip_viewport")
            .fetchSemanticsNode().boundsInRoot
        val chipBounds = labels.indices.map { index ->
            val node = composeRule.onNodeWithTag("adaptive_chip_$index")
            node.assertIsDisplayed()
            node.fetchSemanticsNode().boundsInRoot
        }

        chipBounds.forEachIndexed { index, bounds ->
            assertTrue("Chip $index extends past the viewport's left edge", bounds.left >= viewport.left - 1f)
            assertTrue("Chip $index extends past the viewport's right edge", bounds.right <= viewport.right + 1f)
        }
        if (expectWrap) {
            assertTrue(
                "Large-font filter chips should wrap instead of disappearing off-screen",
                chipBounds.last().top > chipBounds.first().top
            )
        }
    }
}
