package com.example.data.repository

import com.example.data.model.ActiveBiweeklyCycleInfo
import com.example.data.model.AdminWeightedProjection
import com.example.data.model.BiweeklyQuota
import com.example.data.model.BranchOptions
import com.example.data.model.BranchWeightedOutcome
import com.example.data.model.ChartSeries
import com.example.data.model.DailySalesEntry
import com.example.data.model.MetricProgress
import com.example.data.model.ProjectionPace
import com.example.data.model.SalesMetricType
import com.example.data.model.SalesProjectionSummary
import com.example.data.model.UserProfile
import com.example.data.model.resolveQuotaForBranch
import com.example.utils.DateAndFormatters

object ProjectionCalculator {

    const val DEFAULT_GENERAL_GLOBAL_SALES = 250_000.0
    const val DEFAULT_GENERAL_CREDIT_SALES = 150_000.0
    const val DEFAULT_GENERAL_NEW_CUSTOMERS = 45L
    const val DEFAULT_GENERAL_APP_MA_OPS = 60L
    const val DEFAULT_GENERAL_NEW_REQUESTS = 75L

    fun resolvePace(projectedRatio: Float): ProjectionPace = when {
        projectedRatio >= 0.9999f -> ProjectionPace.ON_TRACK
        projectedRatio >= 0.80f -> ProjectionPace.MODERATE_RISK
        else -> ProjectionPace.BEHIND
    }

    fun targetForMetric(quota: BiweeklyQuota, metric: SalesMetricType): Double = when (metric) {
        SalesMetricType.GLOBAL_SALES -> quota.targetGlobalSales
        SalesMetricType.CREDIT_SALES -> quota.targetCreditSales
        SalesMetricType.NEW_CUSTOMERS -> quota.targetNewCustomers.toDouble()
        SalesMetricType.APP_MA_OPS -> quota.targetAppMaOperations.toDouble()
        SalesMetricType.NEW_CUSTOMER_REQUESTS -> quota.targetNewCustomerRequests.toDouble()
    }

    fun actualForMetric(entry: DailySalesEntry, metric: SalesMetricType): Double = when (metric) {
        SalesMetricType.GLOBAL_SALES -> entry.globalSales
        SalesMetricType.CREDIT_SALES -> entry.creditSales
        SalesMetricType.NEW_CUSTOMERS -> entry.newCustomers.toDouble()
        SalesMetricType.APP_MA_OPS -> entry.appMaOperations.toDouble()
        SalesMetricType.NEW_CUSTOMER_REQUESTS -> entry.newCustomerRequests.toDouble()
    }

    /**
     * Resolves the currently open biweekly cycle anchored in Firestore's active quotas
     * rather than the raw calendar date, so accumulated sales persist across day 15 /
     * month-end until the administrator explicitly confirms closing and resetting the cycle.
     */
    fun resolveActiveCycleInfo(
        quotas: List<BiweeklyQuota>,
        todayIso: String = DateAndFormatters.todayIso()
    ): ActiveBiweeklyCycleInfo {
        val activePool = quotas.filter { it.active }.ifEmpty { quotas }
            .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }

        if (activePool.isEmpty()) {
            val fallback = defaultGeneralQuotaPlaceholder(todayIso)
            val totalDays = DateAndFormatters.daysBetweenInclusive(fallback.startDate, fallback.endDate).coerceIn(1, 31)
            val elapsed = DateAndFormatters.dayIndexInPeriod(fallback.startDate, todayIso, totalDays)
            return ActiveBiweeklyCycleInfo(
                periodId = fallback.effectivePeriodId,
                title = fallback.title,
                startDate = fallback.startDate,
                endDate = fallback.endDate,
                effectiveUpperDateIso = maxOf(fallback.endDate, todayIso),
                totalDays = totalDays,
                daysElapsed = elapsed,
                remainingDays = (totalDays - elapsed).coerceAtLeast(0),
                isCycleEnded = false,
                generalQuota = fallback,
                activeQuotaIds = emptySet()
            )
        }

        val generalAnchor = activePool.firstOrNull {
            it.branchName.isNullOrBlank() || it.branchName == BranchOptions.ALL_BRANCHES_OPTION
        }
        val anchor = generalAnchor ?: activePool.first()
        val cyclePeriodId = anchor.effectivePeriodId
        val cycleQuotas = activePool.filter { it.effectivePeriodId == cyclePeriodId }.ifEmpty { activePool }

