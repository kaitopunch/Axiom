package com.axiom.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState

/**
 * Pages a list that is already in memory. Keys are offsets into the list, the same model Room's
 * `LimitOffsetPagingSource` uses, so a screen written against Room paging behaves the same over this.
 *
 * A source is a snapshot: it never sees a newer list. [asPagingData] invalidates it when one arrives,
 * and Paging asks the factory for a fresh source over the new list.
 */
class ListPagingSource<T : Any>(
    private val items: List<T>,
) : PagingSource<Int, T>() {
    override val jumpingSupported: Boolean
        get() = true

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        // Land the refresh on the page the user was looking at, not at the top.
        return page.prevKey?.plus(state.config.pageSize) ?: page.nextKey?.minus(state.config.pageSize)?.coerceAtLeast(0)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val start = (params.key ?: 0).coerceIn(0, items.size)
        val end = (start + params.loadSize).coerceAtMost(items.size)
        val page = items.subList(start, end)
        return LoadResult.Page(
            data = page,
            prevKey = if (start == 0) null else (start - params.loadSize).coerceAtLeast(0),
            nextKey = if (end >= items.size) null else end,
            itemsBefore = start,
            itemsAfter = items.size - end,
        )
    }
}
