package com.tnyfishy.sk

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.tnyfishy.sk.data.StreakRepository
import com.tnyfishy.sk.engine.TikTokAccountFetcher
import com.tnyfishy.sk.engine.TikTokChatScanner
import com.tnyfishy.sk.service.StreakScheduler
import com.tnyfishy.sk.ui.TikTokLoginActivity
import com.tnyfishy.sk.ui.screens.DashboardScreen
import com.tnyfishy.sk.ui.screens.FriendsScreen
import com.tnyfishy.sk.ui.screens.HistoryScreen
import com.tnyfishy.sk.ui.screens.SettingsScreen
import com.tnyfishy.sk.ui.theme.StreakSaverTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val windowFocusSignal = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            val repo = remember { StreakRepository.getInstance(applicationContext) }
            val snapshot by repo.state.collectAsStateWithLifecycle()

            StreakSaverTheme(dynamicColor = snapshot.useDynamicColor) {
                StreakSaverRoot(
                    activity = this,
                    repo = repo,
                    windowFocusSignal = windowFocusSignal
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            windowFocusSignal.update { it + 1 }
        }
    }
}

private enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("Tổng quan", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    FRIENDS("Danh sách", Icons.Rounded.Group, Icons.Outlined.Group),
    HISTORY("Lịch sử", Icons.Rounded.History, Icons.Outlined.History),
    SETTINGS("Cài đặt", Icons.Rounded.Tune, Icons.Outlined.Tune)
}

