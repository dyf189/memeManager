package com.mememanager.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 全局应用设置（DataStore Preferences 持久化）
 */
data class AppSettings(
    val storageType: String = "私有内部",
    val shardSizeMB: Int = 100,
    val themeMode: String = "跟随系统",
    val gridColumns: Int = 3,
    val trashDays: Int = 30,
    val jsonSyncEnabled: Boolean = false,
    /** Jieba 词典启动时后台预热（关 = 首次使用时才加载，那次操作会慢约 10 秒） */
    val jiebaPreloadEnabled: Boolean = true,
    val exportDirUri: String? = null
)

object SettingsKeys {
    val STORAGE_TYPE = stringPreferencesKey("storage_type")
    val SHARD_SIZE_MB = intPreferencesKey("shard_size_mb")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val GRID_COLUMNS = intPreferencesKey("grid_columns")
    val TRASH_DAYS = intPreferencesKey("trash_days")
    val JSON_SYNC_ENABLED = booleanPreferencesKey("json_sync_enabled")
    val JIEBA_PRELOAD_ENABLED = booleanPreferencesKey("jieba_preload_enabled")
    val EXPORT_DIR_URI = stringPreferencesKey("export_dir_uri")
    val SEARCH_HISTORY = stringSetPreferencesKey("search_history")
    val CUSTOM_COLORS = stringSetPreferencesKey("custom_colors")
    val LAST_TRASH_CLEAN = longPreferencesKey("last_trash_clean")
}
