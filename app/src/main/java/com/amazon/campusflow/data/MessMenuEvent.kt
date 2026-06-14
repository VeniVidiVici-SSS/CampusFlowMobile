package com.amazon.campusflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mess_menu_events")
data class MessMenuEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mealType: String,
    val dayOfWeek: String,
    val time: String,
    val menuItems: String,
    val startDateMillis: Long,
    val endTime: String = ""
)
