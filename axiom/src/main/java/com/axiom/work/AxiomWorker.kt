package com.axiom.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.axiom.Axiom
import com.axiom.log.error
import com.axiom.runtime.DefaultAxiom
import com.axiom.state.SyncResult

/**
 * The one worker class Axiom schedules. Which task it runs is in its input data, so a host never
 * registers anything with its `WorkerFactory`: this constructor is the one WorkManager's default
 * factory (and Koin's, and Hilt's, when they decline a class they do not know) can call.
 *
 * It resolves the runtime through [Axiom.get] rather than a constructor argument, which is the single
 * reason [Axiom.init] must run in `Application.onCreate` — WorkManager may start the process for this
 * worker alone, and `onCreate` is the only hook that runs first.
 */
class AxiomWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val key = inputData.getString(KEY_TASK) ?: return Result.failure()
        val axiom = Axiom.getOrNull() as? DefaultAxiom
        if (axiom == null) {
            // No logger to reach either; android.util.Log is the one thing guaranteed to exist here.
            android.util.Log.e("Axiom", "AxiomWorker ran for '$key' but Axiom.init was never called in this process")
            return Result.failure()
        }
        val spec = axiom.taskOrNull(key)
        if (spec == null) {
            axiom.logger.error { "AxiomWorker ran for '$key' but no such task is registered" }
            return Result.failure()
        }

        return when (val result = axiom.runner.run(spec, attempt = runAttemptCount)) {
            is SyncResult.Success, is SyncResult.Unchanged, is SyncResult.Skipped -> Result.success()
            is SyncResult.Failure ->
                if (spec.retry.shouldRetry(result.error, runAttemptCount)) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_TASK = "axiom.task"
    }
}
