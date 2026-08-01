package com.mememanager.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mememanager.ui.viewmodel.SettingsViewModel

/**
 * 设置页面
 *
 * 数据来源：SettingsViewModel（DataStore Preferences 持久化）
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "设置",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── 存储 ──
            item {
                SettingsCard(title = "存储") {
                    FilterRow(
                        label = "默认存储类型",
                        options = listOf("私有内部", "公共目录", "外部索引"),
                        selected = settings.storageType,
                        onSelected = { viewModel.setStorageType(it) }
                    )
                    Divider()
                    SliderRow(
                        label = "分片大小",
                        value = settings.shardSizeMB.toFloat(),
                        onValueChange = { viewModel.setShardSizeMB(it.toInt()) },
                        valueRange = 10f..500f,
                        unit = "MB"
                    )
                }
            }

            // ── 显示 ──
            item {
                SettingsCard(title = "显示") {
                    FilterRow(
                        label = "主题模式",
                        options = listOf("浅色", "深色", "跟随系统"),
                        selected = settings.themeMode,
                        onSelected = { viewModel.setThemeMode(it) }
                    )
                    Divider()
                    SliderRow(
                        label = "每行列数",
                        value = settings.gridColumns.toFloat(),
                        onValueChange = { viewModel.setGridColumns(it.toInt()) },
                        valueRange = 3f..5f,
                        steps = 1,
                        unit = "列"
                    )
                }
            }

            // ── 回收站 ──
            item {
                val deletedCount by viewModel.deletedCount.collectAsStateWithLifecycle()
                var showEmptyConfirm by remember { mutableStateOf(false) }
                SettingsCard(title = "回收站") {
                    SliderRow(
                        label = "保留天数",
                        value = settings.trashDays.toFloat(),
                        onValueChange = { viewModel.setTrashDays(it.toInt()) },
                        valueRange = 2f..90f,
                        unit = if (settings.trashDays >= 90) "" else "天",
                        unitOverride = "永不清理"
                    )
                    Divider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("清空回收站", fontSize = 15.sp)
                            if (deletedCount > 0) {
                                Text(
                                    "${deletedCount} 项",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            onClick = { if (deletedCount > 0) showEmptyConfirm = true },
                            enabled = deletedCount > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("立即清空")
                        }
                    }
                    if (showEmptyConfirm) {
                        AlertDialog(
                            onDismissRequest = { showEmptyConfirm = false },
                            title = { Text("清空回收站") },
                            text = { Text("确定永久删除回收站中的 ${deletedCount} 项？此操作不可撤销。") },
                            confirmButton = {
                                TextButton(onClick = { viewModel.emptyTrash(); showEmptyConfirm = false }) {
                                    Text("清空", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = { TextButton(onClick = { showEmptyConfirm = false }) { Text("取消") } }
                        )
                    }
                }
            }

            // ── 数据 ──
            item {
                SettingsCard(title = "数据") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("同步维护 JSON 数据文件", fontSize = 15.sp)
                            Text(
                                "在公共目录生成 JSON，便于手动备份",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.jsonSyncEnabled,
                            onCheckedChange = { viewModel.setJsonSyncEnabled(it) }
                        )
                    }
                }
            }

            // ── 关于 ──
            item {
                SettingsCard(title = "关于") {
                    InfoRow("版本", "1.0.0")
                    Divider()
                    InfoRow("开源许可", "MIT")
                    Divider()
                    InfoRow("GitHub", "dyf189/memeManager")
                }
            }
        }
    }
}

// ── 子组件 ──

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.size(12.dp))
            content()
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
    Spacer(modifier = Modifier.height(5.dp))
}

@Composable
private fun FilterRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { opt ->
                FilterChip(
                    selected = selected == opt,
                    onClick = { onSelected(opt) },
                    label = { Text(opt, fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }
}
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    unit: String,
    unitOverride: String? = null
) {
    var textValue by remember { mutableStateOf(value.toInt().toString()) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(value) }
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    var justCommitted by remember { mutableStateOf(false) }

    // isOverride 始终跟随当前值，不靠手动设置
    val isOverride = unitOverride != null && dragValue >= valueRange.endInclusive

    // 外部值变化且不在拖拽 + 未聚焦 + 刚未提交 → 同步本地状态
    if (!dragging && !isFocused && !justCommitted) {
        textValue = value.toInt().toString()
        dragValue = value
    }
    // 外部值已追上 → 解除防弹
    if (justCommitted && dragValue == value) {
        justCommitted = false
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
            if (unitOverride != null && isOverride) {
                Text(unitOverride, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary, lineHeight = 34.sp)
            } else {
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
                            textStyle = MaterialTheme.typography.bodySmall.copy(
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
            } // Row
        }
        Slider(
            value = if (dragging || justCommitted) dragValue else value,
            onValueChange = {
                dragValue = it
                textValue = it.toInt().toString()
                dragging = true
            },
            onValueChangeFinished = {
                onValueChange(dragValue)
                justCommitted = true // 防回弹：本帧不覆盖 dragValue
                dragging = false
                focusManager.clearFocus()
            },
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 15.sp)
        Text(
            value,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
