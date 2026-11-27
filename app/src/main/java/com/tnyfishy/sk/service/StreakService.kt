package com.tnyfishy.sk.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.tnyfishy.sk.AppConstants
import com.tnyfishy.sk.MainActivity
import com.tnyfishy.sk.R
import com.tnyfishy.sk.data.ChatPriority
import com.tnyfishy.sk.data.FriendConfig
import com.tnyfishy.sk.data.FriendMessageResult
import com.tnyfishy.sk.data.StreakRepository
import com.tnyfishy.sk.data.StreakRunResult
import com.tnyfishy.sk.engine.NetworkConnectivity
import com.tnyfishy.sk.engine.TikTokWebViewHelper
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

class StreakService : Service() {

    private lateinit var mainHandler: Handler
    private lateinit var repo: StreakRepository

    private var webView: WebView? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private var friendsToProcess: MutableList<FriendConfig> = mutableListOf()
    private var currentFriendIndex = 0
    private var runStartMillis = 0L
    private var runResults: MutableList<FriendMessageResult> = mutableListOf()
    private var targetFriendIds: Set<String>? = null
    private var isTargetedRetry = false
    private var cooldownSkippedCount = 0
    private var alreadyLitSkippedCount = 0
    private val disabledLabels = mutableListOf<String>()

    private var baseScript: String = ""
    private var shuffledNormalMessages: MutableList<String>? = null
    private var normalMessageIndex = 0

    private var isCancelRequested = false
    private var automationStarted = false
    private var failureAttemptsForCurrentFriend = 0
    private var perFriendTimeoutRunnable: Runnable? = null
    private var globalTimeoutRunnable: Runnable? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate() {
        super.onCreate()
        mainHandler = Handler(Looper.getMainLooper())
        repo = StreakRepository.getInstance(this)

        createNotificationChannels(this)
        acquireLocks()
        startForegroundImmediate("Đang chuẩn bị gửi streak...")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == AppConstants.ACTION_STOP_SERVICE) {
            isCancelRequested = true
            appLog("SYSTEM", "-", "Người dùng yêu cầu dừng tác vụ")
            completeService(false, "Đã dừng bởi người dùng.")
            return START_NOT_STICKY
        }

        startForegroundImmediate("Đang chuẩn bị gửi streak...")

        synchronized(runLock) {
            if (isRunningFlag) {
                appLog("SYSTEM", "-", "Bỏ qua lệnh chạy trùng — phiên gửi đang hoạt động")
                return START_NOT_STICKY
            }
            isRunningFlag = true
        }

        val rawTargetIds = intent?.getStringExtra(AppConstants.EXTRA_TARGET_FRIEND_IDS)
        targetFriendIds = rawTargetIds?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
        isCancelRequested = false

        mainHandler.post { startWebViewAutomation() }
        return START_REDELIVER_INTENT
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Re-arm schedule if user swipes the app out of Recents (crucial on HyperOS/MIUI)
        if (repo.isScheduled()) {
            StreakScheduler.scheduleNextRun(applicationContext)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        cancelWatchdogs()
        synchronized(runLock) {
            isRunningFlag = false
        }
        repo.updateLiveStatus(isRunning = false, statusMessage = "")
        releaseLocks()
        cleanupWebView()
        super.onDestroy()
    }

