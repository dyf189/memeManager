package com.mememanager.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val bgColor: Int = 0xFFCCCCCC.toInt(),
    val isReserved: Boolean = false,
    val sortOrder: Int = 0
)
