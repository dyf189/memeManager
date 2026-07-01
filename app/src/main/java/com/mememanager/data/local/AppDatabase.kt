package com.mememanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mememanager.data.local.dao.MediaDao
import com.mememanager.data.local.dao.MediaTagRefDao
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
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun tagDao(): TagDao
    abstract fun mediaTagRefDao(): MediaTagRefDao
}
