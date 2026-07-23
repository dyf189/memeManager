package com.mememanager.util

import com.huaban.analysis.jieba.JiebaSegmenter
import com.huaban.analysis.jieba.SegToken

/**
 * 结巴分词 + FTS5 MATCH 查询构建
 *
 * FTS5 unicode61 tokenizer 对中文逐字拆 token。
 * 中文多字词用 FTS5 phrase 查询（双引号包裹），
 * 例："好的" 匹配索引中相邻的 "好" → "的" token。
 */
object JiebaTokenizer {

    private val segmenter by lazy { JiebaSegmenter() }

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

        val tokens: List<SegToken> = segmenter.process(trimmed, JiebaSegmenter.SegMode.SEARCH)

        val words = tokens
            .filter { it.word.isNotBlank() }
            .map { it.word.trim() }
            .filter { w -> w.length >= 2 || w.any { it.code > 127 } }

        if (words.isEmpty()) return "\"$trimmed\""

        return words.distinct().joinToString(" OR ") { "\"$it\"" }
    }

    /**
     * 用户输入 → 分词列表（用于 UI 高亮）
     */
    fun segment(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return segmenter.process(trimmed, JiebaSegmenter.SegMode.SEARCH)
            .filter { it.word.isNotBlank() }
            .map { it.word.trim() }
            .distinct()
    }

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
