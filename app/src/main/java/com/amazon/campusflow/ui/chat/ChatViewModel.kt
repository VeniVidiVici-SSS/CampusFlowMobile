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

import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.BlockThreshold
import android.content.Context
import android.net.Uri
import com.amazon.campusflow.data.ScheduleDao
import com.amazon.campusflow.data.ScheduleEvent
import com.amazon.campusflow.utils.AlarmScheduler
import com.amazon.campusflow.utils.ExcelParser
import java.util.regex.Pattern

class ChatViewModel(private val dao: ChatMessageDao, private val scheduleDao: ScheduleDao) : ViewModel() {

    private var pendingSchedule: List<ScheduleEvent>? = null

    private val availableModels = listOf(
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash",
        "gemini-flash-latest",
        "gemini-2.0-flash",
        "gemini-3.1-pro-preview"
    )
    private var currentModelIndex = 0

    private var generativeModel = createGenerativeModel(availableModels[currentModelIndex])

    private fun createGenerativeModel(modelName: String): GenerativeModel {
        return GenerativeModel(
            modelName = modelName,
            apiKey = BuildConfig.GEMINI_API_KEY,
            safetySettings = listOf(
                SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
                SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
                SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
                SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
            ),
            systemInstruction = content {
                text("""
You are CampusFlow, a highly intelligent, friendly AI assistant designed for university students.
You help students manage their schedules.

If a student wants to add a class to their schedule, you must gather these exactly 6 pieces of information:
1. Course Name
2. Day of the week (e.g. Saturday)
3. Start Time (MUST be in HH:mm 24-hour format)
4. Location
5. Schedule Start Date (YYYY-MM-DD)
6. Schedule End Date (YYYY-MM-DD)

IMPORTANT INSTRUCTIONS:
- You MUST ask for ALL missing information in a SINGLE message to save time. Do not ask for details one-by-one!
- Inform the student that classes repeat weekly within the start and end dates. 
- Do NOT ask for an end time for the class. Ask for the start date and end date instead.

Once you have gathered ALL 6 pieces of information, you MUST output a secret scheduling block at the very end of your message in exactly this format:
```json
{
  "intent": "schedule_class",
  "courseName": "...",
  "dayOfWeek": "...",
  "startTime": "...",
  "location": "...",
  "startDate": "...",
  "endDate": "..."
}
```
Do not output the JSON block until you have all 6 pieces of info!
            """.trimIndent())
            }
        )
    }

    private fun switchToNextModel(): Boolean {
        if (currentModelIndex < availableModels.size - 1) {
            currentModelIndex++
            generativeModel = createGenerativeModel(availableModels[currentModelIndex])
            chatSession = null // Force recreation of chat session with the new model
            return true
        }
        return false
    }

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

