package com.example.data.repository

import com.example.utils.DateAndFormatters
import java.util.Calendar

data class ScheduledTaskSpec(
    val slug: String,
    val title: String,
    val description: String,
    val dueDateIso: String,
    val dueTime: String,
    val deadlineLabel: String
)

object ScheduledTasksCatalog {

    const val DAILY_SALES_CLOSING_SLUG = "cierre_venta_diaria"
    const val DAILY_SALES_CLOSING_TITLE = "Cierre de venta diaria"

    val ALLOWED_SELF_SCHEDULED_TITLES: Set<String> = setOf(
        "Apertura de piso y encendido de exhibición",
        "Revisión de exhibición destacada",
        "Cierre de caja y orden en módulo de crédito",
        DAILY_SALES_CLOSING_TITLE,
        "Actualización de avance de ventas (2:00 p.m.)",
        "Actualización de avance de ventas (6:00 p.m.)",
        "Inventario",
        "Checklist domingos",
        "Checklist cajas",
        "Exámenes"
    )

    fun tasksForDate(dateIso: String): List<ScheduledTaskSpec> {
        val baseTasks = mutableListOf(
            ScheduledTaskSpec(
                slug = "apertura_piso",
                title = "Apertura de piso y encendido de exhibición",
                description = "Verificar iluminación, limpieza de pasillos y etiquetas de precio visibles.",
                dueDateIso = dateIso,
                dueTime = "23:59",
                deadlineLabel = "Límite: Hoy al cierre"
            ),
            ScheduledTaskSpec(
                slug = "revision_exhibicion",
                title = "Revisión de exhibición destacada",
                description = "Confirmar disponibilidad de modelos promocionales y estado general de salas.",
                dueDateIso = dateIso,
                dueTime = "23:59",
                deadlineLabel = "Límite: Hoy al cierre"
            ),
            ScheduledTaskSpec(
                slug = "cierre_caja_credito",
                title = "Cierre de caja y orden en módulo de crédito",
                description = "Resguardar papelería y dejar el área de atención lista para el siguiente turno.",
                dueDateIso = dateIso,
                dueTime = "23:59",
                deadlineLabel = "Límite: Hoy al cierre"
            ),
            ScheduledTaskSpec(
                slug = DAILY_SALES_CLOSING_SLUG,
                title = DAILY_SALES_CLOSING_TITLE,
                description = "Capturar el cierre de operaciones con las 5 métricas del día y adjuntar respaldo.",
                dueDateIso = dateIso,
                dueTime = "23:59",
                deadlineLabel = "Jornada: Desde 8:00 a.m. al cierre"
            )
        )

        val dayOfWeek = DateAndFormatters.dayOfWeekForIso(dateIso)
        if (dayOfWeek != Calendar.SUNDAY) {
            baseTasks.add(
                ScheduledTaskSpec(
                    slug = "avance_ventas_1400",
                    title = "Actualización de avance de ventas (2:00 p.m.)",
                    description = "Actualizar el avance de ventas de las 2:00 p.m. y documentar con evidencia fotográfica.",
                    dueDateIso = dateIso,
                    dueTime = "17:59",
                    deadlineLabel = "Horario: 2:00 p.m. – 5:59 p.m."
                )
            )
            baseTasks.add(
                ScheduledTaskSpec(
                    slug = "avance_ventas_1800",
                    title = "Actualización de avance de ventas (6:00 p.m.)",
                    description = "Actualizar el avance de ventas de las 6:00 p.m. y documentar con evidencia fotográfica.",
                    dueDateIso = dateIso,
                    dueTime = "23:59",
                    deadlineLabel = "Horario: 6:00 p.m. – cierre"
                )
            )
        }

        when (dayOfWeek) {
            Calendar.FRIDAY -> {
                val saturdayIso = DateAndFormatters.addDaysIso(dateIso, 1)
                baseTasks.add(
                    ScheduledTaskSpec(
                        slug = "inventario",
                        title = "Inventario",
                        description = "Realizar conteo y validación de inventario de viernes. Se debe completar a más tardar el día siguiente al cierre.",
                        dueDateIso = saturdayIso,
                        dueTime = "23:59",
                        deadlineLabel = "Límite: Día siguiente al cierre"
                    )
                )
            }

            Calendar.SUNDAY -> {
                val mondayIso = DateAndFormatters.addDaysIso(dateIso, 1)
                baseTasks.add(
                    ScheduledTaskSpec(
                        slug = "checklist_domingos",
                        title = "Checklist domingos",
                        description = "Completar checklist operativo dominical de sucursal y adjuntar evidencia fotográfica.",
                        dueDateIso = dateIso,
                        dueTime = "23:59",
                        deadlineLabel = "Límite: Hoy al cierre"
                    )
                )
                baseTasks.add(
                    ScheduledTaskSpec(
                        slug = "checklist_cajas",
                        title = "Checklist cajas",
                        description = "Revisar orden, cortes y documentación en área de cajas de domingo con foto de respaldo.",
                        dueDateIso = dateIso,
                        dueTime = "23:59",
                        deadlineLabel = "Límite: Hoy al cierre"
                    )
                )
                baseTasks.add(
                    ScheduledTaskSpec(
                        slug = "examenes",
                        title = "Exámenes",
                        description = "Completar exámenes de domingo y subir fotografía comprobante. Límite máximo: día siguiente hasta las 11:30 a.m.",
                        dueDateIso = mondayIso,
                        dueTime = "11:30",
                        deadlineLabel = "Límite: Día siguiente 11:30 a.m."
                    )
                )
            }
        }

        return baseTasks
    }