    private fun startForegroundImmediate(message: String) {
        runCatching {
            val notification = buildOngoingNotification(message, -1, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            Log.e(TAG, "startForeground failed: ${it.message}")
        }
    }

    private fun acquireLocks() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "StreakKeeper::AutomationWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(15L * 60L * 1000L)
            }
        }
        runCatching {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                @Suppress("DEPRECATION")
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wm?.createWifiLock(mode, "StreakKeeper::WifiLock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseLocks() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
    }

    private fun cancelWatchdogs() {
        perFriendTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        perFriendTimeoutRunnable = null
        globalTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        globalTimeoutRunnable = null
    }

    private fun armGlobalWatchdog() {
        globalTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable {
            if (isRunningFlag && !isCancelRequested) {
                appLog("TIMEOUT", "-", "Quá thời gian tối đa phiên gửi (5 phút), tự động dừng an toàn")
                completeService(false, "Hết thời gian chờ tối đa.")
            }
        }
        globalTimeoutRunnable = r
        mainHandler.postDelayed(r, GLOBAL_TIMEOUT_MS)
    }

    private fun armPerFriendWatchdog(expectedIndex: Int, friend: FriendConfig) {
        perFriendTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable {
            if (!isRunningFlag || isCancelRequested || currentFriendIndex != expectedIndex) return@Runnable
            appLog("TIMEOUT", friend.effectiveLabel, "Quá 25 giây không phản hồi, thử chuyển tiếp")
            onMessageResult(friend.targetKey, false, "Timeout waiting for TikTok response")
        }
        perFriendTimeoutRunnable = r
        mainHandler.postDelayed(r, PER_FRIEND_TIMEOUT_MS)
    }

    private fun startWebViewAutomation() {
        try {
            automationStarted = false
            currentFriendIndex = 0
            failureAttemptsForCurrentFriend = 0
            runStartMillis = System.currentTimeMillis()
            runResults = mutableListOf()
            disabledLabels.clear()
            cooldownSkippedCount = 0
            alreadyLitSkippedCount = 0
            isTargetedRetry = targetFriendIds != null
            repo.clearLiveLogs()
            armGlobalWatchdog()

            var allEnabled = repo.getEnabledFriends()
            if (isTargetedRetry) {
                val ids = targetFriendIds!!
                allEnabled = allEnabled.filter { it.id in ids }
                appLog("SYSTEM", "-", "Chạy thử lại mục tiêu cho ${ids.size} ID; khớp ${allEnabled.size} mục")
            }

            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val skipLitToday = repo.getSkipIfStreakLitToday()
            friendsToProcess = mutableListOf()

            for (friend in allEnabled) {
                val sentToday = friend.lastMessageSentMillis?.let {
                    Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today
                } == true

                if (!isTargetedRetry && sentToday) {
                    cooldownSkippedCount++
                    appLog("SKIP", friend.effectiveLabel, "Đã gửi hôm nay rồi")
                } else if (!isTargetedRetry && skipLitToday && friend.streakLitToday && friend.streakDays > 0) {
                    alreadyLitSkippedCount++
                    appLog("SKIP-STREAK", friend.effectiveLabel, "Đã có chuỗi hôm nay (${friend.streakDays} ngày)")
                } else {
                    friendsToProcess.add(friend)
                }
            }

            applyChatPriority(friendsToProcess)

            if (repo.state.value.randomizeMessages) {
                shuffledNormalMessages = AppConstants.BUILT_IN_STREAK_MESSAGES.toMutableList().apply { shuffle() }
                normalMessageIndex = 0
                appLog("SYSTEM", "-", "Tin nhắn ngẫu nhiên (${shuffledNormalMessages?.size} mẫu)")
            } else {
                shuffledNormalMessages = null
            }

            repo.updateLiveStatus(
                isRunning = true,
                currentIndex = 0,
                totalCount = friendsToProcess.size,
                statusMessage = "Đang kết nối TikTok..."
            )

            appLog(
                "SYSTEM",
                "-",
                "Bắt đầu tốc độ cao: ${friendsToProcess.size} mục cần gửi, ${cooldownSkippedCount + alreadyLitSkippedCount} bỏ qua (đã có chuỗi hôm nay)"
            )

            if (friendsToProcess.isEmpty()) {
                val totalSkipped = cooldownSkippedCount + alreadyLitSkippedCount
                val msg = when {
                    totalSkipped > 0 -> "Tất cả $totalSkipped mục hôm nay đều đã có chuỗi!"
                    isTargetedRetry -> "Không có mục tiêu lỗi nào đang bật"
                    else -> "Chưa cấu hình bạn bè hoặc nhóm nào"
                }
                completeService(totalSkipped > 0, msg)
                return
            }

            if (!NetworkConnectivity.hasWifiOrCellularInternet(this)) {
                completeSkippedNoNetwork()
                return
            }

            updateOngoingNotification("Đang tải hộp thư TikTok...", -1, 0)
            baseScript = loadMinifiedAutomationScript()

            val loginUa = repo.getLoginUserAgent()
            val density = resources.displayMetrics.density

            val wv = WebView(this)
            webView = wv
            TikTokWebViewHelper.configureForHeadlessAutomation(wv, loginUa, density)
            wv.webViewClient = StreakWebViewClient()
            wv.addJavascriptInterface(StreakJsInterface(), "StreakApp")
            wv.loadUrl(AppConstants.MESSAGES_URL)

            mainHandler.postDelayed({
                if (isCancelRequested || !isRunningFlag) return@postDelayed
                if (!(webView?.url.orEmpty()).contains("tiktok.com/messages")) {
                    webView?.loadUrl(AppConstants.MESSAGES_URL)
                    mainHandler.postDelayed({
                        if (isCancelRequested || !isRunningFlag) return@postDelayed
                        if (!(webView?.url.orEmpty()).contains("tiktok.com/messages")) {
                            completeService(false, "Không thể mở trang tiktok.com/messages")
                        }
                    }, 5000)
                }
            }, 5000)
        } catch (ex: Exception) {
            completeService(false, "Lỗi khởi tạo WebView: ${ex.message}")
        }
    }

    private fun loadMinifiedAutomationScript(): String {
        val raw = assets.open("tiktok_automation.js").bufferedReader().use { it.readText() }
        return raw.lineSequence()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun applyChatPriority(list: MutableList<FriendConfig>) {
        val priority = repo.getChatPriority()
        if (priority == ChatPriority.MIXED) return
        val groupsFirst = priority == ChatPriority.GROUPS_FIRST
        val sorted = list.withIndex()
            .sortedWith(compareBy<IndexedValue<FriendConfig>> {
                if (it.value.isGroup == groupsFirst) 0 else 1
            }.thenBy { it.index })
            .map { it.value }
        list.clear()
        list.addAll(sorted)
        appLog("SYSTEM", "-", "Ưu tiên thứ tự gửi: ${priority.storageKey}")
    }

    private fun onPageLoaded(url: String) {
        if (isCancelRequested) return
        when {
            url.contains("tiktok.com/messages") -> {
                if (automationStarted) return
                automationStarted = true
                updateOngoingNotification("Đang gửi tốc độ cao...", 0, friendsToProcess.size)
                repo.updateLiveStatus(statusMessage = "Đã vào hộp thư TikTok...")
                appLog("NAVIGATION", "-", "Trang tin nhắn sẵn sàng")
                // Fast start: script itself polls DOM every 120ms
                mainHandler.postDelayed({ processNextFriend() }, 450)
            }
            url.contains("/login") -> {
                appLog("NAVIGATION", "-", "Phiên TikTok đã hết hạn, cần đăng nhập lại")
                repo.setSessionValid(false)
                completeService(false, "Cần đăng nhập lại TikTok trong ứng dụng.")
            }
        }
    }

    private fun processNextFriend() {
        if (isCancelRequested) return

        val skipUnreachable = repo.getSkipUnreachableUsers()
        if (!skipUnreachable && runResults.any { it.failed }) {
            val firstError = runResults.firstOrNull { it.failed }?.errorMessage ?: "Lỗi gửi tin nhắn"
            completeService(false, "Dừng do lỗi: $firstError")
            return
        }

        if (currentFriendIndex >= friendsToProcess.size) {
            val successCount = runResults.count { it.success }
            val allSucceeded = runResults.isNotEmpty() && successCount == runResults.size
            val msg = if (allSucceeded) {
                "Hoàn tất gửi streak cho tất cả mục"
            } else {
                "Hoàn tất $successCount/${runResults.size} mục"
            }
            completeService(allSucceeded, msg)
            return
        }

        val friend = friendsToProcess[currentFriendIndex]
        val logTarget = if (friend.isGroup) "Nhóm: ${friend.displayName}" else "@${friend.username}"
        appLog("PROCESS", logTarget, "Đang xử lý (${currentFriendIndex + 1}/${friendsToProcess.size})")
        sendCurrentFriendMessage()
    }

    private fun sendCurrentFriendMessage() {
        if (isCancelRequested) return
        val friend = friendsToProcess.getOrNull(currentFriendIndex) ?: return

        val message = shuffledNormalMessages?.takeIf { it.isNotEmpty() }?.let { pool ->
            val picked = pool[normalMessageIndex % pool.size]
            normalMessageIndex++
            if (normalMessageIndex >= pool.size) {
                pool.shuffle()
                normalMessageIndex = 0
            }
            picked
        } ?: repo.getMessageTextForSend()

        val displayLabel = if (friend.isGroup) friend.displayName else "@${friend.username}"
        val status = "${currentFriendIndex + 1}/${friendsToProcess.size} — $displayLabel"
        updateOngoingNotification(status, currentFriendIndex, friendsToProcess.size)
        repo.updateLiveStatus(
            currentIndex = currentFriendIndex,
            totalCount = friendsToProcess.size,
            statusMessage = status
        )

        val target = friend.targetKey.trim()
        if (target.isEmpty()) {
            val error = if (friend.isGroup) "Tên nhóm đang trống" else "Username đang trống"
            val updated = friend.copy(
                failureCount = friend.failureCount + 1,
                consecutiveFailures = friend.consecutiveFailures + 1
            )
            repo.updateFriend(updated)
            runResults.add(
                FriendMessageResult(
                    friendId = friend.id,
                    username = target,
                    isGroup = friend.isGroup,
                    success = false,
                    errorMessage = error
                )
            )
            appLog("FAIL", "-", error)
            currentFriendIndex++
            mainHandler.postDelayed({ processNextFriend() }, 300)
            return
        }

        armPerFriendWatchdog(currentFriendIndex, friend)
        val skipLit = repo.getSkipIfStreakLitToday() && !isTargetedRetry
        val js = buildInjectedScript(target, message, friend.isGroup, skipLit)
        webView?.evaluateJavascript(js, null)
    }

    private fun buildInjectedScript(
        target: String,
        message: String,
        isGroup: Boolean,
        skipIfLitToday: Boolean
    ): String {
        val escapedTarget = target
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
        val escapedMessage = message
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\r", "")
            .replace("\n", "\\n")

        return baseScript
            .replace("[UserName]", escapedTarget)
            .replace("[Message]", escapedMessage)
            .replace("[IsGroup]", if (isGroup) "true" else "false")
            .replace("[SkipIfLitToday]", if (skipIfLitToday) "true" else "false")
    }

    private fun onFriendMetaUpdate(username: String, avatarUrl: String, streakDays: Int, litToday: Boolean) {
        val idx = friendsToProcess.indexOfFirst {
            (it.isGroup && it.displayName.equals(username, ignoreCase = true)) ||
                (!it.isGroup && it.username.equals(username, ignoreCase = true))
        }
        if (idx < 0) return
        val cur = friendsToProcess[idx]
        val next = cur.copy(
            avatarUrl = avatarUrl.ifBlank { cur.avatarUrl },
            streakDays = if (streakDays > 0) streakDays else cur.streakDays,
            streakLitToday = litToday || cur.streakLitToday
        )
        if (next != cur) {
            friendsToProcess[idx] = next
            repo.updateFriend(next)
        }
    }

    private fun onMessageResult(username: String, success: Boolean, errorOrNote: String) {
        if (isCancelRequested) return
        perFriendTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        perFriendTimeoutRunnable = null

        val friend = friendsToProcess.firstOrNull {
            (it.isGroup && it.displayName.equals(username, ignoreCase = true)) ||
                (!it.isGroup && it.username.equals(username, ignoreCase = true))
        }

        if (friend == null) {
            appLog("WARN", username, "Callback JS không khớp mục, thử lại...")
            failureAttemptsForCurrentFriend++
            if (failureAttemptsForCurrentFriend < MAX_SEND_ATTEMPTS_PER_FRIEND) {
                mainHandler.postDelayed({ sendCurrentFriendMessage() }, 1000)
            } else {
                val currentFriend = friendsToProcess.getOrNull(currentFriendIndex)
                if (currentFriend != null) {
                    val err = "Quá số lần thử do sai lệch mục tiêu callback"
                    repo.updateFriend(
                        currentFriend.copy(
                            failureCount = currentFriend.failureCount + 1,
                            consecutiveFailures = currentFriend.consecutiveFailures + 1
                        )
                    )
                    runResults.add(
                        FriendMessageResult(
                            friendId = currentFriend.id,
                            username = currentFriend.targetKey,
                            isGroup = currentFriend.isGroup,
                            success = false,
                            errorMessage = err
                        )
                    )
                    appLog("FAIL", currentFriend.effectiveLabel, err)
                }
                failureAttemptsForCurrentFriend = 0
                currentFriendIndex++
                mainHandler.postDelayed({ processNextFriend() }, 400)
            }
            return
        }

        val label = if (friend.isGroup) "Nhóm: ${friend.displayName}" else "@${friend.username}"

        if (!success) {
            failureAttemptsForCurrentFriend++
            if (failureAttemptsForCurrentFriend < MAX_SEND_ATTEMPTS_PER_FRIEND) {
                appLog("RETRY", label, "Thử lần $failureAttemptsForCurrentFriend/$MAX_SEND_ATTEMPTS_PER_FRIEND: $errorOrNote")
                mainHandler.postDelayed({ sendCurrentFriendMessage() }, 1000)
                return
            }

            val newFailCount = friend.failureCount + 1
            val skipUnreachable = repo.getSkipUnreachableUsers()
            var shouldDisable = false
            if (skipUnreachable && errorOrNote == AppConstants.USER_NOT_FOUND_ERROR && newFailCount >= 3) {
                shouldDisable = true
                disabledLabels.add(label)
                appLog("DISABLED", label, "Tự tắt sau 3 lần không tìm thấy trong danh sách chat")
            }

            val updatedFriend = friend.copy(
                failureCount = newFailCount,
                consecutiveFailures = friend.consecutiveFailures + 1,
                isEnabled = if (shouldDisable) false else friend.isEnabled
            )
            friendsToProcess[currentFriendIndex] = updatedFriend
            repo.updateFriend(updatedFriend)

            runResults.add(
                FriendMessageResult(
                    friendId = friend.id,
                    username = username,
                    isGroup = friend.isGroup,
                    success = false,
                    errorMessage = errorOrNote
                )
            )
            appLog("FAIL", label, errorOrNote)

            failureAttemptsForCurrentFriend = 0
            currentFriendIndex++
            updateOngoingNotification(
                "$currentFriendIndex/${friendsToProcess.size} — Lỗi: $label",
                currentFriendIndex,
                friendsToProcess.size
            )
            mainHandler.postDelayed({ processNextFriend() }, 450)
            return
        }

        val skippedAlreadyLit = errorOrNote == AppConstants.ALREADY_STREAK_TODAY_TOKEN
        val now = System.currentTimeMillis()
        val updatedFriend = friend.copy(
            successCount = if (skippedAlreadyLit) friend.successCount else friend.successCount + 1,
            consecutiveFailures = 0,
            lastMessageSentMillis = now,
            streakLitToday = true
        )
        friendsToProcess[currentFriendIndex] = updatedFriend
        repo.updateFriend(updatedFriend)

        if (skippedAlreadyLit) {
            alreadyLitSkippedCount++
            appLog("SKIP-STREAK", label, "Bỏ qua gửi — hôm nay đã có chuỗi trên TikTok")
        } else {
            appLog("SUCCESS", label, "Đã gửi tin nhắn thành công")
        }

        runResults.add(
            FriendMessageResult(
                friendId = friend.id,
                username = username,
                isGroup = friend.isGroup,
                success = true,
                skippedAlreadyLit = skippedAlreadyLit,
                errorMessage = null,
                timestampMillis = now
            )
        )

        currentFriendIndex++
        failureAttemptsForCurrentFriend = 0
        val completed = currentFriendIndex
        val total = friendsToProcess.size
        val status = if (skippedAlreadyLit) {
            "$completed/$total — Đã có chuỗi: $label"
        } else {
            "$completed/$total — Đã gửi: $label"
        }
        updateOngoingNotification(status, completed, total)
        repo.updateLiveStatus(currentIndex = completed, totalCount = total, statusMessage = status)

        // Ultra-fast transition between friends (400ms instead of 3000ms!)
        val nextDelay = if (currentFriendIndex < total) 400L else 250L
        mainHandler.postDelayed({ processNextFriend() }, nextDelay)
    }

    private fun completeSkippedNoNetwork() {
        try {
            val duration = (System.currentTimeMillis() - runStartMillis).coerceAtLeast(0L)
            repo.addRunResult(
                StreakRunResult(
                    runTimeMillis = runStartMillis,
                    durationMillis = duration,
                    success = false,
                    isTargetedRetry = isTargetedRetry,
                    errorMessage = "Bỏ qua: không có Wi-Fi hoặc dữ liệu di động",
                    friendResults = emptyList()
                )
            )

            val attempt = StreakScheduler.tryScheduleRetryOrGiveUp(
                this, AppConstants.FAILURE_REASON_NO_NETWORK
            )
            val max = AppConstants.MAX_RETRIES_PER_DAY
            val (title, body) = if (attempt > 0) {
                "Streak Keeper — Ngoại tuyến" to
                    "Không có kết nối mạng. Sẽ tự thử lại sau 1 giờ (lần $attempt/$max)."
            } else {
                "Streak Keeper — Hết lượt thử hôm nay" to
                    "Không có mạng sau $max lần thử. Sẽ chạy lại vào lịch kế tiếp."
            }
            notifyFinalStatus(title, body)
        } finally {
            finishAndStop()
        }
    }

    private fun completeService(success: Boolean, message: String) {
        cancelWatchdogs()
        try {
            val now = System.currentTimeMillis()
            val duration = (now - runStartMillis).coerceAtLeast(0L)
            repo.addRunResult(
                StreakRunResult(
                    runTimeMillis = if (runStartMillis > 0L) runStartMillis else now,
                    durationMillis = duration,
                    success = success,
                    isTargetedRetry = isTargetedRetry,
                    errorMessage = if (success) null else message,
                    friendResults = runResults.toList()
                )
            )
            repo.setLastRunMillis(now)

            val sentDirectly = runResults.count { it.success && !it.skippedAlreadyLit }
            val skippedStreak = runResults.count { it.skippedAlreadyLit } + alreadyLitSkippedCount
            val failedResults = runResults.filter { !it.success }
            val parts = mutableListOf<String>()
            if (sentDirectly > 0) parts.add("$sentDirectly đã gửi")
            if (skippedStreak > 0) parts.add("$skippedStreak đã có chuỗi hôm nay")
            if (failedResults.isNotEmpty()) {
                val names = failedResults.map {
                    if (it.isGroup) it.username else "@${it.username}"
                }.distinct()
                val shown = names.take(4).joinToString(", ")
                val extra = (names.size - 4).coerceAtLeast(0)
                val suffix = if (extra > 0) " (+$extra)" else ""
                parts.add("${failedResults.size} lỗi ($shown$suffix)")
            }
            if (disabledLabels.isNotEmpty()) parts.add("${disabledLabels.size} tự tắt")
            if (cooldownSkippedCount > 0) parts.add("$cooldownSkippedCount đã gửi trước đó")

            val finalText = if (parts.isNotEmpty()) {
                "Hoàn tất: ${parts.joinToString(" • ")}"
            } else {
                message
            }
            notifyFinalStatus("Streak Keeper", finalText)

            if (repo.isScheduled()) {
                val allOk = success && (runResults.isEmpty() || runResults.all { it.success })
                if (allOk) {
                    repo.resetTodayRetryCount()
                    repo.setLastRunFailed(false, null)
                    StreakScheduler.scheduleNextRun(this)
                } else if (!isCancelRequested) {
                    val attempt = StreakScheduler.tryScheduleRetryOrGiveUp(
                        this, AppConstants.FAILURE_REASON_SEND_ERROR
                    )
                    if (attempt > 0) {
                        appLog("SYSTEM", "-", "Đặt lịch thử lại sau 1 giờ ($attempt/${AppConstants.MAX_RETRIES_PER_DAY})")
                    } else {
                        appLog("SYSTEM", "-", "Hết lượt thử trong ngày, quay lại lịch chính")
                    }
                }
            }

            appLog("SYSTEM", "-", "Kết thúc (${duration / 1000}s): ${if (success) "Thành công" else message}")
        } finally {
            finishAndStop()
        }
    }

    private fun finishAndStop() {
        cancelWatchdogs()
        synchronized(runLock) {
            isRunningFlag = false
        }
        repo.updateLiveStatus(isRunning = false, statusMessage = "")
        cleanupWebView()
        releaseLocks()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanupWebView() {
        mainHandler.post {
            TikTokWebViewHelper.tearDown(webView)
            webView = null
        }
    }

    private fun appLog(phase: String, target: String, message: String) {
        val ts = timeFormat.format(Date())
        val line = "[$ts] [$phase] [$target] $message"
        repo.appendLiveLog(line)
        Log.d(TAG, line)
    }

    private fun buildOngoingNotification(message: String, progress: Int, max: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, StreakService::class.java).apply { action = AppConstants.ACTION_STOP_SERVICE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_SERVICE_ID)
            .setContentTitle("Streak Keeper")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Dừng", stopIntent)
            .apply {
                if (progress >= 0 && max > 0) setProgress(max, progress, false)
                else setProgress(0, 0, true)
            }
            .build()
    }

    private fun updateOngoingNotification(message: String, progress: Int, max: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID, buildOngoingNotification(message, progress, max))
    }

    private fun notifyFinalStatus(title: String, body: String) {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_STATUS_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID + 1, notification)
    }

    private inner class StreakWebViewClient : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            if (!url.isNullOrBlank()) {
                onPageLoaded(url)
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val uri = request?.url ?: return false
            val scheme = uri.scheme.orEmpty().lowercase()
            if (scheme != "http" && scheme != "https") return true
            if ((uri.encodedSchemeSpecificPart ?: "").startsWith("//aweme")) return true
            return false
        }
    }

    inner class StreakJsInterface {
        @JavascriptInterface
        fun onMessageSent(username: String, success: Boolean, error: String) {
            mainHandler.post { onMessageResult(username, success, error) }
        }

        @JavascriptInterface
        fun onFriendMeta(username: String, avatarUrl: String, streakDays: Int, litToday: Boolean) {
            mainHandler.post { onFriendMetaUpdate(username, avatarUrl, streakDays, litToday) }
        }

        @JavascriptInterface
        fun log(message: String) {
            val ts = timeFormat.format(Date())
            repo.appendLiveLog("[$ts] $message")
            Log.d("StreakJS", message)
        }
    }

    companion object {
        private const val TAG = "StreakService"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_SEND_ATTEMPTS_PER_FRIEND = 3
        private const val PER_FRIEND_TIMEOUT_MS = 25_000L
        private const val GLOBAL_TIMEOUT_MS = 5L * 60L * 1000L

        const val CHANNEL_SERVICE_ID = "streak_service_channel"
        const val CHANNEL_STATUS_ID = "streak_status_channel"

        private val runLock = Any()

        @Volatile
        private var isRunningFlag = false

        val isRunning: Boolean
            get() = isRunningFlag

        fun createNotificationChannels(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (nm.getNotificationChannel(CHANNEL_SERVICE_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_SERVICE_ID,
                        "Tiến trình gửi Streak",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Hiển thị trạng thái khi đang tự động gửi tin nhắn TikTok"
                        setShowBadge(false)
                    }
                )
            }
            if (nm.getNotificationChannel(CHANNEL_STATUS_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_STATUS_ID,
                        "Kết quả & cảnh báo Streak",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Thông báo kết quả gửi hoặc mất kết nối mạng"
                    }
                )
            }
        }
    }
}
