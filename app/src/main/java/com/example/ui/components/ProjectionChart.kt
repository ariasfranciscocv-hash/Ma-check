package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.data.model.ChartSeries
import com.example.data.model.ProjectionPace
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.utils.DateAndFormatters
import kotlin.math.max

@Composable
fun ProjectionLineChart(
    series: ChartSeries,
    pace: ProjectionPace,
    modifier: Modifier = Modifier
) {
    val projectionColor = when (pace) {
        ProjectionPace.ON_TRACK -> SuccessGreen
        ProjectionPace.MODERATE_RISK -> WarningAmber
        ProjectionPace.BEHIND -> BrandRed
    }
    val actualColor = BrandNavy
    val targetColor = BrandRed.copy(alpha = 0.78f)
    val guideColor = MaterialTheme.colorScheme.outline

    val maxDataValue = max(
        series.targetValue,
        max(
            series.projectedCumulativeByDay.maxOrNull() ?: 0.0,
            series.actualCumulativeByDay.maxOrNull() ?: 0.0
        )
    ).coerceAtLeast(1.0) * 1.12

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Meta: ${DateAndFormatters.formatMetricValue(series.metric, series.targetValue)}",
                style = MaterialTheme.typography.labelMedium,
                color = targetColor
            )
            Text(
                text = "Día ${series.elapsedDays} de ${series.totalDays}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .padding(vertical = 4.dp)
        ) {
            val leftPad = 8.dp.toPx()
            val rightPad = 10.dp.toPx()
            val topPad = 12.dp.toPx()
            val bottomPad = 16.dp.toPx()

            val chartW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
            val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
            val totalSteps = (series.totalDays - 1).coerceAtLeast(1)

            fun xForDay(dayIndexZeroBased: Int): Float {
                return leftPad + (dayIndexZeroBased.toFloat() / totalSteps.toFloat()) * chartW
            }

            fun yForValue(value: Double): Float {
                val ratio = (value / maxDataValue).toFloat().coerceIn(0f, 1f)
                return topPad + chartH * (1f - ratio)
            }

            // Horizontal subtle gridlines
            listOf(0.25, 0.5, 0.75, 1.0).forEach { frac ->
                val y = topPad + chartH * (1f - frac.toFloat())
                drawLine(
                    color = guideColor.copy(alpha = 0.28f),
                    start = Offset(leftPad, y),
                    end = Offset(leftPad + chartW, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Ideal pace dotted diagonal from Day 1 (0) to Day N (target)
            drawLine(
                color = guideColor.copy(alpha = 0.8f),
                start = Offset(xForDay(0), yForValue(0.0)),
                end = Offset(xForDay(totalSteps), yForValue(series.targetValue)),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f), 0f)
            )

            // Target horizontal dashed line
            val targetY = yForValue(series.targetValue)
            drawLine(
                color = targetColor,
                start = Offset(leftPad, targetY),
                end = Offset(leftPad + chartW, targetY),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
            )

            // Projection dashed line from elapsed day to final day
            if (series.projectedCumulativeByDay.size >= 2) {
                val startIdx = (series.elapsedDays - 1).coerceIn(0, series.projectedCumulativeByDay.lastIndex)
                val projPath = Path().apply {
                    moveTo(xForDay(startIdx), yForValue(series.projectedCumulativeByDay[startIdx]))
                    for (i in (startIdx + 1) until series.projectedCumulativeByDay.size) {
                        lineTo(xForDay(i), yForValue(series.projectedCumulativeByDay[i]))
                    }
                }
                drawPath(
                    path = projPath,
                    color = projectionColor,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                    )
                )
                val lastIdx = series.projectedCumulativeByDay.lastIndex
                drawCircle(
                    color = projectionColor,
                    radius = 5.dp.toPx(),
                    center = Offset(xForDay(lastIdx), yForValue(series.projectedCumulativeByDay[lastIdx]))
                )
            }

            // Actual cumulative solid line + soft area fill
            val actual = series.actualCumulativeByDay
            if (actual.isNotEmpty()) {
                val actualPath = Path()
                val fillPath = Path()
                actual.forEachIndexed { idx, value ->
                    val x = xForDay(idx)
                    val y = yForValue(value)
                    if (idx == 0) {
                        actualPath.moveTo(x, y)
                        fillPath.moveTo(x, topPad + chartH)
                        fillPath.lineTo(x, y)
                    } else {
                        actualPath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                val lastActualIdx = actual.lastIndex
                fillPath.lineTo(xForDay(lastActualIdx), topPad + chartH)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            actualColor.copy(alpha = 0.24f),
                            actualColor.copy(alpha = 0.02f)
                        ),
                        startY = topPad,
                        endY = topPad + chartH
                    )
                )
                drawPath(
                    path = actualPath,
                    color = actualColor,
                    style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Current day point
                val currentPoint = Offset(xForDay(lastActualIdx), yForValue(actual[lastActualIdx]))
                drawCircle(color = Color.White, radius = 6.dp.toPx(), center = currentPoint)
                drawCircle(color = actualColor, radius = 4.dp.toPx(), center = currentPoint)
            }
        }

        // X-axis day markers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Día 1",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Día ${(series.totalDays / 2).coerceAtLeast(2)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Día ${series.totalDays}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Compact Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChartLegendDot(color = actualColor, label = "Real")
            ChartLegendDot(color = projectionColor, label = "Proyección")
            ChartLegendDot(color = targetColor, label = "Meta 14d")
        }
    }
}

@Composable
private fun ChartLegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
