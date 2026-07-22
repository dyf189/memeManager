package com.mememanager.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.mememanager.data.local.entity.MediaWithTags

/**
 * FTS5 全文搜索 DAO
 *
 * 搜索范围：文件名、描述
 * FTS5 虚拟表 "media_fts" 与 media 表通过外部内容表关联，
 * 增删改触发器在 AppDatabase 回调中创建。
 */
@Dao
interface MediaFtsDao {

    /**
     * FTS5 MATCH 搜索（支持布尔表达式）
     *
     * 用法举例：
     * - "猫"           → 包含"猫"
     * - "猫 OR 狗"     → 包含"猫"或"狗"
     * - "猫*"          → 前缀匹配
     */
    @Transaction
    @Query("""
        SELECT m.* FROM media m
        JOIN media_fts ON m.id = media_fts.rowid
        WHERE media_fts MATCH :query AND m.isDeleted = 0
        ORDER BY rank
    """)
    fun search(query: String): PagingSource<Int, MediaWithTags>
}
