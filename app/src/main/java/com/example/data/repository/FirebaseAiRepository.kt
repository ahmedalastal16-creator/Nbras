package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ContentBlockedException
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.UnknownException
import com.google.firebase.ai.type.UnsupportedUserLocationException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLException

private const val TAG = "FirebaseAiRepository"
private const val PRIMARY_MODEL_NAME = "gemini-3.8-flash"
private const val LITE_FALLBACK_MODEL_NAME = "gemini-3.5-flash-lite"
private const val AI_TIMEOUT_MS = 60_000L
private const val FAST_STALL_THRESHOLD_MS = 10_000L
private const val DETAILED_STALL_THRESHOLD_MS = 14_000L
private const val PRIMARY_UNARY_TIMEOUT_MS = 22_000L
private const val HTTP_PRIMARY_TIMEOUT_MS = 25_000L
private const val HTTP_FALLBACK_TIMEOUT_MS = 30_000L

class FirebaseAiRepository(private val context: Context) : AiRepository {

    private val aiBackend by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
    }

    private val systemPromptText = """
        أنت "نبراس"، معلم ذكي متخصص في الرزم التعليمية الفلسطينية لقطاع غزة (moe.edu.ps/gaza/subjects).
        قواعدك:
        - التزم بمنهاج الرزم التعليمية للمرحلة والمبحث المحددين، وبأسلوب أسئلة التوجيهي والامتحانات المعتمدة.
        - ابدأ بالشرح مباشرة دون مقدمات طويلة، بلغة عربية فصيحة واضحة، ونقاط مرقمة قصيرة ومركزة سهلة الفهم والقراءة الصوتية.
        - إذا كان السؤال خارج الدراسة، وجّه الطالب بلطف للعودة إلى مباحث رزم غزة.
    """.trimIndent()

    private val fastTutorModel by lazy {
        aiBackend.generativeModel(
            modelName = LITE_FALLBACK_MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.35f
                maxOutputTokens = 6144
            },
            systemInstruction = content { text(systemPromptText) },
            requestOptions = RequestOptions(timeoutInMillis = HTTP_PRIMARY_TIMEOUT_MS)
        )
    }

    private val liteFallbackTutorModel by lazy {
        aiBackend.generativeModel(
            modelName = PRIMARY_MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.35f
                maxOutputTokens = 6144
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
            },
            systemInstruction = content { text(systemPromptText) },
            requestOptions = RequestOptions(timeoutInMillis = HTTP_FALLBACK_TIMEOUT_MS)
        )
    }

    private val detailedTutorModel by lazy {
        aiBackend.generativeModel(
            modelName = PRIMARY_MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.45f
                maxOutputTokens = 8192
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
            },
            systemInstruction = content { text(systemPromptText) },
            requestOptions = RequestOptions(timeoutInMillis = HTTP_PRIMARY_TIMEOUT_MS)
        )
    }

    private val detailedLiteFallbackModel by lazy {
        aiBackend.generativeModel(
            modelName = LITE_FALLBACK_MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.45f
                maxOutputTokens = 8192
            },
            systemInstruction = content { text(systemPromptText) },
            requestOptions = RequestOptions(timeoutInMillis = HTTP_FALLBACK_TIMEOUT_MS)
        )
    }

    private val summarySchema by lazy {
        Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "عنوان موجز للدرس وفق الرزم التعليمية"),
                "overview" to Schema.string(description = "نظرة عامة مركزة للمفهوم الأساسي في المبحث"),
                "bulletPoints" to Schema.array(
                    items = Schema.string(),
                    description = "من 3 إلى 5 نقاط رئيسية منظمة من الدرس"
                ),
                "keyTerms" to Schema.array(
                    items = Schema.string(),
                    description = "من 3 إلى 5 مصطلحات أو قوانين مفتاحية"
                ),
                "examTip" to Schema.string(description = "تنبيه امتحاني خاص بنمط أسئلة الرزم والتوجيهي")
            )
        )
    }

    private val flashcardsSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "cards" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "question" to Schema.string(description = "سؤال استرجاع نشط واضح ومحدد"),
                            "answer" to Schema.string(description = "إجابة نموذجية دقيقة ومختصرة"),
                            "hint" to Schema.string(description = "تلميح قصير يساعد الطالب")
                        ),
                        optionalProperties = listOf("hint")
                    ),
                    description = "قائمة البطاقات التعليمية"
                )
            )
        )
    }

    private val quizSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "questions" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "question" to Schema.string(description = "نص السؤال بصياغة امتحانات التوجيهي الوزارية الفلسطينية السابقة"),
                            "options" to Schema.array(
                                items = Schema.string(),
                                description = "أربعة خيارات نصية دقيقة كما في ورقة التوجيهي"
                            ),
                            "correctIndex" to Schema.integer(description = "فهرس الإجابة الصحيحة من 0 إلى 3"),
                            "explanation" to Schema.string(description = "خطوات الحل أو التعليل الوزاري المعتمد لسبب صحة الإجابة"),
                            "examReference" to Schema.string(description = "مرجع نمط الامتحان السابق مثل: نمط توجيهي وزاري 2019 — الدورة الأولى")
                        ),
                        optionalProperties = listOf("examReference")
                    ),
                    description = "قائمة أسئلة اختيار من متعدد بنمط امتحانات التوجيهي القديمة"
                )
            )
        )
    }

    private val summaryModel by lazy { buildJsonModel(PRIMARY_MODEL_NAME, 4096, summarySchema, lowThinking = true, timeoutMs = HTTP_PRIMARY_TIMEOUT_MS) }
    private val liteSummaryModel by lazy { buildJsonModel(LITE_FALLBACK_MODEL_NAME, 4096, summarySchema, lowThinking = false, timeoutMs = HTTP_FALLBACK_TIMEOUT_MS) }

    private val flashcardsModel by lazy { buildJsonModel(PRIMARY_MODEL_NAME, 4096, flashcardsSchema, lowThinking = true, timeoutMs = HTTP_PRIMARY_TIMEOUT_MS) }
    private val liteFlashcardsModel by lazy { buildJsonModel(LITE_FALLBACK_MODEL_NAME, 4096, flashcardsSchema, lowThinking = false, timeoutMs = HTTP_FALLBACK_TIMEOUT_MS) }

    private val quizModel by lazy { buildJsonModel(PRIMARY_MODEL_NAME, 4096, quizSchema, lowThinking = true, timeoutMs = HTTP_PRIMARY_TIMEOUT_MS) }
    private val liteQuizModel by lazy { buildJsonModel(LITE_FALLBACK_MODEL_NAME, 4096, quizSchema, lowThinking = false, timeoutMs = HTTP_FALLBACK_TIMEOUT_MS) }

    private fun buildJsonModel(
        modelName: String,
        maxTokens: Int,
        schema: Schema,
        lowThinking: Boolean,
        timeoutMs: Long
    ): GenerativeModel {
        return aiBackend.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                temperature = 0.35f
                maxOutputTokens = maxTokens
                responseMimeType = "application/json"
                responseSchema = schema
                if (lowThinking) {
                    thinkingConfig = thinkingConfig {
                        thinkingLevel = ThinkingLevel.LOW
                    }
                }
            },
            systemInstruction = content { text(systemPromptText) },
            requestOptions = RequestOptions(timeoutInMillis = timeoutMs)
        )
    }

    override fun askTutorStream(
        prompt: String,
        stageTitle: String,
        subjectTitle: String,
        imageUri: String?,
        speedMode: AiSpeedMode,
        recentHistory: List<Pair<Boolean, String>>
    ): Flow<String> = flow {
        val bitmap = imageUri?.let { loadScaledBitmap(it) }
        val contextHeader = buildString {
            appendLine("المرحلة: $stageTitle | المبحث: $subjectTitle (رزم غزة).")
            if (recentHistory.isNotEmpty()) {
                appendLine("سياق محادثة هذه الرزمة:")
                recentHistory.takeLast(6).forEach { (isUser, msg) ->
                    val speaker = if (isUser) "الطالب" else "نبراس"
                    appendLine("- $speaker: ${msg.take(300)}")
                }
            }
            append("السؤال الحالي: $prompt")
        }

        val userInput = content {
            if (bitmap != null) {
                image(bitmap)
            }
            text(contextHeader)
        }

        val primaryModel = if (bitmap != null || speedMode == AiSpeedMode.DETAILED) {
            detailedTutorModel
        } else {
            fastTutorModel
        }
        val fallbackModel = if (bitmap != null || speedMode == AiSpeedMode.DETAILED) {
            detailedLiteFallbackModel
        } else {
            liteFallbackTutorModel
        }

        val stallMs = if (bitmap == null && speedMode == AiSpeedMode.FAST) {
            FAST_STALL_THRESHOLD_MS
        } else {
            DETAILED_STALL_THRESHOLD_MS
        }

        val accumulated = StringBuilder()
        withTimeout(AI_TIMEOUT_MS) {
            var lastError: Throwable? = null
            val attempts = listOf(primaryModel, fallbackModel)

            for ((attemptIdx, model) in attempts.withIndex()) {
                if (accumulated.isNotBlank()) break
                val hasEmittedChunk = AtomicBoolean(false)
                val stalledBeforeOutput = AtomicBoolean(false)

                try {
                    coroutineScope {
                        val chunkChannel = Channel<String>(Channel.UNLIMITED)
                        val streamJob = launch(Dispatchers.IO) {
                            try {
                                model.generateContentStream(userInput).collect { chunk ->
                                    val part = chunk.text.orEmpty()
                                    if (part.isNotEmpty()) {
                                        hasEmittedChunk.set(true)
                                        chunkChannel.send(part)
                                    }
                                }
                                chunkChannel.close()
                            } catch (e: CancellationException) {
                                chunkChannel.close(e)
                                throw e
                            } catch (t: Throwable) {
                                chunkChannel.close(t)
                            }
                        }

                        val stallWatchdog = launch {
                            delay(stallMs)
                            if (!hasEmittedChunk.get()) {
                                stalledBeforeOutput.set(true)
                                Log.w(TAG, "Stream attempt #$attemptIdx stalled after ${stallMs}ms; switching model")
                                streamJob.cancel(CancellationException("Stream stalled before first token"))
                                chunkChannel.close()
                            }
                        }

                        try {
                            for (part in chunkChannel) {
                                accumulated.append(part)
                                emit(accumulated.toString())
                            }
                        } catch (e: CancellationException) {
                            if (!stalledBeforeOutput.get()) {
                                throw e
                            }
                        } catch (t: Throwable) {
                            lastError = t
                            val rescued = (t as? ResponseStoppedException)?.response?.text
                            if (accumulated.isEmpty() && !rescued.isNullOrBlank()) {
                                accumulated.append(rescued)
                                emit(rescued)
                            }
                        } finally {
                            stallWatchdog.cancel()
                        }
                    }
                } catch (e: CancellationException) {
                    if (!stalledBeforeOutput.get()) {
                        throw e
                    }
                }
            }

            if (accumulated.isEmpty()) {
                Log.w(TAG, "Attempting final unary retry on free Flash-Lite model", lastError)
                delay(650L)
                val unaryModel = if (bitmap == null) fastTutorModel else detailedLiteFallbackModel
                try {
                    val fallbackResponse = withContext(Dispatchers.IO) {
                        unaryModel.generateContent(userInput)
                    }
                    val fallbackText = fallbackResponse.text.orEmpty()
                    if (fallbackText.isNotBlank()) {
                        accumulated.append(fallbackText)
                        emit(fallbackText)
                    } else if (lastError != null) {
                        throw lastError!!
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (stopped: ResponseStoppedException) {
                    val rescuedFallback = stopped.response.text
                    if (!rescuedFallback.isNullOrBlank()) {
                        accumulated.append(rescuedFallback)
                        emit(rescuedFallback)
                    } else {
                        throw (lastError ?: stopped)
                    }
                } catch (fallbackError: Throwable) {
                    throw (lastError ?: fallbackError)
                }
            }
        }

        if (accumulated.isBlank()) {
            throw IllegalStateException("لم يُرجع النموذج نصاً، يرجى المحاولة مرة أخرى.")
        }
    }

    override suspend fun generateSummary(
        topicOrNotes: String,
        stageTitle: String,
        subjectTitle: String,
        imageUri: String?,
        speedMode: AiSpeedMode
    ): AiSummaryResult {
        val bitmap = imageUri?.let { loadScaledBitmap(it) }
        val promptText = buildString {
            appendLine("المرحلة: $stageTitle | المبحث: $subjectTitle (رزم غزة).")
            appendLine("أنشئ ملخصاً أكاديمياً مركزاً وسريع الفهم للمحتوى التالي:")
            appendLine(topicOrNotes.ifBlank { "حلّل صورة الملاحظات/الرزمة المرفقة ولخّصها." })
        }
        val inputContent = content {
            if (bitmap != null) {
                image(bitmap)
            }
            text(promptText)
        }

        val rawJson = generateJsonWithFallback(
            primary = summaryModel,
            fallback = liteSummaryModel,
            inputContent = inputContent
        )
        val json = JSONObject(rawJson)
        val bulletsArray = json.getJSONArray("bulletPoints")
        val termsArray = json.getJSONArray("keyTerms")

        return AiSummaryResult(
            title = json.getString("title"),
            overview = json.getString("overview"),
            bulletPoints = (0 until bulletsArray.length()).map { bulletsArray.getString(it) },
            keyTerms = (0 until termsArray.length()).map { termsArray.getString(it) },
            examTip = json.getString("examTip"),
            isSampleResponse = false
        )
    }

    override suspend fun generateFlashcards(
        topicOrNotes: String,
        stageTitle: String,
        subjectTitle: String,
        count: Int,
        speedMode: AiSpeedMode
    ): List<GeneratedFlashcard> {
        val promptText = """
            المرحلة: $stageTitle | المبحث: $subjectTitle (رزم غزة).
            أنشئ $count بطاقات تعليمية مركزة (سؤال وجواب وتلميح) حول:
            $topicOrNotes
        """.trimIndent()

        val rawJson = generateJsonWithFallback(
            primary = flashcardsModel,
            fallback = liteFlashcardsModel,
            inputContent = content { text(promptText) }
        )
        val json = JSONObject(rawJson)
        val cardsArray = json.getJSONArray("cards")

        return (0 until cardsArray.length()).map { i ->
            val obj = cardsArray.getJSONObject(i)
            GeneratedFlashcard(
                question = obj.getString("question"),
                answer = obj.getString("answer"),
                hint = obj.optString("hint", ""),
                isSampleResponse = false
            )
        }
    }

    override suspend fun generateQuiz(
        topicOrNotes: String,
        stageTitle: String,
        subjectTitle: String,
        count: Int,
        speedMode: AiSpeedMode
    ): List<QuizQuestion> {
        val promptText = """
            المرحلة: $stageTitle | المبحث: $subjectTitle (المنهاج الفلسطيني - رزم غزة).
            أنشئ اختباراً محاكياً تماماً لامتحانات الثانوية العامة (التوجيهي) الوزارية القديمة والسنوات السابقة (الدورات الأولى والثانية والاستدراكية) مكوّناً من $count أسئلة اختيار من متعدد (4 خيارات لكل سؤال) حول:
            $topicOrNotes
            شروط الصياغة الوزارية:
            - صُغ كل سؤال باللغة والأسلوب الدقيق المتبع في أوراق امتحانات التوجيهي القديمة في فلسطين (أسئلة تقيس الفهم والاستنتاج والتطبيق والحسابات أو الإعراب حسب المبحث، وليست أسئلة سطحية).
            - اجعل المشتتات (الخيارات الخاطئة) واقعية ومبنية على الأخطاء الشائعة لدى طلبة التوجيهي.
            - اكتب في examReference تصنيفاً أو دورة وزارية سابقة مشابهة لمستوى السؤال (مثل: «نمط توجيهي وزاري 2018 — الدورة الأولى» أو «نموذج وزاري سابق 2021 — الاستدراكية»).
            - اكتب في explanation التعليل العلمي أو خطوات الحل المعتمدة في نموذج الإجابة الوزاري.
        """.trimIndent()

        val rawJson = generateJsonWithFallback(
            primary = quizModel,
            fallback = liteQuizModel,
            inputContent = content { text(promptText) }
        )
        val json = JSONObject(rawJson)
        val qArray = json.getJSONArray("questions")

        return (0 until qArray.length()).map { i ->
            val obj = qArray.getJSONObject(i)
            val optsArray = obj.getJSONArray("options")
            val options = (0 until optsArray.length()).map { j -> optsArray.getString(j) }
            val safeCorrect = obj.getInt("correctIndex").coerceIn(0, (options.size - 1).coerceAtLeast(0))
            QuizQuestion(
                question = obj.getString("question"),
                options = options,
                correctIndex = safeCorrect,
                explanation = obj.getString("explanation"),
                examReference = obj.optString("examReference", "نمط توجيهي وزاري سابق"),
                isSampleResponse = false
            )
        }
    }

    private suspend fun generateJsonWithFallback(
        primary: GenerativeModel,
        fallback: GenerativeModel,
        inputContent: Content
    ): String = withTimeout(AI_TIMEOUT_MS) {
        var lastError: Throwable? = null
        val models = listOf(fallback, primary, fallback)
        for ((idx, candidate) in models.withIndex()) {
            if (idx == 2) {
                delay(650L)
            }
            val candidateText = try {
                withTimeout(PRIMARY_UNARY_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        candidate.generateContent(inputContent).text
                    }
                }
            } catch (e: TimeoutCancellationException) {
                lastError = e
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                null
            }
            if (!candidateText.isNullOrBlank()) {
                return@withTimeout candidateText
            }
        }
        throw (lastError ?: IllegalStateException("استجابة فارغة من نموذج الذكاء الاصطناعي."))
    }

    private suspend fun loadScaledBitmap(uriString: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            var sampleSize = 1
            while (maxDim / sampleSize > 800) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode attached image bitmap", e)
            null
        }
    }

    companion object {
        fun formatErrorMessage(error: Throwable): String {
            val chain = generateSequence(error) { it.cause }.toList()
            val aiException = chain.filterIsInstance<FirebaseAIException>().firstOrNull()
            val rootCause = chain.lastOrNull() ?: error

            if (chain.any { it is UnknownHostException || it is ConnectException || it is SSLException }) {
                return "تعذر الاتصال بالإنترنت أو بخوادم الذكاء الاصطناعي. تأكد من اتصالك وحاول مجدداً."
            }
            if (chain.any { it is TimeoutCancellationException || it is SocketTimeoutException } ||
                aiException is RequestTimeoutException
            ) {
                return "استغرق الطلب وقتاً أطول من المعتاد. حاول تقصير السؤال أو اضغط إعادة المحاولة."
            }

            return when (aiException) {
                is QuotaExceededException ->
                    "تم تجاوز حد الاستخدام المجاني المؤقت للذكاء الاصطناعي. انتظر قليلاً وحاول مجدداً."
                is ServiceDisabledException ->
                    "خدمة Gemini AI غير مفعّلة بعد على المشروع أو يلزم التحقق من App Check."
                is UnsupportedUserLocationException ->
                    "الخدمة غير مدعومة في الموقع الجغرافي الحالي للخادم."
                is PromptBlockedException, is ContentBlockedException ->
                    "تعذر عرض الرد لأن المحتوى خالف سياسات السلامة؛ أعد صياغة السؤال بشكل دراسي أوضح."
                is InvalidAPIKeyException ->
                    "إعدادات ربط Firebase غير مكتملة؛ يرجى التحقق من ملف إعدادات المشروع."
                is ServerException ->
                    "حدث ضغط مؤقت على خادم Gemini AI. أعد المحاولة بعد ثوانٍ."
                is ResponseStoppedException -> {
                    val reason = aiException.response.candidates.firstOrNull()?.finishReason
                    when (reason) {
                        FinishReason.MAX_TOKENS ->
                            "وصل الرد إلى الحد الأقصى للطول. أعد صياغة السؤال بشكل أقصر أو قسّمه لخطوات."
                        FinishReason.SAFETY, FinishReason.PROHIBITED_CONTENT, FinishReason.BLOCKLIST ->
                            "تعذر عرض الرد لأن المحتوى خالف سياسات السلامة؛ أعد صياغة السؤال بشكل دراسي أوضح."
                        else ->
                            "توقف النموذج عن إكمال الرد. اضغط إعادة أو أعد صياغة السؤال."
                    }
                }
                is UnknownException -> {
                    val causeDetail = rootCause.takeIf { it !== aiException }?.message
                        ?: aiException.cause?.message
                    if (!causeDetail.isNullOrBlank()) {
                        "تعذر إتمام الطلب: $causeDetail"
                    } else {
                        "حدث خطأ غير متوقع أثناء الاتصال بـ Gemini AI. تأكد من الإنترنت وأعد المحاولة."
                    }
                }
                else -> {
                    val primaryMsg = error.localizedMessage?.takeIf {
                        it.isNotBlank() && !it.contains("Something unexpected happened", ignoreCase = true)
                    }
                    val causeMsg = rootCause.takeIf { it !== error }?.message?.takeIf {
                        it.isNotBlank() && !it.contains("Something unexpected happened", ignoreCase = true)
                    }
                    primaryMsg ?: causeMsg?.let { "تعذر إتمام الطلب: $it" }
                        ?: "تعذر الاتصال بالمعلم الذكي حالياً. حاول مرة أخرى."
                }
            }
        }
    }
}
