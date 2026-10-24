package net.kibotu.schlachtensee.services.network

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import net.kibotu.logger.Logger

/**
 * Restricts active diagnostics to at most one start per interval even when
 * several simultaneous OkHttp calls fail. Never depends on callbacks to unlock.
 */
internal class DiagnosticRateLimiter(
    private val elapsedRealtime: () -> Long,
    private val minimumIntervalMs: Long = 8_000L
) {
    private val lastStarted = AtomicLong(-1L)

    fun tryAcquire(): Boolean {
        while (true) {
            val now = elapsedRealtime()
            val previous = lastStarted.get()
            if (previous >= 0L && now >= previous && now - previous < minimumIntervalMs) {
                return false
            }
            if (lastStarted.compareAndSet(previous, now)) return true
        }
    }
}

/** Application-specific coordinator. The backend request is authoritative. */
class AppConnectivityManager {
    companion object {
        const val PRIMARY_APP_HOST = "http://jmnberlin.de"
        const val PRIMARY_APP_ENDPOINT = "http://jmnberlin.de/jmn_ajax.php?anfrage=6"
    }

    enum class Feedback { NO_NETWORK, NO_INTERNET, BACKEND_UNAVAILABLE }

    private val feedbackEvents = MutableSharedFlow<Feedback>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val feedback: SharedFlow<Feedback> = feedbackEvents

    private val generalConnectivity = ConnectivityAndInternetAccess.Builder().build()
    private val diagnosisLimiter = DiagnosticRateLimiter(SystemClock::elapsedRealtime)

    fun notifyNoNetwork() {
        feedbackEvents.tryEmit(Feedback.NO_NETWORK)
    }

    /**
     * This is a *generic* reachability diagnostic, not a test of jmnberlin.de.
     * The real OkHttp failure must be preserved for the caller.
     *
     * A failed broad probe is inconclusive under restrictive networks, so
     * NO_INTERNET is a user-facing diagnostic rather than an authoritative verdict.
     */
    fun diagnoseGeneralInternetAccess(
        context: Context,
        callback: ((ConnectivityAndInternetAccess.InternetResult) -> Unit)? = null
    ): ConnectivityAndInternetAccess.Request? {
        if (!diagnosisLimiter.tryAcquire()) return null

        Logger.w("[Connectivity] Transport failure; checking general Internet reachability")
        return generalConnectivity.checkInternetAsync(context.applicationContext) { result ->
            feedbackEvents.tryEmit(
                if (result.isReachable()) Feedback.BACKEND_UNAVAILABLE else Feedback.NO_INTERNET
            )
            Logger.w(
                "[Connectivity] Generic Internet reachable=" + result.isReachable() +
                    " via=" + result.reachedHost
            )
            callback?.invoke(result)
        }
    }

    fun observeNetwork(
        context: Context,
        callback: ConnectivityAndInternetAccess.NetworkStateCallback
    ): ConnectivityAndInternetAccess.NetworkObserver =
        ConnectivityAndInternetAccess.observeNetwork(context, callback)
}
