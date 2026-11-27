package com.tnyfishy.sk.data

import android.content.Context
import android.content.SharedPreferences
import com.tnyfishy.sk.AppConstants
import com.tnyfishy.sk.engine.ScannedChat
import com.tnyfishy.sk.engine.TikTokWebViewHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class StreakRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val lock = Any()

    init {
        // One-time upgrade of legacy English default message to 3 flames
        val current = prefs.getString(KEY_MESSAGE_TEXT, null)
        if (current == AppConstants.LEGACY_DEFAULT_MESSAGE) {
            prefs.edit().putString(KEY_MESSAGE_TEXT, AppConstants.DEFAULT_MESSAGE).apply()
        }
    }

    private val _state = MutableStateFlow(buildSnapshot())
    val state: StateFlow<AppSettingsSnapshot> = _state.asStateFlow()

    private val _liveRun = MutableStateFlow(LiveRunStatus())
    val liveRun: StateFlow<LiveRunStatus> = _liveRun.asStateFlow()

    private fun refreshState() {
        val next = buildSnapshot()
        _state.update { next }
    }

    private fun buildSnapshot(): AppSettingsSnapshot = synchronized(lock) {
        val storedMsg = prefs.getString(KEY_MESSAGE_TEXT, null)
        AppSettingsSnapshot(
            friends = readFriendsLocked(),
            runHistory = readHistoryLocked(),
            // Preserve intentional empty string while editing; default to 3 flames only when never set
            messageText = storedMsg ?: AppConstants.DEFAULT_MESSAGE,
            randomizeMessages = prefs.getBoolean(KEY_RANDOMIZE_MESSAGES, false),
            isScheduled = prefs.getBoolean(KEY_IS_SCHEDULED, false),
            useFixedTime = prefs.getBoolean(KEY_USE_FIXED_TIME, false),
            fixedTimeHour = prefs.getInt(KEY_FIXED_HOUR, 9).coerceIn(0, 23),
            fixedTimeMinute = prefs.getInt(KEY_FIXED_MINUTE, 0).coerceIn(0, 59),
            intervalHours = prefs.getInt(KEY_INTERVAL_HOURS, AppConstants.DEFAULT_INTERVAL_HOURS)
                .coerceIn(1, 23),
            skipUnreachableUsers = prefs.getBoolean(KEY_SKIP_UNREACHABLE, true),
            skipIfStreakLitToday = prefs.getBoolean(KEY_SKIP_STREAK_LIT_TODAY, true),
            chatPriority = ChatPriority.fromKey(prefs.getString(KEY_CHAT_PRIORITY, null)),
            sendOnBatteryLow = prefs.getBoolean(KEY_SEND_ON_BATTERY_LOW, true),
            lastRunMillis = prefs.getLong(KEY_LAST_RUN, 0L).takeIf { it > 0L },
            isSessionValid = prefs.getBoolean(KEY_SESSION_VALID, false),
            sessionLastCheckMillis = prefs.getLong(KEY_SESSION_LAST_CHECK, 0L).takeIf { it > 0L },
            displayName = resolveDisplayNameLocked(),
            userAvatarUrl = prefs.getString(KEY_TIKTOK_AVATAR_URL, null).orEmpty(),
            todayRetryCount = getTodayRetryCountLocked(),
            lastRunFailed = prefs.getBoolean(KEY_LAST_RUN_FAILED, false),
            lastRunFailureReason = prefs.getString(KEY_LAST_RUN_FAILURE_REASON, null)
                ?.takeIf { it.isNotBlank() },
            useDynamicColor = prefs.getBoolean(KEY_USE_DYNAMIC_COLOR, true)
        )
    }

    // ── Live Service Status & Logs ───────────────────────────────────────────

    fun updateLiveStatus(
        isRunning: Boolean? = null,
        currentIndex: Int? = null,
        totalCount: Int? = null,
        statusMessage: String? = null
    ) {
        _liveRun.update { prev ->
            prev.copy(
                isRunning = isRunning ?: prev.isRunning,
                currentIndex = currentIndex ?: prev.currentIndex,
                totalCount = totalCount ?: prev.totalCount,
                statusMessage = statusMessage ?: prev.statusMessage
            )
        }
    }

    fun appendLiveLog(entry: String) {
        _liveRun.update { prev ->
            prev.copy(logs = (prev.logs + entry).takeLast(MAX_LIVE_LOGS))
        }
    }

    fun clearLiveLogs() {
        _liveRun.update { it.copy(logs = emptyList()) }
    }

    fun getLiveLogs(): List<String> = _liveRun.value.logs

    // ── Friends & Groups ─────────────────────────────────────────────────────

    fun getFriends(): List<FriendConfig> = synchronized(lock) { readFriendsLocked() }

    fun getEnabledFriends(): List<FriendConfig> = getFriends().filter { it.isEnabled }

    fun saveFriends(friends: List<FriendConfig>) {
        synchronized(lock) {
            writeFriendsLocked(friends)
        }
        refreshState()
    }

    fun addFriend(usernameRaw: String, displayNameRaw: String): Result<FriendConfig> {
        val username = usernameRaw.trim().removePrefix("@").trim()
        val displayName = displayNameRaw.trim()
        if (username.isBlank()) return Result.failure(IllegalArgumentException("Vui lòng nhập username TikTok"))

        synchronized(lock) {
            val current = readFriendsLocked().toMutableList()
            if (current.any { !it.isGroup && it.username.equals(username, ignoreCase = true) }) {
                return Result.failure(IllegalArgumentException("@$username đã có trong danh sách"))
            }
            val item = FriendConfig(
                username = username,
                displayName = displayName,
                isEnabled = true,
                isGroup = false
            )
            current.add(item)
            writeFriendsLocked(current)
            refreshState()
            return Result.success(item)
        }
    }

    fun addGroup(groupNameRaw: String): Result<FriendConfig> {
        val name = groupNameRaw.trim()
        if (name.isBlank()) return Result.failure(IllegalArgumentException("Vui lòng nhập tên nhóm chat"))

        synchronized(lock) {
            val current = readFriendsLocked().toMutableList()
            if (current.any { it.isGroup && it.displayName.equals(name, ignoreCase = true) }) {
                return Result.failure(IllegalArgumentException("Nhóm \"$name\" đã có trong danh sách"))
            }
            val item = FriendConfig(
                username = "",
                displayName = name,
                isEnabled = true,
                isGroup = true
            )
            current.add(item)
            writeFriendsLocked(current)
            refreshState()
            return Result.success(item)
        }
    }

    /**
     * Adds selected scanned chats AND refreshes avatarUrl/streakDays/streakLitToday for existing matches.
     */
    fun addScannedChats(chats: List<ScannedChat>): Int {
        if (chats.isEmpty()) return 0
        var added = 0
        var changed = false
        synchronized(lock) {
            val current = readFriendsLocked().toMutableList()
            for (chat in chats) {
                if (chat.isGroup) {
                    val name = chat.displayName.trim()
                    if (name.isBlank()) continue
                    val idx = current.indexOfFirst { it.isGroup && it.displayName.equals(name, ignoreCase = true) }
                    if (idx >= 0) {
                        val existing = current[idx]
                        current[idx] = existing.copy(
                            avatarUrl = chat.avatarUrl.ifBlank { existing.avatarUrl },
                            streakDays = chat.streakDays,
                            streakLitToday = chat.streakLitToday
                        )
                        changed = true
                    } else {
                        current.add(
                            FriendConfig(
                                username = "",
                                displayName = name,
                                avatarUrl = chat.avatarUrl,
                                streakDays = chat.streakDays,
                                streakLitToday = chat.streakLitToday,
                                isEnabled = true,
                                isGroup = true
                            )
                        )
                        added++
                        changed = true
                    }
                } else {
                    val user = chat.username.trim().removePrefix("@").trim()
                    if (user.isBlank()) continue
                    val idx = current.indexOfFirst { !it.isGroup && it.username.equals(user, ignoreCase = true) }
                    if (idx >= 0) {
                        val existing = current[idx]
                        current[idx] = existing.copy(
                            displayName = chat.displayName.ifBlank { existing.displayName },
                            avatarUrl = chat.avatarUrl.ifBlank { existing.avatarUrl },
                            streakDays = chat.streakDays,
                            streakLitToday = chat.streakLitToday
                        )
                        changed = true
                    } else {
                        current.add(
                            FriendConfig(
                                username = user,
                                displayName = chat.displayName.trim(),
                                avatarUrl = chat.avatarUrl,
                                streakDays = chat.streakDays,
                                streakLitToday = chat.streakLitToday,
                                isEnabled = true,
                                isGroup = false
                            )
                        )
                        added++
                        changed = true
                    }
                }
            }
            if (changed) writeFriendsLocked(current)
        }
        if (changed) refreshState()
        return added
    }

    /**
     * Syncs avatar URL & streak count discovered during chat scan into already-saved friends.
     */
    fun syncMetadataFromScan(chat: ScannedChat) {
        var changed = false
        synchronized(lock) {
            val current = readFriendsLocked().toMutableList()
            val idx = if (chat.isGroup) {
                current.indexOfFirst { it.isGroup && it.displayName.equals(chat.displayName, ignoreCase = true) }
            } else {
                current.indexOfFirst { !it.isGroup && it.username.equals(chat.username, ignoreCase = true) }
            }
            if (idx >= 0) {
                val ex = current[idx]
                val nextAvatar = chat.avatarUrl.ifBlank { ex.avatarUrl }
                val nextDays = chat.streakDays
                val nextLit = chat.streakLitToday
                val nextDisp = if (!chat.isGroup && ex.displayName.isBlank() && chat.displayName.isNotBlank()) {
                    chat.displayName
                } else ex.displayName
                if (nextAvatar != ex.avatarUrl || nextDays != ex.streakDays || nextLit != ex.streakLitToday || nextDisp != ex.displayName) {
                    current[idx] = ex.copy(
                        displayName = nextDisp,
                        avatarUrl = nextAvatar,
                        streakDays = nextDays,
                        streakLitToday = nextLit
                    )
                    writeFriendsLocked(current)
                    changed = true
                }
            }
        }
        if (changed) refreshState()
    }

    fun updateFriend(updated: FriendConfig) {
        synchronized(lock) {
            val current = readFriendsLocked().toMutableList()
            val idx = current.indexOfFirst { it.id == updated.id }
            if (idx >= 0) {
                current[idx] = updated
                writeFriendsLocked(current)
            }
        }
        refreshState()
    }

    fun removeFriend(friendId: String) {
        synchronized(lock) {
            val current = readFriendsLocked().filterNot { it.id == friendId }
            writeFriendsLocked(current)
        }
        refreshState()
    }

    fun setAllEnabled(isGroup: Boolean, enabled: Boolean) {
        synchronized(lock) {
            val updated = readFriendsLocked().map {
                if (it.isGroup == isGroup) it.copy(isEnabled = enabled) else it
            }
            writeFriendsLocked(updated)
        }
        refreshState()
    }

    fun clearAllByType(isGroup: Boolean) {
        synchronized(lock) {
            val remaining = readFriendsLocked().filter { it.isGroup != isGroup }
            writeFriendsLocked(remaining)
        }
        refreshState()
    }

    fun exportFriendsJson(): String = synchronized(lock) {
        serializeFriends(readFriendsLocked()).toString(2)
    }

    fun importFriendsJson(rawJson: String): Result<ImportSummary> {
        return runCatching {
            val arr = JSONArray(rawJson)
            var added = 0
            var updated = 0
            var skipped = 0

            synchronized(lock) {
                val existing = readFriendsLocked().toMutableList()
                val seenFriends = HashSet<String>()
                val seenGroups = HashSet<String>()

                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: run { skipped++; continue }
                    val isGroup = obj.optBooleanCompat("isGroup", "IsGroup", false)
                    val rawUsername = obj.optStringCompat("username", "Username").trim().removePrefix("@")
                    val rawDisplay = obj.optStringCompat("displayName", "DisplayName").trim()
                    val avatarUrl = obj.optStringCompat("avatarUrl", "AvatarUrl").trim()
                    val streakDays = obj.optIntCompat("streakDays", "StreakDays", 0)
                    val isEnabled = obj.optBooleanCompat("isEnabled", "IsEnabled", true)
                    val id = obj.optStringCompat("id", "Id").ifBlank { UUID.randomUUID().toString() }
                    val successCount = obj.optIntCompat("successCount", "SuccessCount", 0)
                    val failureCount = obj.optIntCompat("failureCount", "FailureCount", 0)
                    val consecutiveFailures = obj.optIntCompat("consecutiveFailures", "ConsecutiveFailures", 0)
                    val lastSent = parseTimestampCompat(obj)

                    if (isGroup) {
                        if (rawDisplay.isBlank() || !seenGroups.add(rawDisplay.lowercase())) {
                            skipped++
                            continue
                        }
                        val matchIdx = existing.indexOfFirst {
                            it.isGroup && it.displayName.equals(rawDisplay, ignoreCase = true)
                        }
                        val entry = FriendConfig(
                            id = if (matchIdx >= 0) existing[matchIdx].id else id,
                            username = "",
                            displayName = rawDisplay,
                            avatarUrl = avatarUrl,
                            streakDays = streakDays,
                            isEnabled = isEnabled,
                            lastMessageSentMillis = lastSent,
                            successCount = successCount,
                            failureCount = failureCount,
                            consecutiveFailures = consecutiveFailures,
                            isGroup = true
                        )
                        if (matchIdx >= 0) {
                            existing[matchIdx] = entry
                            updated++
                        } else {
                            existing.add(entry)
                            added++
                        }
                    } else {
                        if (rawUsername.length < 2 || !seenFriends.add(rawUsername.lowercase())) {
                            skipped++
                            continue
                        }
                        val matchIdx = existing.indexOfFirst {
                            !it.isGroup && it.username.equals(rawUsername, ignoreCase = true)
                        }
                        val entry = FriendConfig(
                            id = if (matchIdx >= 0) existing[matchIdx].id else id,
                            username = rawUsername,
                            displayName = rawDisplay,
                            avatarUrl = avatarUrl,
                            streakDays = streakDays,
                            isEnabled = isEnabled,
                            lastMessageSentMillis = lastSent,
                            successCount = successCount,
                            failureCount = failureCount,
                            consecutiveFailures = consecutiveFailures,
                            isGroup = false
                        )
                        if (matchIdx >= 0) {
                            existing[matchIdx] = entry
                            updated++
                        } else {
                            existing.add(entry)
                            added++
                        }
                    }
                }
                writeFriendsLocked(existing)
            }
            refreshState()
            ImportSummary(added, updated, skipped)
        }
    }

    // ── Settings ─────────────────────────────────────────────────────────────

    fun getMessageTextForSend(): String =
        state.value.messageText.ifBlank { AppConstants.DEFAULT_MESSAGE }

    fun setMessageText(text: String) {
        prefs.edit().putString(KEY_MESSAGE_TEXT, text).apply()
        refreshState()
    }

    fun setRandomizeMessages(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RANDOMIZE_MESSAGES, enabled).apply()
        refreshState()
    }

    fun isScheduled(): Boolean = prefs.getBoolean(KEY_IS_SCHEDULED, false)

    fun setScheduled(scheduled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_SCHEDULED, scheduled).apply()
        refreshState()
    }

    fun getUseFixedTime(): Boolean = prefs.getBoolean(KEY_USE_FIXED_TIME, false)

    fun setUseFixedTime(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_FIXED_TIME, enabled).apply()
        refreshState()
    }

    fun getFixedTimeHour(): Int = prefs.getInt(KEY_FIXED_HOUR, 9).coerceIn(0, 23)

    fun getFixedTimeMinute(): Int = prefs.getInt(KEY_FIXED_MINUTE, 0).coerceIn(0, 59)

    fun setFixedTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_FIXED_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_FIXED_MINUTE, minute.coerceIn(0, 59))
            .apply()
        refreshState()
    }

    fun getIntervalHours(): Int =
        prefs.getInt(KEY_INTERVAL_HOURS, AppConstants.DEFAULT_INTERVAL_HOURS).coerceIn(1, 23)

    fun setIntervalHours(hours: Int) {
        prefs.edit().putInt(KEY_INTERVAL_HOURS, hours.coerceIn(1, 23)).apply()
        refreshState()
    }

    fun getSkipUnreachableUsers(): Boolean = prefs.getBoolean(KEY_SKIP_UNREACHABLE, true)

    fun setSkipUnreachableUsers(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SKIP_UNREACHABLE, enabled).apply()
        refreshState()
    }

    fun getSkipIfStreakLitToday(): Boolean = prefs.getBoolean(KEY_SKIP_STREAK_LIT_TODAY, true)

    fun setSkipIfStreakLitToday(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SKIP_STREAK_LIT_TODAY, enabled).apply()
        refreshState()
    }

    fun getChatPriority(): ChatPriority =
        ChatPriority.fromKey(prefs.getString(KEY_CHAT_PRIORITY, null))

    fun setChatPriority(priority: ChatPriority) {
        prefs.edit().putString(KEY_CHAT_PRIORITY, priority.storageKey).apply()
        refreshState()
    }

    fun getSendOnBatteryLow(): Boolean = prefs.getBoolean(KEY_SEND_ON_BATTERY_LOW, true)

    fun setSendOnBatteryLow(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SEND_ON_BATTERY_LOW, enabled).apply()
        refreshState()
    }

    fun getUseDynamicColor(): Boolean = prefs.getBoolean(KEY_USE_DYNAMIC_COLOR, true)

    fun setUseDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_DYNAMIC_COLOR, enabled).apply()
        refreshState()
    }

    fun getLastRunMillis(): Long? = prefs.getLong(KEY_LAST_RUN, 0L).takeIf { it > 0L }

    fun setLastRunMillis(millis: Long) {
        prefs.edit().putLong(KEY_LAST_RUN, millis).apply()
        refreshState()
    }

    fun calculateNextRunMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val zone = ZoneId.systemDefault()
        if (getUseFixedTime()) {
            val nowDt = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDateTime()
            var candidate = nowDt.toLocalDate().atTime(getFixedTimeHour(), getFixedTimeMinute())
            if (!candidate.isAfter(nowDt)) {
                candidate = candidate.plusDays(1)
            }
            return candidate.atZone(zone).toInstant().toEpochMilli()
        }

        val intervalMs = getIntervalHours() * 3_600_000L
        val lastRun = getLastRunMillis()
        return if (lastRun != null) {
            val target = lastRun + intervalMs
            if (target <= nowMillis) nowMillis + 60_000L else target
        } else {
            nowMillis + intervalMs
        }
    }

    // ── Resilience: retries, failures, battery anticipation ──────────────────

    private fun todayKey(): String = LocalDate.now().toString()

    private fun getTodayRetryCountLocked(): Int {
        val storedDate = prefs.getString(KEY_RETRY_DATE, null)
        return if (storedDate == todayKey()) prefs.getInt(KEY_RETRY_COUNT, 0) else 0
    }

    fun getTodayRetryCount(): Int = synchronized(lock) { getTodayRetryCountLocked() }

    fun incrementTodayRetryCount(): Int {
        val next = synchronized(lock) {
            val current = getTodayRetryCountLocked()
            val updated = current + 1
            prefs.edit()
                .putString(KEY_RETRY_DATE, todayKey())
                .putInt(KEY_RETRY_COUNT, updated)
                .apply()
            updated
        }
        refreshState()
        return next
    }

    fun resetTodayRetryCount() {
        synchronized(lock) {
            prefs.edit()
                .putString(KEY_RETRY_DATE, todayKey())
                .putInt(KEY_RETRY_COUNT, 0)
                .apply()
        }
        refreshState()
    }

    fun getLastRunFailed(): Boolean = prefs.getBoolean(KEY_LAST_RUN_FAILED, false)

    fun getLastRunFailureReason(): String? =
        prefs.getString(KEY_LAST_RUN_FAILURE_REASON, null)?.takeIf { it.isNotBlank() }

    fun setLastRunFailed(failed: Boolean, reason: String?) {
        prefs.edit().apply {
            putBoolean(KEY_LAST_RUN_FAILED, failed)
            if (failed && !reason.isNullOrBlank()) {
                putString(KEY_LAST_RUN_FAILURE_REASON, reason)
            } else {
                remove(KEY_LAST_RUN_FAILURE_REASON)
            }
        }.apply()
        refreshState()
    }

    fun wasBatteryAnticipationUsedToday(): Boolean =
        prefs.getString(KEY_BATTERY_ANTICIPATION_DATE, null) == todayKey()

    fun markBatteryAnticipationUsedToday() {
        prefs.edit().putString(KEY_BATTERY_ANTICIPATION_DATE, todayKey()).apply()
    }

    // ── Session & Account Profile ────────────────────────────────────────────

    fun isSessionValid(): Boolean = prefs.getBoolean(KEY_SESSION_VALID, false)

    fun refreshSessionFromCookies(): Boolean {
        val hasCookie = TikTokWebViewHelper.hasValidSessionCookie()
        setSessionValid(hasCookie)
        return hasCookie
    }

    fun setSessionValid(valid: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SESSION_VALID, valid)
            .putLong(KEY_SESSION_LAST_CHECK, System.currentTimeMillis())
            .apply()
        refreshState()
    }

    fun getLoginUserAgent(): String =
        prefs.getString(KEY_LOGIN_UA, null)?.ifBlank { null } ?: AppConstants.DESKTOP_CHROME_UA

    fun setLoginUserAgent(ua: String) {
        prefs.edit().putString(KEY_LOGIN_UA, ua).apply()
    }

    private fun resolveDisplayNameLocked(): String {
        val custom = prefs.getString(KEY_DISPLAY_NAME, null)?.trim().orEmpty()
        val rawNick = prefs.getString(KEY_TIKTOK_NICKNAME, null)?.trim().orEmpty()
        val validNick = rawNick.takeIf {
            it.isNotBlank() &&
                !it.equals("User", ignoreCase = true) &&
                !Regex("^user\\d{6,}$", RegexOption.IGNORE_CASE).matches(it)
        }.orEmpty()
        val validCustom = custom.takeIf {
            it.isNotBlank() &&
                !it.equals("User", ignoreCase = true) &&
                !Regex("^user\\d{6,}$", RegexOption.IGNORE_CASE).matches(it)
        }.orEmpty()
        return when {
            validNick.isNotBlank() && validCustom.isBlank() -> validNick
            validCustom.isNotBlank() -> validCustom
            validNick.isNotBlank() -> validNick
            else -> "bạn"
        }
    }

    fun getTikTokNickname(): String? =
        prefs.getString(KEY_TIKTOK_NICKNAME, null)?.trim()?.takeIf {
            it.isNotBlank() && !Regex("^user\\d{6,}$", RegexOption.IGNORE_CASE).matches(it)
        }

    fun setTikTokAccountProfile(nickname: String?, avatarUrl: String?) {
        val editor = prefs.edit()
        var dirty = false
        if (!nickname.isNullOrBlank()) {
            editor.putString(KEY_TIKTOK_NICKNAME, nickname.trim())
            dirty = true
        }
        if (!avatarUrl.isNullOrBlank()) {
            editor.putString(KEY_TIKTOK_AVATAR_URL, avatarUrl.trim())
            dirty = true
        }
        if (dirty) {
            editor.apply()
            refreshState()
        }
    }

    fun setTikTokNickname(nickname: String) = setTikTokAccountProfile(nickname, null)

    fun setDisplayName(name: String) {
        val clean = name.trim()
        prefs.edit().apply {
            if (clean.isBlank()) remove(KEY_DISPLAY_NAME)
            else putString(KEY_DISPLAY_NAME, clean)
        }.apply()
        refreshState()
    }

    fun clearSession() {
        TikTokWebViewHelper.clearAllCookies()
        prefs.edit()
            .putBoolean(KEY_SESSION_VALID, false)
            .remove(KEY_SESSION_LAST_CHECK)
            .remove(KEY_TIKTOK_NICKNAME)
            .remove(KEY_TIKTOK_AVATAR_URL)
            .apply()
        refreshState()
    }

    // ── Run History ──────────────────────────────────────────────────────────

    fun getRunHistory(): List<StreakRunResult> = synchronized(lock) { readHistoryLocked() }

    fun addRunResult(result: StreakRunResult) {
        synchronized(lock) {
            val history = readHistoryLocked().toMutableList()
            history.add(0, result)
            val trimmed = history.take(MAX_HISTORY_ITEMS)
            writeHistoryLocked(trimmed)
        }
        refreshState()
    }

    fun clearRunHistory() {
        synchronized(lock) {
            prefs.edit().remove(KEY_RUN_HISTORY).apply()
        }
        clearLiveLogs()
        refreshState()
    }

    fun getRetryableFailedFriendIdsToday(): List<String> {
        val snapshot = state.value
        val enabledIds = snapshot.friends.filter { it.isEnabled }.map { it.id }.toSet()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        val latestByFriend = LinkedHashMap<String, FriendMessageResult>()
        snapshot.runHistory
            .filter {
                Instant.ofEpochMilli(it.runTimeMillis).atZone(zone).toLocalDate() == today
            }
            .sortedByDescending { it.runTimeMillis }
            .flatMap { it.friendResults }
            .forEach { res ->
                if (res.friendId.isNotBlank() && res.friendId in enabledIds) {
                    latestByFriend.putIfAbsent(res.friendId, res)
                }
            }

        return latestByFriend.values.filter { !it.success }.map { it.friendId }
    }

    // ── JSON Persistence Helpers ─────────────────────────────────────────────

    private fun readFriendsLocked(): List<FriendConfig> {
        val raw = prefs.getString(KEY_FRIENDS_LIST, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        FriendConfig(
                            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                            username = o.optString("username"),
                            displayName = o.optString("displayName"),
                            avatarUrl = o.optString("avatarUrl"),
                            streakDays = o.optInt("streakDays", 0),
                            streakLitToday = o.optBoolean("streakLitToday", false),
                            isEnabled = o.optBoolean("isEnabled", true),
                            lastMessageSentMillis = o.optLong("lastMessageSentMillis", -1L)
                                .takeIf { it > 0L },
                            successCount = o.optInt("successCount", 0),
                            failureCount = o.optInt("failureCount", 0),
                            consecutiveFailures = o.optInt("consecutiveFailures", 0),
                            isGroup = o.optBoolean("isGroup", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeFriendsLocked(friends: List<FriendConfig>) {
        prefs.edit().putString(KEY_FRIENDS_LIST, serializeFriends(friends).toString()).apply()
    }

    private fun serializeFriends(friends: List<FriendConfig>): JSONArray {
        val arr = JSONArray()
        friends.forEach { f ->
            arr.put(JSONObject().apply {
                put("id", f.id)
                put("username", f.username)
                put("displayName", f.displayName)
                if (f.avatarUrl.isNotBlank()) put("avatarUrl", f.avatarUrl)
                if (f.streakDays > 0) put("streakDays", f.streakDays)
                if (f.streakLitToday) put("streakLitToday", true)
                put("isEnabled", f.isEnabled)
                if (f.lastMessageSentMillis != null) {
                    put("lastMessageSentMillis", f.lastMessageSentMillis)
                }
                put("successCount", f.successCount)
                put("failureCount", f.failureCount)
                put("consecutiveFailures", f.consecutiveFailures)
                put("isGroup", f.isGroup)
            })
        }
        return arr
    }

    private fun readHistoryLocked(): List<StreakRunResult> {
        val raw = prefs.getString(KEY_RUN_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val frArr = o.optJSONArray("friendResults") ?: JSONArray()
                    val frList = buildList(frArr.length()) {
                        for (j in 0 until frArr.length()) {
                            val f = frArr.optJSONObject(j) ?: continue
                            add(
                                FriendMessageResult(
                                    friendId = f.optString("friendId"),
                                    username = f.optString("username"),
                                    isGroup = f.optBoolean("isGroup", false),
                                    success = f.optBoolean("success", false),
                                    skippedAlreadyLit = f.optBoolean("skippedAlreadyLit", false),
                                    errorMessage = f.optString("errorMessage").ifBlank { null },
                                    timestampMillis = f.optLong("timestampMillis", System.currentTimeMillis())
                                )
                            )
                        }
                    }
                    add(
                        StreakRunResult(
                            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                            runTimeMillis = o.optLong("runTimeMillis", System.currentTimeMillis()),
                            durationMillis = o.optLong("durationMillis", -1L).takeIf { it >= 0L },
                            success = o.optBoolean("success", false),
                            isTargetedRetry = o.optBoolean("isTargetedRetry", false),
                            errorMessage = o.optString("errorMessage").ifBlank { null },
                            friendResults = frList
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeHistoryLocked(history: List<StreakRunResult>) {
        val arr = JSONArray()
        history.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("runTimeMillis", r.runTimeMillis)
                if (r.durationMillis != null) put("durationMillis", r.durationMillis)
                put("success", r.success)
                put("isTargetedRetry", r.isTargetedRetry)
                if (r.errorMessage != null) put("errorMessage", r.errorMessage)
                put("friendResults", JSONArray().apply {
                    r.friendResults.forEach { f ->
                        put(JSONObject().apply {
                            put("friendId", f.friendId)
                            put("username", f.username)
                            put("isGroup", f.isGroup)
                            put("success", f.success)
                            if (f.skippedAlreadyLit) put("skippedAlreadyLit", true)
                            if (f.errorMessage != null) put("errorMessage", f.errorMessage)
                            put("timestampMillis", f.timestampMillis)
                        })
                    }
                })
            })
        }
        prefs.edit().putString(KEY_RUN_HISTORY, arr.toString()).apply()
    }

    private fun JSONObject.optStringCompat(k1: String, k2: String): String =
        when {
            has(k1) && !isNull(k1) -> optString(k1, "")
            has(k2) && !isNull(k2) -> optString(k2, "")
            else -> ""
        }

    private fun JSONObject.optBooleanCompat(k1: String, k2: String, default: Boolean): Boolean =
        when {
            has(k1) && !isNull(k1) -> optBoolean(k1, default)
            has(k2) && !isNull(k2) -> optBoolean(k2, default)
            else -> default
        }

    private fun JSONObject.optIntCompat(k1: String, k2: String, default: Int): Int =
        when {
            has(k1) && !isNull(k1) -> optInt(k1, default)
            has(k2) && !isNull(k2) -> optInt(k2, default)
            else -> default
        }

    private fun parseTimestampCompat(obj: JSONObject): Long? {
        if (obj.has("lastMessageSentMillis") && !obj.isNull("lastMessageSentMillis")) {
            return obj.optLong("lastMessageSentMillis", -1L).takeIf { it > 0L }
        }
        val iso = obj.optStringCompat("lastMessageSent", "LastMessageSent")
        if (iso.isBlank()) return null
        return runCatching {
            Instant.parse(iso).toEpochMilli()
        }.recoverCatching {
            LocalDateTime.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }

    companion object {
        private const val PREFS_NAME = "streak_saver_prefs"
        private const val KEY_FRIENDS_LIST = "friends_list"
        private const val KEY_RUN_HISTORY = "run_history"
        private const val KEY_MESSAGE_TEXT = "message_text"
        private const val KEY_RANDOMIZE_MESSAGES = "randomize_messages"
        private const val KEY_IS_SCHEDULED = "is_scheduled"
        private const val KEY_USE_FIXED_TIME = "use_fixed_time"
        private const val KEY_FIXED_HOUR = "fixed_hour"
        private const val KEY_FIXED_MINUTE = "fixed_minute"
        private const val KEY_INTERVAL_HOURS = "interval_hours"
        private const val KEY_SKIP_UNREACHABLE = "skip_unreachable"
        private const val KEY_SKIP_STREAK_LIT_TODAY = "skip_streak_lit_today"
        private const val KEY_CHAT_PRIORITY = "chat_priority"
        private const val KEY_SEND_ON_BATTERY_LOW = "send_on_battery_low"
        private const val KEY_LAST_RUN = "last_run"
        private const val KEY_SESSION_VALID = "session_valid"
        private const val KEY_SESSION_LAST_CHECK = "session_last_check"
        private const val KEY_LOGIN_UA = "login_ua"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_TIKTOK_NICKNAME = "tiktok_nickname"
        private const val KEY_TIKTOK_AVATAR_URL = "tiktok_avatar_url"
        private const val KEY_RETRY_COUNT = "retry_count_today"
        private const val KEY_RETRY_DATE = "retry_count_date"
        private const val KEY_LAST_RUN_FAILED = "last_run_failed"
        private const val KEY_LAST_RUN_FAILURE_REASON = "last_run_failure_reason"
        private const val KEY_BATTERY_ANTICIPATION_DATE = "battery_anticipation_date"
        private const val KEY_USE_DYNAMIC_COLOR = "use_dynamic_color"

        private const val MAX_HISTORY_ITEMS = 50
        private const val MAX_LIVE_LOGS = 400

        @Volatile
        private var instance: StreakRepository? = null

        fun getInstance(context: Context): StreakRepository =
            instance ?: synchronized(this) {
                instance ?: StreakRepository(context).also { instance = it }
            }
    }
}
