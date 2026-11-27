package com.tnyfishy.sk.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object FormatUtils {
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val dateTimeFmt = DateTimeFormatter.ofPattern("dd/MM, HH:mm", Locale.getDefault())
    private val dayMonthFmt = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.forLanguageTag("vi-VN"))

    fun isToday(millis: Long?): Boolean {
        if (millis == null) return false
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() == LocalDate.now(zone)
    }

    fun formatDateTime(millis: Long?): String {
        if (millis == null) return "Chưa có"
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateTimeFmt)
    }

    fun formatRelativePast(millis: Long?): String {
        if (millis == null) return "Chưa từng chạy"
        val diffSec = ((System.currentTimeMillis() - millis) / 1000L).coerceAtLeast(0L)
        return when {
            diffSec < 60 -> "Vừa xong"
            diffSec < 3600 -> "${diffSec / 60} phút trước"
            diffSec < 86400 -> "${diffSec / 3600} giờ trước"
            else -> formatDateTime(millis)
        }
    }

    fun formatNextRun(nextMillis: Long): String {
        val now = System.currentTimeMillis()
        val diffSec = (nextMillis - now) / 1000L
        if (diffSec < 30) return "Ngay bây giờ"
        if (diffSec < 60) return "Dưới 1 phút nữa"
        if (diffSec < 3600) return "Trong ${diffSec / 60} phút"

        val zone = ZoneId.systemDefault()
        val dt = Instant.ofEpochMilli(nextMillis).atZone(zone)
        val today = LocalDate.now(zone)
        val time = dt.format(timeFmt)
        return when (dt.toLocalDate()) {
            today -> "Hôm nay lúc $time"
            today.plusDays(1) -> "Ngày mai lúc $time"
            else -> "${dt.format(dayMonthFmt)} lúc $time"
        }
    }

    fun formatDuration(durationMillis: Long?): String {
        if (durationMillis == null) return "—"
        val totalSec = durationMillis / 1000L
        return when {
            totalSec < 1 -> "< 1 giây"
            totalSec < 60 -> "${totalSec} giây"
            else -> "${totalSec / 60} phút ${totalSec % 60} giây"
        }
    }

    fun formatHourMinute(hour: Int, minute: Int): String =
        String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
}
