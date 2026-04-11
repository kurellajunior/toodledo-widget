package io.github.kurella.toodledo.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

/**
 * Tests for RepeatCalculator.next().
 *
 * Reference: RFC 5545 §3.3.10 (RRULE) and Toodledo API v3 repeat extensions.
 *
 * Fixed anchor:
 *   dueDate = 2026-04-08 (Wednesday)
 *
 * The helper defaults today = 2000-01-01 so that pure RRULE arithmetic tests
 * are not affected by the advance-to-future rule. Tests that exercise that rule
 * (advancesToFuture, fromCompletion) pass today = 2026-04-10 explicitly.
 */
class RepeatCalculatorTest {

    /** Calls next() with no startDate; returns just the computed due date. */
    private fun nextDue(repeat: String, due: String, today: String = "2000-01-01"): LocalDate? =
        RepeatCalculator.next(repeat.trim(), null, LocalDate.parse(due), LocalDate.parse(today)).second

    private fun d(s: String) = LocalDate.parse(s)

    // ── Simple FREQ + INTERVAL (RFC 5545 §3.3.10) ───────────────────────────

    @ParameterizedTest(name = "{0}  due={1}  →  {2}")
    @CsvSource(
        // repeat,                          due,         expected
        "FREQ=DAILY,                         2026-04-08,  2026-04-09",
        "FREQ=DAILY;INTERVAL=3,              2026-04-08,  2026-04-11",
        "FREQ=WEEKLY,                        2026-04-08,  2026-04-15",
        "FREQ=WEEKLY;INTERVAL=2,             2026-04-08,  2026-04-22",
        "FREQ=WEEKLY;INTERVAL=11,            2026-04-08,  2026-06-24",
        "FREQ=MONTHLY,                       2026-04-08,  2026-05-08",
        "FREQ=MONTHLY;INTERVAL=2,            2026-04-08,  2026-06-08",
        "FREQ=MONTHLY;INTERVAL=3,            2026-04-08,  2026-07-08",
        "FREQ=MONTHLY;INTERVAL=6,            2026-04-08,  2026-10-08",
        "FREQ=YEARLY,                        2026-04-08,  2027-04-08",
        "FREQ=YEARLY;INTERVAL=2,             2026-04-08,  2028-04-08",
    )
    fun simpleInterval(repeat: String, due: String, expected: String) {
        assertEquals(d(expected), nextDue(repeat, due))
    }

    // ── BYMONTHDAY (RFC 5545 §3.3.10) ───────────────────────────────────────

    @ParameterizedTest(name = "{0}  due={1}  →  {2}")
    @CsvSource(
        // -1 = last day of month
        "FREQ=MONTHLY;BYMONTHDAY=-1,  2026-01-31,  2026-02-28",  // last of Jan → last of Feb
        "FREQ=MONTHLY;BYMONTHDAY=-1,  2026-02-28,  2026-03-31",  // last of Feb → last of Mar
        "FREQ=MONTHLY;BYMONTHDAY=-1,  2026-03-31,  2026-04-30",  // last of Mar → last of Apr
        // Specific day still in the same month
        "FREQ=MONTHLY;BYMONTHDAY=15,  2026-04-08,  2026-04-15",
        // Specific day already past → advance to next month
        "FREQ=MONTHLY;BYMONTHDAY=15,  2026-04-16,  2026-05-15",
        // RFC 5545: months that don't contain the day are SKIPPED (not clamped)
        "FREQ=MONTHLY;BYMONTHDAY=31,  2026-01-31,  2026-03-31",  // Feb has no 31st → skip to Mar
        "FREQ=MONTHLY;BYMONTHDAY=30,  2026-01-30,  2026-03-30",  // Feb has no 30th → skip to Mar
    )
    fun byMonthDay(repeat: String, due: String, expected: String) {
        assertEquals(d(expected), nextDue(repeat, due))
    }

    // ── BYDAY single weekday, FREQ=WEEKLY (RFC 5545 §3.3.10) ────────────────

