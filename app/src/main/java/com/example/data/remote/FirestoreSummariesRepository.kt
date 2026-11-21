package com.example.data.remote

import android.content.Context
import androidx.annotation.Keep
import com.example.R
import com.example.data.curriculum.GazaStage
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

@Keep
@IgnoreExtraProperties
data class CloudStudySummary(
    @DocumentId val id: String = "",
    val userId: String = "",
    val stageId: String = "",
    val stageTitle: String = "",
    val subjectTitle: String = "",
    val title: String = "",
    val content: String = "",
    val keyTerms: String = "",
    val sourceType: String = "manual",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class FirestoreSummariesRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("سجّل دخولك عبر Google أو Apple لحفظ ومزامنة الملخصات سحابياً.")
    }

    fun observeSummaries(userId: String): Flow<List<CloudStudySummary>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != userId) {
            throw IllegalStateException("المستخدم غير مسجل الدخول.")
        }
        val path = "users/$uid/summaries"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("summaries")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudStudySummary::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveSummary(
        stage: GazaStage,
        subjectTitle: String,
        title: String,
        content: String,
        keyTerms: String,
        sourceType: String = "manual",
        existingId: String? = null
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/summaries"
        return try {
            val uid = requireUserId()
            val safeStageId = stage.id.take(64).replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                .ifBlank { "tawjihi_scientific" }
            val safeStageTitle = stage.title.trim().take(120).ifBlank { "رزم غزة" }
            val safeSubject = subjectTitle.trim().take(120).ifBlank { "مبحث دراسي" }
            val safeTitle = title.trim().take(180)
            val safeContent = content.trim().take(25_000)
            val safeTerms = keyTerms.trim().take(500)
            val safeSource = when (sourceType) {
                "ai_lab", "tutor", "manual" -> sourceType
                else -> "manual"
            }

            require(safeTitle.isNotEmpty()) { "عنوان الملخص مطلوب." }
            require(safeContent.isNotEmpty()) { "محتوى الملخص لا يمكن أن يكون فارغاً." }

            val targetId = existingId?.takeIf {
                it.matches(Regex("^[a-zA-Z0-9_\\-]+$")) && it.length in 1..128
            } ?: "sum_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

            val docRef = db.collection("users")
                .document(uid)
                .collection("summaries")
                .document(targetId)
            docPath = docRef.path

            if (existingId != null && targetId == existingId) {
                val updatePayload = mapOf(
                    "stageId" to safeStageId,
                    "stageTitle" to safeStageTitle,
                    "subjectTitle" to safeSubject,
                    "title" to safeTitle,
                    "content" to safeContent,
                    "keyTerms" to safeTerms,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            } else {
                val createPayload = mapOf(
                    "userId" to uid,
                    "stageId" to safeStageId,
                    "stageTitle" to safeStageTitle,
                    "subjectTitle" to safeSubject,
                    "title" to safeTitle,
                    "content" to safeContent,
                    "keyTerms" to safeTerms,
                    "sourceType" to safeSource,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            }
            Result.success(targetId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (existingId != null) OperationType.UPDATE else OperationType.CREATE,
                docPath
            )
            Result.failure(e)
        }
    }

    suspend fun deleteSummary(summaryId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/summaries/$summaryId"
        return try {
            val uid = requireUserId()
            require(summaryId.matches(Regex("^[a-zA-Z0-9_\\-]+$")) && summaryId.length <= 128) {
                "معرّف الملخص غير صالح."
            }
            val docRef = db.collection("users")
                .document(uid)
                .collection("summaries")
                .document(summaryId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
