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
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandRedSoft

@Composable
fun TeamAdminScreen(
    currentUserId: String,
    teamUsers: List<UserProfile>,
    isBusy: Boolean,
    onToggleAdmin: (UserProfile, Boolean) -> Unit,
    onSeedChecklist: (UserProfile) -> Unit
) {
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
                text = "Gestiona permisos de Administrador y asigna el check list diario base a cada sucursal registrada.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(teamUsers, key = { it.userId }) { user ->
            val userIsAdmin = user.parsedRole == UserRole.ADMIN
            val isSelf = user.userId == currentUserId

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
                }
            }
        }
    }
}
