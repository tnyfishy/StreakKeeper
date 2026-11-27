package com.tnyfishy.sk.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.tnyfishy.sk.AppConstants
import com.tnyfishy.sk.data.StreakRepository
import com.tnyfishy.sk.engine.NetworkConnectivity

object NetworkChangeMonitor {
    private const val TAG = "NetworkChangeMonitor"
    private const val RECOVERY_THROTTLE_MS = 30L * 60L * 1000L

    private val lock = Any()
    private var registered = false
    private var lastRecoveryAttemptMs = 0L

    fun register(context: Context) {
        synchronized(lock) {
            if (registered) return
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return
            val appContext = context.applicationContext
            runCatching {
                cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        tryRecover(appContext)
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities
                    ) {
                        if (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return
                        val ok = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        if (ok) tryRecover(appContext)
                    }
                })
                registered = true
            }.onFailure {
                Log.w(TAG, "registerDefaultNetworkCallback failed: ${it.message}")
            }
        }
    }

    private fun tryRecover(context: Context) {
        runCatching {
            val repo = StreakRepository.getInstance(context)
            if (!repo.isScheduled()) return
            if (StreakService.isRunning) return
            if (!repo.getLastRunFailed()) return
            if (repo.getTodayRetryCount() >= AppConstants.MAX_RETRIES_PER_DAY) return
            if (!NetworkConnectivity.hasWifiOrCellularInternet(context)) return

            synchronized(lock) {
                val now = System.currentTimeMillis()
                if (now - lastRecoveryAttemptMs < RECOVERY_THROTTLE_MS) return
                lastRecoveryAttemptMs = now
            }

            Log.d(TAG, "Network restored; starting recovery streak run")
            StreakScheduler.runNow(context)
        }
    }
}
