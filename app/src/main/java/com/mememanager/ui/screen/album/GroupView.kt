package com.mememanager.ui.screen.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.mememanager.data.local.entity.GroupEntity
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.ui.viewmodel.GroupSection
import com.mememanager.ui.viewmodel.GroupViewModel
import androidx.compose.ui.platform.LocalContext
import java.io.File

/**
 * 组别视图：每个组一个区块（组名 + 横向缩略图行），末尾未分组区块
 */
@Composable
fun GroupView(
    viewModel: GroupViewModel,
    onBack: () -> Unit,
    onMediaClick: (MediaWithTags) -> Unit,
    modifier: Modifier = Modifier
) {
    val sections by androidx.lifecycle.compose.collectAsStateWithLifecycle(viewModel.sections)
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<GroupEntity?>(null) }
    var deletingGroup by remember { mutableStateOf<GroupEntity?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        // 标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Text(
                "组别",
                fontSize = 20.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, "新建组")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        if (sections.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "还没有分组，点右上角 + 新建",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
            ) {
                items(sections, key = { it.group?.id ?: -1L }) { section ->
                    GroupSectionBlock(
                        section = section,
                        onMediaClick = onMediaClick,
                        onEdit = { editingGroup = section.group },
                        onDelete = { deletingGroup = section.group }
                    )
                }
            }
        }
    }

    // ── 新建/重命名弹窗 ──
    if (showCreateDialog || editingGroup != null) {
        val isEdit = editingGroup != null
        val initial = if (isEdit) editingGroup!!.name else ""
        var name by remember { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; editingGroup = null },
            title = { Text(if (isEdit) "重命名组" else "新建组") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("组名…") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            if (isEdit) viewModel.update(editingGroup!!.copy(name = name))
                            else viewModel.create(name, 0xFF607D8B.toInt())
                        }
                        showCreateDialog = false; editingGroup = null
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; editingGroup = null }) { Text("取消") }
            }
        )
    }

    // ── 删除确认弹窗 ──
    deletingGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { deletingGroup = null },
            title = { Text("删除组") },
            text = { Text("确定删除组「${group.name}」？组内媒体将变为未分组，不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(group)
                    deletingGroup = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingGroup = null }) { Text("取消") }
            }
        )
    }
}

/** 单个组区块：标题行 + 横向缩略图行 */
@Composable
private fun GroupSectionBlock(
    section: GroupSection,
    onMediaClick: (MediaWithTags) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val group = section.group
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        // 标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (group != null) Color(group.color) else Color(0xFF9E9E9E))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                group?.name ?: "未分组",
                fontSize = 15.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${section.media.size} 个",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (group != null) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, "重命名", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // 缩略图行
        if (section.media.isEmpty()) {
            Text(
                "（空）",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(section.media, key = { it.media.id }) { mwt ->
                    GroupThumb(mwt = mwt, onClick = { onMediaClick(mwt) })
                }
            }
        }
    }
}

@Composable
private fun GroupThumb(mwt: MediaWithTags, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(File(mwt.media.filePath))
                .crossfade(true)
                .build(),
            contentDescription = mwt.media.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
