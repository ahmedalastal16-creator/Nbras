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
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@Keep
@IgnoreExtraProperties
data class ConversationSummary(
    @DocumentId val id: String = "",
    val userId: String = "",
    val stageId: String = "",
    val stageTitle: String = "",
    val subjectTitle: String = "",
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val role: String = "user",
    val text: String = "",
    val imageUri: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

class FirestoreChatRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("يجب تسجيل الدخول عبر Google للوصول إلى المحادثات السحابية.")
    }

    fun observeSavedConversations(userId: String): Flow<List<ConversationSummary>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != userId) {
            throw IllegalStateException("المستخدم غير مسجل الدخول.")
        }
        val path = "users/$uid/conversations"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("conversations")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ConversationSummary::class.java,
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

    fun observeMessages(userId: String, conversationId: String): Flow<List<FirestoreChatMessage>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != userId) {
            throw IllegalStateException("المستخدم غير مسجل الدخول.")
        }
        val path = "users/$uid/conversations/$conversationId/messages"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FirestoreChatMessage::class.java,
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

    suspend fun saveMessage(
        conversationId: String,
        stage: GazaStage,
        subjectTitle: String,
        role: String,
        text: String,
        imageUri: String? = null
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/conversations/$conversationId/messages"
        return try {
            val uid = requireUserId()
            require(conversationId.matches(Regex("^[a-zA-Z0-9_\\-]+$")) && conversationId.length <= 128) {
                "Invalid conversationId"
            }
            require(role == "user" || role == "model") { "Invalid role" }
            val safeText = text.trim().take(20_000)
            require(safeText.isNotEmpty()) { "Message text cannot be empty" }

            val safeStageTitle = stage.title.take(120).ifBlank { "رزم غزة" }
            val safeSubjectTitle = subjectTitle.take(120).ifBlank { "مبحث دراسي" }
            val preview = safeText.replace('\n', ' ').take(280)

            val convRef = db.collection("users")
                .document(uid)
                .collection("conversations")
                .document(conversationId)
            val msgRef = convRef.collection("messages").document()
            docPath = msgRef.path

            val cachedSnap = runCatching { convRef.get(Source.CACHE).await() }.getOrNull()
            val convExists = if (cachedSnap != null && cachedSnap.exists()) {
                true
            } else {
                convRef.get().await().exists()
            }
            val batch = db.batch()
            if (!convExists) {
                val convPayload = mapOf(
                    "userId" to uid,
                    "stageId" to stage.id.take(64),
                    "stageTitle" to safeStageTitle,
                    "subjectTitle" to safeSubjectTitle,
                    "lastMessagePreview" to preview,
                    "messageCount" to 1,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(convRef, convPayload)
            } else {
                val updatePayload = mapOf(
                    "stageTitle" to safeStageTitle,
                    "subjectTitle" to safeSubjectTitle,
                    "lastMessagePreview" to preview,
                    "messageCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.update(convRef, updatePayload)
            }

            val msgPayload = buildMap<String, Any> {
                put("userId", uid)
                put("conversationId", conversationId)
                put("role", role)
                put("text", safeText)
                if (!imageUri.isNullOrBlank()) {
                    put("imageUri", imageUri.take(1000))
                }
                put("createdAt", FieldValue.serverTimestamp())
            }
            batch.set(msgRef, msgPayload)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearConversation(conversationId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/conversations/$conversationId"
        return try {
            val uid = requireUserId()
            val convRef = db.collection("users")
                .document(uid)
                .collection("conversations")
                .document(conversationId)
            docPath = convRef.path

            val messagesSnap = convRef.collection("messages").get().await()
            val docs = messagesSnap.documents
            for (chunk in docs.chunked(400)) {
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            if (convRef.get().await().exists()) {
                convRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
