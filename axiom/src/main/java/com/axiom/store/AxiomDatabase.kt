package com.axiom.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers

/**
 * The SDK's own database: payloads and sync state, nothing of the host's. Its file name is
 * [com.axiom.AxiomConfig.databaseName] so it can never collide with a database the host already has.
 *
 * Version 1. Changing any entity here means a migration in the same change — there is no destructive
 * fallback configured, and this file lives on consumers' devices.
 */
@Database(
    entities = [EntryEntity::class, ChunkEntity::class, SyncStateEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class AxiomDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao

    abstract fun syncStates(): SyncStateDao

    companion object {
        fun open(
            context: Context,
            name: String,
            inMemory: Boolean,
        ): AxiomDatabase =
            if (inMemory) {
                Room
                    .inMemoryDatabaseBuilder(context, AxiomDatabase::class.java)
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .allowMainThreadQueries()
                    .build()
            } else {
                Room
                    .databaseBuilder(context, AxiomDatabase::class.java, name)
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .build()
            }
    }
}
