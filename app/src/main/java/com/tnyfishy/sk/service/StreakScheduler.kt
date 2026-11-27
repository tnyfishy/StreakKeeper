package com.tnyfishy.sk.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.tnyfishy.sk.AppConstants
import com.tnyfishy.sk.data.StreakRepository
import com.tnyfishy.sk.receiver.AlarmReceiver

object StreakScheduler {
    private const val TAG = "StreakScheduler"
    private const val ALARM_REQUEST_CODE = 1001

    fun scheduleNextRun(context: Context) {
        val repo = StreakRepository.getInstance(context)
        val nextRunMillis = repo.calculateNextRunMillis()
        scheduleAt(context, nextRunMillis)
        repo.setScheduled(true)
    }

    fun scheduleRetryInOneHour(context: Context) {
        val repo = StreakRepository.getInstance(context)
        scheduleAt(context, System.currentTimeMillis() + 3_600_000L)
        repo.setScheduled(true)
    }

    fun tryScheduleRetryOrGiveUp(context: Context, reason: String): Int {
        val repo = StreakRepository.getInstance(context)
        val attempt = repo.incrementTodayRetryCount()
        return if (attempt <= AppConstants.MAX_RETRIES_PER_DAY) {
            scheduleRetryInOneHour(context)
            repo.setLastRunFailed(true, reason)
            Log.d(TAG, "Scheduled hourly retry $attempt/${AppConstants.MAX_RETRIES_PER_DAY} ($reason)")
            attempt
        } else {
            repo.setLastRunFailed(false, null)
            scheduleNextRun(context)
            Log.d(TAG, "Retry budget exhausted; reverted to normal next-run slot")
            0
        }
    }

    fun scheduleAt(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildAlarmPendingIntent(context)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }
        } catch (ex: SecurityException) {
            Log.w(TAG, "Exact alarm denied by OEM/HyperOS, falling back: ${ex.message}")
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }.onFailure {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
    }

    fun cancelSchedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarmManager?.cancel(buildAlarmPendingIntent(context))
        StreakRepository.getInstance(context).setScheduled(false)
    }

    fun runNow(context: Context): Boolean {
        if (StreakService.isRunning) return false
        val repo = StreakRepository.getInstance(context)
        if (repo.isScheduled()) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.cancel(buildAlarmPendingIntent(context))
        }
        val intent = Intent(context, StreakService::class.java)
        ContextCompat.startForegroundService(context, intent)
        return true
    }

    fun runFailedTargets(context: Context, friendIds: List<String>): Boolean {
        if (StreakService.isRunning) return false
        val cleanIds = friendIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleanIds.isEmpty()) return false

        val intent = Intent(context, StreakService::class.java).apply {
            putExtra(AppConstants.EXTRA_TARGET_FRIEND_IDS, cleanIds.joinToString(","))
        }
        ContextCompat.startForegroundService(context, intent)
        return true
    }

    fun stopService(context: Context) {
        val intent = Intent(context, StreakService::class.java).apply {
            action = AppConstants.ACTION_STOP_SERVICE
        }
        if (StreakService.isRunning) {
            ContextCompat.startForegroundService(context, intent)
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        return am?.canScheduleExactAlarms() == true
    }

    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestBatteryOptimizationExemption(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }

    fun isXiaomiOrHyperOs(): Boolean {
        val m = Build.MANUFACTURER.orEmpty().lowercase()
        val b = Build.BRAND.orEmpty().lowercase()
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ||
            b.contains("xiaomi") || b.contains("redmi") || b.contains("poco")
    }

    fun openHyperOsAutostart(context: Context): Boolean {
        val intents = listOf(
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            ),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val ok = runCatching { context.startActivity(intent); true }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    fun openHyperOsBatterySettings(context: Context): Boolean {
        val intents = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                putExtra("package_name", context.packageName)
                putExtra("package_label", "Streak Keeper")
            },
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val ok = runCatching { context.startActivity(intent); true }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    private fun buildAlarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AppConstants.ACTION_STREAK_ALARM
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
