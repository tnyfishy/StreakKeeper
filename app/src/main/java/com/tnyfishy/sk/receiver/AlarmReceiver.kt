package com.tnyfishy.sk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.tnyfishy.sk.AppConstants
import com.tnyfishy.sk.service.StreakService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AppConstants.ACTION_STREAK_ALARM) return
        val serviceIntent = Intent(context, StreakService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
