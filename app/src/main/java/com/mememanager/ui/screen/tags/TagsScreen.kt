package com.mememanager.ui.screen.tags

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mememanager.data.local.entity.TagEntity
import com.mememanager.ui.viewmodel.PRESET_COLORS
import com.mememanager.ui.viewmodel.TagsViewModel
import kotlin.math.roundToInt

@Composable
fun TagsScreen(
    viewModel: TagsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()

    var showNewDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<TagEntity?>(null) }
    var showEditDialog by remember { mutableStateOf<TagEntity?>(null) }
    var newName by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "标签管理器",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { newName = ""; showNewDialog = true }) {
                Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                Text("新建")
            }
            TextButton(onClick = { sortMode = !sortMode }) {
                Text(if (sortMode) "完成" else "排序", fontSize = 14.sp)
            }
        }

        if (tags.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "暂无标签",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp
                )
            }
        } else {
            ReorderableTagList(
                tags = tags,
                sortMode = sortMode,
                onReorder = { viewModel.updateSortOrder(it) },
                onEdit = { showEditDialog = it },
                onDelete = { showDeleteConfirm = it }
            )
        }
    }

    // 新建 — 自动分配颜色
    if (showNewDialog) {
        val nextColor = viewModel.nextPresetColor(tags)
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text("新建标签") },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    placeholder = { Text("标签名称") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.addTag(newName.trim(), nextColor); showNewDialog = false
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showNewDialog = false }) { Text("取消") } }
        )
    }

    // 删除确认
    showDeleteConfirm?.let { tag ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除标签") },
            text = { Text("确定删除「${tag.name}」？已关联的媒体不会受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTag(tag); showDeleteConfirm = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") } }
        )
    }

    // 编辑
    if (showEditDialog != null) {
        EditTagDialog(
            tag = showEditDialog!!,
            onDismiss = { showEditDialog = null },
            onConfirm = { updated -> viewModel.updateTag(updated); showEditDialog = null }
        )
    }
}
// 圆形颜色色块 — 选中时 2dp 蓝色边框
@Composable
private fun ColorChip(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .shadow(elevation = if (selected) 4.dp else 1.dp, shape = CircleShape, clip = false)
            .background(Color(color), CircleShape)
            .then(if (selected) Modifier.border(2.dp, Color(0xFF1976D2), CircleShape).border(4.dp,Color(0xFFFFFFFF), CircleShape) else Modifier.border(2.dp,Color(0xFFFFFFFF), CircleShape))
            .clip(CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
    )
}

// ── 编辑标签弹窗 ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditTagDialog(
    tag: TagEntity,
    onDismiss: () -> Unit,
    onConfirm: (TagEntity) -> Unit
) {
    var editName by remember(tag) { mutableStateOf(tag.name) }
    var editColor by remember(tag) { mutableIntStateOf(tag.bgColor) }
    var colors by remember { mutableStateOf(PRESET_COLORS.toMutableList()) }
    var deleteMode by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var newColorHex by remember { mutableStateOf("") }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("重置配色") },
            text = { Text("恢复出厂预设颜色？已添加的颜色将丢失。") },
            confirmButton = { TextButton(onClick = { colors = PRESET_COLORS.toMutableList(); showResetConfirm = false }) { Text(
                text = "重置",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            ) } },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("取消") } }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加颜色") },
            text = {
                Column {
                    OutlinedTextField(value = newColorHex, onValueChange = { newColorHex = it },
                        placeholder = { Text("#FF5733") }, singleLine = true, label = { Text("十六进制颜色值") })
                    Spacer(Modifier.height(10.dp))
                    Text("或从色板选取", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val palette = listOf(
                            0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5,
                            0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4, 0xFF009688, 0xFF4CAF50,
                            0xFF8BC34A, 0xFFCDDC39, 0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800,
                            0xFFFF5722, 0xFF795548, 0xFF607D8B, 0xFF9E9E9E, 0xFF000000
                        )
                        palette.forEach { c ->
                            ColorChip(c.toInt(), selected = false, onClick = { newColorHex = String.format("#%06X", c and 0xFFFFFF) })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val raw = newColorHex.removePrefix("#").trim()
                        var v = raw.toLong(16).toInt()
                        // 无 Alpha 通道时补上 FF（不透明）
                        if (raw.length <= 6) v = v or 0xFF000000.toInt()
                        if (v !in colors) colors = (colors + v).toMutableList()
                    } catch (_: Exception) {}
                    showAddDialog = false
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑标签") },
        confirmButton = {
            FilledTonalButton(onClick = {
                if (editName.isNotBlank()) onConfirm(tag.copy(name = editName.trim(), bgColor = editColor))
            },colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )) { Text("确定") }
        },
        dismissButton = { FilledTonalButton(
            onClick = onDismiss,
            colors = ButtonDefaults.filledTonalButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) { Text("取消") }},
        text = {
            Column {
                OutlinedTextField(value = editName, onValueChange = { editName = it },
                    label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("颜色", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { deleteMode = !deleteMode }) {
                            Icon(
                                if (deleteMode) Icons.Default.Check else Icons.Default.Delete,
                                contentDescription = if (deleteMode) "完成" else "删除颜色",
                                tint = if (deleteMode) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, "添加颜色", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                        TextButton(onClick = { showResetConfirm = true }) { Text(
                            text = "重置",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        ) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    colors.forEach { c ->
                        Box {
                            ColorChip(c, selected = editColor == c, onClick = { if (!deleteMode) editColor = c })
                            if (deleteMode) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(16.dp).clip(CircleShape).background(Color.Red)
                                        .clickable { colors = colors.toMutableList().also { it.remove(c) } },
                                    contentAlignment = Alignment.Center
                                ) { Text("✕", fontSize = 9.sp, color = Color.White) }
                            }
                        }
                    }
                }
            }
        }
    )
}

// ── 排序 ──

@Composable
private fun ReorderableTagList(
    tags: List<TagEntity>,
    sortMode: Boolean,
    onReorder: (List<TagEntity>) -> Unit,
    onEdit: (TagEntity) -> Unit,
    onDelete: (TagEntity?) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(tags, key = { _, t -> t.id }) { index, tag ->
            TagCard(
                tag = tag,
                sortMode = sortMode,
                onEdit = { onEdit(tag) },
                onDelete = if (tag.isReserved) null else { { onDelete(tag) } },
                onMoveUp = if (index > 0) { {
                    onReorder(tags.toMutableList().apply { add(index - 1, removeAt(index)) })
                } } else null,
                onMoveDown = if (index < tags.lastIndex) { {
                    onReorder(tags.toMutableList().apply { add(index + 1, removeAt(index)) })
                } } else null
            )
        }
    }
}

@Composable
private fun TagCard(
    tag: TagEntity,
    sortMode: Boolean,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(tag.bgColor)))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tag.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (tag.isReserved) Text("系统保留", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
            if (sortMode) {
                IconButton(onClick = onMoveUp ?: {}, enabled = onMoveUp != null, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, "上移",
                        tint = if (onMoveUp != null) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onMoveDown ?: {}, enabled = onMoveDown != null, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, "下移",
                        tint = if (onMoveDown != null) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        modifier = Modifier.size(20.dp))
                }
            }
            if (!sortMode) {
                IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Edit, "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    }
                } else {
                    Icon(Icons.Default.Lock, "保留", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.padding(10.dp).size(18.dp))
                }
            }
        }
    }
}
