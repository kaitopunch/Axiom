package com.axiom.store

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One stored payload's header. The JSON itself is in [ChunkEntity] rows, because Android reads a row
 * through a `CursorWindow` of 2 MB and one catalogue payload can be a megabyte of JSON already — a
 * single TEXT column would work right up until a payload grew, and then fail on the device, not in a
 * test.
 *
 * [revision] increments on every write and is what `data()` flows key their re-emission on; a wall-clock
 * stamp could collide when a transform's `publish` and its final write land in the same millisecond.
 */
@Entity(tableName = "axiom_entries")
internal data class EntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "entry_key")
    val key: String,
    @ColumnInfo(name = "revision")
    val revision: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "chunk_count")
    val chunkCount: Int,
)

@Entity(
    tableName = "axiom_chunks",
    primaryKeys = ["entry_key", "position"],
)
internal data class ChunkEntity(
    @ColumnInfo(name = "entry_key")
    val key: String,
    @ColumnInfo(name = "position")
    val position: Int,
    @ColumnInfo(name = "payload")
    val payload: String,
)

/**
 * The persisted half of a task's [com.axiom.state.SyncState]. WorkManager knows whether something is
 * enqueued; only this row knows how the last run ended, and it has to survive the process to say so on
 * the next launch.
 */
@Entity(tableName = "axiom_sync_state")
internal data class SyncStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "entry_key")
    val key: String,
    @ColumnInfo(name = "running")
    val running: Boolean,
    @ColumnInfo(name = "attempt")
    val attempt: Int,
    @ColumnInfo(name = "last_success_at")
    val lastSuccessAt: Long?,
    @ColumnInfo(name = "last_failure_at")
    val lastFailureAt: Long?,
    @ColumnInfo(name = "error_kind")
    val errorKind: String?,
    @ColumnInfo(name = "error_message")
    val errorMessage: String?,
    @ColumnInfo(name = "error_code")
    val errorCode: Int?,
)
