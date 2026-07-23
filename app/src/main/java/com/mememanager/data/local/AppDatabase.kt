package com.mememanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mememanager.data.local.dao.MediaDao
import com.mememanager.data.local.dao.MediaFtsDao
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
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun tagDao(): TagDao
    abstract fun mediaTagRefDao(): MediaTagRefDao
    abstract fun mediaFtsDao(): MediaFtsDao

    companion object {
        private const val CREATE_FTS = """
            CREATE VIRTUAL TABLE IF NOT EXISTS media_fts USING fts5(
                name, description, tokenize='ascii'
            )
        """

        private const val TRIGGERS = """
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
        """

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_FTS)
                db.execSQL(TRIGGERS)
                db.execSQL("""
                    INSERT INTO media_fts(rowid, name, description)
                    SELECT id, name, description FROM media WHERE isDeleted = 0
                """)
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 删除旧 FTS（unicode61 + content=media）
                db.execSQL("DROP TABLE IF EXISTS media_fts")
                // 重建 FTS（ascii tokenizer，手动管理）
                db.execSQL(CREATE_FTS)
                // 不再创建触发器——由 Repository 层手动维护 FTS
                // 存量数据用 rebuild（但 content 表不存在，无法 rebuild）
                // 直接在 onOpen 中手动填充
            }
        }
    }
}
