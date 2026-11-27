package com.tnyfishy.sk

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.tnyfishy.sk.data.StreakRepository
import com.tnyfishy.sk.receiver.BatteryLowReceiver
import com.tnyfishy.sk.service.NetworkChangeMonitor
import com.tnyfishy.sk.service.StreakService

class StreakSaverApp : Application() {
    private var batteryLowReceiver: BatteryLowReceiver? = null

    override fun onCreate() {
        super.onCreate()
        StreakRepository.getInstance(this)
        StreakService.createNotificationChannels(this)
        NetworkChangeMonitor.register(this)

        batteryLowReceiver = BatteryLowReceiver().also { receiver ->
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_LOW),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }
}
