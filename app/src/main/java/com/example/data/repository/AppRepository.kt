package com.example.data.repository

import android.content.Context
import com.example.data.firestore.FirestoreProvider
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.AssignedTask
import com.example.data.model.BiweeklyQuota
import com.example.data.model.BranchOptions
import com.example.data.model.ClosingAttachmentDraft
import com.example.data.model.DailyClosingSaveResult
import com.example.data.model.DailySalesEntry
import com.example.data.model.DailyTaskTemplate
import com.example.data.model.TaskEvidence
import com.example.data.model.TaskEvidenceAttachmentFile
import com.example.data.model.TaskNotification
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.TaskType
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.WeekdayOption
import com.example.utils.DateAndFormatters
import com.example.utils.EncodedAttachment
import com.example.utils.ImageCompressor
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AppRepository(
    private val db: FirebaseFirestore
) {

    constructor(context: Context) : this(FirestoreProvider.getInstance(context))

    private val auth = Firebase.auth

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("El usuario debe iniciar sesión con Google primero.")

    private fun isBootstrappedAdmin(email: String?, emailVerified: Boolean): Boolean =
        Companion.isBootstrappedAdmin(email, emailVerified)

    companion object {
        private const val BOOTSTRAPPED_ADMIN_EMAIL = "arias.francisco.cv@gmail.com"

        fun isBootstrappedAdmin(email: String?, emailVerified: Boolean): Boolean =
            email?.trim()?.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true) == true && emailVerified
    }

    suspend fun syncCurrentUserProfile(
        uid: String,
        displayName: String?,
        email: String?,
        photoUrl: String?,
        emailVerified: Boolean
    ): Result<Unit> {
        val docPath = "users/$uid"
        return try {
            val currentUid = requireUserId()
            check(currentUid == uid) { "La sesión activa no coincide con el usuario." }

            val userRef = db.collection("users").document(uid)
            val snapshot = userRef.get().await()

            val safeName = displayName?.trim()?.takeIf { it.isNotEmpty() }?.take(80)
                ?: email?.substringBefore("@")?.take(80)?.ifBlank { "Usuario MA" }
                ?: "Usuario MA"
            val safeEmail = email?.trim()?.takeIf { it.length >= 3 }?.take(120) ?: "usuario@mueblesamerica.mx"
            val safePhoto = photoUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(500)
            val bootstrapped = isBootstrappedAdmin(safeEmail, emailVerified)

            if (!snapshot.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", safeName)
                    put("email", safeEmail)
                    if (safePhoto != null) put("photoUrl", safePhoto)
                    put("role", if (bootstrapped) UserRole.ADMIN.name else UserRole.EMPLOYEE.name)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                userRef.set(payload).await()
            } else {
                val updateMap = buildMap<String, Any> {
                    put("displayName", safeName)
                    if (safePhoto != null) put("photoUrl", safePhoto)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                userRef.update(updateMap).await()
            }

            if (bootstrapped) {
                val adminRef = db.collection("admins").document(uid)
                val adminSnap = adminRef.get().await()
                if (!adminSnap.exists()) {
                    adminRef.set(
                        mapOf(
                            "userId" to uid,
                            "email" to safeEmail,
                            "grantedBy" to uid,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateCurrentUserBranch(branchName: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Usuario no autenticado."))
        val path = "users/$uid"
        return try {
            require(branchName in BranchOptions.ALL_BRANCHES) {
                "Selecciona una sucursal válida."
            }
            db.collection("users").document(uid)
                .update(
                    mapOf(
                        "branchName" to branchName,
                        "displayName" to branchName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeCurrentUserProfile(uid: String): Flow<UserProfile?> = flow {
        if (uid.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "users/$uid"
        try {
            emitAll(
                db.collection("users").document(uid)
                    .snapshots()
                    .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                    .catch { error ->
                        handleFlowError(error, OperationType.GET, path, uid)
                        throw error
                    }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFlowError(e, OperationType.GET, path, uid)
            throw e
        }
    }

    fun observeIsAdmin(uid: String, email: String?, emailVerified: Boolean): Flow<Boolean> = flow {
        if (isBootstrappedAdmin(email, emailVerified)) {
            emit(true)
            return@flow
        }
        if (uid.isBlank()) {
            emit(false)
            return@flow
        }
        val path = "admins/$uid"
        try {
            emitAll(
                db.collection("admins").document(uid)
                    .snapshots()
                    .map { it.exists() }
                    .catch { error ->
                        handleFlowError(error, OperationType.GET, path, uid)
                        throw error
                    }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFlowError(e, OperationType.GET, path, uid)
            throw e
        }
    }

    fun observeTeamUsers(uid: String, isAdmin: Boolean): Flow<List<UserProfile>> = flow {
        if (!isAdmin || uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users"
        try {
            emitAll(
                db.collection(path)
                    .snapshots()
                    .map { snapshot ->
                        snapshot.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedBy { it.displayName.lowercase() }
                    }
                    .catch { error ->
                        handleFlowError(error, OperationType.LIST, path, uid)
                        throw error
                    }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFlowError(e, OperationType.LIST, path, uid)
            throw e
        }
    }

    suspend fun setUserRole(targetUser: UserProfile, makeAdmin: Boolean): Result<Unit> {
        val path = "users/${targetUser.userId}"
        return try {
            val adminUid = requireUserId()
            require(targetUser.userId.isNotBlank()) { "Usuario inválido." }
            if (!makeAdmin) {
                require(targetUser.userId != adminUid) {
                    "No puedes quitarte el rol de administrador a ti mismo."
                }
            }
            val batch = db.batch()
            val userRef = db.collection("users").document(targetUser.userId)
            val adminRef = db.collection("admins").document(targetUser.userId)
            batch.update(
                userRef,
                mapOf(
                    "role" to if (makeAdmin) UserRole.ADMIN.name else UserRole.EMPLOYEE.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (makeAdmin) {
                batch.set(
                    adminRef,
                    mapOf(
                        "userId" to targetUser.userId,
                        "email" to targetUser.email.take(120),
                        "grantedBy" to adminUid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.delete(adminRef)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeTasks(uid: String, isAdmin: Boolean): Flow<List<AssignedTask>> = flow {
        if (uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "tasks"
        try {
            val query = if (isAdmin) {
                db.collection(path)
            } else {
                db.collection(path).whereEqualTo("assigneeId", uid)
            }
            emitAll(
                query.snapshots()
                    .map { snapshot ->
                        snapshot
                            .toObjects(AssignedTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedWith(
                                compareBy<AssignedTask> { it.parsedStatus == TaskStatus.COMPLETED }
                                    .thenByDescending { it.parsedType == TaskType.EXTRAORDINARY }
                                    .thenByDescending { it.parsedPriority == TaskPriority.URGENT }
                                    .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                            )
                    }
                    .catch { error ->
                        handleFlowError(error, OperationType.LIST, path, uid)
                        throw error
                    }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFlowError(e, OperationType.LIST, path, uid)
            throw e
        }
    }

    suspend fun createTaskAndNotify(
        assignees: List<UserProfile>,
        title: String,
        description: String,
        taskType: TaskType,
        priority: TaskPriority,
        dueDate: String,
        dueTime: String? = null,
        deadlineLabel: String? = null
    ): Result<Int> {
        val path = "tasks"
        return try {
            val adminUid = requireUserId()
            val cleanTitle = title.trim()
            val cleanDesc = description.trim().take(500)
            require(cleanTitle.length in 3..120) { "El título debe tener entre 3 y 120 caracteres." }
            require(assignees.isNotEmpty()) { "Selecciona al menos un usuario asignado." }

            val eligibleAssignees = assignees.filter { candidate ->
                candidate.userId.isNotBlank() &&
                    candidate.parsedRole != UserRole.ADMIN &&
                    !candidate.email.trim().equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true) &&
                    !isAssigneeAdminAccount(candidate.userId)
            }
            require(eligibleAssignees.isNotEmpty()) {
                "Selecciona al menos una sucursal válida (no administradora)."
            }

            val batch = db.batch()
            eligibleAssignees.forEach { assignee ->
                val taskRef = db.collection("tasks").document()
                val safeName = assignee.effectiveBranchLabel.trim().ifBlank { "Sucursal" }.take(80)
                val taskPayload = buildMap<String, Any> {
                    put("assigneeId", assignee.userId)
                    put("assigneeName", safeName)
                    put("createdBy", adminUid)
                    put("title", cleanTitle)
                    put("description", cleanDesc)
                    put("taskType", taskType.name)
                    put("priority", priority.name)
                    put("dueDate", dueDate)
                    if (!dueTime.isNullOrBlank()) put("dueTime", dueTime.take(5))
                    if (!deadlineLabel.isNullOrBlank()) put("deadlineLabel", deadlineLabel.take(80))
                    put("status", TaskStatus.PENDING.name)
                    put("hasPhotoEvidence", false)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                batch.set(taskRef, taskPayload)

                if (taskType == TaskType.EXTRAORDINARY) {
                    val notifRef = db.collection("notifications").document()
                    val notifMsg = "$cleanTitle · Prioridad ${priority.labelEs}".take(300)
                    batch.set(
                        notifRef,
                        mapOf(
                            "recipientId" to assignee.userId,
                            "taskId" to taskRef.id,
                            "title" to "Nueva tarea extraordinaria",
                            "message" to notifMsg,
                            "acknowledged" to false,
                            "createdBy" to adminUid,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            Result.success(eligibleAssignees.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private suspend fun isAssigneeAdminAccount(assigneeId: String): Boolean {
        if (assigneeId.isBlank()) return true
        val currentUser = auth.currentUser
        if (currentUser?.uid == assigneeId &&
            isBootstrappedAdmin(currentUser.email, currentUser.isEmailVerified)
        ) {
            return true
        }
        val userSnap = db.collection("users").document(assigneeId).get().await()
        if (userSnap.getString("role") == UserRole.ADMIN.name) return true
        val userEmail = userSnap.getString("email")
        if (userEmail?.trim()?.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true) == true) return true
        val adminSnap = db.collection("admins").document(assigneeId).get().await()
        return adminSnap.exists()
    }

    fun observeDailyTaskTemplates(uid: String): Flow<List<DailyTaskTemplate>> = flow {
        if (uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "daily_task_templates"
        try {
            emitAll(
                db.collection(path)
                    .whereEqualTo("active", true)
                    .snapshots()
                    .map { snapshot ->
                        snapshot
                            .toObjects(DailyTaskTemplate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedBy { it.title.lowercase() }
                    }
                    .catch { error ->
                        handleFlowError(error, OperationType.LIST, path, uid)
                        throw error
                    }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFlowError(e, OperationType.LIST, path, uid)
            throw e
        }
    }

    suspend fun ensureDefaultDailyTemplatesIfEmpty(): Result<Unit> {
        val path = "daily_task_templates"
        return try {
            val adminUid = requireUserId()
            val anyExisting = db.collection(path)
                .limit(1)
                .get()
                .await()
            if (anyExisting.isEmpty) {
                val defaults = ScheduledTasksCatalog.defaultTemplates(createdBy = adminUid)
                val batch = db.batch()
                defaults.forEach { tpl ->
                    val docRef = db.collection(path).document(tpl.id)
                    batch.set(
                        docRef,
                        buildTemplatePayload(
                            slug = tpl.slug,
                            title = tpl.title,
                            description = tpl.description,
                            priority = tpl.parsedPriority,
                            daysOfWeek = tpl.daysOfWeek,
                            targetBranches = tpl.targetBranches,
                            dueTime = tpl.dueTime,
                            deadlineLabel = tpl.deadlineLabel,
                            dueDayOffset = tpl.dueDayOffset,
                            active = true,
                            createdBy = adminUid,
                            includeCreatedAt = true
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private fun buildTemplatePayload(
        slug: String,
        title: String,
        description: String,
        priority: TaskPriority,
        daysOfWeek: List<String>,
        targetBranches: List<String>,
        dueTime: String?,
        deadlineLabel: String?,
        dueDayOffset: Long,
        active: Boolean,
        createdBy: String?,
        includeCreatedAt: Boolean
    ): Map<String, Any> = buildMap {
        put("slug", slug)
        put("title", title)
        put("description", description)
        put("priority", priority.name)
        put("daysOfWeek", daysOfWeek)
        put("targetBranches", targetBranches)
        if (!dueTime.isNullOrBlank()) {
            put("dueTime", dueTime.take(5))
        }
        if (!deadlineLabel.isNullOrBlank()) {
            put("deadlineLabel", deadlineLabel.take(80))
        }
        put("dueDayOffset", dueDayOffset.coerceIn(0L, 1L))
        put("active", active)
        if (createdBy != null) {
            put("createdBy", createdBy)
        }
        if (includeCreatedAt) {
            put("createdAt", FieldValue.serverTimestamp())
        }
        put("updatedAt", FieldValue.serverTimestamp())
    }

    suspend fun saveDailyTaskTemplate(
        existingTemplateId: String? = null,
        title: String,
        description: String,
        priority: TaskPriority,
        daysOfWeek: List<String>,
        targetBranches: List<String>,
        dueTime: String? = "23:59",
        deadlineLabel: String? = null,
        dueDayOffset: Long = 0L,
        syncTodayInstances: Boolean = true
    ): Result<DailyTaskTemplate> {
        val path = "daily_task_templates"
        return try {
            val adminUid = requireUserId()
            val cleanTitle = title.trim()
            val cleanDesc = description.trim().take(500)
            require(cleanTitle.length in 3..120) {
                "El título debe tener entre 3 y 120 caracteres."
            }
            val normalizedDays = daysOfWeek
                .mapNotNull { WeekdayOption.fromCode(it)?.code }
                .distinct()
            require(normalizedDays.isNotEmpty()) {
                "Selecciona al menos un día de la semana."
            }
            val normalizedBranches = when {
                BranchOptions.ALL_BRANCHES_OPTION in targetBranches ->
                    listOf(BranchOptions.ALL_BRANCHES_OPTION)
                else -> targetBranches.filter { it in BranchOptions.ALL_BRANCHES }.distinct()
            }
            require(normalizedBranches.isNotEmpty()) {
                "Selecciona al menos una sucursal."
            }

            val cleanDueTime = dueTime?.trim()?.takeIf { it.isNotEmpty() }
            if (cleanDueTime != null) {
                require(Regex("""^([01][0-9]|2[0-3]):[0-5][0-9]$""").matches(cleanDueTime)) {
                    "La hora límite debe tener formato HH:mm (00:00 a 23:59)."
                }
            }
            val cleanLabel = deadlineLabel?.trim()?.take(80)?.takeIf { it.isNotEmpty() }
            val safeOffset = dueDayOffset.coerceIn(0L, 1L)

            val existingDoc = if (!existingTemplateId.isNullOrBlank()) {
                db.collection(path).document(existingTemplateId).get().await()
            } else {
                null
            }
            val existingSlug = existingDoc?.getString("slug")?.takeIf { it.isNotBlank() }
            val baseSlug = existingSlug ?: ScheduledTasksCatalog.sanitizeSlug(cleanTitle)
            val templateId = if (existingDoc != null && existingDoc.exists()) {
                existingDoc.id
            } else {
                val suffix = java.util.UUID.randomUUID().toString().replace("-", "").take(6)
                "tpl_${baseSlug}_$suffix".take(100)
            }
            val finalSlug = existingSlug ?: templateId.removePrefix("tpl_").take(60)
            val templateRef = db.collection(path).document(templateId)

            val savedTemplate = DailyTaskTemplate(
                id = templateId,
                slug = finalSlug,
                title = cleanTitle,
                description = cleanDesc,
                priority = priority.name,
                daysOfWeek = normalizedDays,
                targetBranches = normalizedBranches,
                dueTime = cleanDueTime,
                deadlineLabel = cleanLabel,
                dueDayOffset = safeOffset,
                active = true,
                createdBy = existingDoc?.getString("createdBy") ?: adminUid
            )

            if (existingDoc != null && existingDoc.exists()) {
                val updateMap = buildMap<String, Any> {
                    put("slug", finalSlug)
                    put("title", cleanTitle)
                    put("description", cleanDesc)
                    put("priority", priority.name)
                    put("daysOfWeek", normalizedDays)
                    put("targetBranches", normalizedBranches)
                    if (cleanDueTime != null) put("dueTime", cleanDueTime) else put("dueTime", FieldValue.delete())
                    if (cleanLabel != null) put("deadlineLabel", cleanLabel) else put("deadlineLabel", FieldValue.delete())
                    put("dueDayOffset", safeOffset)
                    put("active", true)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                templateRef.update(updateMap).await()
            } else {
                templateRef.set(
                    buildTemplatePayload(
                        slug = finalSlug,
                        title = cleanTitle,
                        description = cleanDesc,
                        priority = priority,
                        daysOfWeek = normalizedDays,
                        targetBranches = normalizedBranches,
                        dueTime = cleanDueTime,
                        deadlineLabel = cleanLabel,
                        dueDayOffset = safeOffset,
                        active = true,
                        createdBy = adminUid,
                        includeCreatedAt = true
                    )
                ).await()
            }

            if (syncTodayInstances) {
                syncTemplateAcrossEmployeesForToday(
                    template = savedTemplate,
                    previousTitle = existingDoc?.getString("title"),
                    actorUid = adminUid
                )
            }

            Result.success(savedTemplate)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun syncTemplateAcrossEmployeesForToday(
        template: DailyTaskTemplate,
        previousTitle: String?,
        actorUid: String
    ) {
        val today = DateAndFormatters.todayIso()
        val dueIso = DateAndFormatters.addDaysIso(today, template.dueDayOffset.toInt())
        val usersSnap = db.collection("users").get().await()
        val employees = usersSnap
            .toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .filter { user ->
                user.userId.isNotBlank() &&
                    user.parsedRole != UserRole.ADMIN &&
                    !user.email.trim().equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true)
            }
        if (employees.isEmpty()) return

        val dailyTasksSnap = db.collection("tasks")
            .whereEqualTo("taskType", TaskType.DAILY.name)
            .get()
            .await()

        val dailyTasks = dailyTasksSnap.documents.mapNotNull { doc ->
            doc.toObject(AssignedTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        val todayTasksByAssignee = dailyTasks
            .filter { DateAndFormatters.extractTaskDayIso(it) == today }
            .groupBy { it.assigneeId }

        val writes = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>?>>()

        for (emp in employees) {
            if (isAssigneeAdminAccount(emp.userId)) continue
            val branch = emp.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
            val userTodayTasks = todayTasksByAssignee[emp.userId].orEmpty()
            val deterministicId = "sched_${emp.userId}_${today}_${template.slug}".take(120)
            val matchingTasks = userTodayTasks.filter { candidate ->
                candidate.id == deterministicId ||
                    candidate.templateId == template.id ||
                    DateAndFormatters.extractScheduledSlug(candidate.id) == template.slug ||
                    (!previousTitle.isNullOrBlank() && candidate.title.trim().equals(previousTitle.trim(), ignoreCase = true)) ||
                    candidate.title.trim().equals(template.title.trim(), ignoreCase = true)
            }

            if (template.appliesTo(today, branch)) {
                if (matchingTasks.isEmpty()) {
                    if (!branch.isNullOrBlank()) {
                        val ref = db.collection("tasks").document(deterministicId)
                        writes.add(
                            ref to buildMap {
                                put("assigneeId", emp.userId)
                                put("assigneeName", emp.effectiveBranchLabel.ifBlank { branch }.take(80))
                                put("createdBy", actorUid)
                                put("title", template.title)
                                put("description", template.description)
                                put("taskType", TaskType.DAILY.name)
                                put("priority", template.parsedPriority.name)
                                put("dueDate", dueIso)
                                template.dueTime?.takeIf { it.isNotBlank() }?.let { put("dueTime", it.take(5)) }
                                template.deadlineLabel?.takeIf { it.isNotBlank() }?.let { put("deadlineLabel", it.take(80)) }
                                put("templateId", template.id)
                                put("status", TaskStatus.PENDING.name)
                                put("hasPhotoEvidence", false)
                                put("createdAt", FieldValue.serverTimestamp())
                                put("updatedAt", FieldValue.serverTimestamp())
                            }
                        )
                    }
                } else {
                    matchingTasks.forEach { existingTask ->
                        val ref = db.collection("tasks").document(existingTask.id)
                        writes.add(
                            ref to buildMap {
                                put("title", template.title)
                                put("description", template.description)
                                put("priority", template.parsedPriority.name)
                                put("dueDate", dueIso)
                                put("templateId", template.id)
                                if (!template.dueTime.isNullOrBlank()) put("dueTime", template.dueTime.take(5))
                                else put("dueTime", FieldValue.delete())
                                if (!template.deadlineLabel.isNullOrBlank()) put("deadlineLabel", template.deadlineLabel.take(80))
                                else put("deadlineLabel", FieldValue.delete())
                                put("updatedAt", FieldValue.serverTimestamp())
                            }
                        )
                    }
                }
            } else {
                matchingTasks
                    .filter { it.parsedStatus == TaskStatus.PENDING }
                    .forEach { pendingTask ->
                        writes.add(db.collection("tasks").document(pendingTask.id) to null)
                    }
            }
        }

        writes.chunked(380).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, payload) ->
                if (payload == null) {
                    batch.delete(ref)
                } else if (payload.containsKey("createdAt")) {
                    batch.set(ref, payload)
                } else {
                    batch.update(ref, payload)
                }
            }
            batch.commit().await()
        }
    }

    suspend fun deleteDailyTaskTemplatePermanently(
        template: DailyTaskTemplate? = null,
        task: AssignedTask? = null,
        removeBranchOnly: Boolean = false
    ): Result<Int> {
        val path = "daily_task_templates"
        return try {
            requireUserId()
            val today = DateAndFormatters.todayIso()
            val targetDay = task?.let { DateAndFormatters.extractTaskDayIso(it) } ?: today

            val resolvedTemplate = template ?: resolveTemplateForTask(task)

            if (removeBranchOnly && task != null) {
                val branchToRemove = task.assigneeName.trim()
                if (resolvedTemplate != null && branchToRemove in BranchOptions.ALL_BRANCHES) {
                    val remainingBranches = if (resolvedTemplate.includesAllBranches) {
                        BranchOptions.ALL_BRANCHES.filter { it != branchToRemove }
                    } else {
                        resolvedTemplate.targetBranches.filter { !it.equals(branchToRemove, ignoreCase = true) }
                    }
                    val tplRef = db.collection(path).document(resolvedTemplate.id)
                    if (remainingBranches.isEmpty()) {
                        tplRef.update(
                            mapOf(
                                "active" to false,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    } else {
                        tplRef.update(
                            mapOf(
                                "targetBranches" to remainingBranches,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
                db.collection("tasks").document(task.id).delete().await()
                return Result.success(1)
            }

            // Definitive permanent deletion: deactivate template(s) in Firestore + delete today's matching tasks
            val templatesToDeactivate = buildList {
                if (resolvedTemplate != null) add(resolvedTemplate.id)
                val taskTplId = task?.templateId?.takeIf { it.isNotBlank() }
                if (taskTplId != null && taskTplId !in this) add(taskTplId)
                val slug = task?.let { DateAndFormatters.extractScheduledSlug(it.id) }
                if (!slug.isNullOrBlank()) {
                    val canonicalId = ScheduledTasksCatalog.templateDocIdForSlug(slug)
                    if (canonicalId !in this) add(canonicalId)
                }
            }

            templatesToDeactivate.forEach { tplId ->
                val tplRef = db.collection(path).document(tplId)
                val snap = tplRef.get().await()
                if (snap.exists()) {
                    tplRef.update(
                        mapOf(
                            "active" to false,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            // Delete matching daily tasks for today/targetDay so none remain in active views
            val dailyDocs = db.collection("tasks")
                .whereEqualTo("taskType", TaskType.DAILY.name)
                .get()
                .await()
                .documents

            val titleNeedle = (resolvedTemplate?.title ?: task?.title)?.trim()
            val slugNeedle = resolvedTemplate?.slug?.takeIf { it.isNotBlank() }
                ?: task?.let { DateAndFormatters.extractScheduledSlug(it.id) }
            val tplIdSet = templatesToDeactivate.toSet()

            val refsToDelete = dailyDocs.mapNotNull { doc ->
                val candidate = doc.toObject(AssignedTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: return@mapNotNull null
                val candidateDay = DateAndFormatters.extractTaskDayIso(candidate)
                val matchesDay = candidateDay == today || candidateDay == targetDay
                if (!matchesDay) return@mapNotNull null

                val candidateSlug = DateAndFormatters.extractScheduledSlug(candidate.id)
                val matches = (task != null && candidate.id == task.id) ||
                    (candidate.templateId != null && candidate.templateId in tplIdSet) ||
                    (slugNeedle != null && candidateSlug == slugNeedle) ||
                    (!titleNeedle.isNullOrBlank() && candidate.title.trim().equals(titleNeedle, ignoreCase = true))

                if (matches) doc.reference else null
            }.distinctBy { it.path }

            refsToDelete.chunked(380).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { ref -> batch.delete(ref) }
                batch.commit().await()
            }

            Result.success(refsToDelete.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private suspend fun resolveTemplateForTask(task: AssignedTask?): DailyTaskTemplate? {
        if (task == null) return null
        val path = "daily_task_templates"
        val directId = task.templateId?.takeIf { it.isNotBlank() }
        if (directId != null) {
            val snap = db.collection(path).document(directId).get().await()
            if (snap.exists()) {
                return snap.toObject(DailyTaskTemplate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }
        val slug = DateAndFormatters.extractScheduledSlug(task.id)
        if (!slug.isNullOrBlank()) {
            val bySlugId = ScheduledTasksCatalog.templateDocIdForSlug(slug)
            val snap = db.collection(path).document(bySlugId).get().await()
            if (snap.exists()) {
                return snap.toObject(DailyTaskTemplate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }
        val activeSnap = db.collection(path)
            .whereEqualTo("active", true)
            .get()
            .await()
        val activeTemplates = activeSnap.toObjects(
            DailyTaskTemplate::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
        return activeTemplates.firstOrNull {
            (!slug.isNullOrBlank() && it.slug == slug) ||
                it.title.trim().equals(task.title.trim(), ignoreCase = true)
        }
    }

    suspend fun seedDailyChecklistForUser(targetUser: UserProfile): Result<Int> {
        if (targetUser.parsedRole == UserRole.ADMIN ||
            targetUser.email.trim().equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true)
        ) {
            return Result.failure(
                IllegalStateException("Las cuentas de administrador no reciben tareas programadas.")
            )
        }
        return syncScheduledTasksForAssignee(
            assigneeId = targetUser.userId,
            assigneeName = targetUser.effectiveBranchLabel,
            branchName = targetUser.branchName
        )
    }

    suspend fun syncScheduledTasksForCurrentUser(branchLabel: String): Result<Int> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("Usuario no autenticado."))
        if (isBootstrappedAdmin(currentUser.email, currentUser.isEmailVerified)) {
            return Result.success(0)
        }
        return syncScheduledTasksForAssignee(
            assigneeId = currentUser.uid,
            assigneeName = branchLabel,
            branchName = branchLabel.takeIf { it in BranchOptions.ALL_BRANCHES }
        )
    }

    /**
     * Checks on login / app resume whether today is a new day compared to the branch's
     * last recorded closing and current time is within opening/workday hours (>= 08:00).
     * Respects recurring template configuration for Cierre de venta diaria (active status, weekday, branch).
     */
    suspend fun resetDailyClosingTaskOnNewDayIfNeeded(
        branchLabel: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<Boolean> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.success(false)
            val uid = currentUser.uid
            if (isBootstrappedAdmin(currentUser.email, currentUser.isEmailVerified) ||
                isAssigneeAdminAccount(uid)
            ) {
                return Result.success(false)
            }
            if (!DateAndFormatters.isWithinOpeningOrWorkdayHours(nowMillis)) {
                return Result.success(false)
            }

            val today = DateAndFormatters.todayIso()

            // Respect admin template state: if the daily closing template was deactivated or doesn't apply today/branch, skip.
            val activeTemplates = db.collection("daily_task_templates")
                .whereEqualTo("active", true)
                .get()
                .await()
                .toObjects(DailyTaskTemplate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

            val closingTemplate = activeTemplates.firstOrNull {
                it.slug == ScheduledTasksCatalog.DAILY_SALES_CLOSING_SLUG ||
                    it.title.trim().equals(ScheduledTasksCatalog.DAILY_SALES_CLOSING_TITLE, ignoreCase = true)
            } ?: return Result.success(false)

            val effectiveBranch = branchLabel.takeIf { it in BranchOptions.ALL_BRANCHES }
                ?: db.collection("users").document(uid).get().await().getString("branchName")
            if (!closingTemplate.appliesTo(today, effectiveBranch)) {
                return Result.success(false)
            }

            val lastClosingDate = try {
                db.collection("sales_entries")
                    .whereEqualTo("userId", uid)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { it.getString("dateString")?.takeIf { d -> d.length == 10 } }
                    .maxOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, "sales_entries")
                return Result.failure(e)
            }

            if (!DateAndFormatters.shouldAutoResetDailyClosing(lastClosingDate, today, nowMillis)) {
                return Result.success(false)
            }

            val userTasksSnap = try {
                db.collection("tasks")
                    .whereEqualTo("assigneeId", uid)
                    .get()
                    .await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, "tasks")
                return Result.failure(e)
            }

            val closingDocs = userTasksSnap.documents.filter { doc ->
                doc.getString("templateId") == closingTemplate.id ||
                    doc.getString("title") == closingTemplate.title ||
                    doc.id.endsWith("_${closingTemplate.slug}")
            }

            val alreadyHasTodayClosingTask = closingDocs.any { doc ->
                doc.getString("dueDate") == today ||
                    doc.id == "sched_${uid}_${today}_${closingTemplate.slug}"
            }
            if (alreadyHasTodayClosingTask) {
                return Result.success(false)
            }

            val safeName = branchLabel.trim().ifBlank { effectiveBranch ?: "Sucursal" }.take(80)
            val deterministicId = "sched_${uid}_${today}_${closingTemplate.slug}".take(120)
            val dueIso = DateAndFormatters.addDaysIso(today, closingTemplate.dueDayOffset.toInt())
            db.collection("tasks").document(deterministicId).set(
                buildMap {
                    put("assigneeId", uid)
                    put("assigneeName", safeName)
                    put("createdBy", uid)
                    put("title", closingTemplate.title)
                    put("description", closingTemplate.description)
                    put("taskType", TaskType.DAILY.name)
                    put("priority", closingTemplate.parsedPriority.name)
                    put("dueDate", dueIso)
                    closingTemplate.dueTime?.takeIf { it.isNotBlank() }?.let { put("dueTime", it.take(5)) }
                    closingTemplate.deadlineLabel?.takeIf { it.isNotBlank() }?.let { put("deadlineLabel", it.take(80)) }
                    put("templateId", closingTemplate.id)
                    put("status", TaskStatus.PENDING.name)
                    put("hasPhotoEvidence", false)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
            ).await()

            Result.success(true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "tasks")
            Result.failure(e)
        }
    }

    private suspend fun syncScheduledTasksForAssignee(
        assigneeId: String,
        assigneeName: String,
        branchName: String? = null
    ): Result<Int> {
        return try {
            val actorUid = requireUserId()
            if (assigneeId.isBlank() || isAssigneeAdminAccount(assigneeId)) {
                return Result.success(0)
            }

            val resolvedBranch = branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
                ?: db.collection("users").document(assigneeId).get().await().getString("branchName")

            val today = DateAndFormatters.todayIso()

            // Simple single-field query: no composite index required.
            val activeTemplates = db.collection("daily_task_templates")
                .whereEqualTo("active", true)
                .get()
                .await()
                .toObjects(DailyTaskTemplate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

            val expectedTemplates = ScheduledTasksCatalog.templatesMatching(
                templates = activeTemplates,
                dateIso = today,
                branchName = resolvedBranch
            )

            if (expectedTemplates.isEmpty()) {
                return Result.success(0)
            }

            val existingDocs = db.collection("tasks")
                .whereEqualTo("assigneeId", assigneeId)
                .get()
                .await()
                .documents

            val existingIds = existingDocs.map { it.id }.toSet()
            val todayExistingTasks = existingDocs.mapNotNull { doc ->
                val obj = doc.toObject(AssignedTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: return@mapNotNull null
                if (DateAndFormatters.extractTaskDayIso(obj) == today) obj else null
            }
            val existingTemplateIdsToday = todayExistingTasks.mapNotNull { it.templateId }.toSet()
            val existingSlugsToday = todayExistingTasks.mapNotNull { DateAndFormatters.extractScheduledSlug(it.id) }.toSet()

            val safeName = assigneeName.trim().ifBlank { resolvedBranch ?: "Sucursal" }.take(80)
            val missing = expectedTemplates.filter { tpl ->
                val slugKey = tpl.slug.ifBlank { ScheduledTasksCatalog.sanitizeSlug(tpl.title) }
                val deterministicId = "sched_${assigneeId}_${today}_$slugKey".take(120)
                deterministicId !in existingIds &&
                    tpl.id !in existingTemplateIdsToday &&
                    slugKey !in existingSlugsToday
            }

            if (missing.isEmpty()) {
                return Result.success(0)
            }

            val batch = db.batch()
            missing.forEach { tpl ->
                val slugKey = tpl.slug.ifBlank { ScheduledTasksCatalog.sanitizeSlug(tpl.title) }
                val deterministicId = "sched_${assigneeId}_${today}_$slugKey".take(120)
                val dueIso = DateAndFormatters.addDaysIso(today, tpl.dueDayOffset.toInt())
                val ref = db.collection("tasks").document(deterministicId)
                batch.set(
                    ref,
                    buildMap {
                        put("assigneeId", assigneeId)
                        put("assigneeName", safeName)
                        put("createdBy", actorUid)
                        put("title", tpl.title)
                        put("description", tpl.description)
                        put("taskType", TaskType.DAILY.name)
                        put("priority", tpl.parsedPriority.name)
                        put("dueDate", dueIso)
                        tpl.dueTime?.takeIf { it.isNotBlank() }?.let { put("dueTime", it.take(5)) }
                        tpl.deadlineLabel?.takeIf { it.isNotBlank() }?.let { put("deadlineLabel", it.take(80)) }
                        put("templateId", tpl.id)
                        put("status", TaskStatus.PENDING.name)
                        put("hasPhotoEvidence", false)
                        put("createdAt", FieldValue.serverTimestamp())
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                )
            }
            batch.commit().await()
            Result.success(missing.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "tasks")
            Result.failure(e)
        }
    }

    suspend fun confirmBranchChecklistResetForQuota(
        branchName: String,
        branchUsers: List<UserProfile>,
        quotaId: String
    ): Result<Int> {
        return try {
            val adminUid = requireUserId()
            require(quotaId.isNotBlank()) { "No hay una meta quincenal activa." }
            val eligibleUsers = branchUsers.filter {
                it.userId.isNotBlank() &&
                    it.parsedRole != UserRole.ADMIN &&
                    !it.email.trim().equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true)
            }
            require(eligibleUsers.isNotEmpty()) {
                "No hay cuentas de sucursal no administradoras en $branchName para confirmar el reinicio."
            }

            val today = DateAndFormatters.todayIso()
            val activeTemplates = db.collection("daily_task_templates")
                .whereEqualTo("active", true)
                .get()
                .await()
                .toObjects(DailyTaskTemplate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            var recreatedTotal = 0

            for (user in eligibleUsers) {
                if (isAssigneeAdminAccount(user.userId)) continue

                val existingSnap = db.collection("tasks")
                    .whereEqualTo("assigneeId", user.userId)
                    .get()
                    .await()

                val docsToDelete = existingSnap.documents
                docsToDelete.chunked(380).forEach { chunk ->
                    val deleteBatch = db.batch()
                    chunk.forEach { doc -> deleteBatch.delete(doc.reference) }
                    deleteBatch.commit().await()
                }

                val createBatch = db.batch()
                val userRef = db.collection("users").document(user.userId)
                createBatch.update(
                    userRef,
                    mapOf(
                        "confirmedQuotaId" to quotaId,
                        "lastChecklistResetAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                val effectiveBranch = user.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
                    ?: branchName.takeIf { it in BranchOptions.ALL_BRANCHES }
                val expectedToday = ScheduledTasksCatalog.templatesMatching(
                    templates = activeTemplates,
                    dateIso = today,
                    branchName = effectiveBranch
                )
                val safeName = branchName.trim().ifBlank { user.effectiveBranchLabel }.take(80)
                expectedToday.forEach { tpl ->
                    val slugKey = tpl.slug.ifBlank { ScheduledTasksCatalog.sanitizeSlug(tpl.title) }
                    val deterministicId = "sched_${user.userId}_${today}_$slugKey".take(120)
                    val dueIso = DateAndFormatters.addDaysIso(today, tpl.dueDayOffset.toInt())
                    val taskRef = db.collection("tasks").document(deterministicId)
                    createBatch.set(
                        taskRef,
                        buildMap {
                            put("assigneeId", user.userId)
                            put("assigneeName", safeName)
                            put("createdBy", adminUid)
                            put("title", tpl.title)
                            put("description", tpl.description)
                            put("taskType", TaskType.DAILY.name)
                            put("priority", tpl.parsedPriority.name)
                            put("dueDate", dueIso)
                            tpl.dueTime?.takeIf { it.isNotBlank() }?.let { put("dueTime", it.take(5)) }
                            tpl.deadlineLabel?.takeIf { it.isNotBlank() }?.let { put("deadlineLabel", it.take(80)) }
                            put("templateId", tpl.id)
                            put("status", TaskStatus.PENDING.name)
                            put("hasPhotoEvidence", false)
                            put("createdAt", FieldValue.serverTimestamp())
                            put("updatedAt", FieldValue.serverTimestamp())
                        }
                    )
                    recreatedTotal++
                }
                createBatch.commit().await()
            }

            Result.success(recreatedTotal)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "tasks")
            Result.failure(e)
        }
    }

    suspend fun updateDailyTask(
        task: AssignedTask,
        title: String,
        description: String,
        priority: TaskPriority,
        dueTime: String?,
        deadlineLabel: String?,
        applyToAllBranchesSameDay: Boolean
    ): Result<Int> {
        val path = if (applyToAllBranchesSameDay) "tasks" else "tasks/${task.id}"
        return try {
            requireUserId()
            require(task.id.isNotBlank()) { "ID de tarea inválido." }
            val cleanTitle = title.trim()
            val cleanDesc = description.trim().take(500)
            require(cleanTitle.length in 3..120) {
                "El título debe tener entre 3 y 120 caracteres."
            }
            val cleanDueTime = dueTime?.trim()?.takeIf { it.isNotEmpty() }
            if (cleanDueTime != null) {
                require(Regex("""^([01][0-9]|2[0-3]):[0-5][0-9]$""").matches(cleanDueTime)) {
                    "La hora límite debe tener formato HH:mm (00:00 a 23:59)."
                }
            }
            val cleanLabel = deadlineLabel?.trim()?.take(80)

            val updatePayload = buildMap<String, Any> {
                put("title", cleanTitle)
                put("description", cleanDesc)
                put("priority", priority.name)
                if (cleanDueTime != null) {
                    put("dueTime", cleanDueTime)
                } else {
                    put("dueTime", FieldValue.delete())
                }
                if (!cleanLabel.isNullOrEmpty()) {
                    put("deadlineLabel", cleanLabel)
                } else {
                    put("deadlineLabel", FieldValue.delete())
                }
                put("updatedAt", FieldValue.serverTimestamp())
            }

            if (!applyToAllBranchesSameDay) {
                db.collection("tasks").document(task.id).update(updatePayload).await()
                return Result.success(1)
            }

            // Single-field equality query requires no composite index; day/slug matching runs in memory.
            val dailyDocs = db.collection("tasks")
                .whereEqualTo("taskType", TaskType.DAILY.name)
                .get()
                .await()
                .documents

            val matchingRefs = findMatchingDailyTaskRefs(dailyDocs, task)
                .ifEmpty { listOf(db.collection("tasks").document(task.id)) }

            matchingRefs.chunked(380).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { ref -> batch.update(ref, updatePayload) }
                batch.commit().await()
            }
            Result.success(matchingRefs.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteDailyTask(
        task: AssignedTask,
        deleteAllBranchesSameDay: Boolean
    ): Result<Int> {
        return deleteDailyTaskTemplatePermanently(
            template = null,
            task = task,
            removeBranchOnly = !deleteAllBranchesSameDay
        )
    }

    private fun findMatchingDailyTaskRefs(
        dailyDocs: List<DocumentSnapshot>,
        targetTask: AssignedTask
    ) = dailyDocs.mapNotNull { doc ->
        val candidate = doc.toObject(AssignedTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: return@mapNotNull null
        val targetDay = DateAndFormatters.extractTaskDayIso(targetTask)
        val candidateDay = DateAndFormatters.extractTaskDayIso(candidate)
        if (candidateDay != targetDay) return@mapNotNull null

        val targetSlug = DateAndFormatters.extractScheduledSlug(targetTask.id)
        val candidateSlug = DateAndFormatters.extractScheduledSlug(candidate.id)
        val sameTask = candidate.id == targetTask.id ||
            (!targetTask.templateId.isNullOrBlank() && candidate.templateId == targetTask.templateId) ||
            (targetSlug != null && candidateSlug == targetSlug) ||
            (candidate.title.trim().equals(targetTask.title.trim(), ignoreCase = true) &&
                candidate.dueDate == targetTask.dueDate)
        if (sameTask) doc.reference else null
    }

    suspend fun completeTaskWithEvidenceFiles(
        task: AssignedTask,
        uploaderName: String,
        files: List<EncodedAttachment>
    ): Result<Unit> {
        val docPath = "tasks/${task.id}"
        return try {
            val uid = requireUserId()
            require(task.id.isNotBlank()) { "ID de tarea inválido." }
            require(task.parsedStatus == TaskStatus.PENDING) {
                "Esta tarea ya fue completada y no admite nuevos archivos."
            }
            require(files.size in 1..ImageCompressor.MAX_TASK_EVIDENCE_FILES) {
                "Debes adjuntar entre 1 y ${ImageCompressor.MAX_TASK_EVIDENCE_FILES} archivos."
            }
            files.forEach { file ->
                require(file.totalBytes in 1..ImageCompressor.MAX_TASK_EVIDENCE_FILE_BYTES) {
                    "El archivo ${file.fileName} supera el máximo de 1.5 MB."
                }
                require(
                    file.base64Chunks.size in 1..4 &&
                        file.base64Chunks.all { it.isNotEmpty() && it.length <= 700_000 }
                ) {
                    "El archivo ${file.fileName} supera el tamaño permitido."
                }
            }

            val taskRef = db.collection("tasks").document(task.id)
            val liveSnap = taskRef.get().await()
            require(liveSnap.exists() && liveSnap.getString("status") == TaskStatus.PENDING.name) {
                "Esta tarea ya fue completada y quedó bloqueada."
            }

            val evidenceRef = db.collection("task_evidences").document()
            val safeUploader = uploaderName.trim().ifBlank { "Sucursal" }.take(80)
            val safeTitle = task.title.trim().ifBlank { "Tarea" }.take(120)
            val firstImagePreview = files.firstOrNull {
                it.mimeType.startsWith("image/", ignoreCase = true) &&
                    it.base64Chunks.size == 1 &&
                    it.base64Chunks.first().length <= 700_000
            }?.base64Chunks?.firstOrNull()

            val evidencePayload = buildMap<String, Any> {
                put("taskId", task.id)
                put("uploaderId", uid)
                put("uploaderName", safeUploader)
                put("taskTitle", safeTitle)
                if (firstImagePreview != null) {
                    put("imageBase64", firstImagePreview)
                }
                put("fileCount", files.size.toLong())
                put("primaryFileName", files.first().fileName.trim().ifBlank { "archivo" }.take(120))
                put("capturedAt", FieldValue.serverTimestamp())
            }

            data class PartWrite(
                val docId: String,
                val payload: Map<String, Any>
            )

            val partWrites = buildList {
                files.forEachIndexed { fileIdx, file ->
                    val cleanName = file.fileName.trim().ifBlank { "archivo_${fileIdx + 1}" }.take(120)
                    val cleanMime = file.mimeType.trim().ifBlank { "application/octet-stream" }.take(100)
                    val chunkCount = file.base64Chunks.size
                    file.base64Chunks.forEachIndexed { chunkIdx, chunkBase64 ->
                        add(
                            PartWrite(
                                docId = "file_${fileIdx}_part_${chunkIdx}",
                                payload = mapOf(
                                    "evidenceId" to evidenceRef.id,
                                    "taskId" to task.id,
                                    "uploaderId" to uid,
                                    "fileIndex" to fileIdx,
                                    "chunkIndex" to chunkIdx,
                                    "chunkCount" to chunkCount,
                                    "fileName" to cleanName,
                                    "mimeType" to cleanMime,
                                    "sizeBytes" to file.totalBytes.coerceIn(1, ImageCompressor.MAX_TASK_EVIDENCE_FILE_BYTES),
                                    "dataBase64" to chunkBase64,
                                    "capturedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        )
                    }
                }
            }

            val partBatches = partWrites.chunked(4)
            partBatches.forEachIndexed { batchIdx, chunk ->
                val batch = db.batch()
                if (batchIdx == 0) {
                    batch.set(evidenceRef, evidencePayload)
                }
                chunk.forEach { part ->
                    batch.set(evidenceRef.collection("files").document(part.docId), part.payload)
                }
                batch.commit().await()
            }

            taskRef.update(
                mapOf(
                    "status" to TaskStatus.COMPLETED.name,
                    "hasPhotoEvidence" to true,
                    "evidenceId" to evidenceRef.id,
                    "completedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun loadTaskEvidenceFiles(evidence: TaskEvidence): Result<List<TaskEvidenceAttachmentFile>> {
        if (evidence.id.isBlank()) {
            return Result.failure(IllegalStateException("Evidencia inválida."))
        }
        val path = "task_evidences/${evidence.id}/files"
        return try {
            requireUserId()
            val snapshot = db.collection("task_evidences")
                .document(evidence.id)
                .collection("files")
                .get()
                .await()

            if (snapshot.isEmpty) {
                val legacyBase64 = evidence.imageBase64.takeIf { it.isNotBlank() }
                    ?: error("No se encontraron archivos adjuntos en esta evidencia.")
                val approxBytes = ((legacyBase64.length * 3) / 4).coerceAtLeast(1)
                return Result.success(
                    listOf(
                        TaskEvidenceAttachmentFile(
                            fileIndex = 0,
                            fileName = evidence.primaryFileName?.takeIf { it.isNotBlank() } ?: "evidencia.jpg",
                            mimeType = "image/jpeg",
                            sizeBytes = approxBytes,
                            base64Chunks = listOf(legacyBase64)
                        )
                    )
                )
            }

            val files = snapshot.documents
                .groupBy { (it.get("fileIndex") as? Number)?.toInt() ?: 0 }
                .entries
                .sortedBy { it.key }
                .mapNotNull { (fileIdx, docs) ->
                    val sortedDocs = docs.sortedBy { (it.get("chunkIndex") as? Number)?.toInt() ?: 0 }
                    val firstDoc = sortedDocs.firstOrNull() ?: return@mapNotNull null
                    val chunks = sortedDocs.mapNotNull { doc ->
                        doc.getString("dataBase64")?.takeIf { it.isNotBlank() }
                    }
                    if (chunks.isEmpty()) return@mapNotNull null
                    TaskEvidenceAttachmentFile(
                        fileIndex = fileIdx,
                        fileName = firstDoc.getString("fileName")?.takeIf { it.isNotBlank() }
                            ?: "archivo_${fileIdx + 1}",
                        mimeType = firstDoc.getString("mimeType")?.takeIf { it.isNotBlank() }
                            ?: "application/octet-stream",
                        sizeBytes = (firstDoc.get("sizeBytes") as? Number)?.toInt() ?: 0,
                        base64Chunks = chunks
                    )
                }

            require(files.isNotEmpty()) { "No se pudieron reconstruir los archivos adjuntos." }
            Result.success(files)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    fun observeEvidences(uid: String, isAdmin: Boolean): Flow<List<TaskEvidence>> = flow {
        // Non-admin employees NEVER read /task_evidences; enforced both here and in firestore.rules
        if (!isAdmin || uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "task_evidences"
        try {
            emitAll(
                db.collection(path)
                    .snapshots()
                    .map { snapshot ->
                        snapshot.toObjects(TaskEvidence::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedByDescending { it.capturedAt ?: Timestamp(0, 0) }
                    }
                    .catch { error ->
                        handleFlowError(error, OperationType.LIST, path, uid)
                        throw error
                    }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFlowError(e, OperationType.LIST, path, uid)
            throw e
        }
    }

    fun observeNotifications(uid: String): Flow<List<TaskNotification>> = flow {
        if (uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "notifications"
        try {
            emitAll(
                db.collection(path)
                    .whereEqualTo("recipientId", uid)
                    .snapshots()
                    .map { snapshot ->
                        snapshot.toObjects(TaskNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    }
                    .catch { error ->
                        handleFlowError(error, OperationType.LIST, path, uid)
                        throw error
                    }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFlowError(e, OperationType.LIST, path, uid)
            throw e
        }
    }

    suspend fun acknowledgeNotification(notificationId: String): Result<Unit> {
        val path = "notifications/$notificationId"
        return try {
            requireUserId()
            require(notificationId.isNotBlank())
            db.collection("notifications").document(notificationId)
                .update(
                    mapOf(
                        "acknowledged" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeActiveQuotas(uid: String): Flow<List<BiweeklyQuota>> = flow {
        if (uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "biweekly_quotas"
        try {
            emitAll(
                db.collection(path)
                    .whereEqualTo("active", true)
                    .snapshots()
                    .map { snapshot ->
                        snapshot.toObjects(BiweeklyQuota::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                    }
                    .catch { error ->
                        handleFlowError(error, OperationType.LIST, path, uid)
                        throw error
                    }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFlowError(e, OperationType.LIST, path, uid)
            throw e
        }
    }

    fun observeActiveQuota(uid: String): Flow<BiweeklyQuota?> =
        observeActiveQuotas(uid).map { quotas ->
            val currentPeriodId = DateAndFormatters.currentBiweeklyPeriodId()
            quotas.firstOrNull {
                it.effectivePeriodId == currentPeriodId &&
                    (it.branchName.isNullOrBlank() || it.branchName == BranchOptions.ALL_BRANCHES_OPTION)
            } ?: quotas.firstOrNull {
                it.branchName.isNullOrBlank() || it.branchName == BranchOptions.ALL_BRANCHES_OPTION
            } ?: quotas.firstOrNull()
        }

    suspend fun saveBiweeklyQuota(
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
        val path = "biweekly_quotas"
        return try {
            val adminUid = requireUserId()
            val cleanTitle = title.trim()
            val cleanBranch = branchName.trim().ifBlank { BranchOptions.ALL_BRANCHES_OPTION }
            val cleanPeriodId = periodId.trim().ifBlank { DateAndFormatters.biweeklyPeriodIdForIso(startDate) }
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

            data class QuotaTargetWrite(
                val branch: String,
                val scale: Int
            )

            val writesToApply: List<QuotaTargetWrite> = if (cleanBranch == BranchOptions.ALL_BRANCHES_OPTION && applyToAllIndividualBranches) {
                BranchOptions.ALL_BRANCHES.map { QuotaTargetWrite(it, 1) } +
                    QuotaTargetWrite(BranchOptions.ALL_BRANCHES_OPTION, BranchOptions.ALL_BRANCHES.size)
            } else {
                listOf(QuotaTargetWrite(cleanBranch, 1))
            }

            val targetBranchesSet = writesToApply.map { it.branch }.toSet()
            val targetDocIds = writesToApply.associate { it.branch to buildQuotaDocId(cleanPeriodId, it.branch) }

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

            val existingById = existingActive.documents.associateBy { it.id }
            for (target in writesToApply) {
                val docId = targetDocIds.getValue(target.branch)
                val docRef = db.collection(path).document(docId)
                val existingSnap = existingById[docId] ?: docRef.get().await()
                val scaledGlobal = (targetGlobalSales * target.scale).coerceAtMost(1_000_000_000.0)
                val scaledCredit = (targetCreditSales.coerceAtLeast(0.0) * target.scale).coerceAtMost(1_000_000_000.0)
                val scaledCustomers = (targetNewCustomers.coerceAtLeast(0L) * target.scale).coerceAtMost(100_000L)
                val scaledAppMa = (targetAppMaOperations.coerceAtLeast(0L) * target.scale).coerceAtMost(100_000L)
                val scaledRequests = (targetNewCustomerRequests.coerceAtLeast(0L) * target.scale).coerceAtMost(100_000L)

                if (existingSnap.exists()) {
                    batch.update(
                        docRef,
                        mapOf(
                            "title" to cleanTitle,
                            "startDate" to startDate,
                            "endDate" to endDate,
                            "totalDays" to totalDays,
                            "periodId" to cleanPeriodId,
                            "branchName" to target.branch,
                            "targetGlobalSales" to scaledGlobal,
                            "targetCreditSales" to scaledCredit,
                            "targetNewCustomers" to scaledCustomers,
                            "targetAppMaOperations" to scaledAppMa,
                            "targetNewCustomerRequests" to scaledRequests,
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
                            "branchName" to target.branch,
                            "targetGlobalSales" to scaledGlobal,
                            "targetCreditSales" to scaledCredit,
                            "targetNewCustomers" to scaledCustomers,
                            "targetAppMaOperations" to scaledAppMa,
                            "targetNewCustomerRequests" to scaledRequests,
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

    private fun buildQuotaDocId(periodId: String, branchName: String): String {
        val branchSlug = branchName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "general" }
        return "quota_${periodId}_${branchSlug}".take(120)
    }

    suspend fun ensureDefaultQuotaForAdminIfMissing(): Result<Unit> {
        val path = "biweekly_quotas"
        return try {
            val adminUid = requireUserId()
            val activeSnap = db.collection(path)
                .whereEqualTo("active", true)
                .limit(1)
                .get()
                .await()
            if (activeSnap.isEmpty) {
                val (startDate, endDate) = DateAndFormatters.defaultBiweeklyRange()
                val periodId = DateAndFormatters.biweeklyPeriodIdForIso(startDate)
                val days = DateAndFormatters.daysBetweenInclusive(startDate, endDate).toLong().coerceIn(1L, 31L)
                val docId = buildQuotaDocId(periodId, BranchOptions.ALL_BRANCHES_OPTION)
                db.collection(path).document(docId).set(
                    mapOf(
                        "title" to DateAndFormatters.defaultBiweeklyTitle(),
                        "startDate" to startDate,
                        "endDate" to endDate,
                        "totalDays" to days,
                        "periodId" to periodId,
                        "branchName" to BranchOptions.ALL_BRANCHES_OPTION,
                        "targetGlobalSales" to 250_000.0,
                        "targetCreditSales" to 150_000.0,
                        "targetNewCustomers" to 45L,
                        "targetAppMaOperations" to 60L,
                        "targetNewCustomerRequests" to 75L,
                        "active" to true,
                        "createdBy" to adminUid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeSalesEntries(uid: String, isAdmin: Boolean): Flow<List<DailySalesEntry>> = flow {
        if (uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "sales_entries"
        try {
            val query = if (isAdmin) {
                db.collection(path)
            } else {
                db.collection(path).whereEqualTo("userId", uid)
            }
            emitAll(
                query.snapshots()
                    .map { snapshot ->
                        snapshot
                            .toObjects(DailySalesEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .sortedByDescending { it.dateString }
                    }
                    .catch { error ->
                        handleFlowError(error, OperationType.LIST, path, uid)
                        throw error
                    }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFlowError(e, OperationType.LIST, path, uid)
            throw e
        }
    }

    suspend fun upsertDailySalesEntry(
        quotaId: String,
        dateString: String,
        userName: String,
        branchName: String? = null,
        globalSales: Double,
        creditSales: Double,
        newCustomers: Long,
        appMaOperations: Long,
        newCustomerRequests: Long,
        notes: String,
        attachmentDraft: ClosingAttachmentDraft = ClosingAttachmentDraft.Unchanged
    ): Result<DailyClosingSaveResult> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Usuario no autenticado."))
        val docId = "${quotaId}_${dateString}_${uid}"
        val path = "sales_entries/$docId"
        return try {
            require(quotaId.isNotBlank()) { "No hay una meta quincenal activa." }
            require(globalSales >= 0.0 && creditSales >= 0.0) { "Los montos deben ser mayores o iguales a 0." }
            require(newCustomers >= 0L && appMaOperations >= 0L && newCustomerRequests >= 0L) {
                "Las unidades deben ser mayores o iguales a 0."
            }

            if (attachmentDraft is ClosingAttachmentDraft.Selected) {
                val att = attachmentDraft.attachment
                require(att.base64Chunks.size in 1..8) {
                    "El archivo o foto adjunta excede el tamaño máximo de 3.5 MB."
                }
                require(att.base64Chunks.all { it.isNotEmpty() && it.length <= 700_000 }) {
                    "El archivo o foto adjunta excede el tamaño permitido."
                }
            }

            val docRef = db.collection("sales_entries").document(docId)
            val existing = docRef.get().await()
            val cleanNotes = notes.trim().take(300)
            val cleanBranch = branchName?.trim()?.takeIf { it in BranchOptions.ALL_BRANCHES }

            val existingChunksSnap = if (attachmentDraft != ClosingAttachmentDraft.Unchanged && existing.exists()) {
                docRef.collection("attachment_chunks")
                    .whereEqualTo("userId", uid)
                    .get()
                    .await()
            } else {
                null
            }

            val batch = db.batch()

            if (!existing.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("userName", userName.trim().ifBlank { "Usuario" }.take(80))
                    if (cleanBranch != null) put("branchName", cleanBranch)
                    put("quotaId", quotaId)
                    put("dateString", dateString)
                    put("globalSales", globalSales)
                    put("creditSales", creditSales)
                    put("newCustomers", newCustomers)
                    put("appMaOperations", appMaOperations)
                    put("newCustomerRequests", newCustomerRequests)
                    put("notes", cleanNotes)
                    if (attachmentDraft is ClosingAttachmentDraft.Selected) {
                        val att = attachmentDraft.attachment
                        val cleanAttName = att.fileName.trim().ifBlank { "adjunto" }.take(120)
                        val cleanAttMime = att.mimeType.trim().ifBlank { "application/octet-stream" }.take(100)
                        put("attachmentName", cleanAttName)
                        put("attachmentMimeType", cleanAttMime)
                        put("attachmentChunkCount", att.base64Chunks.size.toLong())
                        put("attachmentSizeBytes", att.totalBytes.coerceIn(0, 3_500_000).toLong())
                        if (!att.isChunked) {
                            put("attachmentBase64", att.base64Chunks.first())
                        }
                    }
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                batch.set(docRef, payload)
            } else {
                val updatePayload = buildMap<String, Any> {
                    if (cleanBranch != null) put("branchName", cleanBranch)
                    put("globalSales", globalSales)
                    put("creditSales", creditSales)
                    put("newCustomers", newCustomers)
                    put("appMaOperations", appMaOperations)
                    put("newCustomerRequests", newCustomerRequests)
                    put("notes", cleanNotes)
                    when (attachmentDraft) {
                        ClosingAttachmentDraft.Unchanged -> Unit
                        ClosingAttachmentDraft.Removed -> {
                            put("attachmentName", FieldValue.delete())
                            put("attachmentMimeType", FieldValue.delete())
                            put("attachmentBase64", FieldValue.delete())
                            put("attachmentChunkCount", FieldValue.delete())
                            put("attachmentSizeBytes", FieldValue.delete())
                        }
                        is ClosingAttachmentDraft.Selected -> {
                            val att = attachmentDraft.attachment
                            val cleanAttName = att.fileName.trim().ifBlank { "adjunto" }.take(120)
                            val cleanAttMime = att.mimeType.trim().ifBlank { "application/octet-stream" }.take(100)
                            put("attachmentName", cleanAttName)
                            put("attachmentMimeType", cleanAttMime)
                            put("attachmentChunkCount", att.base64Chunks.size.toLong())
                            put("attachmentSizeBytes", att.totalBytes.coerceIn(0, 3_500_000).toLong())
                            if (!att.isChunked) {
                                put("attachmentBase64", att.base64Chunks.first())
                            } else {
                                put("attachmentBase64", FieldValue.delete())
                            }
                        }
                    }
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                batch.update(docRef, updatePayload)
            }

            when (attachmentDraft) {
                ClosingAttachmentDraft.Unchanged -> Unit
                ClosingAttachmentDraft.Removed -> {
                    existingChunksSnap?.documents?.forEach { chunkDoc ->
                        batch.delete(chunkDoc.reference)
                    }
                }
                is ClosingAttachmentDraft.Selected -> {
                    val att = attachmentDraft.attachment
                    val newChunkIds = if (att.isChunked) {
                        att.base64Chunks.indices.map { idx -> "chunk_$idx" }.toSet()
                    } else {
                        emptySet()
                    }
                    existingChunksSnap?.documents?.forEach { chunkDoc ->
                        if (chunkDoc.id !in newChunkIds) {
                            batch.delete(chunkDoc.reference)
                        }
                    }
                    if (att.isChunked) {
                        att.base64Chunks.forEachIndexed { idx, chunkBase64 ->
                            val chunkRef = docRef.collection("attachment_chunks").document("chunk_$idx")
                            batch.set(
                                chunkRef,
                                mapOf(
                                    "userId" to uid,
                                    "entryId" to docId,
                                    "chunkIndex" to idx,
                                    "dataBase64" to chunkBase64,
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                }
            }

            batch.commit().await()

            val saveSummary = try {
                val (periodStartIso, periodEndIso) = DateAndFormatters.biweeklyRangeForIso(dateString)
                val branchEntriesSnap = db.collection("sales_entries")
                    .whereEqualTo("userId", uid)
                    .get()
                    .await()
                val periodDocs = branchEntriesSnap
                    .toObjects(DailySalesEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { entry ->
                        val sameBranch = cleanBranch == null ||
                            entry.branchName == null ||
                            entry.branchName == cleanBranch
                        sameBranch && entry.dateString in periodStartIso..periodEndIso
                    }
                    .sortedWith(
                        compareByDescending<DailySalesEntry> { it.dateString }
                            .thenByDescending { it.updatedAt?.seconds ?: 0L }
                    )
                    .distinctBy { it.dateString }
                DailyClosingSaveResult(
                    branchAccumulatedGlobalSales = periodDocs.sumOf { it.globalSales },
                    branchPeriodEntriesCount = periodDocs.size
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                DailyClosingSaveResult(
                    branchAccumulatedGlobalSales = globalSales,
                    branchPeriodEntriesCount = 1
                )
            }

            Result.success(saveSummary)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun loadSalesEntryAttachmentChunks(entry: DailySalesEntry): Result<List<String>> {
        val inline = entry.attachmentBase64?.takeIf { it.isNotBlank() }
        if (inline != null) {
            return Result.success(listOf(inline))
        }
        if (entry.id.isBlank() || entry.attachmentChunkCount <= 0L) {
            return Result.failure(IllegalStateException("El archivo adjunto no está disponible."))
        }
        val path = "sales_entries/${entry.id}/attachment_chunks"
        return try {
            requireUserId()
            val snapshot = db.collection("sales_entries")
                .document(entry.id)
                .collection("attachment_chunks")
                .whereEqualTo("userId", entry.userId)
                .get()
                .await()
            val orderedChunks = snapshot.documents
                .sortedBy { (it.get("chunkIndex") as? Number)?.toInt() ?: 0 }
                .mapNotNull { it.getString("dataBase64")?.takeIf { b -> b.isNotBlank() } }
            require(orderedChunks.isNotEmpty()) {
                "No se encontraron fragmentos del archivo adjunto."
            }
            Result.success(orderedChunks)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    private fun handleFlowError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        uid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        val activeUid = runCatching { auth.currentUser?.uid }.getOrNull()
        if (exceptionToLog != null && activeUid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
