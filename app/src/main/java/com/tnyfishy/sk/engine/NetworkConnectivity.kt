package com.tnyfishy.sk.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkConnectivity {
    /**
     * True when the active network is Wi-Fi or cellular and reports NET_CAPABILITY_INTERNET.
     * Intentionally does not require VALIDATED to avoid false skips during brief DNS checks.
     */
    fun hasWifiOrCellularInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}
