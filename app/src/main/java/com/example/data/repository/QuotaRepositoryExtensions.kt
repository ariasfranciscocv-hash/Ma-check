package com.example.data.repository

import android.content.Context
import com.example.data.firestore.FirestoreProvider
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.BiweeklyCycleArchive
import com.example.data.model.BiweeklyCycleBranchArchive
import com.example.data.model.BiweeklyQuota
import com.example.data.model.BranchOptions
import com.example.data.model.BranchSalesSummary
import com.example.data.model.SalesMetricType
import com.example.data.model.SalesProjectionSummary
import com.example.utils.DateAndFormatters
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToLong

/**
 * Independent repository operations for managing the administrator's supervision quota,
 * branch-level biweekly quotas, and explicit biweekly cycle closing/archiving without
 * modifying the protected AppRepository.kt.
 */
class QuotaRepositoryExtensions(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(FirestoreProvider.getInstance(context))

    private val auth = Firebase.auth

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("El usuario debe iniciar sesión con Google primero.")

    private fun buildQuotaDocId(periodId: String, branchName: String): String {
        val branchSlug = branchName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "general" }
        return "quota_${periodId}_${branchSlug}".take(120)
    }

    fun observeCycleArchives(uid: String, isAdmin: Boolean): Flow<List<BiweeklyCycleArchive>> = flow {
        if (uid.isBlank() || !isAdmin) {
            emit(emptyList())
            return@flow
        }
        try {
            emitAll(
                db.collection(COLLECTION_CYCLE_ARCHIVES)
                    .snapshots()
                    .map { snapshot ->
                        snapshot.toObjects(
                            BiweeklyCycleArchive::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ).sortedByDescending { it.closedAt?.seconds ?: 0L }
                    }
                    .catch { error ->
                        handleFlowError(error, OperationType.LIST, COLLECTION_CYCLE_ARCHIVES, uid)
                        throw error
                    }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFlowError(e, OperationType.LIST, COLLECTION_CYCLE_ARCHIVES, uid)
            throw e
        }
    }

    /**
     * Archives the current unclosed biweekly cycle summary in Firestore (`biweekly_cycle_archives`),
     * deactivates the closed cycle's quotas so all prior sales entries no longer accumulate,
     * and creates fresh active quotas for the new cycle preserving the administrator's general quota
     * and every individual branch's custom targets intact (starting at $0.00).
     */
    suspend fun closeAndResetBiweeklyCycle(
        adminGeneralSummary: SalesProjectionSummary?,
        branchSummaries: List<BranchSalesSummary>,
        activeQuotas: List<BiweeklyQuota>,
        todayIso: String = DateAndFormatters.todayIso()
    ): Result<BiweeklyCycleArchive> {
        val path = COLLECTION_CYCLE_ARCHIVES
        return try {
            val adminUid = requireUserId()
            val adminEmail = auth.currentUser?.email?.trim()
                ?.takeIf { it.length >= 3 }
                ?.take(120)
                ?: "admin@mueblesamerica.mx"

            val existingActiveSnap = db.collection(COLLECTION_QUOTAS)
                .whereEqualTo("active", true)
                .get()
                .await()
            val firestoreActiveQuotas = existingActiveSnap.toObjects(
                BiweeklyQuota::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val mergedActiveQuotas = if (firestoreActiveQuotas.isNotEmpty()) {
                firestoreActiveQuotas
            } else {
                activeQuotas
            }

            val activeCycle = ProjectionCalculator.resolveActiveCycleInfo(mergedActiveQuotas, todayIso)
            val generalQuota = activeCycle.generalQuota

            val globalMetric = adminGeneralSummary?.metrics?.firstOrNull { it.metric == SalesMetricType.GLOBAL_SALES }
            val creditMetric = adminGeneralSummary?.metrics?.firstOrNull { it.metric == SalesMetricType.CREDIT_SALES }
            val customersMetric = adminGeneralSummary?.metrics?.firstOrNull { it.metric == SalesMetricType.NEW_CUSTOMERS }
            val appMaMetric = adminGeneralSummary?.metrics?.firstOrNull { it.metric == SalesMetricType.APP_MA_OPS }
            val requestsMetric = adminGeneralSummary?.metrics?.firstOrNull { it.metric == SalesMetricType.NEW_CUSTOMER_REQUESTS }

            val adminTargetGlobal = (globalMetric?.targetTotal ?: generalQuota.targetGlobalSales).coerceIn(1.0, 1_000_000_000.0)
            val adminActualGlobal = (globalMetric?.actualTotal ?: 0.0).coerceIn(0.0, 1_000_000_000.0)
            val adminTargetCredit = (creditMetric?.targetTotal ?: generalQuota.targetCreditSales).coerceIn(0.0, 1_000_000_000.0)
            val adminActualCredit = (creditMetric?.actualTotal ?: 0.0).coerceIn(0.0, 1_000_000_000.0)
            val adminTargetCustomers = (customersMetric?.targetTotal?.roundToLong() ?: generalQuota.targetNewCustomers).coerceIn(0L, 100_000L)
            val adminActualCustomers = (customersMetric?.actualTotal?.roundToLong() ?: 0L).coerceIn(0L, 100_000L)
            val adminTargetAppMa = (appMaMetric?.targetTotal?.roundToLong() ?: generalQuota.targetAppMaOperations).coerceIn(0L, 100_000L)
            val adminActualAppMa = (appMaMetric?.actualTotal?.roundToLong() ?: 0L).coerceIn(0L, 100_000L)
            val adminTargetRequests = (requestsMetric?.targetTotal?.roundToLong() ?: generalQuota.targetNewCustomerRequests).coerceIn(0L, 100_000L)
            val adminActualRequests = (requestsMetric?.actualTotal?.roundToLong() ?: 0L).coerceIn(0L, 100_000L)
            val adminCompletionPct = if (adminTargetGlobal > 0.0) {
                ((adminActualGlobal / adminTargetGlobal) * 100.0).roundToLong().coerceIn(0L, 10_000L)
            } else {
                0L
            }
            val entriesCount = (adminGeneralSummary?.entriesCount?.toLong() ?: 0L).coerceIn(0L, 100_000L)

            val branchSummaryMap = branchSummaries.associateBy { it.branchName }
            val latestCustomQuotasByBranch = mergedActiveQuotas
                .filter { it.branchName in BranchOptions.ALL_BRANCHES }
                .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                .associateBy { it.branchName!! }

            val archivedBranches = BranchOptions.ALL_BRANCHES.map { branchName ->
                val summary = branchSummaryMap[branchName]
                val customQuota = latestCustomQuotasByBranch[branchName]
                val effectiveQuota = summary?.quota ?: customQuota ?: generalQuota
                val targetGlobal = effectiveQuota.targetGlobalSales.coerceIn(0.0, 1_000_000_000.0)
                val actualGlobal = (summary?.totalGlobalSales ?: 0.0).coerceIn(0.0, 1_000_000_000.0)
                val targetCredit = effectiveQuota.targetCreditSales.coerceIn(0.0, 1_000_000_000.0)
                val actualCredit = (summary?.totalCreditSales ?: 0.0).coerceIn(0.0, 1_000_000_000.0)
                val pct = summary?.completionPercent?.toLong()?.coerceIn(0L, 10_000L) ?: if (targetGlobal > 0.0) {
                    ((actualGlobal / targetGlobal) * 100.0).roundToLong().coerceIn(0L, 10_000L)
                } else {
                    0L
                }
                BiweeklyCycleBranchArchive(
                    branchName = branchName,
                    targetGlobalSales = targetGlobal,
                    actualGlobalSales = actualGlobal,
                    targetCreditSales = targetCredit,
                    actualCreditSales = actualCredit,
                    actualNewCustomers = (summary?.totalNewCustomers ?: 0L).coerceIn(0L, 100_000L),
                    actualAppMaOperations = (summary?.totalAppMaOps ?: 0L).coerceIn(0L, 100_000L),
                    actualNewCustomerRequests = (summary?.totalNewRequests ?: 0L).coerceIn(0L, 100_000L),
                    completionPercent = pct,
                    hasCustomQuota = summary?.hasCustomBranchQuota ?: (customQuota != null),
                    entriesCount = (summary?.periodEntries?.size?.toLong() ?: 0L).coerceIn(0L, 10_000L)
                )
            }

            val baseCalPeriodId = DateAndFormatters.biweeklyPeriodIdForIso(todayIso)
            val (calStart, calEnd) = DateAndFormatters.biweeklyRangeForIso(todayIso)
            val oldPeriodIds = mergedActiveQuotas.map { it.effectivePeriodId }.toSet() + activeCycle.periodId
            val candidateDocId = buildQuotaDocId(baseCalPeriodId, BranchOptions.ALL_BRANCHES_OPTION)
            val baseDocExists = runCatching {
                db.collection(COLLECTION_QUOTAS).document(candidateDocId).get().await().exists()
            }.getOrDefault(false)

            val useFreshCalendarPeriod = todayIso > activeCycle.endDate &&
                baseCalPeriodId !in oldPeriodIds &&
                !baseDocExists

            val newPeriodId: String
            val newStartDate: String
            val newEndDate: String
            if (useFreshCalendarPeriod) {
                newPeriodId = baseCalPeriodId
                newStartDate = calStart
                newEndDate = calEnd
            } else {
                val epochSec = System.currentTimeMillis() / 1000L
                newPeriodId = "${baseCalPeriodId}_r$epochSec".take(40)
                newStartDate = todayIso
                newEndDate = if (calEnd >= todayIso) calEnd else todayIso
            }
            val newTotalDays = DateAndFormatters.daysBetweenInclusive(newStartDate, newEndDate)
                .toLong()
                .coerceIn(1L, 31L)
            val newTitle = DateAndFormatters.defaultBiweeklyTitle().trim()
                .takeIf { it.length in 3..80 }
                ?: "Ciclo Quincenal"

            val sanitizedPeriodForId = activeCycle.periodId
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                .trim('_')
                .ifBlank { "ciclo" }
                .take(40)
            val archiveId = "archive_${sanitizedPeriodForId}_${System.currentTimeMillis()}".take(120)
            val archiveTitle = activeCycle.title.trim().takeIf { it.length in 3..80 }
                ?: "Ciclo Quincenal Cerrado"
            val closedStart = activeCycle.startDate.takeIf { it.length == 10 } ?: calStart
            val closedEnd = activeCycle.endDate.takeIf { it.length == 10 } ?: calEnd

            val batch = db.batch()
            val archiveRef = db.collection(COLLECTION_CYCLE_ARCHIVES).document(archiveId)
            val branchSummaryMaps = archivedBranches.map { b ->
                mapOf(
                    "branchName" to b.branchName,
                    "targetGlobalSales" to b.targetGlobalSales,
                    "actualGlobalSales" to b.actualGlobalSales,
                    "targetCreditSales" to b.targetCreditSales,
                    "actualCreditSales" to b.actualCreditSales,
                    "actualNewCustomers" to b.actualNewCustomers,
                    "actualAppMaOperations" to b.actualAppMaOperations,
                    "actualNewCustomerRequests" to b.actualNewCustomerRequests,
                    "completionPercent" to b.completionPercent,
                    "hasCustomQuota" to b.hasCustomQuota,
                    "entriesCount" to b.entriesCount
                )
            }
            batch.set(
                archiveRef,
                mapOf(
                    "archiveId" to archiveId,
                    "cyclePeriodId" to activeCycle.periodId.take(40),
                    "title" to archiveTitle,
                    "startDate" to closedStart,
                    "endDate" to closedEnd,
                    "closedOnDate" to todayIso,
                    "totalDays" to activeCycle.totalDays.toLong().coerceIn(1L, 31L),
                    "closedByUid" to adminUid,
                    "closedByEmail" to adminEmail,
                    "adminTargetGlobalSales" to adminTargetGlobal,
                    "adminActualGlobalSales" to adminActualGlobal,
                    "adminTargetCreditSales" to adminTargetCredit,
                    "adminActualCreditSales" to adminActualCredit,
                    "adminTargetNewCustomers" to adminTargetCustomers,
                    "adminActualNewCustomers" to adminActualCustomers,
                    "adminTargetAppMaOperations" to adminTargetAppMa,
                    "adminActualAppMaOperations" to adminActualAppMa,
                    "adminTargetNewCustomerRequests" to adminTargetRequests,
                    "adminActualNewCustomerRequests" to adminActualRequests,
                    "adminCompletionPercent" to adminCompletionPct,
                    "entriesCount" to entriesCount,
                    "branchSummaries" to branchSummaryMaps,
                    "closedAt" to FieldValue.serverTimestamp()
                )
            )

            // Deactivate all previously active quotas so closed-cycle entries no longer count.
            existingActiveSnap.documents.forEach { oldDoc ->
                batch.update(
                    oldDoc.reference,
                    mapOf(
                        "active" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            // Recreate the admin independent general quota intact for the new cycle.
            val newGeneralDocId = buildQuotaDocId(newPeriodId, BranchOptions.ALL_BRANCHES_OPTION)
            batch.set(
                db.collection(COLLECTION_QUOTAS).document(newGeneralDocId),
                mapOf(
                    "title" to newTitle,
                    "startDate" to newStartDate,
                    "endDate" to newEndDate,
                    "totalDays" to newTotalDays,
                    "periodId" to newPeriodId,
                    "branchName" to BranchOptions.ALL_BRANCHES_OPTION,
                    "targetGlobalSales" to generalQuota.targetGlobalSales.coerceIn(1.0, 1_000_000_000.0),
                    "targetCreditSales" to generalQuota.targetCreditSales.coerceIn(0.0, 1_000_000_000.0),
                    "targetNewCustomers" to generalQuota.targetNewCustomers.coerceIn(0L, 100_000L),
                    "targetAppMaOperations" to generalQuota.targetAppMaOperations.coerceIn(0L, 100_000L),
                    "targetNewCustomerRequests" to generalQuota.targetNewCustomerRequests.coerceIn(0L, 100_000L),
                    "active" to true,
                    "createdBy" to adminUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            // Preserve every custom branch quota intact for the new cycle.
            latestCustomQuotasByBranch.forEach { (branchName, branchQuota) ->
                val newBranchDocId = buildQuotaDocId(newPeriodId, branchName)
                batch.set(
                    db.collection(COLLECTION_QUOTAS).document(newBranchDocId),
                    mapOf(
                        "title" to newTitle,
                        "startDate" to newStartDate,
                        "endDate" to newEndDate,
                        "totalDays" to newTotalDays,
                        "periodId" to newPeriodId,
                        "branchName" to branchName,
                        "targetGlobalSales" to branchQuota.targetGlobalSales.coerceIn(1.0, 1_000_000_000.0),
                        "targetCreditSales" to branchQuota.targetCreditSales.coerceIn(0.0, 1_000_000_000.0),
                        "targetNewCustomers" to branchQuota.targetNewCustomers.coerceIn(0L, 100_000L),
                        "targetAppMaOperations" to branchQuota.targetAppMaOperations.coerceIn(0L, 100_000L),
                        "targetNewCustomerRequests" to branchQuota.targetNewCustomerRequests.coerceIn(0L, 100_000L),
                        "active" to true,
                        "createdBy" to adminUid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()

            Result.success(
                BiweeklyCycleArchive(
                    id = archiveId,
                    archiveId = archiveId,
                    cyclePeriodId = activeCycle.periodId,
                    title = archiveTitle,
                    startDate = closedStart,
                    endDate = closedEnd,
                    closedOnDate = todayIso,
                    totalDays = activeCycle.totalDays.toLong(),
                    closedByUid = adminUid,
                    closedByEmail = adminEmail,
                    adminTargetGlobalSales = adminTargetGlobal,
                    adminActualGlobalSales = adminActualGlobal,
                    adminTargetCreditSales = adminTargetCredit,
                    adminActualCreditSales = adminActualCredit,
                    adminTargetNewCustomers = adminTargetCustomers,
                    adminActualNewCustomers = adminActualCustomers,
                    adminTargetAppMaOperations = adminTargetAppMa,
                    adminActualAppMaOperations = adminActualAppMa,
                    adminTargetNewCustomerRequests = adminTargetRequests,
                    adminActualNewCustomerRequests = adminActualRequests,
                    adminCompletionPercent = adminCompletionPct,
                    entriesCount = entriesCount,
                    branchSummaries = archivedBranches
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Saves either:
     * - The administrator's independent general quota (`branchName == ALL_BRANCHES_OPTION` and `!applyToAllIndividualBranches`)
     * - Individual branch quotas (`applyToAllIndividualBranches == true` or a specific branch),
     * NEVER summing or overwriting the administrator's independent general quota with branch totals.
     */
    suspend fun saveIndependentBiweeklyQuota(
        title: String,
        startDate: String,
        endDate: String,
        totalDays: Long,
        targetGlobalSales: Double,
        targetCreditSales: Double,
        targetNewCustomers: Long,
        targetAppMaOperations: Long,
        targetNewCustomerRequests: Long,
        branchName: String = BranchOptions.ALL_BRANCHES_OPTION,
        periodId: String = DateAndFormatters.biweeklyPeriodIdForIso(startDate),
        applyToAllIndividualBranches: Boolean = false
    ): Result<Unit> {
        val path = COLLECTION_QUOTAS
        return try {
            val adminUid = requireUserId()
            val cleanTitle = title.trim()
            val cleanBranch = branchName.trim().ifBlank { BranchOptions.ALL_BRANCHES_OPTION }
            val cleanPeriodId = periodId.trim().ifBlank { DateAndFormatters.biweeklyPeriodIdForIso(startDate) }.take(40)
            require(cleanTitle.length in 3..80) { "El nombre de la quincena debe tener entre 3 y 80 caracteres." }
            require(targetGlobalSales > 0.0) { "La meta de venta global debe ser mayor a 0." }
            require(totalDays in 1L..31L) { "La duración debe estar entre 1 y 31 días." }
            require(cleanBranch == BranchOptions.ALL_BRANCHES_OPTION || cleanBranch in BranchOptions.ALL_BRANCHES) {
                "Selecciona una sucursal válida."
            }

            val existingActive = db.collection(path)
                .whereEqualTo("active", true)
                .get()
                .await()

            val targetBranches: List<String> = if (cleanBranch == BranchOptions.ALL_BRANCHES_OPTION && applyToAllIndividualBranches) {
                // Apply exclusively to the individual branches; keep the admin's general supervision quota untouched.
                BranchOptions.ALL_BRANCHES
            } else {
                listOf(cleanBranch)
            }

            val targetBranchesSet = targetBranches.toSet()
            val targetDocIds = targetBranches.associateWith { buildQuotaDocId(cleanPeriodId, it) }

            val batch = db.batch()
            existingActive.documents.forEach { doc ->
                val docBranch = doc.getString("branchName")?.takeIf { it.isNotBlank() }
                    ?: BranchOptions.ALL_BRANCHES_OPTION
                val docStart = doc.getString("startDate") ?: ""
                val docPeriod = doc.getString("periodId")?.takeIf { it.isNotBlank() }
                    ?: if (docStart.length == 10) DateAndFormatters.biweeklyPeriodIdForIso(docStart) else cleanPeriodId
                val isTargetDoc = doc.id in targetDocIds.values
                if (!isTargetDoc && (docPeriod != cleanPeriodId || docBranch in targetBranchesSet)) {
                    batch.update(
                        doc.reference,
                        mapOf(
                            "active" to false,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            val safeGlobal = targetGlobalSales.coerceIn(0.01, 1_000_000_000.0)
            val safeCredit = targetCreditSales.coerceIn(0.0, 1_000_000_000.0)
            val safeCustomers = targetNewCustomers.coerceIn(0L, 100_000L)
            val safeAppMa = targetAppMaOperations.coerceIn(0L, 100_000L)
            val safeRequests = targetNewCustomerRequests.coerceIn(0L, 100_000L)

            val existingById = existingActive.documents.associateBy { it.id }
            for (targetBranch in targetBranches) {
                val docId = targetDocIds.getValue(targetBranch)
                val docRef = db.collection(path).document(docId)
                val existingSnap = existingById[docId] ?: docRef.get().await()

                if (existingSnap.exists()) {
                    batch.update(
                        docRef,
                        mapOf(
                            "title" to cleanTitle,
                            "startDate" to startDate,
                            "endDate" to endDate,
                            "totalDays" to totalDays,
                            "periodId" to cleanPeriodId,
                            "branchName" to targetBranch,
                            "targetGlobalSales" to safeGlobal,
                            "targetCreditSales" to safeCredit,
                            "targetNewCustomers" to safeCustomers,
                            "targetAppMaOperations" to safeAppMa,
                            "targetNewCustomerRequests" to safeRequests,
                            "active" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.set(
                        docRef,
                        mapOf(
                            "title" to cleanTitle,
                            "startDate" to startDate,
                            "endDate" to endDate,
                            "totalDays" to totalDays,
                            "periodId" to cleanPeriodId,
                            "branchName" to targetBranch,
                            "targetGlobalSales" to safeGlobal,
                            "targetCreditSales" to safeCredit,
                            "targetNewCustomers" to safeCustomers,
                            "targetAppMaOperations" to safeAppMa,
                            "targetNewCustomerRequests" to safeRequests,
                            "active" to true,
                            "createdBy" to adminUid,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /**
     * Directly updates a single indicator target ("Meta") in the administrator's
     * independent general quota document in Firestore without affecting branch quotas.
     */
    suspend fun updateAdminGeneralIndicatorTarget(
        metric: SalesMetricType,
        newTargetValue: Double,
        baseGeneralQuota: BiweeklyQuota?
    ): Result<BiweeklyQuota> {
        val path = COLLECTION_QUOTAS
        return try {
            val adminUid = requireUserId()
            when (metric) {
                SalesMetricType.GLOBAL_SALES -> {
                    require(newTargetValue > 0.0 && newTargetValue <= 1_000_000_000.0) {
                        "La meta de Venta global debe ser mayor a 0."
                    }
                }
                SalesMetricType.CREDIT_SALES -> {
                    require(newTargetValue >= 0.0 && newTargetValue <= 1_000_000_000.0) {
                        "La meta de Venta a crédito debe ser mayor o igual a 0."
                    }
                }
                SalesMetricType.NEW_CUSTOMERS,
                SalesMetricType.APP_MA_OPS,
                SalesMetricType.NEW_CUSTOMER_REQUESTS -> {
                    require(newTargetValue >= 0.0 && newTargetValue <= 100_000.0) {
                        "El objetivo de ${metric.titleEs} debe estar entre 0 y 100,000."
                    }
                }
            }

            val fallback = baseGeneralQuota ?: ProjectionCalculator.defaultGeneralQuotaPlaceholder()
            val periodId = fallback.effectivePeriodId
            val docId = buildQuotaDocId(periodId, BranchOptions.ALL_BRANCHES_OPTION)
            val docRef = db.collection(path).document(docId)
            val existingSnap = docRef.get().await()
            val existingObj = if (existingSnap.exists()) {
                existingSnap.toObject(BiweeklyQuota::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }

            val seed = existingObj ?: fallback
            val updated = when (metric) {
                SalesMetricType.GLOBAL_SALES -> seed.copy(
                    id = docId,
                    branchName = BranchOptions.ALL_BRANCHES_OPTION,
                    periodId = periodId,
                    targetGlobalSales = newTargetValue
                )
                SalesMetricType.CREDIT_SALES -> seed.copy(
                    id = docId,
                    branchName = BranchOptions.ALL_BRANCHES_OPTION,
                    periodId = periodId,
                    targetCreditSales = newTargetValue
                )
                SalesMetricType.NEW_CUSTOMERS -> seed.copy(
                    id = docId,
                    branchName = BranchOptions.ALL_BRANCHES_OPTION,
                    periodId = periodId,
                    targetNewCustomers = newTargetValue.roundToLong().coerceIn(0L, 100_000L)
                )
                SalesMetricType.APP_MA_OPS -> seed.copy(
                    id = docId,
                    branchName = BranchOptions.ALL_BRANCHES_OPTION,
                    periodId = periodId,
                    targetAppMaOperations = newTargetValue.roundToLong().coerceIn(0L, 100_000L)
                )
                SalesMetricType.NEW_CUSTOMER_REQUESTS -> seed.copy(
                    id = docId,
                    branchName = BranchOptions.ALL_BRANCHES_OPTION,
                    periodId = periodId,
                    targetNewCustomerRequests = newTargetValue.roundToLong().coerceIn(0L, 100_000L)
                )
            }

            if (existingSnap.exists()) {
                val fieldUpdate = when (metric) {
                    SalesMetricType.GLOBAL_SALES -> "targetGlobalSales" to updated.targetGlobalSales
                    SalesMetricType.CREDIT_SALES -> "targetCreditSales" to updated.targetCreditSales
                    SalesMetricType.NEW_CUSTOMERS -> "targetNewCustomers" to updated.targetNewCustomers
                    SalesMetricType.APP_MA_OPS -> "targetAppMaOperations" to updated.targetAppMaOperations
                    SalesMetricType.NEW_CUSTOMER_REQUESTS -> "targetNewCustomerRequests" to updated.targetNewCustomerRequests
                }
                docRef.update(
                    mapOf(
                        fieldUpdate,
                        "branchName" to BranchOptions.ALL_BRANCHES_OPTION,
                        "periodId" to periodId,
                        "active" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val (defaultStart, defaultEnd) = DateAndFormatters.defaultBiweeklyRange()
                val start = updated.startDate.takeIf { it.length == 10 } ?: defaultStart
                val end = updated.endDate.takeIf { it.length == 10 } ?: defaultEnd
                val days = updated.totalDays.coerceIn(1L, 31L)
                val title = updated.title.trim().takeIf { it.length in 3..80 }
                    ?: DateAndFormatters.defaultBiweeklyTitle()

                docRef.set(
                    mapOf(
                        "title" to title,
                        "startDate" to start,
                        "endDate" to end,
                        "totalDays" to days,
                        "periodId" to periodId,
                        "branchName" to BranchOptions.ALL_BRANCHES_OPTION,
                        "targetGlobalSales" to updated.targetGlobalSales.coerceAtLeast(1.0),
                        "targetCreditSales" to updated.targetCreditSales.coerceAtLeast(0.0),
                        "targetNewCustomers" to updated.targetNewCustomers.coerceIn(0L, 100_000L),
                        "targetAppMaOperations" to updated.targetAppMaOperations.coerceIn(0L, 100_000L),
                        "targetNewCustomerRequests" to updated.targetNewCustomerRequests.coerceIn(0L, 100_000L),
                        "active" to true,
                        "createdBy" to adminUid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "$path/${metric.name}")
            Result.failure(e)
        }
    }

    private fun handleFlowError(error: Throwable, op: OperationType, path: String, uid: String) {
        if (error is CancellationException) return
        handleFirestoreError(error, op, "$path (uid=$uid)")
    }

    companion object {
        const val COLLECTION_QUOTAS = "biweekly_quotas"
        const val COLLECTION_CYCLE_ARCHIVES = "biweekly_cycle_archives"
    }
}
