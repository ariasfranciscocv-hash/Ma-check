package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.example.data.model.BiweeklyQuota
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandRedSoft
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenSoft
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberSoft

@Composable
fun TeamAdminScreen(
    currentUserId: String,
    teamUsers: List<UserProfile>,
    activeQuota: BiweeklyQuota?,
    isBusy: Boolean,
    onToggleAdmin: (UserProfile, Boolean) -> Unit,
    onSeedChecklist: (UserProfile) -> Unit,
    onConfirmBranchResetForQuota: (branchName: String, branchUsers: List<UserProfile>) -> Unit
) {
    var pendingResetTarget by remember { mutableStateOf<UserProfile?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Sucursales y Roles del Sistema",
                style = MaterialTheme.typography.titleLarge
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
}
