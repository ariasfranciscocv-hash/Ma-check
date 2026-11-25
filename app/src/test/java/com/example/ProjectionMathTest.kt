package com.example

import com.example.utils.DateAndFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionMathTest {

    @Test
    fun `daysBetweenInclusive and dayIndexInPeriod clamp cleanly for 14-day biweekly cycle`() {
        val start = "2026-11-16"
        val end = "2026-11-29"
        val totalDays = DateAndFormatters.daysBetweenInclusive(start, end)
        assertEquals(14, totalDays)

        assertEquals(1, DateAndFormatters.dayIndexInPeriod(start, "2026-11-16", totalDays))
        assertEquals(9, DateAndFormatters.dayIndexInPeriod(start, "2026-11-24", totalDays))
        assertEquals(14, DateAndFormatters.dayIndexInPeriod(start, "2026-12-05", totalDays))
    }

    @Test
    fun `addDaysIso advances date accurately`() {
        assertEquals("2026-11-30", DateAndFormatters.addDaysIso("2026-11-16", 14))
        assertTrue(DateAndFormatters.formatUnits(42.0).contains("42"))
    }
}