    /**
     * Returns (assignedDateIso, spec) pairs for [todayIso] only, ensuring scheduled tasks
     * are generated strictly for the current day.
     */
    fun tasksToEnsureOnDate(
        todayIso: String,
        @Suppress("UNUSED_PARAMETER") nowMillis: Long = System.currentTimeMillis()
    ): List<Pair<String, ScheduledTaskSpec>> =
        tasksForDate(todayIso).map { spec -> todayIso to spec }

    fun templateDocIdForSlug(slug: String): String {
        val safe = sanitizeSlug(slug)
        return "tpl_$safe".take(120)
    }

    fun sanitizeSlug(raw: String): String =
        raw.lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ñ", "n")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(50)
            .ifBlank { "tarea_diaria" }

    fun defaultTemplates(createdBy: String = "system"): List<com.example.data.model.DailyTaskTemplate> {
        val allDays = com.example.data.model.WeekdayOption.ALL_CODES
        val monToSat = com.example.data.model.WeekdayOption.MON_TO_SAT_CODES
        val allBranches = listOf(com.example.data.model.BranchOptions.ALL_BRANCHES_OPTION)

        return listOf(
            com.example.data.model.DailyTaskTemplate(
                id = templateDocIdForSlug("apertura_piso"),
                slug = "apertura_piso",
                title = "Apertura de piso y encendido de exhibición",
                description = "Verificar iluminación, limpieza de pasillos y etiquetas de precio visibles.",
                priority = com.example.data.model.TaskPriority.NORMAL.name,
                daysOfWeek = allDays,
                targetBranches = allBranches,
                dueTime = "23:59",
                deadlineLabel = "Límite: Hoy al cierre",
                dueDayOffset = 0L,
                active = true,
                createdBy = createdBy
            ),
            com.example.data.model.DailyTaskTemplate(
                id = templateDocIdForSlug("revision_exhibicion"),
                slug = "revision_exhibicion",
                title = "Revisión de exhibición destacada",
                description = "Confirmar disponibilidad de modelos promocionales y estado general de salas.",
                priority = com.example.data.model.TaskPriority.NORMAL.name,
                daysOfWeek = allDays,
                targetBranches = allBranches,
                dueTime = "23:59",
                deadlineLabel = "Límite: Hoy al cierre",
                dueDayOffset = 0L,
                active = true,
                createdBy = createdBy
            ),
            com.example.data.model.DailyTaskTemplate(
                id = templateDocIdForSlug("cierre_caja_credito"),
                slug = "cierre_caja_credito",
                title = "Cierre de caja y orden en módulo de crédito",
                description = "Resguardar papelería y dejar el área de atención lista para el siguiente turno.",
                priority = com.example.data.model.TaskPriority.NORMAL.name,
                daysOfWeek = allDays,
                targetBranches = allBranches,
                dueTime = "23:59",
                deadlineLabel = "Límite: Hoy al cierre",
                dueDayOffset = 0L,
                active = true,
                createdBy = createdBy
            ),
            com.example.data.model.DailyTaskTemplate(
                id = templateDocIdForSlug(DAILY_SALES_CLOSING_SLUG),
                slug = DAILY_SALES_CLOSING_SLUG,
                title = DAILY_SALES_CLOSING_TITLE,
                description = "Capturar el cierre de operaciones con las 5 métricas del día y adjuntar respaldo.",
                priority = com.example.data.model.TaskPriority.NORMAL.name,
                daysOfWeek = allDays,
                targetBranches = allBranches,
                dueTime = "23:59",
                deadlineLabel = "Jornada: Desde 8:00 a.m. al cierre",
                dueDayOffset = 0L,
                active = true,
                createdBy = createdBy
            ),
            com.example.data.model.DailyTaskTemplate(
                id = templateDocIdForSlug("avance_ventas_1400"),
                slug = "avance_ventas_1400",
                title = "Actualización de avance de ventas (2:00 p.m.)",
                description = "Actualizar el avance de ventas de las 2:00 p.m. y documentar con evidencia fotográfica.",
                priority = com.example.data.model.TaskPriority.NORMAL.name,
                daysOfWeek = monToSat,
                targetBranches = allBranches,
                dueTime = "17:59",
                deadlineLabel = "Horario: 2:00 p.m. – 5:59 p.m.",
                dueDayOffset = 0L,
                active = true,
                createdBy = createdBy
            ),
            com.example.data.model.DailyTaskTemplate(
                id = templateDocIdForSlug("avance_ventas_1800"),
                slug = "avance_ventas_1800",
                title = "Actualización de avance de ventas (6:00 p.m.)",
                description = "Actualizar el avance de ventas de las 6:00 p.m. y documentar con evidencia fotográfica.",
                priority = com.example.data.model.TaskPriority.NORMAL.name,
                daysOfWeek = monToSat,
                targetBranches = allBranches,
                dueTime = "23:59",
                deadlineLabel = "Horario: 6:00 p.m. – cierre",
                dueDayOffset = 0L,
                active = true,
                createdBy = createdBy
            ),
            com.example.data.model.DailyTaskTemplate(
                id = templateDocIdForSlug("inventario"),
                slug = "inventario",
                title = "Inventario",
                description = "Realizar conteo y validación de inventario de viernes. Se debe completar a más tardar el día siguiente al cierre.",
                priority = com.example.data.model.TaskPriority.NORMAL.name,
                daysOfWeek = listOf(com.example.data.model.WeekdayOption.FRIDAY.code),
                targetBranches = allBranches,
                dueTime = "23:59",
                deadlineLabel = "Límite: Día siguiente al cierre",
                dueDayOffset = 1L,
                active = true,
                createdBy = createdBy
            ),
            com.example.data.model.DailyTaskTemplate(
                id = templateDocIdForSlug("checklist_domingos"),
                slug = "checklist_domingos",
                title = "Checklist domingos",
                description = "Completar checklist operativo dominical de sucursal y adjuntar evidencia fotográfica.",
                priority = com.example.data.model.TaskPriority.NORMAL.name,
                daysOfWeek = listOf(com.example.data.model.WeekdayOption.SUNDAY.code),
                targetBranches = allBranches,
                dueTime = "23:59",
                deadlineLabel = "Límite: Hoy al cierre",
                dueDayOffset = 0L,
                active = true,
                createdBy = createdBy
            ),
            com.example.data.model.DailyTaskTemplate(
                id = templateDocIdForSlug("checklist_cajas"),
                slug = "checklist_cajas",
                title = "Checklist cajas",
                description = "Revisar orden, cortes y documentación en área de cajas de domingo con foto de respaldo.",
                priority = com.example.data.model.TaskPriority.NORMAL.name,
                daysOfWeek = listOf(com.example.data.model.WeekdayOption.SUNDAY.code),
                targetBranches = allBranches,
                dueTime = "23:59",
                deadlineLabel = "Límite: Hoy al cierre",
                dueDayOffset = 0L,
                active = true,
                createdBy = createdBy
            ),
            com.example.data.model.DailyTaskTemplate(
                id = templateDocIdForSlug("examenes"),
                slug = "examenes",
                title = "Exámenes",
                description = "Completar exámenes de domingo y subir fotografía comprobante. Límite máximo: día siguiente hasta las 11:30 a.m.",
                priority = com.example.data.model.TaskPriority.NORMAL.name,
                daysOfWeek = listOf(com.example.data.model.WeekdayOption.SUNDAY.code),
                targetBranches = allBranches,
                dueTime = "11:30",
                deadlineLabel = "Límite: Día siguiente 11:30 a.m.",
                dueDayOffset = 1L,
                active = true,
                createdBy = createdBy
            )
        )
    }

