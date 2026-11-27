package com.example

import com.example.data.model.BiweeklyQuota
import com.example.data.model.BranchOptions
import com.example.data.repository.AppRepository
import com.example.utils.AppCrashGuard
import com.example.utils.DateAndFormatters
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CrashGuardAndAdminFlowTest {

    @Before
    fun setUp() {
        AppCrashGuard.install(null)
        AppCrashGuard.resetForTests()
    }

    @After
    fun tearDown() {
        AppCrashGuard.resetForTests()
    }

    @Test
    fun `AppCrashGuard captures non-fatal and background uncaught exceptions cleanly`() {
        assertNull(AppCrashGuard.errorState.value)

        AppCrashGuard.reportNonFatal("Fallo simulado", IllegalStateException("Error de prueba"))
        val reported = AppCrashGuard.errorState.value
        assertNotNull(reported)
        assertTrue(reported!!.message.contains("IllegalStateException"))
        assertTrue(reported.message.contains("Error de prueba"))

        AppCrashGuard.clearError()
        assertNull(AppCrashGuard.errorState.value)

        val worker = Thread({
            throw RuntimeException("Crash en hilo secundario")
        }, "test-worker-thread")
        worker.start()
        worker.join(2000L)

        val captured = AppCrashGuard.errorState.value
        assertNotNull(captured)
        assertEquals("test-worker-thread", captured!!.threadName)
        assertTrue(captured.message.contains("Crash en hilo secundario"))
    }

    @Test
    fun `Admin default branch mode and bootstrapped admin rules hold`() {
        assertEquals("Todas las Sucursales", BranchOptions.ALL_BRANCHES_OPTION)
        assertEquals(15L, BiweeklyQuota().totalDays)
        assertTrue(AppRepository.isBootstrappedAdmin("arias.francisco.cv@gmail.com", true))
        assertTrue(AppRepository.isBootstrappedAdmin("Arias.Francisco.CV@GMAIL.COM", true))
        assertTrue(AppRepository.isBootstrappedAdmin("  ARIAS.FRANCISCO.CV@gmail.com ", true))
        assertFalse(AppRepository.isBootstrappedAdmin("arias.francisco.cv@gmail.com", false))
        assertFalse(AppRepository.isBootstrappedAdmin("sucursal87@gmail.com", true))
        assertEquals("2026-11_Q1", DateAndFormatters.biweeklyPeriodIdForIso("2026-11-15"))
        assertEquals("2026-11_Q2", DateAndFormatters.biweeklyPeriodIdForIso("2026-11-16"))
    }

    @Test
    fun `extractScheduledAssignedDate and extractScheduledSlug parse task ids cleanly`() {
        val taskId = "sched_user123_2026-11-28_avance_ventas_1400"
        assertEquals("2026-11-28", DateAndFormatters.extractScheduledAssignedDate(taskId))
        assertEquals("avance_ventas_1400", DateAndFormatters.extractScheduledSlug(taskId))
    }
}
