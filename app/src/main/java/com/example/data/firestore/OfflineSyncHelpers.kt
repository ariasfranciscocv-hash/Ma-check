package com.example.data.firestore

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

data class WriteOutcome<T>(
    val value: T,
    val syncedWithServer: Boolean
)

data class SnapshotSyncResult<T>(
    val items: List<T>,
    val pendingWriteIds: Set<String>
) {
    val pendingCount: Int get() = pendingWriteIds.size
}

/**
 * Awaits a Firestore write [Task] without hanging indefinitely when offline.
 *
 * Firestore writes commit immediately to local SQLite persistence before server
 * acknowledgment, while the returned [Task] only resolves once the server confirms.
 * When offline or on a stalled network, this helper returns `false` after the grace
 * window so the UI updates optimistically and Firestore syncs in the background.
 */
suspend fun awaitWriteOfflineAware(
    task: Task<Void>,
    isOnline: Boolean,
    operationType: OperationType,
    path: String,
    onlineTimeoutMs: Long = 3_000L,
    offlineGraceMs: Long = 250L
): Boolean {
    task.addOnFailureListener { error ->
        handleFirestoreError(error, operationType, path)
    }
    val waitMs = if (isOnline) onlineTimeoutMs else offlineGraceMs
    val completed = withTimeoutOrNull(waitMs) {
        task.await()
        true
    }
    return completed == true
}
