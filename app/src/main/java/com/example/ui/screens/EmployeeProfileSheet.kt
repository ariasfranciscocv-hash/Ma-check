package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.EmployeeBranchParticipationStats
import com.example.data.model.EmployeeIdentity
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenSoft
import com.example.utils.DateAndFormatters
import kotlin.math.roundToInt

@Composable
fun EmployeeProfileTabScreen(
    identity: EmployeeIdentity,
    stats: EmployeeBranchParticipationStats?
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("employee_profile_tab_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            EmployeeProfileContent(
                identity = identity,
                stats = stats
            )
        }
    }
}

@Composable
fun EmployeeProfileModalDialog(
    identity: EmployeeIdentity,
    stats: EmployeeBranchParticipationStats?,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .testTag("employee_profile_modal"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BrandNavy)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Badge,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Column {
                            Text(
                                text = "Mi Ficha de Identificación",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Consulta de solo lectura",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_employee_profile_modal")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cerrar ficha",
                            tint = Color.White
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    EmployeeProfileContent(
                        identity = identity,
                        stats = stats
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cerrar", color = BrandNavy)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmployeeProfileContent(
    identity: EmployeeIdentity,
    stats: EmployeeBranchParticipationStats?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("readonly_identity_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = BrandNavy.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Badge,
                                contentDescription = null,
                                tint = BrandNavy,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Ficha de Identidad",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandNavy
                        )
                        Text(
                            text = "Datos vinculados a tu cuenta",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = BrandNavy,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Solo lectura",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandNavy
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderSubtle)

            IdentityReadOnlyFieldRow(
                icon = Icons.Filled.Person,
                label = "Nombre completo",
                value = identity.fullName.ifBlank { "Sin nombre" },
                valueTag = "readonly_identity_name"
            )
            IdentityReadOnlyFieldRow(
                icon = Icons.Filled.Tag,
                label = "Número de Nómina",
                value = identity.displayPayroll,
                isMonospace = true,
                highlightColor = BrandRed,
                valueTag = "readonly_identity_payroll"
            )
            IdentityReadOnlyFieldRow(
                icon = Icons.Filled.Storefront,
                label = "Sucursal asignada",
                value = identity.branchName ?: "Pendiente de asignar",
                valueTag = "readonly_identity_branch"
            )
            IdentityReadOnlyFieldRow(
                icon = Icons.Filled.Email,
                label = "Correo de acceso",
                value = identity.email.ifBlank { "Sin correo" },
                valueTag = "readonly_identity_email"
            )
        }
    }

    if (stats != null) {
        ParticipationDonutCard(
            identity = identity,
            stats = stats
        )
    }
}

@Composable
private fun IdentityReadOnlyFieldRow(
    icon: ImageVector,
    label: String,
    value: String,
    isMonospace: Boolean = false,
    highlightColor: Color = BrandNavy,
    valueTag: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = highlightColor,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = if (isMonospace) {
                        MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily)
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.Bold,
                    color = highlightColor,
                    modifier = Modifier.testTag(valueTag)
                )
            }
        }
    }
}

@Composable
private fun ParticipationDonutCard(
    identity: EmployeeIdentity,
    stats: EmployeeBranchParticipationStats
) {
    val myColor = SuccessGreen
    val othersColor = BrandNavy
    val pendingColor = Color(0xFFDCE3EE)
    val participationRounded = stats.myParticipationPercent.roundToInt().coerceIn(0, 100)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("employee_participation_donut_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Participación en Tareas · ${stats.branchName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandNavy
                )
                Text(
                    text = "${stats.periodTitle} (${DateAndFormatters.formatIsoToShort(stats.periodStartIso)} – ${DateAndFormatters.formatIsoToShort(stats.periodEndIso)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(196.dp)
                        .testTag("employee_donut_canvas"),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 28.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(
                            x = (size.width - diameter) / 2f,
                            y = (size.height - diameter) / 2f
                        )
                        val arcSize = Size(diameter, diameter)
                        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)

                        // Base track
                        drawArc(
                            color = pendingColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke
                        )

                        if (stats.totalBranchTasks > 0) {
                            val totalF = stats.totalBranchTasks.toFloat()
                            val mySweep = (stats.completedByMyPayroll.toFloat() / totalF) * 360f
                            val othersSweep = (stats.completedByOtherPayrolls.toFloat() / totalF) * 360f

                            var startAngle = -90f
                            if (mySweep > 0f) {
                                drawArc(
                                    color = myColor,
                                    startAngle = startAngle,
                                    sweepAngle = mySweep,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = stroke
                                )
                                startAngle += mySweep
                            }
                            if (othersSweep > 0f) {
                                drawArc(
                                    color = othersColor,
                                    startAngle = startAngle,
                                    sweepAngle = othersSweep,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = stroke
                                )
                            }
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "$participationRounded%",
                            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = MonoFontFamily),
                            fontWeight = FontWeight.ExtraBold,
                            color = BrandNavy,
                            modifier = Modifier.testTag("donut_participation_percent")
                        )
                        Text(
                            text = "Tu aporte",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${stats.completedByMyPayroll} de ${stats.totalBranchTasks}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = SuccessGreen,
                            modifier = Modifier.testTag("donut_participation_ratio")
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderSubtle)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DonutLegendRow(
                    color = myColor,
                    label = "Cumplidas por tu Nómina (${identity.displayPayroll})",
                    count = stats.completedByMyPayroll,
                    total = stats.totalBranchTasks
                )
                DonutLegendRow(
                    color = othersColor,
                    label = "Cumplidas por compañeros de sucursal",
                    count = stats.completedByOtherPayrolls,
                    total = stats.totalBranchTasks
                )
                DonutLegendRow(
                    color = pendingColor,
                    label = "Pendientes en la sucursal",
                    count = stats.pendingTasks,
                    total = stats.totalBranchTasks
                )
            }

            Surface(
                color = SuccessGreenSoft,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (stats.totalBranchTasks == 0) {
                            "Aún no hay tareas asignadas a ${stats.branchName} en este periodo."
                        } else {
                            "Has completado ${stats.completedByMyPayroll} de ${stats.totalBranchTasks} tareas asignadas a ${stats.branchName} en esta quincena ($participationRounded%)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandNavy,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}

@Composable
private fun DonutLegendRow(
    color: Color,
    label: String,
    count: Int,
    total: Int
) {
    val pct = if (total > 0) ((count * 100f) / total).roundToInt() else 0
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
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$count ($pct%)",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
            fontWeight = FontWeight.Bold,
            color = BrandNavy
        )
    }
}
