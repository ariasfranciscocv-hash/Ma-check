package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditCalendar
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
import com.example.data.model.BiweeklyQuota
import com.example.data.model.ChartSeries
import com.example.data.model.DailySalesEntry
import com.example.data.model.MetricProgress
import com.example.data.model.ProjectionPace
import com.example.data.model.SalesMetricType
import com.example.data.model.SalesProjectionSummary
import com.example.data.model.UiState
import com.example.ui.components.MueblesAmericaLogo
import com.example.ui.components.ProjectionLineChart
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenSoft
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberSoft
import com.example.utils.DateAndFormatters
import com.example.utils.EncodedAttachment
import com.example.utils.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeSalesScreen(
    branchLabel: String,
    isAdmin: Boolean,
    isBusy: Boolean,
    projectionState: UiState<SalesProjectionSummary?>,
    pendingSalesEntryIds: Set<String> = emptySet(),
    selectedMetric: SalesMetricType,
    chartSeries: ChartSeries?,
    showDailyBriefing: Boolean,
    onDismissBriefing: () -> Unit,
    onSelectMetric: (SalesMetricType) -> Unit,
    onSaveDailyClosing: (
        quotaId: String,
        globalSales: Double,
        creditSales: Double,
        newCustomers: Long,
        appMaOperations: Long,
        newCustomerRequests: Long,
        notes: String,
        attachmentName: String?,
        attachmentMimeType: String?,
        attachmentBase64: String?
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
        onDone: () -> Unit
    ) -> Unit
) {
    var showEditQuotaDialog by remember { mutableStateOf(false) }

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
                    summary = summary,
                    onDismiss = onDismissBriefing
                )
            }

            if (showEditQuotaDialog) {
                EditQuotaDialog(
                    currentQuota = summary?.quota,
                    isBusy = isBusy,
                    onDismiss = { showEditQuotaDialog = false },
                    onSave = { title, start, end, days, g, c, nc, ma, req ->
                        onSaveBiweeklyQuota(title, start, end, days, g, c, nc, ma, req) {
                            showEditQuotaDialog = false
                        }
                    }
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (summary == null) {
                    item {
                        NoActiveQuotaCard(
                            isAdmin = isAdmin,
                            onCreateQuota = { showEditQuotaDialog = true }
                        )
                    }
                } else {
                    item {
                        val todayPendingSync = summary.todayEntry?.id?.let { it in pendingSalesEntryIds } == true
                        DailyClosingFormCard(
                            summary = summary,
                            branchLabel = branchLabel,
                            isBusy = isBusy,
                            isPendingSync = todayPendingSync,
                            onSave = { g, c, nc, ma, req, notes, attName, attMime, attBase64 ->
                                onSaveDailyClosing(
                                    summary.quota.id,
                                    g,
                                    c,
                                    nc,
                                    ma,
                                    req,
                                    notes,
                                    attName,
                                    attMime,
                                    attBase64
                                )
                            }
                        )
                    }

                    item {
                        ProjectionOverviewCard(
                            summary = summary,
                            selectedMetric = selectedMetric,
                            chartSeries = chartSeries,
                            isAdmin = isAdmin,
                            onSelectMetric = onSelectMetric,
                            onEditQuota = { showEditQuotaDialog = true }
                        )
                    }

                    item {
                        Text(
                            text = "Indicadores de la Quincena",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    items(summary.metrics.size) { index ->
                        val item = summary.metrics[index]
                        MetricScorecardItem(
                            progress = item,
                            isSelected = item.metric == selectedMetric,
                            onClick = { onSelectMetric(item.metric) }
                        )
                    }

                    val entriesWithDetails = summary.recentEntries.filter {
                        it.notes.isNotBlank() || !it.attachmentBase64.isNullOrBlank()
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
                            RecentClosingAttachmentCard(entry = entriesWithDetails[index])
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
                    text = "Recuerda capturar tus 5 métricas al cierre de operaciones de hoy.",
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
                Text("Registrar Cierre / Ver Panel")
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
                    "Configura la meta quincenal de 14 días para habilitar el cierre de ventas y las proyecciones."
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
    isBusy: Boolean,
    isPendingSync: Boolean,
    onSave: (Double, Double, Long, Long, Long, String, String?, String?, String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existingToday = summary.todayEntry
    var globalSalesText by rememberSaveable(summary.quota.id, existingToday?.id) {
        mutableStateOf(existingToday?.globalSales?.let { if (it > 0) it.toLong().toString() else "" } ?: "")
    }
    var creditSalesText by rememberSaveable(summary.quota.id, existingToday?.id) {
        mutableStateOf(existingToday?.creditSales?.let { if (it > 0) it.toLong().toString() else "" } ?: "")
    }
    var newCustomersText by rememberSaveable(summary.quota.id, existingToday?.id) {
        mutableStateOf(existingToday?.newCustomers?.let { if (it > 0) it.toString() else "" } ?: "")
    }
    var appMaOpsText by rememberSaveable(summary.quota.id, existingToday?.id) {
        mutableStateOf(existingToday?.appMaOperations?.let { if (it > 0) it.toString() else "" } ?: "")
    }
    var newRequestsText by rememberSaveable(summary.quota.id, existingToday?.id) {
        mutableStateOf(existingToday?.newCustomerRequests?.let { if (it > 0) it.toString() else "" } ?: "")
    }
    var notesText by rememberSaveable(summary.quota.id, existingToday?.id) {
        mutableStateOf(existingToday?.notes ?: "")
    }
    var attachment by remember(summary.quota.id, existingToday?.id) {
        mutableStateOf(
            if (existingToday?.attachmentBase64 != null &&
                existingToday.attachmentName != null &&
                existingToday.attachmentMimeType != null
            ) {
                EncodedAttachment(
                    fileName = existingToday.attachmentName,
                    mimeType = existingToday.attachmentMimeType,
                    base64 = existingToday.attachmentBase64
                )
            } else {
                null
            }
        )
    }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
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
                onSuccess = { encoded -> attachment = encoded },
                onFailure = { err -> attachmentError = err.localizedMessage ?: "No se pudo adjuntar el archivo." }
            )
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
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

    LaunchedEffect(existingToday?.id, existingToday?.updatedAt) {
        if (existingToday != null) {
            globalSalesText = existingToday.globalSales.toLong().toString()
            creditSalesText = existingToday.creditSales.toLong().toString()
            newCustomersText = existingToday.newCustomers.toString()
            appMaOpsText = existingToday.appMaOperations.toString()
            newRequestsText = existingToday.newCustomerRequests.toString()
            notesText = existingToday.notes
            attachment = if (existingToday.attachmentBase64 != null &&
                existingToday.attachmentName != null &&
                existingToday.attachmentMimeType != null
            ) {
                EncodedAttachment(
                    fileName = existingToday.attachmentName,
                    mimeType = existingToday.attachmentMimeType,
                    base64 = existingToday.attachmentBase64
                )
            } else {
                null
            }
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
                }
                if (existingToday != null) {
                    Surface(
                        color = if (isPendingSync) WarningAmberSoft else SuccessGreenSoft,
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isPendingSync) Icons.Filled.CloudUpload else Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = if (isPendingSync) WarningAmber else SuccessGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (isPendingSync) "Local · Pendiente" else "Registrado",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPendingSync) WarningAmber else SuccessGreen
                            )
                        }
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
                text = "Adjuntar foto o archivo del teléfono (opcional)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val uri = ImageCompressor.createTempCameraUri(context)
                        pendingCameraUri = uri
                        cameraLauncher.launch(uri)
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
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
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
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
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

            attachment?.let { att ->
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
                                        text = att.fileName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = att.mimeType,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { attachment = null }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Quitar adjunto",
                                    tint = BrandRed
                                )
                            }
                        }

                        if (att.mimeType.startsWith("image/")) {
                            val bmp = remember(att.base64) {
                                ImageCompressor.decodeBase64ToImageBitmap(att.base64)
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
                Text(
                    text = attachmentError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandRed
                )
            }

            if (validationError != null) {
                Text(
                    text = validationError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandRed
                )
            }

            Button(
                onClick = {
                    val g = globalSalesText.toDoubleOrNull()
                    val c = creditSalesText.toDoubleOrNull()
                    val nc = newCustomersText.toLongOrNull()
                    val ma = appMaOpsText.toLongOrNull()
                    val req = newRequestsText.toLongOrNull()
                    if (g == null || c == null || nc == null || ma == null || req == null) {
                        validationError = "Captura un valor numérico en los 5 indicadores (usa 0 si no hubo)."
                    } else {
                        validationError = null
                        onSave(
                            g,
                            c,
                            nc,
                            ma,
                            req,
                            notesText,
                            attachment?.fileName,
                            attachment?.mimeType,
                            attachment?.base64
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
private fun RecentClosingAttachmentCard(entry: DailySalesEntry) {
    val context = LocalContext.current
    var showImageDialog by remember { mutableStateOf(false) }

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
                    text = entry.userName,
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
            if (!entry.attachmentBase64.isNullOrBlank() && !entry.attachmentName.isNullOrBlank()) {
                val isImage = entry.attachmentMimeType?.startsWith("image/") == true
                OutlinedButton(
                    onClick = {
                        if (isImage) {
                            showImageDialog = true
                        } else {
                            val uri = ImageCompressor.createViewableCacheUri(
                                context,
                                entry.attachmentName,
                                entry.attachmentBase64
                            )
                            if (uri != null) {
                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, entry.attachmentMimeType ?: "*/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(viewIntent) }
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(entry.attachmentName, style = MaterialTheme.typography.labelSmall)
                }
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
                        Text("Meta 14d", style = MaterialTheme.typography.labelMedium)
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
                            selectedContainerColor = BrandNavy,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
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
                }
                PaceStatusChip(pace = activeProgress.pace, projectedRatio = activeProgress.projectedRatio)
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
                            fontWeight = FontWeight.Bold
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
        }
    }
}

@Composable
private fun PaceStatusChip(pace: ProjectionPace, projectedRatio: Float) {
    val pct = (projectedRatio * 100).toInt()
    val (bg, fg, label) = when (pace) {
        ProjectionPace.ON_TRACK -> Triple(SuccessGreenSoft, SuccessGreen, "En meta ($pct%)")
        ProjectionPace.MODERATE_RISK -> Triple(WarningAmberSoft, WarningAmber, "Ajustado ($pct%)")
        ProjectionPace.BEHIND -> Triple(MaterialTheme.colorScheme.secondaryContainer, BrandRed, "Bajo ritmo ($pct%)")
    }
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
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = fg)
        }
    }
}

@Composable
private fun MetricScorecardItem(
    progress: MetricProgress,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val pct = (progress.progressRatio * 100).toInt().coerceAtLeast(0)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$pct% de meta",
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandNavy,
                    fontFamily = MonoFontFamily
                )
            }
            LinearProgressIndicator(
                progress = { progress.progressRatio.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                color = BrandNavy,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EditQuotaDialog(
    currentQuota: BiweeklyQuota?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Long, Double, Double, Long, Long, Long) -> Unit
) {
    val defaultRange = remember { DateAndFormatters.defaultBiweeklyRange() }
    var title by remember { mutableStateOf(currentQuota?.title ?: DateAndFormatters.defaultBiweeklyTitle()) }
    var globalTarget by remember { mutableStateOf((currentQuota?.targetGlobalSales ?: 250000.0).toLong().toString()) }
    var creditTarget by remember { mutableStateOf((currentQuota?.targetCreditSales ?: 150000.0).toLong().toString()) }
    var newCustomersTarget by remember { mutableStateOf((currentQuota?.targetNewCustomers ?: 45L).toString()) }
    var appMaTarget by remember { mutableStateOf((currentQuota?.targetAppMaOperations ?: 60L).toString()) }
    var requestsTarget by remember { mutableStateOf((currentQuota?.targetNewCustomerRequests ?: 75L).toString()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Meta Quincenal (14 días)") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("Nombre de la quincena") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = globalTarget,
                    onValueChange = { globalTarget = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Meta Venta global ($ MXN)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = creditTarget,
                    onValueChange = { creditTarget = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Meta Venta a crédito ($ MXN)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = newCustomersTarget,
                    onValueChange = { newCustomersTarget = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Meta Clientes nuevos (unidades)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = appMaTarget,
                    onValueChange = { appMaTarget = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Meta Operaciones app MA (unidades)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = requestsTarget,
                    onValueChange = { requestsTarget = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Meta Solicitudes cliente nuevo (uds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                if (errorMsg != null) {
                    Text(text = errorMsg ?: "", style = MaterialTheme.typography.bodySmall, color = BrandRed)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = {
                    val g = globalTarget.toDoubleOrNull()
                    val c = creditTarget.toDoubleOrNull()
                    val nc = newCustomersTarget.toLongOrNull()
                    val ma = appMaTarget.toLongOrNull()
                    val req = requestsTarget.toLongOrNull()
                    if (title.trim().length < 3 || g == null || g <= 0 || c == null || nc == null || ma == null || req == null) {
                        errorMsg = "Revisa el nombre y todos los objetivos numéricos."
                    } else {
                        val start = currentQuota?.startDate ?: defaultRange.first
                        val end = currentQuota?.endDate ?: defaultRange.second
                        val days = currentQuota?.totalDays ?: 14L
                        onSave(title.trim(), start, end, days, g, c, nc, ma, req)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy)
            ) {
                Text("Guardar Meta")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
