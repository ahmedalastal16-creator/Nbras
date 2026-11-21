package com.example

import com.example.audio.StudyVoicePlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun `cleanTextForSpeech removes markdown and urls for clear Arabic TTS`() {
        val raw = "### **قانون الزخم**\n• الزخم = الكتلة × السرعة\nراجع https://www.moe.edu.ps/gaza/subjects"
        val cleaned = StudyVoicePlayer.cleanTextForSpeech(raw)
        assertFalse(cleaned.contains("**"))
        assertFalse(cleaned.contains("###"))
        assertFalse(cleaned.contains("https://"))
        assertEquals("قانون الزخم، الزخم يساوي الكتلة × السرعة، راجع", cleaned)
    }
}
