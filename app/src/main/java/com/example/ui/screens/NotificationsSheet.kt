package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.TaskNotification
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.ui.theme.BrandRedSoft
import com.example.utils.DateAndFormatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSheet(
    notifications: List<TaskNotification>,
    onAcknowledge: (String) -> Unit,
    onOpenTasks: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Notificaciones de Tareas",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = {
                    onDismiss()
                    onOpenTasks()
                }) {
                    Text("Ir a Tareas")
                }
            }

            if (notifications.isEmpty()) {
                Text(
                    text = "No tienes notificaciones de tareas extraordinarias por el momento.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(notifications, key = { it.id }) { notif ->
                        Surface(
                            onClick = {
                                if (!notif.acknowledged) onAcknowledge(notif.id)
                                onDismiss()
                                onOpenTasks()
                            },
                            color = if (notif.acknowledged) MaterialTheme.colorScheme.surfaceVariant
                            else BrandRedSoft.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (notif.acknowledged) Icons.Filled.Done else Icons.Filled.Bolt,
                                    contentDescription = null,
                                    tint = if (notif.acknowledged) BrandNavy else BrandRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = notif.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = notif.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = DateAndFormatters.formatTimestamp(notif.createdAt?.toDate()),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BrandNavy
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
