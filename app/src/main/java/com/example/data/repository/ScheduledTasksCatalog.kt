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

    val ALLOWED_SELF_SCHEDULED_TITLES: Set<String> = setOf(
        "Apertura de piso y encendido de exhibición",
        "Revisión de exhibición destacada",
        "Cierre de caja y orden en módulo de crédito",
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
     * Returns (assignedDateIso, spec) pairs for [todayIso] only, ensuring only
     * the current day's tasks are created.
     */
    fun tasksToEnsureOnDate(
        todayIso: String,
        @Suppress("UNUSED_PARAMETER") nowMillis: Long = System.currentTimeMillis()
    ): List<Pair<String, ScheduledTaskSpec>> {
        return tasksForDate(todayIso).map { spec -> todayIso to spec }
    }
}
