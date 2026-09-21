package com.duylt.demo.axiom.ui.products

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axiom.state.SyncState
import com.duylt.demo.axiom.data.record.ProductStore
import com.duylt.demo.axiom.data.repository.ProductRepository
import com.duylt.demo.axiom.ui.common.describe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProductsUiState(
    /** `null` until the store has been written once — "never synced", as distinct from an empty list. */
    val store: ProductStore? = null,
    val state: SyncState = SyncState.Idle(),
) {
    val products get() = store?.products.orEmpty()
    val cachedCount get() = products.count { it.thumbLocal != null }
}

class ProductsViewModel(
    private val repository: ProductRepository,
) : ViewModel() {
    val uiState: StateFlow<ProductsUiState> =
        combine(repository.store, repository.state) { store, state -> ProductsUiState(store, state) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductsUiState())

    /** One-shot messages for the snackbar: what `run()` returned. */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    var refreshing by mutableStateOf(false)
        private set

    fun sync() = repository.sync()

    fun clearAndSync() {
        viewModelScope.launch {
            repository.clearAndSync()
            _messages.send("Store cleared — syncing through WorkManager")
        }
    }

    /** Pull-to-refresh: `run()` awaits the result, and the result is the demo. */
    fun refresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            try {
                _messages.send(repository.refresh().describe())
            } finally {
                refreshing = false
            }
        }
    }
}
