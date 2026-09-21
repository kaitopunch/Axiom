package com.duylt.demo.axiom.data.repository

import com.axiom.Axiom
import com.axiom.state.SyncResult
import com.axiom.state.SyncState
import com.duylt.demo.axiom.data.DemoTasks
import com.duylt.demo.axiom.data.record.ProductStore
import kotlinx.coroutines.flow.Flow

/**
 * What a repository is left with once Axiom owns fetching and storing: reading (README §2 step 5).
 * `data(spec)` is `null` until the first write — the screen distinguishes "never synced" from "empty".
 */
class ProductRepository(
    private val axiom: Axiom,
) {
    private val key = DemoTasks.products.key

    val store: Flow<ProductStore?> = axiom.data(DemoTasks.products)

    val state: Flow<SyncState> = axiom.state(key)

    /** Through WorkManager: retry, constraints, and the freshness window all apply. Safe to spam. */
    fun sync() = axiom.sync(key)

    /** In this coroutine, one attempt, awaited — the pull-to-refresh case. Same freshness rule. */
    suspend fun refresh(): SyncResult = axiom.run(key)

    /** The only way past the freshness window, on purpose (README §4.1). */
    suspend fun clearAndSync() {
        axiom.clear(key)
        axiom.sync(key)
    }
}
