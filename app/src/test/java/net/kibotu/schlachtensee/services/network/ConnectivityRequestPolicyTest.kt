package net.kibotu.schlachtensee.services.network

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectivityRequestPolicyTest {

    @Test
    fun requestStartsOnlyWithUsableConnectivity() {
        assertTrue(ConnectivityRequestPolicy.shouldStartRequest(isConnected = true))
        assertFalse(ConnectivityRequestPolicy.shouldStartRequest(isConnected = false))
    }

    @Test
    fun onlyTransportFailuresTriggerGeneralDiagnostics() {
        assertTrue(ConnectivityRequestPolicy.shouldDiagnose(IOException()))
        assertFalse(ConnectivityRequestPolicy.shouldDiagnose(IllegalStateException()))
    }
}
