package com.example.data.repository

import android.content.Context
import com.example.data.firestore.FirestoreProvider
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.AssignedTask
import com.example.data.model.BiweeklyQuota
import com.example.data.model.BranchOptions
import com.example.data.model.ClosingAttachmentDraft
import com.example.data.model.DailySalesEntry
import com.example.data.model.TaskEvidence
import com.example.data.model.TaskEvidenceAttachmentFile
import com.example.data.model.TaskNotification
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.TaskType
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private const val BOOTSTRAPPED_ADMIN_EMAIL = "arias.francisco.cv@gmail.com"

class AppRepository(
    private val db: FirebaseFirestore
) {

    constructor(context: Context) : this(FirestoreProvider.getInstance(context))

    private val auth = Firebase.auth

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("El usuario debe iniciar sesión con Google primero.")

    private fun isBootstrappedAdmin(email: String?, emailVerified: Boolean): Boolean =
    email?.trim()?.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true) == true && emailVerified
    

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

    fun observeTasks(uid: String, isAdmin: Boolean): Flow<List<AssignedTask>> {
        val path = "tasks"
        val query = if (isAdmin) {
            db.collection(path)
        } else {
            db.collection(path).whereEqualTo("assigneeId", uid)
        }
        return query.snapshots()
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
            batch.commit().await()
            Result.success(assignees.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun seedDailyChecklistForUser(targetUser: UserProfile): Result<Int> {
        return syncScheduledTasksForAssignee(
            assigneeId = targetUser.userId,
            assigneeName = targetUser.effectiveBranchLabel,
            skipIfDayAlreadyCreated = false
        )
    }

    suspend fun syncScheduledTasksForCurrentUser(branchLabel: String): Result<Int> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Usuario no autenticado."))
        return syncScheduledTasksForAssignee(
            assigneeId = uid,
            assigneeName = branchLabel,
            skipIfDayAlreadyCreated = true
        )
    }

    private suspend fun syncScheduledTasksForAssignee(
        assigneeId: String,
        assigneeName: String,
        skipIfDayAlreadyCreated: Boolean
    ): Result<Int> {
        return try {
            val actorUid = requireUserId()
            val today = DateAndFormatters.todayIso()
            val expected = ScheduledTasksCatalog.tasksToEnsureOnDate(today)

            val existingIds = db.collection("tasks")
                .whereEqualTo("assigneeId", assigneeId)
                .get()
                .await()
                .documents
                .map { it.id }
                .toSet()

            val todayPrefix = "sched_${assigneeId}_${today}_"
            if (skipIfDayAlreadyCreated && existingIds.any { it.startsWith(todayPrefix) }) {
                return Result.success(0)
            }

            val safeName = assigneeName.trim().ifBlank { "Sucursal" }.take(80)
            val missing = expected.filter { (assignedDate, spec) ->
                val deterministicId = "sched_${assigneeId}_${assignedDate}_${spec.slug}"
                deterministicId !in existingIds
            }

            if (missing.isEmpty()) {
                return Result.success(0)
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
            require(branchUsers.isNotEmpty()) {
                "No hay usuarios registrados en $branchName para confirmar el reinicio."
            }

            val today = DateAndFormatters.todayIso()
            val expectedToday = ScheduledTasksCatalog.tasksToEnsureOnDate(today)
            var recreatedTotal = 0

            for (user in branchUsers) {
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

                val safeName = branchName.trim().ifBlank { user.effectiveBranchLabel }.take(80)
                expectedToday.forEach { (assignedDate, spec) ->
                    val deterministicId = "sched_${user.userId}_${assignedDate}_${spec.slug}"
                    val taskRef = db.collection("tasks").document(deterministicId)
                    createBatch.set(
                        taskRef,
                        mapOf(
                            "assigneeId" to user.userId,
                            "assigneeName" to safeName,
                            "createdBy" to adminUid,
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

    suspend fun updateTasks(
        tasks: List<AssignedTask>,
        title: String,
        description: String,
        taskType: TaskType,
        priority: TaskPriority,
        dueDate: String,
        dueTime: String?,
        deadlineLabel: String?
    ): Result<Int> {
        val path = "tasks"
        return try {
            requireUserId()
            require(tasks.isNotEmpty()) { "No se encontraron tareas para actualizar." }
            val cleanTitle = title.trim()
            val cleanDesc = description.trim().take(500)
            val cleanDueDate = dueDate.trim()
            val cleanDueTime = dueTime?.trim()?.takeIf { it.isNotEmpty() }
            val cleanDeadlineLabel = deadlineLabel?.trim()?.take(80)?.takeIf { it.isNotEmpty() }

            require(cleanTitle.length in 3..120) {
                "El título debe tener entre 3 y 120 caracteres."
            }
            require(cleanDueDate.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) {
                "La fecha límite debe tener formato AAAA-MM-DD."
            }
            if (cleanDueTime != null) {
                require(cleanDueTime.matches(Regex("""^([01]\d|2[0-3]):[0-5]\d$"""))) {
                    "La hora límite debe tener formato HH:mm (ej. 14:30 o 23:59)."
                }
            }

            tasks.chunked(380).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { task ->
                    val ref = db.collection("tasks").document(task.id)
                    val updatePayload = buildMap<String, Any> {
                        put("title", cleanTitle)
                        put("description", cleanDesc)
                        put("taskType", taskType.name)
                        put("priority", priority.name)
                        put("dueDate", cleanDueDate)
                        if (cleanDueTime != null) {
                            put("dueTime", cleanDueTime)
                        } else if (task.dueTime != null) {
                            put("dueTime", FieldValue.delete())
                        }
                        if (cleanDeadlineLabel != null) {
                            put("deadlineLabel", cleanDeadlineLabel)
                        } else if (task.deadlineLabel != null) {
                            put("deadlineLabel", FieldValue.delete())
                        }
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    batch.update(ref, updatePayload)
                }
                batch.commit().await()
            }
            Result.success(tasks.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTasks(tasks: List<AssignedTask>): Result<Int> {
        val path = "tasks"
        return try {
            requireUserId()
            require(tasks.isNotEmpty()) { "No se encontraron tareas para eliminar." }

            val refsToDelete = mutableListOf<com.google.firebase.firestore.DocumentReference>()
            for (task in tasks) {
                if (task.id.isBlank()) continue
                refsToDelete.add(db.collection("tasks").document(task.id))

                val evId = task.evidenceId?.takeIf { it.isNotBlank() }
                if (evId != null) {
                    val fileDocs = db.collection("task_evidences")
                        .document(evId)
                        .collection("files")
                        .get()
                        .await()
                    fileDocs.documents.forEach { refsToDelete.add(it.reference) }
                    refsToDelete.add(db.collection("task_evidences").document(evId))
                }

                val notifDocs = db.collection("notifications")
                    .whereEqualTo("taskId", task.id)
                    .get()
                    .await()
                notifDocs.documents.forEach { refsToDelete.add(it.reference) }
            }

            refsToDelete.chunked(380).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { ref -> batch.delete(ref) }
                batch.commit().await()
            }
            Result.success(tasks.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
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
    ): Result<Unit> {
        val path = "biweekly_quotas"
        return try {
            val adminUid = requireUserId()
            val cleanTitle = title.trim()
            require(cleanTitle.length in 3..80) { "El nombre de la quincena debe tener entre 3 y 80 caracteres." }
            require(targetGlobalSales > 0.0) { "La meta de venta global debe ser mayor a 0." }
            require(totalDays in 1L..31L) { "La duración debe estar entre 1 y 31 días." }

            val existingActive = db.collection(path)
                .whereEqualTo("active", true)
                .get()
                .await()

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
            batch.commit().await()
            Result.success(Unit)
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
            val adminUid = requireUserId()
            val activeSnap = db.collection(path)
                .whereEqualTo("active", true)
                .limit(1)
                .get()
                .await()
            if (activeSnap.isEmpty) {
                val (startDate, endDate) = DateAndFormatters.defaultBiweeklyRange()
                val days = DateAndFormatters.daysBetweenInclusive(startDate, endDate).toLong().coerceIn(1L, 31L)
                db.collection(path).document().set(
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

    fun observeSalesEntries(uid: String, isAdmin: Boolean): Flow<List<DailySalesEntry>> {
        val path = "sales_entries"
        val query = if (isAdmin) {
            db.collection(path)
        } else {
            db.collection(path).whereEqualTo("userId", uid)
        }
        return query.snapshots()
            .map { snapshot ->
                snapshot
                    .toObjects(DailySalesEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.dateString }
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
        attachmentDraft: ClosingAttachmentDraft = ClosingAttachmentDraft.Unchanged
    ): Result<Unit> {
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

            Result.success(Unit)
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
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
