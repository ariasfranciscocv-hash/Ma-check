package com.example

import com.example.data.model.DailySalesEntry
import com.example.data.model.UserProfile
import com.example.data.model.buildBranchSalesSummaries
import com.example.utils.DateAndFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ProjectionMathTest {

    @Test
    fun `daysBetweenInclusive and dayIndexInPeriod clamp cleanly for calendar half-month cycles`() {
        val start = "2026-11-16"
        val end = "2026-11-30"
        val totalDays = DateAndFormatters.daysBetweenInclusive(start, end)
        assertEquals(15, totalDays)

        assertEquals(1, DateAndFormatters.dayIndexInPeriod(start, "2026-11-16", totalDays))
        assertEquals(9, DateAndFormatters.dayIndexInPeriod(start, "2026-11-24", totalDays))
        assertEquals(15, DateAndFormatters.dayIndexInPeriod(start, "2026-12-05", totalDays))
    }

    @Test
    fun `biweeklyRangeForDate always uses 1-15 and 16-last day of month`() {
        val nov10 = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 10, 10, 0, 0) }
        val nov26 = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 26, 10, 0, 0) }
        val dec20 = Calendar.getInstance().apply { set(2026, Calendar.DECEMBER, 20, 10, 0, 0) }
        val febNonLeap = Calendar.getInstance().apply { set(2026, Calendar.FEBRUARY, 20, 10, 0, 0) }
        val febLeap = Calendar.getInstance().apply { set(2024, Calendar.FEBRUARY, 20, 10, 0, 0) }

        assertEquals("2026-11-01" to "2026-11-15", DateAndFormatters.biweeklyRangeForDate(nov10))
        assertEquals(15, DateAndFormatters.daysBetweenInclusive("2026-11-01", "2026-11-15"))

        assertEquals("2026-11-16" to "2026-11-30", DateAndFormatters.biweeklyRangeForDate(nov26))
        assertEquals(15, DateAndFormatters.daysBetweenInclusive("2026-11-16", "2026-11-30"))

        assertEquals("2026-12-16" to "2026-12-31", DateAndFormatters.biweeklyRangeForDate(dec20))
        assertEquals(16, DateAndFormatters.daysBetweenInclusive("2026-12-16", "2026-12-31"))

        assertEquals("2026-02-16" to "2026-02-28", DateAndFormatters.biweeklyRangeForDate(febNonLeap))
        assertEquals(13, DateAndFormatters.daysBetweenInclusive("2026-02-16", "2026-02-28"))

        assertEquals("2024-02-16" to "2024-02-29", DateAndFormatters.biweeklyRangeForDate(febLeap))
        assertEquals(14, DateAndFormatters.daysBetweenInclusive("2024-02-16", "2024-02-29"))

        assertEquals("2026-11-16" to "2026-11-30", DateAndFormatters.nextBiweeklyRange(nov10))
        assertEquals("2026-12-01" to "2026-12-15", DateAndFormatters.nextBiweeklyRange(nov26))
    }

    @Test
    fun `buildBranchSalesSummaries consolidates all 6 branches and detects today closing`() {
        val users = listOf(
            UserProfile(userId = "u107", branchName = "Sucursal 107", displayName = "Sucursal 107"),
            UserProfile(userId = "u112", branchName = "Sucursal 112", displayName = "Sucursal 112")
        )
        val entries = listOf(
            DailySalesEntry(
                userId = "u107",
                userName = "Sucursal 107",
                dateString = "2026-11-26",
                globalSales = 15000.0,
                creditSales = 9000.0,
                newCustomers = 3L,
                appMaOperations = 4L,
                newCustomerRequests = 5L
            ),
            DailySalesEntry(
                userId = "u112",
                userName = "Sucursal 112",
                dateString = "2026-11-25",
                globalSales = 12000.0,
                creditSales = 7000.0,
                newCustomers = 2L,
                appMaOperations = 2L,
                newCustomerRequests = 3L
            )
        )

        val summaries = buildBranchSalesSummaries(users, entries, todayIso = "2026-11-26")
        assertEquals(6, summaries.size)
        val s107 = summaries.first { it.branchName == "Sucursal 107" }
        val s112 = summaries.first { it.branchName == "Sucursal 112" }
        val s87 = summaries.first { it.branchName == "Sucursal 87" }

        assertEquals(15000.0, s107.globalSales, 0.01)
        assertTrue(s107.hasTodayClosing)
        assertEquals(12000.0, s112.globalSales, 0.01)
        assertFalse(s112.hasTodayClosing)
        assertEquals(0.0, s87.globalSales, 0.01)
        assertFalse(s87.hasTodayClosing)
    }

    @Test
    fun `addDaysIso advances date accurately`() {
        assertEquals("2026-11-30", DateAndFormatters.addDaysIso("2026-11-16", 14))
        assertTrue(DateAndFormatters.formatUnits(42.0).contains("42"))
    }
}
