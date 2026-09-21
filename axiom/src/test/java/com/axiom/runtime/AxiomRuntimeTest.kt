package com.axiom.runtime

import android.content.Context
import com.axiom.Axiom
import com.axiom.data
import com.axiom.jsonType
import com.axiom.log.AxiomLogger
import com.axiom.read
import com.axiom.state.AxiomError
import com.axiom.state.SyncResult
import com.axiom.state.SyncState
import com.axiom.task.task
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The pipeline end to end over a real in-memory Room: fetch → transform → store → state, driven by
 * [Axiom.run] so no WorkManager is involved. What the worker adds (scheduling, retry) is pinned in
 * `AxiomWorkerTest`; what a sync *does* is pinned here, once, for both.
 */
@RunWith(RobolectricTestRunner::class)
class AxiomRuntimeTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private var now = 1_000L
    private val opened = mutableListOf<Axiom>()

    data class Row(
        val id: Int,
        val name: String,
    )

    /**
     * The freshness window is off here — several pipeline tests run a task twice in a row and expect
     * the second run to fetch. The window has its own tests below, which switch it back on.
     */
    private fun axiom(configure: com.axiom.AxiomConfig.() -> Unit): Axiom =
        Axiom
            .create(context) {
                inMemoryDatabase = true
                logger = AxiomLogger.NONE
                clock = { now }
                defaultStaleAfter = null
                configure()
            }.also { opened += it }

    @After
    fun tearDown() {
        opened.forEach { it.close() }
    }

    @Test
    fun `a run stores what fetch returned, typed, and settles on Success`() =
        runBlocking {
            val rows = task<List<Row>>("rows") { fetch { listOf(Row(1, "a"), Row(2, "b")) } }
            val axiom = axiom { tasks(rows) }

            assertNull(axiom.read(rows))
            assertEquals(SyncState.Idle(), axiom.state("rows").first())

            val result = axiom.run("rows")

            assertEquals(SyncResult.Success(1_000L), result)
            assertEquals(listOf(Row(1, "a"), Row(2, "b")), axiom.read(rows))
            assertEquals(listOf(Row(1, "a"), Row(2, "b")), axiom.data(rows).first())
            assertEquals(listOf(Row(1, "a"), Row(2, "b")), axiom.read<List<Row>>("rows"))
            assertEquals(SyncState.Success(1_000L), axiom.state("rows").first())
        }

    /**
     * The reason `publish` exists: a collector of `data()` sees the intermediate value while the
     * transform is still working, and the final value replaces it.
     */
    @Test
    fun `publish makes an intermediate value visible before the transform returns`() =
        runBlocking {
            val seenDuringTransform = mutableListOf<List<Row>?>()
            lateinit var axiom: Axiom
            val rows =
                task<List<Row>>("rows") {
                    fetch { listOf(Row(1, "a")) }
                    transform { fetched ->
                        publish(fetched)
                        seenDuringTransform += axiom.read<List<Row>>(key)
                        fetched.map { it.copy(name = it.name + "!") }
                    }
                }
            axiom = axiom { tasks(rows) }

            axiom.run("rows")

            assertEquals(listOf(listOf(Row(1, "a"))), seenDuringTransform)
            assertEquals(listOf(Row(1, "a!")), axiom.read(rows))
        }

    @Test
    fun `a fetch that throws an IOException fails as NETWORK and settles on Failed`() =
        runBlocking {
            val rows = task<List<Row>>("rows") { fetch { throw IOException("no route to host") } }
            val axiom = axiom { tasks(rows) }

            val result = axiom.run("rows")

            val expected = AxiomError(AxiomError.Kind.NETWORK, "no route to host")
            assertEquals(SyncResult.Failure(expected), result)
            assertEquals(SyncState.Failed(expected, failedAt = 1_000L, lastSyncedAt = null), axiom.state("rows").first())
            assertNull(axiom.read(rows))
        }

    @Test
    fun `a transform that throws fails as TRANSFORM and keeps the previous payload`() =
        runBlocking {
            var shouldFail = false
            val rows =
                task<List<Row>>("rows") {
                    fetch { listOf(Row(1, "a")) }
                    transform { if (shouldFail) error("disk full") else it }
                }
            val axiom = axiom { tasks(rows) }
            axiom.run("rows")
            now = 2_000L
            shouldFail = true

            val result = axiom.run("rows")

            val error = (result as SyncResult.Failure).error
            assertEquals(AxiomError.Kind.TRANSFORM, error.kind)
            assertTrue("a transform failure is not transient", !error.isTransient)
            // The store still holds the last good value, and the state says both things.
            assertEquals(listOf(Row(1, "a")), axiom.read(rows))
            assertEquals(SyncState.Failed(error, failedAt = 2_000L, lastSyncedAt = 1_000L), axiom.state("rows").first())
        }

    /** The whole point of the window is that a release build gets it without asking; pin what "without asking" is. */
    @Test
    fun `out of the box a build is not debug and the default window is thirty minutes`() {
        var debugDefault: Boolean? = null
        var windowDefault: Duration? = null
        Axiom
            .create(context) {
                inMemoryDatabase = true
                logger = AxiomLogger.NONE
                debugDefault = isDebug
                windowDefault = defaultStaleAfter
            }.also { opened += it }

        assertEquals(false, debugDefault)
        assertEquals(30.minutes, windowDefault)
    }

    @Test
    fun `a task's own staleAfter skips a fetch while the store is fresh, and only time gets past it`() =
        runBlocking {
            var fetches = 0
            val rows =
                task<List<Row>>("rows") {
                    fetch { fetches++; listOf(Row(fetches, "x")) }
                    staleAfter = 1.hours
                }
            val axiom = axiom { tasks(rows) }

            axiom.run("rows")
            now += 10 * 60 * 1000L
            val skipped = axiom.run("rows")
            now += 2 * 60 * 60 * 1000L
            val stale = axiom.run("rows")

            assertEquals(SyncResult.Skipped(1_000L), skipped)
            assertTrue(stale is SyncResult.Success)
            assertEquals(2, fetches)
        }

    /** What a release build gets for free: no `staleAfter` on the task, and still no re-fetch inside the window. */
    @Test
    fun `a task without staleAfter takes the config-wide default window`() =
        runBlocking {
            var fetches = 0
            val rows = task<List<Row>>("rows") { fetch { fetches++; listOf(Row(fetches, "x")) } }
            val axiom =
                axiom {
                    defaultStaleAfter = 30.minutes
                    tasks(rows)
                }

            axiom.run("rows")
            now += 29 * 60 * 1000L
            val inside = axiom.run("rows")
            now += 2 * 60 * 1000L
            val outside = axiom.run("rows")

            assertEquals(SyncResult.Skipped(1_000L), inside)
            assertTrue(outside is SyncResult.Success)
            assertEquals(2, fetches)
        }

    /** The developer's switch: a debug build fetches on every run, whatever the task or the default says. */
    @Test
    fun `isDebug fetches on every run, over the task's staleAfter and the default alike`() =
        runBlocking {
            var fetches = 0
            val rows =
                task<List<Row>>("rows") {
                    fetch { fetches++; listOf(Row(fetches, "x")) }
                    staleAfter = 1.hours
                }
            val axiom =
                axiom {
                    isDebug = true
                    defaultStaleAfter = 30.minutes
                    tasks(rows)
                }

            repeat(3) { assertTrue(axiom.run("rows") is SyncResult.Success) }

            assertEquals(3, fetches)
        }

    /** A release build's way out of the window for one task. (The config-wide way out, `null`, is what [axiom] does.) */
    @Test
    fun `staleAfter ZERO opts a task out of the default window`() =
        runBlocking {
            var fetches = 0
            val rows =
                task<List<Row>>("rows") {
                    fetch { fetches++; listOf(Row(fetches, "x")) }
                    staleAfter = Duration.ZERO
                }
            val axiom =
                axiom {
                    defaultStaleAfter = 30.minutes
                    tasks(rows)
                }

            repeat(2) { assertTrue(axiom.run("rows") is SyncResult.Success) }

            assertEquals(2, fetches)
        }

    /**
     * The window starts at the last *success*. A failure after it does not start a new one, so the next
     * run tries again rather than waiting another thirty minutes on the strength of an attempt that
     * brought nothing — and a task that has never succeeded is never skipped.
     */
    @Test
    fun `the window is measured from the last success, not the last attempt`() =
        runBlocking {
            var fail = false
            var fetches = 0
            val rows =
                task<List<Row>>("rows") {
                    fetch { fetches++; if (fail) throw IOException("down") else listOf(Row(fetches, "x")) }
                }
            val axiom =
                axiom {
                    defaultStaleAfter = 30.minutes
                    tasks(rows)
                }

            axiom.run("rows")
            now += 31 * 60 * 1000L
            fail = true
            val failed = axiom.run("rows")
            now += 60 * 1000L
            fail = false
            val retried = axiom.run("rows")

            assertTrue(failed is SyncResult.Failure)
            assertTrue(retried is SyncResult.Success)
            assertEquals(3, fetches)
        }

    @Test
    fun `a negative staleAfter is refused when the task is built`() {
        assertThrows(IllegalArgumentException::class.java) {
            task<List<Row>>("rows") {
                fetch { emptyList() }
                staleAfter = (-1).minutes
            }
        }
    }

    @Test
    fun `reading a key as a type other than its task's throws rather than returning garbage`() {
        val rows = task<List<Row>>("rows") { fetch { emptyList() } }
        val axiom = axiom { tasks(rows) }

        assertThrows(IllegalArgumentException::class.java) {
            axiom.data<List<String>>("rows", jsonType<List<String>>())
        }
    }

    /** A payload longer than one chunk comes back whole — this is what the chunk table is for. */
    @Test
    fun `a payload larger than a chunk round-trips`() =
        runBlocking {
            val big = List(20_000) { Row(it, "name-$it-" + "x".repeat(20)) }
            val rows = task<List<Row>>("rows") { fetch { big } }
            val axiom = axiom { tasks(rows) }

            axiom.run("rows")

            assertEquals(big, axiom.read(rows))
        }

    @Test
    fun `data re-emits after every write`() =
        runBlocking {
            var value = listOf(Row(1, "a"))
            val rows = task<List<Row>>("rows") { fetch { value } }
            val axiom = axiom { tasks(rows) }
            axiom.run("rows")

            val emissions = mutableListOf<List<Row>?>()
            val firstSeen = CompletableDeferred<Unit>()
            val collector =
                launch {
                    axiom.data(rows).take(2).collect {
                        emissions += it
                        if (emissions.size == 1) firstSeen.complete(Unit)
                    }
                }
            // The second write must land after the first emission, or the collector sees one revision.
            firstSeen.await()
            value = listOf(Row(2, "b"))
            axiom.run("rows")
            collector.join()

            assertEquals(listOf(listOf(Row(1, "a")), listOf(Row(2, "b"))), emissions)
        }

    /**
     * The counterpart of the test above: a run that fetched what the store already holds writes nothing,
     * so the collector does not hear from it. Pinned by making the *next* write a different value and
     * checking the collector's second emission is that one — had the unchanged run bumped the revision,
     * `take(2)` would have ended on a repeat of the first value.
     */
    @Test
    fun `a run whose payload matches the store returns Unchanged and does not re-emit data`() =
        runBlocking {
            var value = listOf(Row(1, "a"))
            val rows = task<List<Row>>("rows") { fetch { value } }
            val axiom = axiom { tasks(rows) }
            axiom.run("rows")

            val emissions = mutableListOf<List<Row>?>()
            val firstSeen = CompletableDeferred<Unit>()
            val collector =
                launch {
                    axiom.data(rows).take(2).collect {
                        emissions += it
                        if (emissions.size == 1) firstSeen.complete(Unit)
                    }
                }
            firstSeen.await()
            now = 2_000L
            val unchanged = axiom.run("rows")
            value = listOf(Row(2, "b"))
            now = 3_000L
            val changed = axiom.run("rows")
            collector.join()

            assertEquals(SyncResult.Unchanged(2_000L), unchanged)
            assertEquals(SyncResult.Success(3_000L), changed)
            assertEquals(listOf(listOf(Row(1, "a")), listOf(Row(2, "b"))), emissions)
        }

    /** Unchanged is still a sync: the state row moves on, so the freshness window restarts from it. */
    @Test
    fun `an unchanged run counts as a success for the state and the freshness window`() =
        runBlocking {
            var fetches = 0
            val rows = task<List<Row>>("rows") { fetch { fetches++; listOf(Row(1, "a")) } }
            val axiom =
                axiom {
                    defaultStaleAfter = 30.minutes
                    tasks(rows)
                }

            axiom.run("rows")
            now += 31 * 60 * 1000L
            val unchanged = axiom.run("rows")
            now += 29 * 60 * 1000L
            val skipped = axiom.run("rows")

            assertEquals(SyncResult.Unchanged(1_000L + 31 * 60 * 1000L), unchanged)
            assertEquals(SyncState.Success(1_000L + 31 * 60 * 1000L), axiom.state("rows").first())
            assertEquals(SyncResult.Skipped(1_000L + 31 * 60 * 1000L), skipped)
            assertEquals(2, fetches)
        }

    /** Equality is on the serialised payload, so the order of a list is part of it — a reordering is a change. */
    @Test
    fun `a payload that differs only in order is written`() =
        runBlocking {
            var value = listOf(Row(1, "a"), Row(2, "b"))
            val rows = task<List<Row>>("rows") { fetch { value } }
            val axiom = axiom { tasks(rows) }
            axiom.run("rows")
            value = listOf(Row(2, "b"), Row(1, "a"))
            now = 2_000L

            assertEquals(SyncResult.Success(2_000L), axiom.run("rows"))
            assertEquals(listOf(Row(2, "b"), Row(1, "a")), axiom.read(rows))
        }

    /** The comparison must hold past one chunk: a big payload repeated is Unchanged, and one row edited deep inside it is not. */
    @Test
    fun `a multi-chunk payload is compared whole`() =
        runBlocking {
            val big = List(20_000) { Row(it, "name-$it-" + "x".repeat(20)) }
            var value = big
            val rows = task<List<Row>>("rows") { fetch { value } }
            val axiom = axiom { tasks(rows) }
            axiom.run("rows")
            now = 2_000L

            assertEquals(SyncResult.Unchanged(2_000L), axiom.run("rows"))

            value = big.toMutableList().also { it[19_999] = Row(19_999, "edited") }
            now = 3_000L
            assertEquals(SyncResult.Success(3_000L), axiom.run("rows"))
            assertEquals("edited", axiom.read(rows)!!.last().name)
        }

    /**
     * A `publish` that changed the store makes the run a Success even when the final value equals what
     * was published — the store is not how the run found it. And a publish of what is already stored
     * writes nothing, so a transform whose final value is also unchanged reports Unchanged.
     */
    @Test
    fun `Unchanged means neither publish nor the final write changed the store`() =
        runBlocking {
            var finalValue = listOf(Row(1, "a!"))
            val rows =
                task<List<Row>>("rows") {
                    fetch { listOf(Row(1, "a")) }
                    transform { fetched ->
                        publish(fetched)
                        finalValue
                    }
                }
            val axiom = axiom { tasks(rows) }

            // Publish writes (empty store), final writes: Success.
            assertEquals(SyncResult.Success(1_000L), axiom.run("rows"))
            // Publish writes (store holds "a!", publish is "a"), final writes "a!" back: Success.
            now = 2_000L
            assertEquals(SyncResult.Success(2_000L), axiom.run("rows"))
            // Final value now equals what publish wrote, but publish changed the store: still Success.
            finalValue = listOf(Row(1, "a"))
            now = 3_000L
            assertEquals(SyncResult.Success(3_000L), axiom.run("rows"))
            // Store holds "a"; publish "a" writes nothing, final "a" writes nothing: Unchanged.
            now = 4_000L
            assertEquals(SyncResult.Unchanged(4_000L), axiom.run("rows"))
            assertEquals(listOf(Row(1, "a")), axiom.read(rows))
        }

    @Test
    fun `clear removes the payload and the state`() =
        runBlocking {
            val rows = task<List<Row>>("rows") { fetch { listOf(Row(1, "a")) } }
            val axiom = axiom { tasks(rows) }
            axiom.run("rows")

            axiom.clear("rows")

            assertNull(axiom.read(rows))
            assertEquals(SyncState.Idle(), axiom.state("rows").first())
        }

    @Test
    fun `an unknown key is refused by name`() {
        val axiom = axiom { }

        val thrown = assertThrows(IllegalArgumentException::class.java) { axiom.task("nope") }

        assertTrue(thrown.message!!.contains("nope"))
    }
}
