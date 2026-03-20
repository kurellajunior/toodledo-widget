package com.kurella.toodledo.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TaskTest {

    private val today = LocalDate.of(2026, 3, 20)

    private fun task(
        dueDate: LocalDate = today,
        startDate: LocalDate? = null,
        priority: Priority = Priority.MEDIUM,
        repeat: String = ""
    ) = Task(1L, "Test", priority, startDate, dueDate, repeat)

    @Nested
    inner class CategoryTest {

        @Test
        fun `overdue when due date is before today`() {
            assertEquals(Category.OVERDUE, task(dueDate = today.minusDays(1)).category(today))
        }

        @Test
        fun `current when due today and no start date`() {
            assertEquals(Category.CURRENT, task(dueDate = today).category(today))
        }

        @Test
        fun `current when start date is today`() {
            val t = task(dueDate = today.plusDays(5), startDate = today)
            assertEquals(Category.CURRENT, t.category(today))
        }

        @Test
        fun `current when start date is in the past`() {
            val t = task(dueDate = today.plusDays(5), startDate = today.minusDays(2))
            assertEquals(Category.CURRENT, t.category(today))
        }

        @Test
        fun `future when start date is after today`() {
            val t = task(dueDate = today.plusDays(10), startDate = today.plusDays(3))
            assertEquals(Category.FUTURE, t.category(today))
        }

        @Test
        fun `future due date in future with no start date is current`() {
            // No startDate means startDate==null → null <= today is true → CURRENT
            val t = task(dueDate = today.plusDays(5), startDate = null)
            assertEquals(Category.CURRENT, t.category(today))
        }
    }

    @Nested
    inner class SortDateTest {

        @Test
        fun `overdue tasks sort by due date`() {
            val t = task(dueDate = today.minusDays(3), startDate = today.minusDays(10))
            assertEquals(today.minusDays(3), t.sortDate(today))
        }

        @Test
        fun `current tasks sort by due date`() {
            val t = task(dueDate = today.plusDays(2), startDate = today.minusDays(1))
            assertEquals(today.plusDays(2), t.sortDate(today))
        }

        @Test
        fun `future tasks sort by start date`() {
            val t = task(dueDate = today.plusDays(10), startDate = today.plusDays(3))
            assertEquals(today.plusDays(3), t.sortDate(today))
        }

        @Test
        fun `future tasks without start date sort by due date`() {
            // This case shouldn't happen (no startDate = CURRENT), but tests the fallback
            val t = task(dueDate = today.plusDays(10), startDate = today.plusDays(5))
            assertEquals(today.plusDays(5), t.sortDate(today))
        }
    }

    @Nested
    inner class PriorityTest {

        @Test
        fun `from maps known values`() {
            assertEquals(Priority.NEGATIVE, Priority.from(-1))
            assertEquals(Priority.LOW, Priority.from(0))
            assertEquals(Priority.MEDIUM, Priority.from(1))
            assertEquals(Priority.HIGH, Priority.from(2))
            assertEquals(Priority.TOP, Priority.from(3))
        }

        @Test
        fun `from defaults to MEDIUM for unknown values`() {
            assertEquals(Priority.MEDIUM, Priority.from(99))
            assertEquals(Priority.MEDIUM, Priority.from(-5))
        }

        @Test
        fun `each priority has a distinct color`() {
            val colors = Priority.entries.map { it.color }.toSet()
            assertEquals(Priority.entries.size, colors.size)
        }
    }

    @Nested
    inner class HasNoteTest {

        @Test
        fun `hasNote defaults to false`() {
            assertFalse(task().hasNote)
        }

        @Test
        fun `hasNote true when set`() {
            assertTrue(task().copy(hasNote = true).hasNote)
        }
    }

    @Nested
    inner class RepeatingTest {

        @Test
        fun `empty repeat is not repeating`() {
            assertFalse(task(repeat = "").isRepeating)
        }

        @Test
        fun `None repeat is not repeating`() {
            assertFalse(task(repeat = "None").isRepeating)
        }

        @Test
        fun `non-empty repeat is repeating`() {
            assertTrue(task(repeat = "Weekly").isRepeating)
        }
    }
}
