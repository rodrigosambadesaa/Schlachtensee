package net.kibotu.schlachtensee.services.network

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import net.kibotu.logger.Logger
import java.util.concurrent.atomic.AtomicLong

/** Application-specific connectivity coordinator. */
class AppConnectivityManager {
    companion object {
        const val PRIMARY_APP_HOST = "http://jmnberlin.de"
        const val PRIMARY_APP_ENDPOINT = "http://jmnberlin.de/jmn_ajax.php?anfrage=6"
        private const val DIAGNOSTIC_COOLDOWN_MS = 5_000L
    }

    enum class Feedback { NO_NETWORK, NO_INTERNET, BACKEND_UNAVAILABLE }

    private val feedbackEvents = MutableSharedFlow<Feedback>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val feedback: SharedFlow<Feedback> = feedbackEvents
    private val generalConnectivity = ConnectivityAndInternetAccess.Builder().build()
    private val lastDiagnosisStarted = AtomicLong(0L)
    private val diagnosisInFlight = AtomicLong(0L)

    fun notifyNoNetwork() {
        feedbackEvents.tryEmit(Feedback.NO_NETWORK)
    }

    /**
     * The real backend request is authoritative. Generic Internet diagnosis is
     * started only after a transport-level failure.
     */
    fun diagnoseGeneralInternetAccess(
        context: Context,
        callback: ((ConnectivityAndInternetAccess.InternetResult) -> Unit)? = null
    ): ConnectivityAndInternetAccess.Request? {
        val now = SystemClock.elapsedRealtime()
        val previous = lastDiagnosisStarted.get()
        if (now - previous < DIAGNOSTIC_COOLDOWN_MS ||
            !lastDiagnosisStarted.compareAndSet(previous, now) ||
            !diagnosisInFlight.compareAndSet(0L, 1L)
        ) return null

        Logger.w("[Connectivity] Backend transport failure; starting general Internet diagnostic")
        return generalConnectivity.checkInternetAsync(context) { result ->
            try {
                feedbackEvents.tryEmit(
                    if (result.isReachable()) Feedback.BACKEND_UNAVAILABLE else Feedback.NO_INTERNET
                )
                Logger.w(
                    "[Connectivity] Internet reachable=" + result.isReachable() +
                        " via=" + result.reachedHost
                )
                callback?.invoke(result)
            } finally {
                diagnosisInFlight.set(0L)
            }
        }
    }

    fun observeNetwork(
        context: Context,
        callback: ConnectivityAndInternetAccess.NetworkStateCallback
    ): ConnectivityAndInternetAccess.NetworkObserver =
        ConnectivityAndInternetAccess.observeNetwork(context, callback)
}
