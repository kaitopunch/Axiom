package com.axiom.client

import android.content.Context
import com.axiom.Axiom
import com.axiom.api
import com.axiom.log.AxiomLogger
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.http.GET

/** Named clients over a real socket: what each one puts on the wire, and what a client without a base URL can and cannot do. */
@RunWith(RobolectricTestRunner::class)
class ClientRegistryTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var axiom: Axiom

    interface Api {
        @GET("ping")
        suspend fun ping(): Map<String, String>
    }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
        if (::axiom.isInitialized) axiom.close()
    }

    @Test
    fun `static and provided headers are sent, provided ones winning on a clash`() =
        runBlocking {
            var token = "first"
            axiom =
                Axiom.create(context) {
                    inMemoryDatabase = true
                    logger = AxiomLogger.NONE
                    client("api") {
                        baseUrl = server.url("/v1").toString() // no trailing slash on purpose
                        header("X-API-KEY", "secret")
                        header("Authorization", "static")
                        headers { mapOf("Authorization" to "Bearer $token") }
                    }
                }
            server.enqueue(MockResponse().setBody("""{"ok":"1"}"""))
            server.enqueue(MockResponse().setBody("""{"ok":"2"}"""))

            axiom.api<Api>("api").ping()
            token = "second"
            axiom.api<Api>("api").ping()

            val first = server.takeRequest()
            val second = server.takeRequest()
            assertEquals("/v1/ping", first.path)
            assertEquals("secret", first.getHeader("X-API-KEY"))
            assertEquals("Bearer first", first.getHeader("Authorization"))
            assertEquals("Bearer second", second.getHeader("Authorization"))
        }

    @Test
    fun `a client without a base URL hands out a call factory but not a Retrofit`() {
        axiom =
            Axiom.create(context) {
                inMemoryDatabase = true
                logger = AxiomLogger.NONE
                client("assets")
            }
        server.enqueue(MockResponse().setBody("bytes"))

        val body =
            axiom
                .callFactory("assets")
                .newCall(Request.Builder().url(server.url("/pic.png")).build())
                .execute()
                .use { it.body!!.string() }

        assertEquals("bytes", body)
        val thrown = assertThrows(IllegalStateException::class.java) { axiom.retrofit("assets") }
        assertTrue(thrown.message!!.contains("assets"))
        assertNull(server.takeRequest().getHeader("X-API-KEY"))
    }

    @Test
    fun `an undeclared client is refused by name`() {
        axiom =
            Axiom.create(context) {
                inMemoryDatabase = true
                logger = AxiomLogger.NONE
            }

        val thrown = assertThrows(IllegalArgumentException::class.java) { axiom.callFactory("ghost") }

        assertTrue(thrown.message!!.contains("ghost"))
    }

    @Test
    fun `a client added after init is usable`() =
        runBlocking {
            axiom =
                Axiom.create(context) {
                    inMemoryDatabase = true
                    logger = AxiomLogger.NONE
                }
            axiom.client("late") { baseUrl = server.url("/").toString() }
            server.enqueue(MockResponse().setBody("""{"ok":"1"}"""))

            assertEquals(mapOf("ok" to "1"), axiom.api<Api>("late").ping())
        }
}
