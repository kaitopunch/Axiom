package com.duylt.demo.axiom.data.repository

import androidx.paging.PagingData
import com.axiom.Axiom
import com.axiom.paging.asPagingData
import com.axiom.state.SyncState
import com.duylt.demo.axiom.data.DemoTasks
import com.duylt.demo.axiom.data.record.UserRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserRepository(
    private val axiom: Axiom,
) {
    private val key = DemoTasks.users.key

    /** The stored list as pages (README §3.2). One Pager for the collection; a new list reloads around the anchor. */
    fun pagedUsers(): Flow<PagingData<UserRecord>> = axiom.data(DemoTasks.users).map { it.orEmpty() }.asPagingData()

    val count: Flow<Int?> = axiom.data(DemoTasks.users).map { it?.size }

    val state: Flow<SyncState> = axiom.state(key)

    fun sync() = axiom.sync(key)

    suspend fun clearAndSync() {
        axiom.clear(key)
        axiom.sync(key)
    }
}
