package com.mememanager.ui.screen.tags

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
        var nameError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showNewDialog = false; nameError = null },
            title = { Text("新建标签") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it; nameError = null },
                        placeholder = { Text("标签名称") }, singleLine = true,
                        isError = nameError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (nameError != null) {
                        Text(nameError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isBlank()) return@TextButton
                    if (viewModel.tagExists(newName.trim())) {
                        nameError = "标签「${newName.trim()}」已存在"
                        return@TextButton
                    }
                    viewModel.addTag(newName.trim(), nextColor)
                    showNewDialog = false
                    nameError = null
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showNewDialog = false; nameError = null }) { Text("取消") } }
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
    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = surfaceColor.red < 0.5f && surfaceColor.green < 0.5f && surfaceColor.blue < 0.5f
    Box(
        modifier = Modifier
            .size(36.dp)
            .shadow(elevation = if (selected) 4.dp else 1.dp, shape = CircleShape, clip = false)
            .background(Color(color), CircleShape)
            .then(if (selected) Modifier.border(2.dp, if (!isDark) Color(0xFF1976D2) else Color(0xFF4A90FF), CircleShape).border(4.dp, Color(0xFFFFFFFF), CircleShape) else Modifier.border(2.dp, Color(0xFFFFFFFF), CircleShape))
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
    onConfirm: (TagEntity) -> Unit,
    viewModel: TagsViewModel = hiltViewModel()
) {
    var editName by remember(tag) { mutableStateOf(tag.name) }
    var editColor by remember(tag) { mutableIntStateOf(tag.bgColor) }
    val customHexes by viewModel.customColors.collectAsStateWithLifecycle()
    var colors by remember { mutableStateOf(PRESET_COLORS.toMutableList()) }
    var deleteMode by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }

    // 合并预设 + 自定义颜色
    LaunchedEffect(customHexes) {
        val custom = customHexes.mapNotNull { hex ->
            try { hex.toLong(16).toInt() or 0xFF000000.toInt() } catch (_: Exception) { null }
        }
        colors = (PRESET_COLORS + custom).toMutableList()
    }
    var newColorHex by remember { mutableStateOf("") }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("重置配色") },
            text = { Text("恢复出厂预设颜色？已添加的颜色将丢失。") },
            confirmButton = { TextButton(onClick = { viewModel.resetCustomColors(); showResetConfirm = false }) { Text(
                text = "重置",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            ) } },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("取消") } }
        )
    }

    if (showAddDialog) {
        HsvColorPickerDialog(
            onDismiss = { showAddDialog = false },
            onColorPicked = { color ->
                viewModel.addCustomColor(color)
                showAddDialog = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑标签") },
        confirmButton = {
            FilledTonalButton(onClick = {
                val trimmed = editName.trim()
                if (trimmed.isBlank()) return@FilledTonalButton
                if (trimmed != tag.name && viewModel.tagExists(trimmed)) {
                    editError = "标签「$trimmed」已存在"
                    return@FilledTonalButton
                }
                onConfirm(tag.copy(name = trimmed, bgColor = editColor))
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
                OutlinedTextField(value = editName, onValueChange = { editName = it; editError = null },
                    label = { Text("名称") }, singleLine = true,
                    isError = editError != null,
                    modifier = Modifier.fillMaxWidth())
                if (editError != null) {
                    Text(editError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
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
private fun HsvColorPickerDialog(
    onDismiss: () -> Unit,
    onColorPicked: (Int) -> Unit
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var value by remember { mutableFloatStateOf(1f) }
    var hexText by remember { mutableStateOf("FF0000") }

    fun updateHex() {
        val c = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
        hexText = String.format("%06X", c and 0xFFFFFF)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加颜色") },
        confirmButton = {
            TextButton(onClick = {
                val c = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
                onColorPicked(c)
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 十六进制输入
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { txt ->
                        hexText = txt
                        try {
                            val raw = txt.removePrefix("#").trim()
                            var c = raw.toLong(16).toInt()
                            if (raw.length <= 6) c = c or 0xFF000000.toInt()
                            val hsv = FloatArray(3)
                            android.graphics.Color.RGBToHSV(
                                android.graphics.Color.red(c),
                                android.graphics.Color.green(c),
                                android.graphics.Color.blue(c),
                                hsv
                            )
                            hue = hsv[0]; saturation = hsv[1]; value = hsv[2]
                        } catch (_: Exception) {
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                // 预览色块
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Color(
                                android.graphics.Color.HSVToColor(
                                    floatArrayOf(
                                        hue,
                                        saturation,
                                        value
                                    )
                                )
                            )
                        )
                )

                Spacer(Modifier.height(12.dp))

                // SV 方块
                val hueColor = Color.hsv(hue, 1f, 1f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                saturation = (down.position.x / size.width).coerceIn(0f, 1f)
                                value = (1f - down.position.y / size.height).coerceIn(0f, 1f)
                                updateHex()
                                drag(down.id) { change ->
                                    change.consume()
                                    saturation = (change.position.x / size.width).coerceIn(0f, 1f)
                                    value = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                                    updateHex()
                                }
                            }
                        }
                ) {
                    // 水平渐变：白 → 纯色相
                    drawRect(Brush.horizontalGradient(0f to Color.White, 1f to hueColor))
                    // 垂直渐变：透明 → 黑（multiply）
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black),
                        blendMode = BlendMode.Multiply
                    )
                    // 选中点 — 空心：外白圈 + 内部与方格同色
                    val dotX = saturation * size.width
                    val dotY = (1f - value) * size.height
                    val pickedColor = Color.hsv(hue, saturation, value)
                    drawCircle(pickedColor, 7.dp.toPx(), center = Offset(dotX, dotY))
                    drawCircle(
                        Color.White,
                        8.dp.toPx(),
                        center = Offset(dotX, dotY),
                        style = Stroke(1.5.dp.toPx())
                    )
                }

                Spacer(Modifier.height(10.dp))

                // 色相条
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                hue = (down.position.x / size.width * 360f).coerceIn(0f, 360f)
                                updateHex()
                                drag(down.id) { change ->
                                    change.consume()
                                    hue = (change.position.x / size.width * 360f).coerceIn(0f, 360f)
                                    updateHex()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                    ) {
                        drawRect(
                            Brush.horizontalGradient(
                                0f to Color.Red, 1f / 6 to Color.Yellow,
                                2f / 6 to Color.Green, 3f / 6 to Color.Cyan,
                                4f / 6 to Color.Blue, 5f / 6 to Color.Magenta,
                                1f to Color.Red
                            )
                        )
                        val dotX = hue / 360f * size.width
                        val activeColor = Color.hsv(hue, 1f, 1f)
                        drawCircle(
                            activeColor,
                            6.dp.toPx(),
                            center = Offset(dotX, size.height / 2f)
                        )
                        drawCircle(
                            Color.White,
                            7.dp.toPx(),
                            center = Offset(dotX, size.height / 2f),
                            style = Stroke(2.dp.toPx())
                        )
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
