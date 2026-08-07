package com.mememanager.util

import com.huaban.analysis.jieba.JiebaSegmenter

/**
 * 结巴分词 + FTS5 MATCH 查询构建
 *
 * FTS5 unicode61 tokenizer 对中文逐字拆 token。
 * 中文多字词用 FTS5 phrase 查询（双引号包裹），
 * 例："好的" 匹配索引中相邻的 "好" → "的" token。
 */
object JiebaTokenizer {

    private val segmenter by lazy { JiebaSegmenter() }

    /** 确保词典已加载（在 IO 线程执行，未加载则阻塞等待）——所有调用点先调它 */
    suspend fun ensureLoaded() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        segmenter // 触发 lazy 初始化（首次加载词典约 10 秒）
    }

    /** 后台预热：触发词典加载，避免首次搜索阻塞 */
    fun warmUp() {
        segmenter.process("预热", JiebaSegmenter.SegMode.SEARCH)
    }

    /**
     * 用户输入 → FTS5 MATCH 布尔表达式
     *
     * 中文多字词用双引号包裹做 phrase 查询——FTS5 unicode61 逐字分词后，
     * "好的" 匹配索引中相邻的 "好" → "的" token。
     *
     * 例："卡住了" → "\"卡住\" OR \"了\""
     */
    fun toFtsQuery(query: String): String {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return ""

        // 单字（只有 1 个 Unicode 字符）→ 直接匹配，不调 Jieba
        val codePoints = trimmed.codePointCount(0, trimmed.length)
        if (codePoints == 1) return "\"$trimmed\""

        // 包含空格 → 按空格拆开，各自分词后 OR 合并
        if (trimmed.contains(" ")) {
            val parts = trimmed.split(" ").filter { it.isNotBlank() }
            if (parts.size > 1) {
                return parts.joinToString(" OR ") { part ->
                    val words = segmentRaw(part)
                    if (words.isEmpty()) "\"$part\""
                    else words.joinToString(" OR ") { "\"$it\"" }
                }
            }
        }

        // 常规多字词 → Jieba 分词 + phrase 查询
        val words = segmentRaw(trimmed)
        if (words.isEmpty()) return "\"$trimmed\""
        return words.joinToString(" OR ") { "\"$it\"" }
    }

    /** 内部分词：去重 + 过滤 */ 
    private fun segmentRaw(text: String): List<String> {
        return segmenter.process(text, JiebaSegmenter.SegMode.SEARCH)
            .filter { it.word.isNotBlank() }
            .map { it.word.trim() }
            .filter { w -> w.length >= 2 || w.any { it.code > 127 } }
            .distinct()
    }

    /**
     * 用户输入 → 分词列表（用于 UI 高亮）
     */
    fun segment(query: String): List<String> = segmentRaw(query)

    /**
     * 文本 → FTS 存储内容（Jieba 分词后用空格拼接）
     *
     * 例："卡住了好" → "卡住 了 好"
     */
    fun toFtsContent(text: String): String {
        if (text.isBlank()) return ""
        return segmenter.process(text, JiebaSegmenter.SegMode.SEARCH)
            .joinToString(" ") { it.word }
    }
}
