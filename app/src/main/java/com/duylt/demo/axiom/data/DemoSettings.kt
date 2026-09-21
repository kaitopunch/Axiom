package com.duylt.demo.axiom.data

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The knobs `Axiom.init` is given, kept in one object so the Lab screen shows the same values the
 * runtime was built with (the SDK does not expose its config after init).
 */
object DemoSettings {
    /**
     * Deliberately `false` even in a debug build. A real app passes `BuildConfig.DEBUG` (README §2
     * step 4) so a developer sees fresh data on every relaunch — but then the freshness window never
     * fires, and this demo exists to *show* it: pull-to-refresh inside the window answers
     * `SyncResult.Skipped`, and "Clear + Sync" is the deliberate way past it (README §4.1).
     */
    const val IS_DEBUG: Boolean = false

    /**
     * Short so the window is observable in a demo session; the SDK's default is 30 minutes. Applies to
     * every task that sets no `staleAfter` of its own — `users` here.
     */
    val DEFAULT_STALE_AFTER: Duration = 5.minutes

    /** The `products` task's own window; shorter than the default so the two are visibly different. */
    val PRODUCTS_STALE_AFTER: Duration = 2.minutes

    /** How many products to sync. dummyjson has 194; sixty keeps a cold-start image download short. */
    const val PRODUCT_LIMIT: Int = 60

    /** How often the `categories` task also runs in the background. WorkManager's floor is 15 minutes. */
    val CATEGORIES_PERIOD: Duration = 15.minutes
}
