package com.mememanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mememanager.data.local.dao.MediaDao
import com.mememanager.data.local.dao.TagDao
import com.mememanager.data.local.entity.MediaEntity
import com.mememanager.data.local.entity.MediaTagCrossRef
import com.mememanager.data.local.entity.TagEntity

@Database(
    entities = [
        MediaEntity::class,
        TagEntity::class,
        MediaTagCrossRef::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun tagDao(): TagDao
}
