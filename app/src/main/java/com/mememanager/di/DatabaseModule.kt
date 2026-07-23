package com.mememanager.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mememanager.data.local.AppDatabase
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import com.mememanager.data.local.dao.MediaDao
import com.mememanager.data.local.dao.MediaFtsDao
import com.mememanager.data.local.dao.MediaTagRefDao
import com.mememanager.data.local.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "meme_manager.db"
        )
            .openHelperFactory(RequerySQLiteOpenHelperFactory())
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    createFts(db)
                    db.execSQL("INSERT INTO media_fts(media_fts) VALUES('rebuild')")
                }
                override fun onOpen(db: SupportSQLiteDatabase) {
                    createFts(db)
                    // 全量重建索引：FTS5 rebuild 命令自动从 content 表（media）重新索引
                    db.execSQL("INSERT INTO media_fts(media_fts) VALUES('rebuild')")
                }
                private fun createFts(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE VIRTUAL TABLE IF NOT EXISTS media_fts USING fts5(
                            name, description, content='media', content_rowid='id', tokenize='unicode61'
                        )
                    """)
                    db.execSQL("""
                        CREATE TRIGGER IF NOT EXISTS media_fts_ai AFTER INSERT ON media BEGIN
                            INSERT INTO media_fts(rowid, name, description)
                            VALUES (new.id, new.name, new.description);
                        END;
                        CREATE TRIGGER IF NOT EXISTS media_fts_ad AFTER DELETE ON media BEGIN
                            INSERT INTO media_fts(media_fts, rowid, name, description)
                            VALUES ('delete', old.id, old.name, old.description);
                        END;
                        CREATE TRIGGER IF NOT EXISTS media_fts_au AFTER UPDATE ON media BEGIN
                            INSERT INTO media_fts(media_fts, rowid, name, description)
                            VALUES ('delete', old.id, old.name, old.description);
                            INSERT INTO media_fts(rowid, name, description)
                            VALUES (new.id, new.name, new.description);
                        END;
                    """)
                }
            })
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideMediaDao(database: AppDatabase): MediaDao {
        return database.mediaDao()
    }

    @Provides
    fun provideTagDao(database: AppDatabase): TagDao {
        return database.tagDao()
    }

    @Provides
    fun provideMediaTagRefDao(database: AppDatabase): MediaTagRefDao {
        return database.mediaTagRefDao()
    }

    @Provides
    fun provideMediaFtsDao(database: AppDatabase): MediaFtsDao {
        return database.mediaFtsDao()
    }
}
