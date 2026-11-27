package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val labelEs: String) {
    ADMIN("Administrador"),
    EMPLOYEE("Empleado");

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: EMPLOYEE
    }
}

enum class TaskType(val labelEs: String) {
    DAILY("Diaria"),
    EXTRAORDINARY("Extraordinaria");

    companion object {
        fun fromRaw(raw: String?): TaskType =
            entries.firstOrNull { it.name == raw } ?: DAILY
    }
}

enum class TaskPriority(val labelEs: String) {
    NORMAL("Normal"),
    URGENT("Urgente");

    companion object {
        fun fromRaw(raw: String?): TaskPriority =
            entries.firstOrNull { it.name == raw } ?: NORMAL
    }
}

enum class TaskStatus(val labelEs: String) {
    PENDING("Pendiente"),
    COMPLETED("Completada");

    companion object {
        fun fromRaw(raw: String?): TaskStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class SalesMetricType(
    val titleEs: String,
    val shortTitleEs: String,
    val unitLabel: String,
    val isCurrency: Boolean
) {
    GLOBAL_SALES("Venta global", "Venta global", "MXN", true),
    CREDIT_SALES("Venta a crédito", "Crédito", "MXN", true),
    NEW_CUSTOMERS("Clientes nuevos", "Clientes", "uds", false),
    APP_MA_OPS("Operaciones por app MA", "App MA", "uds", false),
    NEW_CUSTOMER_REQUESTS("Solicitudes de cliente nuevo", "Solicitudes", "uds", false)
}

object BranchOptions {
    const val ALL_BRANCHES_OPTION = "Todas las Sucursales"

    val ALL_BRANCHES = listOf(
        "Sucursal 87",
        "Sucursal 107",
        "Sucursal 109",
        "Sucursal 110",
        "Sucursal 112",
        "Sucursal 148"
    )
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val branchName: String? = null,
    val role: String = UserRole.EMPLOYEE.name,
    val confirmedQuotaId: String? = null,
    val lastChecklistResetAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedRole: UserRole get() = UserRole.fromRaw(role)
    val effectiveBranchLabel: String get() = branchName?.takeIf { it.isNotBlank() } ?: displayName
}

@Keep
@IgnoreExtraProperties
data class AssignedTask(
    @DocumentId val id: String = "",
    val assigneeId: String = "",
    val assigneeName: String = "",
    val createdBy: String = "",
    val title: String = "",
    val description: String = "",
    val taskType: String = TaskType.DAILY.name,
    val priority: String = TaskPriority.NORMAL.name,
    val dueDate: String = "",
    val dueTime: String? = null,
    val deadlineLabel: String? = null,
    val status: String = TaskStatus.PENDING.name,
    val hasPhotoEvidence: Boolean = false,
    val evidenceId: String? = null,
    val completedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedType: TaskType get() = TaskType.fromRaw(taskType)
    val parsedPriority: TaskPriority get() = TaskPriority.fromRaw(priority)
    val parsedStatus: TaskStatus get() = TaskStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class TaskEvidence(
    @DocumentId val id: String = "",
    val taskId: String = "",
    val uploaderId: String = "",
    val uploaderName: String = "",
    val taskTitle: String = "",
    val imageBase64: String = "",
    val fileCount: Long = 1L,
    val primaryFileName: String? = null,
    @ServerTimestamp val capturedAt: Timestamp? = null
) {
    val resolvedFileCount: Int get() = fileCount.toInt().coerceIn(1, 5)
}

data class TaskEvidenceAttachmentFile(
    val fileIndex: Int,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Int,
    val base64Chunks: List<String>
) {
    val isImage: Boolean get() = mimeType.startsWith("image/", ignoreCase = true)
}

@Keep
@IgnoreExtraProperties
data class TaskNotification(
    @DocumentId val id: String = "",
    val recipientId: String = "",
    val taskId: String = "",
    val title: String = "",
    val message: String = "",
    val acknowledged: Boolean = false,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BiweeklyQuota(
    @DocumentId val id: String = "",
    val title: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val totalDays: Long = 15L,
    val periodId: String = "",
    val branchName: String? = null,
    val targetGlobalSales: Double = 0.0,
    val targetCreditSales: Double = 0.0,
    val targetNewCustomers: Long = 0L,
    val targetAppMaOperations: Long = 0L,
    val targetNewCustomerRequests: Long = 0L,
    val active: Boolean = true,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val effectiveBranchName: String
        get() = branchName?.takeIf { it.isNotBlank() } ?: BranchOptions.ALL_BRANCHES_OPTION

    val effectivePeriodId: String
        get() = periodId.ifBlank {
            if (startDate.length == 10) com.example.utils.DateAndFormatters.biweeklyPeriodIdForIso(startDate)
            else com.example.utils.DateAndFormatters.currentBiweeklyPeriodId()
        }
}

@Keep
@IgnoreExtraProperties
data class DailySalesEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val branchName: String? = null,
    val quotaId: String = "",
    val dateString: String = "",
    val globalSales: Double = 0.0,
    val creditSales: Double = 0.0,
    val newCustomers: Long = 0L,
    val appMaOperations: Long = 0L,
    val newCustomerRequests: Long = 0L,
    val notes: String = "",
    val attachmentName: String? = null,
    val attachmentMimeType: String? = null,
    val attachmentBase64: String? = null,
    val attachmentChunkCount: Long = 0L,
    val attachmentSizeBytes: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface ClosingAttachmentDraft {
    data object Unchanged : ClosingAttachmentDraft
    data object Removed : ClosingAttachmentDraft
    data class Selected(val attachment: com.example.utils.EncodedAttachment) : ClosingAttachmentDraft
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class ProjectionPace {
    ON_TRACK,
    MODERATE_RISK,
    BEHIND
}

data class MetricProgress(
    val metric: SalesMetricType,
    val actualTotal: Double,
    val targetTotal: Double,
    val projectedTotal: Double,
    val progressRatio: Float,
    val projectedRatio: Float,
    val dailyAverage: Double,
    val requiredDailyPace: Double,
    val pace: ProjectionPace
)

data class ChartSeries(
    val metric: SalesMetricType,
    val totalDays: Int,
    val elapsedDays: Int,
    val targetValue: Double,
    val actualCumulativeByDay: List<Double>,
    val projectedCumulativeByDay: List<Double>
)

data class SalesProjectionSummary(
    val quota: BiweeklyQuota,
    val daysElapsed: Int,
    val totalDays: Int,
    val metrics: List<MetricProgress>,
    val entriesCount: Int,
    val todayEntry: DailySalesEntry?,
    val recentEntries: List<DailySalesEntry> = emptyList()
)

data class BranchSalesSummary(
    val branchName: String,
    val quota: BiweeklyQuota,
    val hasCustomBranchQuota: Boolean,
    val todayEntry: DailySalesEntry?,
    val periodEntries: List<DailySalesEntry>,
    val totalGlobalSales: Double,
    val totalCreditSales: Double,
    val totalNewCustomers: Long,
    val totalAppMaOps: Long,
    val totalNewRequests: Long,
    val progressRatio: Float,
    val completionPercent: Int
)

fun resolveQuotaForBranch(
    branchName: String,
    quotas: List<BiweeklyQuota>,
    currentPeriodId: String = com.example.utils.DateAndFormatters.currentBiweeklyPeriodId()
): Pair<BiweeklyQuota?, Boolean> {
    if (quotas.isEmpty()) return null to false
    val currentPeriodQuotas = quotas.filter { it.effectivePeriodId == currentPeriodId }
    val exactBranchCurrent = if (branchName in BranchOptions.ALL_BRANCHES) {
        currentPeriodQuotas.firstOrNull { it.branchName == branchName }
    } else {
        null
    }
    if (exactBranchCurrent != null) return exactBranchCurrent to true

    val generalCurrent = currentPeriodQuotas.firstOrNull {
        it.branchName.isNullOrBlank() || it.branchName == BranchOptions.ALL_BRANCHES_OPTION
    }
    if (generalCurrent != null) return generalCurrent to false

    val exactBranchAny = if (branchName in BranchOptions.ALL_BRANCHES) {
        quotas.firstOrNull { it.branchName == branchName }
    } else {
        null
    }
    if (exactBranchAny != null) return exactBranchAny to true

    val fallbackGeneral = quotas.firstOrNull {
        it.branchName.isNullOrBlank() || it.branchName == BranchOptions.ALL_BRANCHES_OPTION
    } ?: quotas.firstOrNull()
    return fallbackGeneral to false
}

data class DailyClosingSaveResult(
    val branchAccumulatedGlobalSales: Double,
    val branchPeriodEntriesCount: Int
)

fun buildBranchSalesSummaries(
    branches: List<String>,
    quotas: List<BiweeklyQuota>,
    entries: List<DailySalesEntry>,
    teamUsers: List<UserProfile>,
    todayIso: String = com.example.utils.DateAndFormatters.todayIso(),
    currentPeriodId: String = com.example.utils.DateAndFormatters.currentBiweeklyPeriodId()
): List<BranchSalesSummary> {
    if (quotas.isEmpty()) return emptyList()
    val (periodStartIso, periodEndIso) = com.example.utils.DateAndFormatters.biweeklyRangeForIso(todayIso)
    val uidToBranch = teamUsers
        .mapNotNull { user ->
            val branch = user.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
            if (branch != null) user.userId to branch else null
        }
        .toMap()

    return branches.mapNotNull { branch ->
        val (resolvedQuota, hasCustom) = resolveQuotaForBranch(branch, quotas, currentPeriodId)
        val quota = resolvedQuota ?: return@mapNotNull null

        val branchEntries = entries
            .filter { entry ->
                val resolvedBranch = entry.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
                    ?: uidToBranch[entry.userId]
                    ?: entry.userName.takeIf { it in BranchOptions.ALL_BRANCHES }
                    ?: entry.userName
                val matchesBranch = resolvedBranch == branch
                val inCalendarQuincena = entry.dateString >= periodStartIso && entry.dateString <= periodEndIso
                matchesBranch && inCalendarQuincena
            }
            .sortedWith(
                compareByDescending<DailySalesEntry> { it.dateString }
                    .thenByDescending { it.updatedAt?.seconds ?: 0L }
                    .thenByDescending { it.quotaId == quota.id }
            )
            .distinctBy { it.dateString }

        val todayEntry = branchEntries.firstOrNull { it.dateString == todayIso }
        val totalGlobal = branchEntries.sumOf { it.globalSales }
        val totalCredit = branchEntries.sumOf { it.creditSales }
        val totalCustomers = branchEntries.sumOf { it.newCustomers }
        val totalAppMa = branchEntries.sumOf { it.appMaOperations }
        val totalRequests = branchEntries.sumOf { it.newCustomerRequests }

        val target = quota.targetGlobalSales
        val ratio = if (target > 0.0) (totalGlobal / target).toFloat().coerceAtLeast(0f) else 0f
        val pct = kotlin.math.round(ratio * 100f).toInt().coerceAtLeast(0)

        BranchSalesSummary(
            branchName = branch,
            quota = quota,
            hasCustomBranchQuota = hasCustom,
            todayEntry = todayEntry,
            periodEntries = branchEntries,
            totalGlobalSales = totalGlobal,
            totalCreditSales = totalCredit,
            totalNewCustomers = totalCustomers,
            totalAppMaOps = totalAppMa,
            totalNewRequests = totalRequests,
            progressRatio = ratio,
            completionPercent = pct
        )
    }
}

data class BranchTaskSummary(
    val branchName: String,
    val tasks: List<AssignedTask>,
    val completedCount: Int,
    val totalCount: Int,
    val allTasksCompleted: Boolean
)

fun buildBranchTaskSummaries(
    teamUsers: List<UserProfile>,
    tasks: List<AssignedTask>
): List<BranchTaskSummary> {
    val uidToBranch = teamUsers
        .mapNotNull { user ->
            val branch = user.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
            if (branch != null) user.userId to branch else null
        }
        .toMap()

    val tasksByBranch = tasks.groupBy { task ->
        uidToBranch[task.assigneeId]
            ?: task.assigneeName.takeIf { it in BranchOptions.ALL_BRANCHES }
            ?: task.assigneeName.ifBlank { "Sin sucursal" }
    }

    val officialSummaries = BranchOptions.ALL_BRANCHES.map { branch ->
        val branchTasks = tasksByBranch[branch].orEmpty().sortedWith(
            compareBy<AssignedTask> { it.parsedStatus == TaskStatus.COMPLETED }
                .thenByDescending { it.parsedType == TaskType.EXTRAORDINARY }
                .thenBy { it.title.lowercase() }
        )
        val completedCount = branchTasks.count { it.parsedStatus == TaskStatus.COMPLETED }
        val totalCount = branchTasks.size
        BranchTaskSummary(
            branchName = branch,
            tasks = branchTasks,
            completedCount = completedCount,
            totalCount = totalCount,
            allTasksCompleted = totalCount > 0 && completedCount == totalCount
        )
    }

    val extraKeys = tasksByBranch.keys.filter { it !in BranchOptions.ALL_BRANCHES }.sorted()
    val extraSummaries = extraKeys.map { groupName ->
        val groupTasks = tasksByBranch[groupName].orEmpty().sortedWith(
            compareBy<AssignedTask> { it.parsedStatus == TaskStatus.COMPLETED }
                .thenBy { it.title.lowercase() }
        )
        val completedCount = groupTasks.count { it.parsedStatus == TaskStatus.COMPLETED }
        val totalCount = groupTasks.size
        BranchTaskSummary(
            branchName = groupName,
            tasks = groupTasks,
            completedCount = completedCount,
            totalCount = totalCount,
            allTasksCompleted = totalCount > 0 && completedCount == totalCount
        )
    }

    return officialSummaries + extraSummaries
}

data class DayTaskGroup(
    val dateIso: String,
    val title: String,
    val isToday: Boolean,
    val tasks: List<AssignedTask>,
    val completedCount: Int,
    val totalCount: Int
) {
    val allCompleted: Boolean get() = totalCount > 0 && completedCount == totalCount
}

fun buildDayTaskGroups(
    tasks: List<AssignedTask>,
    todayIso: String = com.example.utils.DateAndFormatters.todayIso()
): List<DayTaskGroup> {
    if (tasks.isEmpty()) return emptyList()
    val grouped = tasks.groupBy { task ->
        com.example.utils.DateAndFormatters.extractTaskDayIso(task)
    }
    val sortedDays = grouped.keys.sortedWith(
        compareByDescending<String> { it == todayIso }.thenByDescending { it }
    )
    return sortedDays.map { dayIso ->
        val dayTasks = grouped[dayIso].orEmpty().sortedWith(
            compareBy<AssignedTask> { it.parsedStatus == TaskStatus.COMPLETED }
                .thenByDescending { it.parsedType == TaskType.EXTRAORDINARY }
                .thenByDescending { it.parsedPriority == TaskPriority.URGENT }
                .thenBy { it.dueTime ?: "23:59" }
                .thenBy { it.title.lowercase() }
        )
        val completed = dayTasks.count { it.parsedStatus == TaskStatus.COMPLETED }
        DayTaskGroup(
            dateIso = dayIso,
            title = com.example.utils.DateAndFormatters.formatDayGroupHeader(dayIso, todayIso),
            isToday = dayIso == todayIso,
            tasks = dayTasks,
            completedCount = completed,
            totalCount = dayTasks.size
        )
    }
}