        val fallbackRange = DateAndFormatters.biweeklyRangeForIso(todayIso)
        val startDate = anchor.startDate.takeIf { it.length == 10 } ?: fallbackRange.first
        val endDate = anchor.endDate.takeIf { it.length == 10 && it >= startDate } ?: fallbackRange.second
        val totalDays = DateAndFormatters.daysBetweenInclusive(startDate, endDate).coerceIn(1, 31)
        val daysElapsed = DateAndFormatters.dayIndexInPeriod(startDate, todayIso, totalDays)
        val remainingDays = (totalDays - daysElapsed).coerceAtLeast(0)
        val isCycleEnded = todayIso > endDate
        val effectiveUpper = maxOf(endDate, todayIso)
        val title = anchor.title.takeIf { it.isNotBlank() } ?: DateAndFormatters.defaultBiweeklyTitle()

        val resolvedGeneral = cycleQuotas.firstOrNull {
            it.branchName.isNullOrBlank() || it.branchName == BranchOptions.ALL_BRANCHES_OPTION
        } ?: defaultGeneralQuotaPlaceholder(todayIso).copy(
            id = "quota_${cyclePeriodId}_todas_las_sucursales",
            title = title,
            startDate = startDate,
            endDate = endDate,
            totalDays = totalDays.toLong(),
            periodId = cyclePeriodId
        )

        val activeIds = cycleQuotas.map { it.id }.filter { it.isNotBlank() }.toSet()

