package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "metacheck_preferences")

class AppPreferences(private val context: Context) {

    private fun briefingKey(uid: String) = stringPreferencesKey("last_briefing_date_$uid")
    private fun alertedKey(uid: String) = stringSetPreferencesKey("alerted_notifications_$uid")
    private fun branchKey(uid: String) = stringPreferencesKey("last_branch_$uid")
    private fun scheduledSyncKey(uid: String) = stringPreferencesKey("last_scheduled_sync_$uid")
    private fun dailyClosingResetKey(uid: String) = stringPreferencesKey("last_daily_closing_reset_$uid")

    private val safeDataFlow = context.dataStore.data.catch { error ->
        if (error is IOException) {
            emit(emptyPreferences())
        } else {
            throw error
        }
    }

    fun observeLastBriefingDate(uid: String): Flow<String?> =
        safeDataFlow.map { prefs -> prefs[briefingKey(uid)] }

    suspend fun markBriefingSeenToday(uid: String, dateString: String) {
        safeEdit { prefs ->
            prefs[briefingKey(uid)] = dateString
        }
    }

    fun observeLastBranch(uid: String): Flow<String?> =
        safeDataFlow.map { prefs -> prefs[branchKey(uid)] }

    suspend fun saveLastBranch(uid: String, branchName: String) {
        safeEdit { prefs ->
            prefs[branchKey(uid)] = branchName
        }
    }

    suspend fun getLastScheduledSyncDate(uid: String): String? =
        runCatching { safeDataFlow.map { prefs -> prefs[scheduledSyncKey(uid)] }.firstOrNull() }
            .getOrNull()

    suspend fun markScheduledSyncDoneToday(uid: String, dateIso: String) {
        safeEdit { prefs ->
            prefs[scheduledSyncKey(uid)] = dateIso
        }
    }

    suspend fun getLastDailyClosingResetDate(uid: String): String? =
        runCatching { safeDataFlow.map { prefs -> prefs[dailyClosingResetKey(uid)] }.firstOrNull() }
            .getOrNull()

    suspend fun markDailyClosingResetDoneToday(uid: String, dateIso: String) {
        safeEdit { prefs ->
            prefs[dailyClosingResetKey(uid)] = dateIso
        }
    }

    fun observeAlertedNotificationIds(uid: String): Flow<Set<String>> =
        safeDataFlow.map { prefs -> prefs[alertedKey(uid)] ?: emptySet() }

    suspend fun markNotificationsAlerted(uid: String, ids: Collection<String>) {
        if (ids.isEmpty()) return
        safeEdit { prefs ->
            val current = prefs[alertedKey(uid)] ?: emptySet()
            prefs[alertedKey(uid)] = (current + ids).toList().takeLast(100).toSet()
        }
    }

    private suspend fun safeEdit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            context.dataStore.edit(block)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Avoid crashing the UI if local preferences fail to write
        }
    }
}
