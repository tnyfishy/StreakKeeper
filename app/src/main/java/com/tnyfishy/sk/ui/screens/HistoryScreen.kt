package com.tnyfishy.sk.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tnyfishy.sk.data.StreakRunResult
import com.tnyfishy.sk.ui.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<StreakRunResult>,
    hasLogs: Boolean,
    onExportLogs: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearConfirm by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val totalMessages = remember(history) { history.sumOf { it.friendResults.size } }
    val successMessages = remember(history) { history.sumOf { r -> r.friendResults.count { it.success } } }
    val rate = if (totalMessages > 0) successMessages.toFloat() / totalMessages.toFloat() else 0f
    val lastDuration = history.firstOrNull()?.durationMillis

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Lịch sử & Thống kê") },
                subtitle = {
                    Text(
                        if (history.isEmpty()) "Lưu tối đa 50 lần chạy gần nhất"
                        else "${history.size} bản ghi gần nhất",
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
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            // Hero Success Rate Surface
            item(key = "stats_ring", contentType = "stats_ring") {
                Surface(
                    shape = MaterialTheme.shapes.extraLargeIncreased,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(108.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularWavyProgressIndicator(
                                progress = { rate },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                amplitude = { p ->
                                    if (totalMessages > 0 && p < 1f) {
                                        WavyProgressIndicatorDefaults.indicatorAmplitude(p)
                                    } else 0f
                                },
                                modifier = Modifier.size(108.dp)
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (totalMessages > 0) "${(rate * 100).toInt()}%" else "—",
                                    style = MaterialTheme.typography.headlineSmallEmphasized.copy(
                                        fontFeatureSettings = "tnum"
                                    )
                                )
                                Text(
                                    text = "thành công",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (totalMessages > 0) {
                                    "$successMessages / $totalMessages tin nhắn"
                                } else if (history.isNotEmpty()) {
                                    "${history.count { it.success }} / ${history.size} lần chạy thành công"
                                } else {
                                    "Chưa có dữ liệu chạy"
                                },
                                style = MaterialTheme.typography.titleMediumEmphasized.copy(
                                    fontFeatureSettings = "tnum"
                                )
                            )
                            Text(
                                text = "Tự động lưu 50 phiên gửi gần nhất trên thiết bị",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Thời lượng lần cuối: ${FormatUtils.formatDuration(lastDuration)}",
                                style = MaterialTheme.typography.labelLargeEmphasized.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Export Logs & Clear History Actions
            item(key = "actions", contentType = "actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onExportLogs,
                        enabled = hasLogs,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.IosShare, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Xuất log")
                    }
                    OutlinedButton(
                        onClick = { showClearConfirm = true },
                        enabled = history.isNotEmpty() || hasLogs,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Xóa lịch sử")
                    }
                }
            }

            if (history.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            EmptyHistoryShapeBadge()
                            Text(
                                "Chưa có lịch sử chạy",
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Kết quả các lần gửi tự động và thủ công sẽ xuất hiện tại đây.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = history,
                    key = { _, run -> run.id },
                    contentType = { _, _ -> "history_run" }
                ) { index, run ->
                    RunHistorySegmentedItem(
                        run = run,
                        index = index,
                        count = history.size
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Xóa lịch sử và log?") },
            text = { Text("Toàn bộ lịch sử chạy và log chẩn đoán trên thiết bị sẽ bị xóa.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearHistory()
                        showClearConfirm = false
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Hủy") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyHistoryShapeBadge() {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(MaterialShapes.SoftBurst.toShape())
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun RunHistorySegmentedItem(
    run: StreakRunResult,
    index: Int,
    count: Int
) {
    var expanded by remember(run.id) { mutableStateOf(false) }
    val motion = MaterialTheme.motionScheme
    val statusColor = if (run.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val sentOk = run.friendResults.count { it.success }
    val total = run.friendResults.size
    val expandable = run.friendResults.isNotEmpty()

    val subtitle = when {
        total > 0 -> {
            val fail = total - sentOk
            val dur = FormatUtils.formatDuration(run.durationMillis)
            if (fail > 0) "$sentOk/$total đã gửi • $fail lỗi • $dur"
            else "$sentOk/$total đã gửi thành công • $dur"
        }
        !run.errorMessage.isNullOrBlank() -> run.errorMessage
        else -> if (run.success) "Hoàn tất" else "Không có tin nhắn nào được gửi"
    }

    SegmentedListItem(
        onClick = { if (expandable) expanded = !expanded },
        enabled = expandable,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
        },
        trailingContent = if (expandable) {
            {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Thu gọn chi tiết" else "Xem chi tiết",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else null,
        supportingContent = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = if (total == 0 && !run.success) statusColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) 4 else 2,
                    overflow = TextOverflow.Ellipsis
                )

                AnimatedVisibility(
                    visible = expanded && expandable,
                    enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                    exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec())
                ) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        run.friendResults.forEach { fr ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (fr.success) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                                    contentDescription = null,
                                    tint = if (fr.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (fr.isGroup) fr.username else "@${fr.username}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when {
                                        fr.skippedAlreadyLit -> "Đã có chuỗi"
                                        fr.success -> "Đã gửi"
                                        else -> fr.errorMessage ?: "Lỗi"
                                    },
                                    style = MaterialTheme.typography.labelSmallEmphasized,
                                    color = if (fr.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = Modifier.semantics {
            if (expandable) {
                stateDescription = if (expanded) "Đang mở rộng" else "Đang thu gọn"
            }
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = FormatUtils.formatDateTime(run.runTimeMillis),
                style = MaterialTheme.typography.titleMediumEmphasized.copy(
                    fontFeatureSettings = "tnum"
                )
            )
            if (run.isTargetedRetry) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "Thử lại",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
