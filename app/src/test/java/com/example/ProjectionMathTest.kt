package com.example

import com.example.utils.DateAndFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionMathTest {

    @Test
    fun `first half of month is strictly days 1 to 15 across all months including DST`() {
        val (marStart, marEnd) = DateAndFormatters.biweeklyRangeForIso("2026-03-10")
        assertEquals("2026-03-01", marStart)
        assertEquals("2026-03-15", marEnd)
        assertEquals(15, DateAndFormatters.daysBetweenInclusive(marStart, marEnd))

        val (novStart, novEnd) = DateAndFormatters.biweeklyRangeForIso("2026-11-01")
        assertEquals("2026-11-01", novStart)
        assertEquals("2026-11-15", novEnd)
        assertEquals(15, DateAndFormatters.daysBetweenInclusive(novStart, novEnd))
    }

    @Test
    fun `second half of month adjusts strictly to 28, 29, 30, and 31 day months`() {
        // 28-day February (non-leap year 2026)
        val (feb28Start, feb28End) = DateAndFormatters.biweeklyRangeForIso("2026-02-20")
        assertEquals("2026-02-16", feb28Start)
        assertEquals("2026-02-28", feb28End)
        assertEquals(13, DateAndFormatters.daysBetweenInclusive(feb28Start, feb28End))

        // 29-day February (leap year 2024)
        val (feb29Start, feb29End) = DateAndFormatters.biweeklyRangeForIso("2024-02-16")
        assertEquals("2024-02-16", feb29Start)
        assertEquals("2024-02-29", feb29End)
        assertEquals(14, DateAndFormatters.daysBetweenInclusive(feb29Start, feb29End))

        // 30-day November
        val (novStart, novEnd) = DateAndFormatters.biweeklyRangeForIso("2026-11-24")
        assertEquals("2026-11-16", novStart)
        assertEquals("2026-11-30", novEnd)
        assertEquals(15, DateAndFormatters.daysBetweenInclusive(novStart, novEnd))

        // 31-day December
        val (decStart, decEnd) = DateAndFormatters.biweeklyRangeForIso("2026-12-31")
        assertEquals("2026-12-16", decStart)
        assertEquals("2026-12-31", decEnd)
        assertEquals(16, DateAndFormatters.daysBetweenInclusive(decStart, decEnd))
    }

    @Test
    fun `daysBetweenInclusive and dayIndexInPeriod clamp cleanly for calendar biweekly cycle`() {
        val start = "2026-11-16"
        val end = "2026-11-30"
        val totalDays = DateAndFormatters.daysBetweenInclusive(start, end)
        assertEquals(15, totalDays)

        assertEquals(1, DateAndFormatters.dayIndexInPeriod(start, "2026-11-16", totalDays))
        assertEquals(9, DateAndFormatters.dayIndexInPeriod(start, "2026-11-24", totalDays))
        assertEquals(15, DateAndFormatters.dayIndexInPeriod(start, "2026-12-05", totalDays))
    }

    @Test
    fun `addDaysIso advances date accurately across month boundaries`() {
        assertEquals("2026-11-30", DateAndFormatters.addDaysIso("2026-11-16", 14))
        assertEquals("2026-03-01", DateAndFormatters.addDaysIso("2026-02-28", 1))
        assertTrue(DateAndFormatters.formatUnits(42.0).contains("42"))
    }
}
