package com.tnyfishy.sk.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSliderState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tnyfishy.sk.BuildConfig
import com.tnyfishy.sk.data.AppSettingsSnapshot
import com.tnyfishy.sk.data.ChatPriority
import com.tnyfishy.sk.service.StreakScheduler
import com.tnyfishy.sk.ui.FormatUtils
import com.tnyfishy.sk.ui.components.TikTokAvatar
import kotlin.math.roundToInt

private data class SwitchSettingSpec(
    val title: String,
    val subtitle: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    snapshot: AppSettingsSnapshot,
    onDisplayNameChanged: (String) -> Unit,
    onOpenLogin: () -> Unit,
    onLogout: () -> Unit,
    onScheduleToggled: (Boolean) -> Unit,
    onUseFixedTimeToggled: (Boolean) -> Unit,
    onFixedTimeChanged: (hour: Int, minute: Int) -> Unit,
    onIntervalHoursChanged: (Int) -> Unit,
    onRandomizeMessagesToggled: (Boolean) -> Unit,
    onSkipUnreachableToggled: (Boolean) -> Unit,
    onSkipIfStreakLitTodayToggled: (Boolean) -> Unit,
    onSendOnBatteryLowToggled: (Boolean) -> Unit,
    onChatPriorityChanged: (ChatPriority) -> Unit,
    onDynamicColorToggled: (Boolean) -> Unit,
    onOpenHyperOsAutostart: () -> Unit,
    onOpenHyperOsBattery: () -> Unit,
    modifier: Modifier = Modifier
) {
    var displayNameDraft by remember(snapshot.displayName) { mutableStateOf(snapshot.displayName) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val motion = MaterialTheme.motionScheme
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val behaviorItems = listOf(
        SwitchSettingSpec(
            title = "Bỏ qua nếu hôm nay đã có chuỗi",
            subtitle = "Tự kiểm tra trên TikTok và không gửi thừa khi chuỗi hôm nay đã sáng",
            checked = snapshot.skipIfStreakLitToday,
            onCheckedChange = onSkipIfStreakLitTodayToggled
        ),
        SwitchSettingSpec(
            title = "Tin nhắn ngẫu nhiên (50 mẫu)",
            subtitle = "Xáo trộn tự nhiên các câu ngắn giữ streak để tránh gửi trùng lặp",
            checked = snapshot.randomizeMessages,
            onCheckedChange = onRandomizeMessagesToggled
        ),
        SwitchSettingSpec(
            title = "Bỏ qua nick không tìm thấy",
            subtitle = "Tiếp tục gửi người khác nếu 1 nick lỗi; tự tắt sau 3 lần không thấy",
            checked = snapshot.skipUnreachableUsers,
            onCheckedChange = onSkipUnreachableToggled
        ),
        SwitchSettingSpec(
            title = "Gửi sớm khi pin yếu",
            subtitle = "Tự gửi trước nếu hệ thống báo pin yếu và hôm nay chưa gửi (tối đa 1 lần/ngày)",
            checked = snapshot.sendOnBatteryLow,
            onCheckedChange = onSendOnBatteryLowToggled
        )
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Cài đặt") },
                subtitle = {
                    Text(
                        "Tài khoản, lịch tự động & giao diện",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Account & TikTok Session Surface (28dp outer − 16dp padding = 12dp medium input)
            item(key = "account", contentType = "account") {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SettingsProfileAvatar(
                                avatarUrl = snapshot.userAvatarUrl,
                                displayName = snapshot.displayName
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Phiên đăng nhập TikTok",
                                    style = MaterialTheme.typography.titleMediumEmphasized
                                )
                                Text(
                                    text = if (snapshot.isSessionValid) {
                                        "Đang hoạt động • ${FormatUtils.formatDateTime(snapshot.sessionLastCheckMillis)}"
                                    } else {
                                        "Chưa có cookie phiên hợp lệ"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (snapshot.isSessionValid) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = displayNameDraft,
                            onValueChange = {
                                displayNameDraft = it
                                onDisplayNameChanged(it)
                            },
                            label = { Text("Tên hiển thị (tự lấy từ TikTok khi đăng nhập)") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onOpenLogin,
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text(if (snapshot.isSessionValid) "Mở WebView" else "Đăng nhập TikTok")
                            }
                            if (snapshot.isSessionValid) {
                                OutlinedButton(
                                    onClick = { showLogoutConfirm = true },
                                    shapes = ButtonDefaults.shapes()
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text("Đăng xuất")
                                }
                            }
                        }
                    }
                }
            }

            // 2. Automatic Schedule (SegmentedListItem + Spring reveal + State-based Slider)
            item(key = "schedule", contentType = "schedule") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsSectionTitle("Lịch tự động")

                    SegmentedListItem(
                        checked = snapshot.isScheduled,
                        onCheckedChange = onScheduleToggled,
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                        colors = switchSegmentedColors(),
                        supportingContent = {
                            Text("Tự gửi tin nhắn giữ streak ngay cả khi đóng ứng dụng")
                        },
                        trailingContent = {
                            Switch(checked = snapshot.isScheduled, onCheckedChange = null)
                        }
                    ) {
                        Text(
                            "Tự động chạy ngầm theo lịch",
                            style = MaterialTheme.typography.titleMediumEmphasized
                        )
                    }

                    AnimatedVisibility(
                        visible = snapshot.isScheduled,
                        enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                        exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec())
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                            ) {
                                ToggleButton(
                                    checked = !snapshot.useFixedTime,
                                    onCheckedChange = { if (it) onUseFixedTimeToggled(false) },
                                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics { role = Role.RadioButton }
                                ) {
                                    Text("Chu kỳ theo giờ", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                ToggleButton(
                                    checked = snapshot.useFixedTime,
                                    onCheckedChange = { if (it) onUseFixedTimeToggled(true) },
                                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics { role = Role.RadioButton }
                                ) {
                                    Text("Giờ cố định", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            if (snapshot.useFixedTime) {
                                SegmentedListItem(
                                    onClick = { showTimePicker = true },
                                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                                    colors = ListItemDefaults.segmentedColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ),
                                    leadingContent = {
                                        Icon(
                                            Icons.Rounded.AccessTime,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    supportingContent = { Text("Chạm để chọn lại khung giờ hàng ngày") },
                                    trailingContent = {
                                        Text(
                                            text = FormatUtils.formatHourMinute(
                                                snapshot.fixedTimeHour,
                                                snapshot.fixedTimeMinute
                                            ),
                                            style = MaterialTheme.typography.headlineSmallEmphasized.copy(
                                                fontFeatureSettings = "tnum"
                                            ),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                ) {
                                    Text(
                                        "Giờ chạy hàng ngày",
                                        style = MaterialTheme.typography.titleSmallEmphasized
                                    )
                                }
                            } else {
                                IntervalSliderPanel(
                                    intervalHours = snapshot.intervalHours,
                                    onIntervalHoursChanged = onIntervalHoursChanged
                                )
                            }
                        }
                    }
                }
            }

            // 3. Automation Behavior — Genuine SegmentedListItem Group (Zero Cards / Zero Dividers)
            item(key = "behavior", contentType = "behavior") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsSectionTitle("Hành vi gửi & Độ bền")

                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        behaviorItems.forEachIndexed { index, spec ->
                            SegmentedListItem(
                                checked = spec.checked,
                                onCheckedChange = spec.onCheckedChange,
                                shapes = ListItemDefaults.segmentedShapes(index = index, count = behaviorItems.size),
                                colors = switchSegmentedColors(),
                                supportingContent = { Text(spec.subtitle) },
                                trailingContent = { Switch(checked = spec.checked, onCheckedChange = null) }
                            ) {
                                Text(spec.title, style = MaterialTheme.typography.titleSmallEmphasized)
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Thứ tự ưu tiên gửi",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                    ) {
                        ToggleButton(
                            checked = snapshot.chatPriority == ChatPriority.MIXED,
                            onCheckedChange = { if (it) onChatPriorityChanged(ChatPriority.MIXED) },
                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                            modifier = Modifier
                                .weight(1f)
                                .semantics { role = Role.RadioButton }
                        ) {
                            Text("Mặc định", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        ToggleButton(
                            checked = snapshot.chatPriority == ChatPriority.FRIENDS_FIRST,
                            onCheckedChange = { if (it) onChatPriorityChanged(ChatPriority.FRIENDS_FIRST) },
                            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                            modifier = Modifier
                                .weight(1f)
                                .semantics { role = Role.RadioButton }
                        ) {
                            Text("Bạn bè", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        ToggleButton(
                            checked = snapshot.chatPriority == ChatPriority.GROUPS_FIRST,
                            onCheckedChange = { if (it) onChatPriorityChanged(ChatPriority.GROUPS_FIRST) },
                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            modifier = Modifier
                                .weight(1f)
                                .semantics { role = Role.RadioButton }
                        ) {
                            Text("Nhóm", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // 4. Appearance & Dynamic Color Toggle
            item(key = "appearance", contentType = "appearance") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsSectionTitle("Giao diện")
                    SegmentedListItem(
                        checked = snapshot.useDynamicColor && supportsDynamicColor,
                        onCheckedChange = onDynamicColorToggled,
                        enabled = supportsDynamicColor,
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                        colors = switchSegmentedColors(),
                        leadingContent = {
                            Icon(
                                Icons.Rounded.Palette,
                                contentDescription = null
                            )
                        },
                        supportingContent = {
                            Text(
                                if (supportsDynamicColor) {
                                    "Đồng bộ bảng màu Material You theo hình nền hệ thống; tắt để dùng màu Flame-Rose mặc định"
                                } else {
                                    "Yêu cầu Android 12 trở lên"
                                }
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = snapshot.useDynamicColor && supportsDynamicColor,
                                onCheckedChange = null,
                                enabled = supportsDynamicColor
                            )
                        }
                    ) {
                        Text(
                            "Màu động theo hình nền",
                            style = MaterialTheme.typography.titleSmallEmphasized
                        )
                    }
                }
            }

            // 5. Xiaomi / HyperOS Background Resilience Section
            if (StreakScheduler.isXiaomiOrHyperOs()) {
                item(key = "hyperos_resilience", contentType = "hyperos") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingsSectionTitle("Chống tắt ngầm HyperOS / Xiaomi")
                        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                            SegmentedListItem(
                                onClick = onOpenHyperOsAutostart,
                                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                                colors = ListItemDefaults.segmentedColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                leadingContent = {
                                    Icon(
                                        Icons.Rounded.RocketLaunch,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                supportingContent = {
                                    Text("Cho phép tự khởi chạy sau khi khởi động lại thiết bị")
                                },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                                }
                            ) {
                                Text("Tự khởi chạy (Autostart)", style = MaterialTheme.typography.titleSmallEmphasized)
                            }
                            SegmentedListItem(
                                onClick = onOpenHyperOsBattery,
                                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                                colors = ListItemDefaults.segmentedColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                leadingContent = {
                                    Icon(
                                        Icons.Rounded.Bolt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                supportingContent = {
                                    Text("Chọn \"Không hạn chế\" để hệ thống không đóng băng lịch hẹn")
                                },
                                trailingContent = {
                                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                                }
                            ) {
                                Text("Tiết kiệm pin HyperOS", style = MaterialTheme.typography.titleSmallEmphasized)
                            }
                        }
                    }
                }
            }

            // 6. Privacy & About Footer
            item(key = "about", contentType = "about") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Streak Keeper v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.titleSmallEmphasized
                            )
                            Text(
                                "100% xử lý cục bộ trên thiết bị. Không máy chủ trung gian, không theo dõi, cookie chỉ lưu trong WebView hệ thống.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = snapshot.fixedTimeHour,
            initialMinute = snapshot.fixedTimeMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Chọn giờ chạy mỗi ngày") },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = pickerState)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onFixedTimeChanged(pickerState.hour, pickerState.minute)
                        showTimePicker = false
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text("Lưu giờ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Hủy") }
            }
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Đăng xuất TikTok?") },
            text = { Text("Cookie phiên sẽ bị xóa khỏi WebView. Bạn sẽ cần đăng nhập lại trước khi gửi streak.") },
            confirmButton = {
                Button(
                    onClick = {
                        onLogout()
                        showLogoutConfirm = false
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Đăng xuất")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Hủy") }
            }
        )
    }
}

@Composable
private fun switchSegmentedColors() = ListItemDefaults.segmentedColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = MaterialTheme.colorScheme.onSurface,
    leadingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    overlineContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    selectedContentColor = MaterialTheme.colorScheme.onSurface,
    selectedLeadingContentColor = MaterialTheme.colorScheme.primary,
    selectedTrailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedOverlineContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsProfileAvatar(
    avatarUrl: String,
    displayName: String
) {
    TikTokAvatar(
        avatarUrl = avatarUrl,
        fallbackLabel = displayName,
        isGroup = false,
        shape = MaterialShapes.SoftBurst.toShape(),
        size = 56.dp,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        fallbackContent = {
            Icon(
                Icons.Rounded.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalSliderPanel(
    intervalHours: Int,
    onIntervalHoursChanged: (Int) -> Unit
) {
    val sliderState = rememberSliderState(
        value = intervalHours.toFloat(),
        steps = 21,
        trackRange = 1f..23f
    )
    SideEffect(intervalHours) {
        if (sliderState.value.roundToInt() != intervalHours) {
            sliderState.value = intervalHours.toFloat()
        }
    }

    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Mỗi $intervalHours giờ",
                        style = MaterialTheme.typography.titleMediumEmphasized.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Khuyến nghị 23 giờ để giữ chuỗi an toàn",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(
                        onClick = { onIntervalHoursChanged((intervalHours - 1).coerceAtLeast(1)) },
                        enabled = intervalHours > 1,
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Rounded.Remove, contentDescription = "Giảm 1 giờ")
                    }
                    FilledTonalIconButton(
                        onClick = { onIntervalHoursChanged((intervalHours + 1).coerceAtMost(23)) },
                        enabled = intervalHours < 23,
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Tăng 1 giờ")
                    }
                }
            }

            Slider(
                state = sliderState,
                onValueChange = { next ->
                    sliderState.value = next
                    onIntervalHoursChanged(next.roundToInt().coerceIn(1, 23))
                }
            )
        }
    }
}
