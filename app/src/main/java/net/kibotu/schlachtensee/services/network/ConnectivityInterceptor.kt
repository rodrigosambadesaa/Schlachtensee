package net.kibotu.schlachtensee.services.network

import android.content.Context
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Applies the cheap local connectivity gate to every OkHttp operation.
 *
 * The real request remains authoritative: an IOException triggers a bounded,
 * general Internet diagnostic, while HTTP responses are returned untouched.
 */
class ConnectivityInterceptor(
    private val context: Context,
    private val connectivityManager: AppConnectivityManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!ConnectivityRequestPolicy.shouldStartRequest(
                isConnected = ConnectivityAndInternetAccess.isConnected(context),
                hasPhysicalNetwork = ConnectivityAndInternetAccess.hasPhysicalNetwork(context)
            )
        ) {
            connectivityManager.notifyNoNetwork()
            throw NoConnectedNetworkException()
        }

        return try {
            chain.proceed(chain.request()).also { response ->
                // Preserve HTTP responses; a 5xx indicates a responding but failing server.
                if (ConnectivityRequestPolicy.isBackendServerFailure(response.code)) {
                    connectivityManager.notifyBackendUnavailable()
                }
            }
        } catch (error: IOException) {
            if (!chain.call().isCanceled() && ConnectivityRequestPolicy.shouldDiagnose(error)) {
                connectivityManager.diagnoseGeneralInternetAccess(context)
            }
            throw error
        }
    }
}

internal object ConnectivityRequestPolicy {
    fun shouldStartRequest(
        isConnected: Boolean,
        hasPhysicalNetwork: Boolean
    ): Boolean = isConnected && hasPhysicalNetwork

    fun isBackendServerFailure(statusCode: Int): Boolean = statusCode in 500..599

    fun shouldDiagnose(error: Throwable): Boolean =
        error is IOException &&
            error !is NoConnectedNetworkException &&
            error !is SSLException &&
            error !is ProtocolException &&
            (error !is InterruptedIOException || error is SocketTimeoutException)
}

class NoConnectedNetworkException : IOException(
    "No usable network is available"
)
