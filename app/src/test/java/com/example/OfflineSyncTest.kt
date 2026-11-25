package com.example

import com.example.data.firestore.FirestoreProvider
import com.example.data.firestore.OperationType
import com.example.data.firestore.SnapshotSyncResult
import com.example.data.firestore.awaitWriteOfflineAware
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineSyncTest {

    @Test
    fun `persistent settings configures 100MB local disk cache`() {
        val settings = FirestoreProvider.buildPersistentSettings()
        val cacheSettings = settings.cacheSettings
        assertTrue(cacheSettings is PersistentCacheSettings)
        val persistent = cacheSettings as PersistentCacheSettings
        assertEquals(FirestoreProvider.CACHE_SIZE_BYTES, persistent.sizeBytes)
        assertEquals(100L * 1024L * 1024L, persistent.sizeBytes)
    }

    @Test
    fun `awaitWriteOfflineAware returns true when task completes online`() = runTest {
        val tcs = TaskCompletionSource<Void>()
        tcs.setResult(null)

        val synced = awaitWriteOfflineAware(
            task = tcs.task,
            isOnline = true,
            operationType = OperationType.UPDATE,
            path = "tasks/task_1",
            onlineTimeoutMs = 500L,
            offlineGraceMs = 50L
        )

        assertTrue(synced)
    }

    @Test
    fun `awaitWriteOfflineAware returns false immediately when offline and task waits for server`() = runTest {
        val pendingTask = TaskCompletionSource<Void>().task

        val synced = awaitWriteOfflineAware(
            task = pendingTask,
            isOnline = false,
            operationType = OperationType.UPDATE,
            path = "tasks/task_2",
            onlineTimeoutMs = 1_000L,
            offlineGraceMs = 40L
        )

        assertFalse(synced)
    }

    @Test
    fun `awaitWriteOfflineAware returns false after timeout if connection stalls mid write`() = runTest {
        val stalledTask = TaskCompletionSource<Void>().task

        val synced = awaitWriteOfflineAware(
            task = stalledTask,
            isOnline = true,
            operationType = OperationType.WRITE,
            path = "sales_entries/entry_1",
            onlineTimeoutMs = 60L,
            offlineGraceMs = 20L
        )

        assertFalse(synced)
    }

    @Test
    fun `snapshotSyncResult counts pending write ids accurately`() {
        val result = SnapshotSyncResult(
            items = listOf("a", "b", "c"),
            pendingWriteIds = setOf("a", "c")
        )
        assertEquals(2, result.pendingCount)
        assertTrue("a" in result.pendingWriteIds)
    }
}
