package io.github.kurella.toodledo.widget

import android.content.Context
import io.github.kurella.toodledo.widget.R
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
        fun isoWeek(date: LocalDate) =
            date.get(weekFields.weekBasedYear()) to date.get(weekFields.weekOfWeekBasedYear())

        val startWeek = isoWeek(start)
        if (startWeek == isoWeek(today)) return context.getString(R.string.section_this_week)
        if (startWeek == isoWeek(today.plusWeeks(1))) return context.getString(R.string.section_next_week)
        return context.getString(R.string.section_later)
    }
}
