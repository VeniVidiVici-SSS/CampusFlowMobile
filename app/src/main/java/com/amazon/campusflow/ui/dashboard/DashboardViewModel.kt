package com.amazon.campusflow.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.amazon.campusflow.data.AwsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class UnifiedEvent(
    val title: String,
    val timeStr: String,
    val type: String, // "Class", "Mess", "Custom"
    val startMs: Long
)

class DashboardViewModel(private val awsService: AwsService) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _eventsForSelectedDate = MutableStateFlow<List<UnifiedEvent>>(emptyList())
    val eventsForSelectedDate: StateFlow<List<UnifiedEvent>> = _eventsForSelectedDate.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var cachedClasses = listOf<com.amazon.campusflow.data.ScheduleEvent>()
    private var cachedMess = listOf<com.amazon.campusflow.data.MessMenuEvent>()
    private var cachedCustom = listOf<com.amazon.campusflow.data.CustomEvent>()

    init {
        refreshEvents()
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        filterEventsForDate(date)
    }

    fun refreshEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                cachedClasses = awsService.getAllClasses()
                cachedMess = awsService.getAllMessMenus()
                cachedCustom = awsService.getAllCustomEvents()
                filterEventsForDate(_selectedDate.value)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun filterEventsForDate(date: LocalDate) {
        val dayOfWeekStr = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val dateStr = date.toString() // yyyy-MM-dd

        val classes = cachedClasses.filter { it.dayOfWeek.equals(dayOfWeekStr, ignoreCase = true) }
        val mess = cachedMess.filter { it.dayOfWeek.equals(dayOfWeekStr, ignoreCase = true) }
        val custom = cachedCustom.filter { it.date == dateStr }

        val unified = mutableListOf<UnifiedEvent>()

        classes.forEach {
            unified.add(UnifiedEvent(it.courseName, "${it.startTime} to ${if(it.endTime.isNotBlank()) it.endTime else "???"} • ${it.location}", "Class", parseTimeToMillis(it.startTime)))
        }
        mess.forEach {
            unified.add(UnifiedEvent(it.mealType, "${it.time} to ${if(it.endTime.isNotBlank()) it.endTime else "???"} • ${it.menuItems}", "Mess", parseTimeToMillis(it.time)))
        }
        custom.forEach {
            unified.add(UnifiedEvent(it.eventName, "${it.startTime} to ${it.endTime}", "Custom", parseTimeToMillis(it.startTime)))
        }

        unified.sortBy { it.startMs }
        _eventsForSelectedDate.value = unified
    }

    private fun parseTimeToMillis(timeStr: String): Long {
        return try {
            val format12 = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
            val format24 = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val date = try {
                format12.parse(timeStr.trim().uppercase())
            } catch (e: Exception) {
                format24.parse(timeStr.trim())
            }
            date?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}

class DashboardViewModelFactory(
    private val awsService: AwsService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(awsService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
