package com.mememanager.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.sqlite.db.SimpleSQLiteQuery
import com.mememanager.data.local.dao.MediaFtsDao
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.util.JiebaTokenizer
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val mediaFtsDao: MediaFtsDao
) {
    companion object {
        private const val PAGE_SIZE = 30
    }

    /**
     * 搜索媒体
     *
     * @param query 用户原始输入（中文会被分词，英文直接匹配）
     * @return PagingData 流
     */
    fun search(query: String): Flow<PagingData<MediaWithTags>> {
        val ftsQuery = JiebaTokenizer.tokenize(query)
        if (ftsQuery.isEmpty()) return kotlinx.coroutines.flow.emptyFlow()

        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                enablePlaceholders = false,
                initialLoadSize = PAGE_SIZE
            ),
            pagingSourceFactory = {
                mediaFtsDao.search(
                    SimpleSQLiteQuery(
                        """
                        SELECT m.* FROM media m
                        JOIN media_fts ON m.id = media_fts.rowid
                        WHERE media_fts MATCH ? AND m.isDeleted = 0
                        ORDER BY rank
                        """,
                        arrayOf(ftsQuery)
                    )
                )
            }
        ).flow
    }
}
