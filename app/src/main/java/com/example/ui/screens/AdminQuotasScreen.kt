package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ActiveBiweeklyCycleInfo
import com.example.data.model.BiweeklyCycleArchive
import com.example.data.model.BiweeklyQuota
import com.example.data.model.BranchOptions
import com.example.data.model.BranchSalesSummary
import com.example.data.model.ProjectionPace
import com.example.data.model.SalesMetricType
import com.example.data.model.SalesProjectionSummary
import com.example.data.model.resolveQuotaForBranch
import com.example.data.repository.ProjectionCalculator
import com.example.ui.components.AdminCycleClosureControlCard
import com.example.ui.components.ClosedCyclesHistorySection
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenSoft
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberSoft
import com.example.utils.DateAndFormatters
import kotlin.math.roundToInt

/**
 * Fixed, always-visible administrator screen for configuring:
 * 1) The administrator's independent general supervision quota (`Todas las Sucursales`)
 *    whose remaining balance (`Saldo pendiente`) automatically deducts cumulative daily sales from all branches.
 * 2) Every individual store/branch biweekly quota directly without accordions or dropdowns.
 */
@Composable
fun AdminQuotasScreen(
    activeQuotas: List<BiweeklyQuota>,
    adminGeneralSummary: SalesProjectionSummary?,
    branchSalesSummaries: List<BranchSalesSummary>,
    activeCycleInfo: ActiveBiweeklyCycleInfo = ProjectionCalculator.resolveActiveCycleInfo(activeQuotas),
    cycleArchives: List<BiweeklyCycleArchive> = emptyList(),
    isBusy: Boolean,
    onCloseAndResetBiweeklyCycle: (onDone: () -> Unit) -> Unit = { it() },
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
    val cycleStart = activeCycleInfo.startDate
    val cycleEnd = activeCycleInfo.endDate
    val currentPeriodId = activeCycleInfo.periodId
    val totalDays = activeCycleInfo.totalDays.toLong().coerceIn(1L, 31L)

    val generalQuota = activeCycleInfo.generalQuota

    val branchSummaryByName = remember(branchSalesSummaries) {
        branchSalesSummaries.associateBy { it.branchName }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_quotas_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = BrandNavy),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Configuración Directa de Cuotas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (activeCycleInfo.isCycleEnded) {
                            "Ciclo por cerrar: ${DateAndFormatters.formatIsoToShort(cycleStart)} – ${DateAndFormatters.formatIsoToShort(cycleEnd)} ($totalDays días)"
                        } else {
                            "Ciclo activo: ${DateAndFormatters.formatIsoToShort(cycleStart)} – ${DateAndFormatters.formatIsoToShort(cycleEnd)} ($totalDays días)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Text(
                        text = "Tu cuota general de supervisión es independiente y nunca se sobrescribe al editar tiendas. Los acumulados siguen activos hasta que confirmes el cierre del ciclo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.84f)
                    )
                }
            }
        }

        item {
            AdminCycleClosureControlCard(
                cycleInfo = activeCycleInfo,
                adminGeneralSummary = adminGeneralSummary,
                branchSummaries = branchSalesSummaries,
                isBusy = isBusy,
                onConfirmCloseCycle = onCloseAndResetBiweeklyCycle
            )
        }

        item {
            AdminIndependentGeneralQuotaCard(
                generalQuota = generalQuota,
                adminGeneralSummary = adminGeneralSummary,
                cycleStart = cycleStart,
                cycleEnd = cycleEnd,
                totalDays = totalDays,
                isBusy = isBusy,
                onSave = { title, g, c, nc, ma, req, onDone ->
                    onSaveBiweeklyQuota(
                        title,
                        cycleStart,
                        cycleEnd,
                        totalDays,
                        g,
                        c,
                        nc,
                        ma,
                        req,
                        BranchOptions.ALL_BRANCHES_OPTION,
                        false,
                        onDone
                    )
                }
            )
        }

        item {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Cuotas Individuales por Sucursal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandNavy
                )
                Text(
                    text = "Captura o actualiza directamente la meta de cada tienda (${BranchOptions.ALL_BRANCHES.size} sucursales visibles).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(
            items = BranchOptions.ALL_BRANCHES,
            key = { it }
        ) { branchName ->
            val (resolvedQuota, hasCustomQuota) = remember(branchName, activeQuotas, currentPeriodId) {
                resolveQuotaForBranch(branchName, activeQuotas, currentPeriodId)
            }
            val effectiveQuota = resolvedQuota ?: generalQuota
            val branchSummary = branchSummaryByName[branchName]

            BranchQuotaFixedCard(
                branchName = branchName,
                effectiveQuota = effectiveQuota,
                hasCustomQuota = hasCustomQuota,
                branchSummary = branchSummary,
                isBusy = isBusy,
                onSave = { g, c, nc, ma, req, onDone ->
                    val branchTitle = generalQuota.title.trim().takeIf { it.length in 3..80 }
                        ?: DateAndFormatters.defaultBiweeklyTitle()
                    onSaveBiweeklyQuota(
                        branchTitle,
                        cycleStart,
                        cycleEnd,
                        totalDays,
                        g,
                        c,
                        nc,
                        ma,
                        req,
                        branchName,
                        false,
                        onDone
                    )
                }
            )
        }

        item {
            ClosedCyclesHistorySection(
                archives = cycleArchives,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun AdminIndependentGeneralQuotaCard(
    generalQuota: BiweeklyQuota,
    adminGeneralSummary: SalesProjectionSummary?,
    cycleStart: String,
    cycleEnd: String,
    totalDays: Long,
    isBusy: Boolean,
    onSave: (
        title: String,
        globalSales: Double,
        creditSales: Double,
        newCustomers: Long,
        appMaOperations: Long,
        newCustomerRequests: Long,
        onDone: () -> Unit
    ) -> Unit
) {
    val globalMetric = adminGeneralSummary?.metrics?.firstOrNull { it.metric == SalesMetricType.GLOBAL_SALES }
    val actualGlobal = globalMetric?.actualTotal ?: 0.0
    val targetGlobal = globalMetric?.targetTotal ?: generalQuota.targetGlobalSales
    val remainingGlobal = (targetGlobal - actualGlobal).coerceAtLeast(0.0)
    val surplusGlobal = (actualGlobal - targetGlobal).coerceAtLeast(0.0)
    val progressRatio = if (targetGlobal > 0.0) (actualGlobal / targetGlobal).toFloat().coerceIn(0f, 1f) else 0f
    val pct = if (targetGlobal > 0.0) ((actualGlobal / targetGlobal) * 100.0).roundToInt().coerceAtLeast(0) else 0
    val paceColor = quotaPaceColor(globalMetric?.pace ?: ProjectionPace.BEHIND)

    val syncKey = "${generalQuota.id}_${generalQuota.title}_${generalQuota.targetGlobalSales}_${generalQuota.targetCreditSales}_${generalQuota.targetNewCustomers}_${generalQuota.targetAppMaOperations}_${generalQuota.targetNewCustomerRequests}"
    var titleInput by remember(syncKey) { mutableStateOf(generalQuota.title.ifBlank { DateAndFormatters.defaultBiweeklyTitle() }) }
    var globalInput by remember(syncKey) { mutableStateOf(formatEditableAmount(generalQuota.targetGlobalSales)) }
    var creditInput by remember(syncKey) { mutableStateOf(formatEditableAmount(generalQuota.targetCreditSales)) }
    var customersInput by remember(syncKey) { mutableStateOf(generalQuota.targetNewCustomers.toString()) }
    var appMaInput by remember(syncKey) { mutableStateOf(generalQuota.targetAppMaOperations.toString()) }
    var requestsInput by remember(syncKey) { mutableStateOf(generalQuota.targetNewCustomerRequests.toString()) }
    var errorMsg by remember(syncKey) { mutableStateOf<String?>(null) }
    var savedRecently by remember(syncKey) { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, BrandNavy.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_general_quota_card")
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
                        imageVector = Icons.Filled.AdminPanelSettings,
                        contentDescription = null,
                        tint = BrandNavy,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Cuota General del Administrador",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandNavy
                        )
                        Text(
                            text = "${BranchOptions.ALL_BRANCHES_OPTION} · Independiente",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "$pct% cubierto",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = BrandNavy,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Real acumulado (tiendas)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = DateAndFormatters.formatCurrency(actualGlobal),
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("admin_general_actual_total")
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (surplusGlobal > 0.0) "Meta superada" else "Saldo pendiente",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (surplusGlobal > 0.0) SuccessGreen else BrandRed
                            )
                            Text(
                                text = if (surplusGlobal > 0.0) {
                                    "+${DateAndFormatters.formatCurrency(surplusGlobal)}"
                                } else {
                                    DateAndFormatters.formatCurrency(remainingGlobal)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (surplusGlobal > 0.0) SuccessGreen else BrandRed,
                                modifier = Modifier.testTag("admin_general_remaining_balance")
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = paceColor,
                        trackColor = MaterialTheme.colorScheme.surface,
                        strokeCap = StrokeCap.Round
                    )

                    Text(
                        text = "Ciclo ${DateAndFormatters.formatIsoToShort(cycleStart)} – ${DateAndFormatters.formatIsoToShort(cycleEnd)} ($totalDays d) · Cuota independiente: ${DateAndFormatters.formatCurrency(targetGlobal)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = titleInput,
                onValueChange = {
                    titleInput = it.take(80)
                    errorMsg = null
                    savedRecently = false
                },
                label = { Text("Nombre de la quincena") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = globalInput,
                    onValueChange = {
                        globalInput = it.filter { ch -> ch.isDigit() || ch == '.' }
                        errorMsg = null
                        savedRecently = false
                    },
                    label = { Text("Venta global ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("admin_general_quota_input_global")
                )
                OutlinedTextField(
                    value = creditInput,
                    onValueChange = {
                        creditInput = it.filter { ch -> ch.isDigit() || ch == '.' }
                        errorMsg = null
                        savedRecently = false
                    },
                    label = { Text("Venta crédito ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customersInput,
                    onValueChange = {
                        customersInput = it.filter { ch -> ch.isDigit() }
                        errorMsg = null
                        savedRecently = false
                    },
                    label = { Text("Clientes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = appMaInput,
                    onValueChange = {
                        appMaInput = it.filter { ch -> ch.isDigit() }
                        errorMsg = null
                        savedRecently = false
                    },
                    label = { Text("App MA") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = requestsInput,
                    onValueChange = {
                        requestsInput = it.filter { ch -> ch.isDigit() }
                        errorMsg = null
                        savedRecently = false
                    },
                    label = { Text("Solicitudes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            if (errorMsg != null) {
                Text(
                    text = errorMsg.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandRed
                )
            } else if (savedRecently) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Cuota general del administrador guardada.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuccessGreen
                    )
                }
            }

            Button(
                onClick = {
                    val cleanTitle = titleInput.trim()
                    val g = globalInput.toDoubleOrNull()
                    val c = creditInput.toDoubleOrNull()
                    val nc = customersInput.toLongOrNull()
                    val ma = appMaInput.toLongOrNull()
                    val req = requestsInput.toLongOrNull()
                    if (cleanTitle.length < 3 || g == null || g <= 0.0 || c == null || nc == null || ma == null || req == null) {
                        errorMsg = "Ingresa un nombre válido y montos numéricos mayores a 0 en Venta global."
                    } else {
                        errorMsg = null
                        onSave(cleanTitle, g, c, nc, ma, req) {
                            savedRecently = true
                        }
                    }
                },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_general_quota_save_button")
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardar Cuota General")
            }
        }
    }
}

@Composable
private fun BranchQuotaFixedCard(
    branchName: String,
    effectiveQuota: BiweeklyQuota,
    hasCustomQuota: Boolean,
    branchSummary: BranchSalesSummary?,
    isBusy: Boolean,
    onSave: (
        globalSales: Double,
        creditSales: Double,
        newCustomers: Long,
        appMaOperations: Long,
        newCustomerRequests: Long,
        onDone: () -> Unit
    ) -> Unit
) {
    val actualGlobal = branchSummary?.totalGlobalSales ?: 0.0
    val targetGlobal = effectiveQuota.targetGlobalSales
    val remaining = (targetGlobal - actualGlobal).coerceAtLeast(0.0)
    val surplus = (actualGlobal - targetGlobal).coerceAtLeast(0.0)
    val ratio = if (targetGlobal > 0.0) (actualGlobal / targetGlobal).toFloat().coerceIn(0f, 1f) else 0f
    val pct = if (targetGlobal > 0.0) ((actualGlobal / targetGlobal) * 100.0).roundToInt().coerceAtLeast(0) else 0
    val paceColor = quotaPaceColor(branchSummary?.pace ?: ProjectionPace.BEHIND)

    val syncKey = "${branchName}_${hasCustomQuota}_${effectiveQuota.id}_${effectiveQuota.targetGlobalSales}_${effectiveQuota.targetCreditSales}_${effectiveQuota.targetNewCustomers}_${effectiveQuota.targetAppMaOperations}_${effectiveQuota.targetNewCustomerRequests}"
    var globalInput by remember(syncKey) { mutableStateOf(formatEditableAmount(effectiveQuota.targetGlobalSales)) }
    var creditInput by remember(syncKey) { mutableStateOf(formatEditableAmount(effectiveQuota.targetCreditSales)) }
    var customersInput by remember(syncKey) { mutableStateOf(effectiveQuota.targetNewCustomers.toString()) }
    var appMaInput by remember(syncKey) { mutableStateOf(effectiveQuota.targetAppMaOperations.toString()) }
    var requestsInput by remember(syncKey) { mutableStateOf(effectiveQuota.targetNewCustomerRequests.toString()) }
    var errorMsg by remember(syncKey) { mutableStateOf<String?>(null) }
    var savedRecently by remember(syncKey) { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fixed_branch_quota_card_$branchName")
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
                            text = branchName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = BrandNavy
                        )
                        Text(
                            text = if (hasCustomQuota) "Cuota propia asignada" else "Sin cuota propia (usa referencia general)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasCustomQuota) SuccessGreen else WarningAmber
                        )
                    }
                }
                Surface(
                    color = if (hasCustomQuota) SuccessGreenSoft else WarningAmberSoft,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (hasCustomQuota) SuccessGreen else WarningAmber,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Real: ${DateAndFormatters.formatCurrency(actualGlobal)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = MonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (surplus > 0.0) {
                        "Superada: +${DateAndFormatters.formatCurrency(surplus)}"
                    } else {
                        "Saldo pendiente: ${DateAndFormatters.formatCurrency(remaining)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = if (surplus > 0.0) SuccessGreen else BrandNavy
                )
            }

            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(50)),
                color = paceColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            HorizontalDivider(color = BorderSubtle)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = globalInput,
                    onValueChange = {
                        globalInput = it.filter { ch -> ch.isDigit() || ch == '.' }
                        errorMsg = null
                        savedRecently = false
                    },
                    label = { Text("Venta global ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("branch_quota_input_global_$branchName")
                )
                OutlinedTextField(
                    value = creditInput,
                    onValueChange = {
                        creditInput = it.filter { ch -> ch.isDigit() || ch == '.' }
                        errorMsg = null
                        savedRecently = false
                    },
                    label = { Text("Venta crédito ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("branch_quota_input_credit_$branchName")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customersInput,
                    onValueChange = {
                        customersInput = it.filter { ch -> ch.isDigit() }
                        errorMsg = null
                        savedRecently = false
                    },
                    label = { Text("Clientes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = appMaInput,
                    onValueChange = {
                        appMaInput = it.filter { ch -> ch.isDigit() }
                        errorMsg = null
                        savedRecently = false
                    },
                    label = { Text("App MA") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = requestsInput,
                    onValueChange = {
                        requestsInput = it.filter { ch -> ch.isDigit() }
                        errorMsg = null
                        savedRecently = false
                    },
                    label = { Text("Solicitudes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            if (errorMsg != null) {
                Text(
                    text = errorMsg.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandRed
                )
            } else if (savedRecently) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Cuota de $branchName guardada.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SuccessGreen
                    )
                }
            }

            Button(
                onClick = {
                    val g = globalInput.toDoubleOrNull()
                    val c = creditInput.toDoubleOrNull()
                    val nc = customersInput.toLongOrNull()
                    val ma = appMaInput.toLongOrNull()
                    val req = requestsInput.toLongOrNull()
                    if (g == null || g <= 0.0 || c == null || nc == null || ma == null || req == null) {
                        errorMsg = "Revisa los valores; Venta global debe ser mayor a 0."
                    } else {
                        errorMsg = null
                        onSave(g, c, nc, ma, req) {
                            savedRecently = true
                        }
                    }
                },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_branch_quota_button_$branchName")
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Guardar cuota · $branchName")
            }
        }
    }
}

private fun quotaPaceColor(pace: ProjectionPace): Color = when (pace) {
    ProjectionPace.ON_TRACK -> SuccessGreen
    ProjectionPace.MODERATE_RISK -> WarningAmber
    ProjectionPace.BEHIND -> BrandRed
}

private fun formatEditableAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
