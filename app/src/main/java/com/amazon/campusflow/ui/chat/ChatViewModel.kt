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
import com.amazon.campusflow.data.MessMenuEvent
import com.amazon.campusflow.data.CustomEvent
import com.amazon.campusflow.data.MessMenuDao
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

If a student asks about their schedule, classes, mess menu, OR single custom events (like an extra class or meeting), answer naturally using the following context.
If they have no classes or events scheduled, inform them politely.
---
CURRENT SCHEDULE AND MENU CONTEXT:
$scheduleContext
---

If a student wants to add a CLASS to their schedule, you must gather: Course Name, Day of the week, Start Time, Location, and Schedule End Date (YYYY-MM-DD). 

If a student wants to add a MESS MENU or food timing, you must gather: Meal Type, Day of the week, Time, and Menu.

If a student wants to add a SINGLE CUSTOM EVENT (like a one-off extra class, hackathon, club meeting, invitation), you must gather: Event Name, Date (YYYY-MM-DD), Start Time, and End Time.

INTUITIVE CONVERSATIONAL RULES:
- BE INTUITIVE AND SMART: Do not act like a rigid form-filler. Read between the lines. If the user says "I have a meeting at 4", infer the Event Name is "Meeting". If they say "lunch", the meal type is "Lunch".
- CURRENT CONTEXT IS YOUR BRAIN: Use TODAY'S DATE and CURRENT TIME (provided below) to calculate relative times. If they say "in 15 minutes", "tomorrow", "next Monday", or "this evening", CALCULATE the exact dates and times yourself! Never ask the user to clarify something you can easily deduce.
- EVENT LOOKUPS: If deleting or modifying an event, look at the CURRENT SCHEDULE AND MENU CONTEXT. Extract the "Series Start Date" directly from there. NEVER ask the user for the original start date of an event.
- MISSING DETAILS: Only ask the user for details if they are absolutely required and cannot be logically inferred. When you do ask, frame it as a natural, friendly conversation (e.g., "Got it! When does the semester end for that class?"). Do not ask for details one-by-one; gently ask for whatever is missing in a single message.
- Time format: Accept any natural time format from the user. When outputting JSON, you may use 12-hour format with AM/PM (e.g. "06:25 PM") or 24-hour format (e.g. "18:25").
- To UPDATE an item, output a DELETE intent for the old item, followed immediately by a SCHEDULE intent for the new item.

Once you have ALL details for a CLASS, output a secret block at the end:
```json
{
  "intent": "schedule_class",
  "courseName": "...",
  "dayOfWeek": "...",
  "startTime": "...",
  "location": "...",
  "endDate": "..."
}
```

Once you have ALL details for a MESS MENU, output:
```json
{
  "intent": "schedule_mess_menu",
  "mealType": "...",
  "dayOfWeek": "...",
  "time": "...",
  "menu": "..."
}
```

Once you have ALL details for a SINGLE CUSTOM EVENT, output:
```json
{
  "intent": "schedule_custom_event",
  "eventName": "...",
  "date": "...",
  "startTime": "...",
  "endTime": "...",
  "repeatType": "...",
  "repeatInterval": 1,
  "repeatEndDate": "..."
}
```
*Note for custom events: `repeatType` can be "NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY", "CUSTOM_DAYS". `repeatInterval` is only used if type is "CUSTOM_DAYS". `repeatEndDate` is optional, use YYYY-MM-DD.*

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

To CANCEL a SPECIFIC INSTANCE of a recurring custom event (e.g. "cancel today's standup"), output:
```json
{
  "intent": "cancel_custom_event_instance",
  "eventName": "...",
  "dateToCancel": "..."
}
```

