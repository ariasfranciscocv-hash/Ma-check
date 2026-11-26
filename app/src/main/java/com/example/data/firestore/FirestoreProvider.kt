package com.example.data.firestore

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings

object FirestoreProvider {

    private const val TAG = "FirestoreProvider"
    private var configured = false

    fun buildOnlineOnlySettings(): FirebaseFirestoreSettings = firestoreSettings {
        setLocalCacheSettings(memoryCacheSettings {})
    }

    fun getInstance(context: Context): FirebaseFirestore {
        val firestore = FirebaseFirestore.getInstance()
        synchronized(this) {
            if (!configured) {
                try {
                    firestore.firestoreSettings = buildOnlineOnlySettings()
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Firestore settings already initialized", e)
                }
                configured = true
            }
        }
        return firestore
    }
}
