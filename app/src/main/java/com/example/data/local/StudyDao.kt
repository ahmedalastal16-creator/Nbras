package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    @Query("SELECT * FROM study_decks ORDER BY createdAt DESC")
    fun observeDecks(): Flow<List<StudyDeckEntity>>

    @Query("SELECT COUNT(*) FROM study_decks")
    suspend fun getDeckCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeck(deck: StudyDeckEntity): Long

    @Query("DELETE FROM study_decks WHERE id = :deckId")
    suspend fun deleteDeckById(deckId: Long)

    @Query("SELECT * FROM flashcards ORDER BY createdAt DESC")
    fun observeAllFlashcards(): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId ORDER BY createdAt DESC")
    fun observeFlashcardsForDeck(deckId: Long): Flow<List<FlashcardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcard(card: FlashcardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlashcards(cards: List<FlashcardEntity>)

    @Update
    suspend fun updateFlashcard(card: FlashcardEntity)

    @Query("DELETE FROM flashcards WHERE id = :cardId")
    suspend fun deleteFlashcardById(cardId: Long)

    @Query("SELECT * FROM study_notes ORDER BY createdAt DESC")
    fun observeAllNotes(): Flow<List<StudyNoteEntity>>

    @Query("SELECT * FROM study_notes WHERE deckId = :deckId ORDER BY createdAt DESC")
    fun observeNotesForDeck(deckId: Long): Flow<List<StudyNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: StudyNoteEntity): Long

    @Query("DELETE FROM study_notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: Long)

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun observeChatMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearChatHistory()

    @Query("SELECT * FROM focus_sessions ORDER BY completedAt DESC")
    fun observeFocusSessions(): Flow<List<FocusSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFocusSession(session: FocusSessionEntity): Long

    @Query("SELECT * FROM quiz_attempts ORDER BY completedAt DESC")
    fun observeQuizAttempts(): Flow<List<QuizAttemptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizAttempt(attempt: QuizAttemptEntity): Long
}
