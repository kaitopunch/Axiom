package com.axiom.store

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/** A header and its chunks, read inside one transaction so they cannot disagree. */
internal data class EntrySnapshot(
    val entry: EntryEntity,
    val chunks: List<String>,
)

@Dao
internal interface EntryDao {
    @Query("SELECT * FROM axiom_entries WHERE entry_key = :key")
    fun observeEntry(key: String): Flow<EntryEntity?>

    @Query("SELECT * FROM axiom_entries WHERE entry_key = :key")
    suspend fun entry(key: String): EntryEntity?

    @Query("SELECT payload FROM axiom_chunks WHERE entry_key = :key ORDER BY position ASC")
    suspend fun chunks(key: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: EntryEntity)

    @Insert
    suspend fun insertChunks(chunks: List<ChunkEntity>)

    @Query("DELETE FROM axiom_chunks WHERE entry_key = :key")
    suspend fun deleteChunks(key: String)

    @Query("DELETE FROM axiom_entries WHERE entry_key = :key")
    suspend fun deleteEntry(key: String)

    @Query("DELETE FROM axiom_chunks")
    suspend fun deleteAllChunks()

    @Query("DELETE FROM axiom_entries")
    suspend fun deleteAllEntries()

    @Transaction
    suspend fun snapshot(key: String): EntrySnapshot? {
        val entry = entry(key) ?: return null
        return EntrySnapshot(entry, chunks(key))
    }

    /**
     * Replaces [key]'s payload whole — unless the payload it holds already has [digest] (its
     * [fingerprint]), in which case nothing is touched and this returns `false`. Readers see either the
     * old value or the new one, never a mix.
     *
     * The comparison is against the stored copy read back and hashed here, in the same transaction, so
     * "unchanged" is judged against what is actually on disk at the moment of the write. It costs a
     * read of the old chunks; a differing `chunk_count` settles it before that read, since both sides
     * are chunked by the same rule. A write that is skipped leaves [EntryEntity.revision] where it was,
     * which is what keeps every `data()` collector from re-parsing a payload it already holds.
     */
    @Transaction
    suspend fun replace(
        key: String,
        chunks: List<String>,
        digest: String,
        now: Long,
    ): Boolean {
        val previous = entry(key)
        if (previous != null && previous.chunkCount == chunks.size && fingerprint(chunks(key).joinToString(separator = "")) == digest) {
            return false
        }
        deleteChunks(key)
        insertChunks(chunks.mapIndexed { index, payload -> ChunkEntity(key, index, payload) })
        insertEntry(
            EntryEntity(
                key = key,
                revision = (previous?.revision ?: 0L) + 1,
                updatedAt = now,
                chunkCount = chunks.size,
            ),
        )
        return true
    }

    @Transaction
    suspend fun remove(key: String) {
        deleteChunks(key)
        deleteEntry(key)
    }

    @Transaction
    suspend fun removeAll() {
        deleteAllChunks()
        deleteAllEntries()
    }
}

@Dao
internal interface SyncStateDao {
    @Query("SELECT * FROM axiom_sync_state WHERE entry_key = :key")
    fun observe(key: String): Flow<SyncStateEntity?>

    @Query("SELECT * FROM axiom_sync_state")
    fun observeAll(): Flow<List<SyncStateEntity>>

    @Query("SELECT * FROM axiom_sync_state WHERE entry_key = :key")
    suspend fun get(key: String): SyncStateEntity?

    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    /** On process start nothing can still be running, whatever a row left behind by a killed worker says. */
    @Query("UPDATE axiom_sync_state SET running = 0 WHERE running = 1")
    suspend fun resetRunning()

    @Query("DELETE FROM axiom_sync_state WHERE entry_key = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM axiom_sync_state")
    suspend fun deleteAll()
}
