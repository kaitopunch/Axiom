package com.axiom

import com.axiom.client.ClientConfig
import com.axiom.log.AndroidAxiomLogger
import com.axiom.log.AxiomLogger
import com.axiom.serialization.AxiomSerializer
import com.axiom.serialization.GsonSerializer
import com.axiom.task.TaskBuilder
import com.axiom.task.TaskSpec
import com.google.gson.Gson
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Everything [Axiom.init] can be told. Built by the DSL block; read once by the runtime.
 *
 * ```
 * Axiom.init(context) {
 *     isDebug = BuildConfig.DEBUG            // debug: every sync fetches; release: see defaultStaleAfter
 *     client("catalogue") { baseUrl = "…"; header("X-API-KEY", key) }
 *     client("assets")                       // no baseUrl: callFactory only, for raw downloads
 *     tasks(CatalogueTasks.all)
 *     task<List<Color>>("colors") { fetch { api<Api>("catalogue").colors().requireData() } }
 * }
 * ```
 */
class AxiomConfig internal constructor() {
    /** File name of the SDK's own Room database. Change it only if two Axiom instances share a process. */
    var databaseName: String = "axiom.db"

    /** An in-memory store, gone when the process is. For tests. */
    var inMemoryDatabase: Boolean = false

    /**
     * The Gson behind the default Retrofit converter *and* the default [serializer]. One instance so the
     * shape that comes off the wire is the shape that goes into the store — a converter setting cannot
     * make the two disagree.
     */
    var gson: Gson = Gson()

    /**
     * How stored payloads are written and read. Defaults to [GsonSerializer] over [gson]. A consumer on
     * kotlinx.serialization or Moshi supplies its own and sets each client's `converterFactory` to match.
     */
    var serializer: AxiomSerializer? = null

    /** Where Axiom logs. Defaults to logcat under the tag `Axiom`; [AxiomLogger.NONE] silences it. */
    var logger: AxiomLogger = AndroidAxiomLogger()

    /** The wall clock behind `syncedAt` stamps and `staleAfter` checks. Injectable for tests. */
    var clock: () -> Long = System::currentTimeMillis

    /**
     * `true` for a debug build: every sync fetches, whatever the task's `staleAfter` or
     * [defaultStaleAfter] say, so a developer who relaunches the app sees the server's current data
     * each time. Pass `BuildConfig.DEBUG`. Nothing else keys off this — logging is [logger] and each
     * client's `logging`, on purpose, so a release build can still be made to log.
     */
    var isDebug: Boolean = false

    /**
     * How long the store stays fresh for a task that sets no `staleAfter` of its own: while the last
     * successful sync of a key is younger than this, a sync of it is skipped without touching the
     * network. Thirty minutes by default, so a release build does not re-fetch the whole catalogue on
     * every cold start.
     *
     * There is no per-call override — the window is the one thing a release build cannot be talked
     * out of. A task that must fetch on every run sets `staleAfter = Duration.ZERO`; `null` here turns
     * the default off for every task that did not set one. Ignored entirely when [isDebug].
     */
    var defaultStaleAfter: Duration? = 30.minutes

    internal val clients = LinkedHashMap<String, ClientConfig>()
    internal val tasks = LinkedHashMap<String, TaskSpec<*>>()

    /**
     * Declares a named HTTP client. Every task names the client it fetches through, so one app can talk
     * to as many hosts as it likes — each with its own base URL, headers, timeouts and logging — while
     * they all share one connection pool underneath.
     */
    fun client(
        name: String,
        configure: ClientConfig.() -> Unit = {},
    ) {
        require(name.isNotBlank()) { "client name must not be blank" }
        clients[name] = ClientConfig(name).apply(configure)
    }

    /** Registers tasks built elsewhere with [com.axiom.task.task]. A repeated key replaces the earlier spec. */
    fun tasks(vararg specs: TaskSpec<*>) {
        specs.forEach { register(it) }
    }

    /** [tasks], for a list. */
    fun tasks(specs: Iterable<TaskSpec<*>>) {
        specs.forEach { register(it) }
    }

    /** Builds and registers a task in one step. */
    inline fun <reified T : Any> task(
        key: String,
        noinline configure: TaskBuilder<T>.() -> Unit,
    ): TaskSpec<T> = com.axiom.task.task(key, configure).also { register(it) }

    @PublishedApi
    internal fun register(spec: TaskSpec<*>) {
        tasks[spec.key] = spec
    }
}
