package com.github.damontecres.stashapp.folders.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-server bookkeeping for the Folders sync engine.
 *
 * - `lastSyncAtEpochMs` drives the delta query (`updated_at > lastSync`).
 * - `scanComplete` is `false` until the initial full scan finishes; the next launch
 *   uses this to decide whether to resume the initial scan or to issue a delta sync.
 * - `scanProgressTotal` / `scanProgressDone` back the progress chip in the UI while a
 *   scan is in flight; both are nullable because we may not know the total up front.
 */
@Entity(tableName = "folder_sync_state")
data class FolderSyncState(
    @PrimaryKey
    val serverUrl: String,
    val lastSyncAtEpochMs: Long?,
    val scanComplete: Boolean,
    val scanProgressTotal: Int?,
    val scanProgressDone: Int?,
)
