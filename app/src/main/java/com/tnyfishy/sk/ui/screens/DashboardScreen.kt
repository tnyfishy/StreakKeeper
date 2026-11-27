package com.tnyfishy.sk.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tnyfishy.sk.AppConstants
import com.tnyfishy.sk.data.AppSettingsSnapshot
import com.tnyfishy.sk.data.LiveRunStatus
import com.tnyfishy.sk.ui.FormatUtils
import com.tnyfishy.sk.ui.components.TikTokAvatar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class MissingPermissionEntry(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    snapshot: AppSettingsSnapshot,
    liveRun: LiveRunStatus,
    nextRunMillis: Long,
    retryableFailedIds: List<String>,
    missingNotificationPermission: Boolean,
    missingExactAlarmPermission: Boolean,
    missingBatteryExemption: Boolean,
    onOpenLogin: () -> Unit,
    onRunNow: () -> Unit,
    onRetryFailed: () -> Unit,
    onStopRun: () -> Unit,
    onMessageChanged: (String) -> Unit,
    onDisableRandomize: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = MaterialTheme.motionScheme
    val enabledFriends = remember(snapshot.friends) { snapshot.friends.filter { it.isEnabled } }
    val enabledIds = remember(enabledFriends) { enabledFriends.map { it.id }.toSet() }

    val todayRuns = remember(snapshot.runHistory) {
        snapshot.runHistory.filter { FormatUtils.isToday(it.runTimeMillis) }
    }
    val todayResults = remember(todayRuns, enabledIds) {
        todayRuns.flatMap { it.friendResults }.filter { it.friendId in enabledIds }
    }
    val sentTodayFromHistory = remember(todayResults) {
        todayResults.filter { it.success }.map { it.friendId }.toSet().size
    }
    val sentTodayFromFriends = remember(enabledFriends) {
        enabledFriends.count { FormatUtils.isToday(it.lastMessageSentMillis) || it.streakLitToday }
    }
    val sentToday = maxOf(sentTodayFromHistory, sentTodayFromFriends)
    val attemptedToday = remember(todayResults) { todayResults.map { it.friendId }.toSet().size }
    val successPercent = if (attemptedToday > 0) (sentTodayFromHistory * 100) / attemptedToday else null
    val remainingToday = (enabledFriends.size - sentToday).coerceAtLeast(0)
    val progressFraction = if (enabledFriends.isNotEmpty()) {
        (sentToday.toFloat() / enabledFriends.size.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val allHitToday = enabledFriends.isNotEmpty() && remainingToday == 0

    var showRunConfirmDialog by remember { mutableStateOf(false) }
    var showRetryConfirmDialog by remember { mutableStateOf(false) }
    var draftMessage by remember { mutableStateOf(snapshot.messageText) }
    SideEffect(snapshot.messageText) {
        if (snapshot.messageText != draftMessage) {
            draftMessage = snapshot.messageText
        }
    }
    val messageBringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val missingPermissions = remember(
        missingNotificationPermission,
        missingExactAlarmPermission,
        missingBatteryExemption
    ) {
        buildList {
            if (missingNotificationPermission) {
                add(
                    MissingPermissionEntry(
                        key = "notif",
                        icon = Icons.Rounded.NotificationsActive,
                        title = "Thông báo tiến trình",
                        subtitle = "Hiển thị trạng thái gửi khi chạy ngầm",
                        onClick = onRequestNotification
                    )
                )
            }
            if (missingExactAlarmPermission) {
                add(
                    MissingPermissionEntry(
                        key = "alarm",
                        icon = Icons.Rounded.Alarm,
                        title = "Báo thức chính xác",
                        subtitle = "Kích hoạt lịch gửi đúng giờ",
                        onClick = onRequestExactAlarm
                    )
                )
            }
            if (missingBatteryExemption) {
                add(
                    MissingPermissionEntry(
                        key = "battery",
                        icon = Icons.Rounded.BatteryAlert,
                        title = "Không giới hạn pin",
                        subtitle = "Tránh bị hệ thống đóng băng ứng dụng",
                        onClick = onRequestBatteryExemption
                    )
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = statusBarTop + 8.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Hero Moment — Editorial Greeting + Smooth Spring-Animated Permissions Group
            item(key = "hero", contentType = "hero") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = MaterialTheme.shapes.extraLargeIncreased,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HeroSunnyAvatar(
                                avatarUrl = snapshot.userAvatarUrl,
                                displayName = snapshot.displayName
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Xin chào,",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = snapshot.displayName,
                                    style = MaterialTheme.typography.headlineMediumEmphasized,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        val sessionBg = MaterialTheme.colorScheme.surfaceContainerLowest
                        val sessionIconTint = if (snapshot.isSessionValid) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                        val sessionTextColor = if (snapshot.isSessionValid) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                        Surface(
                            onClick = onOpenLogin,
                            shape = CircleShape,
                            color = sessionBg,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .semantics {
                                    stateDescription = if (snapshot.isSessionValid) "Đã kết nối" else "Chưa đăng nhập"
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (snapshot.isSessionValid) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = sessionIconTint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (snapshot.isSessionValid) "TikTok đã kết nối" else "Chưa đăng nhập • Chạm để kết nối",
                                    style = MaterialTheme.typography.labelLargeEmphasized,
                                    color = sessionTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = missingPermissions.isNotEmpty(),
                    enter = expandVertically(
                        animationSpec = motion.defaultSpatialSpec(),
                        expandFrom = Alignment.Top
                    ) + fadeIn(motion.defaultEffectsSpec()),
                    exit = shrinkVertically(
                        animationSpec = motion.defaultSpatialSpec(),
                        shrinkTowards = Alignment.Top
                    ) + fadeOut(motion.fastEffectsSpec())
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Cần cấp ${missingPermissions.size} quyền để chạy ngầm bền bỉ",
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                        ) {
                            missingPermissions.forEachIndexed { index, item ->
                                SegmentedListItem(
                                    onClick = item.onClick,
                                    shapes = ListItemDefaults.segmentedShapes(
                                        index = index,
                                        count = missingPermissions.size
                                    ),
                                    colors = ListItemDefaults.segmentedColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        leadingContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        supportingContentColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.82f),
                                        trailingContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                    ),
                                    leadingContent = {
                                        Icon(item.icon, contentDescription = null)
                                    },
                                    supportingContent = {
                                        Text(item.subtitle)
                                    },
                                    trailingContent = {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                            contentDescription = "Cấp quyền"
                                        )
                                    }
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmallEmphasized
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Today's Streak Progress Panel (28dp outer − 16dp padding = 12dp medium inner tiles)
        item(key = "overview", contentType = "overview") {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tiến độ hôm nay",
                                style = MaterialTheme.typography.titleLargeEmphasized
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (liveRun.isRunning) {
                                    liveRun.statusMessage.ifBlank { "Đang chạy tự động..." }
                                } else {
                                    "Đã giữ $sentToday / ${enabledFriends.size} mục đang bật"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        if (liveRun.isRunning) {
                            RunningContainedIndicator()
                        } else if (allHitToday) {
                            GoalHitBadge()
                        } else {
                            Text(
                                text = "${(progressFraction * 100).toInt()}%",
                                style = MaterialTheme.typography.displaySmallEmphasized.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Wavy means working: waves while actively running or mid-progress, flattens when 0% idle or 100% done
                    if (liveRun.isRunning && liveRun.totalCount <= 0) {
                        LinearWavyProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                        )
                    } else {
                        val shownProgress = if (liveRun.isRunning && liveRun.totalCount > 0) {
                            (liveRun.currentIndex.toFloat() / liveRun.totalCount.toFloat()).coerceIn(0f, 1f)
                        } else {
                            progressFraction
                        }
                        val waveActive = liveRun.isRunning || (shownProgress > 0f && shownProgress < 1f)
                        LinearWavyProgressIndicator(
                            progress = { shownProgress },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            amplitude = { p ->
                                if (waveActive) WavyProgressIndicatorDefaults.indicatorAmplitude(p) else 0f
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                        )
                    }

                    // 3 Stat Tiles — calm tonal surface hierarchy + focused accent (28dp − 16dp = 12dp medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatTile(
                            value = sentToday.toString(),
                            label = "Đã giữ",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            value = successPercent?.let { "$it%" } ?: "—",
                            label = "Thành công",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            value = remainingToday.toString(),
                            label = "Còn lại",
                            containerColor = if (allHitToday) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (allHitToday) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Schedule Footer Strip — medium (12dp) nested corner
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Schedule,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Lần chạy gần nhất",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = FormatUtils.formatRelativePast(snapshot.lastRunMillis),
                                    style = MaterialTheme.typography.titleSmallEmphasized.copy(
                                        fontFeatureSettings = "tnum"
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    "Lịch chạy tiếp theo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (snapshot.isScheduled) {
                                        FormatUtils.formatNextRun(nextRunMillis)
                                    } else {
                                        "Đang tắt lịch"
                                    },
                                    style = MaterialTheme.typography.titleSmallEmphasized.copy(
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = if (snapshot.isScheduled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Streak Message Composer (directly above the primary Send CTA per user preference)
        item(key = "message_composer", contentType = "composer") {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(messageBringIntoView)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Nội dung tin nhắn Streak",
                            style = MaterialTheme.typography.titleMediumEmphasized
                        )
                        Text(
                            text = if (snapshot.randomizeMessages) {
                                "Đang dùng chế độ ngẫu nhiên (${AppConstants.BUILT_IN_STREAK_MESSAGES.size} mẫu)"
                            } else {
                                "Tin nhắn cố định gửi cho các mục đang bật"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (snapshot.randomizeMessages) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Mỗi tài khoản sẽ nhận một mẫu ngẫu nhiên không lặp như \"streak\", \"yo streak\", \"hey streak\"...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = onDisableRandomize,
                                    enabled = !liveRun.isRunning,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Chuyển sang tin nhắn tự nhập")
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = draftMessage,
                            onValueChange = {
                                draftMessage = it
                                onMessageChanged(it)
                            },
                            enabled = !liveRun.isRunning,
                            label = { Text("Tin nhắn gửi đi") },
                            placeholder = { Text(AppConstants.DEFAULT_MESSAGE) },
                            shape = MaterialTheme.shapes.medium,
                            minLines = 1,
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusEvent { focusState ->
                                    if (focusState.isFocused) {
                                        scope.launch {
                                            delay(260)
                                            messageBringIntoView.bringIntoView()
                                        }
                                    }
                                }
                        )
                    }
                }
            }
        }

        // 4. Single Dominant Primary CTA (Medium 56dp with full ButtonDefaults.*For helpers)
        item(key = "actions", contentType = "actions") {
            val ctaHeight = ButtonDefaults.MediumContainerHeight
            AnimatedContent(
                targetState = liveRun.isRunning,
                transitionSpec = {
                    fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
                },
                label = "action_buttons"
            ) { running ->
                if (running) {
                    Button(
                        onClick = onStopRun,
                        shapes = ButtonDefaults.shapesFor(ctaHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(ctaHeight, hasStartIcon = true),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = ctaHeight)
                    ) {
                        Icon(
                            Icons.Rounded.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.iconSizeFor(ctaHeight))
                        )
                        Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(ctaHeight)))
                        Text(
                            "Dừng tác vụ ngay",
                            style = ButtonDefaults.textStyleFor(ctaHeight)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (!snapshot.isSessionValid) {
                                    onOpenLogin()
                                } else {
                                    showRunConfirmDialog = true
                                }
                            },
                            shapes = ButtonDefaults.shapesFor(ctaHeight),
                            contentPadding = ButtonDefaults.contentPaddingFor(ctaHeight, hasStartIcon = true),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = ctaHeight)
                        ) {
                            Icon(
                                imageVector = if (snapshot.isSessionValid) Icons.Rounded.PlayArrow else Icons.AutoMirrored.Rounded.Login,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.iconSizeFor(ctaHeight))
                            )
                            Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(ctaHeight)))
                            Text(
                                text = if (snapshot.isSessionValid) "Gửi Streak ngay" else "Đăng nhập TikTok để bắt đầu",
                                style = ButtonDefaults.textStyleFor(ctaHeight)
                            )
                        }

                        if (retryableFailedIds.isNotEmpty() && snapshot.isSessionValid) {
                            FilledTonalButton(
                                onClick = { showRetryConfirmDialog = true },
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(
                                    "Thử lại ${retryableFailedIds.size} mục lỗi hôm nay",
                                    style = MaterialTheme.typography.labelLargeEmphasized
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Live Execution Console (shown only during active run)
        if (liveRun.isRunning && liveRun.logs.isNotEmpty()) {
            item(key = "live_logs", contentType = "console") {
                val scrollState = rememberScrollState()
                LaunchedEffect(liveRun.logs.size) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Nhật ký chạy trực tiếp",
                                style = MaterialTheme.typography.titleSmallEmphasized
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 96.dp, max = 210.dp)
                                .verticalScroll(scrollState)
                        ) {
                            Text(
                                text = liveRun.logs.takeLast(40).joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        }
        DashboardStatusBarProtection(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    if (showRunConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRunConfirmDialog = false },
            icon = { Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null) },
            title = { Text("Gửi Streak ngay?") },
            text = {
                Text(
                    if (enabledFriends.isEmpty()) {
                        "Danh sách bạn bè/nhóm đang trống hoặc chưa bật mục nào. Hãy thêm ít nhất 1 mục ở tab Danh sách trước."
                    } else {
                        "Ứng dụng sẽ tự động mở TikTok WebView ngầm và gửi tin nhắn đến ${enabledFriends.size} mục đang bật (tự bỏ qua mục đã có chuỗi hôm nay)."
                    }
                )
            },
            confirmButton = {
                if (enabledFriends.isNotEmpty()) {
                    Button(
                        onClick = {
                            showRunConfirmDialog = false
                            onRunNow()
                        },
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Text("Bắt đầu gửi")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showRunConfirmDialog = false }) {
                    Text(if (enabledFriends.isEmpty()) "Đóng" else "Hủy")
                }
            }
        )
    }

    if (showRetryConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRetryConfirmDialog = false },
            icon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
            title = { Text("Thử lại các mục lỗi?") },
            text = {
                Text("Chạy lại riêng cho ${retryableFailedIds.size} bạn bè/nhóm gửi thất bại hôm nay. Lịch chạy chính vẫn giữ nguyên.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRetryConfirmDialog = false
                        onRetryFailed()
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text("Thử lại ngay")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRetryConfirmDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun DashboardStatusBarProtection(
    color: Color,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val topPx = WindowInsets.statusBars.getTop(density)
    if (topPx <= 0) return
    val heightDp = with(density) { (topPx * 1.15f).toDp() }
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color,
                        color.copy(alpha = 0.82f),
                        Color.Transparent
                    )
                )
            )
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroSunnyAvatar(
    avatarUrl: String,
    displayName: String
) {
    TikTokAvatar(
        avatarUrl = avatarUrl,
        fallbackLabel = displayName,
        isGroup = false,
        shape = MaterialShapes.Sunny.toShape(),
        size = 62.dp,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        fallbackContent = {
            Icon(
                imageVector = Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(34.dp)
            )
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RunningContainedIndicator() {
    ContainedLoadingIndicator(modifier = Modifier.size(48.dp))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GoalHitBadge() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(MaterialTheme.colorScheme.tertiary, MaterialShapes.Cookie9Sided.toShape()),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = "Đã giữ toàn bộ chuỗi hôm nay",
            tint = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 11.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmallEmphasized.copy(
                    fontFeatureSettings = "tnum"
                ),
                color = contentColor,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.84f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
