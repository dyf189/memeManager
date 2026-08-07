package com.mememanager.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.mememanager.data.local.entity.MediaEntity
import com.mememanager.data.local.entity.MediaType
import java.io.File

/**
 * 分享工具：系统分享 + 保存到系统相册
 */
object ShareUtil {

    /** 系统分享 — FileProvider 生成 content URI；EXTERNAL 媒体直接用原 URI */
    fun shareMedia(context: Context, media: MediaEntity) {
        val uri: Uri
        if (media.filePath.startsWith("content://")) {
            uri = Uri.parse(media.filePath)
        } else {
            val file = File(media.filePath)
            if (!file.exists()) {
                Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
                return
            }
            uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val mime = when (media.type) {
            MediaType.VIDEO -> "video/*"
            MediaType.GIF -> "image/gif"
            else -> "image/*"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享到"))
    }

    /** 保存到系统相册 */
    fun saveToGallery(context: Context, media: MediaEntity) {
        val resolver = context.contentResolver
        val input = if (media.filePath.startsWith("content://")) {
            resolver.openInputStream(Uri.parse(media.filePath))
        } else {
            val f = File(media.filePath)
            if (!f.exists()) null else f.inputStream()
        }
        if (input == null) {
            Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
            return
        }
        val mime = when (media.type) {
            MediaType.VIDEO -> "video/*"
            MediaType.GIF -> "image/gif"
            else -> "image/*"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, media.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MemeManager")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            resolver.openOutputStream(it)?.use { out ->
                input.use { it.copyTo(out) }
            }
            Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
        } ?: Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
    }
}
