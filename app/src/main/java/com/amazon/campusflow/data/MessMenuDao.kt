package com.amazon.campusflow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MessMenuDao {
    @Insert
    fun insertAll(events: List<MessMenuEvent>)

    @Query("SELECT * FROM mess_menu_events ORDER BY dayOfWeek, time")
    fun getAllEvents(): List<MessMenuEvent>

    @Query("SELECT * FROM mess_menu_events WHERE mealType = :mealType AND dayOfWeek = :dayOfWeek LIMIT 1")
    fun getEvent(mealType: String, dayOfWeek: String): MessMenuEvent?

    @Query("DELETE FROM mess_menu_events WHERE mealType = :mealType AND dayOfWeek = :dayOfWeek")
    fun deleteEvent(mealType: String, dayOfWeek: String)

    @Query("DELETE FROM mess_menu_events")
    fun deleteAll()
}
