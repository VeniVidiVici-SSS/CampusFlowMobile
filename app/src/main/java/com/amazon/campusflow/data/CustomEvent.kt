package com.amazon.campusflow.data

data class CustomEvent(
    val eventName: String,
    val date: String,      // format: YYYY-MM-DD
    val startTime: String, // format: hh:mm a
    val endTime: String    // format: hh:mm a
)
