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

    @androidx.room.Query("INSERT INTO media_fts(rowid, name, description) VALUES (:id, :name, :desc)")
    suspend fun insert(id: Long, name: String, desc: String)

    @androidx.room.Query("UPDATE media_fts SET name = :name, description = :desc WHERE rowid = :id")
    suspend fun update(id: Long, name: String, desc: String)

    @androidx.room.Query("DELETE FROM media_fts WHERE rowid = :id")
    suspend fun delete(id: Long)
}
