package com.amazon.campusflow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Insert
    fun insertAll(events: List<ScheduleEvent>)

    @Query("SELECT * FROM schedule_events ORDER BY dayOfWeek, startTime")
    fun getAllEventsFlow(): Flow<List<ScheduleEvent>>

    @Query("SELECT * FROM schedule_events")
    fun getAllEvents(): List<ScheduleEvent>

    @Query("SELECT * FROM schedule_events WHERE courseName = :courseName AND dayOfWeek = :dayOfWeek LIMIT 1")
    fun getEvent(courseName: String, dayOfWeek: String): ScheduleEvent?

    @Query("DELETE FROM schedule_events WHERE courseName = :courseName AND dayOfWeek = :dayOfWeek")
    fun deleteEvent(courseName: String, dayOfWeek: String)

    @Query("DELETE FROM schedule_events")
    fun deleteAll()
}
