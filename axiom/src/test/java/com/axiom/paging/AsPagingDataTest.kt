package com.axiom.paging

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.testing.asSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AsPagingDataTest {
    private val config = PagingConfig(pageSize = 3, initialLoadSize = 3, prefetchDistance = 1, enablePlaceholders = false)

    @Test
    fun `pages a list in order, one page at a time`() =
        runTest {
            val source = ListPagingSource((1..7).toList())

            val first = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 3, placeholdersEnabled = false)) as PagingSource.LoadResult.Page
            assertEquals(listOf(1, 2, 3), first.data)
            assertNull(first.prevKey)
            assertEquals(3, first.nextKey)

            val last = source.load(PagingSource.LoadParams.Append(key = 6, loadSize = 3, placeholdersEnabled = false)) as PagingSource.LoadResult.Page
            assertEquals(listOf(7), last.data)
            assertNull(last.nextKey)
            assertEquals(3, last.prevKey)
        }

    /** A key past the end — the list shrank under a source that had not been invalidated yet — is an empty page, not a crash. */
    @Test
    fun `a key beyond the list yields an empty final page`() =
        runTest {
            val source = ListPagingSource(listOf(1, 2))

            val page = source.load(PagingSource.LoadParams.Append(key = 10, loadSize = 3, placeholdersEnabled = false)) as PagingSource.LoadResult.Page

            assertEquals(emptyList<Int>(), page.data)
            assertNull(page.nextKey)
        }

    @Test
    fun `asPagingData loads the whole list through the pages`() =
        runTest {
            val items = (1..10).toList()

            val snapshot = flowOf(items).asPagingData(config).asSnapshot { scrollTo(index = items.lastIndex) }

            assertEquals(items, snapshot)
        }

    /** A new list invalidates the current source; the next snapshot is the new list, through the same Pager. */
    @Test
    fun `a new emission is reflected without recreating the pager`() =
        runTest {
            val lists = MutableStateFlow(listOf(1, 2, 3))
            val paged = lists.asPagingData(config)

            assertEquals(listOf(1, 2, 3), paged.asSnapshot { scrollTo(index = 2) })
            lists.value = listOf(4, 5)
            assertEquals(listOf(4, 5), paged.asSnapshot { scrollTo(index = 1) })
        }
}
