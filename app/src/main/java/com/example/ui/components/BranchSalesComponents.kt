package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storefront
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.BiweeklyQuota
import com.example.data.model.BranchOptions
import com.example.data.model.BranchSalesSummary
import com.example.data.model.DailySalesEntry
import com.example.data.model.ProjectionPace
import com.example.data.model.resolveQuotaForBranch
import com.example.data.repository.ProjectionCalculator
import com.example.ui.theme.BorderSubtle
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

@Composable
fun BranchSalesTrackingSection(
    summaries: List<BranchSalesSummary>,
    isAdmin: Boolean,
    onOpenBranchDetail: (BranchSalesSummary) -> Unit,
    onConfigureBranchQuota: ((String) -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Metas y Ventas por Sucursal",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Toca una sucursal para ver el cierre de hoy y su historial quincenal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isAdmin && onConfigureBranchQuota != null) {
                OutlinedButton(
                    onClick = { onConfigureBranchQuota(BranchOptions.ALL_BRANCHES_OPTION) },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("open_assign_quota_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.EditCalendar,
                        contentDescription = "Asignar meta quincenal",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Asignar Meta", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (summaries.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    text = "Asigna una meta quincenal para visualizar el avance acumulado por sucursal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            summaries.forEach { summary ->
                BranchSalesCard(
                    summary = summary,
                    onClick = { onOpenBranchDetail(summary) }
                )
            }
        }
    }
}

private fun paceVisuals(pace: ProjectionPace): Triple<Color, Color, String> = when (pace) {
    ProjectionPace.ON_TRACK -> Triple(SuccessGreen, SuccessGreenSoft, "Proyección positiva")
    ProjectionPace.MODERATE_RISK -> Triple(WarningAmber, WarningAmberSoft, "Riesgo moderado")
    ProjectionPace.BEHIND -> Triple(BrandRed, BrandRedSoft, "Proyección negativa")
}

@Composable
fun BranchSalesCard(
    summary: BranchSalesSummary,
    onClick: () -> Unit
) {
    val todayEntry = summary.todayEntry
    val (progressColor, statusBg, paceLabel) = paceVisuals(summary.pace)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("branch_sales_card_${summary.branchName}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storefront,
                        contentDescription = null,
                        tint = BrandNavy,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = summary.branchName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (summary.hasCustomBranchQuota) "Meta propia de sucursal" else "Meta general aplicada",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (summary.hasCustomBranchQuota) BrandNavy else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = if (todayEntry != null) SuccessGreenSoft else WarningAmberSoft,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (todayEntry != null) {
                            "Hoy: ${DateAndFormatters.formatCurrency(todayEntry.globalSales)}"
                        } else {
                            "Sin cierre hoy"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (todayEntry != null) SuccessGreen else WarningAmber,
                        fontFamily = MonoFontFamily,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${DateAndFormatters.formatCurrency(summary.totalGlobalSales)} / ${DateAndFormatters.formatCurrency(summary.quota.targetGlobalSales)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${summary.completionPercent}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = progressColor
                    )
                }
                LinearProgressIndicator(
                    progress = { summary.progressRatio.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (summary.isGlobalTargetMet) {
                                "Meta superada: +${DateAndFormatters.formatCurrency(summary.surplusGlobalSales)}"
                            } else {
                                "Saldo pendiente: ${DateAndFormatters.formatCurrency(summary.remainingGlobalSales)}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = if (summary.isGlobalTargetMet) SuccessGreen else BrandNavy
                        )
                        Text(
                            text = "Ritmo: ${DateAndFormatters.formatCurrency(summary.dailyAverage)}/día · Proy. ${DateAndFormatters.formatCurrency(summary.projectedGlobalSales)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = statusBg,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("branch_pace_badge_${summary.branchName}")
                    ) {
                        Text(
                            text = "$paceLabel (${summary.projectedPercent}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = progressColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${summary.periodEntries.size} cierre(s) en la quincena",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Ver detalle",
                        style = MaterialTheme.typography.labelMedium,
                        color = BrandNavy,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Abrir detalle de sucursal",
                        tint = BrandNavy,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BranchSalesDetailDialog(
    summary: BranchSalesSummary,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onEditQuota: ((String) -> Unit)? = null,
    onLoadEntryAttachmentChunks: suspend (DailySalesEntry) -> Result<List<String>>
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(color = BrandNavy, contentColor = Color.White) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.testTag("close_branch_detail_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Cerrar detalle",
                                    tint = Color.White
                                )
                            }
                            Column {
                                Text(
                                    text = "Detalle de Ventas de Sucursal",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Text(
                                    text = "${summary.branchName} · ${DateAndFormatters.formatIsoToShort(summary.quota.startDate)} – ${DateAndFormatters.formatIsoToShort(summary.quota.endDate)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.88f)
                                )
                            }
                        }

                        if (isAdmin && onEditQuota != null) {
                            TextButton(
                                onClick = { onEditQuota(summary.branchName) },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                                modifier = Modifier.testTag("detail_edit_branch_quota_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.EditCalendar,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Meta")
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        BranchQuotaComparisonCard(summary = summary)
                    }

                    item {
                        Text(
                            text = "a) Cierre de Ventas del Día en Curso",
                            style = MaterialTheme.typography.titleMedium,
                            color = BrandNavy
                        )
                    }

                    item {
                        TodayClosingDetailCard(
                            todayEntry = summary.todayEntry,
                            onLoadEntryAttachmentChunks = onLoadEntryAttachmentChunks
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "b) Historial de la Quincena Actual",
                                style = MaterialTheme.typography.titleMedium,
                                color = BrandNavy
                            )
                            Text(
                                text = "${summary.periodEntries.size} cierre(s)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (summary.periodEntries.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Text(
                                    text = "Aún no se han registrado cierres de venta para esta sucursal durante la quincena actual.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(
                            items = summary.periodEntries,
                            key = { it.id.ifBlank { it.dateString } }
                        ) { entry ->
                            PeriodHistoryClosingCard(
                                entry = entry,
                                isToday = entry.dateString == DateAndFormatters.todayIso(),
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
private fun BranchQuotaComparisonCard(summary: BranchSalesSummary) {
    val difference = summary.totalGlobalSales - summary.quota.targetGlobalSales
    val (progressColor, statusBg, paceLabel) = paceVisuals(summary.pace)

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.quota.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = BrandNavy
                    )
                    Text(
                        text = if (summary.hasCustomBranchQuota) {
                            "Meta asignada a ${summary.branchName}"
                        } else {
                            "Usando meta general de quincena"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "$paceLabel · ${summary.completionPercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = MonoFontFamily,
                        color = progressColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Acumulado quincena",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = DateAndFormatters.formatCurrency(summary.totalGlobalSales),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Meta quincenal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = DateAndFormatters.formatCurrency(summary.quota.targetGlobalSales),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = MonoFontFamily,
                        color = BrandNavy
                    )
                }
            }

            LinearProgressIndicator(
                progress = { summary.progressRatio.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            Surface(
                color = statusBg.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val remainingText = if (summary.isGlobalTargetMet) {
                        "Meta superada: +${DateAndFormatters.formatCurrency(summary.surplusGlobalSales)}"
                    } else {
                        "Saldo pendiente: ${DateAndFormatters.formatCurrency(summary.remainingGlobalSales)}"
                    }
                    Text(
                        text = remainingText,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (summary.isGlobalTargetMet) SuccessGreen else BrandNavy
                    )
                    val projDeltaLabel = if (summary.projectedDelta >= 0.0) {
                        "+${DateAndFormatters.formatCurrency(summary.projectedDelta)} sobre meta"
                    } else {
                        "-${DateAndFormatters.formatCurrency(abs(summary.projectedDelta))} vs. meta"
                    }
                    Text(
                        text = "Proyección al cierre (día ${summary.daysElapsed}/${summary.totalCycleDays}, restan ${summary.remainingDays}d): ${DateAndFormatters.formatCurrency(summary.projectedGlobalSales)} ($projDeltaLabel)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = progressColor
                    )
                    Text(
                        text = "Ritmo actual: ${DateAndFormatters.formatCurrency(summary.dailyAverage)}/día · Necesario restante: ${DateAndFormatters.formatCurrency(summary.requiredDailyPace)}/día",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = if (difference >= 0) {
                    "Superó la meta por ${DateAndFormatters.formatCurrency(difference)}"
                } else {
                    "Faltan ${DateAndFormatters.formatCurrency(abs(difference))} para alcanzar la meta"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (difference >= 0) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = BorderSubtle)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CompactSummaryStat(label = "Crédito", value = DateAndFormatters.formatCurrency(summary.totalCreditSales))
                CompactSummaryStat(label = "Clientes", value = "${summary.totalNewCustomers}")
                CompactSummaryStat(label = "App MA", value = "${summary.totalAppMaOps}")
                CompactSummaryStat(label = "Solicitudes", value = "${summary.totalNewRequests}")
            }
        }
    }
}

@Composable
private fun CompactSummaryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = MonoFontFamily,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TodayClosingDetailCard(
    todayEntry: DailySalesEntry?,
    onLoadEntryAttachmentChunks: suspend (DailySalesEntry) -> Result<List<String>>
) {
    val todayDisplay = remember { DateAndFormatters.formatIsoToDisplay(DateAndFormatters.todayIso()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("detail_today_closing_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (todayEntry != null) SuccessGreen.copy(alpha = 0.35f) else BorderSubtle)
    ) {
        if (todayEntry == null) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Hoy · $todayDisplay",
                    style = MaterialTheme.typography.labelMedium,
                    color = WarningAmber
                )
                Text(
                    text = "Aún no se ha registrado el cierre de ventas del día en curso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val timestampDate = (todayEntry.updatedAt ?: todayEntry.createdAt)?.toDate()
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Hoy · $todayDisplay",
                            style = MaterialTheme.typography.titleSmall,
                            color = SuccessGreen
                        )
                    }
                    Text(
                        text = DateAndFormatters.formatCurrency(todayEntry.globalSales),
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = BrandNavy
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Registrado por: ${todayEntry.formattedAuthorLabel} · ${DateAndFormatters.formatTimestamp(timestampDate)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CompactSummaryStat("Crédito", DateAndFormatters.formatCurrency(todayEntry.creditSales))
                    CompactSummaryStat("Clientes", "${todayEntry.newCustomers}")
                    CompactSummaryStat("App MA", "${todayEntry.appMaOperations}")
                    CompactSummaryStat("Solicitudes", "${todayEntry.newCustomerRequests}")
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Notas del cierre",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = todayEntry.notes.ifBlank { "Sin notas registradas." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                EntryAttachmentPreviewBlock(
                    entry = todayEntry,
                    onLoadEntryAttachmentChunks = onLoadEntryAttachmentChunks
                )
            }
        }
    }
}

@Composable
private fun PeriodHistoryClosingCard(
    entry: DailySalesEntry,
    isToday: Boolean,
    onLoadEntryAttachmentChunks: suspend (DailySalesEntry) -> Result<List<String>>
) {
    val recordedAt = (entry.updatedAt ?: entry.createdAt)?.toDate()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("detail_history_entry_${entry.dateString}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderSubtle)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarToday,
                        contentDescription = null,
                        tint = BrandNavy,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (isToday) {
                            "${DateAndFormatters.formatIsoToDisplay(entry.dateString)} (Hoy)"
                        } else {
                            DateAndFormatters.formatIsoToDisplay(entry.dateString)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = BrandNavy
                    )
                }
                Text(
                    text = DateAndFormatters.formatCurrency(entry.globalSales),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Registró: ${entry.formattedAuthorLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Crédito: ${DateAndFormatters.formatCurrency(entry.creditSales)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = MonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (recordedAt != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = DateAndFormatters.formatTimestamp(recordedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (entry.notes.isNotBlank()) {
                Text(
                    text = entry.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            EntryAttachmentPreviewBlock(
                entry = entry,
                onLoadEntryAttachmentChunks = onLoadEntryAttachmentChunks
            )
        }
    }
}

@Composable
private fun EntryAttachmentPreviewBlock(
    entry: DailySalesEntry,
    onLoadEntryAttachmentChunks: suspend (DailySalesEntry) -> Result<List<String>>
) {
    val hasAttachment = !entry.attachmentName.isNullOrBlank() &&
        (!entry.attachmentBase64.isNullOrBlank() || entry.attachmentChunkCount > 0L)
    if (!hasAttachment || entry.attachmentName == null) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showFullImage by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var openError by remember { mutableStateOf<String?>(null) }

    val isImage = entry.attachmentMimeType?.startsWith("image/") == true
    val inlineBitmap = remember(entry.attachmentBase64, isImage) {
        if (isImage && !entry.attachmentBase64.isNullOrBlank()) {
            ImageCompressor.decodeBase64ToImageBitmap(entry.attachmentBase64)
        } else {
            null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (inlineBitmap != null) {
            Image(
                bitmap = inlineBitmap,
                contentDescription = "Foto de evidencia del cierre",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showFullImage = true },
                contentScale = ContentScale.Crop
            )
        }

        OutlinedButton(
            enabled = !isLoading,
            onClick = {
                openError = null
                if (inlineBitmap != null) {
                    showFullImage = true
                } else {
                    isLoading = true
                    scope.launch {
                        val chunksResult = withContext(Dispatchers.IO) {
                            onLoadEntryAttachmentChunks(entry)
                        }
                        isLoading = false
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
                                    runCatching { context.startActivity(viewIntent) }
                                        .onFailure {
                                            openError = "No hay aplicación compatible para abrir este archivo."
                                        }
                                } else {
                                    openError = "No se pudo preparar el archivo adjunto."
                                }
                            },
                            onFailure = { err ->
                                openError = err.localizedMessage ?: "No se pudo cargar el adjunto."
                            }
                        )
                    }
                }
            },
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(15.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            val sizeLabel = if (entry.attachmentSizeBytes > 0L) {
                " (${ImageCompressor.formatFileSize(entry.attachmentSizeBytes.toInt())})"
            } else {
                ""
            }
            Text(
                text = if (inlineBitmap != null) "Ver foto · ${entry.attachmentName}" else "${entry.attachmentName}$sizeLabel",
                style = MaterialTheme.typography.labelSmall
            )
        }

        if (openError != null) {
            Text(
                text = openError ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = BrandRed
            )
        }
    }

    if (showFullImage && inlineBitmap != null) {
        AlertDialog(
            onDismissRequest = { showFullImage = false },
            title = { Text(entry.attachmentName) },
            text = {
                Image(
                    bitmap = inlineBitmap,
                    contentDescription = "Foto de evidencia ampliada",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            },
            confirmButton = {
                TextButton(onClick = { showFullImage = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
fun AssignBranchQuotaDialog(
    initialBranch: String,
    activeQuotas: List<BiweeklyQuota>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        startDate: String,
        endDate: String,
        totalDays: Long,
        globalSales: Double,
        creditSales: Double,
        newCustomers: Long,
        appMaOps: Long,
        newRequests: Long,
        branchName: String,
        applyToAllIndividual: Boolean
    ) -> Unit
) {
    val branchOptions = remember { listOf(BranchOptions.ALL_BRANCHES_OPTION) + BranchOptions.ALL_BRANCHES }
    val normalizedInitial = if (initialBranch in branchOptions) initialBranch else BranchOptions.ALL_BRANCHES_OPTION
    var selectedBranch by remember { mutableStateOf(normalizedInitial) }

    val todayIso = DateAndFormatters.todayIso()
    val activeCycle = remember(activeQuotas, todayIso) {
        ProjectionCalculator.resolveActiveCycleInfo(activeQuotas, todayIso)
    }
    val defaultRange = activeCycle.startDate to activeCycle.endDate
    val currentPeriodId = activeCycle.periodId
    val defaultDays = activeCycle.totalDays.toLong().coerceIn(1L, 31L)

    var title by remember { mutableStateOf(DateAndFormatters.defaultBiweeklyTitle()) }
    var globalTarget by remember { mutableStateOf("250000") }
    var creditTarget by remember { mutableStateOf("150000") }
    var newCustomersTarget by remember { mutableStateOf("45") }
    var appMaTarget by remember { mutableStateOf("60") }
    var requestsTarget by remember { mutableStateOf("75") }
    var applyToAllIndividual by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedBranch, activeQuotas) {
        val (resolvedQuota, _) = resolveQuotaForBranch(selectedBranch, activeQuotas, currentPeriodId)
        title = resolvedQuota?.title?.takeIf { it.isNotBlank() } ?: DateAndFormatters.defaultBiweeklyTitle()
        globalTarget = (resolvedQuota?.targetGlobalSales ?: 250000.0).toLong().toString()
        creditTarget = (resolvedQuota?.targetCreditSales ?: 150000.0).toLong().toString()
        newCustomersTarget = (resolvedQuota?.targetNewCustomers ?: 45L).toString()
        appMaTarget = (resolvedQuota?.targetAppMaOperations ?: 60L).toString()
        requestsTarget = (resolvedQuota?.targetNewCustomerRequests ?: 75L).toString()
        if (selectedBranch != BranchOptions.ALL_BRANCHES_OPTION) {
            applyToAllIndividual = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Asignar Meta Quincenal") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Quincena actual: ${DateAndFormatters.formatIsoToShort(defaultRange.first)} – ${DateAndFormatters.formatIsoToShort(defaultRange.second)} · $currentPeriodId",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandNavy,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }

                Text(
                    text = "Selecciona sucursal",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    branchOptions.forEach { branch ->
                        FilterChip(
                            selected = selectedBranch == branch,
                            onClick = { selectedBranch = branch },
                            label = { Text(branch, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandNavy,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("quota_branch_chip_$branch")
                        )
                    }
                }

                if (selectedBranch == BranchOptions.ALL_BRANCHES_OPTION) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Aplicar a cada sucursal",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = BrandNavy
                                )
                                Text(
                                    text = "Asigna este monto a cada una de las ${BranchOptions.ALL_BRANCHES.size} sucursales sin modificar la cuota general",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = applyToAllIndividual,
                                onCheckedChange = { applyToAllIndividual = it },
                                modifier = Modifier.testTag("apply_quota_all_branches_switch")
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    label = { Text("Nombre de la quincena") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = globalTarget,
                    onValueChange = { globalTarget = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Meta Venta global ($ MXN)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quota_input_global_sales")
                )
                OutlinedTextField(
                    value = creditTarget,
                    onValueChange = { creditTarget = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Meta Venta a crédito ($ MXN)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newCustomersTarget,
                    onValueChange = { newCustomersTarget = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Meta Clientes nuevos (unidades)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = appMaTarget,
                    onValueChange = { appMaTarget = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Meta Operaciones app MA (unidades)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = requestsTarget,
                    onValueChange = { requestsTarget = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Meta Solicitudes cliente nuevo (uds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
                    val g = globalTarget.toDoubleOrNull()
                    val c = creditTarget.toDoubleOrNull()
                    val nc = newCustomersTarget.toLongOrNull()
                    val ma = appMaTarget.toLongOrNull()
                    val req = requestsTarget.toLongOrNull()
                    if (title.trim().length < 3 || g == null || g <= 0 || c == null || nc == null || ma == null || req == null) {
                        errorMsg = "Revisa el nombre y todos los objetivos numéricos."
                    } else {
                        errorMsg = null
                        onSave(
                            title.trim(),
                            defaultRange.first,
                            defaultRange.second,
                            defaultDays,
                            g,
                            c,
                            nc,
                            ma,
                            req,
                            selectedBranch,
                            applyToAllIndividual && selectedBranch == BranchOptions.ALL_BRANCHES_OPTION
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                modifier = Modifier.testTag("save_branch_quota_button")
            ) {
                Text("Guardar Meta")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Cancelar")
            }
        }
    )
}
