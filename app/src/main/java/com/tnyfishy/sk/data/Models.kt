package com.tnyfishy.sk.data

import java.util.UUID

enum class ChatPriority(val storageKey: String) {
    MIXED("mixed"),
    FRIENDS_FIRST("friends_first"),
    GROUPS_FIRST("groups_first");

    companion object {
        fun fromKey(key: String?): ChatPriority =
            entries.firstOrNull { it.storageKey == key } ?: MIXED
    }
}

data class FriendConfig(
    val id: String = UUID.randomUUID().toString(),
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val streakDays: Int = 0,
    val streakLitToday: Boolean = false,
    val isEnabled: Boolean = true,
    val lastMessageSentMillis: Long? = null,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val consecutiveFailures: Int = 0,
    val isGroup: Boolean = false
) {
    val effectiveLabel: String
        get() = when {
            isGroup -> displayName.ifBlank { username }
            displayName.isNotBlank() -> displayName
            else -> username
        }

    val targetKey: String
        get() = if (isGroup) displayName else username
}

data class FriendMessageResult(
    val friendId: String = "",
    val username: String = "",
    val isGroup: Boolean = false,
    val success: Boolean = false,
    val skippedAlreadyLit: Boolean = false,
    val errorMessage: String? = null,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    val failed: Boolean
        get() = !success && !errorMessage.isNullOrBlank()
}

data class StreakRunResult(
    val id: String = UUID.randomUUID().toString(),
    val runTimeMillis: Long = System.currentTimeMillis(),
    val durationMillis: Long? = null,
    val success: Boolean = false,
    val isTargetedRetry: Boolean = false,
    val errorMessage: String? = null,
    val friendResults: List<FriendMessageResult> = emptyList()
) {
    val friendsErrorMessage: String
        get() = friendResults.filter { it.failed }.mapNotNull { it.errorMessage }.joinToString(", ")

    val failed: Boolean
        get() = !success && (friendsErrorMessage.isNotBlank() || !errorMessage.isNullOrBlank())
}

data class LiveRunStatus(
    val isRunning: Boolean = false,
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val statusMessage: String = "",
    val logs: List<String> = emptyList()
)

data class AppSettingsSnapshot(
    val friends: List<FriendConfig> = emptyList(),
    val runHistory: List<StreakRunResult> = emptyList(),
    val messageText: String = com.tnyfishy.sk.AppConstants.DEFAULT_MESSAGE,
    val randomizeMessages: Boolean = false,
    val isScheduled: Boolean = false,
    val useFixedTime: Boolean = false,
    val fixedTimeHour: Int = 9,
    val fixedTimeMinute: Int = 0,
    val intervalHours: Int = com.tnyfishy.sk.AppConstants.DEFAULT_INTERVAL_HOURS,
    val skipUnreachableUsers: Boolean = true,
    val skipIfStreakLitToday: Boolean = true,
    val chatPriority: ChatPriority = ChatPriority.MIXED,
    val sendOnBatteryLow: Boolean = true,
    val lastRunMillis: Long? = null,
    val isSessionValid: Boolean = false,
    val sessionLastCheckMillis: Long? = null,
    val displayName: String = "bạn",
    val userAvatarUrl: String = "",
    val todayRetryCount: Int = 0,
    val lastRunFailed: Boolean = false,
    val lastRunFailureReason: String? = null,
    val useDynamicColor: Boolean = true
)

data class ImportSummary(
    val added: Int,
    val updated: Int,
    val skipped: Int
)
