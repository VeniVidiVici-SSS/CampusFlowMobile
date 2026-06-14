package com.amazon.campusflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedule_events")
data class ScheduleEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val courseName: String,
    val dayOfWeek: String, // e.g. "Monday"
    val startTime: String, // e.g. "10:00 AM"
    val location: String,
    val startDateMillis: Long, // to restrict the weekly repetition
    val endDateMillis: Long,
    val endTime: String = ""
)
