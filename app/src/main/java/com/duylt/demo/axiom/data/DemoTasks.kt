package com.duylt.demo.axiom.data

import com.axiom.task.RetryPolicy
import com.axiom.task.TaskScope
import com.axiom.task.TaskSpec
import com.axiom.task.api
import com.axiom.task.task
import com.duylt.demo.axiom.data.local.ImageCache
import com.duylt.demo.axiom.data.record.CategoryRecord
import com.duylt.demo.axiom.data.record.ProbeRecord
import com.duylt.demo.axiom.data.record.ProductStore
import com.duylt.demo.axiom.data.record.UserRecord
import com.duylt.demo.axiom.data.record.toRecord
import com.duylt.demo.axiom.data.record.toRecordOrNull
import com.duylt.demo.axiom.data.remote.DummyJsonApi
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Every Axiom task the demo registers (axiom/README.md §2 step 3). Three fetch real content; three are
 * probes that fail on purpose so the Lab screen can show how each failure kind is classified and
 * whether it is retried.
 */
object DemoTasks {
    private fun TaskScope.api(): DummyJsonApi = api<DummyJsonApi>(DemoClients.API)

    /**
     * The showcase task: fetch, then a `transform` that caches thumbnails and **publishes** the rows
     * first so the list draws with remote URLs while the download runs (README §2 step 3, "hiện rows
     * trước, tải ảnh sau"). The Products screen marks each row local/remote so the hand-over is visible.
     */
    val products: TaskSpec<ProductStore> =
        task("products") {
            fetch {
                val page = api().getProducts(limit = DemoSettings.PRODUCT_LIMIT)
                val rows = page.products.mapNotNull { it.toRecordOrNull() }.sortedBy { it.id }
                if (rows.size < page.products.size) {
                    Timber.w("products: dropped ${page.products.size - rows.size} rows with no title or thumbnail")
                }
                ProductStore(total = page.total, products = rows)
            }
            transform { store ->
                val cache = ImageCache(context, namespace = "products", calls = callFactory(DemoClients.ASSETS))
                val urls = store.products.map { it.thumbUrl }
                val onDisk = cache.cachedPaths(urls)
                val withDisk = store.withThumbs(onDisk)

                val missing = urls.filter { onDisk[it] == null }
                val final =
                    if (missing.isEmpty()) {
                        withDisk // every picture already cached: one store write, one emission
                    } else {
                        publish(withDisk) // rows visible now, remote URLs where the cache has nothing
                        Timber.i("products: caching ${missing.size} thumbnails")
                        store.withThumbs(onDisk + cache.resolveAll(missing))
                    }
                // A download that failed left `thumbLocal` null; that row keeps its URL and the next sync
                // retries, because the *final* value is what Axiom fingerprints (README §10).
                cache.prune(keepPaths = final.products.mapNotNullTo(HashSet()) { it.thumbLocal })
                final
            }
            staleAfter = DemoSettings.PRODUCTS_STALE_AFTER
        }

    /** A plain list. No `staleAfter` → `AxiomConfig.defaultStaleAfter`. Read through `asPagingData()` on the Users screen. */
    val users: TaskSpec<List<UserRecord>> =
        task("users") {
            fetch { api().getUsers().users.map { it.toRecord() }.sortedBy { it.id } }
        }

    /**
     * `Duration.ZERO` opts out of the freshness window — every run fetches — and `periodic` adds a
     * PeriodicWorkRequest next to the one-time one, visible on the Lab screen's WorkManager card.
     */
    val categories: TaskSpec<List<CategoryRecord>> =
        task("categories") {
            fetch { api().getCategories().map { it.toRecord() } }
            staleAfter = Duration.ZERO
            periodic = DemoSettings.CATEGORIES_PERIOD
        }

    // ---- Probes: never succeed, exist to be watched on the Lab screen ----

    /** HTTP 500 → `AxiomError.Kind.HTTP`, code 500, transient → retried with backoff, then `Failed`. */
    val http500: TaskSpec<ProbeRecord> =
        task("probe-http-500") {
            fetch {
                api().fail(500, "Server exploded")
                ProbeRecord(reachedAt = System.currentTimeMillis())
            }
            // Two retries, ten seconds apart-ish, so the whole ladder plays out inside a demo session.
            retry = RetryPolicy(maxRetries = 2, initialBackoff = 10.seconds)
        }

    /** HTTP 404 → `Kind.HTTP`, code 404, **not** transient → `Failed` after one attempt, no retry. */
    val http404: TaskSpec<ProbeRecord> =
        task("probe-http-404") {
            fetch {
                api().fail(404, "No such thing")
                ProbeRecord(reachedAt = System.currentTimeMillis())
            }
        }

    /** DNS failure on the `broken` client → `Kind.NETWORK`, transient → retried, then `Failed`. */
    val unreachable: TaskSpec<ProbeRecord> =
        task("probe-unreachable") {
            fetch {
                api<DummyJsonApi>(DemoClients.BROKEN).getCategories()
                ProbeRecord(reachedAt = System.currentTimeMillis())
            }
            retry = RetryPolicy(maxRetries = 1, initialBackoff = 10.seconds)
        }

    /** What `Application.onCreate` schedules. The probes are left for the Lab screen to trigger by hand. */
    val content: List<TaskSpec<*>> = listOf(products, users, categories)

    val probes: List<TaskSpec<*>> = listOf(http500, http404, unreachable)

    val all: List<TaskSpec<*>> = content + probes

    private fun ProductStore.withThumbs(resolved: Map<String, String?>): ProductStore =
        copy(products = products.map { it.copy(thumbLocal = resolved[it.thumbUrl]) })
}
