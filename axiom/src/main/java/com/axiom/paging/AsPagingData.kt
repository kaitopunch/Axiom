package com.axiom.paging

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/** How many rows one page pulls, when the caller does not say. */
private const val DEFAULT_PAGE_SIZE = 25

/**
 * A [PagingConfig] tuned for a list that is already in memory: no placeholders (a count query bought
 * nothing), one page in, one page per scroll.
 */
val DefaultListPagingConfig: PagingConfig =
    PagingConfig(
        pageSize = DEFAULT_PAGE_SIZE,
        initialLoadSize = DEFAULT_PAGE_SIZE,
        prefetchDistance = 5,
        enablePlaceholders = false,
    )

/**
 * Every emission of this flow, as pages.
 *
 * One [Pager] for the lifetime of the collection — not one per list — and that is the whole point.
 * A new `Pager` per emission would hand the adapter a `PagingData` that starts at the top, so a list
 * that refreshed while the user was on row 500 would snap them back to row 0. Instead each new list
 * invalidates the current [ListPagingSource]; Paging then asks the factory for a fresh source and
 * reloads around the anchor through [ListPagingSource.getRefreshKey], exactly as a Room invalidation
 * does.
 *
 * The first `PagingData` waits for the first list. Until then a collector sees nothing, which is the
 * same as an empty Room table before its first row: the caller's loading state is the caller's to show.
 */
fun <T : Any> Flow<List<T>>.asPagingData(config: PagingConfig = DefaultListPagingConfig): Flow<PagingData<T>> =
    flow {
        coroutineScope {
            val latest = MutableStateFlow<List<T>?>(null)
            val lock = Any()
            var current: ListPagingSource<T>? = null

            launch {
                collect { list ->
                    val previous =
                        synchronized(lock) {
                            latest.value = list
                            current
                        }
                    previous?.invalidate()
                }
            }

            latest.filterNotNull().first()

            emitAll(
                Pager(config) {
                    synchronized(lock) {
                        ListPagingSource(checkNotNull(latest.value)).also { current = it }
                    }
                }.flow,
            )
        }
    }
