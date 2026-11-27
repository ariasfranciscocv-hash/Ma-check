package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.BiweeklyQuota
import com.example.data.model.BranchOptions
import com.example.data.model.BranchSalesSummary
import com.example.data.model.DailySalesEntry
import com.example.data.model.EmployeeIdentity
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.AssignBranchQuotaDialog
import com.example.ui.components.BranchSalesDetailDialog
import com.example.ui.components.BranchSalesTrackingSection
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandRedSoft
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenSoft
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberSoft

@Composable
fun TeamAdminScreen(
    currentUserId: String,
    teamUsers: List<UserProfile>,
    employeeIdentities: List<EmployeeIdentity> = emptyList(),
    activeQuota: BiweeklyQuota?,
    activeQuotas: List<BiweeklyQuota> = emptyList(),
    branchSalesSummaries: List<BranchSalesSummary> = emptyList(),
    isBusy: Boolean,
    onOpenQuotasTab: (() -> Unit)? = null,
    onReleaseEmployeeBranch: (EmployeeIdentity) -> Unit = {},
    onDeleteEmployeeIdentity: (EmployeeIdentity) -> Unit = {},
    onToggleAdmin: (UserProfile, Boolean) -> Unit,
    onSeedChecklist: (UserProfile) -> Unit,
    onConfirmBranchResetForQuota: (branchName: String, branchUsers: List<UserProfile>) -> Unit,
    onLoadEntryAttachmentChunks: suspend (DailySalesEntry) -> Result<List<String>> = { Result.success(emptyList()) },
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
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, onDone -> onDone() }
) {
    var pendingResetTarget by remember { mutableStateOf<UserProfile?>(null) }
    var quotaTargetBranch by remember { mutableStateOf<String?>(null) }
    var selectedBranchDetailName by remember { mutableStateOf<String?>(null) }
    var identityPendingDelete by remember { mutableStateOf<EmployeeIdentity?>(null) }
    var deleteConfirmationStep by remember { mutableIntStateOf(1) }
    var deleteConfirmationChecked by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Metas Quincenales por Sucursal",
                            style = MaterialTheme.typography.titleMedium,
                            color = BrandNavy
                        )
                        Text(
                            text = "Asigna metas para la quincena actual y consulta cierres diarios.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (onOpenQuotasTab != null) {
                                onOpenQuotasTab()
                            } else {
                                quotaTargetBranch = BranchOptions.ALL_BRANCHES_OPTION
                            }
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("team_assign_branch_quota_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EditCalendar,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Asignar Meta", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        if (branchSalesSummaries.isNotEmpty()) {
            item {
                BranchSalesTrackingSection(
                    summaries = branchSalesSummaries,
                    isAdmin = true,
                    onOpenBranchDetail = { detail -> selectedBranchDetailName = detail.branchName },
                    onConfigureBranchQuota = { branchName ->
                        if (onOpenQuotasTab != null) {
                            onOpenQuotasTab()
                        } else {
                            quotaTargetBranch = branchName
                        }
                    }
                )
            }
        }

        item {
            Column(
                modifier = Modifier.padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Badge,
                        contentDescription = null,
                        tint = BrandNavy
                    )
                    Text(
                        text = "Fichas de Empleados",
                        style = MaterialTheme.typography.titleLarge,
                        color = BrandNavy
                    )
                }
                Text(
                    text = "Control exclusivo del administrador: libera la sucursal de un empleado o elimina su ficha por completo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (employeeIdentities.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Aún no hay empleados registrados con Ficha de Identificación.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(employeeIdentities, key = { "identity_${it.userId.ifBlank { it.id }}" }) { identity ->
                EmployeeIdentityAdminCard(
                    identity = identity,
                    isBusy = isBusy,
                    onReleaseBranch = { onReleaseEmployeeBranch(identity) },
                    onRequestDelete = {
                        identityPendingDelete = identity
                        deleteConfirmationStep = 1
                        deleteConfirmationChecked = false
                    }
                )
            }
        }

        item {
            Text(
                text = "Sucursales y Roles del Sistema",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = "Gestiona permisos, asigna el check list base y confirma individualmente el reinicio de tareas por sucursal al actualizar la cuota quincenal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(teamUsers, key = { it.userId }) { user ->
            val userIsAdmin = user.parsedRole == UserRole.ADMIN
            val isSelf = user.userId == currentUserId
            val quotaConfirmed = activeQuota != null && user.confirmedQuotaId == activeQuota.id

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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storefront,
                                contentDescription = null,
                                tint = BrandNavy
                            )
                            Column {
                                Text(
                                    text = user.effectiveBranchLabel,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = user.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            color = if (userIsAdmin) BrandRedSoft else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = user.parsedRole.labelEs,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (userIsAdmin) BrandRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Rol Administrador",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = userIsAdmin,
                                enabled = !isBusy && !isSelf,
                                onCheckedChange = { makeAdmin -> onToggleAdmin(user, makeAdmin) }
                            )
                        }

                        if (!userIsAdmin) {
                            OutlinedButton(
                                onClick = { onSeedChecklist(user) },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.PlaylistAddCheck,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check List Base", style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            Text(
                                text = "Sin checklist diario",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (activeQuota != null) {
                        Surface(
                            color = if (quotaConfirmed) SuccessGreenSoft else WarningAmberSoft,
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (quotaConfirmed) Icons.Filled.CheckCircle else Icons.Filled.RestartAlt,
                                        contentDescription = null,
                                        tint = if (quotaConfirmed) SuccessGreen else WarningAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (quotaConfirmed) {
                                            "Lista confirmada · ${activeQuota.title}"
                                        } else {
                                            "Nueva cuota · Confirmar reinicio"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (quotaConfirmed) SuccessGreen else WarningAmber
                                    )
                                }

                                if (!quotaConfirmed) {
                                    Button(
                                        onClick = { pendingResetTarget = user },
                                        enabled = !isBusy,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = BrandRed,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("team_confirm_reset_${user.userId}")
                                    ) {
                                        Text("Confirmar", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val targetUser = pendingResetTarget
    if (targetUser != null && activeQuota != null) {
        AlertDialog(
            onDismissRequest = { if (!isBusy) pendingResetTarget = null },
            title = { Text("Confirmar reinicio por sucursal") },
            text = {
                Text(
                    "¿Restablecer la lista de tareas de ${targetUser.effectiveBranchLabel} para la cuota \"${activeQuota.title}\"? Se eliminarán las tareas anteriores de esta sucursal y se generará su nuevo check list base."
                )
            },
            confirmButton = {
                Button(
                    enabled = !isBusy,
                    onClick = {
                        onConfirmBranchResetForQuota(targetUser.effectiveBranchLabel, listOf(targetUser))
                        pendingResetTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) {
                    Text("Reiniciar sucursal")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingResetTarget = null }, enabled = !isBusy) {
                    Text("Cancelar")
                }
            }
        )
    }

    val activeBranchDetail = selectedBranchDetailName?.let { name ->
        branchSalesSummaries.firstOrNull { it.branchName == name }
    }
    if (activeBranchDetail != null) {
        BranchSalesDetailDialog(
            summary = activeBranchDetail,
            isAdmin = true,
            onDismiss = { selectedBranchDetailName = null },
            onEditQuota = { branchName -> quotaTargetBranch = branchName },
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

    val deleteTarget = identityPendingDelete
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isBusy) {
                    identityPendingDelete = null
                    deleteConfirmationStep = 1
                    deleteConfirmationChecked = false
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = BrandRed
                )
            },
            title = {
                Text(
                    text = if (deleteConfirmationStep == 1) {
                        "Eliminar ficha por completo (1/2)"
                    } else {
                        "Confirmación final definitiva (2/2)"
                    },
                    color = BrandRed,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (deleteConfirmationStep == 1) {
                        Text(
                            text = "Vas a eliminar permanentemente la ficha de identificación de ${deleteTarget.fullName} (Nómina ${deleteTarget.displayPayroll})."
                        )
                        Text(
                            text = "Al eliminarla, el número de Nómina quedará libre y en su próximo inicio de sesión el empleado deberá llenar los 3 campos desde cero.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Confirma que deseas borrar de forma irreversible la ficha de ${deleteTarget.fullName} (Nómina ${deleteTarget.displayPayroll}, ${deleteTarget.branchName ?: "sin sucursal"})."
                        )
                        Surface(
                            color = BrandRedSoft,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = deleteConfirmationChecked,
                                    onCheckedChange = { deleteConfirmationChecked = it },
                                    modifier = Modifier.testTag("confirm_delete_identity_checkbox")
                                )
                                Text(
                                    text = "Sí, eliminar ficha y liberar Nómina ${deleteTarget.displayPayroll}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = BrandRed
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (deleteConfirmationStep == 1) {
                    Button(
                        enabled = !isBusy,
                        onClick = { deleteConfirmationStep = 2 },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        modifier = Modifier.testTag("delete_identity_step1_button")
                    ) {
                        Text("Continuar")
                    }
                } else {
                    Button(
                        enabled = !isBusy && deleteConfirmationChecked,
                        onClick = {
                            onDeleteEmployeeIdentity(deleteTarget)
                            identityPendingDelete = null
                            deleteConfirmationStep = 1
                            deleteConfirmationChecked = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        modifier = Modifier.testTag("delete_identity_step2_button")
                    ) {
                        Text("Eliminar definitivamente")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        identityPendingDelete = null
                        deleteConfirmationStep = 1
                        deleteConfirmationChecked = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun EmployeeIdentityAdminCard(
    identity: EmployeeIdentity,
    isBusy: Boolean,
    onReleaseBranch: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val hasBranch = identity.hasAssignedBranch

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("employee_identity_admin_card_${identity.userId}")
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
                        text = identity.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = identity.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "Nómina ${identity.displayPayroll}",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = MonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = BrandNavy,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Surface(
                color = if (hasBranch) SuccessGreenSoft else WarningAmberSoft,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storefront,
                        contentDescription = null,
                        tint = if (hasBranch) SuccessGreen else WarningAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (hasBranch) {
                            "Sucursal asignada: ${identity.branchName}"
                        } else {
                            "Sucursal liberada · Pendiente de selección al reingresar"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (hasBranch) SuccessGreen else WarningAmber
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReleaseBranch,
                    enabled = !isBusy && hasBranch,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("release_branch_button_${identity.userId}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Liberar sucursal",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                OutlinedButton(
                    onClick = onRequestDelete,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("delete_identity_button_${identity.userId}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteForever,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Eliminar ficha",
                        style = MaterialTheme.typography.labelSmall,
                        color = BrandRed
                    )
                }
            }
        }
    }
}
