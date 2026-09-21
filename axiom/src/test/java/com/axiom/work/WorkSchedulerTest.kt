package com.axiom.work

import android.content.Context
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.axiom.Axiom
import com.axiom.log.AxiomLogger
import com.axiom.state.SyncState
import com.axiom.task.task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** What `sync` actually hands WorkManager, read back off the test WorkManager, and how `state` reflects it. */
@RunWith(RobolectricTestRunner::class)
class WorkSchedulerTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var axiom: Axiom

    data class Row(
        val id: Int,
    )

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        Axiom.reset()
        axiom =
            Axiom.init(context) {
                inMemoryDatabase = true
                logger = AxiomLogger.NONE
                task<List<Row>>("rows") { fetch { listOf(Row(1)) } }
                task<List<Row>>("more") { fetch { listOf(Row(2)) } }
            }
    }

    @After
    fun tearDown() {
        Axiom.reset()
    }

    private fun infos(key: String): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(WorkScheduler.uniqueName(key)).get()

    @Test
    fun `sync enqueues one unique job per key, tagged and network-constrained`() {
        axiom.sync("rows")
        axiom.sync("rows") // KEEP: the second call must not stack a duplicate

        val scheduled = infos("rows")
        assertEquals(1, scheduled.size)
        val info = scheduled.single()
        assertTrue(info.tags.contains(WorkScheduler.TAG))
        assertTrue(info.tags.contains(WorkScheduler.tagFor("rows")))
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertEquals("rows", WorkScheduler.keyOf(info.tags))
    }

    @Test
    fun `syncAll enqueues every registered task`() {
        axiom.syncAll()

        assertEquals(1, infos("rows").size)
        assertEquals(1, infos("more").size)
    }

    /** Offline, the job sits ENQUEUED behind its constraint — and the state must say so, not say Idle. */
    @Test
    fun `an enqueued job reads as Scheduled`() =
        runBlocking {
            axiom.sync("rows")

            assertEquals(SyncState.Scheduled(lastSyncedAt = null), axiom.state("rows").first())
            assertEquals(SyncState.Scheduled(lastSyncedAt = null), axiom.states().first()["rows"])
            assertEquals(SyncState.Idle(), axiom.states().first()["more"])
        }

    @Test
    fun `cancel removes the job`() =
        runBlocking {
            axiom.sync("rows")

            axiom.cancel("rows")

            assertEquals(WorkInfo.State.CANCELLED, infos("rows").single().state)
            assertEquals(SyncState.Idle(), axiom.state("rows").first())
        }
}
