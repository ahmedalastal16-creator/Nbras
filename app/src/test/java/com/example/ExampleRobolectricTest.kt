package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.curriculum.GazaCurriculumCatalog
import com.example.data.curriculum.PreferredStudyPeriod
import com.example.data.curriculum.ScheduleGenerator
import com.example.data.curriculum.StudyIntensity
import com.example.data.repository.FirebaseAiRepository
import com.google.firebase.ai.type.UnknownException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    fun `package conversation ids are isolated and firestore rule compatible`() {
        val physicsId = GazaCurriculumCatalog.packageConversationId("tawjihi_scientific", "الفيزياء")
        val chemId = GazaCurriculumCatalog.packageConversationId("tawjihi_scientific", "الكيمياء")
        val literaryArabicId = GazaCurriculumCatalog.packageConversationId(
            "tawjihi_literary",
            "اللغة العربية (المطالعة والنقد والبلاغة)"
        )

        assertNotEquals(physicsId, chemId)
        assertNotEquals(physicsId, literaryArabicId)
        assertTrue(physicsId.matches(Regex("^[a-zA-Z0-9_\\-]+$")))
        assertTrue(literaryArabicId.matches(Regex("^[a-zA-Z0-9_\\-]+$")))

        val resolved = GazaCurriculumCatalog.findPackageById(physicsId)
        assertNotNull(resolved)
        assertEquals("الفيزياء", resolved?.second)
    }

    @Test
    fun `unknown firebase ai exception unwraps root cause without raw english string`() {
        val ctor = UnknownException::class.java.getDeclaredConstructor(
            String::class.java,
            Throwable::class.java
        ).apply { isAccessible = true }
        val wrapped = ctor.newInstance(
            "Something unexpected happened.",
            IllegalStateException("App Check attestation failed (403)")
        )
        val formatted = FirebaseAiRepository.formatErrorMessage(wrapped)
        assertFalse(formatted.contains("Something unexpected happened"))
        assertTrue(formatted.contains("App Check attestation failed"))
    }

    @Test
    fun `guest browses freely and sending message opens sign in gate then returns as guest`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hero_open_tutor_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_signin_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tutor").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("gaza_ai_live_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tutor_auto_speak_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("speed_mode_fast").assertIsDisplayed()
        composeTestRule.onNodeWithTag("speed_mode_detailed").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tutor_mic_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gaza_stage_chip_tawjihi_literary").performClick()
        composeTestRule.waitForIdle()

        // Sending a question as guest navigates to Sign-In page (Google & Apple).
        composeTestRule.onNodeWithTag("tutor_prompt_input").performTextInput("اشرح درس البلاغة")
        composeTestRule.onNodeWithTag("tutor_send_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("apple_sign_in_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("continue_as_guest_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Back in TutorScreen and able to continue browsing Lab & Library freely.
        composeTestRule.onNodeWithTag("tutor_prompt_input").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_lab").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("gaza_ai_live_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_lab_list").performScrollToNode(hasTestTag("lab_open_schedule_button"))
        composeTestRule.onNodeWithTag("lab_open_schedule_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_lab_list").performScrollToNode(hasTestTag("lab_mode_summary"))
        composeTestRule.onNodeWithTag("lab_mode_summary").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_schedule").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_schedule_session_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("schedule_list").performScrollToNode(hasTestTag("generate_schedule_button"))
        composeTestRule.onNodeWithTag("generate_schedule_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("generate_schedule_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_library").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("library_search_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("library_open_summaries_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("library_open_summaries_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("summaries_add_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("summaries_list").performScrollToNode(hasTestTag("summaries_search_input"))
        composeTestRule.onNodeWithTag("summaries_search_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("summaries_add_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("summary_editor_title_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("summary_editor_save_button").assertIsDisplayed()
    }

    @Test
    fun `schedule generator covers all seven days and chosen subjects`() {
        val stage = GazaCurriculumCatalog.stages.first()
        val chosen = stage.subjects.take(3)
        val generated = ScheduleGenerator.generateWeeklySchedule(
            stage = stage,
            selectedSubjects = chosen,
            intensity = StudyIntensity.BALANCED,
            preferredPeriod = PreferredStudyPeriod.MORNING
        )

        assertTrue(generated.isNotEmpty())
        assertEquals(7, generated.map { it.dayIndex }.distinct().size)
        assertTrue(chosen.all { subj -> generated.any { it.subjectTitle == subj } })
        assertTrue(generated.all { it.durationMinutes == StudyIntensity.BALANCED.sessionMinutes })
    }
}