    fun sendMessage(context: Context, text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            // Check if we are waiting for dates
            if (pendingSchedule != null) {
                val pattern = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s*(?:to|-)\\s*(\\d{4}-\\d{2}-\\d{2})")
                val matcher = pattern.matcher(text)
                if (matcher.find()) {
                    val startDate = matcher.group(1)
                    val endDate = matcher.group(2)
                    
                    val eventsToInsert = pendingSchedule!!.map {
                        it.copy(startDateMillis = 0L, endDateMillis = 0L) // Simplified for hackathon
                    }
                    scheduleDao.insertAll(eventsToInsert)
                    
                    // Insert the user's message now
                    val userMsg = ChatMessage(text = text.trim(), isFromUser = true)
                    dao.insertMessage(userMsg)

                    // Schedule Alarms
                    AlarmScheduler.scheduleAlarmsForEvents(context, pendingSchedule!!, startDate!!, endDate!!)
                    
                    val botMsg = ChatMessage(text = "Awesome! I have parsed your schedule from $startDate to $endDate and set up your class reminders. You'll get a notification 15 minutes before each class!", isFromUser = false)
                    dao.insertMessage(botMsg)
                    pendingSchedule = null
                    return@launch
                } else {
                    val userMsg = ChatMessage(text = text.trim(), isFromUser = true)
                    dao.insertMessage(userMsg)
                    
                    val botMsg = ChatMessage(text = "I couldn't understand those dates. Please use the exact format: YYYY-MM-DD to YYYY-MM-DD.", isFromUser = false)
                    dao.insertMessage(botMsg)
                    return@launch
                }
            }

            var success = false
            var exceptionMessage = ""
            var retries = 0

            while (!success && retries <= availableModels.size) {
                try {
                    // Initialize the chat session with history BEFORE inserting the new message
                    val chat = getOrCreateChatSession()
                    
                    if (retries == 0) {
                        // Now insert the user message into the DB so it shows in the UI exactly once
                        val userMsg = ChatMessage(text = text.trim(), isFromUser = true)
                        dao.insertMessage(userMsg)
                    }
                    
                    val response = chat.sendMessage(text.trim())
                    response.text?.let { reply ->
                        var cleanReply = reply
                        
                        // Intercept JSON block robustly
                        val intentIdx = reply.indexOf("\"intent\": \"schedule_class\"")
                        if (intentIdx != -1) {
                            try {
                                val jsonStart = reply.lastIndexOf("{", intentIdx)
                                val jsonEnd = reply.indexOf("}", intentIdx) + 1
                                if (jsonStart != -1 && jsonEnd > jsonStart) {
                                    val jsonString = reply.substring(jsonStart, jsonEnd).trim()
                                    val jsonObject = org.json.JSONObject(jsonString)
                                    val courseName = jsonObject.getString("courseName")
                                    val dayOfWeek = jsonObject.getString("dayOfWeek")
                                    val startTime = jsonObject.getString("startTime")
                                    val location = jsonObject.getString("location")
                                    val startDateStr = jsonObject.getString("startDate")
                                    val endDateStr = jsonObject.getString("endDate")
                                    
                                    val event = ScheduleEvent(
                                        courseName = courseName,
                                        dayOfWeek = dayOfWeek,
                                        startTime = startTime,
                                        location = location,
                                        startDateMillis = 0L,
                                        endDateMillis = 0L
                                    )
                                    scheduleDao.insertAll(listOf(event))
                                    AlarmScheduler.scheduleAlarmsForEvents(context, listOf(event), startDateStr, endDateStr)
                                    
                                    // Clean the reply
                                    val beforeJson = reply.substring(0, jsonStart)
                                    val afterJson = if (reply.length > jsonEnd) reply.substring(jsonEnd) else ""
                                    cleanReply = (beforeJson + afterJson).replace("```json", "").replace("```", "").trim()
                                    
                                    if (cleanReply.isBlank()) {
                                        cleanReply = "Class scheduled successfully! You'll get an alert 15 minutes before."
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("ChatViewModel", "Error parsing schedule intent", e)
                            }
                        }

                        val botMsg = ChatMessage(text = cleanReply, isFromUser = false)
                        dao.insertMessage(botMsg)
                        success = true
                    }
                } catch (e: Exception) {
                    val isQuotaError = e is com.google.ai.client.generativeai.type.QuotaExceededException || 
                                       e.message?.contains("Quota exceeded") == true ||
                                       e.message?.contains("429") == true
                                       
                    if (isQuotaError && switchToNextModel()) {
                        retries++
                        Log.w("ChatViewModel", "Quota exceeded, silently falling back to model: ${availableModels[currentModelIndex]}")
                        // Loop continues and retries automatically
                    } else {
                        Log.e("ChatViewModel", "Error communicating with Gemini", e)
                        exceptionMessage = e.message ?: "Could not connect to AI. Please try again."
                        break
                    }
                }
            }
            
            if (!success) {
                val botMsg = ChatMessage(text = "Error: $exceptionMessage", isFromUser = false)
                dao.insertMessage(botMsg)
            }
        }
    }

    fun processScheduleFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val events = ExcelParser.parseSchedule(context, uri)
            if (events.isNotEmpty()) {
                pendingSchedule = events
                val botMsg = ChatMessage(text = "I've received your schedule containing ${events.size} classes! Enter start date and end date for the schedule (format: YYYY-MM-DD to YYYY-MM-DD).", isFromUser = false)
                dao.insertMessage(botMsg)
            } else {
                val botMsg = ChatMessage(text = "I couldn't find any valid classes in that Excel file. Please ensure it has columns: Course, Day, Time, Location.", isFromUser = false)
                dao.insertMessage(botMsg)
            }
        }
    }
}

class ChatViewModelFactory(private val dao: ChatMessageDao, private val scheduleDao: ScheduleDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(dao, scheduleDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
