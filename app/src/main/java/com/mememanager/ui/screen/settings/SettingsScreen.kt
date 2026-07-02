package com.mememanager.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mememanager.ui.theme.MemeManagerTheme

/**
 * 设置页面
 *
 * 分组：
 * 1. 存储 — 默认存储类型、分片大小
 * 2. 显示 — 主题模式、每行列数
 * 3. 回收站 — 保留天数、清空按钮
 * 4. 数据 — JSON 同步开关
 * 5. 关于 — 版本信息
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var storageType by remember { mutableStateOf("私有内部") }
    var shardSizeMB by remember { mutableIntStateOf(100) }
    var themeMode by remember { mutableStateOf("跟随系统") }
    var gridColumns by remember { mutableIntStateOf(3) }
    var trashDays by remember { mutableIntStateOf(30) }
    var jsonSyncEnabled by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
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
                        selected = storageType,
                        onSelected = { storageType = it }
                    )
                    Divider()
                    SliderRow(
                        label = "分片大小",
                        value = shardSizeMB.toFloat(),
                        onValueChange = { shardSizeMB = it.toInt() },
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
                        selected = themeMode,
                        onSelected = { themeMode = it }
                    )
                    Divider()
                    SliderRow(
                        label = "每行列数",
                        value = gridColumns.toFloat(),
                        onValueChange = { gridColumns = it.toInt() },
                        valueRange = 3f..5f,
                        steps = 1,
                        unit = "列"
                    )
                }
            }

            // ── 回收站 ──
            item {
                SettingsCard(title = "回收站") {
                    SliderRow(
                        label = "保留天数",
                        value = trashDays.toFloat(),
                        onValueChange = { trashDays = it.toInt() },
                        valueRange = 0f..90f,
                        unit = "天"
                    )
                    Divider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("清空回收站", fontSize = 15.sp)
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
                        Switch(checked = jsonSyncEnabled, onCheckedChange = { jsonSyncEnabled = it })
                    }
                }
            }

            // ── 关于 ──
            item {
                SettingsCard(title = "关于") {
                    InfoRow("版本", "1.0.0")
                    Divider()
                    InfoRow("开源许可", "Apache 2.0")
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
    unit: String
) {
    var textValue by remember { mutableStateOf(value.toInt().toString()) }
    val intValue = value.toInt()
    if (intValue.toString() != textValue) {
        textValue = intValue.toString()
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 紧凑输入框（34dp 高）
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
                                onValueChange(num.toFloat().coerceIn(valueRange))
                            }
                        },
                        singleLine = true,
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
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
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

// ── Preview ──

@Preview(showBackground = true)
@Composable
private fun PreviewSettingsScreen() {
    MemeManagerTheme {
        SettingsScreen()
    }
}
