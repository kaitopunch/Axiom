package com.axiom.state

import com.axiom.envelope.ApiEnvelope
import com.axiom.envelope.ApiEnvelopeException
import com.axiom.envelope.requireData
import com.axiom.envelope.requireItems
import com.google.gson.JsonSyntaxException
import com.google.gson.stream.MalformedJsonException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

/** The classification table: which throwable becomes which kind, and which kinds are worth a retry. */
class AxiomErrorTest {
    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody("text/plain".toMediaType())))

    @Test
    fun `throwables map to kinds`() {
        assertEquals(AxiomError.Kind.NETWORK, AxiomError.classify(SocketTimeoutException("t"), AxiomError.Phase.FETCH).kind)
        assertEquals(AxiomError.Kind.NETWORK, AxiomError.classify(IOException("io"), AxiomError.Phase.FETCH).kind)
        assertEquals(AxiomError.Kind.HTTP, AxiomError.classify(http(404), AxiomError.Phase.FETCH).kind)
        assertEquals(404, AxiomError.classify(http(404), AxiomError.Phase.FETCH).code)
        assertEquals(AxiomError.Kind.SERVER, AxiomError.classify(ApiEnvelopeException(500, "boom", "skins"), AxiomError.Phase.FETCH).kind)
        assertEquals(500, AxiomError.classify(ApiEnvelopeException(500, "boom", "skins"), AxiomError.Phase.FETCH).code)
        assertEquals(AxiomError.Kind.SERIALIZATION, AxiomError.classify(JsonSyntaxException("bad"), AxiomError.Phase.FETCH).kind)
        // An IOException subclass that means "the body is not JSON", not "the socket died".
        assertEquals(AxiomError.Kind.SERIALIZATION, AxiomError.classify(MalformedJsonException("bad"), AxiomError.Phase.FETCH).kind)
        assertEquals(AxiomError.Kind.UNKNOWN, AxiomError.classify(IllegalStateException("?"), AxiomError.Phase.FETCH).kind)
        // Anything thrown by a transform is a transform failure, whatever its type.
        assertEquals(AxiomError.Kind.TRANSFORM, AxiomError.classify(IOException("disk"), AxiomError.Phase.TRANSFORM).kind)
    }

    @Test
    fun `an AxiomException carries its own classification through`() {
        val own = AxiomError(AxiomError.Kind.SERVER, "custom", code = 42)

        assertEquals(own, AxiomError.classify(AxiomException(own), AxiomError.Phase.FETCH))
    }

    @Test
    fun `transient means worth another attempt`() {
        assertTrue(AxiomError(AxiomError.Kind.NETWORK, "").isTransient)
        assertTrue(AxiomError(AxiomError.Kind.UNKNOWN, "").isTransient)
        assertTrue(AxiomError(AxiomError.Kind.HTTP, "", 503).isTransient)
        assertTrue(AxiomError(AxiomError.Kind.HTTP, "", 429).isTransient)
        assertFalse(AxiomError(AxiomError.Kind.HTTP, "", 404).isTransient)
        assertFalse(AxiomError(AxiomError.Kind.HTTP, "", 401).isTransient)
        assertTrue(AxiomError(AxiomError.Kind.SERVER, "", 500).isTransient)
        assertFalse(AxiomError(AxiomError.Kind.SERVER, "", 400).isTransient)
        assertFalse(AxiomError(AxiomError.Kind.SERIALIZATION, "").isTransient)
        assertFalse(AxiomError(AxiomError.Kind.TRANSFORM, "").isTransient)
    }

    @Test
    fun `equality ignores the cause so a persisted error equals the thrown one`() {
        val thrown = AxiomError(AxiomError.Kind.NETWORK, "down", cause = IOException("down"))
        val restored = AxiomError(AxiomError.Kind.NETWORK, "down")

        assertEquals(thrown, restored)
        assertEquals(thrown.hashCode(), restored.hashCode())
    }

    @Test
    fun `requireData unwraps a good envelope and names the failure otherwise`() {
        assertEquals("x", ApiEnvelope(status = 200, data = "x").requireData())
        assertEquals(listOf(1), ApiEnvelope(status = 200, data = listOf(1)).requireItems())
        assertEquals(emptyList<Int>(), ApiEnvelope<List<Int>>(status = 200, data = null).requireItems())

        val notOk = assertThrows(ApiEnvelopeException::class.java) { ApiEnvelope(status = 500, message = "down", data = "x").requireData("skins") }
        assertEquals(500, notOk.status)
        assertTrue(notOk.message!!.contains("skins"))

        assertThrows(ApiEnvelopeException::class.java) { ApiEnvelope<String>(status = 200, data = null).requireData() }
        assertEquals("y", ApiEnvelope(status = 0, data = "y").requireData(successStatus = 0))
    }
}
