package com.mememanager.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import com.mememanager.data.local.entity.MediaEntity
import com.mememanager.data.local.entity.MediaWithTags

/**
 * FTS5 全文搜索 DAO
 *
 * 使用 @RawQuery 绕过 Room 列名校验（FTS5 的 rank 不是实体列）。
 */
@Dao
interface MediaFtsDao {

    @Transaction
    @RawQuery(observedEntities = [MediaEntity::class])
    fun search(query: SupportSQLiteQuery): PagingSource<Int, MediaWithTags>
}
