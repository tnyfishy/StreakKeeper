package com.tnyfishy.sk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tnyfishy.sk.data.StreakRepository
import com.tnyfishy.sk.service.StreakScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val repo = StreakRepository.getInstance(context)
            if (repo.isScheduled()) {
                StreakScheduler.scheduleNextRun(context)
            }
        }
    }
}
