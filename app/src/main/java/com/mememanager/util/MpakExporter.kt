package com.mememanager.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.ui.screen.album.ShardInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * .mpak 打包器（export-format.md 规范）
 *
 * 文件布局：
 * ┌ 文件头 14B：魔数 "MPAK" + 版本 0x0001 + 媒体数量(4B) + JSON 长度(4B)
 * ├ JSON 段：UTF-8，media 数组
 * ├ 媒体数据段：每条 = 文件名长度(2B) + 文件名 + 内容长度(8B) + 内容
 * └ 尾部：以上全部内容 SHA-256（32B）
 * 全部多字节整数一律大端（网络字节序）。
 */
object MpakExporter {

    private val MAGIC = byteArrayOf(0x4D, 0x50, 0x41, 0x4B) // "MPAK"
    private const val VERSION: Int = 1

    /** 导出结果 */
    data class ExportResult(
        val successCount: Int,
        val failedShards: List<String>
    ) {
        val allSuccess: Boolean get() = failedShards.isEmpty()
    }

    /**
     * 导出所有分片到 SAF 目录
     * @param treeUri 已持久授权的目录 tree URI（设置页选择）
     * @param onProgress (当前片序号, 总片数)
     */
    suspend fun exportShards(
        context: Context,
        shards: List<ShardInfo>,
        treeUri: String,
        onProgress: (Int, Int) -> Unit
    ): ExportResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
            ?: return@withContext ExportResult(0, listOf("导出目录无效，请重新设置"))

        var success = 0
        val failed = mutableListOf<String>()

        shards.forEachIndexed { index, shard ->
            onProgress(index, shards.size)
            val fileName = String.format("memeManager_%03d.mpak", index + 1)
            val file = root.createFile("application/octet-stream", fileName)
            if (file == null) {
                failed += fileName
                return@forEachIndexed
            }
            try {
                context.contentResolver.openOutputStream(file.uri)?.use { out ->
                    writeShard(BufferedOutputStream(out), shard, index)
                }
                success++
            } catch (e: Exception) {
                failed += fileName
            }
        }
        onProgress(shards.size, shards.size)
        ExportResult(success, failed)
    }

    /** 写一个分片：文件头 + JSON + 媒体二进制 + 尾部哈希 */
    private fun writeShard(out: BufferedOutputStream, shard: ShardInfo, shardIndex: Int) {
        // 1. 构建 JSON 段（同时确定每条的安全文件名）
        val entries = shard.media.mapIndexed { i, mwt ->
            val ext = safeExtension(mwt.media.filePath)
            val safeName = String.format("meme_%04d%s", i + 1, ext)
            ExportEntry(mwt, safeName)
        }
        val jsonBytes = buildJson(entries, shardIndex).toString().toByteArray(Charsets.UTF_8)

        // 2. DigestOutputStream：所有写入自动喂 SHA-256，最后取摘要即尾部哈希
        val digest = MessageDigest.getInstance("SHA-256")
        val digestOut = java.security.DigestOutputStream(out, digest)

        // 3. 文件头（全大端）
        digestOut.write(MAGIC)
        digestOut.write(shortBE(VERSION))
        digestOut.write(intBE(entries.size))
        digestOut.write(intBE(jsonBytes.size))

        // 4. JSON 段
        digestOut.write(jsonBytes)

        // 5. 媒体数据段
        for (entry in entries) {
            val nameBytes = entry.safeName.toByteArray(Charsets.UTF_8)
            digestOut.write(shortBE(nameBytes.size))
            digestOut.write(nameBytes)
            val size = File(entry.mwt.media.filePath).length()
            digestOut.write(longBE(size))
            FileInputStream(entry.mwt.media.filePath).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = bis.read(buf)
                        if (n < 0) break
                        digestOut.write(buf, 0, n)
                    }
                }
            }
        }
        digestOut.flush()

        // 6. 尾部 32B：以上全部内容的 SHA-256
        out.write(digest.digest())
        out.flush()
    }

    private fun buildJson(entries: List<ExportEntry>, shardIndex: Int): JSONObject {
        val mediaArray = JSONArray()
        entries.forEach { entry ->
            val m = entry.mwt.media
            val obj = JSONObject()
            obj.put("fileName", entry.safeName)
            obj.put("name", m.name)
            obj.put("type", m.type.name)
            obj.put("size", m.size)
            obj.put("sha256", sha256Hex(File(m.filePath)))
            obj.put("width", m.width)
            obj.put("height", m.height)
            obj.put("description", m.description ?: JSONObject.NULL)
            obj.put("createdAt", m.createdAt)
            val tags = JSONArray()
            entry.mwt.sortedTags.forEach { tags.put(it.name) }
            obj.put("tags", tags)
            mediaArray.put(obj)
        }
        return JSONObject().apply {
            put("format", "memeManager")
            put("version", VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("media", mediaArray)
        }
    }

    /** 单个媒体文件 SHA-256（十六进制小写） */
    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            BufferedInputStream(fis).use { bis ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = bis.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** 从文件路径提取安全扩展名（小写，仅字母数字，最长 5 字符，含点） */
    private fun safeExtension(path: String): String {
        val dot = path.lastIndexOf('.')
        if (dot < 0 || dot == path.length - 1) return ".bin"
        val ext = path.substring(dot + 1).lowercase().filter { it.isLetterOrDigit() }
        return if (ext.isEmpty()) ".bin" else "." + ext.take(5)
    }

    private data class ExportEntry(val mwt: MediaWithTags, val safeName: String)

    // ── 大端序列化 ──
    private fun shortBE(v: Int): ByteArray = byteArrayOf(
        ((v shr 8) and 0xFF).toByte(),
        (v and 0xFF).toByte()
    )

    private fun intBE(v: Int): ByteArray = byteArrayOf(
        ((v shr 24) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        (v and 0xFF).toByte()
    )

    private fun longBE(v: Long): ByteArray = byteArrayOf(
        ((v shr 56) and 0xFF).toByte(),
        ((v shr 48) and 0xFF).toByte(),
        ((v shr 40) and 0xFF).toByte(),
        ((v shr 32) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        (v and 0xFF).toByte()
    )
}
