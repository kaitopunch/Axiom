package com.duylt.demo.axiom.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.axiom.state.SyncState
import com.duylt.demo.axiom.data.record.UserRecord
import com.duylt.demo.axiom.data.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UsersHeader(
    val count: Int? = null,
    val state: SyncState = SyncState.Idle(),
)

class UsersViewModel(
    private val repository: UserRepository,
) : ViewModel() {
    /** README §3.2: `cachedIn(viewModelScope)` so a rotation does not restart the Pager. */
    val users: Flow<PagingData<UserRecord>> = repository.pagedUsers().cachedIn(viewModelScope)

    val header: StateFlow<UsersHeader> =
        combine(repository.count, repository.state) { count, state -> UsersHeader(count, state) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsersHeader())

    fun sync() = repository.sync()

    fun clearAndSync() {
        viewModelScope.launch { repository.clearAndSync() }
    }
}
