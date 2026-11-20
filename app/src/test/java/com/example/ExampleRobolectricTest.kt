package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("نبراس", appName)
    }

    @Test
    fun `main activity launches and navigates across all tabs`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hero_open_tutor_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tutor").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("gaza_ai_live_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gaza_stage_chip_tawjihi_literary").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tutor_prompt_input").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_lab").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("gaza_ai_live_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lab_mode_summary").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_library").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("library_search_input").assertIsDisplayed()
    }
}
