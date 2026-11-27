package com.tnyfishy.sk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tnyfishy.sk.data.StreakRepository
import com.tnyfishy.sk.service.StreakScheduler
import com.tnyfishy.sk.service.StreakService
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BatteryLowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BATTERY_LOW) return

        runCatching {
            val repo = StreakRepository.getInstance(context)
            if (!repo.getSendOnBatteryLow()) return
            if (!repo.isScheduled()) return
            if (StreakService.isRunning) return
            if (repo.wasBatteryAnticipationUsedToday()) return

            val enabled = repo.getEnabledFriends()
            if (enabled.isEmpty()) return

            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val anyFriendPending = enabled.any { f ->
                f.lastMessageSentMillis?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today
                } != true
            }
            val lastRunNotToday = repo.getLastRunMillis()?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today
            } != true

            if (!anyFriendPending && !lastRunNotToday) return

            if (StreakScheduler.runNow(context)) {
                repo.markBatteryAnticipationUsedToday()
                Log.d("BatteryLowReceiver", "Pin yếu — gửi streak sớm trước khi sập nguồn")
            }
        }
    }
}
