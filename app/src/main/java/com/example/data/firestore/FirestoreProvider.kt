package com.example.data.firestore

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings

/**
 * Provides the named Cloud Firestore instance configured with durable local disk persistence
 * so offline task completions, photo evidences, and sales closings survive restarts and sync
 * automatically when connectivity returns.
 */
object FirestoreProvider {

    private const val TAG = "FirestoreProvider"
    const val CACHE_SIZE_BYTES: Long = 100L * 1024L * 1024L // 100 MB

    private val configuredDatabases = mutableSetOf<String>()

    fun buildPersistentSettings(): FirebaseFirestoreSettings = firestoreSettings {
        setLocalCacheSettings(
            persistentCacheSettings {
                setSizeBytes(CACHE_SIZE_BYTES)
            }
        )
    }

    fun getInstance(context: Context): FirebaseFirestore {
        val appContext = context.applicationContext
        val databaseId = appContext.getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(databaseId)
        synchronized(this) {
            if (databaseId !in configuredDatabases) {
                try {
                    firestore.firestoreSettings = buildPersistentSettings()
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Firestore settings already initialized for $databaseId", e)
                }
                configuredDatabases.add(databaseId)
            }
        }
        return firestore
    }
}
