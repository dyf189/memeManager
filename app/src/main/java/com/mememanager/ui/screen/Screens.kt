package com.mememanager.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// ── 导出 AlbumScreen 供 MainActivity 使用 ──
// AlbumScreen 已在 ui/screen/album/ 包下实现
// 这个文件保留 TagsScreen 和 SettingsScreen 占位

@Composable
fun TagsScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "标签",
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
fun SettingsScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
