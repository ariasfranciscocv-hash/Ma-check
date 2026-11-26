package com.example

import com.example.data.model.AssignedTask
import com.example.data.model.TaskStatus
import com.example.data.model.UserProfile
import com.example.data.model.buildBranchTaskSummaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BranchSummaryTest {

    @Test
    fun `branch indicator is green only when all branch tasks are completed and red otherwise`() {
        val users = listOf(
            UserProfile(userId = "u107", branchName = "Sucursal 107", displayName = "Sucursal 107"),
            UserProfile(userId = "u109", branchName = "Sucursal 109", displayName = "Sucursal 109")
        )
        val tasks = listOf(
            AssignedTask(
                id = "t1",
                assigneeId = "u107",
                assigneeName = "Sucursal 107",
                title = "Inventario",
                status = TaskStatus.COMPLETED.name
            ),
            AssignedTask(
                id = "t2",
                assigneeId = "u107",
                assigneeName = "Sucursal 107",
                title = "Checklist cajas",
                status = TaskStatus.COMPLETED.name
            ),
            AssignedTask(
                id = "t3",
                assigneeId = "u109",
                assigneeName = "Sucursal 109",
                title = "Inventario",
                status = TaskStatus.COMPLETED.name
            ),
            AssignedTask(
                id = "t4",
                assigneeId = "u109",
                assigneeName = "Sucursal 109",
                title = "Exámenes",
                status = TaskStatus.PENDING.name
            )
        )

        val summaries = buildBranchTaskSummaries(users, tasks)
        assertEquals(6, summaries.size)

        val s87 = summaries.first { it.branchName == "Sucursal 87" }
        assertFalse(s87.allTasksCompleted)
        assertEquals(0, s87.totalCount)

        val s107 = summaries.first { it.branchName == "Sucursal 107" }
        assertTrue(s107.allTasksCompleted)
        assertEquals(2, s107.completedCount)
        assertEquals(2, s107.totalCount)

        val s109 = summaries.first { it.branchName == "Sucursal 109" }
        assertFalse(s109.allTasksCompleted)
        assertEquals(1, s109.completedCount)
        assertEquals(2, s109.totalCount)

        val s110 = summaries.first { it.branchName == "Sucursal 110" }
        assertFalse(s110.allTasksCompleted)
        assertEquals(0, s110.totalCount)
    }
}
