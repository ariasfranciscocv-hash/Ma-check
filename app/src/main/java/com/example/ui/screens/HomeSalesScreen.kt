package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActiveBiweeklyCycleInfo
import com.example.data.model.AdminWeightedProjection
import com.example.data.model.BiweeklyQuota
import com.example.data.model.BranchOptions
import com.example.data.model.BranchSalesSummary
import com.example.data.model.ChartSeries
import com.example.data.model.ClosingAttachmentDraft
import com.example.data.model.DailySalesEntry
import com.example.data.model.EmployeeIdentity
import com.example.data.model.MetricProgress
import com.example.data.model.ProjectionPace
import com.example.data.model.SalesMetricType
import com.example.data.model.SalesProjectionSummary
import com.example.data.model.UiState
import com.example.data.repository.ProjectionCalculator
import com.example.ui.components.AdminCycleClosureControlCard
import com.example.ui.components.AssignBranchQuotaDialog
import com.example.ui.components.BranchSalesDetailDialog
import com.example.ui.components.BranchSalesTrackingSection
import com.example.ui.components.MueblesAmericaLogo
import com.example.ui.components.ProjectionLineChart
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandRedSoft
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenSoft
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberSoft
import com.example.utils.DateAndFormatters
import com.example.utils.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HomeSalesScreen(
    branchLabel: String,
    isAdmin: Boolean,
    isBusy: Boolean,
    employeeIdentity: EmployeeIdentity? = null,
    projectionState: UiState<SalesProjectionSummary?>,
    adminGeneralSummary: SalesProjectionSummary? = null,
    selectedMetric: SalesMetricType,
    chartSeries: ChartSeries?,
    showDailyBriefing: Boolean,
    closingAttachmentDraft: ClosingAttachmentDraft,
    selectedBranchFilter: String = branchLabel,
    activeQuotas: List<BiweeklyQuota> = emptyList(),
    branchSalesSummaries: List<BranchSalesSummary> = emptyList(),
    activeCycleInfo: ActiveBiweeklyCycleInfo = ProjectionCalculator.resolveActiveCycleInfo(activeQuotas),
    onSelectBranchFilter: ((String) -> Unit)? = null,
    onOpenQuotasTab: (() -> Unit)? = null,
    onCloseAndResetBiweeklyCycle: (((() -> Unit)) -> Unit)? = null,
    onUpdateAttachmentDraft: (ClosingAttachmentDraft) -> Unit,
    onLoadEntryAttachmentChunks: suspend (DailySalesEntry) -> Result<List<String>>,
    onDismissBriefing: () -> Unit,
    onSelectMetric: (SalesMetricType) -> Unit,
    onUpdateGeneralIndicatorTarget: (
        metric: SalesMetricType,
        newTargetValue: Double,
        onDone: () -> Unit
    ) -> Unit = { _, _, onDone -> onDone() },
    onSaveDailyClosing: (
        quotaId: String,
        globalSales: Double,
        creditSales: Double,
        newCustomers: Long,
        appMaOperations: Long,
        newCustomerRequests: Long,
        notes: String,
        attachmentDraft: ClosingAttachmentDraft
    ) -> Unit,
    onSaveBiweeklyQuota: (
        title: String,
        startDate: String,
        endDate: String,
        totalDays: Long,
        targetGlobalSales: Double,
        targetCreditSales: Double,
        targetNewCustomers: Long,
        targetAppMaOperations: Long,
        targetNewCustomerRequests: Long,
        branchName: String,
        applyToAllIndividualBranches: Boolean,
        onDone: () -> Unit
    ) -> Unit
) {
    var quotaTargetBranch by remember { mutableStateOf<String?>(null) }
    var selectedBranchDetailName by remember { mutableStateOf<String?>(null) }

    when (projectionState) {
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "No se pudo cargar el avance de ventas",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = projectionState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        is UiState.Success -> {
            val summary = projectionState.data

            if (showDailyBriefing && summary != null) {
                DailyBriefingDialog(
                    branchLabel = branchLabel,
                    isAdmin = isAdmin,
                    summary = summary,
                    onDismiss = onDismissBriefing
                )
            }

            val activeBranchDetail = selectedBranchDetailName?.let { name ->
                branchSalesSummaries.firstOrNull { it.branchName == name }
            }
            if (activeBranchDetail != null) {
                BranchSalesDetailDialog(
                    summary = activeBranchDetail,
                    isAdmin = isAdmin,
                    onDismiss = { selectedBranchDetailName = null },
                    onEditQuota = if (isAdmin) { branchName ->
                        selectedBranchDetailName = null
                        if (onOpenQuotasTab != null) {
                            onOpenQuotasTab()
                        } else {
                            quotaTargetBranch = branchName
                        }
                    } else null,
                    onLoadEntryAttachmentChunks = onLoadEntryAttachmentChunks
                )
            }

            val targetBranchForQuota = quotaTargetBranch
            if (targetBranchForQuota != null) {
                AssignBranchQuotaDialog(
                    initialBranch = targetBranchForQuota,
                    activeQuotas = activeQuotas,
                    isBusy = isBusy,
                    onDismiss = { quotaTargetBranch = null },
                    onSave = { title, start, end, days, g, c, nc, ma, req, targetBranch, applyAll ->
                        onSaveBiweeklyQuota(
                            title,
                            start,
                            end,
                            days,
                            g,
                            c,
                            nc,
                            ma,
                            req,
                            targetBranch,
                            applyAll
                        ) {
                            quotaTargetBranch = null
                        }
                    }
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isAdmin && onSelectBranchFilter != null) {
                    item {
                        AdminBranchFilterStrip(
                            selectedBranch = selectedBranchFilter.ifBlank { BranchOptions.ALL_BRANCHES_OPTION },
                            onSelectBranch = onSelectBranchFilter
                        )
                    }
                }
                if (isAdmin && onCloseAndResetBiweeklyCycle != null) {
                    item {
                        AdminCycleClosureControlCard(
                            cycleInfo = activeCycleInfo,
                            adminGeneralSummary = adminGeneralSummary ?: summary,
                            branchSummaries = branchSalesSummaries,
                            isBusy = isBusy,
                            onConfirmCloseCycle = onCloseAndResetBiweeklyCycle
                        )
                    }
                }
                if (summary == null) {
                    item {
                        NoActiveQuotaCard(
                            isAdmin = isAdmin,
                            onCreateQuota = {
                                if (onOpenQuotasTab != null) {
                                    onOpenQuotasTab()
                                } else {
                                    quotaTargetBranch = selectedBranchFilter.ifBlank { BranchOptions.ALL_BRANCHES_OPTION }
                                }
                            }
                        )
                    }
                    if (branchSalesSummaries.isNotEmpty()) {
                        item {
                            BranchSalesTrackingSection(
                                summaries = branchSalesSummaries,
                                isAdmin = isAdmin,
                                onOpenBranchDetail = { detail -> selectedBranchDetailName = detail.branchName },
                                onConfigureBranchQuota = if (isAdmin) { branchName ->
                                    if (onOpenQuotasTab != null) {
                                        onOpenQuotasTab()
                                    } else {
                                        quotaTargetBranch = branchName
                                    }
                                } else null
                            )
                        }
                    }
                } else {
                    if (!isAdmin) {
                        item {
                            DailyClosingFormCard(
                                summary = summary,
                                branchLabel = branchLabel,
                                employeeIdentity = employeeIdentity,
                                isBusy = isBusy,
                                closingAttachmentDraft = closingAttachmentDraft,
                                onUpdateAttachmentDraft = onUpdateAttachmentDraft,
                                onSave = { g, c, nc, ma, req, notes, draft ->
                                    onSaveDailyClosing(
                                        summary.quota.id,
                                        g,
                                        c,
                                        nc,
                                        ma,
                                        req,
                                        notes,
                                        draft
                                    )
                                }
                            )
                        }
                    }

                    item {
                        ProjectionOverviewCard(
                            summary = summary,
                            selectedMetric = selectedMetric,
                            chartSeries = chartSeries,
                            isAdmin = isAdmin,
                            onSelectMetric = onSelectMetric,
                            onEditQuota = {
                                if (onOpenQuotasTab != null) {
                                    onOpenQuotasTab()
                                } else {
                                    quotaTargetBranch = selectedBranchFilter.ifBlank { BranchOptions.ALL_BRANCHES_OPTION }
                                }
                            }
                        )
                    }

                    if (branchSalesSummaries.isNotEmpty()) {
                        item {
                            BranchSalesTrackingSection(
                                summaries = branchSalesSummaries,
                                isAdmin = isAdmin,
                                onOpenBranchDetail = { detail -> selectedBranchDetailName = detail.branchName },
                                onConfigureBranchQuota = if (isAdmin) { branchName ->
                                    if (onOpenQuotasTab != null) {
                                        onOpenQuotasTab()
                                    } else {
                                        quotaTargetBranch = branchName
                                    }
                                } else null
                            )
                        }
                    }
                }

                val indicatorsSummary = if (isAdmin) (adminGeneralSummary ?: summary) else summary
                if (indicatorsSummary != null) {
                    item {
                        Column(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Indicadores de la Quincena",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (isAdmin) {
                                Text(
                                    text = "Cuota general independiente · Real = suma de todas las sucursales",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(indicatorsSummary.metrics.size) { index ->
                        val item = indicatorsSummary.metrics[index]
                        MetricScorecardItem(
                            progress = item,
                            isSelected = item.metric == selectedMetric,
                            canEditMeta = isAdmin,
                            isBusy = isBusy,
                            onClick = { onSelectMetric(item.metric) },
                            onSaveTarget = { newTarget, onDone ->
                                onUpdateGeneralIndicatorTarget(item.metric, newTarget, onDone)
                            }
                        )
                    }
                }

                if (summary != null) {
                    val entriesWithDetails = summary.recentEntries.filter {
                        it.notes.isNotBlank() ||
                            (!it.attachmentName.isNullOrBlank() && (!it.attachmentBase64.isNullOrBlank() || it.attachmentChunkCount > 0L))
                    }
                    if (entriesWithDetails.isNotEmpty()) {
                        item {
                            Text(
                                text = "Comentarios y Adjuntos de Cierre",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        items(entriesWithDetails.size) { index ->
                            RecentClosingAttachmentCard(
                                entry = entriesWithDetails[index],
                                onLoadEntryAttachmentChunks = onLoadEntryAttachmentChunks
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyBriefingDialog(
    branchLabel: String,
    isAdmin: Boolean,
    summary: SalesProjectionSummary,
    onDismiss: () -> Unit
) {
    val globalMetric = summary.metrics.firstOrNull { it.metric == SalesMetricType.GLOBAL_SALES }
    val creditMetric = summary.metrics.firstOrNull { it.metric == SalesMetricType.CREDIT_SALES }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                MueblesAmericaLogo(globeSize = 30.dp, fontSize = 18.sp, showTrademark = false)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Avance Diario · $branchLabel",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${summary.quota.title} (Día ${summary.daysElapsed} de ${summary.totalDays})",
                    style = MaterialTheme.typography.labelLarge,
                    color = BrandNavy
                )
                if (globalMetric != null) {
                    BriefingMetricLine(
                        label = "Venta global acumulada",
                        value = DateAndFormatters.formatMetricValue(globalMetric.metric, globalMetric.actualTotal),
                        projected = "Proyección: ${DateAndFormatters.formatMetricValue(globalMetric.metric, globalMetric.projectedTotal)}"
                    )
                }
                if (creditMetric != null) {
                    BriefingMetricLine(
                        label = "Venta a crédito",
                        value = DateAndFormatters.formatMetricValue(creditMetric.metric, creditMetric.actualTotal),
                        projected = "Meta: ${DateAndFormatters.formatMetricValue(creditMetric.metric, creditMetric.targetTotal)}"
                    )
                }
                Text(
                    text = if (isAdmin) {
                        "Supervisión general de sucursales en tiempo real."
                    } else {
                        "Recuerda capturar tus 5 métricas al cierre de operaciones de hoy."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                modifier = Modifier.testTag("dismiss_briefing_button")
            ) {
                Text(if (isAdmin) "Ver Panel de Supervisión" else "Registrar Cierre / Ver Panel")
            }
        }
    )
}

@Composable
private fun BriefingMetricLine(label: String, value: String, projected: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontFamily = MonoFontFamily)
            Text(text = projected, style = MaterialTheme.typography.bodySmall, color = BrandNavy)
        }
    }
}

@Composable
private fun NoActiveQuotaCard(
    isAdmin: Boolean,
    onCreateQuota: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Sin meta quincenal activa",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = if (isAdmin) {
                    "Configura la meta quincenal de este periodo o sucursal para habilitar las proyecciones."
                } else {
                    "El administrador aún no ha publicado la meta quincenal de este periodo."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isAdmin) {
                Button(
                    onClick = onCreateQuota,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                    modifier = Modifier.testTag("create_initial_quota_button")
                ) {
                    Text("Configurar Meta Quincenal")
                }
            }
        }
    }
}

@Composable
private fun DailyClosingFormCard(
    summary: SalesProjectionSummary,
    branchLabel: String,
    employeeIdentity: EmployeeIdentity? = null,
    isBusy: Boolean,
    closingAttachmentDraft: ClosingAttachmentDraft,
    onUpdateAttachmentDraft: (ClosingAttachmentDraft) -> Unit,
    onSave: (Double, Double, Long, Long, Long, String, ClosingAttachmentDraft) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val todayIso = DateAndFormatters.todayIso()
    val existingToday = summary.todayEntry
    var globalSalesText by rememberSaveable(summary.quota.id, todayIso, existingToday?.id) {
        mutableStateOf(existingToday?.globalSales?.let { if (it > 0) it.toLong().toString() else "" } ?: "")
    }
    var creditSalesText by rememberSaveable(summary.quota.id, todayIso, existingToday?.id) {
        mutableStateOf(existingToday?.creditSales?.let { if (it > 0) it.toLong().toString() else "" } ?: "")
    }
    var newCustomersText by rememberSaveable(summary.quota.id, todayIso, existingToday?.id) {
        mutableStateOf(existingToday?.newCustomers?.let { if (it > 0) it.toString() else "" } ?: "")
    }
    var appMaOpsText by rememberSaveable(summary.quota.id, todayIso, existingToday?.id) {
        mutableStateOf(existingToday?.appMaOperations?.let { if (it > 0) it.toString() else "" } ?: "")
    }
    var newRequestsText by rememberSaveable(summary.quota.id, todayIso, existingToday?.id) {
        mutableStateOf(existingToday?.newCustomerRequests?.let { if (it > 0) it.toString() else "" } ?: "")
    }
    var notesText by rememberSaveable(summary.quota.id, todayIso, existingToday?.id) {
        mutableStateOf(existingToday?.notes ?: "")
    }
    var pendingCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var isProcessingAttachment by remember { mutableStateOf(false) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun handlePickedUri(uri: Uri) {
        isProcessingAttachment = true
        attachmentError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ImageCompressor.encodeUriAttachment(context, uri)
            }
            isProcessingAttachment = false
            result.fold(
                onSuccess = { encoded ->
                    onUpdateAttachmentDraft(ClosingAttachmentDraft.Selected(encoded))
                },
                onFailure = { err ->
                    attachmentError = err.localizedMessage ?: "No se pudo adjuntar el archivo."
                }
            )
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUriString?.let { Uri.parse(it) }
        if (success && uri != null) {
            handlePickedUri(uri)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            handlePickedUri(uri)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            handlePickedUri(uri)
        }
    }

    LaunchedEffect(todayIso, existingToday?.id, existingToday?.updatedAt) {
        if (existingToday != null) {
            globalSalesText = existingToday.globalSales.toLong().toString()
            creditSalesText = existingToday.creditSales.toLong().toString()
            newCustomersText = existingToday.newCustomers.toString()
            appMaOpsText = existingToday.appMaOperations.toString()
            newRequestsText = existingToday.newCustomerRequests.toString()
            notesText = existingToday.notes
        } else {
            globalSalesText = ""
            creditSalesText = ""
            newCustomersText = ""
            appMaOpsText = ""
            newRequestsText = ""
            notesText = ""
            attachmentError = null
            validationError = null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cierre de Operaciones · Hoy",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$branchLabel · ${DateAndFormatters.formatIsoToDisplay(DateAndFormatters.todayIso())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (employeeIdentity != null && employeeIdentity.hasIdentityCore) {
                        Text(
                            text = employeeIdentity.formattedMovementAuthor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandNavy
                        )
                    }
                }
                if (existingToday != null) {
                    Surface(
                        color = SuccessGreenSoft,
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Registrado",
                                style = MaterialTheme.typography.labelSmall,
                                color = SuccessGreen
                            )
                        }
                    }
                } else {
                    Surface(
                        color = WarningAmberSoft,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "Pendiente hoy",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarningAmber,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = globalSalesText,
                    onValueChange = { globalSalesText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Venta global ($)") },
                    leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_global_sales")
                )
                OutlinedTextField(
                    value = creditSalesText,
                    onValueChange = { creditSalesText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Venta crédito ($)") },
                    leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_credit_sales")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = newCustomersText,
                    onValueChange = { newCustomersText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Clientes nuevos (uds)") },
                    leadingIcon = { Icon(Icons.Filled.GroupAdd, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_new_customers")
                )
                OutlinedTextField(
                    value = appMaOpsText,
                    onValueChange = { appMaOpsText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Ops App MA (uds)") },
                    leadingIcon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("input_app_ma_ops")
                )
            }

            OutlinedTextField(
                value = newRequestsText,
                onValueChange = { newRequestsText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Solicitudes de cliente nuevo (unidades)") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_new_customer_requests")
            )

            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it.take(300) },
                label = { Text("Comentario de cierre de jornada (opcional)") },
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_closing_notes")
            )

            Text(
                text = "Adjuntar foto o archivo del teléfono (opcional, hasta 3.5 MB)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        runCatching {
                            val uri = ImageCompressor.createTempCameraUri(context)
                            pendingCameraUriString = uri.toString()
                            cameraLauncher.launch(uri)
                        }.onFailure {
                            attachmentError = "No se pudo abrir la cámara en este dispositivo."
                        }
                    },
                    enabled = !isBusy && !isProcessingAttachment,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("closing_attach_camera_button")
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Foto", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        runCatching {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }.onFailure {
                            attachmentError = "No se pudo abrir la galería de imágenes."
                        }
                    },
                    enabled = !isBusy && !isProcessingAttachment,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("closing_attach_gallery_button")
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
                    enabled = !isBusy && !isProcessingAttachment,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("closing_attach_file_button")
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Archivo", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (isProcessingAttachment) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Procesando archivo...", style = MaterialTheme.typography.bodySmall)
                }
            }

            val displayedAttachmentName: String?
            val displayedAttachmentSubtitle: String?
            val displayedInlinePreviewBase64: String?
            val isUnsavedDraft: Boolean

            when (closingAttachmentDraft) {
                is ClosingAttachmentDraft.Selected -> {
                    val att = closingAttachmentDraft.attachment
                    displayedAttachmentName = att.fileName
                    displayedAttachmentSubtitle = "${att.mimeType} · ${ImageCompressor.formatFileSize(att.totalBytes)} · Listo para subir al guardar"
                    displayedInlinePreviewBase64 = if (att.mimeType.startsWith("image/")) att.singleBase64OrNull else null
                    isUnsavedDraft = true
                }
                ClosingAttachmentDraft.Removed -> {
                    displayedAttachmentName = null
                    displayedAttachmentSubtitle = null
                    displayedInlinePreviewBase64 = null
                    isUnsavedDraft = false
                }
                ClosingAttachmentDraft.Unchanged -> {
                    val hasSavedAttachment = existingToday != null &&
                        !existingToday.attachmentName.isNullOrBlank() &&
                        (!existingToday.attachmentBase64.isNullOrBlank() || existingToday.attachmentChunkCount > 0L)
                    if (hasSavedAttachment && existingToday != null) {
                        displayedAttachmentName = existingToday.attachmentName
                        val sizeText = if (existingToday.attachmentSizeBytes > 0L) {
                            " · ${ImageCompressor.formatFileSize(existingToday.attachmentSizeBytes.toInt())}"
                        } else {
                            ""
                        }
                        displayedAttachmentSubtitle = "${existingToday.attachmentMimeType ?: "Archivo"}$sizeText · Subido"
                        displayedInlinePreviewBase64 = if (existingToday.attachmentMimeType?.startsWith("image/") == true) {
                            existingToday.attachmentBase64
                        } else {
                            null
                        }
                    } else {
                        displayedAttachmentName = null
                        displayedAttachmentSubtitle = null
                        displayedInlinePreviewBase64 = null
                    }
                    isUnsavedDraft = false
                }
            }

            if (displayedAttachmentName != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    imageVector = Icons.Filled.Description,
                                    contentDescription = null,
                                    tint = BrandNavy,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = displayedAttachmentName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (displayedAttachmentSubtitle != null) {
                                        Text(
                                            text = displayedAttachmentSubtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isUnsavedDraft) BrandNavy else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = {
                                    attachmentError = null
                                    onUpdateAttachmentDraft(ClosingAttachmentDraft.Removed)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Quitar adjunto",
                                    tint = BrandRed
                                )
                            }
                        }

                        if (displayedInlinePreviewBase64 != null) {
                            val bmp = remember(displayedInlinePreviewBase64) {
                                ImageCompressor.decodeBase64ToImageBitmap(displayedInlinePreviewBase64)
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = "Vista previa del adjunto",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }

            if (attachmentError != null) {
                Surface(
                    color = BrandRedSoft,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = attachmentError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandRed
                        )
                    }
                }
            }

            if (validationError != null) {
                Surface(
                    color = BrandRedSoft,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = validationError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandRed
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val g = globalSalesText.toDoubleOrNull()
                    val c = creditSalesText.toDoubleOrNull()
                    val nc = newCustomersText.toLongOrNull()
                    val ma = appMaOpsText.toLongOrNull()
                    val req = newRequestsText.toLongOrNull()
                    if (g == null || c == null || nc == null || ma == null || req == null) {
                        validationError = "Captura un valor numérico en los 5 indicadores (usa 0 si no hubo) para guardar el cierre y subir el archivo."
                    } else {
                        validationError = null
                        onSave(
                            g,
                            c,
                            nc,
                            ma,
                            req,
                            notesText,
                            closingAttachmentDraft
                        )
                    }
                },
                enabled = !isBusy && !isProcessingAttachment,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_daily_closing_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandNavy,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (existingToday == null) "Guardar Cierre de Hoy" else "Actualizar Cierre de Hoy",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun RecentClosingAttachmentCard(
    entry: DailySalesEntry,
    onLoadEntryAttachmentChunks: suspend (DailySalesEntry) -> Result<List<String>>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showImageDialog by remember { mutableStateOf(false) }
    var isLoadingAttachment by remember { mutableStateOf(false) }
    var openError by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.formattedAuthorLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = BrandNavy
                )
                Text(
                    text = DateAndFormatters.formatIsoToShort(entry.dateString),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entry.notes.isNotBlank()) {
                Text(
                    text = entry.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            val hasAttachment = !entry.attachmentName.isNullOrBlank() &&
                (!entry.attachmentBase64.isNullOrBlank() || entry.attachmentChunkCount > 0L)
            if (hasAttachment && entry.attachmentName != null) {
                val isImage = entry.attachmentMimeType?.startsWith("image/") == true
                OutlinedButton(
                    enabled = !isLoadingAttachment,
                    onClick = {
                        openError = null
                        if (isImage && !entry.attachmentBase64.isNullOrBlank()) {
                            showImageDialog = true
                        } else {
                            isLoadingAttachment = true
                            scope.launch {
                                val chunksResult = withContext(Dispatchers.IO) {
                                    onLoadEntryAttachmentChunks(entry)
                                }
                                isLoadingAttachment = false
                                chunksResult.fold(
                                    onSuccess = { chunks ->
                                        val uri = withContext(Dispatchers.IO) {
                                            ImageCompressor.createViewableCacheUriFromChunks(
                                                context = context,
                                                fileName = entry.attachmentName,
                                                base64Chunks = chunks
                                            )
                                        }
                                        if (uri != null) {
                                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, entry.attachmentMimeType ?: "*/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            runCatching {
                                                context.startActivity(viewIntent)
                                            }.onFailure {
                                                openError = "No hay una aplicación instalada para abrir este tipo de archivo."
                                            }
                                        } else {
                                            openError = "No se pudo preparar el archivo para abrirlo."
                                        }
                                    },
                                    onFailure = { err ->
                                        openError = err.localizedMessage ?: "No se pudo descargar el archivo."
                                    }
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isLoadingAttachment) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    val sizeLabel = if (entry.attachmentSizeBytes > 0L) {
                        " (${ImageCompressor.formatFileSize(entry.attachmentSizeBytes.toInt())})"
                    } else {
                        ""
                    }
                    Text("${entry.attachmentName}$sizeLabel", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (openError != null) {
                Text(
                    text = openError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandRed
                )
            }
        }
    }

    if (showImageDialog && entry.attachmentBase64 != null) {
        val bmp = remember(entry.attachmentBase64) {
            ImageCompressor.decodeBase64ToImageBitmap(entry.attachmentBase64)
        }
        AlertDialog(
            onDismissRequest = { showImageDialog = false },
            title = { Text(entry.attachmentName ?: "Foto de cierre") },
            text = {
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "Foto adjunta al cierre",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showImageDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

private fun paceColorsAndLabel(pace: ProjectionPace): Triple<Color, Color, String> = when (pace) {
    ProjectionPace.ON_TRACK -> Triple(SuccessGreen, SuccessGreenSoft, "Proyección positiva")
    ProjectionPace.MODERATE_RISK -> Triple(WarningAmber, WarningAmberSoft, "Riesgo moderado")
    ProjectionPace.BEHIND -> Triple(BrandRed, BrandRedSoft, "Proyección negativa")
}

@Composable
private fun ProjectionOverviewCard(
    summary: SalesProjectionSummary,
    selectedMetric: SalesMetricType,
    chartSeries: ChartSeries?,
    isAdmin: Boolean,
    onSelectMetric: (SalesMetricType) -> Unit,
    onEditQuota: () -> Unit
) {
    val activeProgress = summary.metrics.firstOrNull { it.metric == selectedMetric }
        ?: summary.metrics.first()
    val (paceColor, _, _) = paceColorsAndLabel(activeProgress.pace)
    val weighted = if (isAdmin) summary.weightedByMetric[activeProgress.metric] else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.quota.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${DateAndFormatters.formatIsoToShort(summary.quota.startDate)} – ${DateAndFormatters.formatIsoToShort(summary.quota.endDate)} · ${summary.entriesCount} cierres",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isAdmin) {
                    OutlinedButton(
                        onClick = onEditQuota,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("edit_quota_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EditCalendar,
                            contentDescription = "Configurar meta quincenal",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Meta ${summary.totalDays}d", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SalesMetricType.entries.forEach { metric ->
                    FilterChip(
                        selected = metric == selectedMetric,
                        onClick = { onSelectMetric(metric) },
                        label = { Text(metric.shortTitleEs) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = BrandNavy
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Acumulado · ${activeProgress.metric.titleEs}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = DateAndFormatters.formatMetricValue(activeProgress.metric, activeProgress.actualTotal),
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = MonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val remainingStr = if (activeProgress.isTargetMet) {
                        "Meta superada (+${DateAndFormatters.formatMetricValue(activeProgress.metric, activeProgress.surplusOverTarget)})"
                    } else {
                        "Saldo pendiente: ${DateAndFormatters.formatMetricValue(activeProgress.metric, activeProgress.remainingToTarget)} de ${DateAndFormatters.formatMetricValue(activeProgress.metric, activeProgress.targetTotal)}"
                    }
                    Text(
                        text = remainingStr,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (activeProgress.isTargetMet) SuccessGreen else BrandNavy,
                        modifier = Modifier.testTag("overview_remaining_balance")
                    )
                }
                PaceStatusChip(pace = activeProgress.pace, projectedRatio = activeProgress.projectedRatio)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { activeProgress.progressRatio.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .testTag("overview_progress_bar"),
                    color = paceColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val actualPct = (activeProgress.progressRatio * 100).roundToInt().coerceAtLeast(0)
                    Text(
                        text = "$actualPct% actual · Día ${summary.daysElapsed}/${summary.totalDays} (restan ${summary.remainingDays}d)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Ritmo: ${DateAndFormatters.formatMetricValue(activeProgress.metric, activeProgress.dailyAverage)}/día",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = MonoFontFamily,
                        color = paceColor
                    )
                }
            }

            if (chartSeries != null) {
                ProjectionLineChart(
                    series = chartSeries,
                    pace = activeProgress.pace
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Proyección día ${summary.totalDays}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = DateAndFormatters.formatMetricValue(activeProgress.metric, activeProgress.projectedTotal),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = paceColor
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Ritmo diario necesario",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = DateAndFormatters.formatMetricValue(activeProgress.metric, activeProgress.requiredDailyPace),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = MonoFontFamily,
                            color = BrandNavy
                        )
                    }
                }
            }

            if (weighted != null) {
                WeightedAdminBalanceCard(weighted = weighted)
            }
        }
    }
}

@Composable
private fun WeightedAdminBalanceCard(weighted: AdminWeightedProjection) {
    val (paceColor, paceBg, paceLabel) = paceColorsAndLabel(weighted.pace)
    val projectedVsAdminPct = (weighted.adminProjectedRatio * 100).roundToInt().coerceAtLeast(0)
    val weightedBranchPct = (weighted.weightedBranchProjectedRatio * 100).roundToInt().coerceAtLeast(0)
    var showBranchWeights by remember { mutableStateOf(false) }

    Surface(
        color = paceBg.copy(alpha = 0.55f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, paceColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_weighted_balance_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Balance Ponderado de Sucursales",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandNavy
                    )
                    Text(
                        text = "Ponderado por peso de cuota · Cumplimiento tiendas: $weightedBranchPct%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(color = paceBg, shape = RoundedCornerShape(50)) {
                    Text(
                        text = "$paceLabel · $projectedVsAdminPct%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = paceColor,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { weighted.adminProjectedRatio.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(RoundedCornerShape(50))
                    .testTag("admin_weighted_progress_bar"),
                color = paceColor,
                trackColor = MaterialTheme.colorScheme.surface,
                strokeCap = StrokeCap.Round
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = SuccessGreenSoft,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Positivas (${weighted.positiveBranchesCount})",
                            style = MaterialTheme.typography.labelSmall,
                            color = SuccessGreen
                        )
                        Text(
                            text = "+${DateAndFormatters.formatMetricValue(weighted.metric, weighted.positiveSurplusTotal)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                        Text(
                            text = "Excedente proyectado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    color = BrandRedSoft,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val laggingCount = weighted.moderateRiskBranchesCount + weighted.behindBranchesCount
                        Text(
                            text = "Rezagadas ($laggingCount)",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandRed
                        )
                        Text(
                            text = "-${DateAndFormatters.formatMetricValue(weighted.metric, weighted.negativeDeficitTotal)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = BrandRed
                        )
                        Text(
                            text = "Déficit proyectado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val verdictText = when {
                weighted.netBalanceVsAdminQuota >= 0.0 && weighted.negativeDeficitTotal > 0.0 ->
                    "El superávit cubre el déficit y proyecta +${DateAndFormatters.formatMetricValue(weighted.metric, weighted.netBalanceVsAdminQuota)} sobre la meta general."
                weighted.netBalanceVsAdminQuota >= 0.0 ->
                    "Ritmo positivo general: +${DateAndFormatters.formatMetricValue(weighted.metric, weighted.netBalanceVsAdminQuota)} sobre la meta general."
                weighted.pace == ProjectionPace.MODERATE_RISK ->
                    "El excedente no alcanza a cubrir el déficit (-${DateAndFormatters.formatMetricValue(weighted.metric, abs(weighted.netBalanceVsAdminQuota))} vs. meta general). Riesgo recuperable."
                else ->
                    "El déficit supera al excedente (-${DateAndFormatters.formatMetricValue(weighted.metric, abs(weighted.netBalanceVsAdminQuota))} vs. meta general). Proyección negativa."
            }
            Text(
                text = verdictText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = paceColor,
                modifier = Modifier.testTag("admin_weighted_verdict")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val netStoreSign = if (weighted.branchNetBalance >= 0.0) "+" else "-"
                Text(
                    text = "Balance vs. cuotas de tienda: $netStoreSign${DateAndFormatters.formatMetricValue(weighted.metric, abs(weighted.branchNetBalance))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { showBranchWeights = !showBranchWeights },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = if (showBranchWeights) "Ocultar pesos" else "Ver pesos",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandNavy
                    )
                }
            }

            if (showBranchWeights) {
                HorizontalDivider(color = paceColor.copy(alpha = 0.25f))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    weighted.branchOutcomes.forEach { outcome ->
                        val (bColor, _, _) = paceColorsAndLabel(outcome.pace)
                        val weightPct = (outcome.quotaWeightShare * 100f).roundToInt()
                        val deltaSign = if (outcome.deltaVsBranchQuota >= 0.0) "+" else "-"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${outcome.branchName} ($weightPct% cuota)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$deltaSign${DateAndFormatters.formatMetricValue(weighted.metric, abs(outcome.deltaVsBranchQuota))}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = bColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaceStatusChip(pace: ProjectionPace, projectedRatio: Float) {
    val pct = (projectedRatio * 100).roundToInt().coerceAtLeast(0)
    val (fg, bg, baseLabel) = paceColorsAndLabel(pace)
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp)
            )
            Text(text = "$baseLabel ($pct%)", style = MaterialTheme.typography.labelMedium, color = fg)
        }
    }
}

@Composable
private fun MetricScorecardItem(
    progress: MetricProgress,
    isSelected: Boolean,
    canEditMeta: Boolean = false,
    isBusy: Boolean = false,
    onClick: () -> Unit,
    onSaveTarget: (Double, () -> Unit) -> Unit = { _, done -> done() }
) {
    val pct = (progress.progressRatio * 100).roundToInt().coerceAtLeast(0)
    val projPct = (progress.projectedRatio * 100).roundToInt().coerceAtLeast(0)
    val (paceColor, paceBg, paceLabel) = paceColorsAndLabel(progress.pace)
    val isCurrency = progress.metric == SalesMetricType.GLOBAL_SALES ||
        progress.metric == SalesMetricType.CREDIT_SALES
    val initialText = remember(progress.metric, progress.targetTotal) {
        if (isCurrency && progress.targetTotal % 1.0 != 0.0) {
            progress.targetTotal.toString()
        } else {
            progress.targetTotal.toLong().toString()
        }
    }
    var isEditingMeta by remember(progress.metric, canEditMeta) { mutableStateOf(false) }
    var targetInput by remember(progress.metric, progress.targetTotal, isEditingMeta) {
        mutableStateOf(initialText)
    }
    var inputError by remember(progress.metric, isEditingMeta) { mutableStateOf<String?>(null) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("indicator_card_${progress.metric.name}"),
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) BorderStroke(1.5.dp, paceColor) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFF8FBFF) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = progress.metric.titleEs,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = paceBg,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("indicator_pace_badge_${progress.metric.name}")
                    ) {
                        Text(
                            text = "$pct% · Proy. $projPct%",
                            style = MaterialTheme.typography.labelSmall,
                            color = paceColor,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (canEditMeta) {
                        TextButton(
                            onClick = {
                                targetInput = initialText
                                inputError = null
                                isEditingMeta = !isEditingMeta
                            },
                            enabled = !isBusy,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("edit_indicator_meta_${progress.metric.name}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Editar meta de ${progress.metric.titleEs}",
                                modifier = Modifier.size(14.dp),
                                tint = BrandNavy
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isEditingMeta) "Cerrar" else "Editar meta",
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandNavy
                            )
                        }
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress.progressRatio.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .testTag("indicator_progress_bar_${progress.metric.name}"),
                color = paceColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Real: ${DateAndFormatters.formatMetricValue(progress.metric, progress.actualTotal)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Meta: ${DateAndFormatters.formatMetricValue(progress.metric, progress.targetTotal)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (canEditMeta) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (canEditMeta) BrandNavy else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (progress.isTargetMet) {
                        "Meta superada: +${DateAndFormatters.formatMetricValue(progress.metric, progress.surplusOverTarget)}"
                    } else {
                        "Saldo pendiente: ${DateAndFormatters.formatMetricValue(progress.metric, progress.remainingToTarget)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = if (progress.isTargetMet) SuccessGreen else BrandNavy,
                    modifier = Modifier.testTag("indicator_remaining_${progress.metric.name}")
                )
                Text(
                    text = "Proy.: ${DateAndFormatters.formatMetricValue(progress.metric, progress.projectedTotal)} · $paceLabel",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = paceColor
                )
            }

            if (canEditMeta && isEditingMeta) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = targetInput,
                            onValueChange = { raw ->
                                targetInput = if (isCurrency) {
                                    raw.filter { it.isDigit() || it == '.' }
                                } else {
                                    raw.filter { it.isDigit() }
                                }
                                inputError = null
                            },
                            label = {
                                Text(
                                    if (isCurrency) {
                                        "Nueva meta ($ MXN)"
                                    } else {
                                        "Nueva meta (unidades)"
                                    }
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (isCurrency) KeyboardType.Decimal else KeyboardType.Number
                            ),
                            singleLine = true,
                            isError = inputError != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("indicator_meta_input_${progress.metric.name}")
                        )
                        if (inputError != null) {
                            Text(
                                text = inputError ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandRed
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    isEditingMeta = false
                                    inputError = null
                                },
                                enabled = !isBusy
                            ) {
                                Text("Cancelar")
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    val parsedValue: Double? = if (isCurrency) {
                                        targetInput.trim().toDoubleOrNull()
                                    } else {
                                        targetInput.trim().toLongOrNull()?.toDouble()
                                    }
                                    val validationMsg = when {
                                        parsedValue == null -> "Ingresa un valor numérico válido."
                                        progress.metric == SalesMetricType.GLOBAL_SALES && parsedValue <= 0.0 ->
                                            "La meta de Venta global debe ser mayor a 0."
                                        parsedValue < 0.0 -> "La meta no puede ser negativa."
                                        else -> null
                                    }
                                    if (validationMsg != null) {
                                        inputError = validationMsg
                                    } else if (parsedValue != null) {
                                        inputError = null
                                        onSaveTarget(parsedValue) {
                                            isEditingMeta = false
                                        }
                                    }
                                },
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandNavy,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("save_indicator_meta_${progress.metric.name}")
                            ) {
                                Text(if (isBusy) "Guardando..." else "Guardar", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminBranchFilterStrip(
    selectedBranch: String,
    onSelectBranch: (String) -> Unit
) {
    val options = remember { listOf(BranchOptions.ALL_BRANCHES_OPTION) + BranchOptions.ALL_BRANCHES }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Vista de Dashboard · $selectedBranch",
                style = MaterialTheme.typography.labelLarge,
                color = BrandNavy
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { branch ->
                    FilterChip(
                        selected = selectedBranch == branch,
                        onClick = { onSelectBranch(branch) },
                        label = { Text(branch, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandNavy,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("dashboard_branch_chip_$branch")
                    )
                }
            }
        }
    }
}
