package com.axiom.store

import com.axiom.state.AxiomError
import com.axiom.state.SyncState
import kotlinx.coroutines.flow.Flow

/** Writes the persisted half of a task's state; see [SyncStateEntity]. */
internal class SyncStateStore(
    private val dao: SyncStateDao,
) {
    fun observe(key: String): Flow<SyncStateEntity?> = dao.observe(key)

    fun observeAll(): Flow<List<SyncStateEntity>> = dao.observeAll()

    suspend fun get(key: String): SyncStateEntity? = dao.get(key)

    suspend fun markRunning(
        key: String,
        attempt: Int,
    ) {
        val previous = dao.get(key)
        dao.upsert(
            (previous ?: empty(key)).copy(running = true, attempt = attempt),
        )
    }

    suspend fun markIdle(key: String) {
        val previous = dao.get(key) ?: return
        dao.upsert(previous.copy(running = false))
    }

    suspend fun markSuccess(
        key: String,
        at: Long,
    ) {
        val previous = dao.get(key) ?: empty(key)
        dao.upsert(
            previous.copy(
                running = false,
                lastSuccessAt = at,
                errorKind = null,
                errorMessage = null,
                errorCode = null,
            ),
        )
    }

    suspend fun markFailure(
        key: String,
        error: AxiomError,
        at: Long,
    ) {
        val previous = dao.get(key) ?: empty(key)
        dao.upsert(
            previous.copy(
                running = false,
                lastFailureAt = at,
                errorKind = error.kind.name,
                errorMessage = error.message,
                errorCode = error.code,
            ),
        )
    }

    suspend fun resetRunning() = dao.resetRunning()

    suspend fun remove(key: String) = dao.delete(key)

    suspend fun removeAll() = dao.deleteAll()

    private fun empty(key: String) =
        SyncStateEntity(
            key = key,
            running = false,
            attempt = 0,
            lastSuccessAt = null,
            lastFailureAt = null,
            errorKind = null,
            errorMessage = null,
            errorCode = null,
        )
}

/** The persisted row, with no WorkManager information: what the last run left behind. */
internal fun SyncStateEntity?.toSettledState(): SyncState {
    if (this == null) return SyncState.Idle()
    if (running) return SyncState.Running(attempt, lastSuccessAt)
    val failedAfterSuccess =
        lastFailureAt != null && (lastSuccessAt == null || lastFailureAt > lastSuccessAt)
    return when {
        failedAfterSuccess ->
            SyncState.Failed(
                error =
                    AxiomError(
                        kind = errorKind?.let { runCatching { AxiomError.Kind.valueOf(it) }.getOrNull() } ?: AxiomError.Kind.UNKNOWN,
                        message = errorMessage ?: "unknown",
                        code = errorCode,
                    ),
                failedAt = requireNotNull(lastFailureAt),
                lastSyncedAt = lastSuccessAt,
            )
        lastSuccessAt != null -> SyncState.Success(lastSuccessAt)
        else -> SyncState.Idle()
    }
}
