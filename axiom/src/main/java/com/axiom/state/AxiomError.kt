package com.axiom.state

import com.axiom.envelope.ApiEnvelopeException
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import retrofit2.HttpException
import java.io.IOException

/**
 * Why a sync failed, in a shape a screen can branch on and the store can persist.
 *
 * A plain class rather than a sealed hierarchy because it has to survive a round trip through the state
 * table as `(kind, code, message)`; [cause] is only present on the instance that was thrown, never on
 * one read back. Equality ignores [cause] for the same reason.
 */
class AxiomError(
    val kind: Kind,
    val message: String,
    val code: Int? = null,
    val cause: Throwable? = null,
) {
    enum class Kind {
        /** The socket: no connection, DNS, timeout, connection reset. */
        NETWORK,

        /** A non-2xx HTTP response; [code] is the status. */
        HTTP,

        /** The gateway's own envelope said no; [code] is its status field. See [ApiEnvelopeException]. */
        SERVER,

        /** The body could not be parsed into the declared type. */
        SERIALIZATION,

        /** The task's `transform` threw. */
        TRANSFORM,

        /** Anything else. */
        UNKNOWN,
    }

    /**
     * Whether another attempt could plausibly succeed. Network failures and 5xx yes; a 4xx, a payload
     * that does not parse or a transform that throws will fail the same way next time. The default
     * [com.axiom.task.RetryPolicy] retries exactly these.
     */
    val isTransient: Boolean
        get() =
            when (kind) {
                Kind.NETWORK, Kind.UNKNOWN -> true
                Kind.HTTP -> code == 408 || code == 429 || (code ?: 0) >= 500
                Kind.SERVER -> (code ?: 0) >= 500
                Kind.SERIALIZATION, Kind.TRANSFORM -> false
            }

    override fun equals(other: Any?): Boolean =
        other is AxiomError && other.kind == kind && other.code == code && other.message == message

    override fun hashCode(): Int = (kind.hashCode() * 31 + (code ?: 0)) * 31 + message.hashCode()

    override fun toString(): String = "AxiomError($kind${code?.let { " $it" } ?: ""}: $message)"

    /** Where in the pipeline a throwable came from; decides the fallback kind. */
    enum class Phase { FETCH, TRANSFORM, STORE }

    companion object {
        fun classify(
            throwable: Throwable,
            phase: Phase,
        ): AxiomError {
            if (throwable is AxiomException) return throwable.error
            val message = throwable.message ?: throwable.javaClass.simpleName
            return when {
                phase == Phase.TRANSFORM -> AxiomError(Kind.TRANSFORM, message, cause = throwable)
                throwable is ApiEnvelopeException ->
                    AxiomError(Kind.SERVER, message, code = throwable.status, cause = throwable)
                throwable is HttpException ->
                    AxiomError(Kind.HTTP, message, code = throwable.code(), cause = throwable)
                // MalformedJsonException is an IOException; it has to be seen before the network case.
                throwable is MalformedJsonException || throwable is JsonParseException ->
                    AxiomError(Kind.SERIALIZATION, message, cause = throwable)
                throwable is IOException -> AxiomError(Kind.NETWORK, message, cause = throwable)
                else -> AxiomError(Kind.UNKNOWN, message, cause = throwable)
            }
        }
    }
}

/** A throwable carrying an already-classified [AxiomError] — for a task that wants to name its own failure kind. */
class AxiomException(
    val error: AxiomError,
) : Exception(error.message, error.cause)
