package com.tnyfishy.sk.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.tnyfishy.sk.data.FriendConfig
import com.tnyfishy.sk.engine.ChatScanState
import com.tnyfishy.sk.engine.ScannedChat
import com.tnyfishy.sk.ui.FormatUtils
import com.tnyfishy.sk.ui.components.TikTokAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    friends: List<FriendConfig>,
    isRunning: Boolean,
    isSessionValid: Boolean,
    scanState: ChatScanState,
    onOpenLogin: () -> Unit,
    onStartScanChats: () -> Unit,
    onCancelScanChats: () -> Unit,
    onAddScannedChats: (List<ScannedChat>) -> Unit,
    onAddFriend: (username: String, displayName: String) -> Result<FriendConfig>,
    onAddGroup: (groupName: String) -> Result<FriendConfig>,
    onUpdateFriend: (FriendConfig) -> Unit,
    onDeleteFriend: (String) -> Unit,
    onSetAllEnabled: (isGroup: Boolean, enabled: Boolean) -> Unit,
    onClearAllByType: (isGroup: Boolean) -> Unit,
    onImportJson: () -> Unit,
    onExportJson: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showGroupsTab by rememberSaveable { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showScanSheet by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<FriendConfig?>(null) }
    var deletingEntry by remember { mutableStateOf<FriendConfig?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    val fabBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = fabBackState,
        isBackEnabled = fabMenuExpanded,
        onBackCompleted = { fabMenuExpanded = false }
    )

    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val motion = MaterialTheme.motionScheme

    val fabVisible by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 || !listState.canScrollForward
        }
    }

    val totalCount = friends.size
    val enabledCount = remember(friends) { friends.count { it.isEnabled } }
    val sentTodayCount = remember(friends) {
        friends.count { FormatUtils.isToday(it.lastMessageSentMillis) || it.streakLitToday }
    }
    val dmCount = remember(friends) { friends.count { !it.isGroup } }
    val grpCount = remember(friends) { friends.count { it.isGroup } }

    val filtered = remember(friends, showGroupsTab, searchQuery) {
        val byType = friends.filter { it.isGroup == showGroupsTab }
        val q = searchQuery.trim()
        if (q.isEmpty()) byType
        else byType.filter {
            it.username.contains(q, ignoreCase = true) ||
                it.displayName.contains(q, ignoreCase = true)
        }
    }

    fun triggerScan() {
        if (!isSessionValid) {
            onOpenLogin()
        } else {
            showScanSheet = true
            onStartScanChats()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Danh sách Streak") },
                subtitle = {
                    Text(
                        "$enabledCount/$totalCount đang bật • $sentTodayCount giữ chuỗi hôm nay",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { overflowExpanded = true },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "Tùy chọn danh sách"
                            )
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Bật tất cả mục đang hiện") },
                                leadingIcon = { Icon(Icons.Rounded.DoneAll, contentDescription = null) },
                                enabled = !isRunning && filtered.isNotEmpty(),
                                onClick = {
                                    overflowExpanded = false
                                    onSetAllEnabled(showGroupsTab, true)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Tắt tất cả mục đang hiện") },
                                leadingIcon = { Icon(Icons.Rounded.RemoveDone, contentDescription = null) },
                                enabled = !isRunning && filtered.isNotEmpty(),
                                onClick = {
                                    overflowExpanded = false
                                    onSetAllEnabled(showGroupsTab, false)
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (showGroupsTab) "Xóa toàn bộ nhóm" else "Xóa toàn bộ bạn bè",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.DeleteSweep,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                enabled = !isRunning && friends.any { it.isGroup == showGroupsTab },
                                onClick = {
                                    overflowExpanded = false
                                    showClearAllConfirm = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
                    .imePadding(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = 104.dp
                ),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                // Connected ToggleButton group (with RadioButton semantics)
                item(key = "mode_switch", contentType = "mode_switch") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                    ) {
                        ToggleButton(
                            checked = !showGroupsTab,
                            onCheckedChange = { if (it) showGroupsTab = false },
                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                            modifier = Modifier
                                .weight(1f)
                                .semantics { role = Role.RadioButton }
                        ) {
                            Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Bạn bè ($dmCount)",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        ToggleButton(
                            checked = showGroupsTab,
                            onCheckedChange = { if (it) showGroupsTab = true },
                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            modifier = Modifier
                                .weight(1f)
                                .semantics { role = Role.RadioButton }
                        ) {
                            Icon(Icons.Rounded.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Nhóm ($grpCount)",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Pill Search Bar
                item(key = "search", contentType = "search") {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = if (showGroupsTab) "Tìm tên nhóm chat..." else "Tìm @username hoặc tên...",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Xóa từ khóa")
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                }

                if (filtered.isEmpty()) {
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
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                EmptyListShapeBadge(isGroup = showGroupsTab)
                                Text(
                                    text = when {
                                        searchQuery.isNotBlank() -> "Không tìm thấy \"$searchQuery\""
                                        showGroupsTab -> "Chưa có nhóm chat nào"
                                        else -> "Chưa có bạn bè nào"
                                    },
                                    style = MaterialTheme.typography.titleMediumEmphasized,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Quét hộp thư TikTok để lấy tự động tên, ảnh đại diện và số ngày chuỗi, hoặc nhập thủ công.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                if (searchQuery.isBlank()) {
                                    Row(
                                        modifier = Modifier.padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { triggerScan() },
                                            enabled = !isRunning,
                                            shapes = ButtonDefaults.shapes()
                                        ) {
                                            Icon(Icons.Rounded.Forum, null, Modifier.size(ButtonDefaults.IconSize))
                                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                            Text("Quét đoạn chat")
                                        }
                                        FilledTonalButton(
                                            onClick = { showAddDialog = true },
                                            enabled = !isRunning,
                                            shapes = ButtonDefaults.shapes()
                                        ) {
                                            Icon(Icons.Rounded.PersonAdd, null, Modifier.size(ButtonDefaults.IconSize))
                                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                            Text("Nhập tay")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        items = filtered,
                        key = { _, entry -> entry.id },
                        contentType = { _, entry -> if (entry.isGroup) "group_row" else "friend_row" }
                    ) { index, entry ->
                        FriendSegmentedRow(
                            entry = entry,
                            index = index,
                            count = filtered.size,
                            isRunning = isRunning,
                            context = context,
                            onToggle = { enabled -> onUpdateFriend(entry.copy(isEnabled = enabled)) },
                            onEdit = { editingEntry = entry },
                            onDelete = { deletingEntry = entry },
                            modifier = Modifier.animateItem(
                                placementSpec = motion.defaultSpatialSpec()
                            )
                        )
                    }
                }
            }

            // M3 Expressive FloatingActionButtonMenu (replaces cluttered dual-button + chip row)
            FloatingActionButtonMenu(
                modifier = Modifier.align(Alignment.BottomEnd),
                expanded = fabMenuExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = fabMenuExpanded,
                        onCheckedChange = { if (!isRunning) fabMenuExpanded = it },
                        modifier = Modifier
                            .semantics {
                                traversalIndex = -1f
                                stateDescription = if (fabMenuExpanded) "Đang mở" else "Đang đóng"
                                contentDescription = "Menu thêm và quản lý danh sách"
                            }
                            .animateFloatingActionButton(
                                visible = (fabVisible || fabMenuExpanded) && !isRunning,
                                alignment = Alignment.BottomEnd
                            )
                    ) {
                        val icon by remember {
                            derivedStateOf {
                                if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add
                            }
                        }
                        Icon(
                            painter = rememberVectorPainter(icon),
                            contentDescription = null,
                            modifier = Modifier.animateIcon({ checkedProgress })
                        )
                    }
                }
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabMenuExpanded = false
                        triggerScan()
                    },
                    icon = { Icon(Icons.Rounded.Forum, contentDescription = null) },
                    text = { Text("Quét đoạn chat TikTok") }
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabMenuExpanded = false
                        showAddDialog = true
                    },
                    icon = { Icon(Icons.Rounded.PersonAdd, contentDescription = null) },
                    text = { Text(if (showGroupsTab) "Thêm nhóm thủ công" else "Nhập @username thủ công") }
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabMenuExpanded = false
                        onImportJson()
                    },
                    icon = { Icon(Icons.Rounded.FileDownload, contentDescription = null) },
                    text = { Text("Nhập từ file JSON") }
                )
                if (friends.isNotEmpty()) {
                    FloatingActionButtonMenuItem(
                        onClick = {
                            fabMenuExpanded = false
                            onExportJson()
                        },
                        icon = { Icon(Icons.Rounded.FileUpload, contentDescription = null) },
                        text = { Text("Xuất danh sách JSON") }
                    )
                }
            }
        }
    }

    if (showScanSheet) {
        ScannedChatsBottomSheet(
            scanState = scanState,
            existingFriends = friends,
            onDismiss = {
                showScanSheet = false
                if (scanState.isScanning) onCancelScanChats()
            },
            onRescan = onStartScanChats,
            onStopScan = onCancelScanChats,
            onConfirmSelected = { selected ->
                onAddScannedChats(selected)
                showScanSheet = false
                if (scanState.isScanning) onCancelScanChats()
            }
        )
    }

    if (showAddDialog) {
        AddFriendOrGroupDialog(
            isGroup = showGroupsTab,
            onDismiss = { showAddDialog = false },
            onSaveFriend = { user, disp ->
                val res = onAddFriend(user, disp)
                if (res.isSuccess) showAddDialog = false
                res.exceptionOrNull()?.message
            },
            onSaveGroup = { name ->
                val res = onAddGroup(name)
                if (res.isSuccess) showAddDialog = false
                res.exceptionOrNull()?.message
            }
        )
    }

    editingEntry?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismiss = { editingEntry = null },
            onConfirm = { newName ->
                onUpdateFriend(entry.copy(displayName = newName.trim()))
                editingEntry = null
            }
        )
    }

    deletingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deletingEntry = null },
            title = { Text(if (entry.isGroup) "Xóa nhóm chat?" else "Xóa bạn bè?") },
            text = { Text("Xóa \"${entry.effectiveLabel}\" khỏi danh sách giữ Streak?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteFriend(entry.id)
                        deletingEntry = null
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
                TextButton(onClick = { deletingEntry = null }) { Text("Hủy") }
            }
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(if (showGroupsTab) "Xóa toàn bộ nhóm chat?" else "Xóa toàn bộ bạn bè?") },
            text = {
                Text(
                    if (showGroupsTab) "Tất cả nhóm chat sẽ bị xóa. Danh sách bạn bè DM vẫn giữ nguyên."
                    else "Tất cả bạn bè DM sẽ bị xóa. Danh sách nhóm chat vẫn giữ nguyên."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllByType(showGroupsTab)
                        showClearAllConfirm = false
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Xóa hết")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("Hủy") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyListShapeBadge(isGroup: Boolean) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(
                if (isGroup) MaterialShapes.Clover4Leaf.toShape()
                else MaterialShapes.Cookie9Sided.toShape()
            )
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isGroup) Icons.Rounded.Group else Icons.Rounded.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(30.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FriendMaskedAvatar(
    avatarUrl: String,
    label: String,
    isGroup: Boolean,
    enabled: Boolean,
    context: Context,
    size: androidx.compose.ui.unit.Dp = 46.dp
) {
    val shape = if (isGroup) {
        MaterialShapes.Clover4Leaf.toShape()
    } else {
        MaterialShapes.Cookie9Sided.toShape()
    }
    TikTokAvatar(
        avatarUrl = avatarUrl,
        fallbackLabel = label,
        isGroup = isGroup,
        shape = shape,
        size = size,
        containerColor = if (enabled) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        context = context
    )
}

@Composable
private fun StreakDaysBadge(
    streakDays: Int,
    litToday: Boolean,
    modifier: Modifier = Modifier
) {
    if (streakDays <= 0) return
    val bg = if (litToday) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val fg = if (litToday) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = CircleShape,
        color = bg,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "$streakDays ngày",
                style = MaterialTheme.typography.labelSmallEmphasized.copy(
                    fontFeatureSettings = "tnum"
                ),
                color = fg,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FriendSegmentedRow(
    entry: FriendConfig,
    index: Int,
    count: Int,
    isRunning: Boolean,
    context: Context,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember(entry.id) { mutableStateOf(false) }

    val healthColor = when {
        entry.consecutiveFailures >= 3 -> MaterialTheme.colorScheme.error
        entry.consecutiveFailures > 0 -> MaterialTheme.colorScheme.tertiary
        entry.streakLitToday || FormatUtils.isToday(entry.lastMessageSentMillis) -> MaterialTheme.colorScheme.primary
        entry.lastMessageSentMillis != null -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }
    val healthText = when {
        entry.consecutiveFailures > 0 -> buildString {
            append("${entry.consecutiveFailures} lỗi liên tiếp")
            if (entry.lastMessageSentMillis != null) {
                append(" • ${FormatUtils.formatDateTime(entry.lastMessageSentMillis)}")
            }
        }
        entry.streakLitToday && !FormatUtils.isToday(entry.lastMessageSentMillis) ->
            "Đã có chuỗi hôm nay trên TikTok"
        entry.lastMessageSentMillis != null ->
            "Gửi gần nhất: ${FormatUtils.formatDateTime(entry.lastMessageSentMillis)}"
        else -> "Chưa gửi lần nào"
    }

    val litToday = entry.streakLitToday || FormatUtils.isToday(entry.lastMessageSentMillis)

    SegmentedListItem(
        checked = entry.isEnabled,
        onCheckedChange = onToggle,
        enabled = !isRunning,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            leadingContentColor = MaterialTheme.colorScheme.onSurface,
            trailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            overlineContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            selectedContentColor = MaterialTheme.colorScheme.onSurface,
            selectedLeadingContentColor = MaterialTheme.colorScheme.onSurface,
            selectedTrailingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedOverlineContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedSupportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        leadingContent = {
            FriendMaskedAvatar(
                avatarUrl = entry.avatarUrl,
                label = entry.effectiveLabel,
                isGroup = entry.isGroup,
                enabled = entry.isEnabled,
                context = context
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (entry.isGroup) "Nhóm chat" else "@${entry.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(healthColor)
                    )
                    Text(
                        text = healthText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = entry.isEnabled,
                    onCheckedChange = null,
                    enabled = !isRunning
                )
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        enabled = !isRunning,
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "Tùy chọn cho ${entry.effectiveLabel}",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sửa tên hiển thị") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Xóa", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        },
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = entry.effectiveLabel,
                style = if (entry.isEnabled) {
                    MaterialTheme.typography.titleMediumEmphasized
                } else {
                    MaterialTheme.typography.titleMedium
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            StreakDaysBadge(
                streakDays = entry.streakDays,
                litToday = litToday
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannedChatsBottomSheet(
    scanState: ChatScanState,
    existingFriends: List<FriendConfig>,
    onDismiss: () -> Unit,
    onRescan: () -> Unit,
    onStopScan: () -> Unit,
    onConfirmSelected: (List<ScannedChat>) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    var filterQuery by remember { mutableStateOf("") }
    // 0 = All, 1 = Has Streak, 2 = Not yet added
    var filterMode by remember { mutableIntStateOf(0) }
    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }

    val existingKeys = remember(existingFriends) {
        existingFriends.map { f ->
            if (f.isGroup) "g:${f.displayName.lowercase()}" else "u:${f.username.lowercase()}"
        }.toSet()
    }

    val visibleChats = remember(scanState.chats, filterQuery, filterMode, existingKeys) {
        val q = filterQuery.trim()
        scanState.chats.filter { chat ->
            val matchesMode = when (filterMode) {
                1 -> chat.streakDays > 0
                2 -> chat.stableKey !in existingKeys
                else -> true
            }
            val matchesQuery = q.isEmpty() ||
                chat.displayName.contains(q, ignoreCase = true) ||
                chat.username.contains(q, ignoreCase = true)
            matchesMode && matchesQuery
        }
    }

    val selectableNewChats = remember(visibleChats, existingKeys) {
        visibleChats.filter { it.stableKey !in existingKeys }
    }

    val allSelectableChats = remember(scanState.chats, existingKeys) {
        scanState.chats.filter { it.stableKey !in existingKeys }
    }

    SideEffect(scanState.chats.isEmpty()) {
        if (scanState.chats.isEmpty() && selectedKeys.isNotEmpty()) {
            selectedKeys = emptySet()
        }
    }

    val totalSelectedCount = remember(allSelectableChats, selectedKeys) {
        allSelectableChats.count { it.stableKey in selectedKeys }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quét đoạn chat TikTok",
                        style = MaterialTheme.typography.titleLargeEmphasized
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = scanState.statusText.ifBlank {
                            if (scanState.isScanning) "Đang kết nối hộp thư..."
                            else "Đã tìm thấy ${scanState.chats.size} cuộc trò chuyện"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(10.dp))
                if (scanState.isScanning) {
                    OutlinedButton(
                        onClick = onStopScan,
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Rounded.Stop, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Dừng")
                    }
                } else {
                    FilledTonalButton(
                        onClick = onRescan,
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Quét lại")
                    }
                }
            }

            if (scanState.isScanning) {
                LinearWavyProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
            }

            scanState.error?.let { err ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (scanState.chats.isNotEmpty()) {
                TextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = {
                        Text(
                            text = "Tìm tên hoặc @username...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (filterQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { filterQuery = "" },
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Xóa từ khóa", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                val streakCount = remember(scanState.chats) { scanState.chats.count { it.streakDays > 0 } }
                val newCount = allSelectableChats.size

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = filterMode == 0,
                            onClick = { filterMode = 0 },
                            label = { Text("Tất cả (${scanState.chats.size})") }
                        )
                        if (streakCount > 0) {
                            FilterChip(
                                selected = filterMode == 1,
                                onClick = { filterMode = 1 },
                                label = { Text("Có chuỗi ($streakCount)") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.LocalFireDepartment,
                                        null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            )
                        }
                        FilterChip(
                            selected = filterMode == 2,
                            onClick = { filterMode = 2 },
                            label = { Text("Chưa thêm ($newCount)") }
                        )
                    }

                    if (selectableNewChats.isNotEmpty()) {
                        val allVisibleSelected = selectableNewChats.all { it.stableKey in selectedKeys }
                        TextButton(
                            onClick = {
                                val visibleKeys = selectableNewChats.map { it.stableKey }.toSet()
                                selectedKeys = if (allVisibleSelected) {
                                    selectedKeys - visibleKeys
                                } else {
                                    selectedKeys + visibleKeys
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(if (allVisibleSelected) "Bỏ chọn" else "Chọn hết")
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (visibleChats.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = when {
                                scanState.isScanning -> "Đang đọc danh sách hộp thư TikTok..."
                                filterQuery.isNotBlank() || filterMode != 0 -> "Không có cuộc trò chuyện nào khớp bộ lọc"
                                else -> "Chưa tìm thấy cuộc trò chuyện nào"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        itemsIndexed(
                            items = visibleChats,
                            key = { _, c -> c.stableKey },
                            contentType = { _, c -> if (c.stableKey in existingKeys) "existing_chat" else "selectable_chat" }
                        ) { idx, chat ->
                            val alreadyExists = chat.stableKey in existingKeys
                            val itemShapes = ListItemDefaults.segmentedShapes(index = idx, count = visibleChats.size)

                            if (alreadyExists) {
                                SegmentedListItem(
                                    shapes = itemShapes,
                                    colors = ListItemDefaults.segmentedColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ),
                                    leadingContent = {
                                        FriendMaskedAvatar(
                                            avatarUrl = chat.avatarUrl,
                                            label = chat.displayName,
                                            isGroup = chat.isGroup,
                                            enabled = false,
                                            context = context,
                                            size = 42.dp
                                        )
                                    },
                                    supportingContent = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (chat.isGroup) "Nhóm chat" else "@${chat.username}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            StreakDaysBadge(
                                                streakDays = chat.streakDays,
                                                litToday = chat.streakLitToday
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.CheckCircle,
                                                    null,
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    "Đã có",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Text(
                                        text = chat.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                ScannedChatSelectableRow(
                                    chat = chat,
                                    shapes = itemShapes,
                                    context = context,
                                    isSelectedProvider = { chat.stableKey in selectedKeys },
                                    onCheckedChange = { checked ->
                                        selectedKeys = if (checked) {
                                            selectedKeys + chat.stableKey
                                        } else {
                                            selectedKeys - chat.stableKey
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            val confirmHeight = ButtonDefaults.MediumContainerHeight
            Button(
                onClick = {
                    val chosen = allSelectableChats.filter { it.stableKey in selectedKeys }
                    onConfirmSelected(chosen)
                },
                enabled = totalSelectedCount > 0,
                shapes = ButtonDefaults.shapesFor(confirmHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(confirmHeight),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .heightIn(min = confirmHeight)
            ) {
                Text(
                    text = if (totalSelectedCount > 0) "Thêm $totalSelectedCount tài khoản đã chọn"
                    else "Chọn tài khoản để thêm",
                    style = ButtonDefaults.textStyleFor(confirmHeight)
                )
            }
        }
    }
}

@Composable
private fun ScannedChatSelectableRow(
    chat: ScannedChat,
    shapes: androidx.compose.material3.ListItemShapes,
    context: Context,
    isSelectedProvider: () -> Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val isSelected = isSelectedProvider()
    SegmentedListItem(
        checked = isSelected,
        onCheckedChange = onCheckedChange,
        shapes = shapes,
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedSupportingContentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f),
            selectedTrailingContentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        leadingContent = {
            FriendMaskedAvatar(
                avatarUrl = chat.avatarUrl,
                label = chat.displayName,
                isGroup = chat.isGroup,
                enabled = true,
                context = context,
                size = 42.dp
            )
        },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (chat.isGroup) "Nhóm chat" else "@${chat.username}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                StreakDaysBadge(
                    streakDays = chat.streakDays,
                    litToday = chat.streakLitToday
                )
            }
        },
        trailingContent = {
            Checkbox(
                checked = isSelected,
                onCheckedChange = null
            )
        }
    ) {
        Text(
            text = chat.displayName,
            style = if (isSelected) MaterialTheme.typography.titleSmallEmphasized
            else MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AddFriendOrGroupDialog(
    isGroup: Boolean,
    onDismiss: () -> Unit,
    onSaveFriend: (String, String) -> String?,
    onSaveGroup: (String) -> String?
) {
    var primaryInput by remember { mutableStateOf("") }
    var displayNameInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isGroup) "Thêm nhóm chat TikTok" else "Thêm bạn bè TikTok") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = primaryInput,
                    onValueChange = {
                        primaryInput = it
                        errorMsg = null
                    },
                    label = { Text(if (isGroup) "Tên nhóm chat (khớp tiêu đề chat)" else "Username TikTok") },
                    placeholder = { Text(if (isGroup) "VD: Hội cú đêm" else "VD: username_tiktok") },
                    singleLine = true,
                    isError = errorMsg != null,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isGroup) {
                    OutlinedTextField(
                        value = displayNameInput,
                        onValueChange = { displayNameInput = it },
                        label = { Text("Tên gợi nhớ (tùy chọn)") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                errorMsg?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    errorMsg = if (isGroup) {
                        onSaveGroup(primaryInput)
                    } else {
                        onSaveFriend(primaryInput, displayNameInput)
                    }
                },
                shapes = ButtonDefaults.shapes()
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

@Composable
private fun EditEntryDialog(
    entry: FriendConfig,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(entry) { mutableStateOf(entry.displayName.ifBlank { entry.username }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry.isGroup) "Sửa tên nhóm chat" else "Sửa tên hiển thị") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = {
                    Text(
                        if (entry.isGroup) "Tên khớp tiêu đề nhóm trên TikTok"
                        else "Tên gợi nhớ cho @${entry.username}"
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank() || !entry.isGroup) onConfirm(text) },
                shapes = ButtonDefaults.shapes()
            ) {
                Text("Cập nhật")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}
