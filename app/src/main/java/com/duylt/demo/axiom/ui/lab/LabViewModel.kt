package com.duylt.demo.axiom.ui.lab

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.axiom.Axiom
import com.axiom.state.SyncState
import com.axiom.task.TaskSpec
import com.duylt.demo.axiom.data.remote.DummyJsonApi
import com.duylt.demo.axiom.log.AxiomLogRecorder
import com.duylt.demo.axiom.log.LogLine
import com.duylt.demo.axiom.ui.common.describe
import com.google.gson.GsonBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One registered task and what its sync is doing right now. */
data class TaskCard(
    val spec: TaskSpec<*>,
    val state: SyncState,
)

/** One WorkManager request carrying Axiom's tag — the SDK's scheduling, seen from outside. */
data class WorkRow(
    val key: String,
    val state: WorkInfo.State,
    val runAttemptCount: Int,
    val periodic: Boolean,
    val nextScheduleAt: Long?,
)

/** What `Axiom.read()` found in the store for a key, pretty-printed for a dialog. */
data class Peek(
    val key: String,
    val json: String?,
    val bytes: Int,
)

class LabViewModel(
    private val axiom: Axiom,
    private val api: DummyJsonApi,
    workManager: WorkManager,
    private val recorder: AxiomLogRecorder,
) : ViewModel() {
    /** `states()` is every registered task at once, keyed — the Lab's whole table from one Flow. */
    val tasks: StateFlow<List<TaskCard>> =
        axiom.states()
            .map { states -> axiom.tasks.map { spec -> TaskCard(spec, states[spec.key] ?: SyncState.Idle()) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), axiom.tasks.map { TaskCard(it, SyncState.Idle()) })

    val work: StateFlow<List<WorkRow>> =
        workManager.getWorkInfosByTagFlow(AXIOM_WORK_TAG)
            .map { infos ->
                infos.map { info ->
                    WorkRow(
                        key = info.tags.firstOrNull { it.startsWith(TASK_TAG_PREFIX) }?.removePrefix(TASK_TAG_PREFIX) ?: "?",
                        state = info.state,
                        runAttemptCount = info.runAttemptCount,
                        periodic = info.periodicityInfo != null,
                        nextScheduleAt = info.nextScheduleTimeMillis.takeIf { it != Long.MAX_VALUE && it > 0 },
                    )
                }.sortedWith(compareBy({ it.periodic }, { it.key }))
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val logs: StateFlow<List<LogLine>> = recorder.lines

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    var peek by mutableStateOf<Peek?>(null)
        private set

    var apiCallResult by mutableStateOf<String?>(null)
        private set

    private val pretty = GsonBuilder().setPrettyPrinting().create()

    fun sync(key: String) = axiom.sync(key)

    fun syncAll() = axiom.syncAll()

    fun cancel(key: String) = axiom.cancel(key)

    fun cancelAll() = axiom.cancelAll()

    fun clear(key: String) {
        viewModelScope.launch {
            axiom.clear(key)
            _messages.send("'$key' cleared — payload and state gone, task still registered")
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            axiom.clearAll()
            _messages.send("Store cleared for every task")
        }
    }

    fun run(key: String) {
        viewModelScope.launch { _messages.send("run('$key') → ${axiom.run(key).describe()}") }
    }

    fun peek(spec: TaskSpec<*>) {
        viewModelScope.launch {
            val value = axiom.read<Any>(spec.key, spec.type)
            val json = value?.let { pretty.toJson(it) }
            peek = Peek(spec.key, json, json?.toByteArray()?.size ?: 0)
        }
    }

    fun dismissPeek() {
        peek = null
    }

    /** The service Koin handed out via `axiomApi<DummyJsonApi>()` — same Retrofit, same client, outside any task. */
    fun callApiThroughKoin() {
        viewModelScope.launch {
            apiCallResult = "calling…"
            apiCallResult =
                try {
                    val categories = api.getCategories()
                    "GET /products/categories → ${categories.size} categories (first: ${categories.firstOrNull()?.name})"
                } catch (e: Exception) {
                    "failed: ${e.javaClass.simpleName}: ${e.message}"
                }
        }
    }

    fun clearLogs() = recorder.clear()

    private companion object {
        // Mirrors WorkScheduler's tags (internal to the SDK; documented in axiom/README.md §4.2).
        const val AXIOM_WORK_TAG = "axiom"
        const val TASK_TAG_PREFIX = "axiom:task:"
    }
}
