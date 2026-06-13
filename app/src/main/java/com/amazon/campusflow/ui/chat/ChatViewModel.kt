package com.amazon.campusflow.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.amazon.campusflow.data.ChatMessageDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import com.amazon.campusflow.BuildConfig
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

class ChatViewModel(private val dao: ChatMessageDao) : ViewModel() {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY,
        systemInstruction = content {
            text("You are CampusFlow, a highly intelligent, friendly, and helpful AI assistant designed specifically for university students. You help students manage their schedules, summarize their documents, and answer academic questions. Your tone should be encouraging, concise, and professional. You must never identify yourself simply as 'a large language model trained by Google'. You are CampusFlow. If asked who you are, state your name and your purpose clearly.")
        }
    )
    private var chatSession: Chat? = null

    val messages: StateFlow<List<ChatMessage>> = dao.getAllMessages()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private suspend fun getOrCreateChatSession(): Chat {
        return chatSession ?: withContext(Dispatchers.IO) {
            val history = dao.getMessagesSnapshot().map { msg ->
                content(role = if (msg.isFromUser) "user" else "model") {
                    text(msg.text)
                }
            }
            val chat = generativeModel.startChat(history)
            chatSession = chat
            chat
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        val userMsg = ChatMessage(text = text.trim(), isFromUser = true)
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertMessage(userMsg)
            
            try {
                val chat = getOrCreateChatSession()
                val response = chat.sendMessage(text.trim())
                response.text?.let { reply ->
                    val botMsg = ChatMessage(text = reply, isFromUser = false)
                    dao.insertMessage(botMsg)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error communicating with Gemini", e)
                val botMsg = ChatMessage(text = "Error: Could not connect to AI. Please try again.", isFromUser = false)
                dao.insertMessage(botMsg)
            }
        }
    }
}

class ChatViewModelFactory(private val dao: ChatMessageDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