@Composable
private fun StreakSaverRoot(
    activity: Activity,
    repo: StreakRepository,
    windowFocusSignal: MutableStateFlow<Int>
) {
    val context = LocalContext.current
    val snapshot by repo.state.collectAsStateWithLifecycle()
    val liveRun by repo.liveRun.collectAsStateWithLifecycle()
    val scanState by TikTokChatScanner.scanState.collectAsStateWithLifecycle()
    val focusTick by windowFocusSignal.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val motion = MaterialTheme.motionScheme

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showNotifRationale by remember { mutableStateOf(false) }

    var hasNotifPerm by remember { mutableStateOf(checkNotificationPermission(context)) }
    var hasExactAlarmPerm by remember { mutableStateOf(StreakScheduler.canScheduleExactAlarms(context)) }
    var hasBatteryExempt by remember { mutableStateOf(StreakScheduler.isIgnoringBatteryOptimizations(context)) }
    var clockTick by remember { mutableIntStateOf(0) }

    // Adaptive breakpoint: WideNavigationRail at >= 600dp, ShortNavigationBar on compact phones
    val density = LocalDensity.current
    val windowWidthDp = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val useWideRail = windowWidthDp >= 600.dp
    val railState = rememberWideNavigationRailState(WideNavigationRailValue.Collapsed)

    // Predictive Back (NavigationEvent SDK 36+): return to Dashboard tab before exiting app
    val tabBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = tabBackState,
        isBackEnabled = selectedTabIndex != 0,
        onBackCompleted = { selectedTabIndex = 0 }
    )

    fun refreshPermissionsNow(): Boolean {
        val notif = checkNotificationPermission(context)
        val alarm = StreakScheduler.canScheduleExactAlarms(context)
        val battery = StreakScheduler.isIgnoringBatteryOptimizations(context)
        hasNotifPerm = notif
        hasExactAlarmPerm = alarm
        hasBatteryExempt = battery
        return notif && alarm && battery
    }

    fun refreshPermissionsWithDelayedFollowUp() {
        val allGranted = refreshPermissionsNow()
        if (!allGranted) {
            scope.launch {
                delay(250)
                if (!refreshPermissionsNow()) {
                    delay(450)
                    refreshPermissionsNow()
                }
            }
        }
    }

    fun refreshSessionAndNicknameAsync(forceProfileSync: Boolean = false) {
        scope.launch {
            val valid = withContext(Dispatchers.IO) {
                repo.refreshSessionFromCookies()
            }
            if (valid && (forceProfileSync || repo.getTikTokNickname().isNullOrBlank() || repo.state.value.userAvatarUrl.isBlank())) {
                TikTokAccountFetcher.fetchAndStoreDisplayName(context)
            }
        }
    }

    fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    val loginLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        refreshPermissionsWithDelayedFollowUp()
        refreshSessionAndNicknameAsync()
        if (result.resultCode == Activity.RESULT_OK) {
            val autoEnabled = result.data?.getBooleanExtra(
                TikTokLoginActivity.EXTRA_AUTO_SCHEDULE_ENABLED, false
            ) == true
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (autoEnabled) "Đăng nhập thành công! Đã tự động bật lịch chạy ngầm."
                    else "Đăng nhập TikTok thành công!"
                )
            }
        }
    }

    fun openLoginActivity() {
        loginLauncher.launch(Intent(context, TikTokLoginActivity::class.java))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionsWithDelayedFollowUp()
                refreshSessionAndNicknameAsync()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(focusTick) {
        if (focusTick > 0) {
            refreshPermissionsWithDelayedFollowUp()
        }
    }

    LaunchedEffect(Unit) {
        refreshPermissionsNow()
        launch { refreshSessionAndNicknameAsync(forceProfileSync = true) }
        while (isActive) {
            delay(30_000L)
            clockTick++
        }
    }

    // 3-state runtime permission flow per android-permissions-security
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted
        refreshPermissionsWithDelayedFollowUp()
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val canAskAgain = ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            )
            scope.launch {
                if (!canAskAgain) {
                    val action = snackbarHostState.showSnackbar(
                        message = "Quyền thông báo đang bị tắt vĩnh viễn trong hệ thống.",
                        actionLabel = "Mở cài đặt",
                        duration = SnackbarDuration.Long
                    )
                    if (action == SnackbarResult.ActionPerformed) {
                        openAppDetailsSettings()
                    }
                } else {
                    snackbarHostState.showSnackbar("Cần quyền thông báo để hiển thị tiến trình chạy ngầm.")
                }
            }
        }
    }

    fun requestNotifPermissionSafely() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        when {
            checkNotificationPermission(context) -> {
                hasNotifPerm = true
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) -> {
                showNotifRationale = true
            }
            else -> {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val content = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                    repo.importFriendsJson(content).getOrThrow()
                }
            }
            result.onSuccess { summary ->
                snackbarHostState.showSnackbar(
                    "Đã nhập: +${summary.added} mới, ${summary.updated} cập nhật, ${summary.skipped} bỏ qua"
                )
            }.onFailure { err ->
                snackbarHostState.showSnackbar("Lỗi nhập JSON: ${err.message}")
            }
        }
    }

    fun shareTextFile(fileName: String, content: String, mimeType: String, title: String) {
        scope.launch {
            val uriResult = withContext(Dispatchers.IO) {
                runCatching {
                    val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val file = File(exportsDir, fileName)
                    file.writeText(content)
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }
            }
            uriResult.onSuccess { uri ->
                runCatching {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, title))
                }.onFailure { err ->
                    snackbarHostState.showSnackbar("Không thể chia sẻ file: ${err.message}")
                }
            }.onFailure { err ->
                snackbarHostState.showSnackbar("Không thể xuất file: ${err.message}")
            }
        }
    }

    val nextRunMillis = remember(
        snapshot.isScheduled,
        snapshot.useFixedTime,
        snapshot.fixedTimeHour,
        snapshot.fixedTimeMinute,
        snapshot.intervalHours,
        snapshot.lastRunMillis,
        clockTick
    ) {
        repo.calculateNextRunMillis()
    }

    val retryableFailedIds = remember(snapshot.runHistory, snapshot.friends, clockTick) {
        repo.getRetryableFailedFriendIdsToday()
    }

    val tabContent: @Composable (Modifier) -> Unit = { contentModifier ->
        Box(modifier = contentModifier) {
            AnimatedContent(
                targetState = MainTab.entries[selectedTabIndex],
                transitionSpec = {
                    fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
                },
                label = "main_tabs"
            ) { tab ->
                when (tab) {
                    MainTab.DASHBOARD -> DashboardScreen(
                        snapshot = snapshot,
                        liveRun = liveRun,
                        nextRunMillis = nextRunMillis,
                        retryableFailedIds = retryableFailedIds,
                        missingNotificationPermission = !hasNotifPerm,
                        missingExactAlarmPermission = !hasExactAlarmPerm,
                        missingBatteryExemption = !hasBatteryExempt,
                        onOpenLogin = { openLoginActivity() },
                        onRunNow = {
                            if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestNotifPermissionSafely()
                            }
                            val started = StreakScheduler.runNow(context)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (started) "Đã khởi chạy gửi Streak!"
                                    else "Đang có một phiên gửi hoạt động."
                                )
                            }
                        },
                        onRetryFailed = {
                            val started = StreakScheduler.runFailedTargets(context, retryableFailedIds)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (started) "Đang thử lại ${retryableFailedIds.size} mục lỗi..."
                                    else "Không thể bắt đầu thử lại lúc này."
                                )
                            }
                        },
                        onStopRun = {
                            StreakScheduler.stopService(context)
                            scope.launch { snackbarHostState.showSnackbar("Đang dừng tác vụ...") }
                        },
                        onMessageChanged = { repo.setMessageText(it) },
                        onDisableRandomize = { repo.setRandomizeMessages(false) },
                        onRequestNotification = { requestNotifPermissionSafely() },
                        onRequestExactAlarm = { StreakScheduler.requestExactAlarmPermission(context) },
                        onRequestBatteryExemption = { StreakScheduler.requestBatteryOptimizationExemption(context) }
                    )

                    MainTab.FRIENDS -> FriendsScreen(
                        friends = snapshot.friends,
                        isRunning = liveRun.isRunning,
                        isSessionValid = snapshot.isSessionValid,
                        scanState = scanState,
                        onOpenLogin = { openLoginActivity() },
                        onStartScanChats = { TikTokChatScanner.startScan(context) },
                        onCancelScanChats = { TikTokChatScanner.cancelScan() },
                        onAddScannedChats = { chosen ->
                            val addedCount = repo.addScannedChats(chosen)
                            scope.launch {
                                snackbarHostState.showSnackbar("Đã thêm $addedCount tài khoản vào danh sách!")
                            }
                        },
                        onAddFriend = { u, d -> repo.addFriend(u, d) },
                        onAddGroup = { g -> repo.addGroup(g) },
                        onUpdateFriend = { repo.updateFriend(it) },
                        onDeleteFriend = { repo.removeFriend(it) },
                        onSetAllEnabled = { isGroup, enabled -> repo.setAllEnabled(isGroup, enabled) },
                        onClearAllByType = { isGroup -> repo.clearAllByType(isGroup) },
                        onImportJson = {
                            importJsonLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                        onExportJson = {
                            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                            shareTextFile(
                                fileName = "streak_friends_$stamp.json",
                                content = repo.exportFriendsJson(),
                                mimeType = "application/json",
                                title = "Xuất danh sách bạn bè Streak"
                            )
                        }
                    )

                    MainTab.HISTORY -> HistoryScreen(
                        history = snapshot.runHistory,
                        hasLogs = liveRun.logs.isNotEmpty(),
                        onExportLogs = {
                            val logs = repo.getLiveLogs()
                            if (logs.isEmpty()) {
                                scope.launch { snackbarHostState.showSnackbar("Chưa có log để xuất.") }
                            } else {
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                                shareTextFile(
                                    fileName = "streak_logs_$stamp.txt",
                                    content = logs.joinToString("\n"),
                                    mimeType = "text/plain",
                                    title = "Xuất log Streak Keeper"
                                )
                            }
                        },
                        onClearHistory = {
                            repo.clearRunHistory()
                            scope.launch { snackbarHostState.showSnackbar("Đã xóa lịch sử và log.") }
                        }
                    )

                    MainTab.SETTINGS -> SettingsScreen(
                        snapshot = snapshot,
                        onDisplayNameChanged = { repo.setDisplayName(it) },
                        onOpenLogin = { openLoginActivity() },
                        onLogout = {
                            repo.clearSession()
                            TikTokChatScanner.resetState()
                            scope.launch { snackbarHostState.showSnackbar("Đã đăng xuất phiên TikTok.") }
                        },
                        onScheduleToggled = { enabled ->
                            if (enabled) StreakScheduler.scheduleNextRun(context)
                            else StreakScheduler.cancelSchedule(context)
                        },
                        onUseFixedTimeToggled = { fixed ->
                            repo.setUseFixedTime(fixed)
                            if (repo.isScheduled()) StreakScheduler.scheduleNextRun(context)
                        },
                        onFixedTimeChanged = { h, m ->
                            repo.setFixedTime(h, m)
                            if (repo.isScheduled() && repo.getUseFixedTime()) {
                                StreakScheduler.scheduleNextRun(context)
                            }
                        },
                        onIntervalHoursChanged = { hours ->
                            repo.setIntervalHours(hours)
                            if (repo.isScheduled() && !repo.getUseFixedTime()) {
                                StreakScheduler.scheduleNextRun(context)
                            }
                        },
                        onRandomizeMessagesToggled = { repo.setRandomizeMessages(it) },
                        onSkipUnreachableToggled = { repo.setSkipUnreachableUsers(it) },
                        onSkipIfStreakLitTodayToggled = { repo.setSkipIfStreakLitToday(it) },
                        onSendOnBatteryLowToggled = { repo.setSendOnBatteryLow(it) },
                        onChatPriorityChanged = { repo.setChatPriority(it) },
                        onDynamicColorToggled = { repo.setUseDynamicColor(it) },
                        onOpenHyperOsAutostart = { StreakScheduler.openHyperOsAutostart(context) },
                        onOpenHyperOsBattery = { StreakScheduler.openHyperOsBatterySettings(context) }
                    )
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!useWideRail) {
                ShortNavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    MainTab.entries.forEachIndexed { index, tab ->
                        val selected = selectedTabIndex == index
                        ShortNavigationBarItem(
                            selected = selected,
                            onClick = { selectedTabIndex = index },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (useWideRail) {
            val railExpanded = railState.targetValue == WideNavigationRailValue.Expanded
            Row(modifier = Modifier.fillMaxSize()) {
                WideNavigationRail(
                    state = railState,
                    header = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (railExpanded) railState.collapse() else railState.expand()
                                }
                            },
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.padding(start = 12.dp)
                        ) {
                            Icon(
                                imageVector = if (railExpanded) Icons.AutoMirrored.Rounded.MenuOpen else Icons.Rounded.Menu,
                                contentDescription = if (railExpanded) "Thu gọn thanh điều hướng" else "Mở rộng thanh điều hướng"
                            )
                        }
                    }
                ) {
                    MainTab.entries.forEachIndexed { index, tab ->
                        val selected = selectedTabIndex == index
                        WideNavigationRailItem(
                            selected = selected,
                            onClick = { selectedTabIndex = index },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(tab.label) },
                            railExpanded = railExpanded
                        )
                    }
                }
                tabContent(Modifier.weight(1f).fillMaxSize())
            }
        } else {
            tabContent(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            )
        }
    }

    if (showNotifRationale && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AlertDialog(
            onDismissRequest = { showNotifRationale = false },
            icon = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
            title = { Text("Cần quyền thông báo") },
            text = {
                Text("Streak Keeper sử dụng dịch vụ chạy ngầm để gửi tin nhắn đúng lịch. Quyền thông báo giúp hệ thống hiển thị tiến trình và giữ dịch vụ không bị dừng đột ngột.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotifRationale = false
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text("Tiếp tục")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotifRationale = false }) { Text("Để sau") }
            }
        )
    }
}

private fun checkNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}
