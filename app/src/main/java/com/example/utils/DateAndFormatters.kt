package com.example.utils

import com.example.data.model.SalesMetricType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object DateAndFormatters {

    private val esMx = Locale.forLanguageTag("es-MX")
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("dd MMM yyyy", esMx)
    private val shortDateFormat = SimpleDateFormat("dd/MM", esMx)
    private val timeFormat = SimpleDateFormat("dd MMM · HH:mm", esMx)
    private val currencyFormat = NumberFormat.getCurrencyInstance(esMx).apply {
        maximumFractionDigits = 0
    }
    private val integerFormat = NumberFormat.getIntegerInstance(esMx)

    fun todayIso(): String = isoFormat.format(Date())

    fun addDaysIso(startIso: String, daysToAdd: Int): String {
        val parsed = runCatching { isoFormat.parse(startIso) }.getOrNull() ?: Date()
        val cal = Calendar.getInstance().apply {
            time = parsed
            add(Calendar.DAY_OF_YEAR, daysToAdd)
        }
        return isoFormat.format(cal.time)
    }

    fun defaultBiweeklyRange(): Pair<String, String> {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val startCal = Calendar.getInstance().apply {
            time = cal.time
            if (day <= 15) {
                set(Calendar.DAY_OF_MONTH, 1)
            } else {
                set(Calendar.DAY_OF_MONTH, 16)
            }
        }
        val endCal = Calendar.getInstance().apply {
            time = startCal.time
            add(Calendar.DAY_OF_MONTH, 13)
        }
        return isoFormat.format(startCal.time) to isoFormat.format(endCal.time)
    }

    fun defaultBiweeklyTitle(): String {
        val cal = Calendar.getInstance()
        val quincenaNum = if (cal.get(Calendar.DAY_OF_MONTH) <= 15) "1ª Quincena" else "2ª Quincena"
        val monthFormat = SimpleDateFormat("MMMM yyyy", esMx)
        val monthName = monthFormat.format(cal.time).replaceFirstChar { it.uppercase() }
        return "$quincenaNum · $monthName"
    }

    fun daysBetweenInclusive(startIso: String, endIso: String): Int {
        val start = runCatching { isoFormat.parse(startIso) }.getOrNull() ?: return 1
        val end = runCatching { isoFormat.parse(endIso) }.getOrNull() ?: return 1
        val diffDays = ((end.time - start.time) / (24L * 60L * 60L * 1000L)).toInt() + 1
        return diffDays.coerceAtLeast(1)
    }

    fun dayIndexInPeriod(startIso: String, targetIso: String, totalDays: Int): Int {
        val start = runCatching { isoFormat.parse(startIso) }.getOrNull() ?: return 1
        val target = runCatching { isoFormat.parse(targetIso) }.getOrNull() ?: return 1
        val diffDays = ((target.time - start.time) / (24L * 60L * 60L * 1000L)).toInt() + 1
        return diffDays.coerceIn(1, totalDays.coerceAtLeast(1))
    }

    fun formatIsoToDisplay(iso: String): String {
        val parsed = runCatching { isoFormat.parse(iso) }.getOrNull() ?: return iso
        return displayDateFormat.format(parsed)
    }

    fun formatIsoToShort(iso: String): String {
        val parsed = runCatching { isoFormat.parse(iso) }.getOrNull() ?: return iso
        return shortDateFormat.format(parsed)
    }

    fun formatTimestamp(date: Date?): String {
        if (date == null) return "Reciente"
        return timeFormat.format(date)
    }

    fun formatMetricValue(metric: SalesMetricType, value: Double): String {
        return if (metric.isCurrency) {
            currencyFormat.format(value)
        } else {
            "${integerFormat.format(value.roundToInt())} ${metric.unitLabel}"
        }
    }

    fun formatCurrency(value: Double): String = currencyFormat.format(value)

    fun formatUnits(value: Double): String = integerFormat.format(value.roundToInt())

    fun dayOfWeekForIso(iso: String): Int {
        val parsed = runCatching { isoFormat.parse(iso) }.getOrNull() ?: Date()
        return Calendar.getInstance().apply { time = parsed }.get(Calendar.DAY_OF_WEEK)
    }

    fun isTaskPastDeadline(
        dueDateIso: String,
        dueTime: String?,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val parsedDate = runCatching { isoFormat.parse(dueDateIso) }.getOrNull() ?: return false
        val parts = dueTime?.split(":")
        val hour = parts?.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 23
        val minute = parts?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 59

        val deadlineCal = Calendar.getInstance().apply {
            time = parsedDate
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return nowMillis > deadlineCal.timeInMillis
    }

    fun formatDeadlineDisplay(
        dueDateIso: String,
        dueTime: String?,
        deadlineLabel: String?
    ): String {
        if (!deadlineLabel.isNullOrBlank()) {
            val shortDate = formatIsoToShort(dueDateIso)
            return "$deadlineLabel ($shortDate)"
        }
        val shortDate = formatIsoToShort(dueDateIso)
        return if (!dueTime.isNullOrBlank() && dueTime != "23:59") {
            "Límite: $shortDate · $dueTime"
        } else {
            "Límite: $shortDate"
        }
    }
}
