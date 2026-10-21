package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.example.data.local.SavedLinkEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedLinkCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun savedLinkRendersItsStoredThumbnailWithAnAccessibleDescription() {
        val link = SavedLinkEntity(
            url = "https://cloud.mail.ru/public/demo/key",
            weblinkKey = "demo/key",
            title = "Demo folder",
            previewThumbnailUrl = "file:///missing-preview-image.webp"
        )

        composeRule.setContent {
            MaterialTheme {
                SavedLinkCard(
                    link = link,
                    onClick = {},
                    onFavoriteToggle = {},
                    onDeleteClick = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            "Предпросмотр: Demo folder",
            useUnmergedTree = true
        ).assertExists()
    }
}
