package com.mememanager.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val filePath: String,
    val type: MediaType,
    val size: Long,
    val width: Int? = null,
    val height: Int? = null,
    val storageType: StorageType = StorageType.PRIVATE,
    val source: String? = null,
    val description: String? = null,
    val takenTime: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val deletedTime: Long? = null,
    val exportedHash: String? = null
)

enum class MediaType {
    IMAGE, VIDEO, GIF
}

enum class StorageType {
    /** 私有内部存储 */
    PRIVATE,
    /** 公共目录 /storage/emulated/0/EmojiManager/ */
    PUBLIC,
    /** 外部索引，不复制文件 */
    EXTERNAL
}
