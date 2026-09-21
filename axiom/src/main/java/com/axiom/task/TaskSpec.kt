package com.axiom.task

import androidx.work.Constraints
import androidx.work.NetworkType
import com.axiom.jsonType
import java.lang.reflect.Type
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * One sync, declared: what to fetch, what to do to it before it is stored, and how to schedule it.
 *
 * Built with [task]; read by the runtime. The store keeps exactly one value of [type] per [key], and
 * [com.axiom.Axiom.data] hands it back as that type.
 */
class TaskSpec<T : Any> internal constructor(
    val key: String,
    /** The full generic type of [T] — what the store serialises and what `data()` parses. */
    val type: Type,
    val retry: RetryPolicy,
    val constraints: Constraints,
    /**
     * Skip the fetch when the store is younger than this. `null` defers to `AxiomConfig.defaultStaleAfter`
     * (thirty minutes unless the host changed it); `Duration.ZERO` fetches every time. Ignored when
     * `AxiomConfig.isDebug`.
     */
    val staleAfter: Duration?,
    /** Also run on this period, in the background, for as long as the app is installed. */
    val periodic: Duration?,
    val fetch: suspend TaskScope.() -> T,
    val transform: (suspend TransformScope<T>.(T) -> T)?,
) {
    override fun toString(): String = "TaskSpec($key: $type)"
}

/** Builds a [TaskSpec]. Use through [task]. */
class TaskBuilder<T : Any>
    @PublishedApi
    internal constructor(
        val key: String,
        val type: Type,
    ) {
        var retry: RetryPolicy = RetryPolicy.DEFAULT

        /** Requires a network by default: a fetch that cannot reach the host should wait, not fail. */
        var constraints: Constraints = Constraints(requiredNetworkType = NetworkType.CONNECTED)

        /** See [TaskSpec.staleAfter]. Leave `null` to take the config-wide default; `Duration.ZERO` to opt out. */
        var staleAfter: Duration? = null

        /** WorkManager's floor is 15 minutes; shorter values throw at build time. */
        var periodic: Duration? = null

        private var fetch: (suspend TaskScope.() -> T)? = null
        private var transform: (suspend TransformScope<T>.(T) -> T)? = null

        /** The network call, and any pure mapping of its result into the shape the store should hold. */
        fun fetch(block: suspend TaskScope.() -> T) {
            fetch = block
        }

        /** Work that runs after [fetch] and before the store is written. See [TransformScope.publish]. */
        fun transform(block: suspend TransformScope<T>.(T) -> T) {
            transform = block
        }

        @PublishedApi
        internal fun build(): TaskSpec<T> {
            require(key.isNotBlank()) { "task key must not be blank" }
            require(!key.contains(':')) { "task key '$key' must not contain ':' — it is used in WorkManager names" }
            val fetch = requireNotNull(fetch) { "task '$key' declares no fetch { … }" }
            staleAfter?.let { require(!it.isNegative()) { "task '$key': staleAfter must not be negative, was $it (Duration.ZERO fetches every time)" } }
            periodic?.let { require(it >= 15.minutes) { "task '$key': periodic must be >= 15 minutes (WorkManager's floor), was $it" } }
            return TaskSpec(key, type, retry, constraints, staleAfter, periodic, fetch, transform)
        }
    }

/**
 * Declares a task. [T] is the type the store holds; generics survive, so `task<List<Skin>>("skins")` is
 * exactly what it says.
 *
 * ```
 * val skins = task<SkinStore>("skins") {
 *     fetch { api<CatalogueApi>("catalogue").skins().requireItems("skins").toStore() }
 *     transform { store -> publish(store); store.withCachedImages() }
 *     retry = RetryPolicy(maxRetries = 3)
 *     staleAfter = 1.hours              // omit for AxiomConfig.defaultStaleAfter; Duration.ZERO to always fetch
 * }
 * ```
 */
inline fun <reified T : Any> task(
    key: String,
    noinline configure: TaskBuilder<T>.() -> Unit,
): TaskSpec<T> = TaskBuilder<T>(key, jsonType<T>()).apply(configure).build()
