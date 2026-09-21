package com.axiom.state

/**
 * What one task's sync is doing, for the layer above to render however it likes.
 *
 * Every state carries [lastSyncedAt] — the store may well hold data from an earlier run while a new one
 * is scheduled, running or failing, and a screen with rows to show usually wants to show them.
 *
 * The shape a screen typically wants collapses to three cases: in flight ([isInFlight]), settled
 * ([isSettled]), or [Idle] — never asked. Whether "settled with an empty store" is an error is the
 * screen's decision; Axiom does not know what empty means for a given payload.
 */
sealed interface SyncState {
    val lastSyncedAt: Long?

    /** Nothing scheduled and nothing running. [lastSyncedAt] is null only if the task has never succeeded. */
    data class Idle(
        override val lastSyncedAt: Long? = null,
    ) : SyncState

    /** Enqueued with WorkManager — waiting for its constraints (a network), or for its backoff after a failed attempt. */
    data class Scheduled(
        override val lastSyncedAt: Long? = null,
    ) : SyncState

    /** Fetching, transforming or storing right now. [attempt] is 0 on the first try. */
    data class Running(
        val attempt: Int = 0,
        override val lastSyncedAt: Long? = null,
    ) : SyncState

    /** The last run wrote the store at [syncedAt]. */
    data class Success(
        val syncedAt: Long,
    ) : SyncState {
        override val lastSyncedAt: Long
            get() = syncedAt
    }

    /** The last run gave up, and nothing is scheduled to try again. */
    data class Failed(
        val error: AxiomError,
        val failedAt: Long,
        override val lastSyncedAt: Long? = null,
    ) : SyncState

    val isInFlight: Boolean
        get() = this is Scheduled || this is Running

    val isSettled: Boolean
        get() = this is Success || this is Failed
}
