package com.example

import com.example.data.repository.ScheduledTasksCatalog
import com.example.utils.DateAndFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduledTasksTest {

    @Test
    fun `friday schedule includes Inventario due next day at close`() {
        // 2026-11-27 is a Friday
        val fridayIso = "2026-11-27"
        assertEquals(Calendar.FRIDAY, DateAndFormatters.dayOfWeekForIso(fridayIso))

        val tasks = ScheduledTasksCatalog.tasksForDate(fridayIso)
        val inventario = tasks.firstOrNull { it.title == "Inventario" }
        assertNotNull(inventario)
        assertEquals("2026-11-28", inventario!!.dueDateIso)
        assertEquals("23:59", inventario.dueTime)
        assertTrue(inventario.deadlineLabel.contains("día siguiente al cierre", ignoreCase = true))
    }

    @Test
    fun `sunday schedule includes Checklist domingos, Checklist cajas, and Examenes due next day 11_30am`() {
        // 2026-11-29 is a Sunday
        val sundayIso = "2026-11-29"
        assertEquals(Calendar.SUNDAY, DateAndFormatters.dayOfWeekForIso(sundayIso))

        val tasks = ScheduledTasksCatalog.tasksForDate(sundayIso)
        val titles = tasks.map { it.title }
        assertTrue("Checklist domingos" in titles)
        assertTrue("Checklist cajas" in titles)
        assertTrue("Exámenes" in titles)

        val examenes = tasks.first { it.title == "Exámenes" }
        assertEquals("2026-11-30", examenes.dueDateIso)
        assertEquals("11:30", examenes.dueTime)
        assertTrue(examenes.deadlineLabel.contains("11:30", ignoreCase = true))
    }

    @Test
    fun `tasksToEnsureOnDate generates tasks strictly for current day only and deadline checks enforce 11_30 cutoff`() {
        val saturdayNoon = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 28, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val saturdayEnsure = ScheduledTasksCatalog.tasksToEnsureOnDate("2026-11-28", saturdayNoon)
        assertTrue(saturdayEnsure.isNotEmpty())
        assertTrue(saturdayEnsure.all { (assignedDate, _) -> assignedDate == "2026-11-28" })
        assertFalse(saturdayEnsure.any { (assignedDate, _) -> assignedDate == "2026-11-27" })

        val monday1129 = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 30, 11, 29, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val monday1131 = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 30, 11, 31, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertFalse(DateAndFormatters.isTaskPastDeadline("2026-11-30", "11:30", monday1129))
        assertTrue(DateAndFormatters.isTaskPastDeadline("2026-11-30", "11:30", monday1131))

        val mondayMorningEnsure = ScheduledTasksCatalog.tasksToEnsureOnDate("2026-11-30", monday1129)
        assertTrue(mondayMorningEnsure.isNotEmpty())
        assertTrue(mondayMorningEnsure.all { (assignedDate, _) -> assignedDate == "2026-11-30" })
        assertFalse(mondayMorningEnsure.any { (assignedDate, _) -> assignedDate == "2026-11-29" })
    }

    @Test
    fun `monday through saturday include 2pm and 6pm sales progress tasks while sunday excludes them`() {
        // 2026-11-23 (Monday) through 2026-11-28 (Saturday)
        val monToSat = listOf("2026-11-23", "2026-11-24", "2026-11-25", "2026-11-26", "2026-11-27", "2026-11-28")
        monToSat.forEach { iso ->
            val tasks = ScheduledTasksCatalog.tasksForDate(iso)
            val t2 = tasks.firstOrNull { it.slug == "avance_ventas_1400" }
            val t6 = tasks.firstOrNull { it.slug == "avance_ventas_1800" }
            assertNotNull("Expected 2pm task on $iso", t2)
            assertNotNull("Expected 6pm task on $iso", t6)
            assertEquals("17:59", t2!!.dueTime)
            assertEquals("23:59", t6!!.dueTime)
            assertEquals("Horario: 2:00 p.m. – 5:59 p.m.", t2.deadlineLabel)
            assertEquals("Horario: 6:00 p.m. – cierre", t6.deadlineLabel)
        }

        // 2026-11-29 is Sunday: should NOT include the Mon-Sat sales checkpoints
        val sundayTasks = ScheduledTasksCatalog.tasksForDate("2026-11-29")
        assertFalse(sundayTasks.any { it.slug == "avance_ventas_1400" || it.slug == "avance_ventas_1800" })
    }

    @Test
    fun `sales checkpoint tasks are visible only inside their established time windows`() {
        val task2pm = com.example.data.model.AssignedTask(
            id = "sched_u1_2026-11-25_avance_ventas_1400",
            title = "Actualización de avance de ventas (2:00 p.m.)",
            dueDate = "2026-11-25",
            dueTime = "17:59"
        )
        val task6pm = com.example.data.model.AssignedTask(
            id = "sched_u1_2026-11-25_avance_ventas_1800",
            title = "Actualización de avance de ventas (6:00 p.m.)",
            dueDate = "2026-11-25",
            dueTime = "23:59"
        )

        val at1359 = millisOf(2026, Calendar.NOVEMBER, 25, 13, 59)
        val at1400 = millisOf(2026, Calendar.NOVEMBER, 25, 14, 0)
        val at1759 = millisOf(2026, Calendar.NOVEMBER, 25, 17, 59)
        val at1800 = millisOf(2026, Calendar.NOVEMBER, 25, 18, 0)
        val nextDay = millisOf(2026, Calendar.NOVEMBER, 26, 0, 5)

        assertFalse(DateAndFormatters.isTaskWithinActiveWindow(task2pm, at1359))
        assertTrue(DateAndFormatters.isTaskWithinActiveWindow(task2pm, at1400))
        assertTrue(DateAndFormatters.isTaskWithinActiveWindow(task2pm, at1759))
        assertFalse(DateAndFormatters.isTaskWithinActiveWindow(task2pm, at1800))

        assertFalse(DateAndFormatters.isTaskWithinActiveWindow(task6pm, at1759))
        assertTrue(DateAndFormatters.isTaskWithinActiveWindow(task6pm, at1800))
        assertFalse(DateAndFormatters.isTaskWithinActiveWindow(task6pm, nextDay))
    }

    @Test
    fun `daily, inventario, and examenes tasks hide automatically once their deadline expires`() {
        val examenes = com.example.data.model.AssignedTask(
            id = "sched_u1_2026-11-29_examenes",
            title = "Exámenes",
            dueDate = "2026-11-30",
            dueTime = "11:30"
        )
        val beforeStart = millisOf(2026, Calendar.NOVEMBER, 28, 23, 55)
        val sundayEvening = millisOf(2026, Calendar.NOVEMBER, 29, 20, 0)
        val monday1130 = millisOf(2026, Calendar.NOVEMBER, 30, 11, 30)
        val monday1131 = millisOf(2026, Calendar.NOVEMBER, 30, 11, 31)

        assertFalse(DateAndFormatters.isTaskWithinActiveWindow(examenes, beforeStart))
        assertTrue(DateAndFormatters.isTaskWithinActiveWindow(examenes, sundayEvening))
        assertTrue(DateAndFormatters.isTaskWithinActiveWindow(examenes, monday1130))
        assertFalse(DateAndFormatters.isTaskWithinActiveWindow(examenes, monday1131))
    }

    @Test
    fun `schedule indicator distinguishes before_window, in_window, after_window, and completed while keeping tasks completable`() {
        val task2pm = com.example.data.model.AssignedTask(
            id = "sched_u1_2026-11-25_avance_ventas_1400",
            title = "Actualización de avance de ventas (2:00 p.m.)",
            dueDate = "2026-11-25",
            dueTime = "17:59",
            status = com.example.data.model.TaskStatus.PENDING.name
        )
        val at1359 = millisOf(2026, Calendar.NOVEMBER, 25, 13, 59)
        val at1400 = millisOf(2026, Calendar.NOVEMBER, 25, 14, 0)
        val at1800 = millisOf(2026, Calendar.NOVEMBER, 25, 18, 0)

        assertEquals(
            DateAndFormatters.TaskScheduleStatus.BEFORE_WINDOW,
            DateAndFormatters.resolveTaskScheduleStatus(task2pm, at1359)
        )
        assertEquals(
            DateAndFormatters.TaskScheduleStatus.IN_WINDOW,
            DateAndFormatters.resolveTaskScheduleStatus(task2pm, at1400)
        )
        assertEquals(
            DateAndFormatters.TaskScheduleStatus.AFTER_WINDOW,
            DateAndFormatters.resolveTaskScheduleStatus(task2pm, at1800)
        )

        val completedTask = task2pm.copy(status = com.example.data.model.TaskStatus.COMPLETED.name)
        assertEquals(
            DateAndFormatters.TaskScheduleStatus.COMPLETED,
            DateAndFormatters.resolveTaskScheduleStatus(completedTask, at1800)
        )
    }

    @Test
    fun `buildDayTaskGroups groups tasks by assigned day with today expanded first`() {
        val tToday1 = com.example.data.model.AssignedTask(
            id = "sched_u1_2026-11-26_apertura_piso",
            title = "Apertura de piso",
            dueDate = "2026-11-26",
            status = com.example.data.model.TaskStatus.COMPLETED.name
        )
        val tToday2 = com.example.data.model.AssignedTask(
            id = "sched_u1_2026-11-26_avance_ventas_1400",
            title = "Avance 2pm",
            dueDate = "2026-11-26",
            dueTime = "17:59",
            status = com.example.data.model.TaskStatus.PENDING.name
        )
        val tYesterday = com.example.data.model.AssignedTask(
            id = "sched_u1_2026-11-25_cierre_caja_credito",
            title = "Cierre de caja",
            dueDate = "2026-11-25",
            status = com.example.data.model.TaskStatus.PENDING.name
        )

        val groups = com.example.data.model.buildDayTaskGroups(
            tasks = listOf(tYesterday, tToday1, tToday2),
            todayIso = "2026-11-26"
        )
        assertEquals(2, groups.size)
        assertEquals("2026-11-26", groups[0].dateIso)
        assertTrue(groups[0].isToday)
        assertEquals(1, groups[0].completedCount)
        assertEquals(2, groups[0].totalCount)
        assertEquals("2026-11-25", groups[1].dateIso)
        assertFalse(groups[1].isToday)
        assertEquals(0, groups[1].completedCount)
        assertEquals(1, groups[1].totalCount)
    }

    @Test
    fun `scheduled checklist includes Cierre de venta diaria and opening hours reset triggers only on new day at or after 8am`() {
        val todayTasks = ScheduledTasksCatalog.tasksForDate("2026-11-26")
        val dailyClosing = todayTasks.firstOrNull { it.title == ScheduledTasksCatalog.DAILY_SALES_CLOSING_TITLE }
        assertNotNull(dailyClosing)
        assertEquals(ScheduledTasksCatalog.DAILY_SALES_CLOSING_SLUG, dailyClosing!!.slug)
        assertTrue(ScheduledTasksCatalog.DAILY_SALES_CLOSING_TITLE in ScheduledTasksCatalog.ALLOWED_SELF_SCHEDULED_TITLES)

        val beforeOpen759 = millisOf(2026, Calendar.NOVEMBER, 26, 7, 59)
        val atOpen800 = millisOf(2026, Calendar.NOVEMBER, 26, 8, 0)
        val evening2130 = millisOf(2026, Calendar.NOVEMBER, 26, 21, 30)

        assertFalse(DateAndFormatters.isWithinOpeningOrWorkdayHours(beforeOpen759))
        assertTrue(DateAndFormatters.isWithinOpeningOrWorkdayHours(atOpen800))
        assertTrue(DateAndFormatters.isWithinOpeningOrWorkdayHours(evening2130))

        // Before 8:00 a.m. -> do not reset yet
        assertFalse(DateAndFormatters.shouldAutoResetDailyClosing("2026-11-25", "2026-11-26", beforeOpen759))
        // New day & at/after 8:00 a.m. -> reset to pending
        assertTrue(DateAndFormatters.shouldAutoResetDailyClosing("2026-11-25", "2026-11-26", atOpen800))
        // No previous closing & at/after 8:00 a.m. -> initialize pending
        assertTrue(DateAndFormatters.shouldAutoResetDailyClosing(null, "2026-11-26", atOpen800))
        // Already closed today -> keep completed / do not reset
        assertFalse(DateAndFormatters.shouldAutoResetDailyClosing("2026-11-26", "2026-11-26", evening2130))
    }

    @Test
    fun `daily task templates filter accurately by weekday and branch without duplicates`() {
        val tplFriSatBranch87 = com.example.data.model.DailyTaskTemplate(
            id = "tpl_promo_fin_semana",
            slug = "promo_fin_semana",
            title = "Montaje de promoción fin de semana",
            description = "Colocar precios promocionales",
            priority = com.example.data.model.TaskPriority.URGENT.name,
            daysOfWeek = listOf(
                com.example.data.model.WeekdayOption.FRIDAY.code,
                com.example.data.model.WeekdayOption.SATURDAY.code
            ),
            targetBranches = listOf("Sucursal 87", "Sucursal 109"),
            dueTime = "18:00",
            deadlineLabel = "Viernes y Sábado",
            active = true
        )

        // 2026-11-27 is Friday, 2026-11-28 is Saturday, 2026-11-29 is Sunday, 2026-11-26 is Thursday
        assertEquals("FRI", DateAndFormatters.weekdayCodeForIso("2026-11-27"))
        assertEquals("SAT", DateAndFormatters.weekdayCodeForIso("2026-11-28"))
        assertEquals("SUN", DateAndFormatters.weekdayCodeForIso("2026-11-29"))

        assertTrue(tplFriSatBranch87.appliesTo("2026-11-27", "Sucursal 87"))
        assertTrue(tplFriSatBranch87.appliesTo("2026-11-28", "Sucursal 109"))
        assertFalse(tplFriSatBranch87.appliesTo("2026-11-27", "Sucursal 110"))
        assertFalse(tplFriSatBranch87.appliesTo("2026-11-29", "Sucursal 87"))
        assertFalse(tplFriSatBranch87.appliesTo("2026-11-26", "Sucursal 87"))

        // Deactivated template must never match any day or branch
        val deletedTpl = tplFriSatBranch87.copy(active = false)
        assertFalse(deletedTpl.appliesTo("2026-11-27", "Sucursal 87"))

        // Default templates match expected days for All Branches
        val defaults = ScheduledTasksCatalog.defaultTemplates("admin_uid")
        val fridayMatches = ScheduledTasksCatalog.templatesMatching(defaults, "2026-11-27", "Sucursal 112")
        val sundayMatches = ScheduledTasksCatalog.templatesMatching(defaults, "2026-11-29", "Sucursal 112")
        assertTrue(fridayMatches.any { it.slug == "inventario" })
        assertFalse(fridayMatches.any { it.slug == "examenes" })
        assertTrue(sundayMatches.any { it.slug == "examenes" })
        assertFalse(sundayMatches.any { it.slug == "avance_ventas_1400" })
    }

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
