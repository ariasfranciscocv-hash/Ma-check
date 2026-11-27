package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppPreferences
import com.example.data.model.ActiveBiweeklyCycleInfo
import com.example.data.model.AssignedTask
import com.example.data.model.BiweeklyCycleArchive
import com.example.data.model.BiweeklyQuota
import com.example.data.model.BranchOptions
import com.example.data.model.BranchSalesSummary
import com.example.data.model.BranchTaskCompletion
import com.example.data.model.BranchTaskLockHelper
import com.example.data.model.ChartSeries
import com.example.data.model.ClosingAttachmentDraft
import com.example.data.model.DailySalesEntry
import com.example.data.model.DailyTaskTemplate
import com.example.data.model.EmployeeBranchParticipationStats
import com.example.data.model.EmployeeIdentity
import com.example.data.model.SalesMetricType
import com.example.data.model.SalesProjectionSummary
import com.example.data.model.TaskEvidence
import com.example.data.model.TaskEvidenceAttachmentFile
import com.example.data.model.TaskNotification
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.TaskType
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.buildBranchSalesSummaries
import com.example.data.repository.AppRepository
import com.example.data.repository.BranchTaskAlreadyCompletedException
import com.example.data.repository.EmployeeIdentityRepository
import com.example.data.repository.ProjectionCalculator
import com.example.data.repository.QuotaRepositoryExtensions
import com.example.data.repository.ScheduledTasksCatalog
import com.example.utils.AppCrashGuard
import com.example.utils.DateAndFormatters
import com.example.utils.EncodedAttachment
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: AppRepository,
    private val preferences: AppPreferences,
    val currentUserId: String,
    val currentUserName: String,
    val currentUserEmail: String?,
    val isEmailVerified: Boolean,
    private val quotaExtensions: QuotaRepositoryExtensions? = null,
    private val identityRepository: EmployeeIdentityRepository? = null
) : ViewModel() {

    private val bootstrappedAdmin: Boolean = AppRepository.isBootstrappedAdmin(currentUserEmail, isEmailVerified)

    private val _selectedMetric = MutableStateFlow(SalesMetricType.GLOBAL_SALES)
    val selectedMetric: StateFlow<SalesMetricType> = _selectedMetric.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _technicalErrorState = MutableStateFlow<String?>(null)
    val technicalErrorState: StateFlow<String?> = _technicalErrorState.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _closingAttachmentDraft = MutableStateFlow<ClosingAttachmentDraft>(ClosingAttachmentDraft.Unchanged)
    val closingAttachmentDraft: StateFlow<ClosingAttachmentDraft> = _closingAttachmentDraft.asStateFlow()

    private val _profileSynced = MutableStateFlow(false)
    val profileSynced: StateFlow<Boolean> = _profileSynced.asStateFlow()

    private val _selectedBranch = MutableStateFlow(
        if (bootstrappedAdmin) BranchOptions.ALL_BRANCHES_OPTION else ""
    )
    val selectedBranch: StateFlow<String> = _selectedBranch.asStateFlow()

    private val _isRoleResolved = MutableStateFlow(bootstrappedAdmin)
    val isRoleResolved: StateFlow<Boolean> = _isRoleResolved.asStateFlow()

    private val _isEmployeeIdentityResolved = MutableStateFlow(bootstrappedAdmin || identityRepository == null)
    val isEmployeeIdentityResolved: StateFlow<Boolean> = _isEmployeeIdentityResolved.asStateFlow()

    private val _branchConfirmedThisSession = MutableStateFlow(bootstrappedAdmin)
    val branchConfirmedThisSession: StateFlow<Boolean> = _branchConfirmedThisSession.asStateFlow()

    private val safeExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable is CancellationException) return@CoroutineExceptionHandler
        Log.e(TAG, "Unhandled coroutine exception in MainViewModel", throwable)
        _isBusy.value = false
        val techDetails = "${throwable::class.java.simpleName}: ${throwable.localizedMessage ?: throwable.message ?: "Error desconocido"}"
        _technicalErrorState.value = techDetails
        AppCrashGuard.reportNonFatal("Error inesperado en operación", throwable)
    }

    val lastCachedBranch: StateFlow<String?> = preferences
        .observeLastBranch(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    val isAdminState: StateFlow<Boolean> = repository
        .observeIsAdmin(currentUserId, currentUserEmail, isEmailVerified)
        .onEach { resolvedAdmin ->
            if (resolvedAdmin) {
                if (_selectedBranch.value.isBlank()) {
                    _selectedBranch.value = BranchOptions.ALL_BRANCHES_OPTION
                }
                _branchConfirmedThisSession.value = true
            }
            _isRoleResolved.value = true
        }
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observeIsAdmin")
            _isRoleResolved.value = true
            emit(bootstrappedAdmin)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
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

    val currentEmployeeIdentity: StateFlow<EmployeeIdentity?> = combine(
        isRoleResolved,
        isAdminState
    ) { resolved, isAdmin -> resolved to isAdmin }
        .flatMapLatest { (resolved, isAdmin) ->
            when {
                !resolved -> flow { }
                isAdmin || bootstrappedAdmin || identityRepository == null -> {
                    _isEmployeeIdentityResolved.value = true
                    flowOf(null)
                }
                else -> identityRepository.observeCurrentEmployeeIdentity(currentUserId)
                    .onEach { _isEmployeeIdentityResolved.value = true }
            }
        }
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observeCurrentEmployeeIdentity")
            _isEmployeeIdentityResolved.value = true
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val employeeIdentities: StateFlow<List<EmployeeIdentity>> = isAdminState
        .flatMapLatest { isAdmin ->
            identityRepository?.observeAllEmployeeIdentities(currentUserId, isAdmin) ?: flowOf(emptyList())
        }
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observeAllEmployeeIdentities")
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

    private val todayDateFlow: Flow<String> = scheduleTickerState
        .map { DateAndFormatters.todayIso() }
        .distinctUntilChanged()

    private val rawTasksState: StateFlow<UiState<List<AssignedTask>>> = isAdminState
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

    val branchTaskCompletionsState: StateFlow<List<BranchTaskCompletion>> = combine(
        isAdminState,
        currentEmployeeIdentity,
        currentUserProfile,
        selectedBranch
    ) { isAdmin, identity, profile, selected ->
        val branch = identity?.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
            ?: profile?.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
            ?: selected.takeIf { it in BranchOptions.ALL_BRANCHES }
        isAdmin to branch
    }
        .distinctUntilChanged()
        .flatMapLatest { (isAdmin, branch) ->
            identityRepository?.observeBranchTaskCompletions(
                uid = currentUserId,
                isAdmin = isAdmin,
                branchName = branch
            ) ?: flowOf(emptyList())
        }
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observeBranchTaskCompletions")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val tasksState: StateFlow<UiState<List<AssignedTask>>> = combine(
        rawTasksState,
        branchTaskCompletionsState,
        isAdminState,
        currentEmployeeIdentity,
        teamUsers
    ) { rawState, completions, isAdmin, identity, users ->
        when (rawState) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> UiState.Error(rawState.message)
            is UiState.Success -> {
                val merged = BranchTaskLockHelper.mergeTasksWithBranchCompletions(
                    tasks = rawState.data,
                    completions = completions,
                    isAdmin = isAdmin,
                    employeeBranch = identity?.branchName,
                    teamUsers = users
                ).let { list ->
                    ScheduledTasksCatalog.filterTasksForRole(list, isAdmin)
                }.sortedWith(
                    compareBy<AssignedTask> { it.parsedStatus == TaskStatus.COMPLETED }
                        .thenByDescending { it.parsedType == TaskType.EXTRAORDINARY }
                        .thenByDescending { it.parsedPriority == TaskPriority.URGENT }
                        .thenByDescending { it.createdAt ?: com.google.firebase.Timestamp(0, 0) }
                )
                UiState.Success(merged)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    val dailyTaskTemplatesState: StateFlow<List<DailyTaskTemplate>> = combine(
        repository.observeDailyTaskTemplates(currentUserId),
        isAdminState
    ) { templates, isAdmin ->
        ScheduledTasksCatalog.filterTemplatesForRole(templates, isAdmin)
    }
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observeDailyTaskTemplates")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
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

    val activeQuotasState: StateFlow<UiState<List<BiweeklyQuota>>> = repository
        .observeActiveQuotas(currentUserId)
        .map<List<BiweeklyQuota>, UiState<List<BiweeklyQuota>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreOrThrow(error, "observeActiveQuotas")
            emit(UiState.Error(root.message ?: "No se pudieron cargar las metas quincenales."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val activeCycleInfoState: StateFlow<ActiveBiweeklyCycleInfo> = combine(
        activeQuotasState,
        todayDateFlow
    ) { quotasState, todayIso ->
        val quotas = (quotasState as? UiState.Success)?.data ?: emptyList()
        ProjectionCalculator.resolveActiveCycleInfo(quotas, todayIso)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ProjectionCalculator.resolveActiveCycleInfo(emptyList())
    )

    val cycleArchivesState: StateFlow<List<BiweeklyCycleArchive>> = isAdminState
        .flatMapLatest { isAdmin ->
            quotaExtensions?.observeCycleArchives(currentUserId, isAdmin) ?: flowOf(emptyList())
        }
        .catch { error ->
            unwrapFirestoreOrThrow(error, "observeCycleArchives")
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList()
        )

    val activeQuotaState: StateFlow<UiState<BiweeklyQuota?>> = combine(
        activeQuotasState,
        todayDateFlow
    ) { state, todayIso ->
        when (state) {
            is UiState.Loading -> UiState.Loading
            is UiState.Error -> UiState.Error(state.message)
            is UiState.Success -> {
                val activeCycle = ProjectionCalculator.resolveActiveCycleInfo(state.data, todayIso)
                val generalQuota = ProjectionCalculator.resolveIndependentGeneralQuota(
                    quotas = state.data,
                    currentPeriodId = activeCycle.periodId
                )
                UiState.Success(generalQuota)
            }
        }
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

    val employeeParticipationStatsState: StateFlow<EmployeeBranchParticipationStats?> = combine(
        currentEmployeeIdentity,
        tasksState,
        branchTaskCompletionsState,
        activeQuotaState,
        todayDateFlow
    ) { identity, tasksUiState, completions, quotaUiState, todayIso ->
        if (identity == null || !identity.hasIdentityCore) return@combine null
        val tasksList = (tasksUiState as? UiState.Success)?.data ?: emptyList()
        val quota = (quotaUiState as? UiState.Success)?.data
        BranchTaskLockHelper.computeEmployeeBranchParticipationStats(
            identity = identity,
            tasks = tasksList,
            completions = completions,
            activeQuota = quota,
            todayIso = todayIso
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null
    )

    val branchSalesSummariesState: StateFlow<List<BranchSalesSummary>> = combine(
        activeQuotasState,
        salesEntriesState,
        teamUsers,
        isAdminState,
        currentUserProfile,
        currentEmployeeIdentity,
        todayDateFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val quotasState = values[0] as UiState<List<BiweeklyQuota>>
        @Suppress("UNCHECKED_CAST")
        val entriesState = values[1] as UiState<List<DailySalesEntry>>
        @Suppress("UNCHECKED_CAST")
        val users = values[2] as List<UserProfile>
        val isAdmin = values[3] as Boolean
        val profile = values[4] as UserProfile?
        val identity = values[5] as EmployeeIdentity?
        val todayIso = values[6] as String

        val quotas = (quotasState as? UiState.Success)?.data ?: return@combine emptyList()
        val rawEntries = (entriesState as? UiState.Success)?.data ?: emptyList()
        val branchesToShow = if (isAdmin) {
            BranchOptions.ALL_BRANCHES
        } else {
            val employeeBranch = identity?.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
                ?: profile?.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
            listOfNotNull(employeeBranch)
        }
        buildBranchSalesSummaries(
            branches = branchesToShow,
            quotas = quotas,
            entries = rawEntries,
            teamUsers = users,
            todayIso = todayIso,
            currentPeriodId = null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val adminGeneralSummaryState: StateFlow<SalesProjectionSummary?> = combine(
        activeQuotasState,
        salesEntriesState,
        isAdminState,
        teamUsers,
        todayDateFlow
    ) { quotasState, entriesState, isAdmin, users, todayIso ->
        if (!isAdmin) return@combine null
        val quotas = (quotasState as? UiState.Success)?.data ?: emptyList()
        val rawEntries = (entriesState as? UiState.Success)?.data ?: emptyList()
        ProjectionCalculator.computeAdminGeneralIndicatorsSummary(
            quotas = quotas,
            allBranchEntries = rawEntries,
            users = users,
            uid = currentUserId,
            todayIso = todayIso
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null
    )

    val projectionSummaryState: StateFlow<UiState<SalesProjectionSummary?>> = combine(
        activeQuotasState,
        salesEntriesState,
        selectedBranch,
        isAdminState,
        teamUsers,
        currentUserProfile,
        currentEmployeeIdentity,
        todayDateFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val quotasState = values[0] as UiState<List<BiweeklyQuota>>
        @Suppress("UNCHECKED_CAST")
        val entriesState = values[1] as UiState<List<DailySalesEntry>>
        val branchFilter = values[2] as String
        val isAdmin = values[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val users = values[4] as List<UserProfile>
        val profile = values[5] as UserProfile?
        val identity = values[6] as EmployeeIdentity?
        val todayIso = values[7] as String

        when {
            quotasState is UiState.Error -> UiState.Error(quotasState.message)
            entriesState is UiState.Error -> UiState.Error(entriesState.message)
            quotasState is UiState.Loading || entriesState is UiState.Loading -> UiState.Loading
            else -> {
                val quotas = (quotasState as UiState.Success).data
                val rawEntries = (entriesState as UiState.Success).data
                val targetBranch = if (isAdmin) {
                    branchFilter.ifBlank { BranchOptions.ALL_BRANCHES_OPTION }
                } else {
                    identity?.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
                        ?: profile?.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
                        ?: branchFilter.ifBlank { BranchOptions.ALL_BRANCHES_OPTION }
                }
                if (isAdmin && targetBranch == BranchOptions.ALL_BRANCHES_OPTION) {
                    UiState.Success(
                        ProjectionCalculator.computeAdminGeneralIndicatorsSummary(
                            quotas = quotas,
                            allBranchEntries = rawEntries,
                            users = users,
                            uid = currentUserId,
                            todayIso = todayIso
                        )
                    )
                } else {
                    val activeCycle = ProjectionCalculator.resolveActiveCycleInfo(quotas, todayIso)
                    val quota = ProjectionCalculator.resolveQuotaForDashboard(
                        targetBranch = targetBranch,
                        quotas = quotas,
                        currentPeriodId = activeCycle.periodId
                    )
                    if (quota == null) {
                        UiState.Success(null)
                    } else {
                        val filteredEntries = filterEntriesForSelectedBranch(rawEntries, branchFilter, isAdmin, users)
                        UiState.Success(
                            ProjectionCalculator.computeProjectionSummary(
                                quota = quota,
                                entries = filteredEntries,
                                uid = currentUserId,
                                users = users,
                                todayIso = todayIso,
                                closingBranchName = if (isAdmin) null else resolveClosingBranchName(),
                                activeQuotaIds = activeCycle.activeQuotaIds
                            )
                        )
                    }
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading
    )

    val selectedChartSeries: StateFlow<ChartSeries?> = combine(
        activeQuotasState,
        salesEntriesState,
        selectedMetric,
        selectedBranch,
        isAdminState,
        teamUsers,
        currentUserProfile,
        currentEmployeeIdentity,
        todayDateFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val quotasState = values[0] as UiState<List<BiweeklyQuota>>
        @Suppress("UNCHECKED_CAST")
        val entriesState = values[1] as UiState<List<DailySalesEntry>>
        val metric = values[2] as SalesMetricType
        val branchFilter = values[3] as String
        val isAdmin = values[4] as Boolean
        @Suppress("UNCHECKED_CAST")
        val users = values[5] as List<UserProfile>
        val profile = values[6] as UserProfile?
        val identity = values[7] as EmployeeIdentity?
        val todayIso = values[8] as String

        val quotas = (quotasState as? UiState.Success)?.data ?: return@combine null
        val targetBranch = if (isAdmin) {
            branchFilter.ifBlank { BranchOptions.ALL_BRANCHES_OPTION }
        } else {
            identity?.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
                ?: profile?.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
                ?: branchFilter.ifBlank { BranchOptions.ALL_BRANCHES_OPTION }
        }
        val activeCycle = ProjectionCalculator.resolveActiveCycleInfo(quotas, todayIso)
        val quota = ProjectionCalculator.resolveQuotaForDashboard(
            targetBranch = targetBranch,
            quotas = quotas,
            currentPeriodId = activeCycle.periodId
        ) ?: if (isAdmin && targetBranch == BranchOptions.ALL_BRANCHES_OPTION) {
            activeCycle.generalQuota
        } else {
            null
        } ?: return@combine null
        val rawEntries = (entriesState as? UiState.Success)?.data ?: emptyList()
        val filteredEntries = filterEntriesForSelectedBranch(rawEntries, branchFilter, isAdmin, users)
        ProjectionCalculator.buildChartSeries(
            quota = quota,
            entries = filteredEntries,
            metric = metric,
            users = users,
            todayIso = todayIso,
            activeQuotaIds = activeCycle.activeQuotaIds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null
    )

    val showFirstEntryBriefingToday: StateFlow<Boolean> = combine(
        preferences.observeLastBriefingDate(currentUserId),
        todayDateFlow
    ) { lastDate, todayIso ->
        lastDate != todayIso
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = false
    )

    private var lastAutoSyncedDayKey: String? = null
    private var lastAutoClosingResetDayKey: String? = null
    private var lastObservedClosingDayIso: String = DateAndFormatters.todayIso()

    init {
        viewModelScope.launch(safeExceptionHandler) {
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
        viewModelScope.launch(safeExceptionHandler) {
            isAdminState.collect { isAdmin ->
                if (isAdmin) {
                    repository.ensureDefaultQuotaForAdminIfMissing()
                    repository.ensureDefaultDailyTemplatesIfEmpty()
                }
            }
        }
        viewModelScope.launch(safeExceptionHandler) {
            combine(
                isRoleResolved,
                isAdminState,
                currentUserProfile,
                dailyTaskTemplatesState,
                currentEmployeeIdentity,
                isEmployeeIdentityResolved
            ) { values ->
                values
            }.collect { values ->
                val resolved = values[0] as Boolean
                val isAdmin = values[1] as Boolean
                val profile = values[2] as UserProfile?
                @Suppress("UNCHECKED_CAST")
                val templates = values[3] as List<DailyTaskTemplate>
                val identity = values[4] as EmployeeIdentity?
                val identityResolved = values[5] as Boolean
                if (!resolved) return@collect
                if (isAdmin || bootstrappedAdmin || profile?.parsedRole == UserRole.ADMIN) {
                    if (_selectedBranch.value.isBlank()) {
                        _selectedBranch.value = BranchOptions.ALL_BRANCHES_OPTION
                    }
                    _branchConfirmedThisSession.value = true
                    return@collect
                }
                if (identityRepository != null) {
                    if (!identityResolved) return@collect
                    if (identity == null || !identity.isComplete) {
                        _branchConfirmedThisSession.value = false
                        if (identity?.requiresBranchOnlyReassignment == true) {
                            _selectedBranch.value = ""
                        }
                        return@collect
                    }
                    val branch = identity.branchName ?: return@collect
                    _selectedBranch.value = branch
                    _branchConfirmedThisSession.value = true
                    preferences.saveLastBranch(currentUserId, branch)
                    val templateSignature = templates.joinToString("|") { "${it.id}:${it.daysOfWeek.joinToString(",")}:${it.targetBranches.joinToString(",")}" }
                    syncScheduledChecklistIfNeeded(branch, templateSignature)
                    syncDailyClosingResetIfNeeded(branch)
                } else {
                    val branch = profile?.branchName
                    if (!branch.isNullOrBlank()) {
                        if (_selectedBranch.value.isBlank()) {
                            _selectedBranch.value = branch
                        }
                        preferences.saveLastBranch(currentUserId, branch)
                        val templateSignature = templates.joinToString("|") { "${it.id}:${it.daysOfWeek.joinToString(",")}:${it.targetBranches.joinToString(",")}" }
                        syncScheduledChecklistIfNeeded(branch, templateSignature)
                        syncDailyClosingResetIfNeeded(branch)
                    }
                }
            }
        }
    }

    fun checkDailyScheduleAndClosingReset(nowMillis: Long = System.currentTimeMillis()) {
        val today = DateAndFormatters.todayIso()
        if (today != lastObservedClosingDayIso) {
            lastObservedClosingDayIso = today
            _closingAttachmentDraft.value = ClosingAttachmentDraft.Unchanged
        }
        if (isAdminState.value || bootstrappedAdmin || currentUserProfile.value?.parsedRole == UserRole.ADMIN) {
            return
        }
        val branch = if (identityRepository != null) {
            val identity = currentEmployeeIdentity.value
            if (identity == null || !identity.isComplete) return
            identity.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES } ?: return
        } else {
            currentUserProfile.value?.branchName?.takeIf { it.isNotBlank() }
                ?: _selectedBranch.value.takeIf { it in BranchOptions.ALL_BRANCHES }
                ?: return
        }
        syncScheduledChecklistIfNeeded(branch, forceRefresh = true)
        syncDailyClosingResetIfNeeded(branch, nowMillis)
    }

    private fun syncScheduledChecklistIfNeeded(
        branchLabel: String,
        templateSignature: String = "",
        forceRefresh: Boolean = false
    ) {
        if (isAdminState.value || bootstrappedAdmin || currentUserProfile.value?.parsedRole == UserRole.ADMIN) {
            return
        }
        val today = DateAndFormatters.todayIso()
        val syncKey = "${today}_${currentUserId}_${branchLabel}_$templateSignature"
        if (!forceRefresh && lastAutoSyncedDayKey == syncKey) return
        lastAutoSyncedDayKey = syncKey
        viewModelScope.launch(safeExceptionHandler) {
            val result = repository.syncScheduledTasksForCurrentUser(branchLabel)
            if (result.isSuccess) {
                preferences.markScheduledSyncDoneToday(currentUserId, today)
            } else {
                lastAutoSyncedDayKey = null
            }
        }
    }

    private fun syncDailyClosingResetIfNeeded(
        branchLabel: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (isAdminState.value || bootstrappedAdmin || currentUserProfile.value?.parsedRole == UserRole.ADMIN) {
            return
        }
        if (!DateAndFormatters.isWithinOpeningOrWorkdayHours(nowMillis)) {
            return
        }
        val today = DateAndFormatters.todayIso()
        val todayKey = "${today}_$currentUserId"
        if (lastAutoClosingResetDayKey == todayKey) return
        lastAutoClosingResetDayKey = todayKey
        viewModelScope.launch(safeExceptionHandler) {
            val alreadyResetDate = preferences.getLastDailyClosingResetDate(currentUserId)
            if (alreadyResetDate == today) {
                return@launch
            }
            val result = repository.resetDailyClosingTaskOnNewDayIfNeeded(branchLabel, nowMillis)
            if (result.isSuccess) {
                preferences.markDailyClosingResetDoneToday(currentUserId, today)
            } else {
                lastAutoClosingResetDayKey = null
            }
        }
    }

    fun selectMetric(metric: SalesMetricType) {
        _selectedMetric.value = metric
    }

    fun selectDashboardBranch(branch: String) {
        val normalized = branch.ifBlank { BranchOptions.ALL_BRANCHES_OPTION }
        _selectedBranch.value = normalized
    }

    fun clearTechnicalError() {
        _technicalErrorState.value = null
    }

    fun confirmBranchSelection(branchName: String) {
        viewModelScope.launch(safeExceptionHandler) {
            val cleanBranch = branchName.trim()
            if (isAdminState.value && (cleanBranch.isEmpty() || cleanBranch == BranchOptions.ALL_BRANCHES_OPTION)) {
                _selectedBranch.value = BranchOptions.ALL_BRANCHES_OPTION
                _branchConfirmedThisSession.value = true
                return@launch
            }
            _isBusy.value = true
            preferences.saveLastBranch(currentUserId, cleanBranch)
            val result = repository.updateCurrentUserBranch(cleanBranch)
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _selectedBranch.value = cleanBranch
                    _branchConfirmedThisSession.value = true
                    if (!isAdminState.value && !bootstrappedAdmin) {
                        syncScheduledChecklistIfNeeded(cleanBranch)
                        syncDailyClosingResetIfNeeded(cleanBranch)
                    }
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "No se pudo guardar la sucursal."
                }
            )
        }
    }

    fun reopenBranchSelection() {
        if (isAdminState.value) {
            if (_selectedBranch.value.isBlank()) {
                _selectedBranch.value = BranchOptions.ALL_BRANCHES_OPTION
            }
            return
        }
        val identity = currentEmployeeIdentity.value
        if (identity != null && identity.isComplete) {
            _actionMessage.value = "Tu ficha y sucursal están bloqueadas. Solo el administrador puede liberar tu sucursal."
            return
        }
        _branchConfirmedThisSession.value = false
    }

    fun registerEmployeeIdentity(
        fullName: String,
        payrollNumber: String,
        branchName: String,
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        val idRepo = identityRepository
        if (idRepo == null) {
            confirmBranchSelection(branchName)
            onSuccess()
            return
        }
        viewModelScope.launch(safeExceptionHandler) {
            _isBusy.value = true
            val result = idRepo.registerEmployeeIdentity(
                fullName = fullName,
                payrollNumberRaw = payrollNumber,
                branchName = branchName,
                email = currentUserEmail
            )
            _isBusy.value = false
            result.fold(
                onSuccess = { saved ->
                    _selectedBranch.value = branchName
                    _branchConfirmedThisSession.value = true
                    preferences.saveLastBranch(currentUserId, branchName)
                    if (!isAdminState.value && !bootstrappedAdmin) {
                        syncScheduledChecklistIfNeeded(branchName, forceRefresh = true)
                        syncDailyClosingResetIfNeeded(branchName)
                    }
                    _actionMessage.value = "Ficha registrada: ${saved.formattedTopBarTag}"
                    onSuccess()
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "No se pudo guardar la ficha de identificación."
                    onError(msg)
                    _actionMessage.value = msg
                }
            )
        }
    }

    fun assignBranchToReleasedIdentity(
        branchName: String,
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        val idRepo = identityRepository ?: return
        viewModelScope.launch(safeExceptionHandler) {
            _isBusy.value = true
            val result = idRepo.assignBranchToReleasedIdentity(branchName)
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _selectedBranch.value = branchName
                    _branchConfirmedThisSession.value = true
                    preferences.saveLastBranch(currentUserId, branchName)
                    if (!isAdminState.value && !bootstrappedAdmin) {
                        syncScheduledChecklistIfNeeded(branchName, forceRefresh = true)
                        syncDailyClosingResetIfNeeded(branchName)
                    }
                    _actionMessage.value = "Sucursal asignada: $branchName"
                    onSuccess()
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "No se pudo actualizar la sucursal asignada."
                    onError(msg)
                    _actionMessage.value = msg
                }
            )
        }
    }

    fun releaseEmployeeBranch(identity: EmployeeIdentity) {
        if (!isCurrentAccountAdmin()) {
            _actionMessage.value = "Solo el administrador puede liberar la sucursal de un empleado."
            return
        }
        val idRepo = identityRepository ?: return
        viewModelScope.launch(safeExceptionHandler) {
            _isBusy.value = true
            val result = idRepo.releaseEmployeeBranchAsAdmin(identity)
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Sucursal liberada para ${identity.fullName} (Nómina ${identity.displayPayroll})."
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "No se pudo liberar la sucursal."
                }
            )
        }
    }

    fun deleteEmployeeIdentityCompletely(identity: EmployeeIdentity) {
        if (!isCurrentAccountAdmin()) {
            _actionMessage.value = "Solo el administrador puede eliminar fichas de empleados."
            return
        }
        val idRepo = identityRepository ?: return
        viewModelScope.launch(safeExceptionHandler) {
            _isBusy.value = true
            val result = idRepo.deleteEmployeeIdentityCompletelyAsAdmin(identity)
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Ficha de ${identity.fullName} (Nómina ${identity.displayPayroll}) eliminada por completo."
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "No se pudo eliminar la ficha."
                }
            )
        }
    }

    fun dismissDailyBriefingForToday() {
        viewModelScope.launch(safeExceptionHandler) {
            preferences.markBriefingSeenToday(currentUserId, DateAndFormatters.todayIso())
        }
    }

    fun markNotificationsAlerted(ids: Collection<String>) {
        viewModelScope.launch(safeExceptionHandler) {
            preferences.markNotificationsAlerted(currentUserId, ids)
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun acknowledgeNotification(notificationId: String) {
        viewModelScope.launch(safeExceptionHandler) {
            repository.acknowledgeNotification(notificationId)
        }
    }

    fun updateClosingAttachmentDraft(draft: ClosingAttachmentDraft) {
        if (isCurrentAccountAdmin()) return
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
        if (isCurrentAccountAdmin()) {
            _actionMessage.value = "Las cuentas de administrador solo supervisan avances; solo las sucursales registran cierres diarios."
            return
        }
        viewModelScope.launch(safeExceptionHandler) {
            _isBusy.value = true
            val identity = currentEmployeeIdentity.value
            val idRepo = identityRepository
            val result = if (identity != null && identity.isComplete && idRepo != null) {
                idRepo.upsertDailySalesEntryWithIdentity(
                    quotaId = quotaId,
                    dateString = DateAndFormatters.todayIso(),
                    identity = identity,
                    branchName = resolveClosingBranchName(),
                    globalSales = globalSales,
                    creditSales = creditSales,
                    newCustomers = newCustomers,
                    appMaOperations = appMaOperations,
                    newCustomerRequests = newCustomerRequests,
                    notes = notes,
                    attachmentDraft = attachmentDraft
                )
            } else {
                repository.upsertDailySalesEntry(
                    quotaId = quotaId,
                    dateString = DateAndFormatters.todayIso(),
                    userName = resolveClosingUserLabel(),
                    branchName = resolveClosingBranchName(),
                    globalSales = globalSales,
                    creditSales = creditSales,
                    newCustomers = newCustomers,
                    appMaOperations = appMaOperations,
                    newCustomerRequests = newCustomerRequests,
                    notes = notes,
                    attachmentDraft = attachmentDraft
                )
            }
            _isBusy.value = false
            result.fold(
                onSuccess = { saveRes ->
                    _closingAttachmentDraft.value = ClosingAttachmentDraft.Unchanged
                    preferences.markBriefingSeenToday(currentUserId, DateAndFormatters.todayIso())
                    _actionMessage.value = "Cierre guardado (${saveRes.branchPeriodEntriesCount} día(s)). Acumulado quincena: ${DateAndFormatters.formatCurrency(saveRes.branchAccumulatedGlobalSales)}"
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
        branchName: String = BranchOptions.ALL_BRANCHES_OPTION,
        applyToAllIndividualBranches: Boolean = false,
        periodId: String? = null,
        onSuccess: () -> Unit
    ) {
        if (!isCurrentAccountAdmin()) {
            _actionMessage.value = "Solo los administradores pueden configurar metas quincenales."
            return
        }
        viewModelScope.launch(safeExceptionHandler) {
            _isBusy.value = true
            val openCycle = activeCycleInfoState.value
            val resolvedPeriodId = periodId?.trim()?.takeIf { it.isNotBlank() }
                ?: if (startDate == openCycle.startDate && openCycle.periodId.isNotBlank()) {
                    openCycle.periodId
                } else {
                    DateAndFormatters.biweeklyPeriodIdForIso(startDate)
                }
            val result = if (quotaExtensions != null) {
                quotaExtensions.saveIndependentBiweeklyQuota(
                    title = title,
                    startDate = startDate,
                    endDate = endDate,
                    totalDays = totalDays,
                    targetGlobalSales = targetGlobalSales,
                    targetCreditSales = targetCreditSales,
                    targetNewCustomers = targetNewCustomers,
                    targetAppMaOperations = targetAppMaOperations,
                    targetNewCustomerRequests = targetNewCustomerRequests,
                    branchName = branchName,
                    periodId = resolvedPeriodId,
                    applyToAllIndividualBranches = applyToAllIndividualBranches
                )
            } else {
                repository.saveBiweeklyQuota(
                    title = title,
                    startDate = startDate,
                    endDate = endDate,
                    totalDays = totalDays,
                    targetGlobalSales = targetGlobalSales,
                    targetCreditSales = targetCreditSales,
                    targetNewCustomers = targetNewCustomers,
                    targetAppMaOperations = targetAppMaOperations,
                    targetNewCustomerRequests = targetNewCustomerRequests,
                    branchName = branchName,
                    periodId = resolvedPeriodId,
                    applyToAllIndividualBranches = applyToAllIndividualBranches
                )
            }
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = if (branchName == BranchOptions.ALL_BRANCHES_OPTION) {
                        if (applyToAllIndividualBranches) {
                            "Meta quincenal aplicada individualmente a las sucursales."
                        } else {
                            "Meta quincenal general independiente actualizada."
                        }
                    } else {
                        "Meta quincenal actualizada para $branchName."
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "No se pudo actualizar la meta."
                }
            )
        }
    }

    fun closeAndResetBiweeklyCycle(
        onSuccess: (BiweeklyCycleArchive) -> Unit = {}
    ) {
        if (!isCurrentAccountAdmin()) {
            _actionMessage.value = "Solo el administrador puede confirmar el cierre del ciclo quincenal."
            return
        }
        val ext = quotaExtensions
        if (ext == null) {
            _actionMessage.value = "Servicio de cierre de ciclo no disponible."
            return
        }
        viewModelScope.launch(safeExceptionHandler) {
            _isBusy.value = true
            val todayIso = DateAndFormatters.todayIso()
            val activeQuotas = (activeQuotasState.value as? UiState.Success)?.data ?: emptyList()
            val result = ext.closeAndResetBiweeklyCycle(
                adminGeneralSummary = adminGeneralSummaryState.value,
                branchSummaries = branchSalesSummariesState.value,
                activeQuotas = activeQuotas,
                todayIso = todayIso
            )
            _isBusy.value = false
            result.fold(
                onSuccess = { archive ->
                    _actionMessage.value = "Ciclo cerrado y archivado. Acumulados reiniciados a $0.00 manteniendo tus cuotas base."
                    onSuccess(archive)
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "No se pudo cerrar el ciclo quincenal."
                }
            )
        }
    }

    fun updateGeneralIndicatorTarget(
        metric: SalesMetricType,
        newTargetValue: Double,
        onSuccess: () -> Unit = {}
    ) {
        if (!isCurrentAccountAdmin()) {
            _actionMessage.value = "Solo los administradores pueden editar la cuota general."
            return
        }
        val ext = quotaExtensions
        if (ext == null) {
            _actionMessage.value = "Servicio de cuotas no disponible."
            return
        }
        viewModelScope.launch(safeExceptionHandler) {
            _isBusy.value = true
            val currentGeneralQuota = adminGeneralSummaryState.value?.quota
                ?: (activeQuotaState.value as? UiState.Success)?.data
            val result = ext.updateAdminGeneralIndicatorTarget(
                metric = metric,
                newTargetValue = newTargetValue,
                baseGeneralQuota = currentGeneralQuota
            )
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Meta general de ${metric.titleEs} guardada."
                    onSuccess()
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "No se pudo guardar la meta de ${metric.titleEs}."
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
        viewModelScope.launch(safeExceptionHandler) {
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

    fun completeTaskWithEvidenceFiles(
        task: AssignedTask,
        files: List<EncodedAttachment>,
        onError: (String) -> Unit = {},
        onSuccess: () -> Unit
    ) {
        if (isCurrentAccountAdmin() && isDailySalesClosingTask(task)) {
            val msg = "Las cuentas de administrador no suben cierres de venta diaria; solo las sucursales lo registran."
            _actionMessage.value = msg
            onError(msg)
            return
        }
        viewModelScope.launch(safeExceptionHandler) {
            _isBusy.value = true
            val identity = currentEmployeeIdentity.value
            val idRepo = identityRepository
            val result = if (!isCurrentAccountAdmin() && identity != null && identity.isComplete && idRepo != null) {
                idRepo.completeTaskWithEvidenceAndIdentity(
                    task = task,
                    identity = identity,
                    files = files
                )
            } else {
                val uploaderName = currentUserProfile.value?.effectiveBranchLabel ?: currentUserName
                repository.completeTaskWithEvidenceFiles(
                    task = task,
                    uploaderName = uploaderName,
                    files = files
                )
            }
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Tarea completada y bloqueada (${files.size} archivo(s) enviados)."
                    onSuccess()
                },
                onFailure = { err ->
                    val msg = when (err) {
                        is BranchTaskAlreadyCompletedException -> err.message ?: "Esta tarea ya fue completada por otro compañero."
                        else -> err.localizedMessage ?: "No se pudo registrar la evidencia."
                    }
                    _actionMessage.value = msg
                    onError(msg)
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
        viewModelScope.launch(safeExceptionHandler) {
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

    fun saveDailyTaskTemplate(
        existingTemplateId: String? = null,
        title: String,
        description: String,
        priority: TaskPriority,
        daysOfWeek: List<String>,
        targetBranches: List<String>,
        dueTime: String? = "23:59",
        deadlineLabel: String? = null,
        dueDayOffset: Long = 0L,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch(safeExceptionHandler) {
            _isBusy.value = true
            val result = repository.saveDailyTaskTemplate(
                existingTemplateId = existingTemplateId,
                title = title,
                description = description,
                priority = priority,
                daysOfWeek = daysOfWeek,
                targetBranches = targetBranches,
                dueTime = dueTime,
                deadlineLabel = deadlineLabel,
                dueDayOffset = dueDayOffset,
                syncTodayInstances = true
            )
            _isBusy.value = false
            result.fold(
                onSuccess = { saved ->
                    _actionMessage.value = if (existingTemplateId.isNullOrBlank()) {
                        "Plantilla diaria \"${saved.title}\" creada y programada (${saved.formatDaysSummaryEs()})."
                    } else {
                        "Plantilla diaria \"${saved.title}\" actualizada definitivamente."
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "No se pudo guardar la plantilla diaria."
                }
            )
        }
    }

    fun deleteDailyTaskTemplatePermanently(
        template: DailyTaskTemplate? = null,
        task: AssignedTask? = null,
        removeBranchOnly: Boolean = false,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch(safeExceptionHandler) {
            _isBusy.value = true
            val result = repository.deleteDailyTaskTemplatePermanently(
                template = template,
                task = task,
                removeBranchOnly = removeBranchOnly
            )
            _isBusy.value = false
            result.fold(
                onSuccess = { count ->
                    _actionMessage.value = if (removeBranchOnly) {
                        "Tarea diaria quitada de ${task?.assigneeName ?: "la sucursal"}."
                    } else {
                        "Plantilla diaria eliminada definitivamente ($count instancia(s) de hoy quitadas)."
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "No se pudo eliminar la plantilla diaria."
                }
            )
        }
    }

    fun updateDailyTask(
        task: AssignedTask,
        title: String,
        description: String,
        priority: TaskPriority,
        dueTime: String?,
        deadlineLabel: String?,
        applyToAllBranchesSameDay: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch(safeExceptionHandler) {
            _isBusy.value = true
            val result = repository.updateDailyTask(
                task = task,
                title = title,
                description = description,
                priority = priority,
                dueTime = dueTime,
                deadlineLabel = deadlineLabel,
                applyToAllBranchesSameDay = applyToAllBranchesSameDay
            )
            _isBusy.value = false
            result.fold(
                onSuccess = { count ->
                    _actionMessage.value = if (applyToAllBranchesSameDay) {
                        "Tarea diaria actualizada en $count sucursal(es)."
                    } else {
                        "Tarea diaria actualizada."
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _actionMessage.value = err.localizedMessage ?: "No se pudo actualizar la tarea diaria."
                }
            )
        }
    }

    fun deleteDailyTask(
        task: AssignedTask,
        deleteAllBranchesSameDay: Boolean,
        onSuccess: () -> Unit
    ) {
        deleteDailyTaskTemplatePermanently(
            template = null,
            task = task,
            removeBranchOnly = !deleteAllBranchesSameDay,
            onSuccess = onSuccess
        )
    }

    fun toggleUserAdminRole(targetUser: UserProfile, makeAdmin: Boolean) {
        viewModelScope.launch(safeExceptionHandler) {
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
        if (targetUser.parsedRole == UserRole.ADMIN) {
            _actionMessage.value = "Las cuentas de administrador no reciben tareas programadas."
            return
        }
        viewModelScope.launch(safeExceptionHandler) {
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

    private fun resolveClosingUserLabel(): String {
        val identity = currentEmployeeIdentity.value
        if (identity != null && identity.hasIdentityCore) {
            return "${identity.displayPayroll} · ${identity.fullName}".take(80)
        }
        val selected = _selectedBranch.value
        if (selected in BranchOptions.ALL_BRANCHES) return selected
        return currentUserProfile.value?.effectiveBranchLabel ?: currentUserName
    }

    private fun resolveClosingBranchName(): String? {
        val identityBranch = currentEmployeeIdentity.value?.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
        if (identityBranch != null) return identityBranch
        val selected = _selectedBranch.value
        if (selected in BranchOptions.ALL_BRANCHES) return selected
        return currentUserProfile.value?.branchName?.takeIf { it in BranchOptions.ALL_BRANCHES }
    }

    private fun isCurrentAccountAdmin(): Boolean =
        isAdminState.value || bootstrappedAdmin || currentUserProfile.value?.parsedRole == UserRole.ADMIN

    private fun isDailySalesClosingTask(task: AssignedTask): Boolean =
        ScheduledTasksCatalog.isDailySalesClosingTask(task)

    private fun filterEntriesForSelectedBranch(
        entries: List<DailySalesEntry>,
        branchFilter: String,
        isAdmin: Boolean,
        users: List<UserProfile>
    ): List<DailySalesEntry> {
        if (!isAdmin || branchFilter.isBlank() || branchFilter == BranchOptions.ALL_BRANCHES_OPTION) {
            return entries
        }
        val matchingUserIds = users
            .filter { it.branchName == branchFilter || it.effectiveBranchLabel == branchFilter }
            .map { it.userId }
            .toSet()
        return entries.filter { entry ->
            entry.branchName == branchFilter ||
                entry.userName == branchFilter ||
                entry.userId in matchingUserIds
        }
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
