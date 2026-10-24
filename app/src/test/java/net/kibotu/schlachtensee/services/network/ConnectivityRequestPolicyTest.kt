package net.kibotu.schlachtensee.services.network

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectivityRequestPolicyTest {
    @Test fun allNetworkStates() {
        for (connected in listOf(false, true)) {
            for (underlying in listOf(false, true)) {
                assertEquals(
                    connected && underlying,
                    ConnectivityRequestPolicy.shouldStartRequest(connected, underlying)
                )
            }
        }
    }

    @Test fun preserveHttpStatusAndReportOnlyServerErrors() {
        for (code in listOf(200, 201, 204, 301, 400, 401, 404, 408, 429)) {
            assertFalse(ConnectivityRequestPolicy.isBackendServerFailure(code))
        }
        for (code in listOf(500, 502, 503, 504, 599)) {
            assertTrue(ConnectivityRequestPolicy.isBackendServerFailure(code))
        }
    }

    @Test fun distinguishTransportErrorsFromTlsAndCancelledWork() {
        assertTrue(ConnectivityRequestPolicy.shouldDiagnose(IOException("route broken")))
        assertTrue(ConnectivityRequestPolicy.shouldDiagnose(SocketTimeoutException()))
        assertFalse(ConnectivityRequestPolicy.shouldDiagnose(NoConnectedNetworkException()))
        assertFalse(ConnectivityRequestPolicy.shouldDiagnose(SSLHandshakeException("certificate")))
        assertFalse(ConnectivityRequestPolicy.shouldDiagnose(SSLPeerUnverifiedException("hostname")))
        assertFalse(ConnectivityRequestPolicy.shouldDiagnose(ProtocolException("bad response")))
        assertFalse(ConnectivityRequestPolicy.shouldDiagnose(InterruptedIOException("cancelled")))
        assertFalse(ConnectivityRequestPolicy.shouldDiagnose(IllegalStateException()))
    }

    @Test fun limiterUsesMonotonicClockAndRecoversAfterCooldown() {
        var now = 1_000L
        val limiter = DiagnosticRateLimiter({ now }, 8_000L)
        assertTrue(limiter.tryAcquire())
        assertFalse(limiter.tryAcquire())
        now = 8_999L
        assertFalse(limiter.tryAcquire())
        now = 9_000L
        assertTrue(limiter.tryAcquire())
        // A test clock rollback is not allowed to keep the limiter stuck indefinitely.
        now = 1L
        assertTrue(limiter.tryAcquire())
    }

    @Test fun concurrentFailuresScheduleExactlyOneProbe() {
        val limiter = DiagnosticRateLimiter({ 10_000L })
        val threads = Executors.newFixedThreadPool(8)
        val ready = CountDownLatch(8)
        val go = CountDownLatch(1)
        val completed = CountDownLatch(8)
        val winners = AtomicInteger()
        repeat(8) {
            threads.execute {
                try {
                    ready.countDown()
                    go.await()
                    if (limiter.tryAcquire()) winners.incrementAndGet()
                } finally {
                    completed.countDown()
                }
            }
        }
        try {
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            go.countDown()
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertEquals(1, winners.get())
        } finally {
            go.countDown()
            threads.shutdownNow()
        }
    }
}
