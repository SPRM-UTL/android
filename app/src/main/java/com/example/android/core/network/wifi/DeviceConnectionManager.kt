package com.example.android.core.network.wifi
import android.annotation.SuppressLint

import com.example.android.core.network.api.*
import com.example.android.core.network.client.*
import com.example.android.core.network.bluetooth.*
import com.example.android.core.network.wifi.*
import com.example.android.core.network.stream.*
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class DeviceConnectionManager(
    private val context: Context,
    private val onLog: (String) -> Unit
) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    @SuppressLint("MissingPermission")
    fun connectToWifiAp(ssid: String, password: String? = null) {
        onLog("Attempting to connect to Wi-Fi AP: $ssid")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val specifierBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)
            if (!password.isNullOrEmpty()) specifierBuilder.setWpa2Passphrase(password)

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifierBuilder.build())
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    connectivityManager.bindProcessToNetwork(network)
                    onLog("App bound to $ssid")
                }
                override fun onUnavailable() {
                    super.onUnavailable()
                    onLog("Failed to connect to $ssid")
                }
                override fun onLost(network: Network) {
                    super.onLost(network)
                    connectivityManager.bindProcessToNetwork(null)
                    onLog("Lost connection to $ssid")
                }
            }
            connectivityManager.requestNetwork(request, networkCallback!!)
        } else {
            onLog("Android < 10: connect to AP manually.")
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connectToWifiApAndWait(
        ssid: String,
        password: String? = null,
        timeoutMs: Long = 15_000
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            onLog("Android < 10: assuming already connected.")
            return true
        }

        onLog("Connecting to AP '$ssid' (timeout ${timeoutMs}ms)...")

        val specifierBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)
        if (!password.isNullOrEmpty()) specifierBuilder.setWpa2Passphrase(password)

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifierBuilder.build())
            .build()

        val connected = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        connectivityManager.bindProcessToNetwork(network)
                        onLog("Bound to '$ssid'. Provisioning can start.")
                        if (cont.isActive) cont.resume(true)
                    }
                    override fun onUnavailable() {
                        super.onUnavailable()
                        onLog("AP '$ssid' unavailable.")
                        if (cont.isActive) cont.resume(false)
                    }
                }
                networkCallback = cb
                connectivityManager.requestNetwork(request, cb)
                cont.invokeOnCancellation {
                    connectivityManager.unregisterNetworkCallback(cb)
                    networkCallback = null
                }
            }
        }

        return connected ?: false
    }

    fun disconnectWifi() {
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
            connectivityManager.bindProcessToNetwork(null)
            onLog("Disconnected and unbound from specific network.")
        }
        networkCallback = null
    }
}

