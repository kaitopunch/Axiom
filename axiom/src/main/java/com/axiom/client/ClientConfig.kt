package com.axiom.client

import okhttp3.Interceptor
import retrofit2.Converter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How much of each request/response the named client logs. Mirrors OkHttp's logging levels. */
enum class HttpLogging { NONE, BASIC, HEADERS, BODY }

/**
 * One named HTTP client: a host, its headers, its timeouts.
 *
 * A client without [baseUrl] is legal and useful — it can hand out a `Call.Factory` for raw downloads
 * but not a Retrofit, which needs a base URL to resolve relative paths against. Asking such a client for
 * a Retrofit throws with the client's name in the message.
 */
class ClientConfig internal constructor(
    val name: String,
) {
    /** Base URL for Retrofit services. A trailing `/` is added if missing — Retrofit requires it. */
    var baseUrl: String? = null

    var connectTimeout: Duration = 30.seconds
    var readTimeout: Duration = 30.seconds
    var writeTimeout: Duration = 30.seconds

    /** Logging level for this client alone. `NONE` by default — a client that downloads 2600 images should not log 2600 lines. */
    var logging: HttpLogging = HttpLogging.NONE

    /** Overrides the default Gson converter for this client. */
    var converterFactory: Converter.Factory? = null

    internal val staticHeaders = LinkedHashMap<String, String>()
    internal var headerProvider: (() -> Map<String, String>)? = null
    internal val interceptors = mutableListOf<Interceptor>()
    internal val networkInterceptors = mutableListOf<Interceptor>()
    internal val redactedHeaders = mutableSetOf<String>()

    /** A header sent on every request. Also redacted from logs, since a fixed header is usually a credential. */
    fun header(
        name: String,
        value: String,
    ) {
        staticHeaders[name] = value
        redactedHeaders += name
    }

    /**
     * Headers computed per request — a token that rotates, a locale that changes. Called on OkHttp's
     * thread for every request, so it must be cheap and must not block on the network.
     */
    fun headers(provider: () -> Map<String, String>) {
        headerProvider = provider
    }

    /** Keeps a header's value out of the log line. [header] does this automatically. */
    fun redactHeader(name: String) {
        redactedHeaders += name
    }

    /** An application interceptor, run after Axiom's header interceptor and before logging. */
    fun interceptor(interceptor: Interceptor) {
        interceptors += interceptor
    }

    /** A network interceptor. See OkHttp's docs for the difference. */
    fun networkInterceptor(interceptor: Interceptor) {
        networkInterceptors += interceptor
    }
}
