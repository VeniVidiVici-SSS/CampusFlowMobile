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

class ChatViewModel(private val dao: ChatMessageDao) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> = dao.getAllMessages()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Send a welcome message if the database is likely empty.
        // For simplicity, we just send it once. A real app might check count.
        viewModelScope.launch {
            // Uncomment to populate initially if needed, but we'll let it be.
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        val userMsg = ChatMessage(text = text.trim(), isFromUser = true)
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertMessage(userMsg)
            
            // Simulate AI response for now
            simulateResponse("I received your message: \"$text\". Gemini integration coming soon.")
        }
    }

    private suspend fun simulateResponse(replyText: String) {
        val botMsg = ChatMessage(text = replyText, isFromUser = false)
        withContext(Dispatchers.IO) {
            dao.insertMessage(botMsg)
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
