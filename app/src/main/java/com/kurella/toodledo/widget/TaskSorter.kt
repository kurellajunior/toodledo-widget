package com.kurella.toodledo.widget

import android.content.Context
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

sealed class ListItem {
    data class SectionHeader(val title: String) : ListItem()
    data class TaskItem(val task: Task) : ListItem()
    data object RefreshItem : ListItem()
}

object TaskSorter {

    fun buildList(tasks: List<Task>, context: Context): List<ListItem> {
        val today = LocalDate.now()
        val sorted = tasks.sortedWith(
            compareBy<Task> { it.category(today).ordinal }
                .thenBy { it.sortDate(today) }
                .thenByDescending { it.priority.value }
                .thenBy { it.title.lowercase() }
        )

        val items = mutableListOf<ListItem>()
        var currentSection: String? = null

        for (task in sorted) {
            val section = sectionLabel(task, today, context)
            if (section != currentSection) {
                currentSection = section
                // Only show headers for future sections, not Overdue/Today
                val cat = task.category(today)
                if (cat == Category.FUTURE) {
                    items += ListItem.SectionHeader(section)
                }
            }
            items += ListItem.TaskItem(task)
        }
        return items
    }

    private fun sectionLabel(task: Task, today: LocalDate, context: Context): String {
        val cat = task.category(today)
        if (cat == Category.OVERDUE) return context.getString(R.string.section_overdue)
        if (cat == Category.CURRENT) return context.getString(R.string.section_today)

        val start = task.startDate ?: task.dueDate
        val tomorrow = today.plusDays(1)
        if (start == tomorrow) return context.getString(R.string.section_tomorrow)

        val weekFields = WeekFields.of(Locale.getDefault())
        val thisWeek = today.get(weekFields.weekOfYear())
        val startWeek = start.get(weekFields.weekOfYear())
        val thisYear = today.year
        val startYear = start.year

        if (startYear == thisYear && startWeek == thisWeek) {
            return context.getString(R.string.section_this_week)
        }
        if (startYear == thisYear && startWeek == thisWeek + 1 ||
            startYear == thisYear + 1 && thisWeek >= 52 && startWeek == 1) {
            return context.getString(R.string.section_next_week)
        }
        return context.getString(R.string.section_later)
    }
}
