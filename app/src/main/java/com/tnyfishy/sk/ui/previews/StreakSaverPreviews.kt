package com.tnyfishy.sk.ui.previews

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.tnyfishy.sk.data.AppSettingsSnapshot
import com.tnyfishy.sk.data.FriendConfig
import com.tnyfishy.sk.data.FriendMessageResult
import com.tnyfishy.sk.data.LiveRunStatus
import com.tnyfishy.sk.data.StreakRunResult
import com.tnyfishy.sk.engine.ChatScanState
import com.tnyfishy.sk.ui.screens.DashboardScreen
import com.tnyfishy.sk.ui.screens.FriendsScreen
import com.tnyfishy.sk.ui.screens.HistoryScreen
import com.tnyfishy.sk.ui.screens.SettingsScreen
import com.tnyfishy.sk.ui.theme.StreakSaverTheme

private fun previewSnapshot(): AppSettingsSnapshot {
    val now = System.currentTimeMillis()
    val friends = listOf(
        FriendConfig(
            id = "f1",
            username = "minhanh_99",
            displayName = "Minh Anh",
            streakDays = 142,
            streakLitToday = true,
            isEnabled = true,
            lastMessageSentMillis = now - 3_600_000L
        ),
        FriendConfig(
            id = "f2",
            username = "khang_dev",
            displayName = "Khang",
            streakDays = 28,
            streakLitToday = false,
            isEnabled = true
        ),
        FriendConfig(
            id = "g1",
            username = "",
            displayName = "Hội cú đêm",
            streakDays = 64,
            streakLitToday = true,
            isEnabled = true,
            isGroup = true,
            lastMessageSentMillis = now - 1_800_000L
        )
    )
    val runs = listOf(
        StreakRunResult(
            id = "r1",
            runTimeMillis = now - 3_600_000L,
            durationMillis = 12_400L,
            success = true,
            friendResults = listOf(
                FriendMessageResult("f1", "minhanh_99", false, true, true),
                FriendMessageResult("g1", "Hội cú đêm", true, true, false)
            )
        )
    )
    return AppSettingsSnapshot(
        friends = friends,
        runHistory = runs,
        isScheduled = true,
        isSessionValid = true,
        displayName = "người ngoài hành lang",
        lastRunMillis = now - 3_600_000L,
        sessionLastCheckMillis = now - 120_000L
    )
}

@Preview(name = "Dashboard Light", showBackground = true)
@Preview(name = "Dashboard Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Dashboard 1.5x Font", fontScale = 1.5f, showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    val sample = previewSnapshot()
    StreakSaverTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            DashboardScreen(
                snapshot = sample,
                liveRun = LiveRunStatus(),
                nextRunMillis = System.currentTimeMillis() + 7_200_000L,
                retryableFailedIds = emptyList(),
                missingNotificationPermission = false,
                missingExactAlarmPermission = false,
                missingBatteryExemption = false,
                onOpenLogin = {},
                onRunNow = {},
                onRetryFailed = {},
                onStopRun = {},
                onMessageChanged = {},
                onDisableRandomize = {},
                onRequestNotification = {},
                onRequestExactAlarm = {},
                onRequestBatteryExemption = {}
            )
        }
    }
}

@Preview(name = "Friends Light", showBackground = true)
@Preview(name = "Friends Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun FriendsScreenPreview() {
    val sample = previewSnapshot()
    StreakSaverTheme(dynamicColor = false) {
        FriendsScreen(
            friends = sample.friends,
            isRunning = false,
            isSessionValid = true,
            scanState = ChatScanState(),
            onOpenLogin = {},
            onStartScanChats = {},
            onCancelScanChats = {},
            onAddScannedChats = {},
            onAddFriend = { _, _ -> Result.failure(IllegalStateException()) },
            onAddGroup = { Result.failure(IllegalStateException()) },
            onUpdateFriend = {},
            onDeleteFriend = {},
            onSetAllEnabled = { _, _ -> },
            onClearAllByType = {},
            onImportJson = {},
            onExportJson = {}
        )
    }
}

@Preview(name = "History Light", showBackground = true)
@Preview(name = "History Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun HistoryScreenPreview() {
    val sample = previewSnapshot()
    StreakSaverTheme(dynamicColor = false) {
        HistoryScreen(
            history = sample.runHistory,
            hasLogs = true,
            onExportLogs = {},
            onClearHistory = {}
        )
    }
}

@Preview(name = "Settings Light", showBackground = true)
@Preview(name = "Settings Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    val sample = previewSnapshot()
    StreakSaverTheme(dynamicColor = false) {
        SettingsScreen(
            snapshot = sample,
            onDisplayNameChanged = {},
            onOpenLogin = {},
            onLogout = {},
            onScheduleToggled = {},
            onUseFixedTimeToggled = {},
            onFixedTimeChanged = { _, _ -> },
            onIntervalHoursChanged = {},
            onRandomizeMessagesToggled = {},
            onSkipUnreachableToggled = {},
            onSkipIfStreakLitTodayToggled = {},
            onSendOnBatteryLowToggled = {},
            onChatPriorityChanged = {},
            onDynamicColorToggled = {},
            onOpenHyperOsAutostart = {},
            onOpenHyperOsBattery = {}
        )
    }
}
