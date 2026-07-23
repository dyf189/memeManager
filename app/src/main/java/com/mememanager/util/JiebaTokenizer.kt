package com.mememanager.util

import com.huaban.analysis.jieba.JiebaSegmenter
import com.huaban.analysis.jieba.SegToken

/**
 * 结巴分词 + FTS5 MATCH 查询构建
 *
 * FTS5 unicode61 tokenizer 对中文逐字拆 token，
 * 因此对 Jieba 产出的多字词通过 NEAR 操作符降级为逐字相邻匹配。
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
     * 例："卡住了" → ("卡" NEAR/1 "住") OR "了"
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

        val terms = words.distinct().map { word ->
            if (word.any { it.code > 127 } && word.length >= 2) {
                // 中文多字词 → 逐字 NEAR
                "(" + word.toCharArray().joinToString(" NEAR/1 ") { "\"$it\"" } + ")"
            } else {
                // 英文/单字 → 直接匹配
                "\"$word\""
            }
        }

        return terms.joinToString(" OR ")
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
}
