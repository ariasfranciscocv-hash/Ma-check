package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "metacheck_preferences")

class AppPreferences(private val context: Context) {

    private fun briefingKey(uid: String) = stringPreferencesKey("last_briefing_date_$uid")
    private fun alertedKey(uid: String) = stringSetPreferencesKey("alerted_notifications_$uid")
    private fun branchKey(uid: String) = stringPreferencesKey("last_branch_$uid")
    private fun tasksSyncDateKey(uid: String) = stringPreferencesKey("last_tasks_sync_date_$uid")

    fun observeLastBriefingDate(uid: String): Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[briefingKey(uid)] }

    suspend fun markBriefingSeenToday(uid: String, dateString: String) {
        context.dataStore.edit { prefs ->
            prefs[briefingKey(uid)] = dateString
        }
    }

    fun observeLastBranch(uid: String): Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[branchKey(uid)] }

    suspend fun saveLastBranch(uid: String, branchName: String) {
        context.dataStore.edit { prefs ->
            prefs[branchKey(uid)] = branchName
        }
    }

    fun observeLastTasksSyncDate(uid: String): Flow<String?> =
        context.dataStore.data.map { prefs -> prefs[tasksSyncDateKey(uid)] }

    suspend fun saveLastTasksSyncDate(uid: String, dateString: String) {
        context.dataStore.edit { prefs ->
            prefs[tasksSyncDateKey(uid)] = dateString
        }
    }

    fun observeAlertedNotificationIds(uid: String): Flow<Set<String>> =
        context.dataStore.data.map { prefs -> prefs[alertedKey(uid)] ?: emptySet() }

    suspend fun markNotificationsAlerted(uid: String, ids: Collection<String>) {
        if (ids.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[alertedKey(uid)] ?: emptySet()
            prefs[alertedKey(uid)] = (current + ids).toList().takeLast(100).toSet()
        }
    }
}
