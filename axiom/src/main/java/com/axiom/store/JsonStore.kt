package com.axiom.store

import com.axiom.log.AxiomLogger
import com.axiom.log.warn
import com.axiom.serialization.AxiomSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.lang.reflect.Type

/**
 * Typed reads and writes over [EntryDao]: serialise, chunk, and back.
 *
 * A chunk is 200 000 chars — under 600 KB even if every char is three bytes of UTF-8, comfortably inside
 * a `CursorWindow`.
 */
internal class JsonStore(
    private val dao: EntryDao,
    private val serializer: AxiomSerializer,
    private val clock: () -> Long,
    private val logger: AxiomLogger,
) {
    /**
     * Stores [value] under [key] and returns whether anything changed. A value that serialises to the
     * bytes the store already holds is not written at all — see [EntryDao.replace] — so a sync that
     * fetched the same payload again does not wake every collector of `observe`.
     */
    suspend fun write(
        key: String,
        value: Any,
        type: Type,
    ): Boolean {
        val (json, digest) =
            withContext(Dispatchers.Default) {
                val json = serializer.toJson(value, type)
                json to fingerprint(json)
            }
        return dao.replace(key, json.chunked(CHUNK_CHARS), digest, clock())
    }

    suspend fun <T : Any> read(
        key: String,
        type: Type,
    ): T? {
        val snapshot = dao.snapshot(key) ?: return null
        return parse(key, snapshot, type)
    }

    /**
     * Re-emits after every write of [key]. Keyed on the entry's revision rather than on the payload so
     * a write is detected without reading a megabyte to compare it; parsed off the main thread.
     */
    fun <T : Any> observe(
        key: String,
        type: Type,
    ): Flow<T?> =
        dao
            .observeEntry(key)
            .map { it?.revision }
            .distinctUntilChanged()
            .map { revision ->
                if (revision == null) null else dao.snapshot(key)?.let { parse<T>(key, it, type) }
            }.flowOn(Dispatchers.Default)

    suspend fun remove(key: String) = dao.remove(key)

    suspend fun removeAll() = dao.removeAll()

    private suspend fun <T : Any> parse(
        key: String,
        snapshot: EntrySnapshot,
        type: Type,
    ): T? =
        withContext(Dispatchers.Default) {
            val json = snapshot.chunks.joinToString(separator = "")
            try {
                serializer.fromJson<T>(json, type)
            } catch (throwable: RuntimeException) {
                // A corrupt payload reads as "nothing stored". The next successful sync overwrites it;
                // crashing every collector of this key would not.
                logger.warn(throwable) { "Stored payload for '$key' does not parse as $type; treating as empty" }
                null
            }
        }

    private companion object {
        const val CHUNK_CHARS = 200_000
    }
}
