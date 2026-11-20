package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "FirebaseAiRepository"
private const val MODEL_NAME = "gemini-3.8-flash"
private const val AI_TIMEOUT_MS = 60_000L

class FirebaseAiRepository(private val context: Context) : AiRepository {

    private val systemPromptText = """
        أنت "نبراس"، المعلم الذكي المتخصص حصرياً في الرزم التعليمية الفلسطينية لقطاع غزة الصادرة عن وزارة التربية والتعليم العالي الفلسطينية (بوابة الرزم التعليمية: moe.edu.ps/gaza/subjects).
        قواعدك الصارمة:
        1. التزم دائماً بمنهاج الرزم التعليمية لطلبة غزة في المرحلة والمبحث المحددين في السؤال، واشرح المفاهيم بلغة عربية فصيحة واضحة وأسلوب تعليمي راقٍ ومنظم.
        2. اربط إجاباتك بالوحدات والمفاهيم المعتمدة في الرزم التعليمية الفلسطينية وبأسئلة الاختبارات الوزارية/التوجيهي ونماذج الوزارة المعتمدة.
        3. إذا طرح الطالب سؤالاً خارج نطاق الدراسة أو خارج الرزم التعليمية، وجّهه بلطف ولباقة للعودة إلى مباحث الرزم التعليمية لقطاع غزة واقترح عليه محوراً دراسياً مرتبطاً بمبحثه الحالي.
        4. نظّم الإجابة بعناوين قصيرة، نقاط واضحة، وقوانين أو أمثلة تطبيقية دقيقة.
    """.trimIndent()

    private val tutorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            systemInstruction = content { text(systemPromptText) }
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
                            "question" to Schema.string(description = "نص السؤال بأسلوب اختبارات الرزم الفلسطينية"),
                            "options" to Schema.array(
                                items = Schema.string(),
                                description = "أربعة خيارات نصية فقط"
                            ),
                            "correctIndex" to Schema.integer(description = "فهرس الإجابة الصحيحة من 0 إلى 3"),
                            "explanation" to Schema.string(description = "توضيح سبب صحة الإجابة")
                        )
                    ),
                    description = "قائمة أسئلة الاختيار من متعدد"
                )
            )
        )
    }

    private val summaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = summarySchema
            },
            systemInstruction = content { text(systemPromptText) }
        )
    }

    private val flashcardsModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = flashcardsSchema
            },
            systemInstruction = content { text(systemPromptText) }
        )
    }

    private val quizModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = quizSchema
            },
            systemInstruction = content { text(systemPromptText) }
        )
    }

    override fun askTutorStream(
        prompt: String,
        stageTitle: String,
        subjectTitle: String,
        imageUri: String?
    ): Flow<String> = flow {
        val bitmap = imageUri?.let { loadScaledBitmap(it) }
        val contextHeader = "المرحلة/الفرع: $stageTitle | المبحث: $subjectTitle (من رزم قطاع غزة التعليمية moe.edu.ps/gaza/subjects)\n\n"
        val userInput = content {
            if (bitmap != null) {
                image(bitmap)
            }
            text(contextHeader + "سؤال الطالب: $prompt")
        }

        val accumulated = StringBuilder()
        withTimeout(AI_TIMEOUT_MS) {
            tutorModel.generateContentStream(userInput).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    accumulated.append(part)
                    emit(accumulated.toString())
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
        imageUri: String?
    ): AiSummaryResult {
        val bitmap = imageUri?.let { loadScaledBitmap(it) }
        val promptText = buildString {
            appendLine("المرحلة/الفرع: $stageTitle | المبحث: $subjectTitle (رزم قطاع غزة التعليمية - moe.edu.ps/gaza/subjects).")
            appendLine("أنشئ ملخصاً أكاديمياً فاخراً ومنظماً بالعربية للمحتوى التالي ضمن هذا المبحث:")
            appendLine(topicOrNotes.ifBlank { "حلّل صورة الملاحظات/الرزمة المرفقة ولخّصها." })
        }
        val inputContent = content {
            if (bitmap != null) {
                image(bitmap)
            }
            text(promptText)
        }

        val response = withTimeout(AI_TIMEOUT_MS) {
            summaryModel.generateContent(inputContent)
        }
        val rawJson = response.text ?: throw IllegalStateException("استجابة فارغة من نموذج الملخصات.")
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
        count: Int
    ): List<GeneratedFlashcard> {
        val promptText = """
            المرحلة/الفرع: $stageTitle | المبحث: $subjectTitle (رزم قطاع غزة التعليمية - moe.edu.ps/gaza/subjects).
            أنشئ $count بطاقات تعليمية (سؤال وجواب وتلميح) دقيقة ومفيدة للمراجعة النهائية حول:
            $topicOrNotes
        """.trimIndent()

        val response = withTimeout(AI_TIMEOUT_MS) {
            flashcardsModel.generateContent(promptText)
        }
        val rawJson = response.text ?: throw IllegalStateException("استجابة فارغة من مولد البطاقات.")
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
        count: Int
    ): List<QuizQuestion> {
        val promptText = """
            المرحلة/الفرع: $stageTitle | المبحث: $subjectTitle (رزم قطاع غزة التعليمية - moe.edu.ps/gaza/subjects).
            أنشئ اختباراً من $count أسئلة اختيار من متعدد (4 خيارات لكل سؤال) بأسلوب أسئلة الامتحانات الفلسطينية حول:
            $topicOrNotes
        """.trimIndent()

        val response = withTimeout(AI_TIMEOUT_MS) {
            quizModel.generateContent(promptText)
        }
        val rawJson = response.text ?: throw IllegalStateException("استجابة فارغة من مولد الاختبار.")
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
                isSampleResponse = false
            )
        }
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
            while (maxDim / sampleSize > 1024) {
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
}
