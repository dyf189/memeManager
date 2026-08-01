package com.mememanager.ui.screen.album

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 一片的打包结果 */
data class ShardInfo(
    val mediaCount: Int,
    val totalBytes: Long
)

/**
 * 分片算法（export-format.md §6.1）
 * 片大小 = 14（文件头） + JSON 段长度（估算） + Σ（2 + fileNameLen + 8 + fileSize）
 */
fun computeShards(mediaSizes: List<Long>, maxSizeBytes: Long): List<ShardInfo> {
    val shards = mutableListOf<ShardInfo>()
    val headerBytes = 14L
    val jsonEstimate = 4 * 1024L          // 起始 JSON 段估算（约 4KB）
    val fileNameLen = 14L                 // "meme_0001.jpg" 固定长度估算
    var current = mutableListOf<Long>()
    var used = headerBytes + jsonEstimate

    for (size in mediaSizes) {
        val itemSize = 2L + fileNameLen + 8L + size
        // 当前片非空且装不下 → 结算当前片，开新片（used 重置）
        if (current.isNotEmpty() && used + itemSize > maxSizeBytes) {
            shards += ShardInfo(current.size, used)
            current = mutableListOf()
            used = headerBytes + jsonEstimate
        }
        // 超大单文件单独成片（允许超限）
        if (current.isEmpty() && itemSize > maxSizeBytes) {
            shards += ShardInfo(1, headerBytes + jsonEstimate + itemSize)
            used = headerBytes + jsonEstimate
            continue
        }
        current += size
        used += itemSize
    }
    if (current.isNotEmpty()) shards += ShardInfo(current.size, used)
    return shards
}

/** 字节数格式化：B / KB / MB / GB */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}

/**
 * 导出预览弹窗：选择分片大小，实时计算分片方案
 * @param mediaSizes 选中媒体的字节大小列表（顺序即导出顺序）
 * @param initialShardSizeMB 设置页的分片大小（作为默认值，弹窗内调整不写回设置）
 */
@Composable
fun ExportDialog(
    mediaCount: Int,
    mediaSizes: List<Long>,
    initialShardSizeMB: Int,
    onDismiss: () -> Unit
) {
    var shardSizeMB by remember { mutableIntStateOf(initialShardSizeMB) }
    val shards = remember(mediaSizes, shardSizeMB) {
        computeShards(mediaSizes, shardSizeMB * 1024L * 1024L)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
        ) {
            // 标题
            Text("导出", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "共 $mediaCount 个媒体 · 将分成 ${shards.size} 片",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(16.dp))

            // 分片大小滑动条（复刻设置页样式，仅本地状态）
            ExportSliderRow(
                label = "分片大小",
                value = shardSizeMB.toFloat(),
                onValueChange = { shardSizeMB = it.toInt() },
                valueRange = 10f..500f,
                unit = "MB"
            )

            Spacer(Modifier.height(12.dp))

            HorizontalDivider()

            // 分片结果列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(shards) { index, shard ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "第 ${index + 1} 片",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${shard.mediaCount} 个媒体",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            formatBytes(shard.totalBytes),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 底部按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                TextButton(onClick = onDismiss) {
                    Text("导出", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** 复刻设置页 SliderRow（弹窗内简化版：无持久化、无防回弹逻辑） */
@Composable
private fun ExportSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    unit: String
) {
    var textValue by remember { mutableStateOf(value.toInt().toString()) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(value) }
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    if (!dragging && !isFocused) {
        textValue = value.toInt().toString()
        dragValue = value
    }

    Column(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                focusManager.clearFocus()
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(58.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = textValue,
                        onValueChange = { raw ->
                            val filtered = raw.filter { it.isDigit() }
                            textValue = filtered
                            val num = filtered.toIntOrNull()
                            if (num != null) {
                                val coerced = num.toFloat().coerceIn(valueRange)
                                dragValue = coerced
                                onValueChange(coerced)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
                        textStyle = TextStyle(
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = unit,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Slider(
            value = if (dragging) dragValue else value,
            onValueChange = {
                dragValue = it
                textValue = it.toInt().toString()
                dragging = true
            },
            onValueChangeFinished = {
                onValueChange(dragValue)
                dragging = false
                focusManager.clearFocus()
            },
            valueRange = valueRange,
            steps = steps
        )
    }
}