    @ParameterizedTest(name = "{0}  due={1}  →  {2}")
    @CsvSource(
        // due=Monday 2026-04-06 → next Tue is same week
        "FREQ=WEEKLY;BYDAY=TU,  2026-04-06,  2026-04-07",
        // due=Tuesday → next Tue is the following week
        "FREQ=WEEKLY;BYDAY=TU,  2026-04-07,  2026-04-14",
        // due=Wednesday → next Wed is the following week
        "FREQ=WEEKLY;BYDAY=WE,  2026-04-08,  2026-04-15",
        // due=Wednesday → next Fri is same week
        "FREQ=WEEKLY;BYDAY=FR,  2026-04-08,  2026-04-10",
    )
    fun bydaySingleWeekday(repeat: String, due: String, expected: String) {
        assertEquals(d(expected), nextDue(repeat, due))
    }

    // ── BYDAY multiple weekdays, FREQ=WEEKLY ────────────────────────────────

    @ParameterizedTest(name = "{0}  due={1}  →  {2}")
    @CsvSource(
        // Every weekday (Mon–Fri): due=Wed → next is Thu
        "'FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR',  2026-04-08,  2026-04-09",
        // Every Mon/Wed/Fri: due=Mon → next is Wed
        "'FREQ=WEEKLY;BYDAY=MO,WE,FR',         2026-04-06,  2026-04-08",
        // Every Mon/Wed/Fri: due=Wed → next is Fri
        "'FREQ=WEEKLY;BYDAY=MO,WE,FR',         2026-04-08,  2026-04-10",
        // Every Mon/Wed/Fri: due=Fri → next is Mon
        "'FREQ=WEEKLY;BYDAY=MO,WE,FR',         2026-04-10,  2026-04-13",
        // Every weekend: due=Wed → next is Sat
        "'FREQ=WEEKLY;BYDAY=SA,SU',            2026-04-08,  2026-04-11",
        // Every weekend: due=Sat → next is Sun
        "'FREQ=WEEKLY;BYDAY=SA,SU',            2026-04-11,  2026-04-12",
    )
    fun bydayMultipleWeekdays(repeat: String, due: String, expected: String) {
        assertEquals(d(expected), nextDue(repeat, due))
    }

    // ── BYDAY with ordinal, FREQ=MONTHLY (RFC 5545 §3.3.10) ─────────────────

    @ParameterizedTest(name = "{0}  due={1}  →  {2}")
    @CsvSource(
        // 2nd Monday: Apr 2026 → 2nd Mon = Apr 13; next = May 11
        "FREQ=MONTHLY;BYDAY=2MO,   2026-04-13,  2026-05-11",
        // 1st Friday: May 1 → 1st Fri = May 1; due=May 1 itself → next = Jun 5
        "FREQ=MONTHLY;BYDAY=1FR,   2026-05-01,  2026-06-05",
        // Last Friday: Apr 24 → last Fri of Apr; next = May 29
        "FREQ=MONTHLY;BYDAY=-1FR,  2026-04-24,  2026-05-29",
        // Last Monday: May 25 → last Mon of May; next = Jun 29
        "FREQ=MONTHLY;BYDAY=-1MO,  2026-05-25,  2026-06-29",
    )
    fun bydayMonthlyOrdinal(repeat: String, due: String, expected: String) {
        assertEquals(d(expected), nextDue(repeat, due))
    }

    // ── FROMCOMP: base = today, not dueDate ──────────────────────────────────

