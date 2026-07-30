package com.mememanager.ui.screen.album

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mememanager.ui.util.TimeGroup

@Composable
fun TimeGroupHeader(
    group: TimeGroup,
    isMultiSelectMode: Boolean = false,
    isGroupSelected: Boolean = false,
    onGroupToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isMultiSelectMode && onGroupToggle != null) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        .then(
                            if (isGroupSelected) Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            else Modifier
                        )
                        .clickable { onGroupToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isGroupSelected) {
                        Icon(Icons.Default.Check, "已选", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = group.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
