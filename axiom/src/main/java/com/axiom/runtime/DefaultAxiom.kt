package com.axiom.runtime

import android.content.Context
import androidx.work.WorkInfo
import com.axiom.Axiom
import com.axiom.AxiomConfig
import com.axiom.client.ClientConfig
import com.axiom.client.ClientRegistry
import com.axiom.log.AxiomLogger
import com.axiom.log.debug
import com.axiom.log.warn
import com.axiom.serialization.GsonSerializer
import com.axiom.state.SyncResult
import com.axiom.state.SyncState
import com.axiom.store.AxiomDatabase
import com.axiom.store.JsonStore
import com.axiom.store.SyncStateEntity
import com.axiom.store.SyncStateStore
import com.axiom.store.toSettledState
import com.axiom.task.TaskSpec
import com.axiom.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import okhttp3.Call
import retrofit2.Retrofit
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

/** The runtime behind [Axiom]. Built by [Axiom.init] / [Axiom.create]; nothing else constructs it. */
internal class DefaultAxiom(
    private val context: Context,
    config: AxiomConfig,
) : Axiom {
    val logger: AxiomLogger = config.logger
    private val clock = config.clock
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database = AxiomDatabase.open(context, config.databaseName, config.inMemoryDatabase)
    private val clients = ClientRegistry(config.gson, logger)
    private val store = JsonStore(database.entries(), config.serializer ?: GsonSerializer(config.gson), clock, logger)
    private val states = SyncStateStore(database.syncStates())
    private val scheduler = WorkScheduler(context, logger)
    val runner = TaskRunner(context, clients, store, states, clock, logger, config.isDebug, config.defaultStaleAfter)

    private val registry = ConcurrentHashMap<String, TaskSpec<*>>()
    private val order = mutableListOf<String>()
    private val shared = ConcurrentHashMap<String, Flow<Any?>>()

    init {
        config.clients.values.forEach { clients.register(it) }
        config.tasks.values.forEach { register(it) }
        // A worker the OS killed mid-run left `running = 1` behind. This process just started, so it
        // cannot be true any more.
        scope.launch { states.resetRunning() }
        // One line per process start saying which freshness rule is in force — the first thing to look
        // for when a release build "is not fetching".
        logger.debug {
            val window = config.defaultStaleAfter
            when {
                config.isDebug -> "debug build: every sync fetches, staleAfter is ignored"
                window == null -> "release build: no default window, a task without staleAfter fetches every run"
                else -> "release build: a task without staleAfter is fresh for $window after each success"
            }
        }
    }

    override val tasks: List<TaskSpec<*>>
        get() = synchronized(order) { order.mapNotNull { registry[it] } }

    override fun task(key: String): TaskSpec<*> =
        taskOrNull(key) ?: throw IllegalArgumentException("No Axiom task registered under '$key'")

    override fun taskOrNull(key: String): TaskSpec<*>? = registry[key]

    override fun register(vararg specs: TaskSpec<*>) {
        specs.forEach { register(it) }
    }

    private fun register(spec: TaskSpec<*>) {
        synchronized(order) {
            if (registry.put(spec.key, spec) == null) order += spec.key
        }
        // A spec replaced under the same key may declare a different type; the shared parse must not
        // keep the old one.
        shared.remove(spec.key)
    }

    override fun client(
        name: String,
        configure: ClientConfig.() -> Unit,
    ) {
        clients.register(ClientConfig(name).apply(configure))
    }

    override fun <S : Any> api(
        client: String,
        service: Class<S>,
    ): S = clients.api(client, service)

    override fun retrofit(client: String): Retrofit = clients.retrofit(client)

    override fun callFactory(client: String): Call.Factory = clients.callFactory(client)

    override fun sync(key: String) {
        scheduler.enqueue(task(key))
    }

    override fun syncAll() {
        tasks.forEach { scheduler.enqueue(it) }
    }

    override fun cancel(key: String) = scheduler.cancel(key)

    override fun cancelAll() {
        tasks.forEach { scheduler.cancel(it.key) }
    }

    override suspend fun run(key: String): SyncResult = runner.run(task(key), attempt = 0)

    override fun state(key: String): Flow<SyncState> =
        combine(states.observe(key), scheduler.observe(key)) { row, info -> resolve(row, info) }
            .distinctUntilChanged()

    override fun states(): Flow<Map<String, SyncState>> =
        combine(states.observeAll(), scheduler.observeAll()) { rows, infos ->
            val byKey = rows.associateBy { it.key }
            tasks.associate { spec -> spec.key to resolve(byKey[spec.key], infos[spec.key]) }
        }.distinctUntilChanged()

    /**
     * The persisted row says how the last run ended; WorkManager says whether another is on its way.
     * The row wins on "running" because it is what an in-process [run] sets, which WorkManager never
     * hears about.
     */
    private fun resolve(
        row: SyncStateEntity?,
        info: WorkInfo?,
    ): SyncState {
        val settled = row.toSettledState()
        if (settled is SyncState.Running) return settled
        val last = row?.lastSuccessAt
        return when (info?.state) {
            WorkInfo.State.RUNNING -> SyncState.Running(info.runAttemptCount, last)
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> SyncState.Scheduled(last)
            else -> settled
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> data(spec: TaskSpec<T>): Flow<T?> = data(spec.key, spec.type)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> data(
        key: String,
        type: Type,
    ): Flow<T?> {
        checkType(key, type)
        return shared.getOrPut(key) {
            store
                .observe<Any>(key, type)
                // One parse per write, shared by every collector of this key; kept warm for five seconds
                // after the last collector leaves so a screen swap does not re-read a megabyte.
                .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000, replayExpirationMillis = 0), replay = 1)
        } as Flow<T?>
    }

    override suspend fun <T : Any> read(spec: TaskSpec<T>): T? = read(spec.key, spec.type)

    override suspend fun <T : Any> read(
        key: String,
        type: Type,
    ): T? {
        checkType(key, type)
        return store.read(key, type)
    }

    /**
     * A key's type is fixed by its task. Reading it as anything else would not fail — Gson would hand
     * back a `LinkedTreeMap` where a record was expected — so the mismatch is caught here, by name.
     */
    private fun checkType(
        key: String,
        type: Type,
    ) {
        val spec = registry[key] ?: return
        require(spec.type == type) {
            "Task '$key' stores ${spec.type}, not $type. Read it as the type it was declared with."
        }
    }

    override suspend fun clear(key: String) {
        store.remove(key)
        states.remove(key)
    }

    override suspend fun clearAll() {
        store.removeAll()
        states.removeAll()
    }

    override fun close() {
        scope.cancel()
        shared.clear()
        runCatching { database.close() }.onFailure { logger.warn(it) { "Closing the Axiom database failed" } }
    }
}
