package com.mememanager.ui.screen.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun BatchActionBar(
    selectedCount: Int,
    totalCount: Int = 0,
    onCancel: () -> Unit,
    onSelectAll: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allSelected = selectedCount >= totalCount && totalCount > 0
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "取消")
                }
                Text("已选 $selectedCount 项", fontSize = 16.sp)
            }
            Row {
                if (onSelectAll != null) {
                    TextButton(onClick = onSelectAll) {
                        Text(
                            if (allSelected) "取消全选" else "全选",
                            fontSize = 13.sp,
                            color = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (onShare != null) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, "分享", modifier = Modifier.size(22.dp))
                    }
                }
                if (onExport != null) {
                    IconButton(onClick = onExport) {
                        Icon(
                            Icons.Filled.Logout,
                            "导出",
                            modifier = Modifier.size(22.dp).rotate(-90f)
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