        return ActiveBiweeklyCycleInfo(
            periodId = cyclePeriodId,
            title = title,
            startDate = startDate,
            endDate = endDate,
            effectiveUpperDateIso = effectiveUpper,
            totalDays = totalDays,
            daysElapsed = daysElapsed,
            remainingDays = remainingDays,
            isCycleEnded = isCycleEnded,
            generalQuota = resolvedGeneral,
            activeQuotaIds = activeIds
        )
    }

    /**
     * Resolves the administrator's independent general supervision quota
     * (`Todas las Sucursales` or blank branchName). Never falls back to an
     * individual branch quota and never sums individual branch quotas.
     */
    fun resolveIndependentGeneralQuota(
        quotas: List<BiweeklyQuota>,
        currentPeriodId: String? = null
    ): BiweeklyQuota? {
        if (quotas.isEmpty()) return null
        val generalQuotas = quotas
            .filter { it.branchName.isNullOrBlank() || it.branchName == BranchOptions.ALL_BRANCHES_OPTION }
            .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }

        if (!currentPeriodId.isNullOrBlank()) {
            return generalQuotas.firstOrNull { it.effectivePeriodId == currentPeriodId }
                ?: generalQuotas.firstOrNull()
        }
        return generalQuotas.firstOrNull()
    }

    fun defaultGeneralQuotaPlaceholder(
        todayIso: String = DateAndFormatters.todayIso()
    ): BiweeklyQuota {
        val (startDate, endDate) = DateAndFormatters.biweeklyRangeForIso(todayIso)
        val periodId = DateAndFormatters.biweeklyPeriodIdForIso(todayIso)
        val totalDays = DateAndFormatters.daysBetweenInclusive(startDate, endDate).toLong().coerceIn(1L, 31L)
        return BiweeklyQuota(
            id = "quota_${periodId}_todas_las_sucursales",
            title = DateAndFormatters.defaultBiweeklyTitle(),
            startDate = startDate,
            endDate = endDate,
            totalDays = totalDays,
            periodId = periodId,
            branchName = BranchOptions.ALL_BRANCHES_OPTION,
            targetGlobalSales = DEFAULT_GENERAL_GLOBAL_SALES,
            targetCreditSales = DEFAULT_GENERAL_CREDIT_SALES,
            targetNewCustomers = DEFAULT_GENERAL_NEW_CUSTOMERS,
            targetAppMaOperations = DEFAULT_GENERAL_APP_MA_OPS,
            targetNewCustomerRequests = DEFAULT_GENERAL_NEW_REQUESTS,
            active = true
        )
    }

    fun resolveQuotaForDashboard(
        targetBranch: String,
        quotas: List<BiweeklyQuota>,
        currentPeriodId: String? = null
    ): BiweeklyQuota? {
        val effectivePeriodId = currentPeriodId?.takeIf { it.isNotBlank() }
            ?: if (quotas.isNotEmpty()) resolveActiveCycleInfo(quotas).periodId else DateAndFormatters.currentBiweeklyPeriodId()
        val normalizedBranch = targetBranch.ifBlank { BranchOptions.ALL_BRANCHES_OPTION }
        return if (normalizedBranch == BranchOptions.ALL_BRANCHES_OPTION) {
            resolveIndependentGeneralQuota(quotas, effectivePeriodId)
        } else {
            resolveQuotaForBranch(normalizedBranch, quotas, effectivePeriodId).first
        }
    }

    private fun resolveBranchKey(
        entry: DailySalesEntry,
        uidToBranch: Map<String, String>
    ): String = entry.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
        ?: uidToBranch[entry.userId]
        ?: entry.userName.takeIf { it in BranchOptions.ALL_BRANCHES }
        ?: entry.userId

    private fun resolveCycleRangeForQuota(
        quota: BiweeklyQuota,
        todayIso: String
    ): Pair<String, String> {
        val fallback = DateAndFormatters.biweeklyRangeForIso(todayIso)
        val start = quota.startDate.takeIf { it.length == 10 } ?: fallback.first
        val end = quota.endDate.takeIf { it.length == 10 && it >= start } ?: fallback.second
        return start to end
    }

    /**
     * Collects and deduplicates daily closing entries belonging to the active unclosed cycle:
     * keeps entries accumulated past `endDate` until admin confirms closure, and excludes
     * closed-cycle entries via `activeQuotaIds`.
     */
    fun selectEntriesForPeriod(
        quota: BiweeklyQuota,
        entries: List<DailySalesEntry>,
        users: List<UserProfile>,
        todayIso: String = DateAndFormatters.todayIso(),
        activeQuotaIds: Set<String> = emptySet()
    ): List<DailySalesEntry> {
        val (periodStartIso, periodEndIso) = resolveCycleRangeForQuota(quota, todayIso)
        val effectiveUpperIso = maxOf(periodEndIso, todayIso)
        val uidToBranch = users
            .mapNotNull { u -> u.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }?.let { u.userId to it } }
            .toMap()

        return entries
            .filter { entry ->
                val inRange = entry.dateString >= periodStartIso && entry.dateString <= effectiveUpperIso
                val matchesActiveQuota = activeQuotaIds.isEmpty() ||
                    entry.quotaId.isBlank() ||
                    entry.quotaId in activeQuotaIds
                inRange && matchesActiveQuota
            }
            .sortedWith(
                compareByDescending<DailySalesEntry> { it.dateString }
                    .thenByDescending { it.updatedAt?.seconds ?: 0L }
                    .thenByDescending { it.createdAt?.seconds ?: 0L }
                    .thenByDescending { it.quotaId == quota.id }
            )
            .distinctBy { entry ->
                "${resolveBranchKey(entry, uidToBranch)}_${entry.dateString}"
            }
    }

    fun computeProjectionSummary(
        quota: BiweeklyQuota,
        entries: List<DailySalesEntry>,
        uid: String,
        users: List<UserProfile>,
        todayIso: String = DateAndFormatters.todayIso(),
        closingBranchName: String? = null,
        weightedByMetric: Map<SalesMetricType, AdminWeightedProjection> = emptyMap(),
        activeQuotaIds: Set<String> = emptySet()
    ): SalesProjectionSummary {
        val (cycleStartIso, cycleEndIso) = resolveCycleRangeForQuota(quota, todayIso)
        val totalDays = DateAndFormatters.daysBetweenInclusive(cycleStartIso, cycleEndIso).coerceIn(1, 31)
        val daysElapsed = DateAndFormatters.dayIndexInPeriod(cycleStartIso, todayIso, totalDays)
        val remainingDays = (totalDays - daysElapsed).coerceAtLeast(0)
        val isCycleEnded = todayIso > cycleEndIso

        val alignedQuota = quota.copy(
            startDate = cycleStartIso,
            endDate = cycleEndIso,
            totalDays = totalDays.toLong()
        )
        val periodEntries = selectEntriesForPeriod(
            quota = alignedQuota,
            entries = entries,
            users = users,
            todayIso = todayIso,
            activeQuotaIds = activeQuotaIds
        )
        val todayEntry = periodEntries.firstOrNull { it.userId == uid && it.dateString == todayIso }
            ?: periodEntries.firstOrNull {
                it.dateString == todayIso &&
                    closingBranchName != null &&
                    (it.branchName == closingBranchName || it.userName == closingBranchName)
            }

        fun buildMetric(
            metric: SalesMetricType,
            actual: Double,
            target: Double
        ): MetricProgress {
            val safeTarget = target.coerceAtLeast(1.0)
            val dailyAvg = actual / daysElapsed.toDouble()
            val projected = actual + dailyAvg * remainingDays.toDouble()
            val requiredPace = if (remainingDays > 0) {
                (safeTarget - actual).coerceAtLeast(0.0) / remainingDays.toDouble()
            } else {
                (safeTarget - actual).coerceAtLeast(0.0)
            }
            val progressRatio = if (target > 0.0) {
                (actual / target).toFloat().coerceIn(0f, 1.5f)
            } else if (actual >= 0.0) {
                1f
            } else {
                0f
            }
            val weighted = weightedByMetric[metric]
            val projectedRatio = weighted?.adminProjectedRatio ?: if (target > 0.0) {
                (projected / target).toFloat().coerceIn(0f, 2f)
            } else if (projected >= 0.0) {
                1f
            } else {
                0f
            }
            val pace = weighted?.pace ?: resolvePace(projectedRatio)
            return MetricProgress(
                metric = metric,
                actualTotal = actual,
                targetTotal = target,
                projectedTotal = weighted?.totalProjected ?: projected,
                progressRatio = progressRatio,
                projectedRatio = projectedRatio,
                dailyAverage = dailyAvg,
                requiredDailyPace = requiredPace,
                pace = pace
            )
        }

        val metrics = listOf(
            buildMetric(
                SalesMetricType.GLOBAL_SALES,
                periodEntries.sumOf { it.globalSales },
                alignedQuota.targetGlobalSales
            ),
            buildMetric(
                SalesMetricType.CREDIT_SALES,
                periodEntries.sumOf { it.creditSales },
                alignedQuota.targetCreditSales
            ),
            buildMetric(
                SalesMetricType.NEW_CUSTOMERS,
                periodEntries.sumOf { it.newCustomers }.toDouble(),
                alignedQuota.targetNewCustomers.toDouble()
            ),
            buildMetric(
                SalesMetricType.APP_MA_OPS,
                periodEntries.sumOf { it.appMaOperations }.toDouble(),
                alignedQuota.targetAppMaOperations.toDouble()
            ),
            buildMetric(
                SalesMetricType.NEW_CUSTOMER_REQUESTS,
                periodEntries.sumOf { it.newCustomerRequests }.toDouble(),
                alignedQuota.targetNewCustomerRequests.toDouble()
            )
        )

        return SalesProjectionSummary(
            quota = alignedQuota,
            daysElapsed = daysElapsed,
            totalDays = totalDays,
            metrics = metrics,
            entriesCount = periodEntries.size,
            todayEntry = todayEntry,
            recentEntries = periodEntries.sortedByDescending { it.dateString }.take(15),
            remainingDays = remainingDays,
            weightedByMetric = weightedByMetric,
            isCycleEnded = isCycleEnded
        )
    }

    /**
     * Evaluates the quota-weighted general projection for administrators anchored to the active
     * unclosed cycle (remaining accumulated past endDate until admin explicitly closes the cycle).
     */
    fun computeAdminWeightedProjection(
        metric: SalesMetricType,
        quotas: List<BiweeklyQuota>,
        allBranchEntries: List<DailySalesEntry>,
        users: List<UserProfile>,
        adminGeneralQuota: BiweeklyQuota,
        todayIso: String = DateAndFormatters.todayIso(),
        branches: List<String> = BranchOptions.ALL_BRANCHES,
        activeQuotaIds: Set<String> = emptySet()
    ): AdminWeightedProjection {
        val (cycleStartIso, cycleEndIso) = resolveCycleRangeForQuota(adminGeneralQuota, todayIso)
        val effectiveUpperIso = maxOf(cycleEndIso, todayIso)
        val totalCycleDays = DateAndFormatters.daysBetweenInclusive(cycleStartIso, cycleEndIso).coerceIn(1, 31)
        val daysElapsed = DateAndFormatters.dayIndexInPeriod(cycleStartIso, todayIso, totalCycleDays)
        val remainingDays = (totalCycleDays - daysElapsed).coerceAtLeast(0)
        val cyclePeriodId = adminGeneralQuota.effectivePeriodId
        val resolvedActiveIds = if (activeQuotaIds.isNotEmpty()) {
            activeQuotaIds
        } else {
            resolveActiveCycleInfo(quotas.ifEmpty { listOf(adminGeneralQuota) }, todayIso).activeQuotaIds
        }

        val uidToBranch = users
            .mapNotNull { u -> u.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }?.let { u.userId to it } }
            .toMap()

        val periodEntriesByBranch = allBranchEntries
            .filter { entry ->
                val inDates = entry.dateString >= cycleStartIso && entry.dateString <= effectiveUpperIso
                val matchesQuota = resolvedActiveIds.isEmpty() ||
                    entry.quotaId.isBlank() ||
                    entry.quotaId in resolvedActiveIds
                inDates && matchesQuota
            }
            .sortedWith(
                compareByDescending<DailySalesEntry> { it.dateString }
                    .thenByDescending { it.updatedAt?.seconds ?: 0L }
                    .thenByDescending { it.createdAt?.seconds ?: 0L }
            )
            .groupBy { entry -> resolveBranchKey(entry, uidToBranch) }

        val interimOutcomes = branches.map { branch ->
            val branchQuota = resolveQuotaForBranch(branch, quotas, cyclePeriodId).first ?: adminGeneralQuota
            val branchTarget = targetForMetric(branchQuota, metric).coerceAtLeast(0.0)
            val dedupedEntries = periodEntriesByBranch[branch].orEmpty().distinctBy { it.dateString }
            val actual = dedupedEntries.sumOf { actualForMetric(it, metric) }
            val dailyAvg = actual / daysElapsed.toDouble()
            val projected = actual + dailyAvg * remainingDays.toDouble()
            val delta = projected - branchTarget
            val branchProjRatio = if (branchTarget > 0.0) {
                (projected / branchTarget).toFloat().coerceAtLeast(0f)
            } else if (projected >= 0.0) {
                1f
            } else {
                0f
            }
            val branchPace = resolvePace(branchProjRatio)
            BranchWeightedOutcome(
                branchName = branch,
                branchTarget = branchTarget,
                actualTotal = actual,
                projectedTotal = projected,
                deltaVsBranchQuota = delta,
                projectedRatio = branchProjRatio,
                pace = branchPace,
                quotaWeightShare = 0f
            )
        }

        val sumOfBranchQuotas = interimOutcomes.sumOf { it.branchTarget }
        val weightedOutcomes = interimOutcomes.map { outcome ->
            val share = if (sumOfBranchQuotas > 0.0) {
                (outcome.branchTarget / sumOfBranchQuotas).toFloat()
            } else if (interimOutcomes.isNotEmpty()) {
                1f / interimOutcomes.size.toFloat()
            } else {
                0f
            }
            outcome.copy(quotaWeightShare = share)
        }

        val totalActual = weightedOutcomes.sumOf { it.actualTotal }
        val totalProjected = weightedOutcomes.sumOf { it.projectedTotal }
        val weightedBranchProjectedRatio = if (sumOfBranchQuotas > 0.0) {
            (totalProjected / sumOfBranchQuotas).toFloat().coerceAtLeast(0f)
        } else if (totalActual >= 0.0) {
            1f
        } else {
            0f
        }

        val positiveBranches = weightedOutcomes.filter { it.deltaVsBranchQuota >= 0.0 }
        val moderateRiskCount = weightedOutcomes.count { it.pace == ProjectionPace.MODERATE_RISK }
        val behindCount = weightedOutcomes.count { it.pace == ProjectionPace.BEHIND }
        val positiveSurplus = positiveBranches.sumOf { it.deltaVsBranchQuota }
        val negativeDeficit = weightedOutcomes.filter { it.deltaVsBranchQuota < 0.0 }.sumOf { -it.deltaVsBranchQuota }
        val branchNetBalance = positiveSurplus - negativeDeficit

        val adminTarget = targetForMetric(adminGeneralQuota, metric).coerceAtLeast(0.0)
        val netBalanceVsAdminQuota = totalProjected - adminTarget
        val adminProjectedRatio = if (adminTarget > 0.0) {
            (totalProjected / adminTarget).toFloat().coerceAtLeast(0f)
        } else if (totalProjected >= 0.0) {
            1f
        } else {
            0f
        }
        val adminPace = resolvePace(adminProjectedRatio)
        val surplusCoversDeficit = adminPace == ProjectionPace.ON_TRACK

        return AdminWeightedProjection(
            metric = metric,
            daysElapsed = daysElapsed,
            totalCycleDays = totalCycleDays,
            remainingDays = remainingDays,
            adminTarget = adminTarget,
            sumOfBranchQuotas = sumOfBranchQuotas,
            totalActual = totalActual,
            totalProjected = totalProjected,
            weightedBranchProjectedRatio = weightedBranchProjectedRatio,
            positiveBranchesCount = positiveBranches.size,
            moderateRiskBranchesCount = moderateRiskCount,
            behindBranchesCount = behindCount,
            positiveSurplusTotal = positiveSurplus,
            negativeDeficitTotal = negativeDeficit,
            branchNetBalance = branchNetBalance,
            netBalanceVsAdminQuota = netBalanceVsAdminQuota,
            adminProjectedRatio = adminProjectedRatio,
            surplusCoversDeficit = surplusCoversDeficit,
            pace = adminPace,
            branchOutcomes = weightedOutcomes
        )
    }

    /**
     * Builds the administrator's general biweekly indicators summary anchored to the active
     * unclosed cycle so accumulated sales never zero silently when the calendar rolls over.
     */
    fun computeAdminGeneralIndicatorsSummary(
        quotas: List<BiweeklyQuota>,
        allBranchEntries: List<DailySalesEntry>,
        users: List<UserProfile>,
        uid: String = "",
        todayIso: String = DateAndFormatters.todayIso(),
        branches: List<String> = BranchOptions.ALL_BRANCHES
    ): SalesProjectionSummary {
        val activeCycle = resolveActiveCycleInfo(quotas, todayIso)
        val generalQuota = activeCycle.generalQuota

        val weightedByMetric = SalesMetricType.entries.associateWith { metric ->
            computeAdminWeightedProjection(
                metric = metric,
                quotas = quotas,
                allBranchEntries = allBranchEntries,
                users = users,
                adminGeneralQuota = generalQuota,
                todayIso = todayIso,
                branches = branches,
                activeQuotaIds = activeCycle.activeQuotaIds
            )
        }

        return computeProjectionSummary(
            quota = generalQuota,
            entries = allBranchEntries,
            uid = uid,
            users = users,
            todayIso = todayIso,
            closingBranchName = null,
            weightedByMetric = weightedByMetric,
            activeQuotaIds = activeCycle.activeQuotaIds
        )
    }

    fun buildChartSeries(
        quota: BiweeklyQuota,
        entries: List<DailySalesEntry>,
        metric: SalesMetricType,
        users: List<UserProfile>,
        todayIso: String = DateAndFormatters.todayIso(),
        activeQuotaIds: Set<String> = emptySet()
    ): ChartSeries {
        val (cycleStartIso, cycleEndIso) = resolveCycleRangeForQuota(quota, todayIso)
        val totalDays = DateAndFormatters.daysBetweenInclusive(cycleStartIso, cycleEndIso).coerceIn(1, 31)
        val elapsedDays = DateAndFormatters.dayIndexInPeriod(cycleStartIso, todayIso, totalDays)
        val periodEntries = selectEntriesForPeriod(
            quota = quota,
            entries = entries,
            users = users,
            todayIso = todayIso,
            activeQuotaIds = activeQuotaIds
        )

        val dailyTotals = DoubleArray(totalDays) { 0.0 }
        periodEntries.forEach { entry ->
            val dayIdx = DateAndFormatters.dayIndexInPeriod(cycleStartIso, entry.dateString, totalDays) - 1
            val value = actualForMetric(entry, metric)
            if (dayIdx in 0 until totalDays) {
                dailyTotals[dayIdx] += value
            }
        }

        val actualCumulative = mutableListOf<Double>()
        var running = 0.0
        for (i in 0 until elapsedDays) {
            running += dailyTotals[i]
            actualCumulative.add(running)
        }

        val avgDaily = if (elapsedDays > 0) running / elapsedDays.toDouble() else 0.0
        val projectedCumulative = mutableListOf<Double>()
        for (day in 1..totalDays) {
            if (day <= elapsedDays) {
                projectedCumulative.add(actualCumulative[day - 1])
            } else {
                projectedCumulative.add(running + avgDaily * (day - elapsedDays))
            }
        }

        val target = targetForMetric(quota, metric)

        return ChartSeries(
            metric = metric,
            totalDays = totalDays,
            elapsedDays = elapsedDays,
            targetValue = target,
            actualCumulativeByDay = actualCumulative,
            projectedCumulativeByDay = projectedCumulative
        )
    }
}
