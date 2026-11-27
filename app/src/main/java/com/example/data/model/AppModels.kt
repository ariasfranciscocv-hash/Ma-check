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

enum class WeekdayOption(
    val code: String,
    val labelEs: String,
    val shortLabelEs: String,
    val calendarDay: Int
) {
    MONDAY("MON", "Lunes", "Lun", java.util.Calendar.MONDAY),
    TUESDAY("TUE", "Martes", "Mar", java.util.Calendar.TUESDAY),
    WEDNESDAY("WED", "Miércoles", "Mié", java.util.Calendar.WEDNESDAY),
    THURSDAY("THU", "Jueves", "Jue", java.util.Calendar.THURSDAY),
    FRIDAY("FRI", "Viernes", "Vie", java.util.Calendar.FRIDAY),
    SATURDAY("SAT", "Sábado", "Sáb", java.util.Calendar.SATURDAY),
    SUNDAY("SUN", "Domingo", "Dom", java.util.Calendar.SUNDAY);

    companion object {
        val ALL_CODES: List<String> = entries.map { it.code }
        val MON_TO_SAT_CODES: List<String> = entries.filter { it != SUNDAY }.map { it.code }

        fun fromCode(raw: String?): WeekdayOption? =
            entries.firstOrNull { it.code.equals(raw?.trim(), ignoreCase = true) }

        fun fromCalendarDay(calendarDay: Int): WeekdayOption? =
            entries.firstOrNull { it.calendarDay == calendarDay }
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
data class EmployeeIdentity(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val fullName: String = "",
    val payrollNumber: String = "",
    val payrollKey: String = "",
    val branchName: String? = null,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val hasIdentityCore: Boolean
        get() = userId.isNotBlank() && fullName.isNotBlank() && payrollNumber.isNotBlank()

    val hasAssignedBranch: Boolean
        get() = !branchName.isNullOrBlank() && branchName in BranchOptions.ALL_BRANCHES

    val isComplete: Boolean
        get() = active && hasIdentityCore && hasAssignedBranch

    val requiresBranchOnlyReassignment: Boolean
        get() = active && hasIdentityCore && !hasAssignedBranch

    val displayPayroll: String
        get() = EmployeeIdentityValidation.formatPayrollForDisplay(payrollNumber)

    val formattedTopBarTag: String
        get() = when {
            displayPayroll.isNotBlank() && !branchName.isNullOrBlank() ->
                "Nómina: $displayPayroll | $branchName"
            displayPayroll.isNotBlank() ->
                "Nómina: $displayPayroll"
            !branchName.isNullOrBlank() ->
                branchName
            else -> "Sin asignar"
        }

    val formattedMovementAuthor: String
        get() = when {
            displayPayroll.isNotBlank() && fullName.isNotBlank() ->
                "Nómina $displayPayroll · $fullName"
            fullName.isNotBlank() -> fullName
            else -> branchName ?: "Sucursal"
        }
}

object EmployeeIdentityValidation {
    const val PAYROLL_PREFIX = "E"
    const val PAYROLL_DIGITS_LENGTH = 6
    private val rawValidPayrollRegex = Regex("^(?:[Ee])?[0-9]{6}$")
    private val digitsOnlyRegex = Regex("^[0-9]{6}$")
    private val fullPayrollRegex = Regex("^E[0-9]{6}$")

    fun filterPayrollDigits(raw: String): String =
        raw.filter { it in '0'..'9' }.take(PAYROLL_DIGITS_LENGTH)

    fun extractPayrollDigits(raw: String): String {
        val trimmed = raw.trim().removePrefix("#").trim()
        val withoutPrefix = if (trimmed.startsWith("e", ignoreCase = true)) {
            trimmed.drop(1)
        } else {
            trimmed
        }
        return filterPayrollDigits(withoutPrefix)
    }

    fun formatFullPayrollFromDigits(digits: String): String {
        val cleanDigits = filterPayrollDigits(digits)
        return if (cleanDigits.isEmpty()) "" else "$PAYROLL_PREFIX$cleanDigits"
    }

    fun cleanPayrollInput(raw: String): String {
        val trimmed = raw.trim().removePrefix("#").trim()
        return when {
            digitsOnlyRegex.matches(trimmed) -> "$PAYROLL_PREFIX$trimmed"
            trimmed.length == PAYROLL_DIGITS_LENGTH + 1 &&
                trimmed.startsWith("e", ignoreCase = true) &&
                digitsOnlyRegex.matches(trimmed.drop(1)) -> "$PAYROLL_PREFIX${trimmed.drop(1)}"
            else -> trimmed
        }
    }

    fun formatPayrollForDisplay(raw: String?): String {
        val trimmed = raw?.trim()?.removePrefix("#")?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        return when {
            digitsOnlyRegex.matches(trimmed) -> "$PAYROLL_PREFIX$trimmed"
            fullPayrollRegex.matches(trimmed.uppercase()) -> trimmed.uppercase()
            else -> trimmed
        }
    }

    fun normalizePayrollKey(raw: String): String =
        cleanPayrollInput(raw).uppercase()

    fun validateFullRegistration(
        fullName: String,
        payrollNumberRaw: String,
        branchName: String
    ): String? {
        val cleanName = fullName.trim()
        val trimmedPayroll = payrollNumberRaw.trim().removePrefix("#").trim()
        return when {
            cleanName.length < 3 -> "Ingresa tu nombre completo (mínimo 3 caracteres)."
            cleanName.length > 80 -> "El nombre completo no puede superar 80 caracteres."
            trimmedPayroll.isEmpty() ->
                "El número de Nómina es obligatorio (6 dígitos después de la E)."
            !rawValidPayrollRegex.matches(trimmedPayroll) ->
                "La Nómina debe tener exactamente 6 dígitos numéricos después de la E (ej. E019753)."
            branchName !in BranchOptions.ALL_BRANCHES ->
                "Selecciona una sucursal asignada válida."
            else -> null
        }
    }
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
    val templateId: String? = null,
    val completedByUid: String? = null,
    val completedByPayroll: String? = null,
    val completedByName: String? = null,
    val completedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedType: TaskType get() = TaskType.fromRaw(taskType)
    val parsedPriority: TaskPriority get() = TaskPriority.fromRaw(priority)
    val parsedStatus: TaskStatus get() = TaskStatus.fromRaw(status)

    val formattedCompletedByLabel: String?
        get() {
            val displayPayroll = EmployeeIdentityValidation.formatPayrollForDisplay(completedByPayroll)
            return when {
                displayPayroll.isNotBlank() && !completedByName.isNullOrBlank() ->
                    "Nómina $displayPayroll · $completedByName"
                displayPayroll.isNotBlank() ->
                    "Nómina $displayPayroll"
                !completedByName.isNullOrBlank() ->
                    completedByName
                else -> null
            }
        }
}

@Keep
@IgnoreExtraProperties
data class BranchTaskCompletion(
    @DocumentId val id: String = "",
    val lockId: String = "",
    val branchName: String = "",
    val dayIso: String = "",
    val periodId: String = "",
    val taskKey: String = "",
    val taskTitle: String = "",
    val templateId: String? = null,
    val taskId: String = "",
    val evidenceId: String = "",
    val completedByUid: String = "",
    val completedByPayroll: String = "",
    val completedByName: String = "",
    @ServerTimestamp val completedAt: Timestamp? = null
) {
    val formattedCompletedByLabel: String
        get() {
            val displayPayroll = EmployeeIdentityValidation.formatPayrollForDisplay(completedByPayroll)
            return when {
                displayPayroll.isNotBlank() && completedByName.isNotBlank() ->
                    "Nómina $displayPayroll · $completedByName"
                displayPayroll.isNotBlank() ->
                    "Nómina $displayPayroll"
                completedByName.isNotBlank() ->
                    completedByName
                else -> branchName
            }
        }
}

data class EmployeeBranchParticipationStats(
    val branchName: String,
    val periodTitle: String,
    val periodStartIso: String,
    val periodEndIso: String,
    val totalBranchTasks: Int,
    val completedByMyPayroll: Int,
    val completedByOtherPayrolls: Int,
    val pendingTasks: Int,
    val myParticipationPercent: Float
) {
    val totalCompletedByBranch: Int get() = completedByMyPayroll + completedByOtherPayrolls
}

object BranchTaskLockHelper {

    fun sanitizeIdPart(raw: String): String =
        raw.lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ü", "u")
            .replace("ñ", "n")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(56)

    fun extractTaskKeyForBranchLock(task: AssignedTask): String {
        val slug = com.example.utils.DateAndFormatters.extractScheduledSlug(task.id)
            ?.let(::sanitizeIdPart)
            ?.takeIf { it.isNotBlank() }
        if (slug != null) return "d_$slug"

        val tpl = task.templateId?.trim()?.removePrefix("tpl_")
            ?.let(::sanitizeIdPart)
            ?.takeIf { it.isNotBlank() }
        if (tpl != null) return "d_$tpl"

        val cleanTitle = sanitizeIdPart(task.title).ifBlank { "tarea" }
        return if (task.parsedType == TaskType.DAILY) {
            "d_$cleanTitle"
        } else {
            val datePart = task.dueDate.trim().replace("-", "").take(12).ifBlank { "nodate" }
            "e_${cleanTitle}_$datePart"
        }
    }

    fun buildLockId(branchName: String, dayIso: String, taskKey: String): String {
        val branchSlug = sanitizeIdPart(branchName).ifBlank { "sucursal" }
        val cleanDay = dayIso.trim().take(10).ifBlank { "nodate" }
        val cleanKey = sanitizeIdPart(taskKey).ifBlank { "tarea" }
        return "lock_${branchSlug}_${cleanDay}_$cleanKey".take(128)
    }

    fun buildLockIdForTask(branchName: String, task: AssignedTask): String {
        val dayIso = com.example.utils.DateAndFormatters.extractTaskDayIso(task)
        val taskKey = extractTaskKeyForBranchLock(task)
        return buildLockId(branchName, dayIso, taskKey)
    }

    fun resolveTaskBranch(
        task: AssignedTask,
        uidToBranch: Map<String, String>
    ): String? {
        uidToBranch[task.assigneeId]?.takeIf { it in BranchOptions.ALL_BRANCHES }?.let { return it }
        return task.assigneeName.trim().takeIf { it in BranchOptions.ALL_BRANCHES }
    }

    fun mergeTasksWithBranchCompletions(
        tasks: List<AssignedTask>,
        completions: List<BranchTaskCompletion>,
        isAdmin: Boolean,
        employeeBranch: String?,
        teamUsers: List<UserProfile>
    ): List<AssignedTask> {
        val uidToBranch = teamUsers
            .mapNotNull { user ->
                val branch = user.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
                if (branch != null) user.userId to branch else null
            }
            .toMap()

        val completionsByLockId = completions.associateBy { c ->
            c.lockId.ifBlank {
                c.id.ifBlank { buildLockId(c.branchName, c.dayIso, c.taskKey) }
            }
        }

        val enriched = tasks.map { task ->
            val branch = if (!isAdmin) {
                employeeBranch?.takeIf { it in BranchOptions.ALL_BRANCHES }
                    ?: resolveTaskBranch(task, uidToBranch)
            } else {
                resolveTaskBranch(task, uidToBranch)
            }
            if (branch == null) {
                task
            } else {
                val lockId = buildLockIdForTask(branch, task)
                val completion = completionsByLockId[lockId]
                if (completion != null) {
                    task.copy(
                        status = TaskStatus.COMPLETED.name,
                        hasPhotoEvidence = true,
                        evidenceId = completion.evidenceId.ifBlank { task.evidenceId },
                        completedByUid = completion.completedByUid.ifBlank { task.completedByUid },
                        completedByPayroll = completion.completedByPayroll.ifBlank { task.completedByPayroll },
                        completedByName = completion.completedByName.ifBlank { task.completedByName },
                        completedAt = completion.completedAt ?: task.completedAt
                    )
                } else {
                    task
                }
            }
        }

        if (!isAdmin) return enriched

        // For admin views, collapse duplicate per-user copies of the same branch task on the same day,
        // prioritizing the completed copy (with payroll/evidence metadata).
        val seenKeys = LinkedHashMap<String, AssignedTask>()
        val sortedForDedup = enriched.sortedWith(
            compareByDescending<AssignedTask> { it.parsedStatus == TaskStatus.COMPLETED }
                .thenByDescending { !it.completedByPayroll.isNullOrBlank() }
                .thenByDescending { !it.evidenceId.isNullOrBlank() }
        )
        for (task in sortedForDedup) {
            val branch = resolveTaskBranch(task, uidToBranch)
            val dedupKey = if (branch != null) {
                buildLockIdForTask(branch, task)
            } else {
                "uid_${task.id}"
            }
            if (dedupKey !in seenKeys) {
                seenKeys[dedupKey] = task
            }
        }
        return seenKeys.values.toList()
    }

    fun computeEmployeeBranchParticipationStats(
        identity: EmployeeIdentity,
        tasks: List<AssignedTask>,
        completions: List<BranchTaskCompletion>,
        activeQuota: BiweeklyQuota?,
        todayIso: String = com.example.utils.DateAndFormatters.todayIso()
    ): EmployeeBranchParticipationStats {
        val branchName = identity.branchName?.takeIf { it.isNotBlank() } ?: "Sin sucursal"
        val (periodStartIso, periodEndIso) = activeQuota
            ?.takeIf { it.startDate.length == 10 && it.endDate.length == 10 && todayIso >= it.startDate }
            ?.let { it.startDate to maxOf(it.endDate, todayIso) }
            ?: com.example.utils.DateAndFormatters.biweeklyRangeForIso(todayIso)
        val periodTitle = activeQuota?.title?.takeIf { it.isNotBlank() }
            ?: com.example.utils.DateAndFormatters.defaultBiweeklyTitle()

        data class BranchTaskSlot(
            val completed: Boolean,
            val completedByPayroll: String?,
            val completedByUid: String?
        )

        val slots = LinkedHashMap<String, BranchTaskSlot>()

        tasks.forEach { task ->
            val dayIso = com.example.utils.DateAndFormatters.extractTaskDayIso(task)
            if (dayIso in periodStartIso..periodEndIso) {
                val taskKey = extractTaskKeyForBranchLock(task)
                val slotKey = "${dayIso}|$taskKey"
                val existing = slots[slotKey]
                val isDone = task.parsedStatus == TaskStatus.COMPLETED
                if (existing == null || (!existing.completed && isDone)) {
                    slots[slotKey] = BranchTaskSlot(
                        completed = isDone,
                        completedByPayroll = task.completedByPayroll,
                        completedByUid = task.completedByUid ?: if (isDone) task.assigneeId else null
                    )
                }
            }
        }

        completions.forEach { comp ->
            if (comp.branchName == branchName && comp.dayIso in periodStartIso..periodEndIso) {
                val cleanKey = sanitizeIdPart(comp.taskKey).ifBlank { "tarea" }
                val slotKey = "${comp.dayIso}|$cleanKey"
                slots[slotKey] = BranchTaskSlot(
                    completed = true,
                    completedByPayroll = comp.completedByPayroll,
                    completedByUid = comp.completedByUid
                )
            }
        }

        var myCompleted = 0
        var othersCompleted = 0
        var pending = 0
        val myPayroll = EmployeeIdentityValidation.formatPayrollForDisplay(identity.payrollNumber)
        val myUid = identity.userId.trim()

        slots.values.forEach { slot ->
            if (!slot.completed) {
                pending++
            } else {
                val slotPayroll = EmployeeIdentityValidation.formatPayrollForDisplay(slot.completedByPayroll)
                val matchesPayroll = myPayroll.isNotBlank() &&
                    slotPayroll.equals(myPayroll, ignoreCase = true)
                val matchesUidFallback = slotPayroll.isBlank() &&
                    myUid.isNotBlank() &&
                    slot.completedByUid == myUid
                if (matchesPayroll || matchesUidFallback) {
                    myCompleted++
                } else {
                    othersCompleted++
                }
            }
        }

        val total = slots.size
        val pct = if (total > 0) (myCompleted.toFloat() * 100f / total.toFloat()) else 0f

        return EmployeeBranchParticipationStats(
            branchName = branchName,
            periodTitle = periodTitle,
            periodStartIso = periodStartIso,
            periodEndIso = periodEndIso,
            totalBranchTasks = total,
            completedByMyPayroll = myCompleted,
            completedByOtherPayrolls = othersCompleted,
            pendingTasks = pending,
            myParticipationPercent = pct
        )
    }
}

@Keep
@IgnoreExtraProperties
data class DailyTaskTemplate(
    @DocumentId val id: String = "",
    val slug: String = "",
    val title: String = "",
    val description: String = "",
    val priority: String = TaskPriority.NORMAL.name,
    val daysOfWeek: List<String> = WeekdayOption.ALL_CODES,
    val targetBranches: List<String> = listOf(BranchOptions.ALL_BRANCHES_OPTION),
    val dueTime: String? = "23:59",
    val deadlineLabel: String? = null,
    val dueDayOffset: Long = 0L,
    val active: Boolean = true,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedPriority: TaskPriority get() = TaskPriority.fromRaw(priority)

    val parsedWeekdays: List<WeekdayOption>
        get() = daysOfWeek.mapNotNull { WeekdayOption.fromCode(it) }.distinct()

    val includesAllBranches: Boolean
        get() = targetBranches.isEmpty() ||
            BranchOptions.ALL_BRANCHES_OPTION in targetBranches ||
            BranchOptions.ALL_BRANCHES.all { it in targetBranches }

    fun appliesToDate(dateIso: String): Boolean {
        if (!active) return false
        val code = com.example.utils.DateAndFormatters.weekdayCodeForIso(dateIso)
        return daysOfWeek.any { it.equals(code, ignoreCase = true) }
    }

    fun appliesToBranch(branchName: String?): Boolean {
        if (!active) return false
        if (includesAllBranches) return true
        if (branchName.isNullOrBlank()) return false
        return targetBranches.any { it.equals(branchName.trim(), ignoreCase = true) }
    }

    fun appliesTo(dateIso: String, branchName: String?): Boolean =
        active && appliesToDate(dateIso) && appliesToBranch(branchName)

    fun formatDaysSummaryEs(): String {
        val parsed = parsedWeekdays
        return when {
            parsed.size == WeekdayOption.entries.size -> "Todos los días"
            parsed.size == 6 && WeekdayOption.SUNDAY !in parsed -> "Lunes a Sábado"
            parsed.isEmpty() -> "Sin días activos"
            else -> parsed.joinToString(", ") { it.shortLabelEs }
        }
    }

    fun formatBranchesSummaryEs(): String = when {
        includesAllBranches -> BranchOptions.ALL_BRANCHES_OPTION
        targetBranches.isEmpty() -> "Sin sucursales"
        targetBranches.size == 1 -> targetBranches.first()
        else -> targetBranches.joinToString(", ") { it.removePrefix("Sucursal ").let { n -> "Suc. $n" } }
    }
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
    val employeePayroll: String? = null,
    val employeeName: String? = null,
    val branchName: String? = null,
    @ServerTimestamp val capturedAt: Timestamp? = null
) {
    val resolvedFileCount: Int get() = fileCount.toInt().coerceIn(1, 5)

    val formattedUploaderLabel: String
        get() {
            val displayPayroll = EmployeeIdentityValidation.formatPayrollForDisplay(employeePayroll)
            return when {
                displayPayroll.isNotBlank() && !employeeName.isNullOrBlank() -> {
                    val branchPart = branchName?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
                    "Nómina $displayPayroll · $employeeName$branchPart"
                }
                uploaderName.isNotBlank() -> uploaderName
                else -> "Sucursal"
            }
        }
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
    val employeePayroll: String? = null,
    val employeeName: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val formattedAuthorLabel: String
        get() {
            val displayPayroll = EmployeeIdentityValidation.formatPayrollForDisplay(employeePayroll)
            return when {
                displayPayroll.isNotBlank() && !employeeName.isNullOrBlank() ->
                    "Nómina $displayPayroll · $employeeName"
                userName.isNotBlank() -> userName
                !branchName.isNullOrBlank() -> branchName
                else -> "Sucursal"
            }
        }
}

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
) {
    val remainingToTarget: Double
        get() = (targetTotal - actualTotal).coerceAtLeast(0.0)

    val surplusOverTarget: Double
        get() = (actualTotal - targetTotal).coerceAtLeast(0.0)

    val isTargetMet: Boolean
        get() = targetTotal > 0.0 && actualTotal >= targetTotal
}

data class BranchWeightedOutcome(
    val branchName: String,
    val branchTarget: Double,
    val actualTotal: Double,
    val projectedTotal: Double,
    val deltaVsBranchQuota: Double,
    val projectedRatio: Float,
    val pace: ProjectionPace,
    val quotaWeightShare: Float
)

data class AdminWeightedProjection(
    val metric: SalesMetricType,
    val daysElapsed: Int,
    val totalCycleDays: Int,
    val remainingDays: Int,
    val adminTarget: Double,
    val sumOfBranchQuotas: Double,
    val totalActual: Double,
    val totalProjected: Double,
    val weightedBranchProjectedRatio: Float,
    val positiveBranchesCount: Int,
    val moderateRiskBranchesCount: Int,
    val behindBranchesCount: Int,
    val positiveSurplusTotal: Double,
    val negativeDeficitTotal: Double,
    val branchNetBalance: Double,
    val netBalanceVsAdminQuota: Double,
    val adminProjectedRatio: Float,
    val surplusCoversDeficit: Boolean,
    val pace: ProjectionPace,
    val branchOutcomes: List<BranchWeightedOutcome>
)

data class ChartSeries(
    val metric: SalesMetricType,
    val totalDays: Int,
    val elapsedDays: Int,
    val targetValue: Double,
    val actualCumulativeByDay: List<Double>,
    val projectedCumulativeByDay: List<Double>
)

data class ActiveBiweeklyCycleInfo(
    val periodId: String,
    val title: String,
    val startDate: String,
    val endDate: String,
    val effectiveUpperDateIso: String,
    val totalDays: Int,
    val daysElapsed: Int,
    val remainingDays: Int,
    val isCycleEnded: Boolean,
    val generalQuota: BiweeklyQuota,
    val activeQuotaIds: Set<String>
)

@Keep
@IgnoreExtraProperties
data class BiweeklyCycleBranchArchive(
    val branchName: String = "",
    val targetGlobalSales: Double = 0.0,
    val actualGlobalSales: Double = 0.0,
    val targetCreditSales: Double = 0.0,
    val actualCreditSales: Double = 0.0,
    val actualNewCustomers: Long = 0L,
    val actualAppMaOperations: Long = 0L,
    val actualNewCustomerRequests: Long = 0L,
    val completionPercent: Long = 0L,
    val hasCustomQuota: Boolean = false,
    val entriesCount: Long = 0L
)

@Keep
@IgnoreExtraProperties
data class BiweeklyCycleArchive(
    @DocumentId val id: String = "",
    val archiveId: String = "",
    val cyclePeriodId: String = "",
    val title: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val closedOnDate: String = "",
    val totalDays: Long = 15L,
    val closedByUid: String = "",
    val closedByEmail: String = "",
    val adminTargetGlobalSales: Double = 0.0,
    val adminActualGlobalSales: Double = 0.0,
    val adminTargetCreditSales: Double = 0.0,
    val adminActualCreditSales: Double = 0.0,
    val adminTargetNewCustomers: Long = 0L,
    val adminActualNewCustomers: Long = 0L,
    val adminTargetAppMaOperations: Long = 0L,
    val adminActualAppMaOperations: Long = 0L,
    val adminTargetNewCustomerRequests: Long = 0L,
    val adminActualNewCustomerRequests: Long = 0L,
    val adminCompletionPercent: Long = 0L,
    val entriesCount: Long = 0L,
    val branchSummaries: List<BiweeklyCycleBranchArchive> = emptyList(),
    @ServerTimestamp val closedAt: Timestamp? = null
)

data class SalesProjectionSummary(
    val quota: BiweeklyQuota,
    val daysElapsed: Int,
    val totalDays: Int,
    val metrics: List<MetricProgress>,
    val entriesCount: Int,
    val todayEntry: DailySalesEntry?,
    val recentEntries: List<DailySalesEntry> = emptyList(),
    val remainingDays: Int = (totalDays - daysElapsed).coerceAtLeast(0),
    val weightedByMetric: Map<SalesMetricType, AdminWeightedProjection> = emptyMap(),
    val isCycleEnded: Boolean = false
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
    val completionPercent: Int,
    val dailyAverage: Double = 0.0,
    val projectedGlobalSales: Double = 0.0,
    val projectedDelta: Double = 0.0,
    val projectedRatio: Float = 0f,
    val projectedPercent: Int = 0,
    val pace: ProjectionPace = ProjectionPace.BEHIND,
    val daysElapsed: Int = 1,
    val remainingDays: Int = 0,
    val totalCycleDays: Int = 15,
    val requiredDailyPace: Double = 0.0
) {
    val remainingGlobalSales: Double
        get() = (quota.targetGlobalSales - totalGlobalSales).coerceAtLeast(0.0)

    val surplusGlobalSales: Double
        get() = (totalGlobalSales - quota.targetGlobalSales).coerceAtLeast(0.0)

    val isGlobalTargetMet: Boolean
        get() = quota.targetGlobalSales > 0.0 && totalGlobalSales >= quota.targetGlobalSales
}

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
    currentPeriodId: String? = null
): List<BranchSalesSummary> {
    if (quotas.isEmpty()) return emptyList()
    val activeCycle = com.example.data.repository.ProjectionCalculator.resolveActiveCycleInfo(quotas, todayIso)
    val effectivePeriodId = currentPeriodId?.takeIf { it.isNotBlank() } ?: activeCycle.periodId
    val periodStartIso = activeCycle.startDate
    val periodEndIso = activeCycle.endDate
    val effectiveUpperIso = activeCycle.effectiveUpperDateIso
    val totalCycleDays = activeCycle.totalDays
    val daysElapsed = activeCycle.daysElapsed
    val remainingDays = activeCycle.remainingDays
    val activeQuotaIds = activeCycle.activeQuotaIds

    val uidToBranch = teamUsers
        .mapNotNull { user ->
            val branch = user.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
            if (branch != null) user.userId to branch else null
        }
        .toMap()

    return branches.mapNotNull { branch ->
        val (resolvedQuota, hasCustom) = resolveQuotaForBranch(branch, quotas, effectivePeriodId)
        val quota = resolvedQuota ?: return@mapNotNull null

        val branchEntries = entries
            .filter { entry ->
                val resolvedBranch = entry.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
                    ?: uidToBranch[entry.userId]
                    ?: entry.userName.takeIf { it in BranchOptions.ALL_BRANCHES }
                    ?: entry.userName
                val matchesBranch = resolvedBranch == branch
                val inActiveCycleDates = entry.dateString >= periodStartIso && entry.dateString <= effectiveUpperIso
                val matchesActiveQuota = activeQuotaIds.isEmpty() ||
                    entry.quotaId.isBlank() ||
                    entry.quotaId in activeQuotaIds
                matchesBranch && inActiveCycleDates && matchesActiveQuota
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

        val dailyAverage = totalGlobal / daysElapsed.toDouble()
        val projectedGlobal = totalGlobal + dailyAverage * remainingDays.toDouble()
        val projectedDelta = projectedGlobal - target
        val projectedRatio = if (target > 0.0) {
            (projectedGlobal / target).toFloat().coerceAtLeast(0f)
        } else if (totalGlobal >= 0.0) {
            1f
        } else {
            0f
        }
        val projectedPercent = kotlin.math.round(projectedRatio * 100f).toInt().coerceAtLeast(0)
        val pace = when {
            projectedRatio >= 0.9999f -> ProjectionPace.ON_TRACK
            projectedRatio >= 0.80f -> ProjectionPace.MODERATE_RISK
            else -> ProjectionPace.BEHIND
        }
        val requiredDailyPace = if (remainingDays > 0) {
            (target - totalGlobal).coerceAtLeast(0.0) / remainingDays.toDouble()
        } else {
            (target - totalGlobal).coerceAtLeast(0.0)
        }

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
            completionPercent = pct,
            dailyAverage = dailyAverage,
            projectedGlobalSales = projectedGlobal,
            projectedDelta = projectedDelta,
            projectedRatio = projectedRatio,
            projectedPercent = projectedPercent,
            pace = pace,
            daysElapsed = daysElapsed,
            remainingDays = remainingDays,
            totalCycleDays = totalCycleDays,
            requiredDailyPace = requiredDailyPace
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
