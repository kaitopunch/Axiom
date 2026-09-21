package com.duylt.demo.axiom.data

import com.axiom.Axiom
import com.axiom.log.AxiomLogger
import com.axiom.state.AxiomError
import com.axiom.state.SyncResult
import com.duylt.demo.axiom.data.record.CategoryRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * The tasks against a real wire and a real (in-memory) store — the "Fetch qua wire thật" recipe from
 * axiom/README.md §5. `Axiom.create` is not the singleton, so everything goes through `run()`.
 */
@RunWith(RobolectricTestRunner::class)
class DemoTasksTest {
    private lateinit var server: MockWebServer
    private lateinit var axiom: Axiom

    /** Path → body. Concurrent image downloads make a FIFO queue of responses unreliable, so route by path. */
    private val routes = mutableMapOf<String, MockResponse>()

    @Before
    fun setUp() {
        server =
            MockWebServer().apply {
                dispatcher =
                    object : Dispatcher() {
                        override fun dispatch(request: RecordedRequest): MockResponse =
                            routes[request.path?.substringBefore('?')] ?: MockResponse().setResponseCode(404)
                    }
                start()
            }
        axiom =
            Axiom.create(RuntimeEnvironment.getApplication()) {
                inMemoryDatabase = true
                logger = AxiomLogger.NONE
                defaultStaleAfter = null // run() the same key twice without the freshness window in the way
                demo(baseUrl = server.url("/").toString())
            }
    }

    @After
    fun tearDown() {
        axiom.close()
        server.shutdown()
    }

    @Test
    fun `categories - second identical fetch is Unchanged`() =
        runTest {
            routes["/products/categories"] = json("""[{"slug":"beauty","name":"Beauty","url":"x"},{"slug":"laptops","name":"Laptops","url":"y"}]""")

            assertTrue(axiom.run(DemoTasks.categories.key) is SyncResult.Success)
            assertEquals(
                listOf(CategoryRecord("beauty", "Beauty"), CategoryRecord("laptops", "Laptops")),
                axiom.read(DemoTasks.categories),
            )

            // Same bytes again: fetched, fingerprinted equal, not written (README §4.3).
            assertTrue(axiom.run(DemoTasks.categories.key) is SyncResult.Unchanged)
        }

    @Test
    fun `products - thumbnails are cached and rows point at the files`() =
        runTest {
            val thumb = server.url("/img/1.webp").toString()
            routes["/products"] =
                json(
                    """{"products":[{"id":1,"title":"Mascara","price":9.99,"category":"beauty","brand":"Essence","rating":2.5,"thumbnail":"$thumb"},
                       {"id":2,"title":"","price":1.0,"category":"beauty","brand":null,"rating":1.0,"thumbnail":"$thumb"}],"total":2,"skip":0,"limit":0}""",
                )
            routes["/img/1.webp"] = MockResponse().setBody("not-really-an-image")

            assertTrue(axiom.run(DemoTasks.products.key) is SyncResult.Success)

            val store = checkNotNull(axiom.read(DemoTasks.products))
            assertEquals(2, store.total)
            assertEquals(listOf(1), store.products.map { it.id }) // the untitled row was dropped
            val cached = checkNotNull(store.products.single().thumbLocal)
            assertEquals("not-really-an-image", File(cached).readText())
        }

    @Test
    fun `products - a failed thumbnail download keeps the remote URL`() =
        runTest {
            val thumb = server.url("/img/missing.webp").toString()
            routes["/products"] =
                json("""{"products":[{"id":7,"title":"Lipstick","price":12.0,"category":"beauty","brand":"Chic","rating":4.0,"thumbnail":"$thumb"}],"total":1}""")
            // no route for the image → 404

            assertTrue(axiom.run(DemoTasks.products.key) is SyncResult.Success)
            val row = checkNotNull(axiom.data(DemoTasks.products).first()).products.single()
            assertNull(row.thumbLocal)
            assertEquals(thumb, row.thumbUrl)
        }

    @Test
    fun `probe 404 - classified as HTTP 404, not transient`() =
        runTest {
            val failure = axiom.run(DemoTasks.http404.key) as SyncResult.Failure
            assertEquals(AxiomError.Kind.HTTP, failure.error.kind)
            assertEquals(404, failure.error.code)
            assertTrue(!failure.error.isTransient)
        }

    private fun json(body: String): MockResponse = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}
