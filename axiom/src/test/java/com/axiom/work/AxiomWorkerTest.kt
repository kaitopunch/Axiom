package com.axiom.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.axiom.Axiom
import com.axiom.log.AxiomLogger
import com.axiom.state.SyncState
import com.axiom.task.RetryPolicy
import com.axiom.task.task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/**
 * The retry ladder, rung by rung, and the two ways a worker can find nothing to do.
 *
 * Driven through [TestListenableWorkerBuilder] because `runAttemptCount` reaches a worker inside
 * `WorkerParameters`, which has no public constructor. Uses the **singleton** ([Axiom.init]) on purpose:
 * that is how the real worker finds the runtime, and a test that handed it an instance some other way
 * would not be testing the one thing that can go wrong.
 */
@RunWith(RobolectricTestRunner::class)
class AxiomWorkerTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    data class Row(
        val id: Int,
    )

    @Before
    fun setUp() {
        Axiom.reset()
    }

    @After
    fun tearDown() {
        Axiom.reset()
    }

    private fun worker(
        key: String,
        attempt: Int,
    ): AxiomWorker =
        TestListenableWorkerBuilder<AxiomWorker>(context)
            .setInputData(workDataOf(AxiomWorker.KEY_TASK to key))
            .setRunAttemptCount(attempt)
            .build()

    private fun init(vararg specs: com.axiom.task.TaskSpec<*>) {
        Axiom.init(context) {
            inMemoryDatabase = true
            logger = AxiomLogger.NONE
            tasks(*specs)
        }
    }

    @Test
    fun `a transient failure retries until the budget is spent, then fails`() {
        init(
            task<List<Row>>("rows") {
                fetch { throw IOException("down") }
                retry = RetryPolicy(maxRetries = 2, initialBackoff = 10.seconds)
            },
        )

        assertEquals(ListenableWorker.Result.retry(), runBlocking { worker("rows", attempt = 0).doWork() })
        assertEquals(ListenableWorker.Result.retry(), runBlocking { worker("rows", attempt = 1).doWork() })
        assertEquals(ListenableWorker.Result.failure(), runBlocking { worker("rows", attempt = 2).doWork() })
        // One past the top: a ladder that only knows how to stop at exactly the budget would retry forever.
        assertEquals(ListenableWorker.Result.failure(), runBlocking { worker("rows", attempt = 3).doWork() })
    }

    /** A payload that does not parse will not parse in ten seconds either. */
    @Test
    fun `a non-transient failure does not spend the budget`() {
        init(task<List<Row>>("rows") { fetch { throw com.google.gson.JsonSyntaxException("bad") } })

        assertEquals(ListenableWorker.Result.failure(), runBlocking { worker("rows", attempt = 0).doWork() })
    }

    @Test
    fun `success does not consult the ladder`() =
        runBlocking {
            init(task<List<Row>>("rows") { fetch { listOf(Row(1)) } })

            assertEquals(ListenableWorker.Result.success(), worker("rows", attempt = 3).doWork())
            assertEquals(listOf(Row(1)), Axiom.get().read(Axiom.get().task("rows") as com.axiom.task.TaskSpec<List<Row>>))
            assertEquals(true, Axiom.get().state("rows").first() is SyncState.Success)
        }

    /**
     * A store inside its freshness window is a successful run as far as WorkManager is concerned: no
     * fetch, no retry, no failure. `init` leaves the default window (thirty minutes) on, so the second
     * worker, a few milliseconds after the first, finds the store fresh.
     */
    @Test
    fun `a worker for a task inside its freshness window succeeds without fetching`() =
        runBlocking {
            var fetches = 0
            init(task<List<Row>>("rows") { fetch { fetches++; listOf(Row(fetches)) } })

            assertEquals(ListenableWorker.Result.success(), worker("rows", attempt = 0).doWork())
            assertEquals(ListenableWorker.Result.success(), worker("rows", attempt = 0).doWork())

            assertEquals(1, fetches)
        }

    /** A fetch that confirmed the store is current is a success to WorkManager: nothing to retry, nothing failed. */
    @Test
    fun `a worker whose fetch matches the store succeeds without writing`() =
        runBlocking {
            Axiom.init(context) {
                inMemoryDatabase = true
                logger = AxiomLogger.NONE
                defaultStaleAfter = null
                tasks(task<List<Row>>("rows") { fetch { listOf(Row(1)) } })
            }

            assertEquals(ListenableWorker.Result.success(), worker("rows", attempt = 0).doWork())
            assertEquals(ListenableWorker.Result.success(), worker("rows", attempt = 0).doWork())

            assertEquals(true, Axiom.get().state("rows").first() is SyncState.Success)
        }

    @Test
    fun `an unregistered key fails without retrying`() {
        init()

        assertEquals(ListenableWorker.Result.failure(), runBlocking { worker("ghost", attempt = 0).doWork() })
    }

    @Test
    fun `a worker in a process where Axiom was never initialised fails loudly rather than crashing`() {
        assertEquals(ListenableWorker.Result.failure(), runBlocking { worker("rows", attempt = 0).doWork() })
    }
}
