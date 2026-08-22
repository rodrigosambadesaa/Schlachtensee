package net.kibotu.schlachtensee.services.network

import android.content.Context
import net.kibotu.logger.Logger

/**
 * Encapsulates connectivity checking logic for Schlachtensee app.
 *
 * Primary checks test the application's required backend endpoints (jmnberlin.de).
 * If the app resource check fails, it falls back to a general external domain probe
 * (google, facebook, etc.) as an extreme case fallback to diagnose overall network access.
 */
class AppConnectivityManager {

    companion object {
        const val PRIMARY_APP_HOST = "http://jmnberlin.de/jmn_ajax.php?anfrage=6"
    }

    private val appResourceConnectivity: ConnectivityAndInternetAccess =
        ConnectivityAndInternetAccess.Builder()
            .setHosts(listOf(PRIMARY_APP_HOST))
            .build()

    private val fallbackExternalConnectivity: ConnectivityAndInternetAccess =
        ConnectivityAndInternetAccess.Builder().build()

    /**
     * Performs an asynchronous internet reachability check.
     * Checks app resources first. If app resource reachability fails, checks general
     * external domains as an extreme fallback.
     */
    fun checkInternetAccess(
        context: Context,
        callback: (ConnectivityAndInternetAccess.InternetResult) -> Unit
    ): ConnectivityAndInternetAccess.Request {
        Logger.d("[Connectivity] Starting primary check against app resource: $PRIMARY_APP_HOST")
        return appResourceConnectivity.checkInternetAsync(context) { appResult ->
            if (appResult.isReachable()) {
                Logger.d("[Connectivity] App resource is reachable via: ${appResult.reachedHost}")
                callback(appResult)
            } else {
                Logger.w("[Connectivity] App resource check failed. Running fallback external domain probe...")
                fallbackExternalConnectivity.checkInternetAsync(context) { fallbackResult ->
                    Logger.d("[Connectivity] Fallback check completed. Reachable: ${fallbackResult.isReachable()}, via: ${fallbackResult.reachedHost}")
                    callback(fallbackResult)
                }
            }
        }
    }

    /**
     * Performs a blocking internet reachability check.
     */
    fun checkInternetAccessBlocking(context: Context): ConnectivityAndInternetAccess.InternetResult {
        val appResult = appResourceConnectivity.checkInternetBlocking(context)
        if (appResult.isReachable()) {
            return appResult
        }
        Logger.w("[Connectivity] App resource blocking check failed. Running fallback external domain probe...")
        return fallbackExternalConnectivity.checkInternetBlocking(context)
    }

    /**
     * Observes network state changes passively using Android's ConnectivityManager callbacks.
     */
    fun observeNetwork(
        context: Context,
        callback: ConnectivityAndInternetAccess.NetworkStateCallback
    ): ConnectivityAndInternetAccess.NetworkObserver {
        return ConnectivityAndInternetAccess.observeNetwork(context, callback)
    }
}
