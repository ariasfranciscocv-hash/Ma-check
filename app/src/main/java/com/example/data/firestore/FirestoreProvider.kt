package com.example.data.firestore

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings

/**
 * Provides the named Cloud Firestore instance configured with memory-only cache
 * (no offline local disk persistence).
 */
object FirestoreProvider {

    private const val TAG = "FirestoreProvider"
    private val configuredDatabases = mutableSetOf<String>()

    fun buildOnlineOnlySettings(): FirebaseFirestoreSettings = firestoreSettings {
        setLocalCacheSettings(memoryCacheSettings {})
    }

    fun getInstance(context: Context): FirebaseFirestore {
        val appContext = context.applicationContext
        val databaseId = appContext.getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(databaseId)
        synchronized(this) {
            if (databaseId !in configuredDatabases) {
                try {
                    firestore.firestoreSettings = buildOnlineOnlySettings()
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Firestore settings already initialized for $databaseId", e)
                }
                configuredDatabases.add(databaseId)
            }
        }
        return firestore
    }
}
