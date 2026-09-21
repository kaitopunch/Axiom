package com.axiom.runtime

import android.content.Context
import com.axiom.client.ClientRegistry
import com.axiom.log.AxiomLogger
import com.axiom.log.debug
import com.axiom.log.info
import com.axiom.log.warn
import com.axiom.state.AxiomError
import com.axiom.state.SyncResult
import com.axiom.store.JsonStore
import com.axiom.store.SyncStateStore
import com.axiom.task.TaskSpec
import com.axiom.task.TransformScope
import kotlinx.coroutines.CancellationException
import okhttp3.Call
import kotlin.time.Duration

/**
 * One attempt of one task: fetch → transform → store, with the state row kept honest throughout.
 *
 * This is the whole pipeline, and it is the same whether a worker or [com.axiom.Axiom.run] drives it.
 * The worker adds scheduling and retry on top; nothing about *what a sync does* lives there.
 *
 * The first thing an attempt does is decide whether to fetch at all — see [staleAfter]. That decision
 * has no per-call override: a release build's freshness window is meant to hold against a retry
 * button, a pull-to-refresh and a cold start alike, and the one switch that lifts it is [isDebug].
 *
 * The last thing it does is decide whether to write at all: the store compares what the transform
 * returned against what it holds ([JsonStore.write]) and a payload that did not change is not written,
 * so a sync that confirmed the data is still current costs the collectors of `data()` nothing. That
 * run reports [SyncResult.Unchanged]; it still counts as a success for the freshness window.
 */
internal class TaskRunner(
    private val context: Context,
    private val clients: ClientRegistry,
    private val store: JsonStore,
    private val states: SyncStateStore,
    private val clock: () -> Long,
    private val logger: AxiomLogger,
    private val isDebug: Boolean,
    private val defaultStaleAfter: Duration?,
) {
    suspend fun <T : Any> run(
        spec: TaskSpec<T>,
        attempt: Int,
    ): SyncResult {
        val key = spec.key
        val previous = states.get(key)
        val staleAfter = staleAfter(spec)
        val lastSuccessAt = previous?.lastSuccessAt
        if (staleAfter != null && lastSuccessAt != null && clock() - lastSuccessAt < staleAfter.inWholeMilliseconds) {
            logger.debug { "'$key' is fresh (synced ${clock() - lastSuccessAt} ms ago, staleAfter=$staleAfter); skipping" }
            return SyncResult.Skipped(lastSuccessAt)
        }

        states.markRunning(key, attempt)
        logger.debug { "'$key' attempt ${attempt + 1} starting" }
        val scope = Scope<T>(key, attempt, spec)

        return try {
            val fetched =
                try {
                    spec.fetch(scope)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    throw Classified(AxiomError.classify(throwable, AxiomError.Phase.FETCH))
                }

            val transform = spec.transform
            val stored =
                if (transform == null) {
                    fetched
                } else {
                    try {
                        transform(scope, fetched)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        throw Classified(AxiomError.classify(throwable, AxiomError.Phase.TRANSFORM))
                    }
                }

            val written = store.write(key, stored, spec.type)
            val now = clock()
            states.markSuccess(key, now)
            // The store changed this run if either write changed it: a `publish` that landed followed by a
            // final value equal to it is still a run that left the store different from how it found it.
            if (written || scope.published) {
                logger.info { "'$key' synced" }
                SyncResult.Success(now)
            } else {
                logger.info { "'$key' fetched what the store already holds; nothing written" }
                SyncResult.Unchanged(now)
            }
        } catch (cancellation: CancellationException) {
            // WorkManager cancels by cancelling the coroutine. A cancelled run is not a failure — the
            // row goes back to what it was, and the scope that asked for the cancellation hears it.
            states.markIdle(key)
            throw cancellation
        } catch (throwable: Throwable) {
            val error = (throwable as? Classified)?.error ?: AxiomError.classify(throwable, AxiomError.Phase.STORE)
            states.markFailure(key, error, clock())
            logger.warn(error.cause ?: throwable) { "'$key' attempt ${attempt + 1} failed: $error" }
            SyncResult.Failure(error)
        }
    }

    /**
     * How long the store stays fresh for [spec]: not at all in a debug build; else what the task said;
     * else the config-wide default. `null` means "fetch every time". Measured from the last *success*
     * — a failed attempt leaves the window where it was, so a task that has never succeeded is never
     * skipped.
     */
    private fun staleAfter(spec: TaskSpec<*>): Duration? = if (isDebug) null else spec.staleAfter ?: defaultStaleAfter

    /** Carries an already-classified error out of the phase that produced it. */
    private class Classified(
        val error: AxiomError,
    ) : Exception(error.message, error.cause)

    private inner class Scope<T : Any>(
        override val key: String,
        override val attempt: Int,
        private val spec: TaskSpec<T>,
    ) : TransformScope<T> {
        override val context: Context
            get() = this@TaskRunner.context

        override fun <S : Any> api(
            client: String,
            service: Class<S>,
        ): S = clients.api(client, service)

        override fun callFactory(client: String): Call.Factory = clients.callFactory(client)

        /** Whether a [publish] of this attempt changed the store. */
        var published: Boolean = false
            private set

        override suspend fun publish(value: T) {
            if (store.write(key, value, spec.type)) {
                published = true
                logger.debug { "'$key' published an intermediate value" }
            } else {
                logger.debug { "'$key' published an intermediate value the store already held; nothing written" }
            }
        }
    }
}
