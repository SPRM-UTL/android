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
            val specifierBuilder = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
            
            if (!password.isNullOrEmpty()) {
                specifierBuilder.setWpa2Passphrase(password)
            }

            val specifier = specifierBuilder.build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    onLog("Successfully connected to $ssid")
                    // Bind process to this network so sockets use it
                    connectivityManager.bindProcessToNetwork(network)
                    onLog("App bound to local network. Ready to send commands.")
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    onLog("Failed to connect to $ssid (Unavailable)")
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    onLog("Lost connection to $ssid")
                    connectivityManager.bindProcessToNetwork(null)
                }
            }

            connectivityManager.requestNetwork(request, networkCallback!!)
            onLog("Network request sent for $ssid (Requires Android 10+ location permission on).")
        } else {
            onLog("For Android < 10, please connect manually via settings for now.")
            // Implementing older WifiManager API for < Android 10 requires more boilerplate.
        }
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

