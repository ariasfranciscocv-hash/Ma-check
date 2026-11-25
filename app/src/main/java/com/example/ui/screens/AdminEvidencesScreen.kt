package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.data.model.TaskEvidence
import com.example.data.model.UiState
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.utils.DateAndFormatters
import com.example.utils.ImageCompressor

@Composable
fun AdminEvidencesScreen(
    evidencesState: UiState<List<TaskEvidence>>
) {
    var selectedEvidence by remember { mutableStateOf<TaskEvidence?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AdminPanelSettings,
                    contentDescription = null,
                    tint = BrandNavy
                )
                Column {
                    Text(
                        text = "Auditoría Fotográfica Privada",
                        style = MaterialTheme.typography.titleSmall,
                        color = BrandNavy
                    )
                    Text(
                        text = "Acceso exclusivo de administradores. Oculto para empleados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        when (evidencesState) {
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
                    Text(text = evidencesState.message, color = BrandRed)
                }
            }

            is UiState.Success -> {
                if (evidencesState.data.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Aún no hay fotografías enviadas",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Cuando las sucursales documenten sus tareas con foto aparecerán aquí.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(evidencesState.data, key = { it.id }) { evidence ->
                            val bitmap = remember(evidence.imageBase64) {
                                ImageCompressor.decodeBase64ToImageBitmap(evidence.imageBase64)
                            }
                            Card(
                                onClick = { selectedEvidence = evidence },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = "Evidencia de ${evidence.taskTitle}",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp)
                                                .clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }
                                    Text(
                                        text = evidence.taskTitle,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = evidence.uploaderName,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = BrandNavy
                                        )
                                        Text(
                                            text = DateAndFormatters.formatTimestamp(evidence.capturedAt?.toDate()),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

    selectedEvidence?.let { evidence ->
        EvidencePreviewDialog(
            evidence = evidence,
            onDismiss = { selectedEvidence = null }
        )
    }
}

@Composable
fun EvidencePreviewDialog(
    evidence: TaskEvidence,
    onDismiss: () -> Unit
) {
    val bitmap = remember(evidence.imageBase64) {
        ImageCompressor.decodeBase64ToImageBitmap(evidence.imageBase64)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(evidence.taskTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Evidencia fotográfica ampliada",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
                Text(
                    text = "Enviada por: ${evidence.uploaderName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = BrandNavy
                )
                Text(
                    text = "Fecha: ${DateAndFormatters.formatTimestamp(evidence.capturedAt?.toDate())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
