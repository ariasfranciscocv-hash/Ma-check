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
    fun `saturday carry-over includes friday Inventario and monday 11_30 cutoff is enforced`() {
        val saturdayNoon = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 28, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val saturdayEnsure = ScheduledTasksCatalog.tasksToEnsureOnDate("2026-11-28", saturdayNoon)
        assertTrue(saturdayEnsure.any { (assignedDate, spec) ->
            assignedDate == "2026-11-27" && spec.title == "Inventario"
        })

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
        assertTrue(mondayMorningEnsure.any { (assignedDate, spec) ->
            assignedDate == "2026-11-29" && spec.title == "Exámenes"
        })
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

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
