package com.amazon.campusflow.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.amazon.campusflow.data.ScheduleEvent
import com.amazon.campusflow.data.MessMenuEvent
import com.amazon.campusflow.data.CustomEvent
import com.amazon.campusflow.notifications.NotificationReceiver
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object AlarmScheduler {
    fun scheduleAlarmsForEvents(context: Context, events: List<ScheduleEvent>, startDateStr: String, endDateStr: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat12 = SimpleDateFormat("h:mm a", Locale.getDefault()) // 10:00 AM or 6:15 AM
        val timeFormat24 = SimpleDateFormat("H:mm", Locale.getDefault()) // 21:15 or 6:15

        try {
            val startDate = dateFormat.parse(startDateStr) ?: return
            val endDate = dateFormat.parse(endDateStr) ?: return

            val currentCal = Calendar.getInstance().apply { time = startDate }
            val endCal = Calendar.getInstance().apply { time = endDate }

            while (currentCal.before(endCal) || currentCal.timeInMillis == endCal.timeInMillis) {
                val dayOfWeekInt = currentCal.get(Calendar.DAY_OF_WEEK)
                val dayOfWeekString = getDayString(dayOfWeekInt)

                for (event in events) {
                    if (event.dayOfWeek.equals(dayOfWeekString, ignoreCase = true)) {
                        try {
                            val timeDate = try { timeFormat12.parse(event.startTime) } catch (e: Exception) { null } 
                                           ?: try { timeFormat24.parse(event.startTime) } catch (e: Exception) { null }
                            if (timeDate != null) {
                                val timeCal = Calendar.getInstance().apply { time = timeDate }
                                val alarmCal = Calendar.getInstance().apply {
                                    timeInMillis = currentCal.timeInMillis
                                    set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                                    set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                                    set(Calendar.SECOND, 0)
                                    // Subtract 15 minutes for the alarm
                                    add(Calendar.MINUTE, -15)
                                }

                                // If alarm time is in the future, schedule it
                                if (alarmCal.timeInMillis > System.currentTimeMillis()) {
                                    val intent = Intent(context, NotificationReceiver::class.java).apply {
                                        putExtra("COURSE_NAME", event.courseName)
                                        putExtra("LOCATION", event.location)
                                    }
                                    // Use a unique ID for each alarm based on event + time
                                    val requestCode = (event.courseName.hashCode() + alarmCal.timeInMillis).toInt()
                                    val pendingIntent = PendingIntent.getBroadcast(
                                        context,
                                        requestCode,
                                        intent,
                                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                    )

                                    try {
                                        alarmManager.setExactAndAllowWhileIdle(
                                            AlarmManager.RTC_WAKEUP,
                                            alarmCal.timeInMillis,
                                            pendingIntent
                                        )
                                    } catch (e: SecurityException) {
                                        Log.e("AlarmScheduler", "Exact alarm permission missing", e)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("AlarmScheduler", "Error parsing time: ${event.startTime}", e)
                        }
                    }
                }
                currentCal.add(Calendar.DAY_OF_YEAR, 1)
            }
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "Error scheduling alarms", e)
        }
    }

    fun scheduleAlarmsForMessMenus(context: Context, events: List<MessMenuEvent>, startDateMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val timeFormat12 = SimpleDateFormat("h:mm a", Locale.getDefault())
        val timeFormat24 = SimpleDateFormat("H:mm", Locale.getDefault())

        try {
            val currentCal = Calendar.getInstance().apply { timeInMillis = startDateMillis }
            // Schedule for 12 weeks (84 days) into the future
            val endCal = Calendar.getInstance().apply { 
                timeInMillis = startDateMillis
                add(Calendar.DAY_OF_YEAR, 84)
            }

            while (currentCal.before(endCal)) {
                val dayOfWeekInt = currentCal.get(Calendar.DAY_OF_WEEK)
                val dayOfWeekString = getDayString(dayOfWeekInt)

                for (event in events) {
                    if (event.dayOfWeek.equals(dayOfWeekString, ignoreCase = true)) {
                        try {
                            val timeDate = try { timeFormat12.parse(event.time) } catch (e: Exception) { null } 
                                           ?: try { timeFormat24.parse(event.time) } catch (e: Exception) { null }
                            if (timeDate != null) {
                                val timeCal = Calendar.getInstance().apply { time = timeDate }
                                val alarmCal = Calendar.getInstance().apply {
                                    timeInMillis = currentCal.timeInMillis
                                    set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                                    set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                                    set(Calendar.SECOND, 0)
                                    // Subtract 15 minutes for the alarm
                                    add(Calendar.MINUTE, -15)
                                }

                                if (alarmCal.timeInMillis > System.currentTimeMillis()) {
                                    val intent = Intent(context, NotificationReceiver::class.java).apply {
                                        putExtra("COURSE_NAME", event.mealType)
                                        putExtra("LOCATION", event.menuItems)
                                        putExtra("IS_MESS", true)
                                    }
                                    val requestCode = ("Mess${event.mealType}".hashCode() + alarmCal.timeInMillis).toInt()
                                    val pendingIntent = PendingIntent.getBroadcast(
                                        context,
                                        requestCode,
                                        intent,
                                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                                    )

                                    try {
                                        alarmManager.setExactAndAllowWhileIdle(
                                            AlarmManager.RTC_WAKEUP,
                                            alarmCal.timeInMillis,
                                            pendingIntent
                                        )
                                    } catch (e: SecurityException) {
                                        Log.e("AlarmScheduler", "Exact alarm permission missing", e)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("AlarmScheduler", "Error parsing time: ${event.time}", e)
                        }
                    }
                }
                currentCal.add(Calendar.DAY_OF_YEAR, 1)
            }
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "Error scheduling mess menu alarms", e)
        }
    }

    fun cancelAlarmsForEvents(context: Context, event: ScheduleEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val timeFormat12 = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val timeFormat24 = SimpleDateFormat("HH:mm", Locale.getDefault())

        try {
            val currentCal = Calendar.getInstance()
            currentCal.add(Calendar.DAY_OF_YEAR, -30)
            val endCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 365) }

            while (currentCal.before(endCal)) {
                val dayOfWeekInt = currentCal.get(Calendar.DAY_OF_WEEK)
                val dayOfWeekString = getDayString(dayOfWeekInt)

                if (event.dayOfWeek.equals(dayOfWeekString, ignoreCase = true)) {
                    val timeDate = try { timeFormat12.parse(event.startTime) } catch (e: Exception) { null } 
                                   ?: try { timeFormat24.parse(event.startTime) } catch (e: Exception) { null }
                    if (timeDate != null) {
                        val timeCal = Calendar.getInstance().apply { time = timeDate }
                        val alarmCal = Calendar.getInstance().apply {
                            timeInMillis = currentCal.timeInMillis
                            set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                            set(Calendar.SECOND, 0)
                            add(Calendar.MINUTE, -15)
                        }

                        val intent = Intent(context, NotificationReceiver::class.java).apply {
                            putExtra("COURSE_NAME", event.courseName)
                            putExtra("LOCATION", event.location)
                        }
                        val requestCode = (event.courseName.hashCode() + alarmCal.timeInMillis).toInt()
                        
                        val pendingIntent = PendingIntent.getBroadcast(
                            context,
                            requestCode,
                            intent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        alarmManager.cancel(pendingIntent)
                        pendingIntent.cancel()
                    }
                }
                currentCal.add(Calendar.DAY_OF_YEAR, 1)
            }
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "Error canceling alarms", e)
        }
    }

    fun cancelAlarmsForMessMenus(context: Context, event: MessMenuEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val timeFormat12 = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val timeFormat24 = SimpleDateFormat("HH:mm", Locale.getDefault())

        try {
            val currentCal = Calendar.getInstance()
            currentCal.add(Calendar.DAY_OF_YEAR, -30)
            val endCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 365) }

            while (currentCal.before(endCal)) {
                val dayOfWeekInt = currentCal.get(Calendar.DAY_OF_WEEK)
                val dayOfWeekString = getDayString(dayOfWeekInt)

                if (event.dayOfWeek.equals(dayOfWeekString, ignoreCase = true)) {
                    val timeDate = try { timeFormat12.parse(event.time) } catch (e: Exception) { null } 
                                   ?: try { timeFormat24.parse(event.time) } catch (e: Exception) { null }
                    if (timeDate != null) {
                        val timeCal = Calendar.getInstance().apply { time = timeDate }
                        val alarmCal = Calendar.getInstance().apply {
                            timeInMillis = currentCal.timeInMillis
                            set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                            set(Calendar.SECOND, 0)
                            add(Calendar.MINUTE, -15)
                        }

                        val intent = Intent(context, NotificationReceiver::class.java).apply {
                            putExtra("COURSE_NAME", "Mess: ${event.mealType}")
                            putExtra("LOCATION", "Mess")
                        }
                        val requestCode = ("Mess${event.mealType}".hashCode() + alarmCal.timeInMillis).toInt()
                        
                        val pendingIntent = PendingIntent.getBroadcast(
                            context,
                            requestCode,
                            intent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        alarmManager.cancel(pendingIntent)
                        pendingIntent.cancel()
                    }
                }
                currentCal.add(Calendar.DAY_OF_YEAR, 1)
            }
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "Error canceling mess menu alarms", e)
        }
    }

    fun scheduleAlarmsForCustomEvents(context: Context, event: CustomEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val timeFormat12 = SimpleDateFormat("h:mm a", Locale.getDefault())
        val timeFormat24 = SimpleDateFormat("H:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        try {
            val startDate = dateFormat.parse(event.date) ?: return
            val endDate = event.repeatEndDate?.let { dateFormat.parse(it) } ?: Calendar.getInstance().apply { add(Calendar.MONTH, 6) }.time
            val timeDate = try { timeFormat12.parse(event.startTime) } catch (e: Exception) { null } 
                           ?: try { timeFormat24.parse(event.startTime) } catch (e: Exception) { null }
            
            if (timeDate != null) {
                val timeCal = Calendar.getInstance().apply { time = timeDate }
                val currentCal = Calendar.getInstance().apply { time = startDate }
                val endCal = Calendar.getInstance().apply { time = endDate }
                val cancelledSet = event.cancelledDates.split(",").filter { it.isNotBlank() }.toSet()

                // If no repeat, just run once
                if (event.repeatType.equals("NONE", ignoreCase = true)) {
                    endCal.time = currentCal.time
                }

                while (currentCal.before(endCal) || currentCal.timeInMillis == endCal.timeInMillis) {
                    val currentDateStr = dateFormat.format(currentCal.time)
                    if (!cancelledSet.contains(currentDateStr)) {
                        val eventStartCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, currentCal.get(Calendar.YEAR))
                            set(Calendar.MONTH, currentCal.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, currentCal.get(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                            set(Calendar.SECOND, 0)
                        }
                        
                        val alarmCal = Calendar.getInstance().apply {
                            timeInMillis = eventStartCal.timeInMillis
                            add(Calendar.MINUTE, -15)
                        }

                        var triggerTime = alarmCal.timeInMillis
                        val now = System.currentTimeMillis()

                        if (triggerTime <= now && eventStartCal.timeInMillis > now) {
                            triggerTime = now + 2000
                        }

                        if (triggerTime > now) {
                            val intent = Intent(context, NotificationReceiver::class.java).apply {
                                putExtra("COURSE_NAME", event.eventName)
                                putExtra("LOCATION", event.startTime)
                                putExtra("IS_CUSTOM", true)
                            }
                            val requestCode = ("Custom${event.eventName}$currentDateStr".hashCode() + alarmCal.timeInMillis).toInt()
                            val pendingIntent = PendingIntent.getBroadcast(
                                context,
                                requestCode,
                                intent,
                                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                            )

                            try {
                                alarmManager.setExactAndAllowWhileIdle(
                                    AlarmManager.RTC_WAKEUP,
                                    triggerTime,
                                    pendingIntent
                                )
                            } catch (e: SecurityException) {
                                Log.e("AlarmScheduler", "Exact alarm permission missing", e)
                            }
                        }
                    }

                    // Increment loop
                    when (event.repeatType.uppercase()) {
                        "NONE" -> break
                        "DAILY" -> currentCal.add(Calendar.DAY_OF_YEAR, 1)
                        "WEEKLY" -> currentCal.add(Calendar.DAY_OF_YEAR, 7)
                        "MONTHLY" -> currentCal.add(Calendar.MONTH, 1)
                        "YEARLY" -> currentCal.add(Calendar.YEAR, 1)
                        "CUSTOM_DAYS" -> currentCal.add(Calendar.DAY_OF_YEAR, event.repeatInterval.takeIf { it > 0 } ?: 1)
                        else -> break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "Error scheduling custom event alarm", e)
        }
    }

    fun cancelAlarmsForCustomEvents(context: Context, event: CustomEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val timeFormat12 = SimpleDateFormat("h:mm a", Locale.getDefault())
        val timeFormat24 = SimpleDateFormat("H:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        try {
            val startDate = dateFormat.parse(event.date) ?: return
            val endDate = event.repeatEndDate?.let { dateFormat.parse(it) } ?: Calendar.getInstance().apply { add(Calendar.MONTH, 6) }.time
            val timeDate = try { timeFormat12.parse(event.startTime) } catch (e: Exception) { null } 
                           ?: try { timeFormat24.parse(event.startTime) } catch (e: Exception) { null }
            
            if (timeDate != null) {
                val timeCal = Calendar.getInstance().apply { time = timeDate }
                val currentCal = Calendar.getInstance().apply { time = startDate }
                val endCal = Calendar.getInstance().apply { time = endDate }

                if (event.repeatType.equals("NONE", ignoreCase = true)) {
                    endCal.time = currentCal.time
                }

                while (currentCal.before(endCal) || currentCal.timeInMillis == endCal.timeInMillis) {
                    val currentDateStr = dateFormat.format(currentCal.time)
                    val eventStartCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, currentCal.get(Calendar.YEAR))
                        set(Calendar.MONTH, currentCal.get(Calendar.MONTH))
                        set(Calendar.DAY_OF_MONTH, currentCal.get(Calendar.DAY_OF_MONTH))
                        set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                        set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                        set(Calendar.SECOND, 0)
                    }
                    val alarmCal = Calendar.getInstance().apply {
                        timeInMillis = eventStartCal.timeInMillis
                        add(Calendar.MINUTE, -15)
                    }

                    val intent = Intent(context, NotificationReceiver::class.java)
                    val requestCode = ("Custom${event.eventName}$currentDateStr".hashCode() + alarmCal.timeInMillis).toInt()
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
                    )

                    if (pendingIntent != null) {
                        alarmManager.cancel(pendingIntent)
                    }

                    when (event.repeatType.uppercase()) {
                        "NONE" -> break
                        "DAILY" -> currentCal.add(Calendar.DAY_OF_YEAR, 1)
                        "WEEKLY" -> currentCal.add(Calendar.DAY_OF_YEAR, 7)
                        "MONTHLY" -> currentCal.add(Calendar.MONTH, 1)
                        "YEARLY" -> currentCal.add(Calendar.YEAR, 1)
                        "CUSTOM_DAYS" -> currentCal.add(Calendar.DAY_OF_YEAR, event.repeatInterval.takeIf { it > 0 } ?: 1)
                        else -> break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmScheduler", "Error canceling custom event alarm", e)
        }
    }

    private fun getDayString(day: Int): String {
        return when (day) {
            Calendar.SUNDAY -> "Sunday"
            Calendar.MONDAY -> "Monday"
            Calendar.TUESDAY -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY -> "Thursday"
            Calendar.FRIDAY -> "Friday"
            Calendar.SATURDAY -> "Saturday"
            else -> ""
        }
    }
}
