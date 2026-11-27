package com.example.data.repository

import android.content.Context
import com.example.data.firestore.FirestoreProvider
import com.example.data.firestore.OperationType
import com.example.data.firestore.handleFirestoreError
import com.example.data.model.AssignedTask
import com.example.data.model.BiweeklyQuota
import com.example.data.model.BranchOptions
import com.example.data.model.BranchTaskCompletion
import com.example.data.model.BranchTaskLockHelper
import com.example.data.model.ClosingAttachmentDraft
import com.example.data.model.DailyClosingSaveResult
import com.example.data.model.DailySalesEntry
import com.example.data.model.EmployeeIdentity
import com.example.data.model.EmployeeIdentityValidation
import com.example.data.model.TaskStatus
import com.example.utils.DateAndFormatters
import com.example.utils.EncodedAttachment
import com.example.utils.ImageCompressor
import com.google.firebase.Firebase
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

class BranchTaskAlreadyCompletedException(
    val completedByPayroll: String,
    val completedByName: String,
    val branchName: String,
    message: String
) : IllegalStateException(message)

/**
 * Repository for mandatory employee identification cards ("Ficha de Identificación de Empleado"),
 * payroll uniqueness enforcement, admin-exclusive branch release / deletion, and identity stamping
 * on sales closings and task evidences without modifying protected repository files.
 */
class EmployeeIdentityRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(FirestoreProvider.getInstance(context))

    private val auth = Firebase.auth

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("El usuario debe iniciar sesión con Google primero.")

    fun observeCurrentEmployeeIdentity(uid: String): Flow<EmployeeIdentity?> = flow {
        if (uid.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "$COLLECTION_IDENTITIES/$uid"
        try {
            emitAll(
                db.collection(COLLECTION_IDENTITIES).document(uid)
                    .snapshots()
                    .map { snap ->
                        if (snap.exists()) {
                            snap.toObject(EmployeeIdentity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        } else {
                            null
                        }
                    }
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

    fun observeAllEmployeeIdentities(uid: String, isAdmin: Boolean): Flow<List<EmployeeIdentity>> = flow {
        if (!isAdmin || uid.isBlank()) {
            emit(emptyList())
            return@flow
        }
        try {
            emitAll(
                db.collection(COLLECTION_IDENTITIES)
                    .snapshots()
                    .map { snapshot ->
                        snapshot.toObjects(EmployeeIdentity::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            .filter { it.hasIdentityCore }
                            .sortedBy { it.fullName.lowercase() }
                    }
                    .catch { error ->
                        handleFlowError(error, OperationType.LIST, COLLECTION_IDENTITIES, uid)
                        throw error
                    }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFlowError(e, OperationType.LIST, COLLECTION_IDENTITIES, uid)
            throw e
        }
    }

    fun observeBranchTaskCompletions(
        uid: String,
        isAdmin: Boolean,
        branchName: String?
    ): Flow<List<BranchTaskCompletion>> = flow {
        if (uid.isBlank() || (!isAdmin && branchName.isNullOrBlank())) {
            emit(emptyList())
            return@flow
        }
        try {
            val query = if (isAdmin) {
                db.collection(COLLECTION_BRANCH_COMPLETIONS)
            } else {
                db.collection(COLLECTION_BRANCH_COMPLETIONS)
                    .whereEqualTo("branchName", branchName)
            }
            emitAll(
                query.snapshots()
                    .map { snapshot ->
                        snapshot.toObjects(
                            BranchTaskCompletion::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                    .catch { error ->
                        handleFlowError(error, OperationType.LIST, COLLECTION_BRANCH_COMPLETIONS, uid)
                        throw error
                    }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFlowError(e, OperationType.LIST, COLLECTION_BRANCH_COMPLETIONS, uid)
            throw e
        }
    }

    suspend fun registerEmployeeIdentity(
        fullName: String,
        payrollNumberRaw: String,
        branchName: String,
        email: String?
    ): Result<EmployeeIdentity> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Usuario no autenticado."))
        val path = "$COLLECTION_IDENTITIES/$uid"
        return try {
            val validationError = EmployeeIdentityValidation.validateFullRegistration(
                fullName = fullName,
                payrollNumberRaw = payrollNumberRaw,
                branchName = branchName
            )
            require(validationError == null) { validationError ?: "Datos inválidos." }

            val cleanName = fullName.trim().take(80)
            val cleanPayroll = EmployeeIdentityValidation.cleanPayrollInput(payrollNumberRaw)
            val payrollKey = EmployeeIdentityValidation.normalizePayrollKey(payrollNumberRaw)
            val safeEmail = email?.trim()?.takeIf { it.length >= 3 }?.take(120)
                ?: auth.currentUser?.email?.trim()?.takeIf { it.length >= 3 }?.take(120)
                ?: "usuario@mueblesamerica.mx"

            val identityRef = db.collection(COLLECTION_IDENTITIES).document(uid)
            val existingIdentity = identityRef.get().await()
            if (existingIdentity.exists() &&
                !existingIdentity.getString("payrollNumber").isNullOrBlank() &&
                !existingIdentity.getString("fullName").isNullOrBlank()
            ) {
                return Result.failure(
                    IllegalStateException("Tu ficha de identificación ya fue registrada. Solo el administrador puede liberar tu sucursal o eliminar la ficha.")
                )
            }

            val payrollRef = db.collection(COLLECTION_PAYROLLS).document(payrollKey)
            val existingPayrollSnap = payrollRef.get().await()
            if (existingPayrollSnap.exists()) {
                val ownerUid = existingPayrollSnap.getString("userId")
                if (ownerUid != null && ownerUid != uid) {
                    return Result.failure(
                        IllegalStateException("El número de Nómina $cleanPayroll ya está asignado a otro usuario activo.")
                    )
                }
            }

            val batch = db.batch()
            batch.set(
                identityRef,
                mapOf(
                    "userId" to uid,
                    "email" to safeEmail,
                    "fullName" to cleanName,
                    "payrollNumber" to cleanPayroll,
                    "payrollKey" to payrollKey,
                    "branchName" to branchName,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                payrollRef,
                mapOf(
                    "payrollNumber" to cleanPayroll,
                    "userId" to uid,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            try {
                batch.commit().await()
            } catch (firestoreErr: FirebaseFirestoreException) {
                if (firestoreErr.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                    firestoreErr.code == FirebaseFirestoreException.Code.ALREADY_EXISTS
                ) {
                    val recheck = runCatching { payrollRef.get().await() }.getOrNull()
                    if (recheck != null && recheck.exists() && recheck.getString("userId") != uid) {
                        return Result.failure(
                            IllegalStateException("El número de Nómina $cleanPayroll ya está asignado a otro usuario activo.")
                        )
                    }
                }
                throw firestoreErr
            }

            // Keep /users/{uid}.branchName synced so existing checklist & sales rules/queries work seamlessly.
            runCatching {
                val userRef = db.collection("users").document(uid)
                if (userRef.get().await().exists()) {
                    userRef.update(
                        mapOf(
                            "branchName" to branchName,
                            "displayName" to branchName,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            Result.success(
                EmployeeIdentity(
                    id = uid,
                    userId = uid,
                    email = safeEmail,
                    fullName = cleanName,
                    payrollNumber = cleanPayroll,
                    payrollKey = payrollKey,
                    branchName = branchName,
                    active = true
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun assignBranchToReleasedIdentity(branchName: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Usuario no autenticado."))
        val path = "$COLLECTION_IDENTITIES/$uid"
        return try {
            require(branchName in BranchOptions.ALL_BRANCHES) {
                "Selecciona una sucursal válida."
            }
            val identityRef = db.collection(COLLECTION_IDENTITIES).document(uid)
            val snap = identityRef.get().await()
            require(snap.exists()) {
                "No se encontró tu ficha de identificación."
            }
            require(snap.getString("branchName").isNullOrBlank()) {
                "Tu sucursal ya está asignada y bloqueada. Solo el administrador puede liberarla."
            }

            identityRef.update(
                mapOf(
                    "branchName" to branchName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            runCatching {
                val userRef = db.collection("users").document(uid)
                if (userRef.get().await().exists()) {
                    userRef.update(
                        mapOf(
                            "branchName" to branchName,
                            "displayName" to branchName,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun releaseEmployeeBranchAsAdmin(identity: EmployeeIdentity): Result<Unit> {
        val targetUid = identity.userId.ifBlank { identity.id }
        val path = "$COLLECTION_IDENTITIES/$targetUid"
        return try {
            requireUserId()
            require(targetUid.isNotBlank()) { "Empleado inválido." }

            val batch = db.batch()
            val identityRef = db.collection(COLLECTION_IDENTITIES).document(targetUid)
            batch.update(
                identityRef,
                mapOf(
                    "branchName" to FieldValue.delete(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val userRef = db.collection("users").document(targetUid)
            if (userRef.get().await().exists()) {
                batch.update(
                    userRef,
                    mapOf(
                        "branchName" to FieldValue.delete(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
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

    suspend fun deleteEmployeeIdentityCompletelyAsAdmin(identity: EmployeeIdentity): Result<Unit> {
        val targetUid = identity.userId.ifBlank { identity.id }
        val path = "$COLLECTION_IDENTITIES/$targetUid"
        return try {
            requireUserId()
            require(targetUid.isNotBlank()) { "Empleado inválido." }

            val payrollKey = identity.payrollKey.ifBlank {
                EmployeeIdentityValidation.normalizePayrollKey(identity.payrollNumber)
            }

            val batch = db.batch()
            batch.delete(db.collection(COLLECTION_IDENTITIES).document(targetUid))
            if (payrollKey.isNotBlank()) {
                batch.delete(db.collection(COLLECTION_PAYROLLS).document(payrollKey))
            }
            val userRef = db.collection("users").document(targetUid)
            if (userRef.get().await().exists()) {
                batch.update(
                    userRef,
                    mapOf(
                        "branchName" to FieldValue.delete(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertDailySalesEntryWithIdentity(
        quotaId: String,
        dateString: String,
        identity: EmployeeIdentity,
        branchName: String?,
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
            val cleanBranch = (branchName ?: identity.branchName)?.trim()?.takeIf { it in BranchOptions.ALL_BRANCHES }
            val cleanPayroll = identity.payrollNumber.trim().take(32)
            val cleanFullName = identity.fullName.trim().take(80)
            val displayPayroll = EmployeeIdentityValidation.formatPayrollForDisplay(cleanPayroll)
            val authorDisplay = "$displayPayroll · $cleanFullName".take(80)

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
                    put("userName", authorDisplay)
                    if (cleanPayroll.isNotBlank()) put("employeePayroll", cleanPayroll)
                    if (cleanFullName.isNotBlank()) put("employeeName", cleanFullName)
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
                    put("userName", authorDisplay)
                    if (cleanPayroll.isNotBlank()) put("employeePayroll", cleanPayroll)
                    if (cleanFullName.isNotBlank()) put("employeeName", cleanFullName)
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
                val activeQuotasSnap = db.collection("biweekly_quotas")
                    .whereEqualTo("active", true)
                    .get()
                    .await()
                val activeQuotas = activeQuotasSnap.toObjects(
                    BiweeklyQuota::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                val activeCycle = ProjectionCalculator.resolveActiveCycleInfo(activeQuotas, dateString)
                val activeIds = activeCycle.activeQuotaIds
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
                        val inDates = entry.dateString >= activeCycle.startDate &&
                            entry.dateString <= activeCycle.effectiveUpperDateIso
                        val matchesQuota = activeIds.isEmpty() ||
                            entry.quotaId.isBlank() ||
                            entry.quotaId in activeIds
                        sameBranch && inDates && matchesQuota
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

    suspend fun completeTaskWithEvidenceAndIdentity(
        task: AssignedTask,
        identity: EmployeeIdentity,
        files: List<EncodedAttachment>
    ): Result<Unit> {
        val docPath = "tasks/${task.id}"
        return try {
            val uid = requireUserId()
            require(task.id.isNotBlank()) { "ID de tarea inválido." }
            require(task.parsedStatus == TaskStatus.PENDING) {
                buildAlreadyCompletedMessage(
                    branchName = identity.branchName ?: task.assigneeName,
                    payroll = task.completedByPayroll,
                    name = task.completedByName
                )
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

            val cleanPayroll = identity.payrollNumber.trim().take(32)
            val cleanFullName = identity.fullName.trim().take(80)
            val cleanBranch = identity.branchName?.trim()?.takeIf { it in BranchOptions.ALL_BRANCHES }
                ?: task.assigneeName.trim().takeIf { it in BranchOptions.ALL_BRANCHES }
                ?: throw IllegalStateException("Tu ficha no tiene una sucursal válida asignada.")

            val dayIso = DateAndFormatters.extractTaskDayIso(task)
            val periodId = DateAndFormatters.biweeklyPeriodIdForIso(dayIso)
            val taskKey = BranchTaskLockHelper.extractTaskKeyForBranchLock(task)
            val lockId = BranchTaskLockHelper.buildLockId(cleanBranch, dayIso, taskKey)

            val taskRef = db.collection("tasks").document(task.id)
            val lockRef = db.collection(COLLECTION_BRANCH_COMPLETIONS).document(lockId)
            val evidenceRef = db.collection("task_evidences").document()

            val displayPayroll = EmployeeIdentityValidation.formatPayrollForDisplay(cleanPayroll)
            val safeUploader = buildString {
                append(displayPayroll)
                append(" · ").append(cleanFullName)
                append(" · ").append(cleanBranch)
            }.take(80)
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
                if (cleanPayroll.isNotBlank()) put("employeePayroll", cleanPayroll)
                if (cleanFullName.isNotBlank()) put("employeeName", cleanFullName)
                put("branchName", cleanBranch)
                put("taskTitle", safeTitle)
                if (firstImagePreview != null) {
                    put("imageBase64", firstImagePreview)
                }
                put("fileCount", files.size.toLong())
                put("primaryFileName", files.first().fileName.trim().ifBlank { "archivo" }.take(120))
                put("capturedAt", FieldValue.serverTimestamp())
            }

            val lockPayload = buildMap<String, Any> {
                put("lockId", lockId)
                put("branchName", cleanBranch)
                put("dayIso", dayIso)
                put("periodId", periodId)
                put("taskKey", taskKey)
                put("taskTitle", safeTitle)
                task.templateId?.takeIf { it.isNotBlank() }?.let { put("templateId", it) }
                put("taskId", task.id)
                put("evidenceId", evidenceRef.id)
                put("completedByUid", uid)
                put("completedByPayroll", cleanPayroll)
                put("completedByName", cleanFullName)
                put("completedAt", FieldValue.serverTimestamp())
            }

            val taskUpdatePayload = mapOf(
                "status" to TaskStatus.COMPLETED.name,
                "hasPhotoEvidence" to true,
                "evidenceId" to evidenceRef.id,
                "completedByUid" to uid,
                "completedByPayroll" to cleanPayroll,
                "completedByName" to cleanFullName,
                "completedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            data class PartWrite(
                val docId: String,
                val payload: Map<String, Any>,
                val base64Size: Int
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
                                ),
                                base64Size = chunkBase64.length
                            )
                        )
                    }
                }
            }

            val totalBase64Bytes = partWrites.sumOf { it.base64Size }
            val preCommitParts: List<PartWrite>
            val transactionParts: List<PartWrite>

            if (totalBase64Bytes <= 7_200_000) {
                preCommitParts = emptyList()
                transactionParts = partWrites
            } else {
                // Keep transaction well under Firestore's 10 MiB payload limit when multiple huge PDFs are attached
                val preCheckLock = lockRef.get().await()
                if (preCheckLock.exists()) {
                    throw buildAlreadyCompletedException(
                        branchName = cleanBranch,
                        payroll = preCheckLock.getString("completedByPayroll"),
                        name = preCheckLock.getString("completedByName")
                    )
                }
                var runningBytes = 0
                val txList = mutableListOf<PartWrite>()
                val preList = mutableListOf<PartWrite>()
                for (part in partWrites.asReversed()) {
                    if (runningBytes + part.base64Size <= 6_500_000) {
                        txList.add(part)
                        runningBytes += part.base64Size
                    } else {
                        preList.add(part)
                    }
                }
                preCommitParts = preList.asReversed()
                transactionParts = txList.asReversed()

                if (preCommitParts.isNotEmpty()) {
                    preCommitParts.chunked(4).forEachIndexed { batchIdx, chunk ->
                        val batch = db.batch()
                        if (batchIdx == 0) {
                            batch.set(evidenceRef, evidencePayload)
                        }
                        chunk.forEach { part ->
                            batch.set(evidenceRef.collection("files").document(part.docId), part.payload)
                        }
                        batch.commit().await()
                    }
                }
            }

            try {
                db.runTransaction { transaction ->
                    val taskSnap = transaction.get(taskRef)
                    val lockSnap = transaction.get(lockRef)

                    if (!taskSnap.exists()) {
                        throw IllegalStateException("La tarea ya no existe o fue eliminada.")
                    }
                    if (lockSnap.exists()) {
                        throw buildAlreadyCompletedException(
                            branchName = cleanBranch,
                            payroll = lockSnap.getString("completedByPayroll"),
                            name = lockSnap.getString("completedByName")
                        )
                    }
                    if (taskSnap.getString("status") != TaskStatus.PENDING.name) {
                        throw buildAlreadyCompletedException(
                            branchName = cleanBranch,
                            payroll = taskSnap.getString("completedByPayroll"),
                            name = taskSnap.getString("completedByName")
                        )
                    }

                    transaction.set(lockRef, lockPayload)
                    if (preCommitParts.isEmpty()) {
                        transaction.set(evidenceRef, evidencePayload)
                    }
                    transactionParts.forEach { part ->
                        transaction.set(evidenceRef.collection("files").document(part.docId), part.payload)
                    }
                    transaction.update(taskRef, taskUpdatePayload)
                    null
                }.await()
            } catch (e: BranchTaskAlreadyCompletedException) {
                throw e
            } catch (e: FirebaseFirestoreException) {
                val recheckLock = runCatching { lockRef.get().await() }.getOrNull()
                if (recheckLock != null && recheckLock.exists()) {
                    throw buildAlreadyCompletedException(
                        branchName = cleanBranch,
                        payroll = recheckLock.getString("completedByPayroll"),
                        name = recheckLock.getString("completedByName")
                    )
                }
                throw e
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    private fun buildAlreadyCompletedException(
        branchName: String,
        payroll: String?,
        name: String?
    ): BranchTaskAlreadyCompletedException {
        val cleanPayroll = payroll?.trim().orEmpty()
        val cleanName = name?.trim().orEmpty()
        return BranchTaskAlreadyCompletedException(
            completedByPayroll = cleanPayroll,
            completedByName = cleanName,
            branchName = branchName,
            message = buildAlreadyCompletedMessage(branchName, cleanPayroll, cleanName)
        )
    }

    private fun buildAlreadyCompletedMessage(
        branchName: String,
        payroll: String?,
        name: String?
    ): String {
        val displayPayroll = EmployeeIdentityValidation.formatPayrollForDisplay(payroll)
        val who = when {
            displayPayroll.isNotBlank() && !name.isNullOrBlank() -> "Nómina $displayPayroll ($name)"
            displayPayroll.isNotBlank() -> "Nómina $displayPayroll"
            !name.isNullOrBlank() -> name
            else -> "otro compañero de la sucursal"
        }
        return "Subida bloqueada: esta tarea de $branchName ya fue completada por $who. Solo un usuario por sucursal puede realizarla."
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

    companion object {
        const val COLLECTION_IDENTITIES = "employee_identities"
        const val COLLECTION_PAYROLLS = "payroll_registry"
        const val COLLECTION_BRANCH_COMPLETIONS = "branch_task_completions"
    }
}
