package com.mememanager.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mememanager.ui.theme.MemeManagerTheme

/**
 * 设置页面
 *
 * 所有状态均为本地 remember，暂不持久化到 DataStore。
 *
 * 分组：
 * 1. 存储 — 默认存储类型、分片大小
 * 2. 显示 — 主题模式、每行列数
 * 3. 回收站 — 保留天数、清空按钮
 * 4. 数据 — JSON 同步开关
 * 5. 关于 — 版本信息
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    // ── 本地状态 ──
    var storageType by remember { mutableStateOf("私有内部") }
    var shardSizeMB by remember { mutableIntStateOf(100) }

    var themeMode by remember { mutableStateOf("跟随系统") }
    var gridColumns by remember { mutableIntStateOf(3) }

    var trashDays by remember { mutableIntStateOf(30) }

    var jsonSyncEnabled by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("设置") })
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ── 存储 ──
            item { SectionHeader("存储") }

            item {
                SettingRow(label = "默认存储类型") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("私有内部", "公共目录", "外部索引").forEach { opt ->
                            FilterChip(
                                selected = storageType == opt,
                                onClick = { storageType = opt },
                                label = { Text(opt, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }

            item {
                SliderRow(
                    label = "分片大小",
                    value = shardSizeMB.toFloat(),
                    onValueChange = { shardSizeMB = it.toInt() },
                    valueRange = 10f..500f,
                    valueText = "${shardSizeMB} MB"
                )
            }

            // ── 显示 ──
            item { SectionHeader("显示") }

            item {
                SettingRow(label = "主题模式") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("浅色", "深色", "跟随系统").forEach { opt ->
                            FilterChip(
                                selected = themeMode == opt,
                                onClick = { themeMode = opt },
                                label = { Text(opt, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }

            item {
                SliderRow(
                    label = "每行列数",
                    value = gridColumns.toFloat(),
                    onValueChange = { gridColumns = it.toInt() },
                    valueRange = 3f..5f,
                    steps = 1,
                    valueText = "${gridColumns} 列"
                )
            }

            // ── 回收站 ──
            item { SectionHeader("回收站") }

            item {
                SliderRow(
                    label = "保留天数",
                    value = trashDays.toFloat(),
                    onValueChange = { trashDays = it.toInt() },
                    valueRange = 0f..90f,
                    valueText = if (trashDays == 0) "直接删除" else "${trashDays} 天"
                )
            }

            item {
                SettingRow(label = "清空回收站") {
                    Button(
                        onClick = { /* TODO */ },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("立即清空")
                    }
                }
            }

            // ── 数据 ──
            item { SectionHeader("数据") }

            item {
                SwitchRow(
                    label = "同步维护 JSON 数据文件",
                    description = "在公共目录生成 JSON，便于手动备份",
                    checked = jsonSyncEnabled,
                    onCheckedChange = { jsonSyncEnabled = it }
                )
            }

            // ── 关于 ──
            item { SectionHeader("关于") }

            item {
                InfoRow(label = "版本", value = "1.0.0")
            }
            item {
                InfoRow(
                    label = "开源许可",
                    value = "Apache 2.0"
                )
            }
        }
    }
}

// ── 子组件 ──

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingRow(
    label: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        content()
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueText: String
) {
    SettingRow(label = label) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp)
            Text(
                description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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

// ── Preview ──

@Preview(showBackground = true)
@Composable
private fun PreviewSettingsScreen() {
    MemeManagerTheme {
        SettingsScreen()
    }
}
