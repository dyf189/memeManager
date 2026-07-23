package com.mememanager.ui.screen.tags

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mememanager.data.local.entity.TagEntity
import com.mememanager.ui.viewmodel.PRESET_COLORS
import com.mememanager.ui.viewmodel.TagsViewModel
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min

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

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("标签管理器", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { newName = ""; showNewDialog = true }) {
                Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                Text("新建")
            }
        }

        if (tags.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无标签", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            }
        } else {
            ReorderableTagList(
                tags = tags,
                onReorder = { viewModel.updateSortOrder(it) },
                onEdit = { showEditDialog = it },
                onDelete = { showDeleteConfirm = it }
            )
        }
    }

    // 新建
    if (showNewDialog) {
        val nextColor = viewModel.nextPresetColor(tags)
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text("新建标签") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it },
                    placeholder = { Text("标签名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.addTag(newName.trim(), nextColor)
                        showNewDialog = false
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
                TextButton(onClick = { viewModel.deleteTag(tag); showDeleteConfirm = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") } }
        )
    }

    // 编辑
    showEditDialog?.let { tag ->
        var editName by remember(tag) { mutableStateOf(tag.name) }
        var editColor by remember(tag) { mutableIntStateOf(tag.bgColor) }
        AlertDialog(
            onDismissRequest = { showEditDialog = null },
            title = { Text("编辑标签") },
            text = {
                Column {
                    OutlinedTextField(value = editName, onValueChange = { editName = it },
                        label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text("颜色", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PRESET_COLORS.forEachIndexed { i, c ->
                            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                                .then(if (editColor == c) Modifier.padding(3.dp).clip(CircleShape).background(Color(c))
                                else Modifier)
                                .pointerInput(c) { awaitPointerEventScope { awaitPointerEvent(); editColor = c } }
                            ) {
                                if (editColor == c) Box(Modifier.fillMaxSize().then(Modifier), contentAlignment = Alignment.Center) {} else Unit
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editName.isNotBlank()) {
                        viewModel.updateTag(tag.copy(name = editName.trim(), bgColor = editColor))
                        showEditDialog = null
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = null }) { Text("取消") } }
        )
    }
}

// ── 拖拽排序 ──

@Composable
private fun ReorderableTagList(
    tags: List<TagEntity>,
    onReorder: (List<TagEntity>) -> Unit,
    onEdit: (TagEntity) -> Unit,
    onDelete: (TagEntity?) -> Unit
) {
    val listState = rememberLazyListState()
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var itemHeight by remember { mutableIntStateOf(80) } // 估算高度，onSizeChanged 精确

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(tags, key = { _, t -> t.id }) { index, tag ->
            val isDragging = index == dragIndex
            val animatedOffset by animateDpAsState(
                if (isDragging) dragOffset.dp else 0.dp,
                animationSpec = spring(stiffness = 300f),
                label = "drag"
            )
            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .offset { IntOffset(0, if (isDragging) animatedOffset.roundToPx() else 0) }
                    .onSizeChanged { itemHeight = it.height }
            ) {
                TagCard(
                    tag = tag,
                    onEdit = { onEdit(tag) },
                    onDelete = if (tag.isReserved) null else { { onDelete(tag) } },
                    onDragStart = { dragIndex = index },
                    onDrag = { delta ->
                        dragOffset += delta
                        // 计算目标位置 + 交换
                        val targetIndex = (index + (dragOffset / itemHeight).roundToInt())
                            .coerceIn(0, tags.size - 1)
                        if (targetIndex != index && targetIndex != dragIndex) {
                            val reordered = tags.toMutableList()
                            reordered.removeAt(index)
                            reordered.add(targetIndex, tag)
                            onReorder(reordered)
                            dragOffset = 0f
                            dragIndex = targetIndex
                        }
                    },
                    onDragEnd = {
                        dragIndex = -1
                        dragOffset = 0f
                    }
                )
            }
        }
    }
}

@Composable
private fun TagCard(
    tag: TagEntity,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
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
            // 拖拽手柄 — 长按触发拖拽
            Text(
                text = "≡",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .padding(end = 10.dp)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    }
            )
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(tag.bgColor)))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tag.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (tag.isReserved) Text("系统保留", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
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
