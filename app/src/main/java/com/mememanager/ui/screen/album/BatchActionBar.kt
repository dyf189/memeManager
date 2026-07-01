package com.mememanager.ui.screen.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 批量操作栏（多选模式顶部栏）
 *
 * ← 取消    已选 N 项    🏷️  📤  🗑️  📤
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchActionBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onTag: () -> Unit = {},
    onExport: () -> Unit = {},
    onDelete: () -> Unit = {},
    onShare: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text("已选 $selectedCount 项", fontSize = 16.sp)
        },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "取消"
                )
            }
        },
        actions = {
            IconButton(onClick = onTag) {
                Icon(Icons.Default.Label, contentDescription = "打标签")
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Default.UploadFile, contentDescription = "导出")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.IosShare, contentDescription = "分享")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = modifier
    )
}
