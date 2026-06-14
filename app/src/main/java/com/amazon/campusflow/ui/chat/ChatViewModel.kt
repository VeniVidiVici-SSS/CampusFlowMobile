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
import com.amazon.campusflow.data.MessMenuDao
import com.amazon.campusflow.data.MessMenuEvent
import com.amazon.campusflow.data.AwsService
import com.amazon.campusflow.utils.AlarmScheduler
import com.amazon.campusflow.utils.ExcelParser
import java.util.regex.Pattern

class ChatViewModel(
    private val dao: ChatMessageDao, 
    private val awsService: AwsService
) : ViewModel() {

    private var pendingSchedule: List<ScheduleEvent>? = null

    private val availableModels = listOf(
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash",
        "gemini-flash-latest",
        "gemini-2.0-flash",
        "gemini-3.1-pro-preview"
    )
    private var currentModelIndex = 0
    private var currentScheduleContext = "Loading schedule..."

    private var generativeModel = createGenerativeModel(availableModels[currentModelIndex], currentScheduleContext)

    private fun createGenerativeModel(modelName: String, scheduleContext: String): GenerativeModel {
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
You help students manage their schedules AND act as a generalized academic tutor.

You are fully capable of answering general queries on ANY subject, writing code, solving math/logic problems, and providing detailed academic explanations.

If a student asks about their schedule, classes, OR mess menu, answer naturally using the following context.
If they have no classes or menus scheduled, inform them politely.
---
CURRENT SCHEDULE AND MENU CONTEXT:
$scheduleContext
---

If a student wants to add a class to their schedule, you must gather 6 pieces of information: Course Name, Day of the week, Start Time, Location, Schedule Start Date (YYYY-MM-DD), and Schedule End Date (YYYY-MM-DD).

If a student wants to add a mess menu or food timing, you must gather 4 pieces of information: Meal Type (e.g. Breakfast/Lunch/Dinner), Day of the week, Time, and Menu.

CRITICAL CONVERSATIONAL RULES:
- The user will speak in natural language (e.g., "I have DSA on Monday at 10 PM in Hall A"). Extract the data yourself!
- DO NOT force the user to use a specific format for time or dates. 
- If the user provides a 12-hour time (like "10 PM"), YOU must silently convert it to 24-hour HH:mm format (e.g. "22:00") for the JSON block. Do not ask the user to format it.
- If information is missing, you MUST ask for EVERY SINGLE PIECE of missing information together in a SINGLE message. Do not ask for details one-by-one, as this wastes chat turns!
- Frame the request for missing info naturally. For example, instead of a robotic bulleted list, say: "Got it! To finish scheduling, could you tell me what room that is in, and what dates the semester starts and ends?"
- Do NOT ask for an end time for the class/meal.
- To DELETE a class or menu, ask for the Course Name/Meal Type and Day.
- To UPDATE a class or menu, you MUST output a DELETE intent for the old item, and then immediately output a SCHEDULE intent for the new item in the same message!

Once you have ALL details for a CLASS, output a secret block at the end:
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

Once you have ALL details for a MESS MENU, output a secret block at the end:
```json
{
  "intent": "schedule_mess_menu",
  "mealType": "...",
  "dayOfWeek": "...",
  "time": "...",
  "menu": "..."
}
```

To DELETE a CLASS, output:
```json
{
  "intent": "delete_class",
  "courseName": "...",
  "dayOfWeek": "..."
}
```

To DELETE a MESS MENU, output:
```json
{
  "intent": "delete_mess_menu",
  "mealType": "...",
  "dayOfWeek": "..."
}
```
Do not output the JSON block until you have ALL info gathered!
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
            generativeModel = createGenerativeModel(availableModels[currentModelIndex], currentScheduleContext)
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
            val classEvents = awsService.getAllClasses()
            val messEvents = awsService.getAllMessMenus()
            
            val classContext = if (classEvents.isEmpty()) "No classes scheduled." else "Classes:\n" + classEvents.joinToString("\n") { 
                "- ${it.courseName} on ${it.dayOfWeek} at ${it.startTime} in ${it.location}"
            }
            
            val messContext = if (messEvents.isEmpty()) "No mess menu added." else "Mess Menu:\n" + messEvents.joinToString("\n") {
                "- ${it.mealType} on ${it.dayOfWeek} at ${it.time}. Menu: ${it.menuItems}"
            }

            currentScheduleContext = "$classContext\n$messContext"

            generativeModel = createGenerativeModel(availableModels[currentModelIndex], currentScheduleContext)
            
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
                    awsService.insertClasses(eventsToInsert)
                    
                    // Insert the user's message now
                    val userMsg = ChatMessage(text = text.trim(), isFromUser = true)
                    dao.insertMessage(userMsg)

                    // Schedule Alarms
                    AlarmScheduler.scheduleAlarmsForEvents(context, pendingSchedule!!, startDate!!, endDate!!)
                    
                    val botMsg = ChatMessage(text = "Awesome! I have parsed your schedule from $startDate to $endDate and set up your class reminders. You'll get a notification 15 minutes before each class!", isFromUser = false)
                    dao.insertMessage(botMsg)
                    pendingSchedule = null
                    chatSession = null // Invalidate session to reload context with new schedule
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
                        
                        // Intercept JSON blocks robustly with regex
                        val intentRegex = """\{[\s\S]*?"intent"\s*:\s*"([^"]+)"[\s\S]*?\}""".toRegex()
                        val matches = intentRegex.findAll(reply).toList()
                        
                        if (matches.isNotEmpty()) {
                            try {
                                for (match in matches) {
                                    val jsonString = match.value
                                    val jsonObject = org.json.JSONObject(jsonString)
                                    val intentType = jsonObject.getString("intent")
                                    
                                    when (intentType) {
                                        "schedule_class" -> {
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
                                            awsService.insertClasses(listOf(event))
                                            AlarmScheduler.scheduleAlarmsForEvents(context, listOf(event), startDateStr, endDateStr)
                                        }
                                        "schedule_mess_menu" -> {
                                            val mealType = jsonObject.getString("mealType")
                                            val dayOfWeek = jsonObject.getString("dayOfWeek")
                                            val time = jsonObject.getString("time")
                                            val menu = jsonObject.getString("menu")
                                            val startMillis = System.currentTimeMillis()
                                            
                                            val event = MessMenuEvent(
                                                mealType = mealType,
                                                dayOfWeek = dayOfWeek,
                                                time = time,
                                                menuItems = menu,
                                                startDateMillis = startMillis
                                            )
                                            awsService.insertMessMenus(listOf(event))
                                            AlarmScheduler.scheduleAlarmsForMessMenus(context, listOf(event), startMillis)
                                        }
                                        "delete_class" -> {
                                            val courseName = jsonObject.getString("courseName")
                                            val dayOfWeek = jsonObject.getString("dayOfWeek")
                                            val event = awsService.getEvent(courseName, dayOfWeek)
                                            if (event != null) {
                                                AlarmScheduler.cancelAlarmsForEvents(context, event)
                                                awsService.deleteClass(courseName, dayOfWeek)
                                            }
                                        }
                                        "delete_mess_menu" -> {
                                            val mealType = jsonObject.getString("mealType")
                                            val dayOfWeek = jsonObject.getString("dayOfWeek")
                                            val event = awsService.getMessEvent(mealType, dayOfWeek)
                                            if (event != null) {
                                                AlarmScheduler.cancelAlarmsForMessMenus(context, event)
                                                awsService.deleteMessMenu(mealType, dayOfWeek)
                                            }
                                        }
                                    }
                                }
                                
                                // Strip JSON from cleanReply
                                var strippedReply = reply
                                for (match in matches) {
                                    strippedReply = strippedReply.replace(match.value, "")
                                }
                                cleanReply = strippedReply.replace("```json", "").replace("```", "").trim()
                                
                                if (cleanReply.isBlank()) {
                                    cleanReply = "Done! I've updated your schedule."
                                }
                                
                                chatSession = null // Invalidate session to reload context
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
            try {
                // Upload original Excel to S3 bucket
                val s3Stream = context.contentResolver.openInputStream(uri)
                if (s3Stream != null) {
                    try {
                        val s3Url = awsService.uploadScheduleToS3(s3Stream)
                        Log.d("ChatViewModel", "Uploaded schedule to S3: $s3Url")
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Failed to upload to S3", e)
                    } finally {
                        s3Stream.close()
                    }
                }

                // Parse locally
                val events = ExcelParser.parseSchedule(context, uri)
                if (events.isNotEmpty()) {
                    pendingSchedule = events
                    val botMsg = ChatMessage(text = "I've received your schedule containing ${events.size} classes! Enter start date and end date for the schedule (format: YYYY-MM-DD to YYYY-MM-DD).", isFromUser = false)
                    dao.insertMessage(botMsg)
                } else {
                    val botMsg = ChatMessage(text = "I couldn't find any valid classes in that Excel file. Please ensure it has columns: Course, Day, Time, Location.", isFromUser = false)
                    dao.insertMessage(botMsg)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error processing file", e)
            }
        }
    }
}

class ChatViewModelFactory(
    private val dao: ChatMessageDao, 
    private val awsService: AwsService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(dao, awsService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
