package net.kibotu.schlachtensee.services.network

import android.content.Context
import java.io.IOException
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
                ConnectivityAndInternetAccess.isConnected(context)
            )
        ) {
            throw NoConnectedNetworkException()
        }

        return try {
            chain.proceed(chain.request())
        } catch (error: IOException) {
            if (ConnectivityRequestPolicy.shouldDiagnose(error)) {
                connectivityManager.diagnoseGeneralInternetAccess(context)
            }
            throw error
        }
    }
}

internal object ConnectivityRequestPolicy {
    fun shouldStartRequest(isConnected: Boolean): Boolean = isConnected

    fun shouldDiagnose(error: Throwable): Boolean = error is IOException
}

class NoConnectedNetworkException : IOException(
    "No usable network is available"
)
