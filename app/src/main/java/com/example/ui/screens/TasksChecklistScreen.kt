package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AssignedTask
import com.example.data.model.BiweeklyQuota
import com.example.data.model.BranchOptions
import com.example.data.model.BranchTaskSummary
import com.example.data.model.DailyTaskTemplate
import com.example.data.model.DayTaskGroup
import com.example.data.model.TaskEvidence
import com.example.data.model.TaskEvidenceAttachmentFile
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.TaskType
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.WeekdayOption
import com.example.data.model.buildBranchTaskSummaries
import com.example.data.model.buildDayTaskGroups
import com.example.data.repository.ScheduledTasksCatalog
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandRedSoft
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenSoft
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberSoft
import com.example.utils.DateAndFormatters
import com.example.utils.DateAndFormatters.TaskScheduleStatus
import com.example.utils.EncodedAttachment
import com.example.utils.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class TaskFilter(val label: String) {
    ALL("Todas"),
    PENDING("Pendientes"),
    EXTRAORDINARY("Extraordinarias"),
    COMPLETED("Completadas")
}

private enum class AdminTaskViewMode(val label: String) {
    BY_BRANCH("Por Sucursal"),
    DETAILED_LIST("Lista Detallada"),
    TEMPLATES("Plantillas")
}

@Composable
fun TasksChecklistScreen(
    isAdmin: Boolean,
    isBusy: Boolean,
    nowMillis: Long,
    tasksState: UiState<List<AssignedTask>>,
    dailyTaskTemplates: List<DailyTaskTemplate> = emptyList(),
    evidencesState: UiState<List<TaskEvidence>>,
    teamUsers: List<UserProfile>,
    currentUserProfile: UserProfile?,
    activeQuota: BiweeklyQuota?,
    onCreateTask: (
        assignees: List<UserProfile>,
        title: String,
        description: String,
        taskType: TaskType,
        priority: TaskPriority,
        dueDate: String,
        dueTime: String?,
        deadlineLabel: String?,
        onSuccess: () -> Unit
    ) -> Unit,
    onSaveDailyTaskTemplate: (
        existingTemplateId: String?,
        title: String,
        description: String,
        priority: TaskPriority,
        daysOfWeek: List<String>,
        targetBranches: List<String>,
        dueTime: String?,
        deadlineLabel: String?,
        dueDayOffset: Long,
        onSuccess: () -> Unit
    ) -> Unit = { _, _, _, _, _, _, _, _, _, onSuccess -> onSuccess() },
    onDeleteDailyTaskTemplate: (
        template: DailyTaskTemplate?,
        task: AssignedTask?,
        removeBranchOnly: Boolean,
        onSuccess: () -> Unit
    ) -> Unit = { _, _, _, onSuccess -> onSuccess() },
    onCompleteTaskWithEvidenceFiles: (
        task: AssignedTask,
        files: List<EncodedAttachment>,
        onSuccess: () -> Unit
    ) -> Unit,
    onConfirmBranchResetForQuota: (branchName: String, branchUsers: List<UserProfile>) -> Unit,
    onLoadEvidenceFiles: suspend (TaskEvidence) -> Result<List<TaskEvidenceAttachmentFile>>,
    onUpdateDailyTask: (
        task: AssignedTask,
        title: String,
        description: String,
        priority: TaskPriority,
        dueTime: String?,
        deadlineLabel: String?,
        applyToAllBranchesSameDay: Boolean,
        onSuccess: () -> Unit
    ) -> Unit = { _, _, _, _, _, _, _, onSuccess -> onSuccess() },
    onDeleteDailyTask: (
        task: AssignedTask,
        deleteAllBranchesSameDay: Boolean,
        onSuccess: () -> Unit
    ) -> Unit = { _, _, onSuccess -> onSuccess() }
) {
    var adminViewMode by remember { mutableStateOf(AdminTaskViewMode.BY_BRANCH) }
    var selectedFilter by remember { mutableStateOf(TaskFilter.ALL) }
    var taskToDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreateTaskDialog by remember { mutableStateOf(false) }
    var createDialogInitialType by remember { mutableStateOf(TaskType.EXTRAORDINARY) }
    var evidencePreviewForAdmin by remember { mutableStateOf<TaskEvidence?>(null) }
    var dailyTaskToEdit by remember { mutableStateOf<AssignedTask?>(null) }
    var dailyTaskToDelete by remember { mutableStateOf<AssignedTask?>(null) }
    var templateToEdit by remember { mutableStateOf<DailyTaskTemplate?>(null) }
    var templateToDelete by remember { mutableStateOf<DailyTaskTemplate?>(null) }
    val expandedDayState = remember { mutableStateMapOf<String, Boolean>() }

    val taskToDocument = remember(taskToDocumentId, tasksState) {
        val targetId = taskToDocumentId ?: return@remember null
        (tasksState as? UiState.Success)?.data
            ?.firstOrNull { it.id == targetId && it.parsedStatus == TaskStatus.PENDING }
    }

    val evidencesMap = remember(evidencesState, isAdmin) {
        if (!isAdmin) emptyMap()
        else (evidencesState as? UiState.Success)?.data?.associateBy { it.taskId } ?: emptyMap()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = {
                        createDialogInitialType = if (adminViewMode == AdminTaskViewMode.TEMPLATES) {
                            TaskType.DAILY
                        } else {
                            TaskType.EXTRAORDINARY
                        }
                        showCreateTaskDialog = true
                    },
                    containerColor = BrandRed,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("create_task_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Crear nueva tarea o plantilla")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isAdmin) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AdminTaskViewMode.entries.forEach { mode ->
                        FilterChip(
                            selected = adminViewMode == mode,
                            onClick = { adminViewMode = mode },
                            label = {
                                val text = if (mode == AdminTaskViewMode.TEMPLATES && dailyTaskTemplates.isNotEmpty()) {
                                    "${mode.label} (${dailyTaskTemplates.size})"
                                } else {
                                    mode.label
                                }
                                Text(text, style = MaterialTheme.typography.labelMedium)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = BrandNavy
                            ),
                            modifier = Modifier.testTag("admin_task_mode_${mode.name.lowercase()}")
                        )
                    }
                }
            }

            if (!isAdmin || adminViewMode == AdminTaskViewMode.DETAILED_LIST) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TaskFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = filter == selectedFilter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = BrandNavy
                            )
                        )
                    }
                }
            }

            if (isAdmin && adminViewMode == AdminTaskViewMode.TEMPLATES) {
                AdminDailyTemplatesView(
                    templates = dailyTaskTemplates,
                    onCreateTemplate = {
                        createDialogInitialType = TaskType.DAILY
                        showCreateTaskDialog = true
                    },
                    onEditTemplate = { tpl -> templateToEdit = tpl },
                    onDeleteTemplate = { tpl -> templateToDelete = tpl }
                )
            } else {
                when (tasksState) {
                    is UiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = BrandNavy)
                        }
                    }

                    is UiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tasksState.message,
                                color = BrandRed,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    is UiState.Success -> {
                        if (isAdmin && adminViewMode == AdminTaskViewMode.BY_BRANCH) {
                            val summaries = remember(teamUsers, tasksState.data) {
                                buildBranchTaskSummaries(teamUsers, tasksState.data)
                            }
                            AdminBranchesChecklistView(
                                summaries = summaries,
                                teamUsers = teamUsers,
                                activeQuota = activeQuota,
                                isBusy = isBusy,
                                nowMillis = nowMillis,
                                evidencesMap = evidencesMap,
                                onViewAdminEvidence = { ev -> evidencePreviewForAdmin = ev },
                                onConfirmBranchResetForQuota = onConfirmBranchResetForQuota,
                                onEditDailyTask = { task -> dailyTaskToEdit = task },
                                onDeleteDailyTask = { task -> dailyTaskToDelete = task }
                            )
                        } else {
                            val filtered = remember(tasksState.data, selectedFilter) {
                                when (selectedFilter) {
                                    TaskFilter.ALL -> tasksState.data
                                    TaskFilter.PENDING -> tasksState.data.filter { it.parsedStatus == TaskStatus.PENDING }
                                    TaskFilter.EXTRAORDINARY -> tasksState.data.filter { it.parsedType == TaskType.EXTRAORDINARY }
                                    TaskFilter.COMPLETED -> tasksState.data.filter { it.parsedStatus == TaskStatus.COMPLETED }
                                }
                            }

                            if (filtered.isEmpty()) {
                                EmptyTasksState(
                                    isAdmin = isAdmin,
                                    onCreateTask = {
                                        createDialogInitialType = TaskType.EXTRAORDINARY
                                        showCreateTaskDialog = true
                                    }
                                )
                            } else {
                                val dayGroups = remember(filtered) { buildDayTaskGroups(filtered) }

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(dayGroups, key = { it.dateIso }) { group ->
                                        val isExpanded = expandedDayState[group.dateIso] ?: group.isToday
                                        DayTaskGroupCard(
                                            group = group,
                                            isExpanded = isExpanded,
                                            onToggleExpand = {
                                                expandedDayState[group.dateIso] = !isExpanded
                                            }
                                        ) {
                                            group.tasks.forEach { task ->
                                                TaskCardItem(
                                                    task = task,
                                                    nowMillis = nowMillis,
                                                    isAdmin = isAdmin,
                                                    linkedEvidence = if (isAdmin) evidencesMap[task.id] else null,
                                                    onDocumentClick = {
                                                        if (task.parsedStatus == TaskStatus.PENDING) {
                                                            taskToDocumentId = task.id
                                                        }
                                                    },
                                                    onViewAdminEvidence = { ev -> evidencePreviewForAdmin = ev },
                                                    onEditDailyTask = { dailyTaskToEdit = task },
                                                    onDeleteDailyTask = { dailyTaskToDelete = task }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    taskToDocument?.let { task ->
        TaskDocumentationDialog(
            task = task,
            isBusy = isBusy,
            onDismiss = { taskToDocumentId = null },
            onSubmitFiles = { files ->
                onCompleteTaskWithEvidenceFiles(task, files) {
                    taskToDocumentId = null
                }
            }
        )
    }

    if (showCreateTaskDialog && isAdmin) {
        val availableUsers = remember(teamUsers) {
            teamUsers.filter { it.parsedRole != UserRole.ADMIN }
        }
        CreateTaskDialog(
            teamUsers = availableUsers,
            initialTaskType = createDialogInitialType,
            isBusy = isBusy,
            onDismiss = { showCreateTaskDialog = false },
            onCreateExtraordinary = { assignees, title, desc, priority, dueDate, dueTime, deadlineLabel ->
                onCreateTask(assignees, title, desc, TaskType.EXTRAORDINARY, priority, dueDate, dueTime, deadlineLabel) {
                    showCreateTaskDialog = false
                }
            },
            onSaveDailyTemplate = { title, desc, priority, daysOfWeek, targetBranches, dueTime, deadlineLabel, dueDayOffset ->
                onSaveDailyTaskTemplate(
                    null,
                    title,
                    desc,
                    priority,
                    daysOfWeek,
                    targetBranches,
                    dueTime,
                    deadlineLabel,
                    dueDayOffset
                ) {
                    showCreateTaskDialog = false
                }
            }
        )
    }

    evidencePreviewForAdmin?.let { evidence ->
        if (isAdmin) {
            EvidencePreviewDialog(
                evidence = evidence,
                onLoadEvidenceFiles = onLoadEvidenceFiles,
                onDismiss = { evidencePreviewForAdmin = null }
            )
        }
    }

    val editingTask = dailyTaskToEdit
    val editingTemplate = templateToEdit
    if (isAdmin && (editingTask != null || editingTemplate != null)) {
        val linkedTemplate = editingTemplate ?: editingTask?.let { findTemplateForTask(it, dailyTaskTemplates) }
        EditDailyTaskDialog(
            task = editingTask,
            template = linkedTemplate,
            isBusy = isBusy,
            onDismiss = {
                if (!isBusy) {
                    dailyTaskToEdit = null
                    templateToEdit = null
                }
            },
            onConfirmTemplateEdit = { existingTemplateId, title, desc, priority, daysOfWeek, targetBranches, dueTime, deadlineLabel, dueDayOffset ->
                onSaveDailyTaskTemplate(
                    existingTemplateId,
                    title,
                    desc,
                    priority,
                    daysOfWeek,
                    targetBranches,
                    dueTime,
                    deadlineLabel,
                    dueDayOffset
                ) {
                    dailyTaskToEdit = null
                    templateToEdit = null
                }
            },
            onConfirmSingleTaskEdit = { task, title, desc, priority, dueTime, deadlineLabel ->
                onUpdateDailyTask(task, title, desc, priority, dueTime, deadlineLabel, false) {
                    dailyTaskToEdit = null
                    templateToEdit = null
                }
            }
        )
    }

    val deletingTask = dailyTaskToDelete
    val deletingTemplate = templateToDelete
    if (isAdmin && (deletingTask != null || deletingTemplate != null)) {
        val linkedTemplate = deletingTemplate ?: deletingTask?.let { findTemplateForTask(it, dailyTaskTemplates) }
        DeleteDailyTaskDialog(
            task = deletingTask,
            template = linkedTemplate,
            isBusy = isBusy,
            onDismiss = {
                if (!isBusy) {
                    dailyTaskToDelete = null
                    templateToDelete = null
                }
            },
            onConfirmDelete = { removeBranchOnly ->
                onDeleteDailyTaskTemplate(linkedTemplate, deletingTask, removeBranchOnly) {
                    dailyTaskToDelete = null
                    templateToDelete = null
                }
            }
        )
    }
}

private fun findTemplateForTask(
    task: AssignedTask,
    templates: List<DailyTaskTemplate>
): DailyTaskTemplate? {
    if (!task.templateId.isNullOrBlank()) {
        templates.firstOrNull { it.id == task.templateId }?.let { return it }
    }
    val slug = DateAndFormatters.extractScheduledSlug(task.id)
    if (!slug.isNullOrBlank()) {
        templates.firstOrNull {
            it.slug == slug || it.id == ScheduledTasksCatalog.templateDocIdForSlug(slug)
        }?.let { return it }
    }
    return templates.firstOrNull {
        it.title.trim().equals(task.title.trim(), ignoreCase = true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminDailyTemplatesView(
    templates: List<DailyTaskTemplate>,
    onCreateTemplate: () -> Unit,
    onEditTemplate: (DailyTaskTemplate) -> Unit,
    onDeleteTemplate: (DailyTaskTemplate) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Plantillas Recurrentes",
                                style = MaterialTheme.typography.titleMedium,
                                color = BrandNavy
                            )
                            Text(
                                text = "Se generan automáticamente cada día según los días de la semana y sucursales activos. Nunca se asignan a cuentas de administrador.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(
                        onClick = onCreateTemplate,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("create_daily_template_button")
                    ) {
                        Icon(Icons.Filled.EventRepeat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nueva Plantilla Diaria")
                    }
                }
            }
        }

        if (templates.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "No hay plantillas diarias activas",
                            style = MaterialTheme.typography.titleSmall,
                            color = BrandNavy
                        )
                        Text(
                            text = "Crea una plantilla recurrente para elegir los días de la semana y las sucursales asignadas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(templates, key = { it.id }) { tpl ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("template_card_${tpl.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tpl.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = BrandNavy
                                )
                                if (tpl.description.isNotBlank()) {
                                    Text(
                                        text = tpl.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Surface(
                                color = if (tpl.parsedPriority == TaskPriority.URGENT) BrandRedSoft else MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = tpl.parsedPriority.labelEs,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (tpl.parsedPriority == TaskPriority.URGENT) BrandRed else BrandNavy,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Días: ${tpl.formatDaysSummaryEs()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandNavy,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = tpl.formatBranchesSummaryEs(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            if (!tpl.deadlineLabel.isNullOrBlank() || !tpl.dueTime.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = tpl.deadlineLabel ?: "Límite ${tpl.dueTime}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onEditTemplate(tpl) },
                                modifier = Modifier.testTag("edit_template_${tpl.id}")
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, tint = BrandNavy, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Editar", color = BrandNavy)
                            }
                            TextButton(
                                onClick = { onDeleteTemplate(tpl) },
                                modifier = Modifier.testTag("delete_template_${tpl.id}")
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = BrandRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Eliminar", color = BrandRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTaskGroupCard(
    group: DayTaskGroup,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    val badgeBg = when {
        group.allCompleted -> SuccessGreenSoft
        group.isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val badgeFg = when {
        group.allCompleted -> SuccessGreen
        group.isToday -> BrandNavy
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("day_group_card_${group.dateIso}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("day_group_header_${group.dateIso}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = null,
                            tint = badgeFg,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${group.completedCount} de ${group.totalCount} tareas completadas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(color = badgeBg, shape = RoundedCornerShape(50)) {
                    Text(
                        text = "${group.completedCount}/${group.totalCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Ocultar día ${group.title}" else "Desplegar día ${group.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    content()
                }
            }
        }
    }
}

@Composable
private fun AdminBranchesChecklistView(
    summaries: List<BranchTaskSummary>,
    teamUsers: List<UserProfile>,
    activeQuota: BiweeklyQuota?,
    isBusy: Boolean,
    nowMillis: Long,
    evidencesMap: Map<String, TaskEvidence>,
    onViewAdminEvidence: (TaskEvidence) -> Unit,
    onConfirmBranchResetForQuota: (branchName: String, branchUsers: List<UserProfile>) -> Unit,
    onEditDailyTask: (AssignedTask) -> Unit,
    onDeleteDailyTask: (AssignedTask) -> Unit
) {
    val expandedState = remember { mutableStateMapOf<String, Boolean>() }
    val completedBranches = summaries.count { it.allTasksCompleted }
    var pendingResetBranch by remember { mutableStateOf<Pair<String, List<UserProfile>>?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cumplimiento por Sucursal",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Toca una sucursal para ver sus días desplegables y confirmar reinicios por cuota.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = if (completedBranches == summaries.size && summaries.isNotEmpty()) SuccessGreenSoft else BrandRedSoft,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "$completedBranches/${summaries.size} al 100%",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (completedBranches == summaries.size && summaries.isNotEmpty()) SuccessGreen else BrandRed,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        items(summaries, key = { it.branchName }) { summary ->
            val isExpanded = expandedState[summary.branchName] == true
            val branchUsers = remember(teamUsers, summary.branchName) {
                teamUsers.filter { it.effectiveBranchLabel == summary.branchName || it.branchName == summary.branchName }
            }
            BranchChecklistCard(
                summary = summary,
                branchUsers = branchUsers,
                activeQuota = activeQuota,
                isBusy = isBusy,
                nowMillis = nowMillis,
                isExpanded = isExpanded,
                evidencesMap = evidencesMap,
                onToggleExpand = {
                    expandedState[summary.branchName] = !isExpanded
                },
                onRequestBranchReset = {
                    pendingResetBranch = summary.branchName to branchUsers
                },
                onViewAdminEvidence = onViewAdminEvidence,
                onEditDailyTask = onEditDailyTask,
                onDeleteDailyTask = onDeleteDailyTask
            )
        }
    }

    val resetRequest = pendingResetBranch
    if (resetRequest != null && activeQuota != null) {
        val (branchName, users) = resetRequest
        AlertDialog(
            onDismissRequest = { if (!isBusy) pendingResetBranch = null },
            title = { Text("Confirmar reinicio · $branchName") },
            text = {
                Text(
                    "Se actualizará la lista de tareas de $branchName para la cuota \"${activeQuota.title}\". " +
                        "Se borrarán las tareas del ciclo anterior de esta sucursal y se generará su nuevo check list base."
                )
            },
            confirmButton = {
                Button(
                    enabled = !isBusy,
                    onClick = {
                        onConfirmBranchResetForQuota(branchName, users)
                        pendingResetBranch = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) {
                    Text("Confirmar reinicio")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingResetBranch = null }, enabled = !isBusy) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun BranchChecklistCard(
    summary: BranchTaskSummary,
    branchUsers: List<UserProfile>,
    activeQuota: BiweeklyQuota?,
    isBusy: Boolean,
    nowMillis: Long,
    isExpanded: Boolean,
    evidencesMap: Map<String, TaskEvidence>,
    onToggleExpand: () -> Unit,
    onRequestBranchReset: () -> Unit,
    onViewAdminEvidence: (TaskEvidence) -> Unit,
    onEditDailyTask: (AssignedTask) -> Unit,
    onDeleteDailyTask: (AssignedTask) -> Unit
) {
    val indicatorColor = if (summary.allTasksCompleted) SuccessGreen else BrandRed
    val indicatorBg = if (summary.allTasksCompleted) SuccessGreenSoft else BrandRedSoft
    val statusText = when {
        summary.totalCount == 0 -> "Sin tareas"
        summary.allTasksCompleted -> "Todas cumplidas"
        else -> "Pendientes (${summary.totalCount - summary.completedCount})"
    }

    val needsQuotaReset = activeQuota != null &&
        branchUsers.isNotEmpty() &&
        branchUsers.any { it.confirmedQuotaId != activeQuota.id }
    val quotaConfirmed = activeQuota != null &&
        branchUsers.isNotEmpty() &&
        branchUsers.all { it.confirmedQuotaId == activeQuota.id }

    val expandedDaysInBranch = remember(summary.branchName) { mutableStateMapOf<String, Boolean>() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("branch_status_card_${summary.branchName}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(indicatorBg),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(indicatorColor)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Storefront,
                            contentDescription = null,
                            tint = BrandNavy,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = summary.branchName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (summary.totalCount == 0) {
                            "Sin tareas registradas aún"
                        } else {
                            "${summary.completedCount} de ${summary.totalCount} tareas realizadas"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (needsQuotaReset) {
                        Text(
                            text = "Cuota actualizada · Requiere confirmar reinicio",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmber
                        )
                    }
                }

                Surface(color = indicatorBg, shape = RoundedCornerShape(50)) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = indicatorColor,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Ocultar tareas de ${summary.branchName}" else "Desplegar tareas de ${summary.branchName}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    if (activeQuota != null) {
                        val resetBannerBg = when {
                            quotaConfirmed -> SuccessGreenSoft
                            needsQuotaReset -> WarningAmberSoft
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val resetBannerFg = when {
                            quotaConfirmed -> SuccessGreen
                            needsQuotaReset -> WarningAmber
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            color = resetBannerBg,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = when {
                                            quotaConfirmed -> "Lista confirmada para ${activeQuota.title}"
                                            needsQuotaReset -> "Nueva cuota: ${activeQuota.title}"
                                            else -> "Sin usuario registrado en esta sucursal"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = resetBannerFg
                                    )
                                    if (needsQuotaReset) {
                                        Text(
                                            text = "Confirma para reiniciar las tareas de esta sucursal.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (needsQuotaReset) {
                                    Button(
                                        onClick = onRequestBranchReset,
                                        enabled = !isBusy,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = BrandRed,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("confirm_branch_reset_${summary.branchName}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.RestartAlt,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Confirmar", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    if (summary.tasks.isEmpty()) {
                        Text(
                            text = "Esta sucursal todavía no tiene tareas asignadas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    } else {
                        val branchDayGroups = remember(summary.tasks) { buildDayTaskGroups(summary.tasks) }
                        branchDayGroups.forEach { dayGroup ->
                            val dayExpanded = expandedDaysInBranch[dayGroup.dateIso] ?: dayGroup.isToday
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedDaysInBranch[dayGroup.dateIso] = !dayExpanded
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = dayGroup.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = BrandNavy
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "${dayGroup.completedCount}/${dayGroup.totalCount}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (dayGroup.allCompleted) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Icon(
                                                imageVector = if (dayExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    AnimatedVisibility(visible = dayExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            dayGroup.tasks.forEach { task ->
                                                BranchTaskStatusRow(
                                                    task = task,
                                                    nowMillis = nowMillis,
                                                    linkedEvidence = evidencesMap[task.id],
                                                    onViewAdminEvidence = onViewAdminEvidence,
                                                    onEditDailyTask = { onEditDailyTask(task) },
                                                    onDeleteDailyTask = { onDeleteDailyTask(task) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchTaskStatusRow(
    task: AssignedTask,
    nowMillis: Long,
    linkedEvidence: TaskEvidence?,
    onViewAdminEvidence: (TaskEvidence) -> Unit,
    onEditDailyTask: () -> Unit,
    onDeleteDailyTask: () -> Unit
) {
    val scheduleStatus = DateAndFormatters.resolveTaskScheduleStatus(task, nowMillis)
    val dotColor = when (scheduleStatus) {
        TaskScheduleStatus.COMPLETED -> SuccessGreen
        TaskScheduleStatus.IN_WINDOW -> SuccessGreen
        TaskScheduleStatus.BEFORE_WINDOW -> BrandNavy
        TaskScheduleStatus.AFTER_WINDOW -> WarningAmber
    }
    val badgeBg = when (scheduleStatus) {
        TaskScheduleStatus.COMPLETED -> SuccessGreenSoft
        TaskScheduleStatus.IN_WINDOW -> SuccessGreenSoft
        TaskScheduleStatus.BEFORE_WINDOW -> MaterialTheme.colorScheme.primaryContainer
        TaskScheduleStatus.AFTER_WINDOW -> WarningAmberSoft
    }
    val statusText = when (scheduleStatus) {
        TaskScheduleStatus.COMPLETED -> "Realizada"
        TaskScheduleStatus.IN_WINDOW -> "En horario"
        TaskScheduleStatus.BEFORE_WINDOW -> DateAndFormatters.formatScheduleStartHint(task)
        TaskScheduleStatus.AFTER_WINDOW -> "Fuera de horario"
    }
    val isDone = task.parsedStatus == TaskStatus.COMPLETED
    val isDaily = task.parsedType == TaskType.DAILY

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isDone) SuccessGreen else BrandRed)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${task.parsedType.labelEs} · ${DateAndFormatters.formatDeadlineDisplay(task.dueDate, task.dueTime, task.deadlineLabel)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(color = badgeBg, shape = RoundedCornerShape(50)) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = dotColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (isDone && linkedEvidence != null) {
                        TextButton(
                            onClick = { onViewAdminEvidence(linkedEvidence) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Visibility,
                                contentDescription = "Ver archivos de ${task.title}",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Archivos (${linkedEvidence.resolvedFileCount})",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            if (isDaily) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onEditDailyTask,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("branch_edit_daily_task_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Editar ${task.title}",
                            tint = BrandNavy,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Editar", style = MaterialTheme.typography.labelSmall, color = BrandNavy)
                    }
                    TextButton(
                        onClick = onDeleteDailyTask,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("branch_delete_daily_task_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Eliminar ${task.title}",
                            tint = BrandRed,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Eliminar", style = MaterialTheme.typography.labelSmall, color = BrandRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTasksState(
    isAdmin: Boolean,
    onCreateTask: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_tasks),
            contentDescription = "Sin tareas pendientes",
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .height(170.dp)
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No hay tareas en esta vista",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isAdmin) {
                "No hay tareas con este filtro. Crea una nueva tarea con el botón + cuando la necesites."
            } else {
                "No hay tareas registradas con este filtro por el momento."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (isAdmin) {
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onCreateTask,
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Crear Tarea Extraordinaria")
            }
        }
    }
}

@Composable
private fun TaskCardItem(
    task: AssignedTask,
    nowMillis: Long,
    isAdmin: Boolean,
    linkedEvidence: TaskEvidence?,
    onDocumentClick: () -> Unit,
    onViewAdminEvidence: (TaskEvidence) -> Unit,
    onEditDailyTask: () -> Unit,
    onDeleteDailyTask: () -> Unit
) {
    val isExtraordinary = task.parsedType == TaskType.EXTRAORDINARY
    val isCompleted = task.parsedStatus == TaskStatus.COMPLETED
    val scheduleStatus = DateAndFormatters.resolveTaskScheduleStatus(task, nowMillis)
    val deadlineDisplay = DateAndFormatters.formatDeadlineDisplay(task.dueDate, task.dueTime, task.deadlineLabel)

    val scheduleBg = when (scheduleStatus) {
        TaskScheduleStatus.COMPLETED -> SuccessGreenSoft
        TaskScheduleStatus.IN_WINDOW -> SuccessGreenSoft
        TaskScheduleStatus.BEFORE_WINDOW -> MaterialTheme.colorScheme.primaryContainer
        TaskScheduleStatus.AFTER_WINDOW -> WarningAmberSoft
    }
    val scheduleFg = when (scheduleStatus) {
        TaskScheduleStatus.COMPLETED -> SuccessGreen
        TaskScheduleStatus.IN_WINDOW -> SuccessGreen
        TaskScheduleStatus.BEFORE_WINDOW -> BrandNavy
        TaskScheduleStatus.AFTER_WINDOW -> WarningAmber
    }
    val scheduleText = when (scheduleStatus) {
        TaskScheduleStatus.COMPLETED -> "Completada"
        TaskScheduleStatus.IN_WINDOW -> "En horario"
        TaskScheduleStatus.BEFORE_WINDOW -> DateAndFormatters.formatScheduleStartHint(task)
        TaskScheduleStatus.AFTER_WINDOW -> "Fuera de horario"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = if (isExtraordinary) BrandRedSoft else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isExtraordinary) {
                                Icon(
                                    imageVector = Icons.Filled.Bolt,
                                    contentDescription = null,
                                    tint = BrandRed,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = task.parsedType.labelEs,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isExtraordinary) BrandRed else BrandNavy
                            )
                        }
                    }

                    if (task.parsedPriority == TaskPriority.URGENT) {
                        Surface(color = WarningAmberSoft, shape = RoundedCornerShape(50)) {
                            Text(
                                text = "Urgente",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarningAmber,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Surface(color = scheduleBg, shape = RoundedCornerShape(50)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isCompleted) Icons.Filled.CheckCircle else Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = scheduleFg,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = scheduleText,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheduleFg
                        )
                    }
                }
            }

            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (task.description.isNotBlank()) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Asignada a: ${task.assigneeName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandNavy
                )
                Text(
                    text = deadlineDisplay,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (scheduleStatus == TaskScheduleStatus.AFTER_WINDOW) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isCompleted) {
                Button(
                    onClick = onDocumentClick,
                    enabled = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("document_task_button_${task.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandNavy,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Subir Evidencia (hasta 5 archivos)")
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_locked_banner_${task.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = BrandNavy,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isAdmin) {
                                    val count = linkedEvidence?.resolvedFileCount ?: 1
                                    "Bloqueada · $count archivo(s) adjunto(s)"
                                } else {
                                    "Completada y bloqueada · No admite más archivos"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isAdmin && linkedEvidence != null) {
                            TextButton(onClick = { onViewAdminEvidence(linkedEvidence) }) {
                                Icon(
                                    imageVector = Icons.Filled.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ver (${linkedEvidence.resolvedFileCount})")
                            }
                        }
                    }
                }
            }

            if (isAdmin && task.parsedType == TaskType.DAILY) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onEditDailyTask,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("edit_daily_task_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Editar tarea diaria",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Editar", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onDeleteDailyTask,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("delete_daily_task_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Eliminar tarea diaria",
                            tint = BrandRed,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Eliminar", style = MaterialTheme.typography.labelSmall, color = BrandRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskDocumentationDialog(
    task: AssignedTask,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmitFiles: (List<EncodedAttachment>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val attachedFiles = remember(task.id) { mutableStateListOf<EncodedAttachment>() }
    var isProcessing by remember { mutableStateOf(false) }
    var attachmentError by remember { mutableStateOf<String?>(null) }

    fun appendUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val remaining = ImageCompressor.MAX_TASK_EVIDENCE_FILES - attachedFiles.size
        if (remaining <= 0) {
            attachmentError = "Máximo 5 archivos por tarea."
            return
        }
        val toProcess = uris.take(remaining)
        val overflow = uris.size > remaining
        isProcessing = true
        attachmentError = null
        scope.launch {
            val errors = mutableListOf<String>()
            toProcess.forEach { uri ->
                val encodedResult = withContext(Dispatchers.IO) {
                    ImageCompressor.encodeUriAttachment(
                        context = context,
                        uri = uri,
                        maxTotalBytes = ImageCompressor.MAX_TASK_EVIDENCE_FILE_BYTES,
                        maxChunks = 4,
                        maxSizeLabel = "1.5 MB"
                    )
                }
                encodedResult.fold(
                    onSuccess = { encoded ->
                        if (attachedFiles.size < ImageCompressor.MAX_TASK_EVIDENCE_FILES) {
                            attachedFiles.add(encoded)
                        }
                    },
                    onFailure = { err ->
                        errors.add(err.localizedMessage ?: "No se pudo procesar un archivo.")
                    }
                )
            }
            isProcessing = false
            attachmentError = when {
                errors.isNotEmpty() -> errors.first()
                overflow -> "Se agregaron los archivos hasta completar el máximo de 5."
                else -> null
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUriString?.let { Uri.parse(it) }
        if (success && uri != null) {
            appendUris(listOf(uri))
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(ImageCompressor.MAX_TASK_EVIDENCE_FILES)
    ) { uris ->
        if (uris.isNotEmpty()) {
            appendUris(uris)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            appendUris(uris)
        }
    }

    val canAddMore = attachedFiles.size < ImageCompressor.MAX_TASK_EVIDENCE_FILES && !isBusy && !isProcessing

    AlertDialog(
        onDismissRequest = { if (!isBusy && !isProcessing) onDismiss() },
        title = { Text("Documentar Tarea") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = BrandNavy
                )
                Surface(
                    color = BrandRedSoft,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Privacidad y cierre definitivo: puedes subir de 1 a 5 archivos (fotos o documentos, máx. 1.5 MB c/u). Al enviarse, la tarea queda bloqueada y solo el administrador verá los archivos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandRed
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val uri = ImageCompressor.createTempCameraUri(context)
                                pendingCameraUriString = uri.toString()
                                takePictureLauncher.launch(uri)
                            }.onFailure {
                                attachmentError = "No se pudo abrir la cámara en este dispositivo."
                            }
                        },
                        enabled = canAddMore,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("take_photo_button")
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cámara", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }.onFailure {
                                attachmentError = "No se pudo abrir la galería en este dispositivo."
                            }
                        },
                        enabled = canAddMore,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_gallery_button")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Galería", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                filePickerLauncher.launch(arrayOf("*/*"))
                            }.onFailure {
                                attachmentError = "No se pudo abrir el selector de archivos."
                            }
                        },
                        enabled = canAddMore,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_file_button")
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Archivo", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Text(
                    text = "Archivos adjuntos (${attachedFiles.size}/${ImageCompressor.MAX_TASK_EVIDENCE_FILES})",
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandNavy
                )

                if (isProcessing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Procesando archivos...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                attachedFiles.forEachIndexed { index, file ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (file.mimeType.startsWith("image/")) {
                                            Icons.Filled.PhotoLibrary
                                        } else {
                                            Icons.Filled.Description
                                        },
                                        contentDescription = null,
                                        tint = BrandNavy,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "${index + 1}. ${file.fileName}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${file.mimeType} · ${ImageCompressor.formatFileSize(file.totalBytes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    enabled = !isBusy && !isProcessing,
                                    onClick = { attachedFiles.removeAt(index) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Quitar ${file.fileName}",
                                        tint = BrandRed
                                    )
                                }
                            }

                            if (file.mimeType.startsWith("image/") && file.singleBase64OrNull != null) {
                                val previewBitmap = remember(file.singleBase64OrNull) {
                                    ImageCompressor.decodeBase64ToImageBitmap(file.singleBase64OrNull ?: "")
                                }
                                if (previewBitmap != null) {
                                    Image(
                                        bitmap = previewBitmap,
                                        contentDescription = "Vista previa ${file.fileName}",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }

                if (attachmentError != null) {
                    Text(
                        text = attachmentError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (attachedFiles.isNotEmpty()) {
                        onSubmitFiles(attachedFiles.toList())
                    }
                },
                enabled = attachedFiles.isNotEmpty() && !isBusy && !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                modifier = Modifier.testTag("submit_photo_evidence_button")
            ) {
                Text(
                    if (isBusy) "Enviando..."
                    else "Enviar (${attachedFiles.size}) y Completar"
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy && !isProcessing
            ) {
                Text("Cancelar")
            }
        }
    )
}

private enum class DeadlinePreset(
    val chipLabel: String,
    val dueTime: String,
    val deadlineLabel: String,
    val daysOffset: Int
) {
    TODAY_CLOSE("Hoy al cierre", "23:59", "Límite: Hoy al cierre", 0),
    TOMORROW_CLOSE("Mañana al cierre", "23:59", "Límite: Día siguiente al cierre", 1),
    TOMORROW_1130("Mañana 11:30 a.m.", "11:30", "Límite: Día siguiente 11:30 a.m.", 1)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayMultiSelector(
    selectedDays: List<String>,
    onDaysChanged: (List<String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Días de la semana activos", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onDaysChanged(WeekdayOption.MON_TO_SAT_CODES) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Lun–Sáb", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(
                    onClick = {
                        if (selectedDays.size == WeekdayOption.entries.size) {
                            onDaysChanged(emptyList())
                        } else {
                            onDaysChanged(WeekdayOption.ALL_CODES)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        if (selectedDays.size == WeekdayOption.entries.size) "Ninguno" else "Todos",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WeekdayOption.entries.forEach { day ->
                val isSelected = day.code in selectedDays
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val updated = if (isSelected) {
                            selectedDays - day.code
                        } else {
                            (selectedDays + day.code).distinct()
                        }
                        onDaysChanged(updated)
                    },
                    label = { Text(day.labelEs, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandNavy,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("weekday_chip_${day.code.lowercase()}")
                )
            }
        }
    }
}

@Composable
private fun TemplateBranchMultiSelector(
    allBranchesSelected: Boolean,
    selectedBranches: List<String>,
    onAllBranchesToggle: (Boolean) -> Unit,
    onSelectedBranchesChange: (List<String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Sucursales asignadas", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = allBranchesSelected,
                onClick = { onAllBranchesToggle(true) },
                label = { Text(BranchOptions.ALL_BRANCHES_OPTION, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BrandNavy,
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.testTag("template_branches_all")
            )
            FilterChip(
                selected = !allBranchesSelected,
                onClick = { onAllBranchesToggle(false) },
                label = { Text("Específicas", style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BrandNavy,
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.testTag("template_branches_specific")
            )
        }
        if (!allBranchesSelected) {
            BranchOptions.ALL_BRANCHES.forEach { branch ->
                val checked = branch in selectedBranches
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val updated = if (checked) selectedBranches - branch else (selectedBranches + branch).distinct()
                            onSelectedBranchesChange(updated)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { isChecked ->
                            val updated = if (isChecked) (selectedBranches + branch).distinct() else selectedBranches - branch
                            onSelectedBranchesChange(updated)
                        }
                    )
                    Text(text = branch, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text(
            text = "Las tareas recurrentes nunca se asignan a cuentas de Administrador.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CreateTaskDialog(
    teamUsers: List<UserProfile>,
    initialTaskType: TaskType = TaskType.EXTRAORDINARY,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onCreateExtraordinary: (List<UserProfile>, String, String, TaskPriority, String, String?, String?) -> Unit,
    onSaveDailyTemplate: (
        title: String,
        description: String,
        priority: TaskPriority,
        daysOfWeek: List<String>,
        targetBranches: List<String>,
        dueTime: String?,
        deadlineLabel: String?,
        dueDayOffset: Long
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var taskType by remember(initialTaskType) { mutableStateOf(initialTaskType) }
    var priority by remember { mutableStateOf(TaskPriority.NORMAL) }
    var deadlinePreset by remember { mutableStateOf(DeadlinePreset.TODAY_CLOSE) }
    var customDueTime by remember { mutableStateOf("23:59") }

    // For Daily recurring templates
    var selectedDays by remember { mutableStateOf(WeekdayOption.ALL_CODES) }
    var allBranchesSelected by remember { mutableStateOf(true) }
    var selectedBranches by remember { mutableStateOf(BranchOptions.ALL_BRANCHES) }

    // For Extraordinary tasks
    val selectedUids = remember { mutableStateListOf<String>() }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Text(if (taskType == TaskType.DAILY) "Nueva Plantilla Diaria" else "Nueva Tarea Extraordinaria")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    label = { Text("Título de la tarea") },
                    singleLine = true,
                    modifier = Modifier.testTag("new_task_title_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(500) },
                    label = { Text("Instrucciones / qué adjuntar") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.testTag("new_task_desc_input")
                )

                Text("Tipo de tarea", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskType.entries.forEach { type ->
                        FilterChip(
                            selected = taskType == type,
                            onClick = {
                                taskType = type
                                if (type == TaskType.EXTRAORDINARY) priority = TaskPriority.URGENT
                                errorMsg = null
                            },
                            label = { Text(type.labelEs) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (type == TaskType.EXTRAORDINARY) BrandRed else BrandNavy,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("create_task_type_${type.name.lowercase()}")
                        )
                    }
                }

                if (taskType == TaskType.EXTRAORDINARY) {
                    Text(
                        text = "Las tareas extraordinarias envían una notificación automática a los usuarios seleccionados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandRed
                    )
                }

                Text("Prioridad", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskPriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.labelEs) }
                        )
                    }
                }

                Text("Horario sugerido / referencia", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DeadlinePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = deadlinePreset == preset,
                            onClick = {
                                deadlinePreset = preset
                                customDueTime = preset.dueTime
                            },
                            label = { Text(preset.chipLabel) }
                        )
                    }
                }

                if (taskType == TaskType.DAILY) {
                    OutlinedTextField(
                        value = customDueTime,
                        onValueChange = { customDueTime = it.take(5) },
                        label = { Text("Hora límite (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    WeekdayMultiSelector(
                        selectedDays = selectedDays,
                        onDaysChanged = { selectedDays = it }
                    )

                    TemplateBranchMultiSelector(
                        allBranchesSelected = allBranchesSelected,
                        selectedBranches = selectedBranches,
                        onAllBranchesToggle = { isAll ->
                            allBranchesSelected = isAll
                            if (isAll) {
                                selectedBranches = BranchOptions.ALL_BRANCHES
                            }
                        },
                        onSelectedBranchesChange = { selectedBranches = it }
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Asignar a sucursales", style = MaterialTheme.typography.labelMedium)
                        TextButton(onClick = {
                            if (selectedUids.size == teamUsers.size) {
                                selectedUids.clear()
                            } else {
                                selectedUids.clear()
                                selectedUids.addAll(teamUsers.map { it.userId })
                            }
                        }) {
                            Text(if (selectedUids.size == teamUsers.size) "Ninguna" else "Todas")
                        }
                    }

                    teamUsers.forEach { user ->
                        val checked = user.userId in selectedUids
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) selectedUids.add(user.userId)
                                    else selectedUids.remove(user.userId)
                                }
                            )
                            Column {
                                Text(
                                    text = user.effectiveBranchLabel,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${user.parsedRole.labelEs} · ${user.email}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (errorMsg != null) {
                    Text(text = errorMsg ?: "", style = MaterialTheme.typography.bodySmall, color = BrandRed)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = {
                    val cleanTitle = title.trim()
                    if (cleanTitle.length < 3) {
                        errorMsg = "Escribe un título de al menos 3 caracteres."
                        return@Button
                    }
                    if (taskType == TaskType.DAILY) {
                        val cleanTime = customDueTime.trim()
                        val branchesPayload = if (allBranchesSelected) {
                            listOf(BranchOptions.ALL_BRANCHES_OPTION)
                        } else {
                            selectedBranches
                        }
                        when {
                            selectedDays.isEmpty() ->
                                errorMsg = "Selecciona al menos un día de la semana."
                            branchesPayload.isEmpty() ->
                                errorMsg = "Selecciona al menos una sucursal."
                            cleanTime.isNotEmpty() && !Regex("""^([01][0-9]|2[0-3]):[0-5][0-9]$""").matches(cleanTime) ->
                                errorMsg = "Usa formato de 24 horas HH:mm (ej. 17:59 o 23:59)."
                            else -> {
                                errorMsg = null
                                onSaveDailyTemplate(
                                    cleanTitle,
                                    description.trim(),
                                    priority,
                                    selectedDays,
                                    branchesPayload,
                                    cleanTime.ifEmpty { null },
                                    deadlinePreset.deadlineLabel,
                                    deadlinePreset.daysOffset.toLong()
                                )
                            }
                        }
                    } else {
                        val chosen = teamUsers.filter { it.userId in selectedUids }
                        if (chosen.isEmpty()) {
                            errorMsg = "Selecciona al menos un usuario o sucursal."
                        } else {
                            errorMsg = null
                            val dueIso = DateAndFormatters.addDaysIso(
                                DateAndFormatters.todayIso(),
                                deadlinePreset.daysOffset
                            )
                            onCreateExtraordinary(
                                chosen,
                                cleanTitle,
                                description.trim(),
                                priority,
                                dueIso,
                                deadlinePreset.dueTime,
                                deadlinePreset.deadlineLabel
                            )
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (taskType == TaskType.EXTRAORDINARY) BrandRed else BrandNavy
                ),
                modifier = Modifier.testTag("confirm_create_task_button")
            ) {
                Text(
                    when {
                        isBusy -> "Guardando..."
                        taskType == TaskType.DAILY -> "Guardar Plantilla"
                        else -> "Asignar Tarea"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun EditDailyTaskDialog(
    task: AssignedTask?,
    template: DailyTaskTemplate?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirmTemplateEdit: (
        existingTemplateId: String?,
        title: String,
        description: String,
        priority: TaskPriority,
        daysOfWeek: List<String>,
        targetBranches: List<String>,
        dueTime: String?,
        deadlineLabel: String?,
        dueDayOffset: Long
    ) -> Unit,
    onConfirmSingleTaskEdit: (
        task: AssignedTask,
        title: String,
        description: String,
        priority: TaskPriority,
        dueTime: String?,
        deadlineLabel: String?
    ) -> Unit
) {
    val dialogKey = template?.id ?: task?.id ?: "new"
    var title by remember(dialogKey) { mutableStateOf(template?.title ?: task?.title ?: "") }
    var description by remember(dialogKey) { mutableStateOf(template?.description ?: task?.description ?: "") }
    var priority by remember(dialogKey) { mutableStateOf(template?.parsedPriority ?: task?.parsedPriority ?: TaskPriority.NORMAL) }
    var dueTime by remember(dialogKey) { mutableStateOf(template?.dueTime ?: task?.dueTime ?: "23:59") }
    var deadlineLabel by remember(dialogKey) { mutableStateOf(template?.deadlineLabel ?: task?.deadlineLabel ?: "") }
    val dueDayOffset = template?.dueDayOffset ?: 0L

    val initialDays = remember(dialogKey) {
        template?.daysOfWeek?.takeIf { it.isNotEmpty() }
            ?: task?.let { listOf(DateAndFormatters.weekdayCodeForIso(DateAndFormatters.extractTaskDayIso(it))) }
            ?: WeekdayOption.ALL_CODES
    }
    var selectedDays by remember(dialogKey) { mutableStateOf(initialDays) }

    val initialAllBranches = remember(dialogKey) {
        template?.includesAllBranches ?: (task == null)
    }
    var allBranchesSelected by remember(dialogKey) { mutableStateOf(initialAllBranches) }
    val initialBranches = remember(dialogKey) {
        when {
            template != null && !template.includesAllBranches -> template.targetBranches
            task != null && task.assigneeName in BranchOptions.ALL_BRANCHES -> listOf(task.assigneeName)
            else -> BranchOptions.ALL_BRANCHES
        }
    }
    var selectedBranches by remember(dialogKey) { mutableStateOf(initialBranches) }
    var editAsDefinitiveTemplate by remember(dialogKey) { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (template != null || editAsDefinitiveTemplate) "Editar Plantilla Diaria" else "Editar Tarea de Hoy")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_daily_title_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(500) },
                    label = { Text("Descripción / instrucciones") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_daily_desc_input")
                )
                OutlinedTextField(
                    value = dueTime,
                    onValueChange = { dueTime = it.take(5) },
                    label = { Text("Hora límite (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = deadlineLabel,
                    onValueChange = { deadlineLabel = it.take(80) },
                    label = { Text("Etiqueta de horario (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Prioridad", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskPriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.labelEs) }
                        )
                    }
                }

                if (task != null) {
                    Text("Modo de edición", style = MaterialTheme.typography.labelMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = editAsDefinitiveTemplate,
                            onClick = { editAsDefinitiveTemplate = true },
                            label = { Text("Plantilla recurrente definitiva (días y sucursales)") },
                            modifier = Modifier.testTag("edit_scope_all_branches")
                        )
                        FilterChip(
                            selected = !editAsDefinitiveTemplate,
                            onClick = { editAsDefinitiveTemplate = false },
                            label = { Text("Solo esta instancia (${task.assigneeName})") },
                            modifier = Modifier.testTag("edit_scope_single_branch")
                        )
                    }
                }

                if (editAsDefinitiveTemplate || task == null) {
                    WeekdayMultiSelector(
                        selectedDays = selectedDays,
                        onDaysChanged = { selectedDays = it }
                    )

                    TemplateBranchMultiSelector(
                        allBranchesSelected = allBranchesSelected,
                        selectedBranches = selectedBranches,
                        onAllBranchesToggle = { isAll ->
                            allBranchesSelected = isAll
                            if (isAll) {
                                selectedBranches = BranchOptions.ALL_BRANCHES
                            }
                        },
                        onSelectedBranchesChange = { selectedBranches = it }
                    )
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = {
                    val cleanTitle = title.trim()
                    val cleanTime = dueTime.trim()
                    val branchesPayload = if (allBranchesSelected) {
                        listOf(BranchOptions.ALL_BRANCHES_OPTION)
                    } else {
                        selectedBranches
                    }
                    when {
                        cleanTitle.length < 3 ->
                            errorMsg = "El título debe tener al menos 3 caracteres."
                        cleanTime.isNotEmpty() && !Regex("""^([01][0-9]|2[0-3]):[0-5][0-9]$""").matches(cleanTime) ->
                            errorMsg = "Usa formato de 24 horas HH:mm (ej. 17:59 o 23:59)."
                        (editAsDefinitiveTemplate || task == null) && selectedDays.isEmpty() ->
                            errorMsg = "Selecciona al menos un día de la semana."
                        (editAsDefinitiveTemplate || task == null) && branchesPayload.isEmpty() ->
                            errorMsg = "Selecciona al menos una sucursal."
                        else -> {
                            errorMsg = null
                            if (editAsDefinitiveTemplate || task == null) {
                                onConfirmTemplateEdit(
                                    template?.id ?: task?.templateId,
                                    cleanTitle,
                                    description.trim(),
                                    priority,
                                    selectedDays,
                                    branchesPayload,
                                    cleanTime.ifEmpty { null },
                                    deadlineLabel.trim().ifEmpty { null },
                                    dueDayOffset
                                )
                            } else {
                                onConfirmSingleTaskEdit(
                                    task,
                                    cleanTitle,
                                    description.trim(),
                                    priority,
                                    cleanTime.ifEmpty { null },
                                    deadlineLabel.trim().ifEmpty { null }
                                )
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                modifier = Modifier.testTag("confirm_edit_daily_task_button")
            ) {
                Text(if (isBusy) "Guardando..." else "Guardar cambios")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun DeleteDailyTaskDialog(
    task: AssignedTask?,
    template: DailyTaskTemplate?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirmDelete: (removeBranchOnly: Boolean) -> Unit
) {
    var removeBranchOnly by remember(task?.id, template?.id) { mutableStateOf(false) }
    val targetTitle = template?.title ?: task?.title ?: "Tarea diaria"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminar Tarea Diaria") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "¿Deseas eliminar \"$targetTitle\"?",
                    style = MaterialTheme.typography.titleSmall,
                    color = BrandNavy
                )
                Text(
                    text = if (task != null) {
                        "Puedes eliminar la plantilla recurrente de forma definitiva (desactiva la plantilla y borra las instancias de hoy para que no vuelva a generarse) o quitarla únicamente de ${task.assigneeName}."
                    } else {
                        "Se desactivará su plantilla recurrente en Firestore y se eliminarán las instancias de hoy para que jamás vuelva a generarse en los días siguientes."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (task != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !removeBranchOnly,
                            onClick = { removeBranchOnly = false },
                            label = { Text("Eliminar definitivamente (plantilla + hoy)") },
                            modifier = Modifier.testTag("delete_scope_all_branches")
                        )
                        FilterChip(
                            selected = removeBranchOnly,
                            onClick = { removeBranchOnly = true },
                            label = { Text("Quitar solo de ${task.assigneeName}") },
                            modifier = Modifier.testTag("delete_scope_single_branch")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = { onConfirmDelete(removeBranchOnly) },
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                modifier = Modifier.testTag("confirm_delete_daily_task_button")
            ) {
                Text(if (isBusy) "Eliminando..." else "Eliminar definitivamente")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Cancelar")
            }
        }
    )
}