    @ParameterizedTest(name = "{0}  due={1}  today={2}  →  {3}")
    @CsvSource(
        // Weekly from completion: today=Fri Apr 10 → next week = Fri Apr 17
        "FREQ=WEEKLY;FROMCOMP,              2026-03-01,  2026-04-10,  2026-04-17",
        // Every 11 weeks from completion
        "FREQ=WEEKLY;INTERVAL=11;FROMCOMP,  2026-03-01,  2026-04-10,  2026-06-26",
        // Monthly from completion: today=Apr 10 → May 10
        "FREQ=MONTHLY;FROMCOMP,             2026-03-01,  2026-04-10,  2026-05-10",
        // Yearly from completion
        "FREQ=YEARLY;FROMCOMP,              2026-01-01,  2026-04-10,  2027-04-10",
        // BYDAY + FROMCOMP: every Tue, base=today(Fri Apr 10) → next Tue = Apr 14
        "'FREQ=WEEKLY;BYDAY=TU;FROMCOMP',   2026-03-01,  2026-04-10,  2026-04-14",
    )
    fun fromCompletion(repeat: String, due: String, today: String, expected: String) {
        assertEquals(d(expected), nextDue(repeat.trim(), due, today))
    }

    // ── Advance to future: result always > today ─────────────────────────────
    //
    // The rule is applied repeatedly until the next occurrence is strictly after
    // today. This covers overdue tasks where the naive "next = due + interval"
    // would still be in the past.

    @ParameterizedTest(name = "{0}  due={1}  today={2}  →  {3}")
    @CsvSource(
        // Weekly, DueDate many months in the past: must skip to next future Mon (Sep 1 = Mon)
        "FREQ=WEEKLY,                        2025-09-01,  2026-04-10,  2026-04-13",
        // Daily, overdue by 2 days: must reach today+1 (Apr 11)
        "FREQ=DAILY,                         2026-04-08,  2026-04-10,  2026-04-11",
        // Monthly, last day, several months overdue: must reach Apr 30
        "FREQ=MONTHLY;BYMONTHDAY=-1,         2026-01-31,  2026-04-10,  2026-04-30",
        // Every Tue, due=Mon Apr 6: naive next = Apr 7 (past) → must reach Apr 14
        "FREQ=WEEKLY;BYDAY=TU,               2026-04-06,  2026-04-10,  2026-04-14",
    )
    fun advancesToFuture(repeat: String, due: String, today: String, expected: String) {
        assertEquals(d(expected), nextDue(repeat.trim(), due, today))
    }

    // ── startDate propagation and sanity check ───────────────────────────────

    @ParameterizedTest(name = "{0}  start={1}  due={2}  →  ({3}, {4})")
    @CsvSource(
        // 3-day lead time preserved: due Apr 8, start Apr 5 → next due Apr 15, next start Apr 12
        "FREQ=WEEKLY,  2026-04-05,  2026-04-08,  2026-04-12,  2026-04-15",
        // Same-day start (lead=0): due Apr 8, start Apr 8 → next start Apr 15 (= next due, valid → kept)
        "FREQ=WEEKLY,  2026-04-08,  2026-04-08,  2026-04-15,   2026-04-15",
        // start after due (invalid input): start Apr 10, due Apr 8 → next start would be after next due → dropped
        "FREQ=WEEKLY,  2026-04-10,  2026-04-08,  null,         2026-04-15",
        // Monthly with lead: due May 8, start May 1 (7-day lead) → next due Jun 8, next start Jun 1
        "FREQ=MONTHLY, 2026-05-01,  2026-05-08,  2026-06-01,   2026-06-08",
    )
    fun startDatePropagation(repeat: String, start: String, due: String, expStart: String, expDue: String) {
        val startDate = if (start == "null") null else d(start)
        val expected = (if (expStart == "null") null else d(expStart)) to d(expDue)
        assertEquals(expected, RepeatCalculator.next(repeat.trim(), startDate, d(due)))
    }

    // ── Unhandled / invalid → null ───────────────────────────────────────────

    @ParameterizedTest(name = "''{0}''  →  (null, null)")
    @ValueSource(strings = ["PARENT", "", "  ", "FREQ=BOGUS", "GARBAGE", "None"])
    fun returnsNullDue(repeat: String) {
        assertNull(RepeatCalculator.next(repeat, null, d("2026-04-08")).second)
    }
}
