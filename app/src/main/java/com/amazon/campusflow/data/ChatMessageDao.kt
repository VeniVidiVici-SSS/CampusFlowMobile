package com.amazon.campusflow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.amazon.campusflow.ui.chat.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getMessagesSnapshot(): List<ChatMessage>

    @Insert
    fun insertMessage(message: ChatMessage)
}
