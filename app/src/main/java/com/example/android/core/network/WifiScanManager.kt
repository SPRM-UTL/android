package com.example.android.core.network

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult as WifiScanResult
import android.net.wifi.WifiManager

class WifiScanManager(
    private val context: Context,
    private val onWifiDeviceFound: (WifiScanResult) -> Unit,
    private val onLog: (String) -> Unit
) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val wifiScanReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                scanSuccess()
            } else {
                scanFailure()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startWifiScan() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiScanReceiver, intentFilter)

        val success = wifiManager.startScan()
        if (!success) {
            scanFailure()
        } else {
            onLog("Wi-Fi scan started successfully.")
        }
    }

    fun stopWifiScan() {
        try {
            context.unregisterReceiver(wifiScanReceiver)
        } catch (e: Exception) {
            // Ignore if not registered
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanSuccess() {
        onLog("Wi-Fi Scan complete. Found results:")
        val results = wifiManager.scanResults
        for (result in results) {
            onWifiDeviceFound(result)
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanFailure() {
        onLog("Wi-Fi Scan failed (maybe throttled). Using older results.")
        val results = wifiManager.scanResults
        for (result in results) {
            onWifiDeviceFound(result)
        }
    }
}
