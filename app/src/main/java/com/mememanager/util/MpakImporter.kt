package com.mememanager.util

import android.content.Context
import android.net.Uri
import com.mememanager.data.local.entity.MediaEntity
import com.mememanager.data.local.entity.MediaType
import com.mememanager.data.local.entity.StorageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.DigestInputStream
import java.security.MessageDigest

/**
 * .mpak 导入器（export-format.md 规范）
 *
 * 校验流程：
 * 1. 魔数 "MPAK" + 版本号
 * 2. 尾部 SHA-256 比对（除最后 32B 外的全部内容）
 * 3. 逐条三层校验：定界安全（declaredLen vs 剩余字节）、文件名交叉验证、size 交叉验证
 * 提取：写入应用媒体目录（PRIVATE），重名自动加后缀
 */
object MpakImporter {

    private val MAGIC = byteArrayOf(0x4D, 0x50, 0x41, 0x4B) // "MPAK"
    private const val VERSION: Int = 1
    private const val MEDIA_DIR = "media"

    /** 解析出的一条媒体（尚未入库） */
    data class ParsedMedia(
        val entity: MediaEntity,
        val tagNames: List<String>
    )

    data class ImportResult(
        val items: List<ParsedMedia> = emptyList(),
        val failed: List<String> = emptyList()
    ) {
        val success: Int get() = items.size
        val skipped: Int get() = 0
    }

    /**
     * 导入单个 .mpak 分片
     * @param onProgress (当前媒体序号, 本片媒体总数)
     */
    suspend fun importMpak(
        context: Context,
        uri: Uri,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): ImportResult = withContext(Dispatchers.IO) {
        val cr = context.contentResolver

        // 文件总长（用于定界安全）
        val totalLength = cr.openAssetFileDescriptor(uri, "r")?.length ?: -1L

        cr.openInputStream(uri)?.use { raw ->
            val input = BufferedInputStream(raw)
            val digest = MessageDigest.getInstance("SHA-256")
            val din = DigestInputStream(input, digest)

            // ── 1. 文件头 ──
            val header = ByteArray(14)
            readFully(din, header)
            if (!header.copyOfRange(0, 4).contentEquals(MAGIC)) {
                return@withContext ImportResult(failed = listOf("不是有效的 .mpak 文件（魔数不匹配）"))
            }
            val version = readShortBE(header, 4)
            if (version != VERSION) {
                return@withContext ImportResult(failed = listOf("不支持的版本 v$version"))
            }
            val mediaCount = readIntBE(header, 6)
            val jsonLen = readIntBE(header, 10)

            // ── 2. JSON 段 ──
            val jsonBytes = ByteArray(jsonLen)
            readFully(din, jsonBytes)
            val root = try {
                JSONObject(String(jsonBytes, Charsets.UTF_8))
            } catch (e: Exception) {
                return@withContext ImportResult(failed = listOf("元数据 JSON 解析失败"))
            }
            if (root.optString("format") != "memeManager") {
                return@withContext ImportResult(failed = listOf("format 标识不匹配"))
            }
            val mediaArray = root.optJSONArray("media") ?: JSONArray()

            // ── 3. 媒体数据段 ──
            var offset = 14L + jsonLen
            val parsed = mutableListOf<ParsedMedia>()
            val failed = mutableListOf<String>()

            for (i in 0 until mediaArray.length()) {
                onProgress(i, mediaArray.length())
                val meta = mediaArray.optJSONObject(i) ?: continue
                try {
                    // 文件名
                    val nameLen = readShortBE(readNB(din, 2), 0)
                    val fileName = String(readNB(din, nameLen), Charsets.UTF_8)
                    offset += 2 + nameLen
                    // 内容长度（declaredLen）
                    val declaredLen = readLongBE(readNB(din, 8), 0)
                    offset += 8

                    // 定界安全：declaredLen 不能超过剩余内容字节（总长 - 当前偏移 - 尾部 32B）
                    val remaining = totalLength - offset - 32
                    if (declaredLen < 0 || declaredLen > remaining) {
                        failed += fileName
                        // 跳过损坏条目：无法可靠定位下一条，直接放弃本片剩余部分
                        break
                    }

                    // 交叉验证：文件名、size 与 JSON 一致
                    if (meta.optString("fileName") != fileName) {
                        failed += fileName
                        // 结构错位，无法继续
                        break
                    }
                    val jsonSize = meta.optLong("size", -1L)
                    if (jsonSize != declaredLen) {
                        failed += fileName
                        // 长度不符，无法继续
                        break
                    }

                    // 提取：写入应用媒体目录（重名自动加后缀）
                    val destFile = uniqueFile(File(context.filesDir, MEDIA_DIR), fileName)
                    FileOutputStream(destFile).use { out ->
                        copyN(din, out, declaredLen)
                    }
                    offset += declaredLen

                    // 构建实体
                    val entity = MediaEntity(
                        name = meta.optString("name", fileName),
                        filePath = destFile.absolutePath,
                        type = parseType(meta.optString("type")),
                        size = declaredLen,
                        width = if (meta.isNull("width")) null else meta.optInt("width", 0).takeIf { it > 0 },
                        height = if (meta.isNull("height")) null else meta.optInt("height", 0).takeIf { it > 0 },
                        storageType = StorageType.PRIVATE,
                        description = if (meta.isNull("description")) null else meta.optString("description"),
                        createdAt = meta.optLong("createdAt", System.currentTimeMillis())
                    )
                    val tagNames = meta.optJSONArray("tags")?.let { arr ->
                        (0 until arr.length()).map { arr.optString(it) }
                            .filter { it.isNotBlank() && it != "[已导出]" } // 导入主动移除 [已导出]
                    } ?: emptyList()

                    parsed += ParsedMedia(entity, tagNames)
                } catch (e: Exception) {
                    failed += meta.optString("fileName", "条目$i")
                    break
                }
            }

            // ── 4. 尾部 SHA-256 校验（除最后 32B 外的全部内容） ──
            // 必须先取 digest（此时流已读完媒体段，尚未读尾部 32B）
            val contentHash = digest.digest()
            val tail = ByteArray(32)
            readFully(din, tail)
            if (!contentHash.contentEquals(tail)) {
                return@withContext ImportResult(
                    items = parsed,
                    failed = failed + "文件完整性校验失败（SHA-256 不匹配）"
                )
            }

            return@withContext ImportResult(items = parsed, failed = failed)
        } ?: ImportResult(failed = listOf("无法打开文件"))
    }

