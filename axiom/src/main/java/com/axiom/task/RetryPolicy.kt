package com.axiom.task

import androidx.work.BackoffPolicy
import com.axiom.state.AxiomError
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How a task's worker retries. Both halves live here on purpose — the number of retries and the wait
 * between them are one policy, and a policy split over two places is how they drift.
 *
 * @param maxRetries retries granted **after** the first attempt: a failing task runs `maxRetries + 1`
 * times before it reports [com.axiom.state.SyncState.Failed].
 * @param initialBackoff the first wait; doubles from there under [BackoffPolicy.EXPONENTIAL]. WorkManager
 * clamps it to at least 10 seconds.
 * @param retryOn which failures are worth another attempt. Defaults to [AxiomError.isTransient]: a
 * parse error will parse the same way in ten seconds.
 */
data class RetryPolicy(
    val maxRetries: Int = 3,
    val initialBackoff: Duration = 10.seconds,
    val backoffPolicy: BackoffPolicy = BackoffPolicy.EXPONENTIAL,
    val retryOn: (AxiomError) -> Boolean = { it.isTransient },
) {
    init {
        require(maxRetries >= 0) { "maxRetries must be >= 0" }
    }

    fun shouldRetry(
        error: AxiomError,
        attempt: Int,
    ): Boolean = attempt < maxRetries && retryOn(error)

    companion object {
        val DEFAULT = RetryPolicy()

        /** One attempt, whatever happened. */
        val NONE = RetryPolicy(maxRetries = 0)
    }
}
