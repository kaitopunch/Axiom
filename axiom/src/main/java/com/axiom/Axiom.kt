package com.axiom

import android.content.Context
import com.axiom.client.ClientConfig
import com.axiom.runtime.DefaultAxiom
import com.axiom.state.SyncResult
import com.axiom.state.SyncState
import com.axiom.task.TaskSpec
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import okhttp3.Call
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * The SDK's one entry point: named HTTP clients, declarative sync tasks, a Room-backed store that is
 * the single source of truth for what those tasks fetched, and WorkManager to run them.
 *
 * The shape, end to end:
 *
 * ```
 * // Application.onCreate — before WorkManager can run anything
 * Axiom.init(this) {
 *     isDebug = BuildConfig.DEBUG      // debug: fetch on every run; release: at most every 30 minutes
 *     client("catalogue") {
 *         baseUrl = "https://api.example.com/"
 *         header("X-API-KEY", apiKey)
 *     }
 *     tasks(skins, colors)              // TaskSpec<T> built with task<T>("key") { fetch { … } }
 * }
 * Axiom.get().syncAll()
 *
 * // anywhere below: the store is the source of truth, the state says what the network is doing
 * axiom.data(skins)  : Flow<SkinStore?>
 * axiom.state("skins"): Flow<SyncState>
 * ```
 *
 * Nothing here knows about UI. A task fetches, optionally transforms, and stores; the layer above reads
 * [data] and [state] and decides what to draw. The store persists across process death and is what a
 * screen reads *first* — the network only ever updates it.
 *
 * The singleton is deliberate: WorkManager instantiates [com.axiom.work.AxiomWorker] on its own, in a
 * process that may have been started for that purpose, and the worker needs to find the task registry
 * and the store without a DI container to ask. [init] in `Application.onCreate` is the contract. For
 * tests, [create] builds an instance that is not the singleton.
 */
interface Axiom {
    /** Every registered task, in registration order. */
    val tasks: List<TaskSpec<*>>

    /** The task registered under [key]. Throws [IllegalArgumentException] for an unknown key. */
    fun task(key: String): TaskSpec<*>

    /** The task registered under [key], or null. */
    fun taskOrNull(key: String): TaskSpec<*>?

    /**
     * Registers tasks after [Axiom.init] has run. Registering a key twice replaces the earlier spec —
     * intentionally, so a feature module loaded later can redefine a task without a restart.
     */
    fun register(vararg specs: TaskSpec<*>)

    /** Adds or replaces a named HTTP client after init. See [AxiomConfig.client]. */
    fun client(
        name: String,
        configure: ClientConfig.() -> Unit,
    )

    /** A Retrofit service bound to the named client. Requires that client to have a `baseUrl`. */
    fun <S : Any> api(client: String, service: Class<S>): S

    /** The Retrofit instance behind the named client. Requires a `baseUrl`. */
    fun retrofit(client: String): Retrofit

    /**
     * The OkHttp call factory behind the named client — for downloads that are not a Retrofit call
     * (images, files). Works for a client with no `baseUrl`.
     */
    fun callFactory(client: String): Call.Factory

    /**
     * Schedules [key] through WorkManager. A pending or running sync of the same key is left alone
     * (`ExistingWorkPolicy.KEEP`); a finished one re-runs. Safe to call from a retry button as often as
     * the user likes.
     *
     * Whether the run then fetches is decided when it starts, not here: in a debug build
     * (`AxiomConfig.isDebug`) it always does; otherwise only once the store is older than the task's
     * `staleAfter` (or `AxiomConfig.defaultStaleAfter`, thirty minutes, when the task set none). There
     * is no flag to get past that window — a caller that truly needs a fresh copy inside it calls
     * [clear] first.
     */
    fun sync(key: String)

    /** [sync] for every registered task, in registration order. */
    fun syncAll()

    /** Cancels the pending or running sync of [key], if any. The store is untouched. */
    fun cancel(key: String)

    /** [cancel] for every registered task. */
    fun cancelAll()

