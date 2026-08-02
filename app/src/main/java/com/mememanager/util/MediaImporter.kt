package com.mememanager.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.mememanager.data.local.entity.MediaEntity
import com.mememanager.data.local.entity.MediaType
import com.mememanager.data.local.entity.StorageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 媒体导入工具：从 content:// URI 复制到应用目录 + 构建 MediaEntity
 */
object MediaImporter {

    private const val MEDIA_DIR = "media"

    /**
     * 从 URI 导入单张媒体，返回 MediaEntity（不含 id）
     */
    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        storageType: StorageType = StorageType.PRIVATE
    ): MediaEntity = withContext(Dispatchers.IO) {
        val cr = context.contentResolver

        // 查询文件名和大小（部分相册 provider 不支持，拿不到时走 fallback 链）
        var fileName: String? = null
        var fileSize = 0L
        cr.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) fileName = cursor.getString(nameIdx)
                if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
            }
        }

        // 推断媒体类型（mime 优先）
        val mimeType = cr.getType(uri)
        val mediaType = inferType(fileName ?: "", mimeType)

        // 文件名 fallback 链：DISPLAY_NAME → URI 路径尾段（带扩展名才用）→ IMG_时间戳.扩展名
        val finalName = when {
            !fileName.isNullOrBlank() -> fileName!!
            else -> {
                val seg = uri.lastPathSegment
                val pathName = if (!seg.isNullOrBlank() && seg.contains('.')) seg.substringAfterLast('/') else ""
                if (pathName.isNotBlank()) pathName
                else "IMG_${System.currentTimeMillis()}${extensionFor(mediaType)}"
            }
        }

        // 目标文件
        val destDir = when (storageType) {
            StorageType.PRIVATE -> File(context.filesDir, MEDIA_DIR)
            StorageType.PUBLIC -> File(context.getExternalFilesDir(null), MEDIA_DIR)
            StorageType.EXTERNAL -> {
                // 外部索引：不复制文件，直接构建实体
                return@withContext MediaEntity(
                    name = finalName,
                    filePath = uri.toString(),
                    type = mediaType,
                    size = fileSize,
                    storageType = StorageType.EXTERNAL,
                    createdAt = System.currentTimeMillis(),
                    takenTime = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }
        }
        if (!destDir.exists()) destDir.mkdirs()

        // 处理重名
        var destFile = File(destDir, finalName)
        if (destFile.exists()) {
            val dotIndex = finalName.lastIndexOf('.')
            val base = if (dotIndex >= 0) finalName.substring(0, dotIndex) else finalName
            val ext = if (dotIndex >= 0) finalName.substring(dotIndex) else ""
            var counter = 1
            while (destFile.exists()) {
                destFile = File(destDir, "${base}_${counter}$ext")
                counter++
            }
        }

        // 复制文件
        cr.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        val actualSize = if (fileSize <= 0) destFile.length() else fileSize

        MediaEntity(
            name = destFile.name,
            filePath = destFile.absolutePath,
            type = mediaType,
            size = actualSize,
            storageType = storageType,
            createdAt = System.currentTimeMillis(),
            takenTime = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun extensionFor(type: MediaType): String = when (type) {
        MediaType.VIDEO -> ".mp4"
        MediaType.GIF -> ".gif"
        MediaType.IMAGE -> ".jpg"
    }

    private fun inferType(fileName: String, mimeType: String?): MediaType {
        val mime = mimeType ?: ""
        val name = fileName.lowercase()
        return when {
            mime.startsWith("video/") || name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") -> MediaType.VIDEO
            mime == "image/gif" || name.endsWith(".gif") -> MediaType.GIF
            else -> MediaType.IMAGE
        }
    }
}
