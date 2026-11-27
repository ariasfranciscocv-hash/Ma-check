package com.example.utils

import com.example.data.model.AssignedTask
import com.example.data.model.SalesMetricType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

object DateAndFormatters {

    private val esMx = Locale.forLanguageTag("es-MX")
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
    }
    private val isoUtcFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val displayDateFormat = SimpleDateFormat("dd MMM yyyy", esMx)
    private val shortDateFormat = SimpleDateFormat("dd/MM", esMx)
    private val timeFormat = SimpleDateFormat("dd MMM · HH:mm", esMx)
    private val currencyFormat = NumberFormat.getCurrencyInstance(esMx).apply {
        maximumFractionDigits = 0
    }
    private val integerFormat = NumberFormat.getIntegerInstance(esMx)

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    const val OPENING_HOUR_OF_DAY = 8

    private fun formatIsoLocal(date: Date): String =
        synchronized(isoFormat) { isoFormat.format(date) }

    private fun parseIsoLocal(iso: String): Date? =
        runCatching { synchronized(isoFormat) { isoFormat.parse(iso) } }.getOrNull()

    private fun parseIsoUtc(iso: String): Date? =
        runCatching { synchronized(isoUtcFormat) { isoUtcFormat.parse(iso) } }.getOrNull()

    fun todayIso(): String = formatIsoLocal(Date())

    fun addDaysIso(startIso: String, daysToAdd: Int): String {
        val parsed = parseIsoUtc(startIso) ?: parseIsoLocal(startIso) ?: Date()
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            time = parsed
            add(Calendar.DAY_OF_YEAR, daysToAdd)
        }
        return synchronized(isoUtcFormat) { isoUtcFormat.format(cal.time) }
    }

    fun defaultBiweeklyRange(referenceDate: Date = Date()): Pair<String, String> {
        val cal = Calendar.getInstance().apply { time = referenceDate }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val startCal = Calendar.getInstance().apply {
            time = cal.time
            set(Calendar.DAY_OF_MONTH, if (day <= 15) 1 else 16)
        }
        val endCal = Calendar.getInstance().apply {
            time = startCal.time
            val endDay = if (day <= 15) 15 else getActualMaximum(Calendar.DAY_OF_MONTH)
            set(Calendar.DAY_OF_MONTH, endDay)
        }
        return formatIsoLocal(startCal.time) to formatIsoLocal(endCal.time)
    }

    fun biweeklyRangeForIso(dateIso: String): Pair<String, String> {
        val parsed = parseIsoLocal(dateIso) ?: Date()
        return defaultBiweeklyRange(parsed)
    }

    fun currentBiweeklyPeriodId(referenceDate: Date = Date()): String {
        val cal = Calendar.getInstance().apply { time = referenceDate }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val half = if (cal.get(Calendar.DAY_OF_MONTH) <= 15) "Q1" else "Q2"
        return String.format(Locale.US, "%04d-%02d_%s", year, month, half)
    }

    fun biweeklyPeriodIdForIso(dateIso: String): String {
        val parsed = parseIsoLocal(dateIso) ?: Date()
        return currentBiweeklyPeriodId(parsed)
    }

    fun defaultBiweeklyTitle(referenceDate: Date = Date()): String {
        val cal = Calendar.getInstance().apply { time = referenceDate }
        val quincenaNum = if (cal.get(Calendar.DAY_OF_MONTH) <= 15) "1ª Quincena" else "2ª Quincena"
        val monthFormat = SimpleDateFormat("MMMM yyyy", esMx)
        val monthName = monthFormat.format(cal.time).replaceFirstChar { it.uppercase() }
        return "$quincenaNum · $monthName"
    }

    fun daysBetweenInclusive(startIso: String, endIso: String): Int {
        val start = parseIsoUtc(startIso) ?: return 1
        val end = parseIsoUtc(endIso) ?: return 1
        val diffDays = ((end.time - start.time) / MILLIS_PER_DAY).toInt() + 1
        return diffDays.coerceAtLeast(1)
    }

    fun dayIndexInPeriod(startIso: String, targetIso: String, totalDays: Int): Int {
        val start = parseIsoUtc(startIso) ?: return 1
        val target = parseIsoUtc(targetIso) ?: return 1
        val diffDays = ((target.time - start.time) / MILLIS_PER_DAY).toInt() + 1
        return diffDays.coerceIn(1, totalDays.coerceAtLeast(1))
    }

    fun formatIsoToDisplay(iso: String): String {
        val parsed = parseIsoLocal(iso) ?: return iso
        return synchronized(displayDateFormat) { displayDateFormat.format(parsed) }
    }

    fun formatIsoToShort(iso: String): String {
        val parsed = parseIsoLocal(iso) ?: return iso
        return synchronized(shortDateFormat) { shortDateFormat.format(parsed) }
    }

    fun formatTimestamp(date: Date?): String {
        if (date == null) return "Reciente"
        return synchronized(timeFormat) { timeFormat.format(date) }
    }

    fun formatMetricValue(metric: SalesMetricType, value: Double): String {
        return if (metric.isCurrency) {
            synchronized(currencyFormat) { currencyFormat.format(value) }
        } else {
            val formattedInt = synchronized(integerFormat) { integerFormat.format(value.roundToInt()) }
            "$formattedInt ${metric.unitLabel}"
        }
    }

    fun formatCurrency(value: Double): String =
        synchronized(currencyFormat) { currencyFormat.format(value) }

    fun formatUnits(value: Double): String =
        synchronized(integerFormat) { integerFormat.format(value.roundToInt()) }

    fun dayOfWeekForIso(iso: String): Int {
        val parsed = parseIsoLocal(iso) ?: Date()
        return Calendar.getInstance().apply { time = parsed }.get(Calendar.DAY_OF_WEEK)
    }

    fun isWithinOpeningOrWorkdayHours(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val hour = Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY)
        return hour in OPENING_HOUR_OF_DAY..23
    }

    fun shouldAutoResetDailyClosing(
        lastClosingDateIso: String?,
        todayIso: String = todayIso(),
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isWithinOpeningOrWorkdayHours(nowMillis)) return false
        return lastClosingDateIso.isNullOrBlank() || lastClosingDateIso != todayIso
    }

    private val scheduledIdDateRegex = Regex("""^sched_.+_(\d{4}-\d{2}-\d{2})_([a-z0-9_]+)$""")

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

    fun extractScheduledSlug(taskId: String): String? =
        scheduledIdDateRegex.find(taskId)?.groupValues?.getOrNull(2)

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
