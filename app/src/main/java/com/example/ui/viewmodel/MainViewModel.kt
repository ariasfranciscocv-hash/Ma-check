package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppPreferences
import com.example.data.model.AssignedTask
import com.example.data.model.BiweeklyQuota
import com.example.data.model.ChartSeries
import com.example.data.model.ClosingAttachmentDraft
import com.example.data.model.DailySalesEntry
import com.example.data.model.MetricProgress
import com.example.data.model.ProjectionPace
import com.example.data.model.SalesMetricType
import com.example.data.model.SalesProjectionSummary
import com.example.data.model.TaskEvidence
import com.example.data.model.TaskEvidenceAttachmentFile
import com.example.data.model.TaskNotification
import com.example.data.model.TaskPriority
import com.example.data.model.TaskType
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.buildBranchSalesSummaries
import com.example.data.repository.AppRepository
import com.example.utils.DateAndFormatters
import com.example.utils.EncodedAttachment
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: AppRepository,
    private val preferences: AppPreferences,
    val currentUserId: String,
    val currentUserName: String,
    val currentUserEmail: String?,
    val isEmailVerified: Boolean
) : ViewModel() {

    private val _selectedMetric = MutableStateFlow(SalesMetricType.GLOBAL_SALES)
    val selectedMetric: StateFlow<SalesMetricType> = _selectedMetric.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _closingAttachmentDraft = MutableStateFlow<ClosingAttachmentDraft>(ClosingAttachmentDraft.Unchanged)
    val closingAttachmentDraft: StateFlow<ClosingAttachmentDraft> = _closingAttachmentDraft.asStateFlow()

    private val _profileSynced = MutableStateFlow(false)
    val profileSynced: StateFlow<Boolean> = _profileSynced.asStateFlow()

    private val _branchConfirmedThisSession = MutableStateFlow(false)
    val branchConfirmedThisSession: StateFlow<Boolean> = _branchConfirmedThisSession.asStateFlow()

    val lastCachedBranch: StateFlow<String?> = preferences
        .observeLastBranch(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    private val bootstrappedAdmin =
        currentUserEmail == "arias.francisco.cv@gmail.com" && isEmailVerified

    val adminCheckState: StateFlow<Boolean?> = repository
        .observeIsAdmin(currentUserId, currentUserEmail, isEmailVerified)
        .map<Boolean, Boolean?> { it }
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observeIsAdmin")
            emit(false)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = if (bootstrappedAdmin) true else null
        )

    val isAdminState: StateFlow<Boolean> = adminCheckState
        .map { it == true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = bootstrappedAdmin
        )

    val currentUserProfile: StateFlow<UserProfile?> = repository
        .observeCurrentUserProfile(currentUserId)
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observeCurrentUserProfile")
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    val teamUsers: StateFlow<List<UserProfile>> = isAdminState
        .flatMapLatest { isAdmin -> repository.observeTeamUsers(currentUserId, isAdmin) }
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observeTeamUsers")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val scheduleTickerState: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000L)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = System.currentTimeMillis()
    )

    val tasksState: StateFlow<UiState<List<AssignedTask>>> = isAdminState
        .flatMapLatest { isAdmin -> repository.observeTasks(currentUserId, isAdmin) }
        .map<List<AssignedTask>, UiState<List<AssignedTask>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreOrThrow(error, "observeTasks")
            emit(UiState.Error(root.message ?: "No se pudieron cargar las tareas."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val evidencesState: StateFlow<UiState<List<TaskEvidence>>> = isAdminState
        .flatMapLatest { isAdmin -> repository.observeEvidences(currentUserId, isAdmin) }
        .map<List<TaskEvidence>, UiState<List<TaskEvidence>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreOrThrow(error, "observeEvidences")
            emit(UiState.Error(root.message ?: "No se pudieron cargar las evidencias."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val notificationsState: StateFlow<List<TaskNotification>> = repository
        .observeNotifications(currentUserId)
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observeNotifications")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val alertedNotificationIds: StateFlow<Set<String>> = preferences
        .observeAlertedNotificationIds(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptySet()
        )

    val activeQuotaState: StateFlow<UiState<BiweeklyQuota?>> = repository
        .observeActiveQuota(currentUserId)
        .map<BiweeklyQuota?, UiState<BiweeklyQuota?>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreOrThrow(error, "observeActiveQuota")
            emit(UiState.Error(root.message ?: "No se pudo cargar la meta quincenal."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val salesEntriesState: StateFlow<UiState<List<DailySalesEntry>>> = isAdminState
        .flatMapLatest { isAdmin -> repository.observeSalesEntries(currentUserId, isAdmin) }
        .map<List<DailySalesEntry>, UiState<List<DailySalesEntry>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreOrThrow(error, "observeSalesEntries")
            emit(UiState.Error(root.message ?: "No se pudieron cargar los cierres de ventas."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val projectionSummaryState: StateFlow<UiState<SalesProjectionSummary?>> = combine(
        activeQuotaState,
        salesEntriesState,
        teamUsers,
        isAdminState
    ) { quotaState, entriesState, users, isAdmin ->
        when {
            quotaState is UiState.Error -> UiState.Error(quotaState.message)
            entriesState is UiState.Error -> UiState.Error(entriesState.message)
            quotaState is UiState.Loading || entriesState is UiState.Loading -> UiState.Loading
            else -> {
                val quota = (quotaState as UiState.Success).data
                val entries = (entriesState as UiState.Success).data
                if (quota == null) {
                    UiState.Success(null)
                } else {
                    UiState.Success(
                        computeProjectionSummary(
                            quota = quota,
                            entries = entries,
                            uid = currentUserId,
                            teamUsers = users,
                            includeBranchBreakdown = isAdmin
                        )
                    )
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    val selectedChartSeries: StateFlow<ChartSeries?> = combine(
        activeQuotaState,
        salesEntriesState,
        selectedMetric
    ) { quotaState, entriesState, metric ->
        val quota = (quotaState as? UiState.Success)?.data ?: return@combine null
        val entries = (entriesState as? UiState.Success)?.data ?: emptyList()
        buildChartSeries(quota, entries, metric)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null
    )

    val showFirstEntryBriefingToday: StateFlow<Boolean> = preferences
        .observeLastBriefingDate(currentUserId)
        .map { lastDate -> lastDate != DateAndFormatters.todayIso() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = false
        )

    private val lastTasksSyncDateState: StateFlow<String?> = preferences
        .observeLastTasksSyncDate(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    private val syncedTodayAttempted = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            val result = repository.syncCurrentUserProfile(
                uid = currentUserId,
                displayName = currentUserName,
                email = currentUserEmail,
                photoUrl = null,
                emailVerified = isEmailVerified
            )
            _profileSynced.value = true
            result.onFailure { err ->
                _actionMessage.value = "Error al sincronizar perfil: ${err.localizedMessage}"
            }
        }
        viewModelScope.launch {
            isAdminState.collect { isAdmin ->
                if (isAdmin) {
                    _branchConfirmedThisSession.value = true
                    repository.ensureDefaultQuotaForAdminIfMissing()
                }
            }
        }
        viewModelScope.launch {
            combine(
                adminCheckState,
                currentUserProfile,
                preferences.observeLastTasksSyncDate(currentUserId)
            ) { resolvedAdmin, profile, lastSyncDate ->
                Triple(resolvedAdmin, profile, lastSyncDate)
            }.collect { (resolvedAdmin, profile, lastSyncDate) ->
                if (resolvedAdmin != false) return@collect
                val branch = profile?.branchName?.takeIf { it.isNotBlank() } ?: return@collect
                preferences.saveLastBranch(currentUserId, branch)
                val todayIso = DateAndFormatters.todayIso()
                if (lastSyncDate != todayIso) {
                    syncTodayChecklistOncePerDay(branch, todayIso)
                }
            }
        }
    }

    private fun syncTodayChecklistOncePerDay(branchLabel: String, todayIso: String = DateAndFormatters.todayIso()) {
        if (adminCheckState.value != false) return
        val attemptKey = "${currentUserId}_$todayIso"
        if (!syncedTodayAttempted.add(attemptKey)) return
        viewModelScope.launch {
            val result = repository.syncScheduledTasksForCurrentUser(branchLabel)
            if (result.isSuccess) {
                preferences.saveLastTasksSyncDate(currentUserId, todayIso)
            } else {
                syncedTodayAttempted.remove(attemptKey)
            }
        }
    }

    fun selectMetric(metric: SalesMetricType) {
        _selectedMetric.value = metric
    }

    fun confirmBranchSelection(branchName: String) {
        viewModelScope.launch {
            _isBusy.value = true
            preferences.saveLastBranch(currentUserId, branchName)
            val result = repository.updateCurrentUserBranch(branchName)
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _branchConfirmedThisSession.value = true
                    val todayIso = DateAndFormatters.todayIso()
                    if (lastTasksSyncDateState.value != todayIso) {
                        syncTodayChecklistOncePerDay(branchName, todayIso)
                    }
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "No se pudo guardar la sucursal."
                }
            )
        }
    }

    fun reopenBranchSelection() {
        _branchConfirmedThisSession.value = false
    }

    fun dismissDailyBriefingForToday() {
        viewModelScope.launch {
            preferences.markBriefingSeenToday(currentUserId, DateAndFormatters.todayIso())
        }
    }

    fun markNotificationsAlerted(ids: Collection<String>) {
        viewModelScope.launch {
            preferences.markNotificationsAlerted(currentUserId, ids)
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun acknowledgeNotification(notificationId: String) {
        viewModelScope.launch {
            repository.acknowledgeNotification(notificationId)
        }
    }

    fun updateClosingAttachmentDraft(draft: ClosingAttachmentDraft) {
        _closingAttachmentDraft.value = draft
    }

    suspend fun loadClosingAttachmentChunks(entry: DailySalesEntry): Result<List<String>> {
        return repository.loadSalesEntryAttachmentChunks(entry)
    }

    fun saveDailyClosingSales(
        quotaId: String,
        globalSales: Double,
        creditSales: Double,
        newCustomers: Long,
        appMaOperations: Long,
        newCustomerRequests: Long,
        notes: String,
        attachmentDraft: ClosingAttachmentDraft = _closingAttachmentDraft.value,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.upsertDailySalesEntry(
                quotaId = quotaId,
                dateString = DateAndFormatters.todayIso(),
                userName = currentUserProfile.value?.effectiveBranchLabel ?: currentUserName,
                globalSales = globalSales,
                creditSales = creditSales,
                newCustomers = newCustomers,
                appMaOperations = appMaOperations,
                newCustomerRequests = newCustomerRequests,
                notes = notes,
                attachmentDraft = attachmentDraft
            )
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _closingAttachmentDraft.value = ClosingAttachmentDraft.Unchanged
                    preferences.markBriefingSeenToday(currentUserId, DateAndFormatters.todayIso())
                    _actionMessage.value = "Cierre de operaciones guardado correctamente."
                    onSaved()
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "No se pudo guardar el cierre."
                }
            )
        }
    }

    fun saveBiweeklyQuota(
        title: String,
        startDate: String,
        endDate: String,
        totalDays: Long,
        targetGlobalSales: Double,
        targetCreditSales: Double,
        targetNewCustomers: Long,
        targetAppMaOperations: Long,
        targetNewCustomerRequests: Long,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveBiweeklyQuota(
                title = title,
                startDate = startDate,
                endDate = endDate,
                totalDays = totalDays,
                targetGlobalSales = targetGlobalSales,
                targetCreditSales = targetCreditSales,
                targetNewCustomers = targetNewCustomers,
                targetAppMaOperations = targetAppMaOperations,
                targetNewCustomerRequests = targetNewCustomerRequests
            )
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Meta quincenal actualizada."
                    onSuccess()
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "No se pudo actualizar la meta."
                }
            )
        }
    }

    fun createTask(
        assignees: List<UserProfile>,
        title: String,
        description: String,
        taskType: TaskType,
        priority: TaskPriority,
        dueDate: String,
        dueTime: String? = null,
        deadlineLabel: String? = null,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createTaskAndNotify(
                assignees = assignees,
                title = title,
                description = description,
                taskType = taskType,
                priority = priority,
                dueDate = dueDate,
                dueTime = dueTime,
                deadlineLabel = deadlineLabel
            )
            _isBusy.value = false
            result.fold(
                onSuccess = { count ->
                    _actionMessage.value = if (taskType == TaskType.EXTRAORDINARY) {
                        "Tarea extraordinaria asignada y notificación enviada ($count)."
                    } else {
                        "Tarea asignada correctamente ($count)."
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "No se pudo crear la tarea."
                }
            )
        }
    }

    fun updateTasks(
        tasks: List<AssignedTask>,
        title: String,
        description: String,
        taskType: TaskType,
        priority: TaskPriority,
        dueDate: String,
        dueTime: String?,
        deadlineLabel: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateTasks(
                tasks = tasks,
                title = title,
                description = description,
                taskType = taskType,
                priority = priority,
                dueDate = dueDate,
                dueTime = dueTime,
                deadlineLabel = deadlineLabel
            )
            _isBusy.value = false
            result.fold(
                onSuccess = { count ->
                    _actionMessage.value = if (count > 1) {
                        "Se actualizaron $count tareas correctamente."
                    } else {
                        "Tarea actualizada correctamente."
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "No se pudo actualizar la tarea."
                }
            )
        }
    }

    fun deleteTasks(
        tasks: List<AssignedTask>,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deleteTasks(tasks)
            _isBusy.value = false
            result.fold(
                onSuccess = { count ->
                    _actionMessage.value = if (count > 1) {
                        "Se eliminaron $count tareas correctamente."
                    } else {
                        "Tarea eliminada correctamente."
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "No se pudo eliminar la tarea."
                }
            )
        }
    }

    fun completeTaskWithEvidenceFiles(
        task: AssignedTask,
        files: List<EncodedAttachment>,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val uploaderName = currentUserProfile.value?.effectiveBranchLabel ?: currentUserName
            val result = repository.completeTaskWithEvidenceFiles(
                task = task,
                uploaderName = uploaderName,
                files = files
            )
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Tarea completada y bloqueada (${files.size} archivo(s) enviados)."
                    onSuccess()
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "No se pudo registrar la evidencia."
                }
            )
        }
    }

    suspend fun loadTaskEvidenceFiles(evidence: TaskEvidence): Result<List<TaskEvidenceAttachmentFile>> {
        return repository.loadTaskEvidenceFiles(evidence)
    }

    fun confirmBranchChecklistResetForQuota(
        branchName: String,
        branchUsers: List<UserProfile>
    ) {
        val activeQuota = (activeQuotaState.value as? UiState.Success)?.data
        if (activeQuota == null || activeQuota.id.isBlank()) {
            _actionMessage.value = "Primero define o activa una meta quincenal."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.confirmBranchChecklistResetForQuota(
                branchName = branchName,
                branchUsers = branchUsers,
                quotaId = activeQuota.id
            )
            _isBusy.value = false
            result.fold(
                onSuccess = { count ->
                    _actionMessage.value = "Lista de $branchName reiniciada para \"${activeQuota.title}\" ($count tareas nuevas)."
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "No se pudo reiniciar la lista de $branchName."
                }
            )
        }
    }

    fun toggleUserAdminRole(targetUser: UserProfile, makeAdmin: Boolean) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.setUserRole(targetUser, makeAdmin)
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = if (makeAdmin) {
                        "${targetUser.displayName} ahora es Administrador."
                    } else {
                        "${targetUser.displayName} ahora es Empleado."
                    }
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "No se pudo actualizar el rol."
                }
            )
        }
    }

    fun seedDailyChecklist(targetUser: UserProfile) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.seedDailyChecklistForUser(targetUser)
            _isBusy.value = false
            result.fold(
                onSuccess = { added ->
                    _actionMessage.value = if (added > 0) {
                        "Check list programado asignado a ${targetUser.effectiveBranchLabel} ($added nuevas)."
                    } else {
                        "${targetUser.effectiveBranchLabel} ya tiene todas las tareas programadas de hoy."
                    }
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "Error al asignar check list."
                }
            )
        }
    }

    private fun computeProjectionSummary(
        quota: BiweeklyQuota,
        entries: List<DailySalesEntry>,
        uid: String,
        teamUsers: List<UserProfile>,
        includeBranchBreakdown: Boolean
    ): SalesProjectionSummary {
        val today = DateAndFormatters.todayIso()
        val totalDays = quota.totalDays.toInt().coerceIn(1, 31)
        val daysElapsed = DateAndFormatters.dayIndexInPeriod(quota.startDate, today, totalDays)
        val periodEntries = entries.filter { it.quotaId == quota.id }
        val todayEntry = periodEntries.firstOrNull { it.userId == uid && it.dateString == today }

        fun buildMetric(
            metric: SalesMetricType,
            actual: Double,
            target: Double
        ): MetricProgress {
            val safeTarget = target.coerceAtLeast(1.0)
            val dailyAvg = actual / daysElapsed.toDouble()
            val projected = dailyAvg * totalDays.toDouble()
            val remainingDays = (totalDays - daysElapsed).coerceAtLeast(1)
            val requiredPace = ((safeTarget - actual).coerceAtLeast(0.0)) / remainingDays.toDouble()
            val progressRatio = (actual / safeTarget).toFloat().coerceIn(0f, 1.5f)
            val projectedRatio = (projected / safeTarget).toFloat().coerceIn(0f, 2f)
            val pace = when {
                projectedRatio >= 0.98f -> ProjectionPace.ON_TRACK
                projectedRatio >= 0.80f -> ProjectionPace.MODERATE_RISK
                else -> ProjectionPace.BEHIND
            }
            return MetricProgress(
                metric = metric,
                actualTotal = actual,
                targetTotal = target,
                projectedTotal = projected,
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
                quota.targetGlobalSales
            ),
            buildMetric(
                SalesMetricType.CREDIT_SALES,
                periodEntries.sumOf { it.creditSales },
                quota.targetCreditSales
            ),
            buildMetric(
                SalesMetricType.NEW_CUSTOMERS,
                periodEntries.sumOf { it.newCustomers }.toDouble(),
                quota.targetNewCustomers.toDouble()
            ),
            buildMetric(
                SalesMetricType.APP_MA_OPS,
                periodEntries.sumOf { it.appMaOperations }.toDouble(),
                quota.targetAppMaOperations.toDouble()
            ),
            buildMetric(
                SalesMetricType.NEW_CUSTOMER_REQUESTS,
                periodEntries.sumOf { it.newCustomerRequests }.toDouble(),
                quota.targetNewCustomerRequests.toDouble()
            )
        )

        val branchBreakdowns = if (includeBranchBreakdown) {
            buildBranchSalesSummaries(
                teamUsers = teamUsers,
                entries = periodEntries,
                todayIso = today
            )
        } else {
            emptyList()
        }

        return SalesProjectionSummary(
            quota = quota,
            daysElapsed = daysElapsed,
            totalDays = totalDays,
            metrics = metrics,
            entriesCount = periodEntries.size,
            todayEntry = todayEntry,
            recentEntries = periodEntries.sortedByDescending { it.dateString }.take(15),
            branchBreakdowns = branchBreakdowns
        )
    }

    private fun buildChartSeries(
        quota: BiweeklyQuota,
        entries: List<DailySalesEntry>,
        metric: SalesMetricType
    ): ChartSeries {
        val totalDays = quota.totalDays.toInt().coerceIn(1, 31)
        val today = DateAndFormatters.todayIso()
        val elapsedDays = DateAndFormatters.dayIndexInPeriod(quota.startDate, today, totalDays)
        val periodEntries = entries.filter { it.quotaId == quota.id }

        val dailyTotals = DoubleArray(totalDays) { 0.0 }
        periodEntries.forEach { entry ->
            val dayIdx = DateAndFormatters.dayIndexInPeriod(quota.startDate, entry.dateString, totalDays) - 1
            val value = when (metric) {
                SalesMetricType.GLOBAL_SALES -> entry.globalSales
                SalesMetricType.CREDIT_SALES -> entry.creditSales
                SalesMetricType.NEW_CUSTOMERS -> entry.newCustomers.toDouble()
                SalesMetricType.APP_MA_OPS -> entry.appMaOperations.toDouble()
                SalesMetricType.NEW_CUSTOMER_REQUESTS -> entry.newCustomerRequests.toDouble()
            }
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

        val target = when (metric) {
            SalesMetricType.GLOBAL_SALES -> quota.targetGlobalSales
            SalesMetricType.CREDIT_SALES -> quota.targetCreditSales
            SalesMetricType.NEW_CUSTOMERS -> quota.targetNewCustomers.toDouble()
            SalesMetricType.APP_MA_OPS -> quota.targetAppMaOperations.toDouble()
            SalesMetricType.NEW_CUSTOMER_REQUESTS -> quota.targetNewCustomerRequests.toDouble()
        }

        return ChartSeries(
            metric = metric,
            totalDays = totalDays,
            elapsedDays = elapsedDays,
            targetValue = target,
            actualCumulativeByDay = actualCumulative,
            projectedCumulativeByDay = projectedCumulative
        )
    }

    private fun unwrapFirestoreOrThrow(error: Throwable, opName: String): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error in $opName", rootError)
        return rootError
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
