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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
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
import com.example.data.model.BranchTaskSummary
import com.example.data.model.TaskEvidence
import com.example.data.model.TaskPriority
import com.example.data.model.TaskStatus
import com.example.data.model.TaskType
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.buildBranchTaskSummaries
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandRedSoft
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenSoft
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberSoft
import com.example.utils.DateAndFormatters
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
    DETAILED_LIST("Lista Detallada")
}

@Composable
fun TasksChecklistScreen(
    isAdmin: Boolean,
    isBusy: Boolean,
    tasksState: UiState<List<AssignedTask>>,
    pendingTaskIds: Set<String> = emptySet(),
    evidencesState: UiState<List<TaskEvidence>>,
    teamUsers: List<UserProfile>,
    currentUserProfile: UserProfile?,
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
    onCompleteTaskWithEvidence: (
        task: AssignedTask,
        imageBase64: String,
        onSuccess: () -> Unit
    ) -> Unit
) {
    var adminViewMode by remember { mutableStateOf(AdminTaskViewMode.BY_BRANCH) }
    var selectedFilter by remember { mutableStateOf(TaskFilter.ALL) }
    var taskToDocument by remember { mutableStateOf<AssignedTask?>(null) }
    var showCreateTaskDialog by remember { mutableStateOf(false) }
    var evidencePreviewForAdmin by remember { mutableStateOf<TaskEvidence?>(null) }

    val evidencesMap = remember(evidencesState, isAdmin) {
        if (!isAdmin) emptyMap()
        else (evidencesState as? UiState.Success)?.data?.associateBy { it.taskId } ?: emptyMap()
    }

    Scaffold(
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = { showCreateTaskDialog = true },
                    containerColor = BrandRed,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("create_task_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Crear nueva tarea extraordinaria")
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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AdminTaskViewMode.entries.forEach { mode ->
                        FilterChip(
                            selected = adminViewMode == mode,
                            onClick = { adminViewMode = mode },
                            label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandNavy,
                                selectedLabelColor = Color.White
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
                                selectedContainerColor = BrandNavy,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

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
                            pendingTaskIds = pendingTaskIds,
                            evidencesMap = evidencesMap,
                            onViewAdminEvidence = { ev -> evidencePreviewForAdmin = ev }
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
                            EmptyTasksState(isAdmin = isAdmin, onCreateTask = { showCreateTaskDialog = true })
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filtered, key = { it.id }) { task ->
                                    TaskCardItem(
                                        task = task,
                                        isAdmin = isAdmin,
                                        isPendingSync = task.id in pendingTaskIds,
                                        linkedEvidence = if (isAdmin) evidencesMap[task.id] else null,
                                        onDocumentClick = { taskToDocument = task },
                                        onViewAdminEvidence = { ev -> evidencePreviewForAdmin = ev }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    taskToDocument?.let { task ->
        PhotoDocumentationDialog(
            task = task,
            isBusy = isBusy,
            onDismiss = { taskToDocument = null },
            onSubmitBase64 = { base64 ->
                onCompleteTaskWithEvidence(task, base64) {
                    taskToDocument = null
                }
            }
        )
    }

    if (showCreateTaskDialog && isAdmin) {
        val availableUsers = remember(teamUsers, currentUserProfile) {
            if (teamUsers.isNotEmpty()) teamUsers
            else listOfNotNull(currentUserProfile)
        }
        CreateTaskDialog(
            teamUsers = availableUsers,
            isBusy = isBusy,
            onDismiss = { showCreateTaskDialog = false },
            onCreate = { assignees, title, desc, type, priority, dueDate, dueTime, deadlineLabel ->
                onCreateTask(assignees, title, desc, type, priority, dueDate, dueTime, deadlineLabel) {
                    showCreateTaskDialog = false
                }
            }
        )
    }

    evidencePreviewForAdmin?.let { evidence ->
        if (isAdmin) {
            EvidencePreviewDialog(
                evidence = evidence,
                onDismiss = { evidencePreviewForAdmin = null }
            )
        }
    }
}

@Composable
private fun AdminBranchesChecklistView(
    summaries: List<BranchTaskSummary>,
    pendingTaskIds: Set<String>,
    evidencesMap: Map<String, TaskEvidence>,
    onViewAdminEvidence: (TaskEvidence) -> Unit
) {
    val expandedState = remember { mutableStateMapOf<String, Boolean>() }
    val completedBranches = summaries.count { it.allTasksCompleted }

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
                            text = "Toca una sucursal para desplegar el detalle de sus tareas",
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
            BranchChecklistCard(
                summary = summary,
                isExpanded = isExpanded,
                pendingTaskIds = pendingTaskIds,
                evidencesMap = evidencesMap,
                onToggleExpand = {
                    expandedState[summary.branchName] = !isExpanded
                },
                onViewAdminEvidence = onViewAdminEvidence
            )
        }
    }
}

@Composable
private fun BranchChecklistCard(
    summary: BranchTaskSummary,
    isExpanded: Boolean,
    pendingTaskIds: Set<String>,
    evidencesMap: Map<String, TaskEvidence>,
    onToggleExpand: () -> Unit,
    onViewAdminEvidence: (TaskEvidence) -> Unit
) {
    val indicatorColor = if (summary.allTasksCompleted) SuccessGreen else BrandRed
    val indicatorBg = if (summary.allTasksCompleted) SuccessGreenSoft else BrandRedSoft
    val statusText = when {
        summary.totalCount == 0 -> "Sin tareas"
        summary.allTasksCompleted -> "Todas cumplidas"
        else -> "Pendientes (${summary.totalCount - summary.completedCount})"
    }

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

                    if (summary.tasks.isEmpty()) {
                        Text(
                            text = "Esta sucursal todavía no tiene tareas asignadas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    } else {
                        summary.tasks.forEach { task ->
                            BranchTaskStatusRow(
                                task = task,
                                isPendingSync = task.id in pendingTaskIds,
                                linkedEvidence = evidencesMap[task.id],
                                onViewAdminEvidence = onViewAdminEvidence
                            )
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
    isPendingSync: Boolean,
    linkedEvidence: TaskEvidence?,
    onViewAdminEvidence: (TaskEvidence) -> Unit
) {
    val isDone = task.parsedStatus == TaskStatus.COMPLETED
    val isPastDeadline = !isDone && DateAndFormatters.isTaskPastDeadline(task.dueDate, task.dueTime)
    val dotColor = if (isDone) SuccessGreen else BrandRed
    val badgeBg = if (isDone) SuccessGreenSoft else BrandRedSoft
    val statusText = when {
        isDone && isPendingSync -> "Realizada (Local)"
        isDone -> "Realizada"
        isPastDeadline -> "Vencida"
        else -> "No realizada"
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(dotColor)
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
                    color = if (isPastDeadline) BrandRed else MaterialTheme.colorScheme.onSurfaceVariant
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
                            contentDescription = "Ver foto de ${task.title}",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Foto", style = MaterialTheme.typography.labelSmall)
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
                "Crea una tarea diaria o extraordinaria con el botón + para asignarla y notificar a las sucursales."
            } else {
                "Cuando el administrador te asigne tareas diarias o extraordinarias aparecerán aquí."
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
    isAdmin: Boolean,
    isPendingSync: Boolean,
    linkedEvidence: TaskEvidence?,
    onDocumentClick: () -> Unit,
    onViewAdminEvidence: (TaskEvidence) -> Unit
) {
    val isExtraordinary = task.parsedType == TaskType.EXTRAORDINARY
    val isCompleted = task.parsedStatus == TaskStatus.COMPLETED
    val isPastDeadline = !isCompleted && DateAndFormatters.isTaskPastDeadline(task.dueDate, task.dueTime)
    val deadlineDisplay = DateAndFormatters.formatDeadlineDisplay(task.dueDate, task.dueTime, task.deadlineLabel)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = if (isExtraordinary) BrandRedSoft else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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

                val statusBg = when {
                    isCompleted -> SuccessGreenSoft
                    isPastDeadline -> BrandRedSoft
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val statusFg = when {
                    isCompleted -> SuccessGreen
                    isPastDeadline -> BrandRed
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val statusText = when {
                    isCompleted -> task.parsedStatus.labelEs
                    isPastDeadline -> "Plazo vencido"
                    else -> task.parsedStatus.labelEs
                }

                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isCompleted) Icons.Filled.CheckCircle else Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = statusFg,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusFg
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
                    color = if (isPastDeadline) BrandRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isPendingSync) {
                Surface(
                    color = WarningAmberSoft,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("task_pending_sync_badge_${task.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudUpload,
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Guardado localmente · Pendiente de sincronizar",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmber
                        )
                    }
                }
            }

            if (!isCompleted) {
                Button(
                    onClick = onDocumentClick,
                    enabled = !isPastDeadline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("document_task_button_${task.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandNavy,
                        contentColor = Color.White,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = BrandRed
                    )
                ) {
                    Icon(
                        imageVector = if (isPastDeadline) Icons.Filled.Lock else Icons.Filled.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isPastDeadline) "Plazo vencido · No editable"
                        else "Documentar con Foto y Completar"
                    )
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
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
                                text = if (isAdmin) "Evidencia fotográfica disponible"
                                else "Evidencia confidencial (Solo Administrador)",
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
                                Text("Ver Foto")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoDocumentationDialog(
    task: AssignedTask,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSubmitBase64: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var compressedBase64 by remember { mutableStateOf<String?>(null) }
    var isCompressing by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }

    fun handleUri(uri: Uri) {
        isCompressing = true
        photoError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ImageCompressor.compressUriToBase64(context, uri)
            }
            isCompressing = false
            result.fold(
                onSuccess = { encoded -> compressedBase64 = encoded },
                onFailure = { err -> photoError = err.localizedMessage ?: "No se pudo procesar la foto." }
            )
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            handleUri(uri)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            handleUri(uri)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy && !isCompressing) onDismiss() },
        title = { Text("Documentar Tarea") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            text = "Privacidad: al enviar, la fotografía solo podrá visualizarse por los administradores del sistema.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandRed
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val uri = ImageCompressor.createTempCameraUri(context)
                            pendingCameraUri = uri
                            takePictureLauncher.launch(uri)
                        },
                        enabled = !isBusy && !isCompressing,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("take_photo_button")
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cámara")
                    }

                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isBusy && !isCompressing,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_gallery_button")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Galería")
                    }
                }

                if (isCompressing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Optimizando fotografía...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                compressedBase64?.let { encoded ->
                    val previewBitmap = remember(encoded) {
                        ImageCompressor.decodeBase64ToImageBitmap(encoded)
                    }
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap,
                            contentDescription = "Vista previa antes de enviar",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                if (photoError != null) {
                    Text(
                        text = photoError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { compressedBase64?.let(onSubmitBase64) },
                enabled = compressedBase64 != null && !isBusy && !isCompressing,
                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                modifier = Modifier.testTag("submit_photo_evidence_button")
            ) {
                Text(if (isBusy) "Enviando..." else "Enviar y Completar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy && !isCompressing
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

@Composable
private fun CreateTaskDialog(
    teamUsers: List<UserProfile>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (List<UserProfile>, String, String, TaskType, TaskPriority, String, String?, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var taskType by remember { mutableStateOf(TaskType.EXTRAORDINARY) }
    var priority by remember { mutableStateOf(TaskPriority.URGENT) }
    var deadlinePreset by remember { mutableStateOf(DeadlinePreset.TODAY_CLOSE) }
    val selectedUids = remember { mutableStateListOf<String>() }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Nueva Tarea Asignada") },
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
                    label = { Text("Instrucciones / qué fotografiar") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.testTag("new_task_desc_input")
                )

                Text("Tipo de tarea", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskType.entries.forEach { type ->
                        FilterChip(
                            selected = taskType == type,
                            onClick = { taskType = type },
                            label = { Text(type.labelEs) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (type == TaskType.EXTRAORDINARY) BrandRed else BrandNavy,
                                selectedLabelColor = Color.White
                            )
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

                Text("Límite para completarse", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DeadlinePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = deadlinePreset == preset,
                            onClick = { deadlinePreset = preset },
                            label = { Text(preset.chipLabel) }
                        )
                    }
                }

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

                if (errorMsg != null) {
                    Text(text = errorMsg ?: "", style = MaterialTheme.typography.bodySmall, color = BrandRed)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = {
                    val chosen = teamUsers.filter { it.userId in selectedUids }
                    when {
                        title.trim().length < 3 -> errorMsg = "Escribe un título de al menos 3 caracteres."
                        chosen.isEmpty() -> errorMsg = "Selecciona al menos un usuario o sucursal."
                        else -> {
                            val dueIso = DateAndFormatters.addDaysIso(
                                DateAndFormatters.todayIso(),
                                deadlinePreset.daysOffset
                            )
                            onCreate(
                                chosen,
                                title.trim(),
                                description.trim(),
                                taskType,
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
                Text(if (isBusy) "Creando..." else "Asignar Tarea")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Cancelar")
            }
        }
    )
}
