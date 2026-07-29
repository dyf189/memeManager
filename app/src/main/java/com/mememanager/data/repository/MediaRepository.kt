package com.mememanager.data.repository

import androidx.compose.remote.creation.dsl.first
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.sqlite.db.SimpleSQLiteQuery
import com.mememanager.data.local.dao.MediaDao
import com.mememanager.data.local.dao.MediaFtsDao
import com.mememanager.data.local.dao.MediaTagRefDao
import com.mememanager.data.local.entity.MediaEntity
import com.mememanager.data.local.entity.MediaTagCrossRef
import com.mememanager.data.local.entity.MediaType
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val mediaDao: MediaDao,
    private val mediaTagRefDao: MediaTagRefDao,
    private val mediaFtsDao: MediaFtsDao
) {
    companion object {
        private const val PAGE_SIZE = 30
    }

    // ── 分页查询 ──

    fun getAlbumFlow(type: MediaType? = null, tagIds: Set<Long> = emptySet()): Flow<PagingData<MediaWithTags>> {
        return Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
            when {
                type != null && tagIds.isNotEmpty() -> mediaDao.getAlbumPagingSourceByTypeAndTags(type, tagIds)
                type != null -> mediaDao.getAlbumPagingSourceByType(type)
                tagIds.isNotEmpty() -> mediaDao.getAlbumPagingSourceByTags(tagIds)
                else -> mediaDao.getAlbumPagingSource()
            }
        }.flow
    }

    fun getDeletedFlow(): Flow<PagingData<MediaWithTags>> {
        return Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
            mediaDao.getDeletedPagingSource()
        }.flow
    }

    // ── 单条查询 ──

    fun getMediaWithTagsById(id: Long): Flow<MediaWithTags?> =
        mediaDao.getMediaWithTagsById(id)

    suspend fun getMediaById(id: Long): MediaEntity? =
        mediaDao.getByIdSuspend(id)

    // ── CRUD ──

    suspend fun insert(media: MediaEntity): Long {
        val id = mediaDao.insert(media)
        syncFts(id, media.name, media.description ?: "")
        return id
    }

    suspend fun update(media: MediaEntity) {
        mediaDao.update(media)
        syncFts(media.id, media.name, media.description ?: "")
    }

    suspend fun delete(media: MediaEntity) {
        mediaDao.delete(media)
        mediaFtsDao.deleteFts(SimpleSQLiteQuery("DELETE FROM media_fts WHERE rowid = ?", arrayOf(media.id)))
    }

    suspend fun deleteById(id: Long) {
        val entity = mediaDao.getByIdSuspend(id)
        if (entity != null) {
            val file = java.io.File(entity.filePath)
            if (file.exists()) file.delete()
        }
        mediaDao.deleteById(id)
        mediaFtsDao.deleteFts(SimpleSQLiteQuery("DELETE FROM media_fts WHERE rowid = ?", arrayOf(id)))
    }

    // ── 回收站 ──

    suspend fun softDelete(id: Long) {
        mediaDao.softDelete(id, System.currentTimeMillis())
        mediaFtsDao.deleteFts(SimpleSQLiteQuery("DELETE FROM media_fts WHERE rowid = ?", arrayOf(id)))
    }

    suspend fun restore(id: Long) {
        mediaDao.restore(id)
        val media = mediaDao.getByIdSuspend(id) ?: return
        syncFts(media.id, media.name, media.description ?: "")
    }

    suspend fun purgeDeletedBefore(cutoffTime: Long): Int {
        // 先拿到所有要清掉的实体，删除对应文件
        val toPurge = mediaDao.getAll().first().filter { it.isDeleted && (it.deletedTime ?: 0) < cutoffTime }
        toPurge.forEach { entity ->
            val file = java.io.File(entity.filePath)
            if (file.exists()) file.delete()
        }
        return mediaDao.purgeDeletedBefore(cutoffTime)
    }

    private suspend fun syncFts(id: Long, name: String, description: String) {
        val tokenizedName = com.mememanager.util.JiebaTokenizer.toFtsContent(name)
        val tokenizedDesc = com.mememanager.util.JiebaTokenizer.toFtsContent(description)
        try {
            mediaFtsDao.insertFts(
                SimpleSQLiteQuery(
                    "INSERT INTO media_fts(rowid, name, description) VALUES (?, ?, ?)",
                    arrayOf(id, tokenizedName, tokenizedDesc)
                )
            )
        } catch (_: Exception) {
            mediaFtsDao.updateFts(
                SimpleSQLiteQuery(
                    "UPDATE media_fts SET name = ?, description = ? WHERE rowid = ?",
                    arrayOf(tokenizedName, tokenizedDesc, id)
                )
            )
        }
    }

    /** 全量重建 FTS 索引（升级迁移后调用一次） */
    suspend fun seedAllFts() {
        val all = mediaDao.getAll().first()
        all.filter { !it.isDeleted }.forEach { media ->
            syncFts(media.id, media.name, media.description ?: "")
        }
    }

    suspend fun purgeDeletedBefore(cutoffTime: Long): Int =
        mediaDao.purgeDeletedBefore(cutoffTime)

    fun getDeletedCount(): Flow<Int> = mediaDao.getDeletedCount()

    fun getActiveCount(): Flow<Int> = mediaDao.getActiveCount()

    // ── 标签关联 ──

    fun getTagsForMedia(mediaId: Long): Flow<List<TagEntity>> =
        mediaTagRefDao.getTagsForMedia(mediaId)

    suspend fun addTagToMedia(mediaId: Long, tagId: Long) {
        mediaTagRefDao.insert(MediaTagCrossRef(mediaId = mediaId, tagId = tagId))
    }

    suspend fun removeTagFromMedia(mediaId: Long, tagId: Long) {
        mediaTagRefDao.deleteByMediaIdAndTagId(mediaId, tagId)
    }

    suspend fun setTagsForMedia(mediaId: Long, tagIds: Set<Long>) {
        mediaTagRefDao.deleteByMediaId(mediaId)
        tagIds.forEach { tagId ->
            mediaTagRefDao.insert(MediaTagCrossRef(mediaId = mediaId, tagId = tagId))
        }
    }

    suspend fun getTagIdsForMedia(mediaId: Long): List<Long> =
        mediaTagRefDao.getTagIdsForMedia(mediaId)
}
