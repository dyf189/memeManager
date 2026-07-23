package com.mememanager.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.sqlite.db.SimpleSQLiteQuery
import com.mememanager.data.local.dao.MediaFtsDao
import com.mememanager.data.local.entity.MediaWithTags
import com.mememanager.util.JiebaTokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val mediaFtsDao: MediaFtsDao
) {
    companion object {
        private const val PAGE_SIZE = 30
        private const val FTS_SQL = """
            SELECT m.* FROM media m
            JOIN media_fts ON m.id = media_fts.rowid
            WHERE media_fts MATCH ? AND m.isDeleted = 0
            ORDER BY rank
        """
    }

    fun search(query: String): Flow<PagingData<MediaWithTags>> = flow {
        // 分词放在 IO 线程，避免 Jieba 首次加载词典阻塞主线程
        val ftsQuery = withContext(Dispatchers.IO) {
            JiebaTokenizer.tokenize(query)
        }
        if (ftsQuery.isEmpty()) return@flow

        emitAll(
            Pager(
                config = PagingConfig(
                    pageSize = PAGE_SIZE,
                    enablePlaceholders = false,
                    initialLoadSize = PAGE_SIZE
                ),
                pagingSourceFactory = {
                    mediaFtsDao.search(
                        SimpleSQLiteQuery(FTS_SQL, arrayOf(ftsQuery))
                    )
                }
            ).flow
        )
    }
}
