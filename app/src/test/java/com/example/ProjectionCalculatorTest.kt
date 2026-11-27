package com.example

import com.example.data.model.BiweeklyQuota
import com.example.data.model.BranchOptions
import com.example.data.model.DailySalesEntry
import com.example.data.model.ProjectionPace
import com.example.data.model.SalesMetricType
import com.example.data.model.UserProfile
import com.example.data.model.buildBranchSalesSummaries
import com.example.data.repository.ProjectionCalculator
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionCalculatorTest {

    private val generalQuota = BiweeklyQuota(
        id = "quota_2026-11_Q2_todas_las_sucursales",
        title = "2ª Quincena · Noviembre 2026",
        startDate = "2026-11-16",
        endDate = "2026-11-30",
        totalDays = 15L,
        periodId = "2026-11_Q2",
        branchName = BranchOptions.ALL_BRANCHES_OPTION,
        targetGlobalSales = 900_000.0,
        targetCreditSales = 500_000.0,
        targetNewCustomers = 120L,
        targetAppMaOperations = 180L,
        targetNewCustomerRequests = 210L,
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
        targetNewCustomers = 15L,
        targetAppMaOperations = 20L,
        targetNewCustomerRequests = 25L,
        active = true
    )

    private val branch107Quota = BiweeklyQuota(
        id = "quota_2026-11_Q2_sucursal_107",
        title = "2ª Quincena · Noviembre 2026",
        startDate = "2026-11-16",
        endDate = "2026-11-30",
        totalDays = 15L,
        periodId = "2026-11_Q2",
        branchName = "Sucursal 107",
        targetGlobalSales = 140_000.0,
        targetCreditSales = 80_000.0,
        targetNewCustomers = 20L,
        targetAppMaOperations = 30L,
        targetNewCustomerRequests = 35L,
        active = true
    )

    @Test
    fun `resolveIndependentGeneralQuota never returns single branch quota or sums branches`() {
        val branchOnlyQuotas = listOf(branch87Quota, branch107Quota)
        assertNull(
            ProjectionCalculator.resolveIndependentGeneralQuota(
                quotas = branchOnlyQuotas,
                currentPeriodId = "2026-11_Q2"
            )
        )

        val resolved = ProjectionCalculator.resolveIndependentGeneralQuota(
            quotas = listOf(branch87Quota, generalQuota, branch107Quota),
            currentPeriodId = "2026-11_Q2"
        )
        assertEquals(generalQuota.id, resolved?.id)
        assertEquals(900_000.0, resolved?.targetGlobalSales ?: 0.0, 0.01)
    }

    @Test
    fun `computeAdminGeneralIndicatorsSummary keeps independent targets and sums all branches without double counting`() {
        val users = listOf(
            UserProfile(userId = "u87", displayName = "Sucursal 87", branchName = "Sucursal 87"),
            UserProfile(userId = "u107", displayName = "Sucursal 107", branchName = "Sucursal 107")
        )
        val entries = listOf(
            DailySalesEntry(
                id = "e87_older",
                userId = "u87",
                userName = "Sucursal 87",
                branchName = "Sucursal 87",
                quotaId = branch87Quota.id,
                dateString = "2026-11-26",
                globalSales = 10_000.0,
                creditSales = 5_000.0,
                newCustomers = 1L,
                appMaOperations = 2L,
                newCustomerRequests = 3L,
                updatedAt = Timestamp(1000L, 0)
            ),
            DailySalesEntry(
                id = "e87_latest",
                userId = "u87",
                userName = "Sucursal 87",
                branchName = "Sucursal 87",
                quotaId = branch87Quota.id,
                dateString = "2026-11-26",
                globalSales = 35_000.0,
                creditSales = 20_000.0,
                newCustomers = 5L,
                appMaOperations = 8L,
                newCustomerRequests = 9L,
                updatedAt = Timestamp(2000L, 0)
            ),
            DailySalesEntry(
                id = "e107_day1",
                userId = "u107",
                userName = "Sucursal 107",
                branchName = "Sucursal 107",
                quotaId = branch107Quota.id,
                dateString = "2026-11-20",
                globalSales = 45_000.0,
                creditSales = 25_000.0,
                newCustomers = 6L,
                appMaOperations = 10L,
                newCustomerRequests = 12L
            ),
            // Outside 2ª quincena de noviembre (must be excluded)
            DailySalesEntry(
                id = "e107_prev_q",
                userId = "u107",
                userName = "Sucursal 107",
                branchName = "Sucursal 107",
                quotaId = branch107Quota.id,
                dateString = "2026-11-15",
                globalSales = 99_000.0,
                creditSales = 99_000.0
            )
        )

        val summary = ProjectionCalculator.computeAdminGeneralIndicatorsSummary(
            quotas = listOf(generalQuota, branch87Quota, branch107Quota),
            allBranchEntries = entries,
            users = users,
            uid = "admin_uid",
            todayIso = "2026-11-26"
        )

        // Independent general targets (NOT 100k + 140k = 240k)
        val global = summary.metrics.first { it.metric == SalesMetricType.GLOBAL_SALES }
        val credit = summary.metrics.first { it.metric == SalesMetricType.CREDIT_SALES }
        val customers = summary.metrics.first { it.metric == SalesMetricType.NEW_CUSTOMERS }
        val appMa = summary.metrics.first { it.metric == SalesMetricType.APP_MA_OPS }
        val requests = summary.metrics.first { it.metric == SalesMetricType.NEW_CUSTOMER_REQUESTS }

        assertEquals(900_000.0, global.targetTotal, 0.01)
        assertEquals(500_000.0, credit.targetTotal, 0.01)
        assertEquals(120.0, customers.targetTotal, 0.01)
        assertEquals(180.0, appMa.targetTotal, 0.01)
        assertEquals(210.0, requests.targetTotal, 0.01)

        // Real actuals = sum across all branches (deduped for Sucursal 87 on 2026-11-26)
        assertEquals(80_000.0, global.actualTotal, 0.01)
        assertEquals(45_000.0, credit.actualTotal, 0.01)
        assertEquals(11.0, customers.actualTotal, 0.01)
        assertEquals(18.0, appMa.actualTotal, 0.01)
        assertEquals(21.0, requests.actualTotal, 0.01)
        assertEquals(2, summary.entriesCount)
    }

    @Test
    fun `buildBranchSalesSummaries classifies branch pace as ON_TRACK, MODERATE_RISK, and BEHIND using biweekly cycle`() {
        // 2026-11-20 is day 5 of 15 in 2026-11_Q2 (Nov 16..30), remaining = 10 days
        val q87 = branch87Quota.copy(targetGlobalSales = 150_000.0) // daily target = 10,000/day
        val q107 = branch107Quota.copy(targetGlobalSales = 150_000.0)
        val q109 = BiweeklyQuota(
            id = "q109",
            startDate = "2026-11-16",
            endDate = "2026-11-30",
            totalDays = 15L,
            periodId = "2026-11_Q2",
            branchName = "Sucursal 109",
            targetGlobalSales = 150_000.0,
            active = true
        )
        val users = listOf(
            UserProfile(userId = "u87", branchName = "Sucursal 87"),
            UserProfile(userId = "u107", branchName = "Sucursal 107"),
            UserProfile(userId = "u109", branchName = "Sucursal 109")
        )
        val entries = listOf(
            // Sucursal 87: 55,000 in 5 days -> 11,000/day -> proj 165,000 >= 150,000 -> ON_TRACK (Verde)
            DailySalesEntry(id = "e87", userId = "u87", branchName = "Sucursal 87", dateString = "2026-11-20", globalSales = 55_000.0),
            // Sucursal 107: 45,000 in 5 days -> 9,000/day -> proj 135,000 (90%) -> MODERATE_RISK (Ámbar)
            DailySalesEntry(id = "e107", userId = "u107", branchName = "Sucursal 107", dateString = "2026-11-20", globalSales = 45_000.0),
            // Sucursal 109: 30,000 in 5 days -> 6,000/day -> proj 90,000 (60%) -> BEHIND (Rojo)
            DailySalesEntry(id = "e109", userId = "u109", branchName = "Sucursal 109", dateString = "2026-11-20", globalSales = 30_000.0)
        )

        val summaries = buildBranchSalesSummaries(
            branches = listOf("Sucursal 87", "Sucursal 107", "Sucursal 109"),
            quotas = listOf(q87, q107, q109),
            entries = entries,
            teamUsers = users,
            todayIso = "2026-11-20",
            currentPeriodId = "2026-11_Q2"
        )

        assertEquals(ProjectionPace.ON_TRACK, summaries[0].pace)
        assertEquals(165_000.0, summaries[0].projectedGlobalSales, 0.01)
        assertEquals(11_000.0, summaries[0].dailyAverage, 0.01)
        assertEquals(10, summaries[0].remainingDays)

        assertEquals(ProjectionPace.MODERATE_RISK, summaries[1].pace)
        assertEquals(135_000.0, summaries[1].projectedGlobalSales, 0.01)

        assertEquals(ProjectionPace.BEHIND, summaries[2].pace)
        assertEquals(90_000.0, summaries[2].projectedGlobalSales, 0.01)
    }

    @Test
    fun `computeAdminWeightedProjection weights quotas and evaluates surplus vs deficit against independent admin target`() {
        // 2026-11-20 is day 5 of 15 in Q2
        val adminIndependentQuota = BiweeklyQuota(
            id = "quota_admin",
            startDate = "2026-11-16",
            endDate = "2026-11-30",
            totalDays = 15L,
            periodId = "2026-11_Q2",
            branchName = BranchOptions.ALL_BRANCHES_OPTION,
            targetGlobalSales = 400_000.0,
            active = true
        )
        // High-quota store (75% weight): target 300k; low-quota store (25% weight): target 100k
        val q87 = branch87Quota.copy(targetGlobalSales = 300_000.0)
        val q107 = branch107Quota.copy(targetGlobalSales = 100_000.0)
        val users = listOf(
            UserProfile(userId = "u87", branchName = "Sucursal 87"),
            UserProfile(userId = "u107", branchName = "Sucursal 107")
        )

        // Case 1: High-quota store projects 360k (+60k), low-quota store projects 60k (-40k).
        // Naive % avg = (120% + 60%) / 2 = 90%, but quota-weighted = 420k / 400k = 105% -> ON_TRACK!
        val entriesPositiveNet = listOf(
            DailySalesEntry(id = "e87", userId = "u87", branchName = "Sucursal 87", dateString = "2026-11-20", globalSales = 120_000.0),
            DailySalesEntry(id = "e107", userId = "u107", branchName = "Sucursal 107", dateString = "2026-11-20", globalSales = 20_000.0)
        )
        val weightedGreen = ProjectionCalculator.computeAdminWeightedProjection(
            metric = SalesMetricType.GLOBAL_SALES,
            quotas = listOf(adminIndependentQuota, q87, q107),
            allBranchEntries = entriesPositiveNet,
            users = users,
            adminGeneralQuota = adminIndependentQuota,
            todayIso = "2026-11-20",
            branches = listOf("Sucursal 87", "Sucursal 107")
        )
        assertEquals(ProjectionPace.ON_TRACK, weightedGreen.pace)
        assertTrue(weightedGreen.surplusCoversDeficit)
        assertEquals(60_000.0, weightedGreen.positiveSurplusTotal, 0.01)
        assertEquals(40_000.0, weightedGreen.negativeDeficitTotal, 0.01)
        assertEquals(20_000.0, weightedGreen.netBalanceVsAdminQuota, 0.01)
        assertEquals(1.05f, weightedGreen.weightedBranchProjectedRatio, 0.001f)
        assertEquals(0.75f, weightedGreen.branchOutcomes[0].quotaWeightShare, 0.001f)
        assertEquals(0.25f, weightedGreen.branchOutcomes[1].quotaWeightShare, 0.001f)

        // Case 2: Surplus (+20k) does not cover deficit (-60k) -> net -40k (90% of 400k) -> MODERATE_RISK (Ámbar)
        val entriesModerate = listOf(
            DailySalesEntry(id = "e87", userId = "u87", branchName = "Sucursal 87", dateString = "2026-11-20", globalSales = 106_666.67),
            DailySalesEntry(id = "e107", userId = "u107", branchName = "Sucursal 107", dateString = "2026-11-20", globalSales = 13_333.33)
        )
        val weightedAmber = ProjectionCalculator.computeAdminWeightedProjection(
            metric = SalesMetricType.GLOBAL_SALES,
            quotas = listOf(adminIndependentQuota, q87, q107),
            allBranchEntries = entriesModerate,
            users = users,
            adminGeneralQuota = adminIndependentQuota,
            todayIso = "2026-11-20",
            branches = listOf("Sucursal 87", "Sucursal 107")
        )
        assertEquals(ProjectionPace.MODERATE_RISK, weightedAmber.pace)
        assertFalse(weightedAmber.surplusCoversDeficit)
        assertEquals(-40_000.0, weightedAmber.netBalanceVsAdminQuota, 1.0)

        // Case 3: Severe deficit -> projected 240k (60% of 400k) -> BEHIND (Rojo)
        val entriesBehind = listOf(
            DailySalesEntry(id = "e87", userId = "u87", branchName = "Sucursal 87", dateString = "2026-11-20", globalSales = 70_000.0),
            DailySalesEntry(id = "e107", userId = "u107", branchName = "Sucursal 107", dateString = "2026-11-20", globalSales = 10_000.0)
        )
        val weightedRed = ProjectionCalculator.computeAdminWeightedProjection(
            metric = SalesMetricType.GLOBAL_SALES,
            quotas = listOf(adminIndependentQuota, q87, q107),
            allBranchEntries = entriesBehind,
            users = users,
            adminGeneralQuota = adminIndependentQuota,
            todayIso = "2026-11-20",
            branches = listOf("Sucursal 87", "Sucursal 107")
        )
        assertEquals(ProjectionPace.BEHIND, weightedRed.pace)
        assertFalse(weightedRed.surplusCoversDeficit)
        assertEquals(-160_000.0, weightedRed.netBalanceVsAdminQuota, 0.01)
    }

    @Test
    fun `cumulative branch daily sales deduct from independent admin general quota remaining balance while advancing each branch`() {
        val users = listOf(
            UserProfile(userId = "u87", branchName = "Sucursal 87"),
            UserProfile(userId = "u107", branchName = "Sucursal 107")
        )
        // Day 1 & Day 2 sales from Sucursal 87 + Day 1 from Sucursal 107
        val entries = listOf(
            DailySalesEntry(
                id = "e87_d1",
                userId = "u87",
                branchName = "Sucursal 87",
                dateString = "2026-11-16",
                globalSales = 25_000.0,
                creditSales = 15_000.0,
                newCustomers = 4L,
                appMaOperations = 5L,
                newCustomerRequests = 6L
            ),
            DailySalesEntry(
                id = "e87_d2",
                userId = "u87",
                branchName = "Sucursal 87",
                dateString = "2026-11-17",
                globalSales = 30_000.0,
                creditSales = 18_000.0,
                newCustomers = 3L,
                appMaOperations = 4L,
                newCustomerRequests = 5L
            ),
            DailySalesEntry(
                id = "e107_d1",
                userId = "u107",
                branchName = "Sucursal 107",
                dateString = "2026-11-17",
                globalSales = 45_000.0,
                creditSales = 27_000.0,
                newCustomers = 8L,
                appMaOperations = 9L,
                newCustomerRequests = 10L
            )
        )

        // General admin target is 900,000; branch targets are 100,000 and 140,000 (sum = 240,000 != 900,000)
        val quotas = listOf(generalQuota, branch87Quota, branch107Quota)

        val adminSummary = ProjectionCalculator.computeAdminGeneralIndicatorsSummary(
            quotas = quotas,
            allBranchEntries = entries,
            users = users,
            uid = "admin_uid",
            todayIso = "2026-11-17"
        )
        val adminGlobal = adminSummary.metrics.first { it.metric == SalesMetricType.GLOBAL_SALES }
        // Admin target stays 900,000 (not overwritten by branch quotas)
        assertEquals(900_000.0, adminGlobal.targetTotal, 0.01)
        // Cumulative sales = 25k + 30k + 45k = 100k
        assertEquals(100_000.0, adminGlobal.actualTotal, 0.01)
        // Remaining balance toward admin general quota = 900k - 100k = 800k
        assertEquals(800_000.0, adminGlobal.remainingToTarget, 0.01)
        assertFalse(adminGlobal.isTargetMet)

        // Branch summaries advance cumulatively and independently against their own quotas
        val branchSummaries = buildBranchSalesSummaries(
            branches = listOf("Sucursal 87", "Sucursal 107"),
            quotas = quotas,
            entries = entries,
            teamUsers = users,
            todayIso = "2026-11-17",
            currentPeriodId = "2026-11_Q2"
        )
        val s87 = branchSummaries.first { it.branchName == "Sucursal 87" }
        val s107 = branchSummaries.first { it.branchName == "Sucursal 107" }

        assertEquals(55_000.0, s87.totalGlobalSales, 0.01)
        assertEquals(100_000.0, s87.quota.targetGlobalSales, 0.01)
        assertEquals(45_000.0, s87.remainingGlobalSales, 0.01)

        assertEquals(45_000.0, s107.totalGlobalSales, 0.01)
        assertEquals(140_000.0, s107.quota.targetGlobalSales, 0.01)
        assertEquals(95_000.0, s107.remainingGlobalSales, 0.01)
    }

    @Test
    fun `accumulated branch and admin sales persist past day 15 and month end until admin closes cycle`() {
        val users = listOf(
            UserProfile(userId = "u87", branchName = "Sucursal 87"),
            UserProfile(userId = "u107", branchName = "Sucursal 107")
        )
        // Test 1: Calendar rolled past day 15 (today = 2026-11-16) while Q1 (Nov 1..15) is still active/unclosed
        val q1General = generalQuota.copy(
            id = "quota_2026-11_Q1_todas_las_sucursales",
            title = "1ª Quincena · Noviembre 2026",
            startDate = "2026-11-01",
            endDate = "2026-11-15",
            totalDays = 15L,
            periodId = "2026-11_Q1",
            active = true
        )
        val q1Branch87 = branch87Quota.copy(
            id = "quota_2026-11_Q1_sucursal_87",
            startDate = "2026-11-01",
            endDate = "2026-11-15",
            totalDays = 15L,
            periodId = "2026-11_Q1",
            active = true
        )
        val q1Entries = listOf(
            DailySalesEntry(
                id = "e87_q1",
                userId = "u87",
                branchName = "Sucursal 87",
                quotaId = q1Branch87.id,
                dateString = "2026-11-14",
                globalSales = 65_000.0,
                creditSales = 35_000.0,
                newCustomers = 9L,
                appMaOperations = 12L,
                newCustomerRequests = 14L
            )
        )

        val q1CycleInfo = ProjectionCalculator.resolveActiveCycleInfo(listOf(q1General, q1Branch87), todayIso = "2026-11-16")
        assertTrue(q1CycleInfo.isCycleEnded)
        assertEquals("2026-11_Q1", q1CycleInfo.periodId)

        val q1AdminSummary = ProjectionCalculator.computeAdminGeneralIndicatorsSummary(
            quotas = listOf(q1General, q1Branch87),
            allBranchEntries = q1Entries,
            users = users,
            uid = "admin_uid",
            todayIso = "2026-11-16"
        )
        assertTrue(q1AdminSummary.isCycleEnded)
        assertEquals(65_000.0, q1AdminSummary.metrics.first { it.metric == SalesMetricType.GLOBAL_SALES }.actualTotal, 0.01)

        // Test 2: Calendar rolled past month-end (today = 2026-12-01) while Q2 (Nov 16..30) is still unclosed
        val q2Entries = listOf(
            DailySalesEntry(
                id = "e87_q2",
                userId = "u87",
                branchName = "Sucursal 87",
                quotaId = branch87Quota.id,
                dateString = "2026-11-28",
                globalSales = 75_000.0,
                creditSales = 40_000.0
            ),
            DailySalesEntry(
                id = "e107_q2",
                userId = "u107",
                branchName = "Sucursal 107",
                quotaId = branch107Quota.id,
                dateString = "2026-11-30",
                globalSales = 95_000.0,
                creditSales = 50_000.0
            )
        )
        val q2AdminSummary = ProjectionCalculator.computeAdminGeneralIndicatorsSummary(
            quotas = listOf(generalQuota, branch87Quota, branch107Quota),
            allBranchEntries = q2Entries,
            users = users,
            uid = "admin_uid",
            todayIso = "2026-12-01"
        )
        assertTrue(q2AdminSummary.isCycleEnded)
        assertEquals(170_000.0, q2AdminSummary.metrics.first { it.metric == SalesMetricType.GLOBAL_SALES }.actualTotal, 0.01)

        val q2BranchSummaries = buildBranchSalesSummaries(
            branches = listOf("Sucursal 87", "Sucursal 107"),
            quotas = listOf(generalQuota, branch87Quota, branch107Quota),
            entries = q2Entries,
            teamUsers = users,
            todayIso = "2026-12-01"
        )
        assertEquals(75_000.0, q2BranchSummaries[0].totalGlobalSales, 0.01)
        assertEquals(95_000.0, q2BranchSummaries[1].totalGlobalSales, 0.01)
    }

    @Test
    fun `confirming cycle close resets accumulated totals to zero while preserving base quotas and counting new cycle cleanly`() {
        val users = listOf(
            UserProfile(userId = "u87", branchName = "Sucursal 87"),
            UserProfile(userId = "u107", branchName = "Sucursal 107")
        )
        val oldEntries = listOf(
            DailySalesEntry(
                id = "e87_old",
                userId = "u87",
                branchName = "Sucursal 87",
                quotaId = branch87Quota.id,
                dateString = "2026-11-30",
                globalSales = 82_000.0
            ),
            DailySalesEntry(
                id = "e107_old",
                userId = "u107",
                branchName = "Sucursal 107",
                quotaId = branch107Quota.id,
                dateString = "2026-11-30",
                globalSales = 118_000.0
            )
        )

        // New cycle active quotas created by closeAndResetBiweeklyCycle (preserving targets intact)
        val decQ1General = generalQuota.copy(
            id = "quota_2026-12_Q1_todas_las_sucursales",
            title = "1ª Quincena · Diciembre 2026",
            startDate = "2026-12-01",
            endDate = "2026-12-15",
            totalDays = 15L,
            periodId = "2026-12_Q1",
            active = true
        )
        val decQ1Branch87 = branch87Quota.copy(
            id = "quota_2026-12_Q1_sucursal_87",
            title = "1ª Quincena · Diciembre 2026",
            startDate = "2026-12-01",
            endDate = "2026-12-15",
            totalDays = 15L,
            periodId = "2026-12_Q1",
            active = true
        )
        val decQ1Branch107 = branch107Quota.copy(
            id = "quota_2026-12_Q1_sucursal_107",
            title = "1ª Quincena · Diciembre 2026",
            startDate = "2026-12-01",
            endDate = "2026-12-15",
            totalDays = 15L,
            periodId = "2026-12_Q1",
            active = true
        )
        val newActiveQuotas = listOf(decQ1General, decQ1Branch87, decQ1Branch107)

        // Immediately after reset: all accumulated totals are $0.00, base quotas preserved
        val resetAdminSummary = ProjectionCalculator.computeAdminGeneralIndicatorsSummary(
            quotas = newActiveQuotas,
            allBranchEntries = oldEntries,
            users = users,
            uid = "admin_uid",
            todayIso = "2026-12-01"
        )
        assertFalse(resetAdminSummary.isCycleEnded)
        val resetGlobal = resetAdminSummary.metrics.first { it.metric == SalesMetricType.GLOBAL_SALES }
        assertEquals(0.0, resetGlobal.actualTotal, 0.01)
        assertEquals(900_000.0, resetGlobal.targetTotal, 0.01)

        val resetBranches = buildBranchSalesSummaries(
            branches = listOf("Sucursal 87", "Sucursal 107"),
            quotas = newActiveQuotas,
            entries = oldEntries,
            teamUsers = users,
            todayIso = "2026-12-01"
        )
        assertEquals(0.0, resetBranches[0].totalGlobalSales, 0.01)
        assertEquals(100_000.0, resetBranches[0].quota.targetGlobalSales, 0.01)
        assertEquals(0.0, resetBranches[1].totalGlobalSales, 0.01)
        assertEquals(140_000.0, resetBranches[1].quota.targetGlobalSales, 0.01)

        // New cycle sales entries accumulate cleanly without mixing with archived cycle entries
        val newEntry = DailySalesEntry(
            id = "e87_new",
            userId = "u87",
            branchName = "Sucursal 87",
            quotaId = decQ1Branch87.id,
            dateString = "2026-12-01",
            globalSales = 18_500.0,
            creditSales = 9_000.0
        )
        val combinedEntries = oldEntries + newEntry
        val afterNewSaleAdmin = ProjectionCalculator.computeAdminGeneralIndicatorsSummary(
            quotas = newActiveQuotas,
            allBranchEntries = combinedEntries,
            users = users,
            uid = "admin_uid",
            todayIso = "2026-12-01"
        )
        assertEquals(18_500.0, afterNewSaleAdmin.metrics.first { it.metric == SalesMetricType.GLOBAL_SALES }.actualTotal, 0.01)
    }
}

