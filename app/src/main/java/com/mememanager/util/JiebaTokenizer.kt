package com.mememanager.util

import com.huaban.analysis.jieba.JiebaSegmenter
import com.huaban.analysis.jieba.SegToken

/**
 * 结巴分词 → FTS5 MATCH 查询词条
 *
 * 将中文查询分词后，用 OR 连接各词条，
 * 同时保留原始查询作为前缀匹配词条，覆盖未登录词。
 */
object JiebaTokenizer {

    private val segmenter by lazy { JiebaSegmenter() }

    /**
     * 分词 → FTS5 MATCH 表达式
     *
     * 例："搞笑猫咪" → "搞笑 OR 猫咪 OR 搞笑猫咪*"
     *
     * @param query 用户原始输入
     * @return FTS5 MATCH 可用的布尔表达式
     */
    fun tokenize(query: String): String {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return ""

        // 结巴分词
        val tokens: List<SegToken> = segmenter.process(trimmed, JiebaSegmenter.SegMode.SEARCH)

        val words = tokens
            .filter { it.word.isNotBlank() }
            .map { it.word.trim() }
            .filter { it.length >= 2 || it.any { c -> c.code > 127 } }

        if (words.isEmpty()) return "\"$trimmed\"*"

        val unique = words.distinct()

        // 每个多字词生成两种匹配：
        // 1. 原词（匹配 FTS 中的完整 token，如英文）
        // 2. NEAR 降级（中文逐字匹配，"卡住" → "卡 NEAR/1 住"）
        val terms = unique.flatMap { word ->
            if (word.length >= 2 && word.any { it.code > 127 }) {
                val near = word.toCharArray().joinToString(" NEAR/1 ") { "\"$it\"" }
                listOf(word, "($near)")
            } else {
                listOf(word)
            }
        }

        return terms.joinToString(" OR ")
    }
}
