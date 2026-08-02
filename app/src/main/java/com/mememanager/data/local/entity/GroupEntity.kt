package com.mememanager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 组别：每个媒体只能属于 0 或 1 个组（与标签的多对多不同）
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: Int = 0xFF607D8B.toInt(),
    val sortOrder: Int = 0
)
