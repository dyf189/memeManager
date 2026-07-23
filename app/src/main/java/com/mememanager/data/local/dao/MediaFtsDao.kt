package com.mememanager.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import com.mememanager.data.local.entity.MediaEntity
import com.mememanager.data.local.entity.MediaWithTags

/**
 * FTS5 全文搜索 DAO — 全部 @RawQuery（media_fts 不是 Room Entity）
 */
@Dao
interface MediaFtsDao {

    @Transaction
    @RawQuery(observedEntities = [MediaEntity::class])
    fun search(query: SupportSQLiteQuery): PagingSource<Int, MediaWithTags>

    @RawQuery
    suspend fun insertFts(query: SupportSQLiteQuery): Long

    @RawQuery
    suspend fun updateFts(query: SupportSQLiteQuery): Int

    @RawQuery
    suspend fun deleteFts(query: SupportSQLiteQuery): Int
}
