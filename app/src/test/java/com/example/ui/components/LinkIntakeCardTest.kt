package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.data.model.CloudItem
import com.example.ui.screens.MediaMetadataPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkIntakeCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun linkCardAcceptsEditingAndExposesSeparatePasteAndOpenActions() {
        var pasted = false
        var submitted = false
        lateinit var value: androidx.compose.runtime.MutableState<String>

        composeRule.setContent {
            MaterialTheme {
                value = remember { mutableStateOf("") }
                LinkIntakeCard(
                    value = value.value,
                    onValueChange = { value.value = it },
                    onPaste = { pasted = true },
                    onSubmit = { submitted = true },
                    errorMessage = null
                )
            }
        }

        composeRule.onNodeWithTag("link_input_field").performTextInput("https://cloud.mail.ru/public/example")
        composeRule.onNodeWithTag("paste_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("open_folder_button").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals("https://cloud.mail.ru/public/example", value.value)
            assertTrue(pasted)
            assertTrue(submitted)
        }
    }

    @Test
    fun linkCardShowsRecoverableErrorFeedback() {
        composeRule.setContent {
            MaterialTheme {
                LinkIntakeCard(
                    value = "https://cloud.mail.ru/public/example",
                    onValueChange = {},
                    onPaste = {},
                    onSubmit = {},
                    errorMessage = "Не удалось открыть папку. Проверьте подключение и повторите попытку."
                )
            }
        }

        composeRule.onNodeWithText(
            "Не удалось открыть папку. Проверьте подключение и повторите попытку."
        ).assertIsDisplayed()
    }

    @Test
    fun mediaMetadataDisplaysTypeSizeDateAndDurationWhenAvailable() {
        val item = CloudItem(
            name = "clip.mp4",
            size = 2048L,
            type = "video",
            durationSeconds = 62L,
            weblink = "folder/clip"
        )

        composeRule.setContent {
            MaterialTheme {
                MediaMetadataPanel(item = item)
            }
        }

        composeRule.onNodeWithText("Видео").assertIsDisplayed()
        composeRule.onNodeWithText("Размер · ${item.formattedSize}").assertIsDisplayed()
        composeRule.onNodeWithText("Дата не указана").assertIsDisplayed()
        composeRule.onNodeWithText("Длительность · 1:02").assertIsDisplayed()
    }
}
