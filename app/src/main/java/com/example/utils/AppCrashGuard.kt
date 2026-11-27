package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean

data class TechnicalErrorReport(
    val title: String,
    val message: String,
    val stackTrace: String,
    val threadName: String,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    val fullTechnicalText: String
        get() = buildString {
            appendLine(title)
            appendLine("Hilo: $threadName")
            appendLine("Mensaje: $message")
            if (stackTrace.isNotBlank()) {
                appendLine()
                append(stackTrace)
            }
        }.trim()
}

/**
 * Installs a global uncaught exception handler and holds technical error state so
 * unexpected runtime errors are surfaced cleanly on screen instead of silent crashes.
 */
object AppCrashGuard {

    private const val TAG = "AppCrashGuard"
    private const val PREFS_NAME = "metacheck_crash_guard"
    private const val KEY_TITLE = "last_title"
    private const val KEY_MESSAGE = "last_message"
    private const val KEY_STACK = "last_stack"
    private const val KEY_THREAD = "last_thread"
    private const val KEY_TIMESTAMP = "last_timestamp"

    private val installed = AtomicBoolean(false)
    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    private val _errorState = MutableStateFlow<TechnicalErrorReport?>(null)
    val errorState: StateFlow<TechnicalErrorReport?> = _errorState.asStateFlow()

    fun install(context: Context? = null) {
        if (context != null) {
            appContext = context.applicationContext
            restorePendingPersistedError()
        }
        if (!installed.compareAndSet(false, true)) return

        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val report = buildReport(
                title = "Error inesperado en tiempo de ejecución",
                threadName = thread.name,
                throwable = throwable
            )
            runCatching { Log.e(TAG, "Uncaught exception on thread ${thread.name}", throwable) }
            persistReportSynchronously(report)
            _errorState.value = report

            // On background threads, we can keep the UI alive to render the error overlay.
            // On the Android main looper thread, Android cannot continue dispatching UI frames,
            // so after synchronously persisting the technical report (for immediate display on relaunch),
            // delegate to the system default handler if the process cannot stay responsive.
            val isMainThread = runCatching { Looper.getMainLooper().thread === thread }.getOrDefault(false)
            if (isMainThread || throwable is VirtualMachineError) {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun reportNonFatal(
        title: String,
        throwable: Throwable,
        threadName: String = Thread.currentThread().name
    ) {
        val report = buildReport(title = title, threadName = threadName, throwable = throwable)
        runCatching { Log.e(TAG, "$title ($threadName)", throwable) }
        _errorState.value = report
    }

    fun reportTechnicalMessage(
        title: String,
        technicalMessage: String,
        threadName: String = Thread.currentThread().name
    ) {
        val report = TechnicalErrorReport(
            title = title,
            message = technicalMessage.ifBlank { "Error técnico desconocido." },
            stackTrace = "",
            threadName = threadName
        )
        runCatching { Log.e(TAG, "$title: $technicalMessage") }
        _errorState.value = report
    }

    fun clearError() {
        _errorState.value = null
        runCatching { prefs()?.edit()?.clear()?.apply() }
    }

    internal fun resetForTests() {
        _errorState.value = null
        runCatching { prefs()?.edit()?.clear()?.commit() }
    }

    private fun buildReport(
        title: String,
        threadName: String,
        throwable: Throwable
    ): TechnicalErrorReport {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val rawTrace = sw.toString().take(4000)
        val conciseMessage = buildString {
            append(throwable::class.java.simpleName)
            val msg = throwable.localizedMessage ?: throwable.message
            if (!msg.isNullOrBlank()) {
                append(": ")
                append(msg)
            }
        }
        return TechnicalErrorReport(
            title = title,
            message = conciseMessage,
            stackTrace = rawTrace,
            threadName = threadName
        )
    }

    private fun persistReportSynchronously(report: TechnicalErrorReport) {
        runCatching {
            prefs()?.edit()
                ?.putString(KEY_TITLE, report.title)
                ?.putString(KEY_MESSAGE, report.message)
                ?.putString(KEY_STACK, report.stackTrace)
                ?.putString(KEY_THREAD, report.threadName)
                ?.putLong(KEY_TIMESTAMP, report.timestampMillis)
                ?.commit()
        }
    }

    private fun restorePendingPersistedError() {
        runCatching {
            val sp = prefs() ?: return
            val message = sp.getString(KEY_MESSAGE, null) ?: return
            val title = sp.getString(KEY_TITLE, "Error inesperado") ?: "Error inesperado"
            val stack = sp.getString(KEY_STACK, "") ?: ""
            val thread = sp.getString(KEY_THREAD, "main") ?: "main"
            val ts = sp.getLong(KEY_TIMESTAMP, System.currentTimeMillis())
            _errorState.value = TechnicalErrorReport(
                title = title,
                message = message,
                stackTrace = stack,
                threadName = thread,
                timestampMillis = ts
            )
        }
    }

    private fun prefs(): SharedPreferences? =
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
