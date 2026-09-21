package com.axiom.work

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.axiom.log.AxiomLogger
import com.axiom.log.warn
import com.axiom.task.TaskSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/**
 * Axiom's view of WorkManager: one unique one-time job per task, plus an optional periodic one.
 *
 * WorkManager is looked up lazily and its absence tolerated: an instance built with [com.axiom.Axiom.create]
 * in a JVM test has no WorkManager, and asking it for state should say "nothing scheduled", not throw.
 */
internal class WorkScheduler(
    private val context: Context,
    private val logger: AxiomLogger,
) {
    private fun workManager(): WorkManager? =
        try {
            WorkManager.getInstance(context)
        } catch (notInitialised: IllegalStateException) {
            logger.warn { "WorkManager is not initialised; Axiom cannot schedule or observe work in this process" }
            null
        }

    fun enqueue(spec: TaskSpec<*>) {
        val workManager = workManager() ?: return
        val request =
            OneTimeWorkRequestBuilder<AxiomWorker>()
                .setConstraints(spec.constraints)
                .setBackoffCriteria(
                    spec.retry.backoffPolicy,
                    spec.retry.initialBackoff.inWholeMilliseconds,
                    TimeUnit.MILLISECONDS,
                ).setInputData(inputData(spec.key))
                .addTag(TAG)
                .addTag(tagFor(spec.key))
                .build()
        // KEEP: a sync already in flight finishes instead of restarting, a finished one re-runs. That is
        // what makes this safe to call from a retry button as often as the user taps it. Whether the
        // re-run touches the network is the runner's call (staleAfter), not the scheduler's.
        workManager.enqueueUniqueWork(uniqueName(spec.key), ExistingWorkPolicy.KEEP, request)

        val periodic = spec.periodic
        if (periodic != null) {
            val periodicRequest =
                PeriodicWorkRequestBuilder<AxiomWorker>(periodic.inWholeMilliseconds, TimeUnit.MILLISECONDS)
                    .setConstraints(spec.constraints)
                    .setBackoffCriteria(
                        spec.retry.backoffPolicy,
                        spec.retry.initialBackoff.inWholeMilliseconds,
                        TimeUnit.MILLISECONDS,
                    ).setInputData(inputData(spec.key))
                    .addTag(TAG)
                    .addTag(tagFor(spec.key))
                    .build()
            workManager.enqueueUniquePeriodicWork(periodicName(spec.key), ExistingPeriodicWorkPolicy.UPDATE, periodicRequest)
        }
    }

    fun cancel(key: String) {
        val workManager = workManager() ?: return
        workManager.cancelUniqueWork(uniqueName(key))
        workManager.cancelUniqueWork(periodicName(key))
    }

    /** The one-time job's info, or null when none was ever enqueued (or WorkManager is absent). */
    fun observe(key: String): Flow<WorkInfo?> {
        val workManager = workManager() ?: return flowOf(null)
        return workManager
            .getWorkInfosForUniqueWorkFlow(uniqueName(key))
            .map { infos -> infos.firstOrNull() }
    }

    /** Every Axiom one-time job's info, keyed by task. */
    fun observeAll(): Flow<Map<String, WorkInfo>> {
        val workManager = workManager() ?: return flowOf(emptyMap())
        return workManager
            .getWorkInfosByTagFlow(TAG)
            .map { infos ->
                infos
                    .filter { it.periodicityInfo == null }
                    .mapNotNull { info -> keyOf(info.tags)?.let { it to info } }
                    .toMap()
            }
    }

    private fun inputData(key: String): Data = workDataOf(AxiomWorker.KEY_TASK to key)

    companion object {
        const val TAG = "axiom"

        fun uniqueName(key: String) = "axiom:$key"

        fun periodicName(key: String) = "axiom:$key:periodic"

        fun tagFor(key: String) = "axiom:task:$key"

        fun keyOf(tags: Set<String>): String? =
            tags.firstOrNull { it.startsWith("axiom:task:") }?.removePrefix("axiom:task:")
    }
}
