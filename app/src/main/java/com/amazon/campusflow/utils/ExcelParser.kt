package com.amazon.campusflow.utils

import android.content.Context
import android.net.Uri
import com.amazon.campusflow.data.ScheduleEvent
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.DataFormatter
import java.io.InputStream

object ExcelParser {
    fun parseSchedule(context: Context, uri: Uri): List<ScheduleEvent> {
        val events = mutableListOf<ScheduleEvent>()
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val workbook = WorkbookFactory.create(stream)
                val sheet = workbook.getSheetAt(0)
                
                val formatter = DataFormatter()
                
                // Assuming first row is header, skip it.
                var isFirstRow = true
                for (row in sheet) {
                    if (isFirstRow) {
                        isFirstRow = false
                        continue
                    }
                    
                    // Column 0: Course name
                    // Column 1: Day of the week
                    // Column 2: Start Time
                    // Column 3: Location
                    val courseName = formatter.formatCellValue(row.getCell(0))?.trim() ?: ""
                    val dayOfWeek = formatter.formatCellValue(row.getCell(1))?.trim() ?: ""
                    val startTime = formatter.formatCellValue(row.getCell(2))?.trim() ?: ""
                    val location = formatter.formatCellValue(row.getCell(3))?.trim() ?: ""
                    
                    if (courseName.isNotEmpty() && dayOfWeek.isNotEmpty() && startTime.isNotEmpty()) {
                        events.add(
                            ScheduleEvent(
                                courseName = courseName,
                                dayOfWeek = dayOfWeek,
                                startTime = startTime,
                                location = location,
                                startDateMillis = 0L, // To be filled later
                                endDateMillis = 0L    // To be filled later
                            )
                        )
                    }
                }
                workbook.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return events
    }
}
