package com.example.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

private const val TAG = "StudyVoicePlayer"

data class VoicePlaybackState(
    val isReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val speakingItemId: String? = null,
    val speechRate: Float = 1.0f,
    val hasArabicVoice: Boolean = true,
    val ttsErrorMessage: String? = null
)

class StudyVoicePlayer(context: Context) {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(VoicePlaybackState())
    val state: StateFlow<VoicePlaybackState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var lastRawText: String? = null
    private var pendingSpeak: Pair<String, String>? = null
    private var activeSequencePrefix: String = ""

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts ?: return@TextToSpeech
                    val arabicSupported = configureArabicLocale(engine)
                    engine.setSpeechRate(_state.value.speechRate)
                    engine.setPitch(1.0f)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _state.update { it.copy(isSpeaking = true, ttsErrorMessage = null) }
                        }

                        override fun onDone(utteranceId: String?) {
                            if (utteranceId != null && utteranceId.endsWith("_last")) {
                                _state.update {
                                    it.copy(isSpeaking = false, speakingItemId = null)
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _state.update {
                                it.copy(
                                    isSpeaking = false,
                                    speakingItemId = null,
                                    ttsErrorMessage = "تعذر تشغيل النطق الصوتي في محرك الجهاز."
                                )
                            }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            Log.w(TAG, "TTS error code=$errorCode utterance=$utteranceId")
                            _state.update {
                                it.copy(
                                    isSpeaking = false,
                                    speakingItemId = null,
                                    ttsErrorMessage = "تعذر تشغيل النطق العربي على هذا الجهاز."
                                )
                            }
                        }
                    })
                    _state.update {
                        it.copy(
                            isReady = true,
                            hasArabicVoice = arabicSupported,
                            ttsErrorMessage = null
                        )
                    }
                    val queued = pendingSpeak
                    pendingSpeak = null
                    if (queued != null) {
                        speak(queued.first, queued.second)
                    }
                } else {
                    Log.w(TAG, "TextToSpeech initialization failed with status=$status")
                    _state.update {
                        it.copy(
                            isReady = false,
                            hasArabicVoice = false,
                            isSpeaking = false,
                            speakingItemId = null
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to initialize TextToSpeech", e)
            _state.update {
                it.copy(
                    isReady = false,
                    hasArabicVoice = false,
                    isSpeaking = false,
                    speakingItemId = null
                )
            }
        }
    }

    private fun configureArabicLocale(engine: TextToSpeech): Boolean {
        val candidates = listOf(
            Locale.Builder().setLanguage("ar").setRegion("PS").build(),
            Locale.Builder().setLanguage("ar").setRegion("SA").build(),
            Locale.Builder().setLanguage("ar").setRegion("EG").build(),
            Locale.Builder().setLanguage("ar").build()
        )
        for (locale in candidates) {
            val res = engine.isLanguageAvailable(locale)
            if (res >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = locale
                return true
            }
        }
        engine.language = Locale.getDefault()
        return false
    }

    fun toggleSpeak(itemId: String, rawText: String) {
        val current = _state.value
        if (current.isSpeaking && current.speakingItemId == itemId) {
            stop()
            return
        }
        speak(itemId, rawText)
    }

    fun speak(itemId: String, rawText: String) {
        val cleaned = cleanTextForSpeech(rawText)
        if (cleaned.isBlank()) return

        lastRawText = rawText
        val engine = tts
        if (engine == null || !_state.value.isReady) {
            pendingSpeak = itemId to rawText
            initTts()
            return
        }

        try {
            engine.stop()
            engine.setSpeechRate(_state.value.speechRate)
            val chunks = splitIntoTtsChunks(cleaned)
            activeSequencePrefix = "${itemId}_${System.currentTimeMillis()}"
            _state.update { it.copy(isSpeaking = true, speakingItemId = itemId, ttsErrorMessage = null) }

            var anyQueued = false
            chunks.forEachIndexed { index, chunk ->
                val isLast = index == chunks.lastIndex
                val utteranceId = if (isLast) {
                    "${activeSequencePrefix}_${index}_last"
                } else {
                    "${activeSequencePrefix}_$index"
                }
                val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val params = Bundle()
                val speakResult = engine.speak(chunk, queueMode, params, utteranceId)
                if (speakResult == TextToSpeech.SUCCESS) {
                    anyQueued = true
                }
            }
            if (!anyQueued) {
                _state.update {
                    it.copy(
                        isSpeaking = false,
                        speakingItemId = null,
                        ttsErrorMessage = "محرك النطق على الجهاز لا يدعم القراءة حالياً."
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to speak explanation", e)
            _state.update { it.copy(isSpeaking = false, speakingItemId = null) }
        }
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.75f, 1.5f)
        _state.update { it.copy(speechRate = clamped) }
        tts?.setSpeechRate(clamped)
        val currentItem = _state.value.speakingItemId
        val text = lastRawText
        if (_state.value.isSpeaking && currentItem != null && text != null) {
            speak(currentItem, text)
        }
    }

    fun stop() {
        pendingSpeak = null
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        }
        _state.update { it.copy(isSpeaking = false, speakingItemId = null) }
    }

    fun release() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS", e)
        }
        tts = null
    }

    companion object {
        internal fun cleanTextForSpeech(raw: String): String {
            return raw
                .replace(Regex("https?://\\S+"), " ")
                .replace(Regex("```[\\s\\S]*?```"), " ")
                .replace("`", "")
                .replace("**", "")
                .replace("__", "")
                .replace("###", "")
                .replace("##", "")
                .replace("#", "")
                .replace("•", "، ")
                .replace("→", " يؤدي إلى ")
                .replace("←", " ")
                .replace("=", " يساوي ")
                .replace("*", "")
                .replace(Regex("\\n{2,}"), ". ")
                .replace("\n", "، ")
                .replace(Regex("(،\\s*)+"), "، ")
                .replace(Regex("\\.\\s*،"), ". ")
                .replace(Regex("\\s{2,}"), " ")
                .trim()
                .trimEnd('،', ' ')
                .trim()
        }

        private fun splitIntoTtsChunks(text: String, maxChunkSize: Int = 1800): List<String> {
            if (text.length <= maxChunkSize) return listOf(text)
            val result = mutableListOf<String>()
            var start = 0
            while (start < text.length) {
                var end = (start + maxChunkSize).coerceAtMost(text.length)
                if (end < text.length) {
                    val lastPeriod = text.lastIndexOf('.', end)
                    val lastComma = text.lastIndexOf('،', end)
                    val lastSpace = text.lastIndexOf(' ', end)
                    val splitAt = maxOf(lastPeriod, lastComma, lastSpace)
                    if (splitAt > start + 200) {
                        end = splitAt + 1
                    }
                }
                result.add(text.substring(start, end).trim())
                start = end
            }
            return result.filter { it.isNotBlank() }
        }
    }
}