    /**
     * Runs [key] **in this coroutine**, without WorkManager: one attempt, no retry, no constraints.
     * The store and [state] update exactly as they would from a worker, and the same freshness rule
     * applies — see [sync]. For a caller that wants to await the result (pull-to-refresh, a test), or
     * has its own scheduling.
     */
    suspend fun run(key: String): SyncResult

    /** What the sync of [key] is doing. Re-emits on every change and never completes. */
    fun state(key: String): Flow<SyncState>

    /** [state] for every registered task at once, keyed by task key. */
    fun states(): Flow<Map<String, SyncState>>

    /**
     * What the store holds for [spec], parsed to its declared type. `null` until the first successful
     * sync (or the first `publish` from a transform). Re-emits after every write; parsing happens once
     * per write, shared between every collector of the same key.
     */
    fun <T : Any> data(spec: TaskSpec<T>): Flow<T?>

    /** [data] by key. [type] must equal the registered task's type — a mismatch throws, not returns garbage. */
    fun <T : Any> data(key: String, type: Type): Flow<T?>

    /** A one-shot read of the store for [spec]. */
    suspend fun <T : Any> read(spec: TaskSpec<T>): T?

    /** A one-shot read of the store by key. Same type rule as [data]. */
    suspend fun <T : Any> read(key: String, type: Type): T?

    /** Removes [key]'s stored payload and sync state. The task stays registered. */
    suspend fun clear(key: String)

    /** [clear] for everything in the store. */
    suspend fun clearAll()

    /** Releases the database and cancels internal work. The singleton is not cleared — see [reset]. */
    fun close()

    companion object {
        @Volatile
        private var instance: Axiom? = null

        /**
         * Builds the singleton. Call once from `Application.onCreate`, before anything that may run a
         * worker. A second call returns the existing instance and ignores [configure] — logged, not
         * thrown, because a second `Application.onCreate` in a multi-process app is not a bug.
         */
        fun init(
            context: Context,
            configure: AxiomConfig.() -> Unit,
        ): Axiom {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                return create(context, configure).also { instance = it }
            }
        }

        /** The singleton. Throws [IllegalStateException] before [init]. */
        fun get(): Axiom =
            instance ?: throw IllegalStateException(
                "Axiom.init(context) { … } has not been called. Call it from Application.onCreate.",
            )

        /** The singleton, or null before [init]. */
        fun getOrNull(): Axiom? = instance

        val isInitialized: Boolean
            get() = instance != null

        /**
         * A standalone instance that is **not** the singleton: workers cannot find it, so [Axiom.sync]
         * on it enqueues work that will fail to resolve. What it is for is tests and tools that drive
         * tasks through [Axiom.run] and read the store directly.
         */
        fun create(
            context: Context,
            configure: AxiomConfig.() -> Unit,
        ): Axiom {
            val config = AxiomConfig().apply(configure)
            return DefaultAxiom(context.applicationContext, config)
        }

        /**
         * Closes and forgets the singleton so the next [init] builds a fresh one. For tests that need
         * the singleton (worker tests); production code has no reason to call this.
         */
        fun reset() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}

/** [Axiom.api] with the service type inferred. */
inline fun <reified S : Any> Axiom.api(client: String): S = api(client, S::class.java)

/** [Axiom.data] by key with the type inferred. Must match the registered task's type. */
inline fun <reified T : Any> Axiom.data(key: String): Flow<T?> = data(key, jsonType<T>())

/** [Axiom.read] by key with the type inferred. Must match the registered task's type. */
suspend inline fun <reified T : Any> Axiom.read(key: String): T? = read(key, jsonType<T>())

/**
 * The full generic [Type] of [T], generics included — `List<Skin>` stays `List<Skin>` rather than
 * decaying to `List`. This is what makes a stored `List<T>` round-trip through Gson; a `Class<T>` would
 * come back as `List<LinkedTreeMap>`.
 */
inline fun <reified T> jsonType(): Type = object : TypeToken<T>() {}.type
