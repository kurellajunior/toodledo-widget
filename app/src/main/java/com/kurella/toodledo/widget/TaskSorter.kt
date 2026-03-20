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

    fun buildList(
        tasks: List<Task>,
        context: Context,
        today: LocalDate = LocalDate.now()
    ): List<ListItem> {
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
                if (task.category(today) == Category.FUTURE) {
                    items += ListItem.SectionHeader(section)
                }
            }
            items += ListItem.TaskItem(task)
        }
        return items
    }

    private fun sectionLabel(task: Task, today: LocalDate, context: Context): String {
        val category = task.category(today)
        if (category == Category.OVERDUE) return context.getString(R.string.section_overdue)
        if (category == Category.CURRENT) return context.getString(R.string.section_today)

        val start = task.startDate ?: task.dueDate
        if (start == today.plusDays(1)) return context.getString(R.string.section_tomorrow)

        val weekFields = WeekFields.of(Locale.getDefault())
        val todayWeek = today.get(weekFields.weekOfWeekBasedYear())
        val todayYear = today.get(weekFields.weekBasedYear())
        val startWeek = start.get(weekFields.weekOfWeekBasedYear())
        val startYear = start.get(weekFields.weekBasedYear())

        if (startYear == todayYear && startWeek == todayWeek) {
            return context.getString(R.string.section_this_week)
        }
        // Next week: either same year week+1, or year boundary (week 52/53 → week 1)
        val nextWeek = today.plusWeeks(1)
        val nextWeekNum = nextWeek.get(weekFields.weekOfWeekBasedYear())
        val nextWeekYear = nextWeek.get(weekFields.weekBasedYear())
        if (startYear == nextWeekYear && startWeek == nextWeekNum) {
            return context.getString(R.string.section_next_week)
        }
        return context.getString(R.string.section_later)
    }
}
