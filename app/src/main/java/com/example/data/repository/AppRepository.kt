package com.example.data.repository

import android.content.Context
import com.example.data.firestore.FirestoreProvider
import com.example.data.firestore.OperationType
import com.example.data.firestore.SnapshotSyncResult
import com.example.data.firestore.WriteOutcome
import com.example.data.firestore.awaitWriteOfflineAware
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.AssignedTask
import com.example.data.model.BiweeklyQuota
import com.example.data.model.BranchOptions
import com.example.data.model.DailySalesEntry
import com.example.data.model.TaskEvidence
import com.example.data.model.TaskNotification
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.TaskType
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.utils.ConnectivityChecker
import com.example.utils.DateAndFormatters
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private const val BOOTSTRAPPED_ADMIN_EMAIL = "arias.francisco.cv@gmail.com"

class AppRepository(
    private val db: FirebaseFirestore,
    private val isOnlineCheck: () -> Boolean = { true }
) {

    constructor(
        context: Context,
        connectivityChecker: ConnectivityChecker? = null
    ) : this(
        db = FirestoreProvider.getInstance(context),
        isOnlineCheck = { connectivityChecker?.isCurrentlyOnline() ?: true }
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("El usuario debe iniciar sesión con Google primero.")

    private fun isBootstrappedAdmin(email: String?, emailVerified: Boolean): Boolean =
        email == BOOTSTRAPPED_ADMIN_EMAIL && emailVerified

    private suspend fun getDocumentOfflineAware(docRef: DocumentReference): DocumentSnapshot? {
        if (!isOnlineCheck()) {
            return runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
        }
        val serverOrDefault = runCatching {
            withTimeoutOrNull(3_500L) { docRef.get(Source.DEFAULT).await() }
        }.getOrNull()
        return serverOrDefault ?: runCatching { docRef.get(Source.CACHE).await() }.getOrNull()
    }

    private suspend fun getQueryOfflineAware(query: Query): QuerySnapshot {
        if (!isOnlineCheck()) {
            return query.get(Source.CACHE).await()
        }
        val onlineSnap = runCatching {
            withTimeoutOrNull(4_000L) { query.get(Source.DEFAULT).await() }
        }.getOrNull()
        return onlineSnap ?: query.get(Source.CACHE).await()
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
            val online = isOnlineCheck()
            val snapshot = getDocumentOfflineAware(userRef)

            if (!online) {
                // Offline: keep cached profile intact so app launches cleanly without network errors
                return Result.success(Unit)
            }

            val safeName = displayName?.trim()?.takeIf { it.isNotEmpty() }?.take(80)
                ?: email?.substringBefore("@")?.take(80)?.ifBlank { "Usuario MA" }
                ?: "Usuario MA"
            val safeEmail = email?.trim()?.takeIf { it.length >= 3 }?.take(120) ?: "usuario@mueblesamerica.mx"
            val safePhoto = photoUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(500)
            val bootstrapped = isBootstrappedAdmin(safeEmail, emailVerified)

            if (snapshot == null || !snapshot.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", safeName)
                    put("email", safeEmail)
                    if (safePhoto != null) put("photoUrl", safePhoto)
                    put("role", if (bootstrapped) UserRole.ADMIN.name else UserRole.EMPLOYEE.name)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                awaitWriteOfflineAware(
                    task = userRef.set(payload),
                    isOnline = online,
                    operationType = OperationType.CREATE,
                    path = docPath
                )
            } else {
                val updateMap = buildMap<String, Any> {
                    put("displayName", safeName)
                    if (safePhoto != null) put("photoUrl", safePhoto)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                awaitWriteOfflineAware(
                    task = userRef.update(updateMap),
                    isOnline = online,
                    operationType = OperationType.UPDATE,
                    path = docPath
                )
            }

            if (bootstrapped) {
                val adminRef = db.collection("admins").document(uid)
                val adminSnap = getDocumentOfflineAware(adminRef)
                if (adminSnap == null || !adminSnap.exists()) {
                    awaitWriteOfflineAware(
                        task = adminRef.set(
                            mapOf(
                                "userId" to uid,
                                "email" to safeEmail,
                                "grantedBy" to uid,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        ),
                        isOnline = online,
                        operationType = OperationType.CREATE,
                        path = "admins/$uid"
                    )
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

    suspend fun updateCurrentUserBranch(branchName: String): Result<WriteOutcome<Unit>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Usuario no autenticado."))
        val path = "users/$uid"
        return try {
            require(branchName in BranchOptions.ALL_BRANCHES) {
                "Selecciona una sucursal válida."
            }
            val online = isOnlineCheck()
            val synced = awaitWriteOfflineAware(
                task = db.collection("users").document(uid)
                    .update(
                        mapOf(
                            "branchName" to branchName,
                            "displayName" to branchName,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ),
                isOnline = online,
                operationType = OperationType.UPDATE,
                path = path
            )
            Result.success(WriteOutcome(Unit, synced))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeCurrentUserProfile(uid: String): Flow<UserProfile?> = flow {
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    fun observeIsAdmin(uid: String, email: String?, emailVerified: Boolean): Flow<Boolean> {
        if (isBootstrappedAdmin(email, emailVerified)) {
            return flowOf(true)
        }
        val path = "admins/$uid"
        return db.collection("admins").document(uid)
            .snapshots()
            .map { it.exists() }
            .catch { error ->
                handleFlowError(error, OperationType.GET, path, uid)
                throw error
            }
    }

    fun observeTeamUsers(uid: String, isAdmin: Boolean): Flow<List<UserProfile>> {
        if (!isAdmin) return flowOf(emptyList())
        val path = "users"
        return db.collection(path)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.displayName.lowercase() }
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun setUserRole(targetUser: UserProfile, makeAdmin: Boolean): Result<WriteOutcome<Unit>> {
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
            val synced = awaitWriteOfflineAware(
                task = batch.commit(),
                isOnline = isOnlineCheck(),
                operationType = OperationType.UPDATE,
                path = path
            )
            Result.success(WriteOutcome(Unit, synced))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeTasksWithSync(uid: String, isAdmin: Boolean): Flow<SnapshotSyncResult<AssignedTask>> {
        val path = "tasks"
        val query = if (isAdmin) {
            db.collection(path)
        } else {
            db.collection(path).whereEqualTo("assigneeId", uid)
        }
        return query.snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                val pendingIds = snapshot.documents
                    .filter { it.metadata.hasPendingWrites() }
                    .map { it.id }
                    .toSet()
                val sorted = snapshot
                    .toObjects(AssignedTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(
                        compareBy<AssignedTask> { it.parsedStatus == TaskStatus.COMPLETED }
                            .thenByDescending { it.parsedType == TaskType.EXTRAORDINARY }
                            .thenByDescending { it.parsedPriority == TaskPriority.URGENT }
                            .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                    )
                SnapshotSyncResult(items = sorted, pendingWriteIds = pendingIds)
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, path, uid)
                throw error
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
    ): Result<WriteOutcome<Int>> {
        val path = "tasks"
        return try {
            val adminUid = requireUserId()
            val cleanTitle = title.trim()
            val cleanDesc = description.trim().take(500)
            require(cleanTitle.length in 3..120) { "El título debe tener entre 3 y 120 caracteres." }
            require(assignees.isNotEmpty()) { "Selecciona al menos un usuario asignado." }

            val batch = db.batch()
            assignees.forEach { assignee ->
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
            val synced = awaitWriteOfflineAware(
                task = batch.commit(),
                isOnline = isOnlineCheck(),
                operationType = OperationType.CREATE,
                path = path
            )
            Result.success(WriteOutcome(assignees.size, synced))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun seedDailyChecklistForUser(targetUser: UserProfile): Result<WriteOutcome<Int>> {
        return syncScheduledTasksForAssignee(
            assigneeId = targetUser.userId,
            assigneeName = targetUser.effectiveBranchLabel
        )
    }

    suspend fun syncScheduledTasksForCurrentUser(branchLabel: String): Result<WriteOutcome<Int>> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Usuario no autenticado."))
        return syncScheduledTasksForAssignee(
            assigneeId = uid,
            assigneeName = branchLabel
        )
    }

    private suspend fun syncScheduledTasksForAssignee(
        assigneeId: String,
        assigneeName: String
    ): Result<WriteOutcome<Int>> {
        return try {
            val actorUid = requireUserId()
            val today = DateAndFormatters.todayIso()
            val expected = ScheduledTasksCatalog.tasksToEnsureOnDate(today)

            val existingIds = getQueryOfflineAware(
                db.collection("tasks").whereEqualTo("assigneeId", assigneeId)
            )
                .documents
                .map { it.id }
                .toSet()

            val safeName = assigneeName.trim().ifBlank { "Sucursal" }.take(80)
            val missing = expected.filter { (assignedDate, spec) ->
                val deterministicId = "sched_${assigneeId}_${assignedDate}_${spec.slug}"
                deterministicId !in existingIds
            }

            if (missing.isEmpty()) {
                return Result.success(WriteOutcome(0, true))
            }

            val batch = db.batch()
            missing.forEach { (assignedDate, spec) ->
                val deterministicId = "sched_${assigneeId}_${assignedDate}_${spec.slug}"
                val ref = db.collection("tasks").document(deterministicId)
                batch.set(
                    ref,
                    mapOf(
                        "assigneeId" to assigneeId,
                        "assigneeName" to safeName,
                        "createdBy" to actorUid,
                        "title" to spec.title,
                        "description" to spec.description,
                        "taskType" to TaskType.DAILY.name,
                        "priority" to TaskPriority.NORMAL.name,
                        "dueDate" to spec.dueDateIso,
                        "dueTime" to spec.dueTime,
                        "deadlineLabel" to spec.deadlineLabel,
                        "status" to TaskStatus.PENDING.name,
                        "hasPhotoEvidence" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            val synced = awaitWriteOfflineAware(
                task = batch.commit(),
                isOnline = isOnlineCheck(),
                operationType = OperationType.CREATE,
                path = "tasks"
            )
            Result.success(WriteOutcome(missing.size, synced))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "tasks")
            Result.failure(e)
        }
    }

    suspend fun completeTaskWithPhotoEvidence(
        task: AssignedTask,
        uploaderName: String,
        imageBase64: String
    ): Result<WriteOutcome<Unit>> {
        val docPath = "tasks/${task.id}"
        return try {
            val uid = requireUserId()
            require(task.id.isNotBlank()) { "ID de tarea inválido." }
            require(!DateAndFormatters.isTaskPastDeadline(task.dueDate, task.dueTime)) {
                "El plazo límite para completar esta tarea ha vencido (${task.deadlineLabel ?: task.dueDate})."
            }
            require(imageBase64.length in 50..700_000) { "La evidencia fotográfica no es válida." }

            val evidenceRef = db.collection("task_evidences").document()
            val taskRef = db.collection("tasks").document(task.id)

            val batch = db.batch()
            batch.set(
                evidenceRef,
                mapOf(
                    "taskId" to task.id,
                    "uploaderId" to uid,
                    "uploaderName" to uploaderName.trim().ifBlank { "Empleado" }.take(80),
                    "taskTitle" to task.title.trim().ifBlank { "Tarea" }.take(120),
                    "imageBase64" to imageBase64,
                    "capturedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                taskRef,
                mapOf(
                    "status" to TaskStatus.COMPLETED.name,
                    "hasPhotoEvidence" to true,
                    "evidenceId" to evidenceRef.id,
                    "completedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val synced = awaitWriteOfflineAware(
                task = batch.commit(),
                isOnline = isOnlineCheck(),
                operationType = OperationType.UPDATE,
                path = docPath
            )
            Result.success(WriteOutcome(Unit, synced))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun observeEvidences(uid: String, isAdmin: Boolean): Flow<List<TaskEvidence>> {
        // Non-admin employees NEVER read /task_evidences; enforced both here and in firestore.rules
        if (!isAdmin) return flowOf(emptyList())
        val path = "task_evidences"
        return db.collection(path)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(TaskEvidence::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.capturedAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    fun observeNotifications(uid: String): Flow<List<TaskNotification>> {
        val path = "notifications"
        return db.collection(path)
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
    }

    suspend fun acknowledgeNotification(notificationId: String): Result<Unit> {
        val path = "notifications/$notificationId"
        return try {
            requireUserId()
            require(notificationId.isNotBlank())
            awaitWriteOfflineAware(
                task = db.collection("notifications").document(notificationId)
                    .update(
                        mapOf(
                            "acknowledged" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ),
                isOnline = isOnlineCheck(),
                operationType = OperationType.UPDATE,
                path = path
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeActiveQuota(uid: String): Flow<BiweeklyQuota?> {
        val path = "biweekly_quotas"
        return db.collection(path)
            .whereEqualTo("active", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(BiweeklyQuota::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .maxByOrNull { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, path, uid)
                throw error
            }
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
        targetNewCustomerRequests: Long
    ): Result<WriteOutcome<Unit>> {
        val path = "biweekly_quotas"
        return try {
            val adminUid = requireUserId()
            val cleanTitle = title.trim()
            require(cleanTitle.length in 3..80) { "El nombre de la quincena debe tener entre 3 y 80 caracteres." }
            require(targetGlobalSales > 0.0) { "La meta de venta global debe ser mayor a 0." }
            require(totalDays in 1L..31L) { "La duración debe estar entre 1 y 31 días." }

            val existingActive = getQueryOfflineAware(
                db.collection(path).whereEqualTo("active", true)
            )

            val batch = db.batch()
            existingActive.documents.forEach { doc ->
                batch.update(
                    doc.reference,
                    mapOf(
                        "active" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val newRef = db.collection(path).document()
            batch.set(
                newRef,
                mapOf(
                    "title" to cleanTitle,
                    "startDate" to startDate,
                    "endDate" to endDate,
                    "totalDays" to totalDays,
                    "targetGlobalSales" to targetGlobalSales,
                    "targetCreditSales" to targetCreditSales.coerceAtLeast(0.0),
                    "targetNewCustomers" to targetNewCustomers.coerceAtLeast(0L),
                    "targetAppMaOperations" to targetAppMaOperations.coerceAtLeast(0L),
                    "targetNewCustomerRequests" to targetNewCustomerRequests.coerceAtLeast(0L),
                    "active" to true,
                    "createdBy" to adminUid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val synced = awaitWriteOfflineAware(
                task = batch.commit(),
                isOnline = isOnlineCheck(),
                operationType = OperationType.WRITE,
                path = path
            )
            Result.success(WriteOutcome(Unit, synced))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun ensureDefaultQuotaForAdminIfMissing(): Result<Unit> {
        val path = "biweekly_quotas"
        return try {
            if (!isOnlineCheck()) {
                return Result.success(Unit)
            }
            val adminUid = requireUserId()
            val activeSnap = getQueryOfflineAware(
                db.collection(path).whereEqualTo("active", true).limit(1)
            )
            if (activeSnap.isEmpty) {
                val (startDate, endDate) = DateAndFormatters.defaultBiweeklyRange()
                val days = DateAndFormatters.daysBetweenInclusive(startDate, endDate).toLong().coerceIn(1L, 31L)
                awaitWriteOfflineAware(
                    task = db.collection(path).document().set(
                        mapOf(
                            "title" to DateAndFormatters.defaultBiweeklyTitle(),
                            "startDate" to startDate,
                            "endDate" to endDate,
                            "totalDays" to days,
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
                    ),
                    isOnline = true,
                    operationType = OperationType.CREATE,
                    path = path
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeSalesEntriesWithSync(uid: String, isAdmin: Boolean): Flow<SnapshotSyncResult<DailySalesEntry>> {
        val path = "sales_entries"
        val query = if (isAdmin) {
            db.collection(path)
        } else {
            db.collection(path).whereEqualTo("userId", uid)
        }
        return query.snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                val pendingIds = snapshot.documents
                    .filter { it.metadata.hasPendingWrites() }
                    .map { it.id }
                    .toSet()
                val sorted = snapshot
                    .toObjects(DailySalesEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.dateString }
                SnapshotSyncResult(items = sorted, pendingWriteIds = pendingIds)
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, path, uid)
                throw error
            }
    }

    suspend fun upsertDailySalesEntry(
        quotaId: String,
        dateString: String,
        userName: String,
        globalSales: Double,
        creditSales: Double,
        newCustomers: Long,
        appMaOperations: Long,
        newCustomerRequests: Long,
        notes: String,
        attachmentName: String? = null,
        attachmentMimeType: String? = null,
        attachmentBase64: String? = null
    ): Result<WriteOutcome<Unit>> {
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
            if (attachmentBase64 != null) {
                require(attachmentBase64.length in 50..700_000) {
                    "El archivo o foto adjunta excede el tamaño permitido."
                }
            }

            val docRef = db.collection("sales_entries").document(docId)
            val existing = getDocumentOfflineAware(docRef)
            val cleanNotes = notes.trim().take(300)
            val cleanAttName = attachmentName?.trim()?.takeIf { it.isNotEmpty() }?.take(120)
            val cleanAttMime = attachmentMimeType?.trim()?.takeIf { it.isNotEmpty() }?.take(100)
            val cleanAttBase64 = attachmentBase64?.takeIf { it.isNotBlank() }

            val online = isOnlineCheck()
            val writeTask = if (existing == null || !existing.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("userName", userName.trim().ifBlank { "Usuario" }.take(80))
                    put("quotaId", quotaId)
                    put("dateString", dateString)
                    put("globalSales", globalSales)
                    put("creditSales", creditSales)
                    put("newCustomers", newCustomers)
                    put("appMaOperations", appMaOperations)
                    put("newCustomerRequests", newCustomerRequests)
                    put("notes", cleanNotes)
                    if (cleanAttName != null && cleanAttMime != null && cleanAttBase64 != null) {
                        put("attachmentName", cleanAttName)
                        put("attachmentMimeType", cleanAttMime)
                        put("attachmentBase64", cleanAttBase64)
                    }
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload)
            } else {
                val updatePayload = buildMap<String, Any> {
                    put("globalSales", globalSales)
                    put("creditSales", creditSales)
                    put("newCustomers", newCustomers)
                    put("appMaOperations", appMaOperations)
                    put("newCustomerRequests", newCustomerRequests)
                    put("notes", cleanNotes)
                    if (cleanAttName != null && cleanAttMime != null && cleanAttBase64 != null) {
                        put("attachmentName", cleanAttName)
                        put("attachmentMimeType", cleanAttMime)
                        put("attachmentBase64", cleanAttBase64)
                    } else {
                        put("attachmentName", FieldValue.delete())
                        put("attachmentMimeType", FieldValue.delete())
                        put("attachmentBase64", FieldValue.delete())
                    }
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updatePayload)
            }

            val synced = awaitWriteOfflineAware(
                task = writeTask,
                isOnline = online,
                operationType = OperationType.WRITE,
                path = path
            )
            Result.success(WriteOutcome(Unit, synced))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
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
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
