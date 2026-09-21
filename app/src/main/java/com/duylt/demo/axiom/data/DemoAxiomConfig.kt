package com.duylt.demo.axiom.data

import com.axiom.AxiomConfig
import com.axiom.client.HttpLogging
import kotlin.time.Duration.Companion.seconds

/**
 * Everything the data layer needs from Axiom, in one call, so `Application.onCreate` passes parameters
 * and nothing else — the pattern axiom/README.md §2 step 4 recommends:
 *
 * ```
 * Axiom.init(this) { demo(baseUrl = BuildConfig.DUMMYJSON_BASE_URL) }
 * ```
 */
fun AxiomConfig.demo(
    baseUrl: String,
    httpLogging: HttpLogging = HttpLogging.NONE,
) {
    client(DemoClients.API) {
        this.baseUrl = baseUrl
        // A fixed header is treated as a credential and redacted from the log lines (visible with
        // logging = HEADERS; the demo uses BASIC so the Lab's log card stays one line per call).
        header("X-Demo-Client", "axiom-demo/1.0")
        logging = httpLogging
        connectTimeout = 15.seconds
        readTimeout = 15.seconds
    }

    client(DemoClients.ASSETS) {
        connectTimeout = 15.seconds
        readTimeout = 15.seconds
    }

    client(DemoClients.BROKEN) {
        this.baseUrl = DemoClients.BROKEN_BASE_URL
        connectTimeout = 5.seconds
    }

    tasks(DemoTasks.all)
}
