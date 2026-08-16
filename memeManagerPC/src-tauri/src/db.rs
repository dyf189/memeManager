//! SQLite 数据层：建表、默认数据、连接管理
//!
//! 表结构对齐 goals.md 实体（MediaEntity / TagEntity / MediaTagCrossRef），
//! 字段命名对齐 export-format.md 的跨平台建议（sha256 即媒体内容哈希）。

use rusqlite::Connection;
use std::path::Path;

/// 应用数据库连接（进程内单实例，由 tauri State 持有）
pub struct Db(pub std::sync::Mutex<Connection>);

const SCHEMA: &str = r#"
CREATE TABLE IF NOT EXISTS media (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  file_name     TEXT NOT NULL,
  file_path     TEXT NOT NULL UNIQUE,
  storage_type  TEXT NOT NULL DEFAULT 'user',
  media_type    TEXT NOT NULL,               -- image | gif | video
  mime_type     TEXT,
  source        TEXT NOT NULL DEFAULT 'custom',
  description   TEXT NOT NULL DEFAULT '',
  taken_time    INTEGER NOT NULL,
  import_time   INTEGER NOT NULL,
  file_size     INTEGER NOT NULL,
  width         INTEGER,
  height        INTEGER,
  sha256        TEXT,
  is_deleted    INTEGER NOT NULL DEFAULT 0,
  deleted_time  INTEGER,
  sort_order    INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_media_taken    ON media(taken_time DESC);
CREATE INDEX IF NOT EXISTS idx_media_deleted  ON media(is_deleted);

CREATE TABLE IF NOT EXISTS tag (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  name        TEXT NOT NULL UNIQUE,
  bg_color    TEXT NOT NULL DEFAULT '#409eff',
  is_reserved INTEGER NOT NULL DEFAULT 0,
  sort_order  INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS media_tag (
  media_id  INTEGER NOT NULL REFERENCES media(id) ON DELETE CASCADE,
  tag_id    INTEGER NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
  PRIMARY KEY (media_id, tag_id)
);

CREATE TABLE IF NOT EXISTS settings (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL
);
"#;

/// 在给定连接上建表 + 写入默认数据
pub fn init_schema(conn: &Connection) -> rusqlite::Result<()> {
    // 外键约束（级联删除）默认关闭，必须每个连接显式开启
    conn.execute_batch("PRAGMA foreign_keys = ON;")?;
    conn.execute_batch(SCHEMA)?;
    // 旧库迁移：添加 sort_order 列（已存在则忽略）
    let _ = conn.execute(
        "ALTER TABLE media ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0",
        [],
    );
    // 清理旧版本的 [已导出] 保留标签（该标签已废弃，保留标签机制仍保留）
    let _ = conn.execute("DELETE FROM tag WHERE name = '[已导出]' AND is_reserved = 1", []);
    // 清理历史孤儿关联（外键此前未启用，删除媒体/标签时留下了悬空行）
    conn.execute_batch(
        "DELETE FROM media_tag WHERE media_id NOT IN (SELECT id FROM media);
         DELETE FROM media_tag WHERE tag_id NOT IN (SELECT id FROM tag);",
    )?;
    // 旧库迁移：修正误把 media_type 写进 mime_type 的行（image/gif/video → 真实 MIME）
    conn.execute(
        "UPDATE media SET mime_type = CASE media_type
             WHEN 'gif' THEN 'image/gif'
             WHEN 'video' THEN 'video/mp4'
             ELSE 'image/jpeg'
         END
         WHERE mime_type IN ('image', 'gif', 'video')",
        [],
    )?;
    Ok(())
}

/// 打开（或创建）数据库文件并初始化
pub fn init(db_path: &Path) -> rusqlite::Result<Connection> {
    if let Some(parent) = db_path.parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    let conn = Connection::open(db_path)?;
    init_schema(&conn)?;
    Ok(conn)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_schema_init_and_no_default_tags() {
        let conn = Connection::open_in_memory().unwrap();
        init_schema(&conn).unwrap();

        let total: i64 = conn
            .query_row("SELECT COUNT(*) FROM tag", [], |r| r.get(0))
            .unwrap();
        assert_eq!(total, 0);

        // 再次初始化不产生重复
        init_schema(&conn).unwrap();
        let total: i64 = conn.query_row("SELECT COUNT(*) FROM tag", [], |r| r.get(0)).unwrap();
        assert_eq!(total, 0);
    }

    #[test]
    fn test_foreign_keys_cascade() {
        let conn = Connection::open_in_memory().unwrap();
        init_schema(&conn).unwrap();

        conn.execute(
            "INSERT INTO media (file_name, file_path, media_type, taken_time, import_time, file_size)
             VALUES ('a.png', '/t/a.png', 'image', 0, 0, 1)",
            [],
        )
        .unwrap();
        conn.execute("INSERT INTO tag (name, bg_color, sort_order) VALUES ('T', '#fff', 1)", [])
            .unwrap();
        conn.execute("INSERT INTO media_tag (media_id, tag_id) VALUES (1, 1)", []).unwrap();

        // 删除媒体 → 关联级联清除
        conn.execute("DELETE FROM media WHERE id = 1", []).unwrap();
        let n: i64 = conn.query_row("SELECT COUNT(*) FROM media_tag", [], |r| r.get(0)).unwrap();
        assert_eq!(n, 0);

        // 删除标签 → 关联级联清除
        conn.execute(
            "INSERT INTO media (file_name, file_path, media_type, taken_time, import_time, file_size)
             VALUES ('b.png', '/t/b.png', 'image', 0, 0, 1)",
            [],
        )
        .unwrap();
        conn.execute("INSERT INTO media_tag (media_id, tag_id) VALUES (2, 1)", []).unwrap();
        conn.execute("DELETE FROM tag WHERE id = 1", []).unwrap();
        let n: i64 = conn.query_row("SELECT COUNT(*) FROM media_tag", [], |r| r.get(0)).unwrap();
        assert_eq!(n, 0);
    }

    #[test]
    fn test_orphan_cleanup_migration() {
        let conn = Connection::open_in_memory().unwrap();
        init_schema(&conn).unwrap();
        // 先关掉外键模拟旧库写入悬空关联
        conn.execute_batch("PRAGMA foreign_keys = OFF;").unwrap();
        conn.execute(
            "INSERT INTO media (file_name, file_path, media_type, taken_time, import_time, file_size)
             VALUES ('a.png', '/t/a.png', 'image', 0, 0, 1)",
            [],
        )
        .unwrap();
        conn.execute("INSERT INTO media_tag (media_id, tag_id) VALUES (1, 999)", []).unwrap();
        // 再次 init_schema 应清掉孤儿行
        init_schema(&conn).unwrap();
        let n: i64 = conn.query_row("SELECT COUNT(*) FROM media_tag", [], |r| r.get(0)).unwrap();
        assert_eq!(n, 0);
    }
}