    fun templatesMatching(
        templates: List<com.example.data.model.DailyTaskTemplate>,
        dateIso: String,
        branchName: String?
    ): List<com.example.data.model.DailyTaskTemplate> =
        templates.filter { it.appliesTo(dateIso, branchName) }

    fun isDailySalesClosingTask(task: com.example.data.model.AssignedTask): Boolean {
        val slug = DateAndFormatters.extractScheduledSlug(task.id)
        val closingTplId = templateDocIdForSlug(DAILY_SALES_CLOSING_SLUG)
        return task.title.trim().equals(DAILY_SALES_CLOSING_TITLE, ignoreCase = true) ||
            slug == DAILY_SALES_CLOSING_SLUG ||
            task.id.contains("_${DAILY_SALES_CLOSING_SLUG}_") ||
            task.id.endsWith("_${DAILY_SALES_CLOSING_SLUG}") ||
            task.templateId == closingTplId
    }

    fun isDailySalesClosingTemplate(template: com.example.data.model.DailyTaskTemplate): Boolean {
        val closingTplId = templateDocIdForSlug(DAILY_SALES_CLOSING_SLUG)
        return template.slug == DAILY_SALES_CLOSING_SLUG ||
            template.id == closingTplId ||
            template.title.trim().equals(DAILY_SALES_CLOSING_TITLE, ignoreCase = true)
    }

    fun filterTasksForRole(
        tasks: List<com.example.data.model.AssignedTask>,
        isAdmin: Boolean
    ): List<com.example.data.model.AssignedTask> =
        if (isAdmin) tasks.filterNot(::isDailySalesClosingTask) else tasks

    fun filterTemplatesForRole(
        templates: List<com.example.data.model.DailyTaskTemplate>,
        isAdmin: Boolean
    ): List<com.example.data.model.DailyTaskTemplate> =
        if (isAdmin) templates.filterNot(::isDailySalesClosingTemplate) else templates
}