To DELETE an ENTIRE Custom Event series, output:
```json
{
  "intent": "delete_custom_event",
  "eventName": "..."
}
```
Do not output the JSON block until you have ALL info gathered!
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
            val customEvents = awsService.getAllCustomEvents()
            
            val classContext = if (classEvents.isEmpty()) "No classes scheduled." else "Classes:\n" + classEvents.joinToString("\n") { 
                "- ${it.courseName} on ${it.dayOfWeek} at ${it.startTime} in ${it.location}"
            }
            
            val messContext = if (messEvents.isEmpty()) "No mess menu added." else "Mess Menu:\n" + messEvents.joinToString("\n") {
                "- ${it.mealType} on ${it.dayOfWeek} at ${it.time}. Menu: ${it.menuItems}"
            }

            val customContext = if (customEvents.isEmpty()) "No custom events." else "Custom Events:\n" + customEvents.joinToString("\n") {
                val repeatContext = if (it.repeatType != "NONE") " (Repeats ${it.repeatType})" else ""
                "- ${it.eventName}. Series Start Date: ${it.date}. Time: ${it.startTime} to ${it.endTime}$repeatContext"
            }

            val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val currentTime = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            currentScheduleContext = "TODAY'S DATE: $todayDate | CURRENT TIME: $currentTime\n\n$classContext\n$messContext\n$customContext"

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
            // Check if we are waiting for dates from Excel upload
            if (pendingSchedule != null) {
                val pattern = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})")
                val matcher = pattern.matcher(text)
                if (matcher.find()) {
                    val endDateStr = matcher.group(1)
                    val cal = java.util.Calendar.getInstance()
                    val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    val startDateStr = format.format(cal.time)
                    
                    val eventsToInsert = pendingSchedule!!.map {
                        it.copy(startDateMillis = 0L, endDateMillis = 0L) // Simplified for hackathon
                    }
                    awsService.insertClasses(eventsToInsert)
                    
                    val userMsg = ChatMessage(text = text.trim(), isFromUser = true)
                    dao.insertMessage(userMsg)

                    AlarmScheduler.scheduleAlarmsForEvents(context, pendingSchedule!!, startDateStr, endDateStr!!)
                    
                    val botMsg = ChatMessage(text = "Awesome! I have set your schedule to start today and end on $endDateStr. You'll get a notification 15 minutes before each class!", isFromUser = false)
                    dao.insertMessage(botMsg)
                    pendingSchedule = null
                    chatSession = null // Invalidate session to reload context with new schedule
                    return@launch
                } else {
                    val userMsg = ChatMessage(text = text.trim(), isFromUser = true)
                    dao.insertMessage(userMsg)
                    
                    val botMsg = ChatMessage(text = "I couldn't understand that date. Please provide the end date exactly as: YYYY-MM-DD.", isFromUser = false)
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
                                            val endDateStr = jsonObject.getString("endDate")
                                            
                                            val cal = java.util.Calendar.getInstance()
                                            val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                            val startDateStr = format.format(cal.time)
                                            
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
                                        "schedule_custom_event" -> {
                                            val eventName = jsonObject.getString("eventName")
                                            val date = jsonObject.getString("date")
                                            val startTime = jsonObject.getString("startTime")
                                            val endTime = jsonObject.getString("endTime")
                                            val repeatType = if (jsonObject.has("repeatType")) jsonObject.getString("repeatType") else "NONE"
                                            val repeatInterval = if (jsonObject.has("repeatInterval")) jsonObject.getInt("repeatInterval") else 1
                                            val repeatEndDate = if (jsonObject.has("repeatEndDate")) jsonObject.getString("repeatEndDate") else null
                                            
                                            val event = CustomEvent(
                                                eventName = eventName,
                                                date = date,
                                                startTime = startTime,
                                                endTime = endTime,
                                                repeatType = repeatType,
                                                repeatInterval = repeatInterval,
                                                repeatEndDate = repeatEndDate
                                            )
                                            // Schedule alarm FIRST so it works even if AWS fails
                                            AlarmScheduler.scheduleAlarmsForCustomEvents(context, event)
                                            try {
                                                awsService.insertCustomEvent(event)
                                            } catch (e: Exception) {
                                                Log.e("ChatViewModel", "Failed to sync custom event to AWS", e)
                                            }
                                        }
                                        "cancel_custom_event_instance" -> {
                                            val eventName = jsonObject.getString("eventName")
                                            val dateToCancel = jsonObject.getString("dateToCancel")
                                            
                                            try {
                                                // Intuitively find the existing event by name so the AI doesn't have to guess the series start date
                                                val existingEvent = awsService.getAllCustomEvents().find { it.eventName.equals(eventName, ignoreCase = true) }
                                                if (existingEvent != null) {
                                                    val seriesStartDate = existingEvent.date
                                                    AlarmScheduler.cancelAlarmsForCustomEvents(context, existingEvent)
                                                    awsService.cancelCustomEventInstance(eventName, seriesStartDate, dateToCancel)
                                                    
                                                    // Fetch the updated event and reschedule
                                                    val updatedEvent = awsService.getCustomEvent(eventName, seriesStartDate)
                                                    if (updatedEvent != null) {
                                                        AlarmScheduler.scheduleAlarmsForCustomEvents(context, updatedEvent)
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e("ChatViewModel", "Failed to cancel instance", e)
                                            }
                                        }
                                        "delete_custom_event" -> {
                                            val eventName = jsonObject.getString("eventName")
                                            // Find the event by name intuitively
                                            val event = awsService.getAllCustomEvents().find { it.eventName.equals(eventName, ignoreCase = true) }
                                            if (event != null) {
                                                AlarmScheduler.cancelAlarmsForCustomEvents(context, event)
                                                try {
                                                    awsService.deleteCustomEvent(event.eventName, event.date)
                                                } catch (e: Exception) {
                                                    Log.e("ChatViewModel", "Failed to delete custom event from AWS", e)
                                                }
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
                    val botMsg = ChatMessage(text = "I've received your schedule containing ${events.size} classes! The start date is set to today. Please provide the End Date for the schedule (format: YYYY-MM-DD).", isFromUser = false)
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
