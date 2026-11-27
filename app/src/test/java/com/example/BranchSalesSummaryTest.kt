package com.example

import com.example.data.model.BiweeklyQuota
import com.example.data.model.BranchOptions
import com.example.data.model.DailySalesEntry
import com.example.data.model.UserProfile
import com.example.data.model.buildBranchSalesSummaries
import com.example.data.model.resolveQuotaForBranch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BranchSalesSummaryTest {

    private val generalQuota = BiweeklyQuota(
        id = "quota_2026-11_Q2_todas_las_sucursales",
        title = "2ª Quincena · Noviembre 2026",
        startDate = "2026-11-16",
        endDate = "2026-11-30",
        totalDays = 15L,
        periodId = "2026-11_Q2",
        branchName = BranchOptions.ALL_BRANCHES_OPTION,
        targetGlobalSales = 200_000.0,
        targetCreditSales = 100_000.0,
        active = true
    )

    private val branch87Quota = BiweeklyQuota(
        id = "quota_2026-11_Q2_sucursal_87",
        title = "2ª Quincena · Noviembre 2026",
        startDate = "2026-11-16",
        endDate = "2026-11-30",
        totalDays = 15L,
        periodId = "2026-11_Q2",
        branchName = "Sucursal 87",
        targetGlobalSales = 100_000.0,
        targetCreditSales = 60_000.0,
        active = true
    )

    @Test
    fun `resolveQuotaForBranch prefers exact branch quota and falls back to general quota`() {
        val quotas = listOf(generalQuota, branch87Quota)

        val (q87, isCustom87) = resolveQuotaForBranch("Sucursal 87", quotas, "2026-11_Q2")
        assertEquals("quota_2026-11_Q2_sucursal_87", q87?.id)
        assertTrue(isCustom87)

        val (q107, isCustom107) = resolveQuotaForBranch("Sucursal 107", quotas, "2026-11_Q2")
        assertEquals("quota_2026-11_Q2_todas_las_sucursales", q107?.id)
        assertFalse(isCustom107)
    }

    @Test
    fun `buildBranchSalesSummaries separates today closing and sums sorted quincena history`() {
        val users = listOf(
            UserProfile(userId = "u87", displayName = "Ana 87", branchName = "Sucursal 87"),
            UserProfile(userId = "u107", displayName = "Luis 107", branchName = "Sucursal 107")
        )
        val entries = listOf(
            DailySalesEntry(
                id = "e1",
                userId = "u87",
                userName = "Ana 87",
                branchName = "Sucursal 87",
                quotaId = generalQuota.id,
                dateString = "2026-11-18",
                globalSales = 30_000.0,
                creditSales = 18_000.0,
                newCustomers = 4L,
                appMaOperations = 6L,
                newCustomerRequests = 8L,
                notes = "Buen cierre"
            ),
            DailySalesEntry(
                id = "e2",
                userId = "u87",
                userName = "Carlos 87",
                branchName = "Sucursal 87",
                quotaId = branch87Quota.id,
                dateString = "2026-11-26",
                globalSales = 45_000.0,
                creditSales = 25_000.0,
                newCustomers = 5L,
                appMaOperations = 9L,
                newCustomerRequests = 11L,
                notes = "Cierre de hoy"
            ),
            DailySalesEntry(
                id = "e_old",
                userId = "u87",
                userName = "Ana 87",
                branchName = "Sucursal 87",
                quotaId = branch87Quota.id,
                dateString = "2026-11-15",
                globalSales = 99_000.0
            ),
            DailySalesEntry(
                id = "e107",
                userId = "u107",
                userName = "Luis 107",
                quotaId = generalQuota.id,
                dateString = "2026-11-20",
                globalSales = 50_000.0
            )
        )

        val summaries = buildBranchSalesSummaries(
            branches = listOf("Sucursal 87", "Sucursal 107"),
            quotas = listOf(generalQuota, branch87Quota),
            entries = entries,
            teamUsers = users,
            todayIso = "2026-11-26",
            currentPeriodId = "2026-11_Q2"
        )

        assertEquals(2, summaries.size)

        val sum87 = summaries[0]
        assertEquals("Sucursal 87", sum87.branchName)
        assertTrue(sum87.hasCustomBranchQuota)
        assertNotNull(sum87.todayEntry)
        assertEquals("e2", sum87.todayEntry?.id)
        assertEquals(listOf("2026-11-26", "2026-11-18"), sum87.periodEntries.map { it.dateString })
        assertEquals(75_000.0, sum87.totalGlobalSales, 0.01)
        assertEquals(43_000.0, sum87.totalCreditSales, 0.01)
        assertEquals(75, sum87.completionPercent)
        assertEquals(0.75f, sum87.progressRatio, 0.001f)

        val sum107 = summaries[1]
        assertEquals("Sucursal 107", sum107.branchName)
        assertFalse(sum107.hasCustomBranchQuota)
        assertNull(sum107.todayEntry)
        assertEquals(1, sum107.periodEntries.size)
        assertEquals(50_000.0, sum107.totalGlobalSales, 0.01)
        assertEquals(25, sum107.completionPercent)
    }
}