    // ── 辅助 ──

    private fun parseType(name: String): MediaType = when (name) {
        "VIDEO" -> MediaType.VIDEO
        "GIF" -> MediaType.GIF
        else -> MediaType.IMAGE
    }

    /** 目标文件去重：已存在则加 _1、_2 后缀 */
    private fun uniqueFile(dir: File, fileName: String): File {
        if (!dir.exists()) dir.mkdirs()
        var dest = File(dir, fileName)
        if (!dest.exists()) return dest
        val dot = fileName.lastIndexOf('.')
        val base = if (dot >= 0) fileName.substring(0, dot) else fileName
        val ext = if (dot >= 0) fileName.substring(dot) else ""
        var counter = 1
        while (dest.exists()) {
            dest = File(dir, "${base}_$counter$ext")
            counter++
        }
        return dest
    }

    private fun readFully(input: java.io.InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) throw java.io.EOFException("文件意外结束")
            off += n
        }
    }

    private fun readNB(input: java.io.InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        readFully(input, buf)
        return buf
    }

    private fun copyN(input: java.io.InputStream, output: java.io.OutputStream, n: Long) {
        var remaining = n
        val buf = ByteArray(64 * 1024)
        while (remaining > 0) {
            val read = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (read < 0) throw java.io.EOFException("文件意外结束")
            output.write(buf, 0, read)
            remaining -= read
        }
    }

    // 大端读取
    private fun readShortBE(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun readIntBE(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or
            ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or
            (b[off + 3].toInt() and 0xFF)

    private fun readLongBE(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 8) {
            v = (v shl 8) or (b[off + i].toLong() and 0xFF)
        }
        return v
    }
}
