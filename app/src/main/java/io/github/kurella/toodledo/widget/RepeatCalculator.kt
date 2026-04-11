package io.github.kurella.toodledo.widget

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Calculates the next due date for a Toodledo repeating task.
 *
 * Toodledo uses a subset of iCal RRULE (RFC 5545) with two custom extensions:
 *   FROMCOMP – reschedule from completion date instead of due date
 *   PARENT   – subtask inherits repeat from its parent; no local calculation possible
 *
 * Supported RRULE components: FREQ, INTERVAL, BYDAY, BYMONTHDAY.
 *
 * The result always lies strictly after `today` — the rule is applied in a loop
 * until a future date is reached.
 */
internal object RepeatCalculator {

    fun nextDate(repeat: String, dueDate: LocalDate, today: LocalDate = LocalDate.now()): LocalDate? {
        if (repeat.isBlank() || repeat == "PARENT") return null

        val params = repeat.split(";").associateBy(
            { if ('=' in it) it.substringBefore('=') else it },
            { if ('=' in it) it.substringAfter('=') else "" }
        )

        val freq = params["FREQ"] ?: return null
        val interval = params["INTERVAL"]?.toLongOrNull() ?: 1L
        val byDay = params["BYDAY"]
        val byMonthDay = params["BYMONTHDAY"]?.toIntOrNull()
        val fromComp = "FROMCOMP" in params
        val base = if (fromComp) today else dueDate

        var current = base
        do {
            current = when {
                byMonthDay != null -> nextByMonthDay(current, byMonthDay)
                byDay != null -> nextByDay(current, freq, interval, byDay) ?: return null
                else -> nextByInterval(current, freq, interval) ?: return null
            }
        } while (!current.isAfter(today))

        return current
    }

    // RFC 5545 §3.3.10: BYMONTHDAY with negative values counts from end of month.
    // Months that don't contain the requested day-of-month are skipped entirely.
    private fun nextByMonthDay(base: LocalDate, byMonthDay: Int): LocalDate {
        fun dayIn(m: LocalDate): LocalDate? = when {
            byMonthDay == -1 -> m.withDayOfMonth(m.lengthOfMonth())
            byMonthDay < -1 -> {
                val day = m.lengthOfMonth() + byMonthDay + 1
                if (day >= 1) m.withDayOfMonth(day) else null
            }
            byMonthDay in 1..m.lengthOfMonth() -> m.withDayOfMonth(byMonthDay)
            else -> null  // day doesn't exist in this month – RFC 5545: skip
        }

        val sameMonth = dayIn(base)
        if (sameMonth != null && sameMonth.isAfter(base)) return sameMonth

        var month = base.plusMonths(1).withDayOfMonth(1)
        repeat(12) {
            val candidate = dayIn(month)
            if (candidate != null) return candidate
            month = month.plusMonths(1)
        }
        return base.plusYears(1)  // unreachable for any valid byMonthDay
    }

    // RFC 5545 §3.3.10 BYDAY:
    //   FREQ=WEEKLY  → next occurrence of any listed weekday after base
    //   FREQ=MONTHLY → Nth weekday of month (ordinal prefix like "2MO", "-1FR")
    private fun nextByDay(base: LocalDate, freq: String, interval: Long, byDay: String): LocalDate? {
        val dayParts = byDay.split(",")
        return when (freq) {
            "WEEKLY" -> {
                val targets = dayParts.mapNotNull { parseDayOfWeek(it.trim()) }.toSet()
                if (targets.isEmpty()) return null
                findNextMatchingDay(base.plusDays(1), targets)
            }
            "MONTHLY" -> {
                val m = Regex("(-?\\d+)(\\w{2})").matchEntire(dayParts.first().trim())
                    ?: return null
                val ordinal = m.groupValues[1].toIntOrNull() ?: return null
                val dow = parseDayOfWeek(m.groupValues[2]) ?: return null
                val month = base.plusMonths(interval).withDayOfMonth(1)
                nthDayOfWeekInMonth(month, ordinal, dow)
            }
            else -> null
        }
    }

    private fun nextByInterval(base: LocalDate, freq: String, interval: Long): LocalDate? =
        when (freq) {
            "DAILY" -> base.plusDays(interval)
            "WEEKLY" -> base.plusWeeks(interval)
            "MONTHLY" -> base.plusMonths(interval)
            "YEARLY" -> base.plusYears(interval)
            else -> null
        }

    private fun findNextMatchingDay(start: LocalDate, days: Set<DayOfWeek>): LocalDate {
        var d = start
        while (d.dayOfWeek !in days) d = d.plusDays(1)
        return d
    }

    private fun parseDayOfWeek(s: String): DayOfWeek? = when (s.uppercase().take(2)) {
        "MO" -> DayOfWeek.MONDAY
        "TU" -> DayOfWeek.TUESDAY
        "WE" -> DayOfWeek.WEDNESDAY
        "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY
        "SA" -> DayOfWeek.SATURDAY
        "SU" -> DayOfWeek.SUNDAY
        else -> null
    }

    // RFC 5545 §3.3.10: positive n = nth occurrence from start, negative = from end of month.
    // n=1 → first, n=-1 → last, n=2 → second, n=-2 → second-to-last.
    private fun nthDayOfWeekInMonth(monthStart: LocalDate, n: Int, dow: DayOfWeek): LocalDate {
        return if (n > 0) {
            var d = monthStart
            while (d.dayOfWeek != dow) d = d.plusDays(1)
            d.plusWeeks((n - 1).toLong())
        } else {
            var d = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
            while (d.dayOfWeek != dow) d = d.minusDays(1)
            d.minusWeeks((-n - 1).toLong())
        }
    }
}
