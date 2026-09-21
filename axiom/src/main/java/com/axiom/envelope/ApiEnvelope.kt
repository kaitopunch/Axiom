package com.axiom.envelope

import com.google.gson.annotations.SerializedName
import java.io.IOException

/**
 * The `{ status, message, data }` envelope many gateways wrap every payload in. Optional: a task's
 * `fetch` may return whatever its Retrofit interface returns; this is here so an app on this convention
 * does not write the unwrap and the status check itself, and gets the failure classified as a
 * [com.axiom.state.AxiomError.Kind.SERVER] error rather than a generic one.
 *
 * [status] is the API's own code, not HTTP's. The two disagree often enough to matter: a transport 200
 * can carry a `status` that is not, so Retrofit reporting success is not on its own proof the payload is
 * good. [requireData] is where that second check lives.
 */
data class ApiEnvelope<T>(
    @SerializedName("status")
    val status: Int? = null,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("data")
    val data: T? = null,
)

/** Thrown by [requireData] when the envelope's status is not the success code or its data is missing. */
class ApiEnvelopeException(
    val status: Int?,
    val serverMessage: String?,
    what: String?,
) : IOException(
        "Fetching ${what ?: "payload"} failed: status=$status, message=$serverMessage",
    )

private const val DEFAULT_SUCCESS = 200

/**
 * The payload, or an [ApiEnvelopeException] naming [what] and the status the server actually sent.
 *
 * @param successStatus the status the gateway uses for success; 200 for most.
 */
fun <T : Any> ApiEnvelope<T>.requireData(
    what: String? = null,
    successStatus: Int = DEFAULT_SUCCESS,
): T {
    if (status != successStatus) throw ApiEnvelopeException(status, message, what)
    return data ?: throw ApiEnvelopeException(status, message ?: "empty data", what)
}

/**
 * [requireData] for a list payload, where a missing `data` on a successful status means "no rows" rather
 * than an error — which is how most list endpoints behave.
 */
fun <T> ApiEnvelope<List<T>>.requireItems(
    what: String? = null,
    successStatus: Int = DEFAULT_SUCCESS,
): List<T> {
    if (status != successStatus) throw ApiEnvelopeException(status, message, what)
    return data.orEmpty()
}
