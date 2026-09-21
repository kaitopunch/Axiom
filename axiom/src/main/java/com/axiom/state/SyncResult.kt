package com.axiom.state

/** The outcome of one attempt, as [com.axiom.Axiom.run] returns it and the worker maps it. */
sealed interface SyncResult {
    /** The store was written at [syncedAt]. */
    data class Success(
        val syncedAt: Long,
    ) : SyncResult

    /**
     * The fetch (and transform) ran and produced exactly what the store already held, so nothing was
     * written and no `data()` collector re-emits. Still a successful sync: [syncedAt] is the new
     * `lastSyncedAt`, and the freshness window starts over from it.
     */
    data class Unchanged(
        val syncedAt: Long,
    ) : SyncResult

    /** Nothing was fetched: the store was younger than the task's `staleAfter` (or `AxiomConfig.defaultStaleAfter`). */
    data class Skipped(
        val lastSyncedAt: Long,
    ) : SyncResult

    data class Failure(
        val error: AxiomError,
    ) : SyncResult
}
