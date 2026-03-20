package io.github.kurella.toodledo.widget

import java.time.LocalDate

data class Task(
    val id: Long,
    val title: String,
    val priority: Priority,
    val startDate: LocalDate?,
    val dueDate: LocalDate,
    val repeat: String,
    val hasNote: Boolean = false
) {
    val isRepeating: Boolean get() = repeat.isNotEmpty() && repeat != "None"

    fun category(today: LocalDate): Category = when {
        dueDate < today -> Category.OVERDUE
        startDate == null || startDate <= today -> Category.CURRENT
        else -> Category.FUTURE
    }

    fun sortDate(today: LocalDate): LocalDate = when (category(today)) {
        Category.OVERDUE, Category.CURRENT -> dueDate
        Category.FUTURE -> startDate ?: dueDate
    }
}

enum class Priority(val value: Int, val color: Int) {
    NEGATIVE(-1, 0xFF9E9E9E.toInt()),
    LOW(0, 0xFF64B5F6.toInt()),
    MEDIUM(1, 0xFF4CAF50.toInt()),
    HIGH(2, 0xFFFF9800.toInt()),
    TOP(3, 0xFFF44336.toInt());

    companion object {
        fun from(value: Int): Priority = entries.firstOrNull { it.value == value } ?: MEDIUM
    }
}

enum class Category { OVERDUE, CURRENT, FUTURE }
