package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.model.TaskEvidence
import com.example.data.model.TaskEvidenceAttachmentFile
import com.example.data.model.UiState
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.utils.DateAndFormatters
import com.example.utils.ImageCompressor

@Composable
fun AdminEvidencesScreen(
    evidencesState: UiState<List<TaskEvidence>>,
    onLoadEvidenceFiles: suspend (TaskEvidence) -> Result<List<TaskEvidenceAttachmentFile>>
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
                        text = "Auditoría Privada de Evidencias",
                        style = MaterialTheme.typography.titleSmall,
                        color = BrandNavy
                    )
                    Text(
                        text = "Acceso exclusivo de administradores (fotos y archivos hasta 5 por tarea).",
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
                            text = "Aún no hay evidencias enviadas",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Cuando las sucursales completen tareas con fotos o archivos aparecerán aquí.",
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
                            val fileCount = evidence.resolvedFileCount
                            Card(
                                onClick = { selectedEvidence = evidence },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
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
                                    } else {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Description,
                                                    contentDescription = null,
                                                    tint = BrandNavy
                                                )
                                                Text(
                                                    text = evidence.primaryFileName ?: "Documento adjunto",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = BrandNavy,
                                                    modifier = Modifier.weight(1f)
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
                                            text = evidence.taskTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.AttachFile,
                                                    contentDescription = null,
                                                    tint = BrandNavy,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = "$fileCount archivo(s)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = BrandNavy
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = evidence.formattedUploaderLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = BrandNavy,
                                            modifier = Modifier.weight(1f)
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
            onLoadEvidenceFiles = onLoadEvidenceFiles,
            onDismiss = { selectedEvidence = null }
        )
    }
}

@Composable
fun EvidencePreviewDialog(
    evidence: TaskEvidence,
    onLoadEvidenceFiles: suspend (TaskEvidence) -> Result<List<TaskEvidenceAttachmentFile>>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember(evidence.id) { mutableStateOf(true) }
    var loadError by remember(evidence.id) { mutableStateOf<String?>(null) }
    var loadedFiles by remember(evidence.id) { mutableStateOf<List<TaskEvidenceAttachmentFile>>(emptyList()) }
    var openFileError by remember(evidence.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(evidence.id) {
        isLoading = true
        loadError = null
        val result = onLoadEvidenceFiles(evidence)
        isLoading = false
        result.fold(
            onSuccess = { files -> loadedFiles = files },
            onFailure = { err ->
                loadError = err.localizedMessage ?: "No se pudieron cargar los archivos de evidencia."
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(evidence.taskTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Enviada por: ${evidence.formattedUploaderLabel} · ${DateAndFormatters.formatTimestamp(evidence.capturedAt?.toDate())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Cargando archivos adjuntos...", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (loadError != null) {
                    val fallbackBmp = remember(evidence.imageBase64) {
                        ImageCompressor.decodeBase64ToImageBitmap(evidence.imageBase64)
                    }
                    if (fallbackBmp != null) {
                        Image(
                            bitmap = fallbackBmp,
                            contentDescription = "Evidencia fotográfica ampliada",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = loadError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandRed
                        )
                    }
                }

                loadedFiles.forEachIndexed { idx, file ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (file.isImage) Icons.Filled.PhotoCamera else Icons.Filled.Description,
                                    contentDescription = null,
                                    tint = BrandNavy,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${idx + 1}. ${file.fileName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${file.mimeType} · ${ImageCompressor.formatFileSize(file.sizeBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (file.isImage) {
                                val bmp = remember(file.base64Chunks) {
                                    ImageCompressor.decodeBase64ChunksToImageBitmap(file.base64Chunks)
                                }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = "Evidencia ${file.fileName}",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(210.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    openFileError = null
                                    val uri = ImageCompressor.createViewableCacheUriFromChunks(
                                        context = context,
                                        fileName = file.fileName,
                                        base64Chunks = file.base64Chunks
                                    )
                                    if (uri != null) {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, file.mimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching {
                                            context.startActivity(intent)
                                        }.onFailure {
                                            openFileError = "No hay una aplicación instalada para abrir ${file.fileName}."
                                        }
                                    } else {
                                        openFileError = "No se pudo preparar el archivo ${file.fileName}."
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Abrir archivo", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                if (openFileError != null) {
                    Text(
                        text = openFileError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandRed
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
