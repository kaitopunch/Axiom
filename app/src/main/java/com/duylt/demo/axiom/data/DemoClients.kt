package com.duylt.demo.axiom.data

/**
 * The named Axiom clients this app declares (axiom/README.md §2 step 4). Three, and each exists for a
 * reason a viewer can see on the Lab screen:
 */
object DemoClients {
    /** dummyjson.com — every Retrofit call. Logging on in debug; the fixed demo header is auto-redacted. */
    const val API = "dummyjson"

    /**
     * No `baseUrl`: only ever hands out a `Call.Factory` for the thumbnails the products task caches.
     * Absolute URLs come from the payload (a CDN host, not the API host), so a Retrofit would have
     * nothing to resolve against — and a separate client keeps API headers off third-party requests.
     */
    const val ASSETS = "assets"

    /** A host that does not resolve. The `unreachable` task fetches through it to show `AxiomError.Kind.NETWORK` and retry. */
    const val BROKEN = "broken"

    const val BROKEN_BASE_URL = "https://axiom-demo.invalid/"
}
