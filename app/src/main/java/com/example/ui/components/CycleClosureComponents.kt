package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ActiveBiweeklyCycleInfo
import com.example.data.model.BiweeklyCycleArchive
import com.example.data.model.BranchOptions
import com.example.data.model.BranchSalesSummary
import com.example.data.model.SalesMetricType
import com.example.data.model.SalesProjectionSummary
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
import kotlin.math.roundToInt

/**
 * Administrator control card displayed on the Dashboard and Quotas screen to explicitly confirm
 * closing the active biweekly cycle, archive its summary, and reset accumulators to $0.00
 * while keeping configured base quotas intact.
 */
@Composable
fun AdminCycleClosureControlCard(
    cycleInfo: ActiveBiweeklyCycleInfo,
    adminGeneralSummary: SalesProjectionSummary?,
    branchSummaries: List<BranchSalesSummary>,
    isBusy: Boolean,
    onConfirmCloseCycle: (onDone: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    val globalMetric = adminGeneralSummary?.metrics?.firstOrNull { it.metric == SalesMetricType.GLOBAL_SALES }
    val actualGlobal = globalMetric?.actualTotal ?: 0.0
    val targetGlobal = globalMetric?.targetTotal ?: cycleInfo.generalQuota.targetGlobalSales
    val pct = if (targetGlobal > 0.0) ((actualGlobal / targetGlobal) * 100.0).roundToInt().coerceAtLeast(0) else 0

    val containerColor = if (cycleInfo.isCycleEnded) WarningAmberSoft.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surface
    val borderColor = if (cycleInfo.isCycleEnded) WarningAmber.copy(alpha = 0.65f) else BorderSubtle

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (cycleInfo.isCycleEnded) 1.5.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("admin_cycle_closure_card")
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (cycleInfo.isCycleEnded) Icons.Filled.WarningAmber else Icons.Filled.EventAvailable,
                        contentDescription = null,
                        tint = if (cycleInfo.isCycleEnded) WarningAmber else BrandNavy,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = if (cycleInfo.isCycleEnded) {
                                "Ciclo quincenal concluido · Pendiente de cierre"
                            } else {
                                "Control de Cierre de Ciclo Quincenal"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (cycleInfo.isCycleEnded) BrandRed else BrandNavy
                        )
                        Text(
                            text = "${cycleInfo.title} (${DateAndFormatters.formatIsoToShort(cycleInfo.startDate)} – ${DateAndFormatters.formatIsoToShort(cycleInfo.endDate)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    color = if (cycleInfo.isCycleEnded) BrandRedSoft else SuccessGreenSoft,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (cycleInfo.isCycleEnded) "Por cerrar" else "Abierto",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (cycleInfo.isCycleEnded) BrandRed else SuccessGreen,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = if (cycleInfo.isCycleEnded) {
                    "La fecha de corte ya concluyó, pero los acumulados de todas las sucursales y tu cuota general siguen activos hasta que confirmes el cierre."
                } else {
                    "Los acumulados de ventas quincenales no se reinician automáticamente al cambiar de fecha; solo se archivan y reinician a $0.00 cuando confirmes el cierre."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Acumulado general actual",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${DateAndFormatters.formatCurrency(actualGlobal)} / ${DateAndFormatters.formatCurrency(targetGlobal)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = BrandNavy
                        )
                    }
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (pct >= 100) SuccessGreen else BrandNavy
                    )
                }
            }

            Button(
                onClick = { showConfirmDialog = true },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (cycleInfo.isCycleEnded) BrandRed else BrandNavy,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("confirm_close_cycle_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Confirmar Cierre de Ciclo y Reiniciar Acumulados",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    if (showConfirmDialog) {
        ConfirmCloseBiweeklyCycleDialog(
            cycleInfo = cycleInfo,
            adminGeneralSummary = adminGeneralSummary,
            branchSummaries = branchSummaries,
            isBusy = isBusy,
            onDismiss = { if (!isBusy) showConfirmDialog = false },
            onConfirm = {
                onConfirmCloseCycle {
                    showConfirmDialog = false
                }
            }
        )
    }
}

@Composable
fun ConfirmCloseBiweeklyCycleDialog(
    cycleInfo: ActiveBiweeklyCycleInfo,
    adminGeneralSummary: SalesProjectionSummary?,
    branchSummaries: List<BranchSalesSummary>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val globalMetric = adminGeneralSummary?.metrics?.firstOrNull { it.metric == SalesMetricType.GLOBAL_SALES }
    val creditMetric = adminGeneralSummary?.metrics?.firstOrNull { it.metric == SalesMetricType.CREDIT_SALES }
    val customersMetric = adminGeneralSummary?.metrics?.firstOrNull { it.metric == SalesMetricType.NEW_CUSTOMERS }
    val appMaMetric = adminGeneralSummary?.metrics?.firstOrNull { it.metric == SalesMetricType.APP_MA_OPS }
    val requestsMetric = adminGeneralSummary?.metrics?.firstOrNull { it.metric == SalesMetricType.NEW_CUSTOMER_REQUESTS }

    val adminActualGlobal = globalMetric?.actualTotal ?: 0.0
    val adminTargetGlobal = globalMetric?.targetTotal ?: cycleInfo.generalQuota.targetGlobalSales
    val adminPct = if (adminTargetGlobal > 0.0) ((adminActualGlobal / adminTargetGlobal) * 100.0).roundToInt().coerceAtLeast(0) else 0

    val branchSummaryByName = remember(branchSummaries) {
        branchSummaries.associateBy { it.branchName }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Archive,
                    contentDescription = null,
                    tint = BrandRed,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Cerrar y Reiniciar Ciclo Quincenal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandNavy
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Ciclo a cerrar: ${cycleInfo.title} (${DateAndFormatters.formatIsoToShort(cycleInfo.startDate)} – ${DateAndFormatters.formatIsoToShort(cycleInfo.endDate)})",
                    style = MaterialTheme.typography.labelLarge,
                    color = BrandNavy
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "Resumen General del Administrador",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandNavy
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Venta global:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${DateAndFormatters.formatCurrency(adminActualGlobal)} / ${DateAndFormatters.formatCurrency(adminTargetGlobal)} ($adminPct%)",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (adminPct >= 100) SuccessGreen else BrandNavy
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Venta crédito:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${DateAndFormatters.formatCurrency(creditMetric?.actualTotal ?: 0.0)} / ${DateAndFormatters.formatCurrency(creditMetric?.targetTotal ?: cycleInfo.generalQuota.targetCreditSales)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = MonoFontFamily,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Clientes: ${(customersMetric?.actualTotal ?: 0.0).roundToInt()} · App MA: ${(appMaMetric?.actualTotal ?: 0.0).roundToInt()} · Solicitudes: ${(requestsMetric?.actualTotal ?: 0.0).roundToInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Resumen Final por Sucursal",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandNavy
                )

                BranchOptions.ALL_BRANCHES.forEach { branchName ->
                    val branch = branchSummaryByName[branchName]
                    val actual = branch?.totalGlobalSales ?: 0.0
                    val target = branch?.quota?.targetGlobalSales ?: cycleInfo.generalQuota.targetGlobalSales
                    val pct = branch?.completionPercent ?: if (target > 0.0) ((actual / target) * 100.0).roundToInt() else 0
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = branchName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandNavy
                                )
                                Text(
                                    text = "${DateAndFormatters.formatCurrency(actual)} / ${DateAndFormatters.formatCurrency(target)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = MonoFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (pct >= 100) SuccessGreen else BrandNavy
                            )
                        }
                    }
                }

                Surface(
                    color = BrandRedSoft.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Al confirmar: este resumen se archivará en el historial de Firestore, los acumulados se reiniciarán a $0.00 para el nuevo ciclo y tus cuotas base (general y por tienda) permanecerán intactas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandNavy,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                modifier = Modifier.testTag("dialog_confirm_close_cycle_button")
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Confirmar y Reiniciar Ciclo")
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
fun ClosedCyclesHistorySection(
    archives: List<BiweeklyCycleArchive>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = BrandNavy,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Historial de Ciclos Cerrados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = BrandNavy
            )
        }

        if (archives.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Aún no hay ciclos quincenales archivados. Cuando confirmes un cierre de ciclo aparecerá aquí su resumen final.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            archives.forEach { archive ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("closed_cycle_archive_card_${archive.id}")
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = archive.title.ifBlank { "Ciclo Quincenal Cerrado" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandNavy
                                )
                                Text(
                                    text = "${DateAndFormatters.formatIsoToShort(archive.startDate)} – ${DateAndFormatters.formatIsoToShort(archive.endDate)} · Cerrado el ${DateAndFormatters.formatIsoToShort(archive.closedOnDate)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = if (archive.adminCompletionPercent >= 100L) SuccessGreenSoft else MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "${archive.adminCompletionPercent}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = MonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (archive.adminCompletionPercent >= 100L) SuccessGreen else BrandNavy,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "General: ${DateAndFormatters.formatCurrency(archive.adminActualGlobalSales)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandNavy
                            )
                            Text(
                                text = "Meta: ${DateAndFormatters.formatCurrency(archive.adminTargetGlobalSales)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = MonoFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (archive.branchSummaries.isNotEmpty()) {
                            HorizontalDivider(color = BorderSubtle)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                archive.branchSummaries.forEach { branch ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = branch.branchName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${DateAndFormatters.formatCurrency(branch.actualGlobalSales)} / ${DateAndFormatters.formatCurrency(branch.targetGlobalSales)} (${branch.completionPercent}%)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = MonoFontFamily,
                                            color = if (branch.completionPercent >= 100L) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
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
