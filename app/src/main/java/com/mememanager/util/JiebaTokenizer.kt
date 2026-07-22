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
            .filter { it.length >= 2 || it.any { c -> c.code > 127 } } // 短英文过滤，中文单字保留

        if (words.isEmpty()) return "\"$trimmed\"*"

        // 去重 + OR 连接
        val unique = words.distinct()
        // 加上原始查询的前缀匹配（覆盖未登录词）
        val terms = unique + "\"$trimmed\"*"

        return terms.joinToString(" OR ")
    }
}
