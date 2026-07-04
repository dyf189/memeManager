package com.mememanager.ui.screen.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mememanager.data.local.entity.MediaEntity
import com.mememanager.data.local.entity.MediaType
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.data.local.entity.StorageType
import com.mememanager.data.local.entity.TagEntity
import com.mememanager.ui.theme.MemeManagerTheme

/**
 * 媒体详情页
 *
 * 布局：全屏大图 + 底部可展开信息浮层。
 * 描述和标签均可编辑——描述通过弹窗编辑，标签通过 +/- 按钮添加/删除。
 *
 * @param mediaItems 媒体列表（支持 HorizontalPager 左右滑动）
 * @param availableTags 可选标签列表（用于添加标签弹窗）
 * @param initialIndex 初始显示的媒体索引
 * @param onBack 返回回调
 * @param onEdit 编辑回调
 * @param onShare 分享回调
 * @param onUpdateDescription 描述更新回调
 * @param onAddTag 添加标签回调
 * @param onRemoveTag 删除标签回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailScreen(
    mediaItems: List<MediaWithTags> = emptyList(),
    availableTags: List<TagEntity> = emptyList(),
    initialIndex: Int = 0,
    onBack: () -> Unit = {},
    onEdit: (MediaWithTags) -> Unit = {},
    onShare: (MediaWithTags) -> Unit = {},
    onUpdateDescription: (MediaWithTags, String) -> Unit = { _, _ -> },
    onAddTag: (MediaWithTags, TagEntity) -> Unit = { _, _ -> },
    onRemoveTag: (MediaWithTags, TagEntity) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    if (mediaItems.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("无媒体", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (mediaItems.size - 1).coerceAtLeast(0)),
        pageCount = { mediaItems.size }
    )
    val currentMedia = mediaItems[pagerState.currentPage]

    // ── 本地编辑状态 ──
    var isMenuExpanded by remember { mutableStateOf(false) }
    var showDescriptionDialog by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }
    var editingDescription by remember(currentMedia) { mutableStateOf(currentMedia.media.description ?: "") }

    var showBottomSheet by remember { mutableStateOf(false) }
    var isTagDeleteMode by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }

    // 获取当前媒体数据（示例，根据你的实际 ViewModel 调整）
    val currentMediaWithTags by viewModel.mediaWithTags.collectAsStateWithLifecycle()

    // ── 标签操作回调 ──
    val onEnterTagDeleteMode = { isTagDeleteMode = true }
    val onExitTagDeleteMode = { isTagDeleteMode = false }

    val onEditDescription = {
        showEditDialog = true  // 打开编辑描述对话框
    }

    val onAddTagClick = {
        showAddTagDialog = true  // 打开添加标签对话框
    }

    val onRemoveTag: (TagEntity) -> Unit = { tag ->
        viewModel.removeTag(mediaId, tag.id)
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentMedia.media.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(currentMedia) }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                    IconButton(onClick = { onShare(currentMedia) }) {
                        Icon(Icons.Default.Share, contentDescription = "分享")
                    }
                    Box {
                        IconButton(onClick = { isMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("保存到相册") },
                                onClick = { isMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("查看原始信息") },
                                onClick = { isMenuExpanded = false }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val media = mediaItems[page]
                MediaDisplay(media = media)
            }

            if (mediaItems.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${mediaItems.size}",
                    fontSize = 12.sp,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }

            // 在 MediaDetailScreen 顶层
            var showBottomSheet by remember { mutableStateOf(false) }

// 点击底部缩略图或某个按钮时打开
// 例如在图片下方的信息栏点击后：
            Button(onClick = { showBottomSheet = true }) {
                Text("查看详情")
            }

// 显示 ModalBottomSheet
            if (showBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showBottomSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), // 直接全展开，跳过半展开
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    dragHandle = { BottomSheetDefaults.DragHandle() }  // 可拖拽手柄
                ) {
                    MediaBottomSheetContent(
                        media = currentMediaWithTags,
                        isTagDeleteMode = isTagDeleteMode,
                        onEnterTagDeleteMode = onEnterTagDeleteMode,
                        onExitTagDeleteMode = onExitTagDeleteMode,
                        onEditDescription = { /* 打开编辑对话框 */ },
                        onAddTagClick = { /* 打开添加标签 */ },
                        onRemoveTag = { tag -> /* 删除标签 */ }
                    )
                }
            }
        }
    }

    // ── 描述编辑弹窗 ──
    if (showDescriptionDialog) {
        AlertDialog(
            onDismissRequest = { showDescriptionDialog = false },
            title = { Text("编辑描述") },
            text = {
                OutlinedTextField(
                    value = editingDescription,
                    onValueChange = { editingDescription = it },
                    placeholder = { Text("输入描述…") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateDescription(currentMedia, editingDescription)
                    showDescriptionDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDescriptionDialog = false }) { Text("取消") }
            }
        )
    }

    // ── 标签选择弹窗 ──
    if (showTagPicker) {
        val currentTagIds = currentMedia.tags.map { it.id }.toSet()
        AlertDialog(
            onDismissRequest = { showTagPicker = false },
            title = { Text("选择标签") },
            text = {
                val filtered = availableTags.filter { it.id !in currentTagIds }
                if (filtered.isEmpty()) {
                    Text("没有可添加的标签", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column {
                        filtered.forEach { tag ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAddTag(currentMedia, tag)
                                        showTagPicker = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(tag.bgColor))
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(tag.name, fontSize = 15.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTagPicker = false }) { Text("关闭") }
            }
        )
    }
}

// ── 媒体展示区 ──

@Composable
private fun MediaDisplay(media: MediaWithTags) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when (media.media.type) {
                    MediaType.IMAGE -> "🖼️"
                    MediaType.GIF -> "🎞️"
                    MediaType.VIDEO -> "🎬"
                },
                fontSize = 64.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = media.media.name,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = formatSize(media.media.size),
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.4f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaBottomSheetContent(
    media: MediaWithTags,
    isTagDeleteMode: Boolean,
    onEnterTagDeleteMode: () -> Unit,
    onExitTagDeleteMode: () -> Unit,
    onEditDescription: () -> Unit,
    onAddTagClick: () -> Unit,
    onRemoveTag: (TagEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
    ) {
        // 文件名
        Text(
            text = media.media.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))

        // 描述
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "描述",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onEditDescription,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text("编辑", style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(
            text = media.media.description ?: "暂无描述",
            style = MaterialTheme.typography.bodyMedium,
            color = if (media.media.description != null)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        // 标签
        Text(
            "标签",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (media.tags.isEmpty() && !isTagDeleteMode) {
                Text(
                    "暂无标签",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            media.tags.forEach { tag ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(
                            start = 12.dp, end = if (isTagDeleteMode) 4.dp else 12.dp,
                            top = 6.dp, bottom = 6.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tag.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isTagDeleteMode) {
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { onRemoveTag(tag) },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            FilledTonalIconButton(
                onClick = onAddTagClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Add, "添加标签", modifier = Modifier.size(18.dp))
            }

            FilledTonalIconButton(
                onClick = {
                    if (isTagDeleteMode) onExitTagDeleteMode() else onEnterTagDeleteMode()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Text("−", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        // 元数据
        MetaDataItem("来源", media.media.source ?: "未知")
        MetaDataItem("时间", formatDate(media.media.createdAt))
        MetaDataItem("大小", formatSize(media.media.size))
        MetaDataItem("存储", when (media.media.storageType) {
            StorageType.PRIVATE -> "私有内部"
            StorageType.PUBLIC -> "公共目录"
            StorageType.EXTERNAL -> "外部索引"
        })
        if (media.media.width != null && media.media.height != null) {
            MetaDataItem("分辨率", "${media.media.width}×${media.media.height}")
        }
    }
}

@Composable
private fun MetaDataItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── 工具函数 ──

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> "%.1f MB".format(mb)
        kb >= 1.0 -> "%.0f KB".format(kb)
        else -> "$bytes B"
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

// ── Preview ──

@Preview(showBackground = true)
@Composable
fun PreviewMediaDetailScreen() {
    val now = System.currentTimeMillis()
    val allTags = listOf(
        TagEntity(id = 1, name = "开心", bgColor = 0xFFFF9800.toInt(), sortOrder = 0),
        TagEntity(id = 2, name = "爆笑", bgColor = 0xFFF44336.toInt(), sortOrder = 1),
        TagEntity(id = 3, name = "可爱", bgColor = 0xFFE91E63.toInt(), sortOrder = 2),
        TagEntity(id = 4, name = "沙雕", bgColor = 0xFF4CAF50.toInt(), sortOrder = 3),
    )

    var mediaItems by remember {
        mutableStateOf(
            listOf(
                MediaWithTags(
                    media = MediaEntity(
                        id = 1, name = "搞笑猫咪.gif", filePath = "/fake/cat.gif",
                        type = MediaType.GIF, size = 2_345_678,
                        width = 720, height = 1280,
                        storageType = StorageType.PRIVATE,
                        source = "微信", description = "一只超级搞笑的橘猫表情包",
                        createdAt = now - 3 * 3600_000, takenTime = now - 3 * 3600_000, updatedAt = now
                    ),
                    tags = listOf(allTags[0], allTags[1])
                ),
                MediaWithTags(
                    media = MediaEntity(
                        id = 2, name = "沙雕狗子.mp4", filePath = "/fake/dog.mp4",
                        type = MediaType.VIDEO, size = 15_678_900,
                        width = 1080, height = 1920,
                        storageType = StorageType.PUBLIC,
                        source = "QQ", description = null,
                        createdAt = now - 5 * 3600_000, takenTime = now - 5 * 3600_000, updatedAt = now
                    ),
                    tags = emptyList()
                ),
                MediaWithTags(
                    media = MediaEntity(
                        id = 3, name = "熊猫头.jpg", filePath = "/fake/panda.jpg",
                        type = MediaType.IMAGE, size = 512_000,
                        width = 480, height = 480,
                        storageType = StorageType.EXTERNAL,
                        source = null, description = "经典熊猫头，懂的都懂",
                        createdAt = now - 86400_000, takenTime = now - 86400_000, updatedAt = now
                    ),
                    tags = listOf(allTags[2])
                ),
            )
        )
    }

    MemeManagerTheme {
        MediaDetailScreen(
            mediaItems = mediaItems,
            availableTags = allTags,
            initialIndex = 1,
            onUpdateDescription = { media, newDesc ->
                mediaItems = mediaItems.map { item ->
                    if (item.media.id == media.media.id) {
                        item.copy(media = item.media.copy(description = newDesc.ifEmpty { null }))
                    } else item
                }
            },
            onAddTag = { media, tag ->
                mediaItems = mediaItems.map { item ->
                    if (item.media.id == media.media.id && tag !in item.tags) {
                        item.copy(tags = item.tags + tag)
                    } else item
                }
            },
            onRemoveTag = { media, tag ->
                mediaItems = mediaItems.map { item ->
                    if (item.media.id == media.media.id) {
                        item.copy(tags = item.tags.filter { it.id != tag.id })
                    } else item
                }
            }
        )
    }
}
