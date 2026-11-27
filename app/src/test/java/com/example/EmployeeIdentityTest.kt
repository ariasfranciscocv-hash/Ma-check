package com.example

import com.example.data.model.AssignedTask
import com.example.data.model.BiweeklyQuota
import com.example.data.model.BranchTaskCompletion
import com.example.data.model.BranchTaskLockHelper
import com.example.data.model.DailySalesEntry
import com.example.data.model.EmployeeIdentity
import com.example.data.model.EmployeeIdentityValidation
import com.example.data.model.TaskEvidence
import com.example.data.model.TaskStatus
import com.example.data.model.TaskType
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmployeeIdentityTest {

    @Test
    fun `validateFullRegistration enforces mandatory name, exact 6 digits with E prefix, and valid branch`() {
        // Short name rejected
        assertNotNull(
            EmployeeIdentityValidation.validateFullRegistration(
                fullName = "ab",
                payrollNumberRaw = "E019753",
                branchName = "Sucursal 107"
            )
        )
        // Blank payroll rejected
        assertNotNull(
            EmployeeIdentityValidation.validateFullRegistration(
                fullName = "Juan Pérez",
                payrollNumberRaw = "   ",
                branchName = "Sucursal 107"
            )
        )
        // Invalid branch rejected
        assertNotNull(
            EmployeeIdentityValidation.validateFullRegistration(
                fullName = "Juan Pérez",
                payrollNumberRaw = "E019753",
                branchName = "Sucursal Invalida"
            )
        )
        // Fewer than 6 digits rejected
        assertNotNull(
            EmployeeIdentityValidation.validateFullRegistration(
                fullName = "Juan Pérez",
                payrollNumberRaw = "E01975",
                branchName = "Sucursal 107"
            )
        )
        // More than 6 digits rejected
        assertNotNull(
            EmployeeIdentityValidation.validateFullRegistration(
                fullName = "Juan Pérez",
                payrollNumberRaw = "E0197539",
                branchName = "Sucursal 107"
            )
        )
        // Letters/spaces/special chars rejected
        assertNotNull(
            EmployeeIdentityValidation.validateFullRegistration(
                fullName = "Juan Pérez",
                payrollNumberRaw = "123 45!",
                branchName = "Sucursal 107"
            )
        )
        assertNotNull(
            EmployeeIdentityValidation.validateFullRegistration(
                fullName = "Juan Pérez",
                payrollNumberRaw = "ab-12345",
                branchName = "Sucursal 107"
            )
        )
        // Exact 6 digits without E passes and concatenates E
        assertNull(
            EmployeeIdentityValidation.validateFullRegistration(
                fullName = "Juan Pérez López",
                payrollNumberRaw = "019753",
                branchName = "Sucursal 107"
            )
        )
        // Exact E + 6 digits passes
        assertNull(
            EmployeeIdentityValidation.validateFullRegistration(
                fullName = "Juan Pérez López",
                payrollNumberRaw = "e019753",
                branchName = "Sucursal 107"
            )
        )
        assertEquals("E019753", EmployeeIdentityValidation.cleanPayrollInput("019753"))
        assertEquals("E019753", EmployeeIdentityValidation.cleanPayrollInput("e019753"))
        assertEquals("E019753", EmployeeIdentityValidation.normalizePayrollKey("019753"))
        assertEquals("019753", EmployeeIdentityValidation.filterPayrollDigits("E01-97 53abc89"))
        assertEquals("019753", EmployeeIdentityValidation.extractPayrollDigits("E019753"))
        assertEquals("E019753", EmployeeIdentityValidation.formatFullPayrollFromDigits("019753"))
    }

    @Test
    fun `employee identity transitions cleanly between complete and branch-released states`() {
        val complete = EmployeeIdentity(
            id = "u1",
            userId = "u1",
            email = "empleado@mueblesamerica.mx",
            fullName = "María López",
            payrollNumber = "E019753",
            payrollKey = "E019753",
            branchName = "Sucursal 107",
            active = true
        )
        assertTrue(complete.isComplete)
        assertFalse(complete.requiresBranchOnlyReassignment)
        assertEquals("E019753", complete.displayPayroll)
        assertEquals("Nómina: E019753 | Sucursal 107", complete.formattedTopBarTag)
        assertEquals("Nómina E019753 · María López", complete.formattedMovementAuthor)

        val released = complete.copy(branchName = null)
        assertFalse(released.isComplete)
        assertTrue(released.requiresBranchOnlyReassignment)
        assertEquals("Nómina: E019753", released.formattedTopBarTag)
    }

    @Test
    fun `sales entry and task evidence format employee payroll and name when present`() {
        val entryWithIdentity = DailySalesEntry(
            id = "e1",
            userId = "u1",
            userName = "E019753 · María López",
            branchName = "Sucursal 107",
            employeePayroll = "E019753",
            employeeName = "María López"
        )
        assertEquals("Nómina E019753 · María López", entryWithIdentity.formattedAuthorLabel)

        val legacyEntry = DailySalesEntry(
            id = "e2",
            userId = "u2",
            userName = "Sucursal 109"
        )
        assertEquals("Sucursal 109", legacyEntry.formattedAuthorLabel)

        val evidenceWithIdentity = TaskEvidence(
            id = "ev1",
            taskId = "t1",
            uploaderId = "u1",
            uploaderName = "E019753 · María López · Sucursal 107",
            employeePayroll = "E019753",
            employeeName = "María López",
            branchName = "Sucursal 107"
        )
        assertEquals(
            "Nómina E019753 · María López · Sucursal 107",
            evidenceWithIdentity.formattedUploaderLabel
        )
    }

    @Test
    fun `branch task lock generates identical lockId across coworkers of same branch`() {
        val aliceTask = AssignedTask(
            id = "sched_alice_2026-11-27_apertura_piso",
            assigneeId = "alice_uid",
            assigneeName = "Sucursal 107",
            title = "Apertura de piso y encendido de exhibición",
            taskType = TaskType.DAILY.name,
            dueDate = "2026-11-27",
            templateId = "tpl_apertura_piso"
        )
        val bobTaskWithoutTemplateId = AssignedTask(
            id = "sched_bob_2026-11-27_apertura_piso",
            assigneeId = "bob_uid",
            assigneeName = "Sucursal 107",
            title = "Apertura de piso y encendido de exhibición",
            taskType = TaskType.DAILY.name,
            dueDate = "2026-11-27",
            templateId = null
        )

        val aliceLock = BranchTaskLockHelper.buildLockIdForTask("Sucursal 107", aliceTask)
        val bobLock = BranchTaskLockHelper.buildLockIdForTask("Sucursal 107", bobTaskWithoutTemplateId)
        val otherBranchLock = BranchTaskLockHelper.buildLockIdForTask("Sucursal 109", bobTaskWithoutTemplateId)

        assertEquals(aliceLock, bobLock)
        assertTrue(aliceLock.matches(Regex("^[a-zA-Z0-9_-]{1,128}$")))
        assertFalse(aliceLock == otherBranchLock)
    }

    @Test
    fun `mergeTasksWithBranchCompletions locks coworker pending tasks and deduplicates admin view`() {
        val aliceTaskCompleted = AssignedTask(
            id = "sched_alice_2026-11-27_apertura_piso",
            assigneeId = "alice_uid",
            assigneeName = "Sucursal 107",
            title = "Apertura de piso",
            taskType = TaskType.DAILY.name,
            dueDate = "2026-11-27",
            status = TaskStatus.COMPLETED.name,
            hasPhotoEvidence = true,
            evidenceId = "ev_1",
            templateId = "tpl_apertura_piso",
            completedByUid = "alice_uid",
            completedByPayroll = "E019753",
            completedByName = "Alicia Gómez"
        )
        val bobTaskPending = AssignedTask(
            id = "sched_bob_2026-11-27_apertura_piso",
            assigneeId = "bob_uid",
            assigneeName = "Sucursal 107",
            title = "Apertura de piso",
            taskType = TaskType.DAILY.name,
            dueDate = "2026-11-27",
            status = TaskStatus.PENDING.name,
            templateId = "tpl_apertura_piso"
        )
        val completion = BranchTaskCompletion(
            id = BranchTaskLockHelper.buildLockIdForTask("Sucursal 107", aliceTaskCompleted),
            lockId = BranchTaskLockHelper.buildLockIdForTask("Sucursal 107", aliceTaskCompleted),
            branchName = "Sucursal 107",
            dayIso = "2026-11-27",
            periodId = "2026-11_Q2",
            taskKey = BranchTaskLockHelper.extractTaskKeyForBranchLock(aliceTaskCompleted),
            taskTitle = "Apertura de piso",
            templateId = "tpl_apertura_piso",
            taskId = aliceTaskCompleted.id,
            evidenceId = "ev_1",
            completedByUid = "alice_uid",
            completedByPayroll = "E019753",
            completedByName = "Alicia Gómez"
        )

        // Employee Bob sees his pending task marked completed & attributed to Alice's payroll
        val bobMerged = BranchTaskLockHelper.mergeTasksWithBranchCompletions(
            tasks = listOf(bobTaskPending),
            completions = listOf(completion),
            isAdmin = false,
            employeeBranch = "Sucursal 107",
            teamUsers = emptyList()
        )
        assertEquals(1, bobMerged.size)
        assertEquals(TaskStatus.COMPLETED, bobMerged.first().parsedStatus)
        assertEquals("E019753", bobMerged.first().completedByPayroll)
        assertEquals("Alicia Gómez", bobMerged.first().completedByName)
        assertEquals("Nómina E019753 · Alicia Gómez", bobMerged.first().formattedCompletedByLabel)

        // Admin sees deduplicated single branch entry with Alice's completion & evidenceId
        val users = listOf(
            UserProfile(userId = "alice_uid", branchName = "Sucursal 107", role = UserRole.EMPLOYEE.name),
            UserProfile(userId = "bob_uid", branchName = "Sucursal 107", role = UserRole.EMPLOYEE.name)
        )
        val adminMerged = BranchTaskLockHelper.mergeTasksWithBranchCompletions(
            tasks = listOf(bobTaskPending, aliceTaskCompleted),
            completions = listOf(completion),
            isAdmin = true,
            employeeBranch = null,
            teamUsers = users
        )
        assertEquals(1, adminMerged.size)
        assertEquals(TaskStatus.COMPLETED, adminMerged.first().parsedStatus)
        assertEquals("ev_1", adminMerged.first().evidenceId)
        assertEquals("E019753", adminMerged.first().completedByPayroll)
    }

    @Test
    fun `computeEmployeeBranchParticipationStats calculates branch totals and payroll share accurately`() {
        val bobIdentity = EmployeeIdentity(
            id = "bob_uid",
            userId = "bob_uid",
            email = "bob@mueblesamerica.mx",
            fullName = "Roberto Díaz",
            payrollNumber = "E019754",
            payrollKey = "E019754",
            branchName = "Sucursal 107",
            active = true
        )
        val quota = BiweeklyQuota(
            id = "q_1",
            title = "2ª Quincena · Noviembre 2026",
            startDate = "2026-11-16",
            endDate = "2026-11-30"
        )
        val tasks = listOf(
            // Completed by Bob (E019754)
            AssignedTask(
                id = "sched_bob_2026-11-26_apertura_piso",
                assigneeId = "bob_uid",
                assigneeName = "Sucursal 107",
                title = "Apertura de piso",
                taskType = TaskType.DAILY.name,
                dueDate = "2026-11-26",
                status = TaskStatus.COMPLETED.name,
                completedByUid = "bob_uid",
                completedByPayroll = "E019754",
                completedByName = "Roberto Díaz"
            ),
            // Completed by coworker Alice (E019753)
            AssignedTask(
                id = "sched_bob_2026-11-26_revision_exhibicion",
                assigneeId = "bob_uid",
                assigneeName = "Sucursal 107",
                title = "Revisión de exhibición",
                taskType = TaskType.DAILY.name,
                dueDate = "2026-11-26",
                status = TaskStatus.COMPLETED.name,
                completedByUid = "alice_uid",
                completedByPayroll = "E019753",
                completedByName = "Alicia Gómez"
            ),
            // Pending task for branch
            AssignedTask(
                id = "sched_bob_2026-11-27_apertura_piso",
                assigneeId = "bob_uid",
                assigneeName = "Sucursal 107",
                title = "Apertura de piso",
                taskType = TaskType.DAILY.name,
                dueDate = "2026-11-27",
                status = TaskStatus.PENDING.name
            ),
            // Previous period task (outside 2026-11-16..2026-11-30) should be excluded
            AssignedTask(
                id = "sched_bob_2026-11-10_apertura_piso",
                assigneeId = "bob_uid",
                assigneeName = "Sucursal 107",
                title = "Apertura de piso",
                taskType = TaskType.DAILY.name,
                dueDate = "2026-11-10",
                status = TaskStatus.COMPLETED.name,
                completedByUid = "bob_uid",
                completedByPayroll = "E019754"
            )
        )
        // Plus one coworker completion earlier in same period not in Bob's local tasks list
        val extraCompletion = BranchTaskCompletion(
            id = "lock_sucursal_107_2026-11-20_d_inventario",
            lockId = "lock_sucursal_107_2026-11-20_d_inventario",
            branchName = "Sucursal 107",
            dayIso = "2026-11-20",
            periodId = "2026-11_Q2",
            taskKey = "d_inventario",
            taskTitle = "Inventario",
            taskId = "sched_alice_2026-11-20_inventario",
            evidenceId = "ev_old",
            completedByUid = "alice_uid",
            completedByPayroll = "E019753",
            completedByName = "Alicia Gómez"
        )

        val stats = BranchTaskLockHelper.computeEmployeeBranchParticipationStats(
            identity = bobIdentity,
            tasks = tasks,
            completions = listOf(extraCompletion),
            activeQuota = quota,
            todayIso = "2026-11-27"
        )

        assertEquals("Sucursal 107", stats.branchName)
        assertEquals(4, stats.totalBranchTasks)
        assertEquals(1, stats.completedByMyPayroll)
        assertEquals(2, stats.completedByOtherPayrolls)
        assertEquals(1, stats.pendingTasks)
        assertEquals(25f, stats.myParticipationPercent, 0.01f)
    }
}
