package net.kibotu.schlachtensee.services.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

/** Deterministic local contract tests: no dependence on a third-party server. */
class SchlachtenseeApiContractTest {
    private lateinit var backend: MockWebServer
    private lateinit var api: SchlachtenseeApi

    @Before
    fun setUp() {
        backend = MockWebServer()
        backend.start()
        api = Retrofit.Builder()
            .baseUrl(backend.url("/"))
            .client(OkHttpClient.Builder()
                .callTimeout(3, TimeUnit.SECONDS)
                .build())
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(SchlachtenseeApi::class.java)
    }

    @After
    fun tearDown() {
        backend.shutdown()
    }

    @Test
    fun lastTemperatureUsesExpectedPathAndReturnsPayload() {
        backend.enqueue(MockResponse().setResponseCode(200).setBody("12.7"))
        assertEquals("12.7", runBlocking { api.lastTemperature() })
        assertEquals("/jmn_ajax.php?anfrage=6", backend.takeRequest().path)
    }

    @Test
    fun http503IsNotMisclassifiedAsOffline() {
        backend.enqueue(MockResponse().setResponseCode(503).setBody("temporary outage"))
        var status = 0
        try {
            runBlocking { api.lastTemperature() }
            fail("Expected Retrofit to preserve the HTTP error")
        } catch (error: HttpException) {
            status = error.code()
        }
        assertEquals(503, status)
        assertEquals("/jmn_ajax.php?anfrage=6", backend.takeRequest().path)
    }

    @Test
    fun connectionFailuresPropagateAsTransportErrors() {
        backend.shutdown()
        try {
            runBlocking { api.lastTemperature() }
            fail("Expected an IOException for a closed backend")
        } catch (error: IOException) {
            assertTrue(error !is NoConnectedNetworkException)
        }
    }
}
