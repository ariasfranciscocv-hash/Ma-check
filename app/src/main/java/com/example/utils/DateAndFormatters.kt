package com.example.utils

import com.example.data.model.AssignedTask
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

    fun biweeklyRangeForDate(cal: Calendar = Calendar.getInstance()): Pair<String, String> {
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val startCal = (cal.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, if (day <= 15) 1 else 16)
        }
        val endCal = (cal.clone() as Calendar).apply {
            val endDay = if (day <= 15) 15 else getActualMaximum(Calendar.DAY_OF_MONTH)
            set(Calendar.DAY_OF_MONTH, endDay)
        }
        synchronized(isoFormat) {
            return isoFormat.format(startCal.time) to isoFormat.format(endCal.time)
        }
    }

    fun nextBiweeklyRange(cal: Calendar = Calendar.getInstance()): Pair<String, String> {
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val nextCal = (cal.clone() as Calendar).apply {
            if (day <= 15) {
                set(Calendar.DAY_OF_MONTH, 16)
            } else {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, 1)
            }
        }
        return biweeklyRangeForDate(nextCal)
    }

    fun defaultBiweeklyRange(): Pair<String, String> = biweeklyRangeForDate(Calendar.getInstance())

    fun biweeklyTitleForRange(startIso: String): String {
        val parsed = runCatching { synchronized(isoFormat) { isoFormat.parse(startIso) } }.getOrNull() ?: Date()
        val cal = Calendar.getInstance().apply { time = parsed }
        val quincenaNum = if (cal.get(Calendar.DAY_OF_MONTH) <= 15) "1ª Quincena" else "2ª Quincena"
        val monthFormat = SimpleDateFormat("MMMM yyyy", esMx)
        val monthName = monthFormat.format(cal.time).replaceFirstChar { it.uppercase() }
        return "$quincenaNum · $monthName"
    }

    fun defaultBiweeklyTitle(): String = biweeklyTitleForRange(defaultBiweeklyRange().first)

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

    private val scheduledIdDateRegex = Regex("""^sched_.+_(\d{4}-\d{2}-\d{2})_[a-z0-9_]+$""")

    private fun parseDateTimeMillis(
        dateIso: String,
        timeHm: String?,
        endOfMinute: Boolean
    ): Long? {
        val parsedDate = runCatching {
            synchronized(isoFormat) { isoFormat.parse(dateIso) }
        }.getOrNull() ?: return null
        val parts = timeHm?.split(":")
        val hour = parts?.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23)
            ?: if (endOfMinute) 23 else 0
        val minute = parts?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59)
            ?: if (endOfMinute) 59 else 0

        return Calendar.getInstance().apply {
            time = parsedDate
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, if (endOfMinute) 59 else 0)
            set(Calendar.MILLISECOND, if (endOfMinute) 999 else 0)
        }.timeInMillis
    }

    fun extractScheduledAssignedDate(taskId: String): String? =
        scheduledIdDateRegex.find(taskId)?.groupValues?.getOrNull(1)

    fun resolveTaskWindowMillis(task: AssignedTask): Pair<Long, Long>? {
        if (task.dueDate.isBlank()) return null

        val is2pmTask = task.id.endsWith("_avance_ventas_1400") ||
            task.title.contains("2:00 p.m.", ignoreCase = true)
        val is6pmTask = task.id.endsWith("_avance_ventas_1800") ||
            task.title.contains("6:00 p.m.", ignoreCase = true)

        val scheduledStartDate = extractScheduledAssignedDate(task.id)
        val startDateIso = scheduledStartDate ?: if (is2pmTask || is6pmTask) task.dueDate else null

        val startMillis = when {
            is2pmTask -> parseDateTimeMillis(startDateIso!!, "14:00", endOfMinute = false)
            is6pmTask -> parseDateTimeMillis(startDateIso!!, "18:00", endOfMinute = false)
            scheduledStartDate != null -> parseDateTimeMillis(scheduledStartDate, "00:00", endOfMinute = false)
            else -> {
                val createdDateIso = task.createdAt?.toDate()?.let { createdDate ->
                    synchronized(isoFormat) { isoFormat.format(createdDate) }
                }
                parseDateTimeMillis(createdDateIso ?: task.dueDate, "00:00", endOfMinute = false)
            }
        } ?: return null

        val effectiveEndTime = when {
            is2pmTask && (task.dueTime.isNullOrBlank() || task.dueTime == "23:59") -> "17:59"
            else -> task.dueTime?.takeIf { it.isNotBlank() } ?: "23:59"
        }
        val endMillis = parseDateTimeMillis(task.dueDate, effectiveEndTime, endOfMinute = true)
            ?: return null

        return startMillis to endMillis
    }

    fun isTaskWithinActiveWindow(
        task: AssignedTask,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val (startMillis, endMillis) = resolveTaskWindowMillis(task) ?: return true
        return nowMillis in startMillis..endMillis
    }

    fun isTaskPastDeadline(
        dueDateIso: String,
        dueTime: String?,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val deadlineMillis = parseDateTimeMillis(dueDateIso, dueTime, endOfMinute = true) ?: return false
        return nowMillis > deadlineMillis
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

    enum class TaskScheduleStatus {
        COMPLETED,
        IN_WINDOW,
        BEFORE_WINDOW,
        AFTER_WINDOW
    }

    fun resolveTaskScheduleStatus(
        task: AssignedTask,
        nowMillis: Long = System.currentTimeMillis()
    ): TaskScheduleStatus {
        if (task.status == com.example.data.model.TaskStatus.COMPLETED.name) {
            return TaskScheduleStatus.COMPLETED
        }
        val (startMillis, endMillis) = resolveTaskWindowMillis(task) ?: return TaskScheduleStatus.IN_WINDOW
        return when {
            nowMillis < startMillis -> TaskScheduleStatus.BEFORE_WINDOW
            nowMillis > endMillis -> TaskScheduleStatus.AFTER_WINDOW
            else -> TaskScheduleStatus.IN_WINDOW
        }
    }

    fun formatScheduleStartHint(task: AssignedTask): String = when {
        task.id.endsWith("_avance_ventas_1400") || task.title.contains("2:00 p.m.", ignoreCase = true) ->
            "Programada · 2:00 p.m."
        task.id.endsWith("_avance_ventas_1800") || task.title.contains("6:00 p.m.", ignoreCase = true) ->
            "Programada · 6:00 p.m."
        else -> {
            val assigned = extractScheduledAssignedDate(task.id) ?: task.dueDate
            "Programada · ${formatIsoToShort(assigned)}"
        }
    }

    fun extractTaskDayIso(task: AssignedTask): String {
        extractScheduledAssignedDate(task.id)?.let { return it }
        val createdIso = task.createdAt?.toDate()?.let { date ->
            synchronized(isoFormat) { isoFormat.format(date) }
        }
        if (!createdIso.isNullOrBlank()) return createdIso
        return task.dueDate.ifBlank { todayIso() }
    }

    fun formatDayGroupHeader(dateIso: String, todayIso: String = todayIso()): String {
        val yesterdayIso = addDaysIso(todayIso, -1)
        val parsed = runCatching { synchronized(isoFormat) { isoFormat.parse(dateIso) } }.getOrNull()
        val readableDate = if (parsed != null) {
            SimpleDateFormat("EEE dd MMM", esMx).format(parsed)
                .replace(".", "")
                .replaceFirstChar { it.uppercase() }
        } else {
            dateIso
        }
        return when (dateIso) {
            todayIso -> "Hoy · $readableDate"
            yesterdayIso -> "Ayer · $readableDate"
            else -> readableDate
        }
    }
}
