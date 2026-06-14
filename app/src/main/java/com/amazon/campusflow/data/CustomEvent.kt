package com.amazon.campusflow.data

data class CustomEvent(
    val eventName: String,
    val date: String,      // format: YYYY-MM-DD
    val startTime: String, // format: h:mm a or H:mm
    val endTime: String,    // format: h:mm a or H:mm
    val repeatType: String = "NONE", // NONE, DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM_DAYS
    val repeatInterval: Int = 1,
    val repeatEndDate: String? = null,
    val cancelledDates: String = "" // comma separated dates to skip e.g. "2026-06-15,2026-06-16"
)
