//! 媒体/标签/回收站/设置 的数据操作（SQLite）
//! 所有实现函数接收 &Connection，便于单元测试；tauri command 为薄包装。

use rusqlite::{params, Connection, OptionalExtension};
use std::fs;
use std::path::{Path, PathBuf};

use crate::mpak::sha256_file;

// ===== 结构体（返回前端）=====

#[derive(serde::Serialize, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Media {
    pub id: i64,
    pub file_name: String,
    pub file_path: String,
    pub storage_type: String,
    pub media_type: String,
    pub mime_type: Option<String>,
    pub source: String,
    pub description: String,
    pub taken_time: i64,
    pub import_time: i64,
    pub file_size: i64,
    pub width: Option<i64>,
    pub height: Option<i64>,
    pub sha256: Option<String>,
    pub is_deleted: bool,
    pub deleted_time: Option<i64>,
    /// 手动排序位置（0 = 未手动排序，按时间）
    pub sort_order: i64,
    pub tag_ids: Vec<i64>,
}

#[derive(serde::Serialize, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Tag {
    pub id: i64,
    pub name: String,
    pub bg_color: String,
    pub is_reserved: bool,
    pub sort_order: i64,
}

#[derive(serde::Serialize, Debug)]
#[serde(rename_all = "camelCase")]
pub struct ScanSummary {
    pub added: usize,
    pub updated: usize,
    pub skipped: usize,
    /// 目录中已消失的文件被清理的记录数
    pub removed: usize,
}

// ===== 扩展名识别 =====

/// 识别媒体类型；非媒体扩展名返回 None
fn classify(path: &Path) -> Option<(&'static str, &'static str)> {
    let ext = path.extension()?.to_string_lossy().to_lowercase();
    match ext.as_str() {
        "jpg" | "jpeg" | "png" | "webp" | "bmp" | "svg" | "ico" | "tif" | "tiff" | "avif" | "heic" | "heif" => Some(("image", "image/jpeg")),
        "gif" => Some(("gif", "image/gif")),
        "mp4" | "webm" | "mov" | "mkv" | "avi" | "mpg" | "mpeg" | "ogv" | "3gp" | "flv" => Some(("video", "video/mp4")),
        _ => None,
    }
}

/// 递归（可选）收集目录下所有媒体文件路径
/// 目录判断用 entry.file_type()（不跟随符号链接），避免符号链接环导致无限递归
fn collect_media_files(dir: &Path, recursive: bool, out: &mut Vec<PathBuf>) {
    let Ok(rd) = fs::read_dir(dir) else { return };
    for entry in rd.flatten() {
        let p = entry.path();
        let is_dir = entry.file_type().map(|t| t.is_dir()).unwrap_or(false);
        if is_dir {
            if recursive {
                collect_media_files(&p, true, out);
            }
        } else if classify(&p).is_some() {
            out.push(p);
        }
    }
}

// ===== 扫描入库 =====

/// 扫描文件夹并同步入库（目录同步语义）：
/// - 新文件 → 插入
/// - 已存在路径：文件大小变化 → 更新记录（重算哈希/时间）；不变 → 跳过
/// - 目录中已消失的文件 → 清理对应记录（物理文件已不存在）
pub fn scan_folder_impl(conn: &Connection, dir: &str, recursive: bool) -> Result<ScanSummary, String> {
    let root = Path::new(dir);
    if !root.is_dir() {
        return Err(format!("目录不存在: {}", dir));
    }

    let mut files = Vec::new();
    collect_media_files(root, recursive, &mut files);
    files.sort();

    let now = crate::mpak::now_ms();
    let mut added = 0usize;
    let mut updated = 0usize;
    let mut skipped = 0usize;
    let mut removed = 0usize;

    // 库中属于该目录的已有记录（file_path → id）。
    // LIKE 模式需转义 \% \_（目录名中的通配符字符），并用 ESCAPE '\' 声明转义符，
    // 否则目录名含 _ 或 % 时可能误匹配兄弟目录的记录并把它们当"已消失"删掉。
    let dir_norm = dir.trim_end_matches(['/', '\\']);
    let esc = dir_norm.replace('\\', "\\\\").replace('%', "\\%").replace('_', "\\_");
    // 路径分隔符本身在 ESCAPE '\' 语义下也要转义（Windows 的 \），否则结尾的 \% 会被当成字面百分号
    let sep = if std::path::MAIN_SEPARATOR == '\\' { "\\\\" } else { "/" };
    let pattern = format!("{}{}%", esc, sep);
    let mut existing: Vec<(i64, String)> = Vec::new();
    {
        // 查询失败必须上抛：吞掉错误会把 existing 当空表，误删该目录全部记录
        let mut stmt = conn
            .prepare("SELECT id, file_path FROM media WHERE file_path LIKE ?1 ESCAPE '\\'")
            .map_err(|e| e.to_string())?;
        let rows = stmt
            .query_map(params![pattern], |r| Ok((r.get::<_, i64>(0)?, r.get::<_, String>(1)?)))
            .map_err(|e| e.to_string())?;
        for r in rows {
            existing.push(r.map_err(|e| e.to_string())?);
        }
    }

    let collected: std::collections::HashSet<&str> = files
        .iter()
        .filter_map(|p| p.to_str())
        .collect();

    // 1) 目录中已消失的文件 → 删除记录
    for (id, fp) in &existing {
        if !collected.contains(fp.as_str()) {
            if conn.execute("DELETE FROM media WHERE id = ?1", params![id]).is_ok() {
                removed += 1;
            }
        }
    }

    // 2) 处理当前目录中的文件
    let existing_map: std::collections::HashMap<&str, i64> =
        existing.iter().map(|(id, fp)| (fp.as_str(), *id)).collect();

    for path in &files {
        let file_path = path.to_string_lossy().into_owned();
        let Ok(meta) = fs::metadata(path) else {
            skipped += 1;
            continue;
        };
        let file_size = meta.len() as i64;
        let (media_type, mime_type) = classify(path).unwrap_or(("image", "image/jpeg"));
        let file_name = path
            .file_name()
            .map(|s| s.to_string_lossy().into_owned())
            .unwrap_or_default();
        let taken_time = meta
            .modified()
            .ok()
            .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
            .map(|d| d.as_millis() as i64)
            .unwrap_or(now);

        if let Some(&id) = existing_map.get(file_path.as_str()) {
            // 已入库：大小变化 → 更新（重新计算哈希与时间）；不变 → 跳过
            let size_in_db: Option<i64> = conn
                .query_row("SELECT file_size FROM media WHERE id = ?1", params![id], |r| r.get(0))
                .optional()
                .map_err(|e| e.to_string())?
                .flatten();
            if size_in_db == Some(file_size) {
                skipped += 1;
                continue;
            }
            let sha = sha256_file(path);
            let res = conn.execute(
                "UPDATE media SET file_size = ?1, sha256 = ?2, media_type = ?3, mime_type = ?4, taken_time = ?5, file_name = ?6 WHERE id = ?7",
                params![file_size, sha, media_type, mime_type, taken_time, file_name, id],
            );
            if res.is_ok() {
                updated += 1;
            } else {
                skipped += 1;
            }
            continue;
        }

        // 新文件 → 插入
        let sha = sha256_file(path);
        let res = conn.execute(
            "INSERT INTO media (file_name, file_path, storage_type, media_type, mime_type, source, description, taken_time, import_time, file_size, sha256, is_deleted)
             VALUES (?1, ?2, 'user', ?3, ?4, 'custom', '', ?5, ?6, ?7, ?8, 0)",
            params![file_name, file_path, media_type, mime_type, taken_time, now, file_size, sha],
        );
        if res.is_ok() {
            added += 1;
        } else {
            skipped += 1;
        }
    }

    Ok(ScanSummary { added, updated, skipped, removed })
}

// ===== 查询 =====

fn row_to_media(row: &rusqlite::Row) -> rusqlite::Result<Media> {
    Ok(Media {
        id: row.get(0)?,
        file_name: row.get(1)?,
        file_path: row.get(2)?,
        storage_type: row.get(3)?,
        media_type: row.get(4)?,
        mime_type: row.get(5)?,
        source: row.get(6)?,
        description: row.get(7)?,
        taken_time: row.get(8)?,
        import_time: row.get(9)?,
        file_size: row.get(10)?,
        width: row.get(11)?,
        height: row.get(12)?,
        sha256: row.get(13)?,
        is_deleted: row.get::<_, i64>(14)? != 0,
        deleted_time: row.get(15)?,
        sort_order: row.get(16)?,
        tag_ids: Vec::new(),
    })
}

const MEDIA_COLUMNS: &str = "id, file_name, file_path, storage_type, media_type, mime_type, source, description, taken_time, import_time, file_size, width, height, sha256, is_deleted, deleted_time, sort_order";

fn tag_ids_of(conn: &Connection, media_id: i64) -> Vec<i64> {
    let mut stmt = match conn.prepare("SELECT tag_id FROM media_tag WHERE media_id = ?1 ORDER BY tag_id") {
        Ok(s) => s,
        Err(_) => return Vec::new(),
    };
    stmt.query_map(params![media_id], |r| r.get::<_, i64>(0))
        .map(|rows| rows.flatten().collect())
        .unwrap_or_default()
}

fn load_media(conn: &Connection, query: &str, params: &[&dyn rusqlite::ToSql]) -> Result<Vec<Media>, String> {
    let mut stmt = conn
        .prepare(&format!("SELECT {} FROM media {}", MEDIA_COLUMNS, query))
        .map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map(params, row_to_media)
        .map_err(|e| e.to_string())?;
    let mut list = Vec::new();
    for r in rows {
        let mut m = r.map_err(|e| e.to_string())?;
        m.tag_ids = tag_ids_of(conn, m.id);
        list.push(m);
    }
    Ok(list)
}

/// 相册媒体（手动排序优先，未手动排序的按时间降序；不含回收站）
pub fn list_media_impl(conn: &Connection) -> Result<Vec<Media>, String> {
    load_media(
        conn,
        "WHERE is_deleted = 0 ORDER BY sort_order ASC, taken_time DESC",
        &[],
    )
}

/// 回收站媒体
pub fn list_recycle_impl(conn: &Connection) -> Result<Vec<Media>, String> {
    load_media(conn, "WHERE is_deleted = 1 ORDER BY deleted_time DESC", &[])
}

fn row_to_tag(row: &rusqlite::Row) -> rusqlite::Result<Tag> {
    Ok(Tag {
        id: row.get(0)?,
        name: row.get(1)?,
        bg_color: row.get(2)?,
        is_reserved: row.get::<_, i64>(3)? != 0,
        sort_order: row.get(4)?,
    })
}

pub fn list_tags_impl(conn: &Connection) -> Result<Vec<Tag>, String> {
    let mut stmt = conn
        .prepare("SELECT id, name, bg_color, is_reserved, sort_order FROM tag ORDER BY sort_order, id")
        .map_err(|e| e.to_string())?;
    let rows = stmt.query_map([], row_to_tag).map_err(|e| e.to_string())?;
    rows.map(|r| r.map_err(|e| e.to_string())).collect()
}

// ===== 标签 CRUD =====

pub fn add_tag_impl(conn: &Connection, name: &str, bg_color: &str) -> Result<Tag, String> {
    let name = name.trim();
    if name.is_empty() {
        return Err("标签名不能为空".into());
    }
    let dup: Option<i64> = conn
        .query_row("SELECT id FROM tag WHERE name = ?1", params![name], |r| r.get(0))
        .optional()
        .map_err(|e| e.to_string())?;
    if dup.is_some() {
        return Err("标签名已存在".into());
    }
    // 新标签排到队尾
    let max_order: i64 = conn
        .query_row("SELECT COALESCE(MAX(sort_order), 0) FROM tag", [], |r| r.get(0))
        .map_err(|e| e.to_string())?;
    conn.execute(
        "INSERT INTO tag (name, bg_color, is_reserved, sort_order) VALUES (?1, ?2, 0, ?3)",
        params![name, bg_color, max_order + 1],
    )
    .map_err(|e| e.to_string())?;
    let id = conn.last_insert_rowid();
    Ok(Tag {
        id,
        name: name.to_string(),
        bg_color: bg_color.to_string(),
        is_reserved: false,
        sort_order: max_order + 1,
    })
}

pub fn rename_tag_impl(conn: &Connection, id: i64, name: &str) -> Result<(), String> {
    let name = name.trim();
    if name.is_empty() {
        return Err("标签名不能为空".into());
    }
    let reserved: Option<i64> = conn
        .query_row("SELECT is_reserved FROM tag WHERE id = ?1", params![id], |r| r.get(0))
        .optional()
        .map_err(|e| e.to_string())?;
    if reserved.is_none() {
        return Err("标签不存在".into());
    }
    if reserved == Some(1) {
        return Err("保留标签不可重命名".into());
    }
    let dup: Option<i64> = conn
        .query_row("SELECT id FROM tag WHERE name = ?1 AND id != ?2", params![name, id], |r| r.get(0))
        .optional()
        .map_err(|e| e.to_string())?;
    if dup.is_some() {
        return Err("标签名已存在".into());
    }
    conn.execute("UPDATE tag SET name = ?1 WHERE id = ?2", params![name, id])
        .map_err(|e| e.to_string())?;
    Ok(())
}

pub fn set_tag_color_impl(conn: &Connection, id: i64, color: &str) -> Result<(), String> {
    conn.execute("UPDATE tag SET bg_color = ?1 WHERE id = ?2", params![color, id])
        .map_err(|e| e.to_string())?;
    Ok(())
}

pub fn delete_tag_impl(conn: &Connection, id: i64) -> Result<(), String> {
    let reserved: Option<i64> = conn
        .query_row("SELECT is_reserved FROM tag WHERE id = ?1", params![id], |r| r.get(0))
        .optional()
        .map_err(|e| e.to_string())?;
    match reserved {
        None => Err("标签不存在".into()),
        Some(1) => Err("保留标签不可删除".into()),
        Some(_) => {
            conn.execute("DELETE FROM tag WHERE id = ?1", params![id])
                .map_err(|e| e.to_string())?;
            Ok(())
        }
    }
}

pub fn move_tag_impl(conn: &Connection, id: i64, dir: i64) -> Result<(), String> {    // 与相邻标签交换 sort_order（dir: -1 上移 / 1 下移）
    let order: Option<i64> = conn
        .query_row("SELECT sort_order FROM tag WHERE id = ?1", params![id], |r| r.get(0))
        .optional()
        .map_err(|e| e.to_string())?;
    let Some(order) = order else { return Err("标签不存在".into()) };
    let other: Option<(i64, i64)> = conn
        .query_row(
            "SELECT id, sort_order FROM tag WHERE sort_order = ?1 AND id != ?2",
            params![order + dir, id],
            |r| Ok((r.get(0)?, r.get(1)?)),
        )
        .optional()
        .map_err(|e| e.to_string())?;
    let Some((other_id, other_order)) = other else { return Ok(()) };
    conn.execute("UPDATE tag SET sort_order = ?1 WHERE id = ?2", params![other_order, id])
        .map_err(|e| e.to_string())?;
    conn.execute("UPDATE tag SET sort_order = ?1 WHERE id = ?2", params![order, other_id])
        .map_err(|e| e.to_string())?;
    Ok(())
}

/// 按给定 id 顺序整体重写 sort_order（拖拽排序持久化）
pub fn set_tag_order_impl(conn: &Connection, ids: &[i64]) -> Result<(), String> {
    let tx = conn.unchecked_transaction().map_err(|e| e.to_string())?;
    for (i, id) in ids.iter().enumerate() {
        tx.execute(
            "UPDATE tag SET sort_order = ?1 WHERE id = ?2",
            params![i as i64 + 1, id],
        )
        .map_err(|e| e.to_string())?;
    }
    tx.commit().map_err(|e| e.to_string())?;
    Ok(())
}

// ===== 媒体更新 =====

pub fn set_description_impl(conn: &Connection, id: i64, description: &str) -> Result<(), String> {
    conn.execute(
        "UPDATE media SET description = ?1 WHERE id = ?2",
        params![description.trim(), id],
    )
    .map_err(|e| e.to_string())?;
    Ok(())
}

/// 批量替换媒体标签（勾选集合整体覆盖）
pub fn replace_media_tags_impl(conn: &Connection, media_ids: &[i64], tag_ids: &[i64]) -> Result<(), String> {
    let tx = conn.unchecked_transaction().map_err(|e| e.to_string())?;
    for &mid in media_ids {
        tx.execute("DELETE FROM media_tag WHERE media_id = ?1", params![mid])
            .map_err(|e| e.to_string())?;
        for &tid in tag_ids {
            tx.execute(
                "INSERT OR IGNORE INTO media_tag (media_id, tag_id) VALUES (?1, ?2)",
                params![mid, tid],
            )
            .map_err(|e| e.to_string())?;
        }
    }
    tx.commit().map_err(|e| e.to_string())?;
    Ok(())
}

/// 按给定 id 顺序整体重写 sort_order（相册拖拽排序持久化）
pub fn set_media_order_impl(conn: &Connection, ids: &[i64]) -> Result<(), String> {
    let tx = conn.unchecked_transaction().map_err(|e| e.to_string())?;
    for (i, id) in ids.iter().enumerate() {
        tx.execute(
            "UPDATE media SET sort_order = ?1 WHERE id = ?2",
            params![i as i64 + 1, id],
        )
        .map_err(|e| e.to_string())?;
    }
    tx.commit().map_err(|e| e.to_string())?;
    Ok(())
}

// ===== 回收站 =====

pub fn delete_media_impl(conn: &Connection, ids: &[i64]) -> Result<usize, String> {
    let now = crate::mpak::now_ms();
    let mut n = 0usize;
    for &id in ids {
        n += conn
            .execute(
                "UPDATE media SET is_deleted = 1, deleted_time = ?1 WHERE id = ?2 AND is_deleted = 0",
                params![now, id],
            )
            .map_err(|e| e.to_string())? as usize;
    }
    Ok(n)
}

pub fn restore_media_impl(conn: &Connection, ids: &[i64]) -> Result<usize, String> {
    let mut n = 0usize;
    for &id in ids {
        n += conn
            .execute(
                "UPDATE media SET is_deleted = 0, deleted_time = NULL WHERE id = ?1 AND is_deleted = 1",
                params![id],
            )
            .map_err(|e| e.to_string())? as usize;
    }
    Ok(n)
}

/// 彻底删除（回收站记录 + 关联标签；不删源文件，由调用方决定）
pub fn purge_media_impl(conn: &Connection, ids: &[i64]) -> Result<usize, String> {
    let mut n = 0usize;
    for &id in ids {
        n += conn
            .execute("DELETE FROM media WHERE id = ?1", params![id])
            .map_err(|e| e.to_string())? as usize;
    }
    Ok(n)
}

// ===== 设置 =====

pub fn get_settings_impl(conn: &Connection) -> Result<std::collections::HashMap<String, String>, String> {
    let mut stmt = conn
        .prepare("SELECT key, value FROM settings")
        .map_err(|e| e.to_string())?;
    let rows = stmt
        .query_map([], |r| Ok((r.get::<_, String>(0)?, r.get::<_, String>(1)?)))
        .map_err(|e| e.to_string())?;
    let mut map = std::collections::HashMap::new();
    for r in rows {
        let (k, v) = r.map_err(|e| e.to_string())?;
        map.insert(k, v);
    }
    Ok(map)
}

pub fn set_setting_impl(conn: &Connection, key: &str, value: &str) -> Result<(), String> {
    conn.execute(
        "INSERT INTO settings (key, value) VALUES (?1, ?2)
         ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        params![key, value],
    )
    .map_err(|e| e.to_string())?;
    Ok(())
}

// ===== 单文件/批量文件导入 =====

/// 把 .mpak 导入提取出的元数据合并到媒体库：
/// 按落位路径找到记录 → 更新描述/时间/尺寸 → 按标签名合并标签（同名复用，不存在则创建）
pub fn apply_imported_metadata_impl(
    conn: &Connection,
    items: &[crate::mpak::import::ImportedItem],
) -> Result<(), String> {
    for item in items {
        let Some(id) = conn
            .query_row(
                "SELECT id FROM media WHERE file_path = ?1",
                params![item.file_path],
                |r| r.get::<_, i64>(0),
            )
            .optional()
            .map_err(|e| e.to_string())?
        else {
            continue; // 记录尚未入库（可能被去重跳过），跳过元数据
        };

        let _ = conn.execute(
            "UPDATE media SET description = ?1, taken_time = ?2, width = ?3, height = ?4 WHERE id = ?5",
            params![
                item.description.as_deref().unwrap_or(""),
                item.created_at,
                item.width,
                item.height,
                id
            ],
        );

        for tag_name in &item.tags {
            let tag_id: Option<i64> = conn
                .query_row("SELECT id FROM tag WHERE name = ?1", params![tag_name], |r| r.get(0))
                .optional()
                .map_err(|e| e.to_string())?;
            let tag_id = match tag_id {
                Some(t) => t,
                None => {
                    let max_order: i64 = conn
                        .query_row("SELECT COALESCE(MAX(sort_order), 0) FROM tag", [], |r| r.get(0))
                        .map_err(|e| e.to_string())?;
                    conn.execute(
                        "INSERT INTO tag (name, bg_color, is_reserved, sort_order) VALUES (?1, '#f6821f', 0, ?2)",
                        params![tag_name, max_order + 1],
                    )
                    .map_err(|e| e.to_string())?;
                    conn.last_insert_rowid()
                }
            };
            let _ = conn.execute(
                "INSERT OR IGNORE INTO media_tag (media_id, tag_id) VALUES (?1, ?2)",
                params![id, tag_id],
            );
        }
    }
    Ok(())
}
/// 把用户选中的文件复制到目标目录（文件名清洗 + 冲突后缀），随后扫描入库
pub fn import_files_impl(
    conn: &Connection,
    paths: &[String],
    dest_dir: &str,
) -> Result<ScanSummary, String> {
    if paths.is_empty() {
        return Err("未选择任何文件".into());
    }
    fs::create_dir_all(dest_dir).map_err(|e| format!("创建目录失败「{}」: {}", dest_dir, e))?;

    for p in paths {
        let src = Path::new(p);
        let name = src
            .file_name()
            .map(|s| s.to_string_lossy().into_owned())
            .unwrap_or_default();
        let target = crate::mpak::import::resolve_target(dest_dir, &name);
        fs::copy(src, &target).map_err(|e| format!("复制文件失败「{}」: {}", p, e))?;
    }

    // 复制完成后扫描目标目录入库（新增/更新/清理统计）
    scan_folder_impl(conn, dest_dir, true)
}

// ===== 已索引目录（文件系统监视范围）=====

const INDEX_DIRS_KEY: &str = "index_dirs";

/// 已索引目录列表（JSON 数组，存于 settings 表）
pub fn list_index_dirs_impl(conn: &Connection) -> Result<Vec<String>, String> {
    let raw: Option<String> = conn
        .query_row(
            "SELECT value FROM settings WHERE key = ?1",
            params![INDEX_DIRS_KEY],
            |r| r.get(0),
        )
        .optional()
        .map_err(|e| e.to_string())?
        .flatten();
    match raw {
        None => Ok(Vec::new()),
        Some(s) => serde_json::from_str(&s).map_err(|e| format!("索引目录数据损坏: {}", e)),
    }
}

/// 添加索引目录（去重）
pub fn add_index_dir_impl(conn: &Connection, dir: &str) -> Result<(), String> {
    let mut dirs = list_index_dirs_impl(conn)?;
    if dirs.iter().any(|d| d == dir) {
        return Ok(());
    }
    dirs.push(dir.to_string());
    let json = serde_json::to_string(&dirs).map_err(|e| e.to_string())?;
    set_setting_impl(conn, INDEX_DIRS_KEY, &json)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::db::init_schema;

    fn mem_db() -> Connection {
        let conn = Connection::open_in_memory().unwrap();
        init_schema(&conn).unwrap();
        conn
    }

    fn make_file(dir: &Path, name: &str, bytes: &[u8]) {
        fs::write(dir.join(name), bytes).unwrap();
    }

    #[test]
    fn test_scan_and_list() {
        let conn = mem_db();
        let dir = std::env::temp_dir().join(format!("mm_scan_{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        make_file(&dir, "a.png", b"png-data");
        make_file(&dir, "b.gif", b"gif-data");
        make_file(&dir, "c.mp4", b"video-data");
        make_file(&dir, "note.txt", b"ignored");

        let sum = scan_folder_impl(&conn, dir.to_str().unwrap(), false).unwrap();
        assert_eq!(sum.added, 3);
        assert_eq!(sum.skipped, 0);

        let list = list_media_impl(&conn).unwrap();
        assert_eq!(list.len(), 3);
        // 类型正确
        let types: Vec<&str> = list.iter().map(|m| m.media_type.as_str()).collect();
        assert!(types.contains(&"image") && types.contains(&"gif") && types.contains(&"video"));
        // sha256 已计算
        assert!(list.iter().all(|m| m.sha256.is_some()));
        // mime_type 存真实 MIME（而非 media_type）
        let gif = list.iter().find(|m| m.file_name == "b.gif").unwrap();
        assert_eq!(gif.mime_type.as_deref(), Some("image/gif"));
        let mp4 = list.iter().find(|m| m.file_name == "c.mp4").unwrap();
        assert_eq!(mp4.mime_type.as_deref(), Some("video/mp4"));

        // 重复扫描：全部跳过
        let sum2 = scan_folder_impl(&conn, dir.to_str().unwrap(), false).unwrap();
        assert_eq!(sum2.added, 0);
        assert_eq!(sum2.skipped, 3);

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn test_scan_recursive() {
        let conn = mem_db();
        let dir = std::env::temp_dir().join(format!("mm_scan_rec_{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        let sub = dir.join("sub");
        fs::create_dir_all(&sub).unwrap();
        make_file(&dir, "top.png", b"t");
        make_file(&sub, "deep.jpg", b"d");

        let sum = scan_folder_impl(&conn, dir.to_str().unwrap(), true).unwrap();
        assert_eq!(sum.added, 2);
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn test_tag_crud() {
        let conn = mem_db();
        let t = add_tag_impl(&conn, "搞笑", "#f56c6c").unwrap();
        assert_eq!(t.name, "搞笑");
        assert!(!t.is_reserved);

        // 重名拒绝
        assert!(add_tag_impl(&conn, "搞笑", "#000000").is_err());
        // 普通标签可以叫任何名字（含 [已导出]）
        assert!(add_tag_impl(&conn, "[已导出]", "#000000").is_ok());

        rename_tag_impl(&conn, t.id, "超搞笑").unwrap();
        let tags = list_tags_impl(&conn).unwrap();
        assert!(tags.iter().any(|x| x.name == "超搞笑"));

        // 保留标签机制仍保留：手动造一个保留标签验证不可删/不可改名
        conn.execute(
            "INSERT INTO tag (name, bg_color, is_reserved, sort_order) VALUES ('SYS', '#000000', 1, 999)",
            [],
        )
        .unwrap();
        let reserved = list_tags_impl(&conn).unwrap().iter().find(|x| x.is_reserved).unwrap().id;
        assert!(delete_tag_impl(&conn, reserved).is_err());
        assert!(rename_tag_impl(&conn, reserved, "改名").is_err());

        delete_tag_impl(&conn, t.id).unwrap();
        assert_eq!(list_tags_impl(&conn).unwrap().len(), 2);
    }

    #[test]
    fn test_recycle_flow() {
        let conn = mem_db();
        let dir = std::env::temp_dir().join(format!("mm_rc_{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        make_file(&dir, "x.png", b"x");
        scan_folder_impl(&conn, dir.to_str().unwrap(), false).unwrap();

        let list = list_media_impl(&conn).unwrap();
        let id = list[0].id;
        assert!(list[0].tag_ids.is_empty());

        // 打标签
        let tag = add_tag_impl(&conn, "猫", "#67c23a").unwrap();
        replace_media_tags_impl(&conn, &[id], &[tag.id]).unwrap();
        assert_eq!(list_media_impl(&conn).unwrap()[0].tag_ids, vec![tag.id]);

        // 进回收站 → 列表消失，回收站可见
        delete_media_impl(&conn, &[id]).unwrap();
        assert!(list_media_impl(&conn).unwrap().is_empty());
        let rc = list_recycle_impl(&conn).unwrap();
        assert_eq!(rc.len(), 1);
        assert!(rc[0].is_deleted);

        // 还原
        restore_media_impl(&conn, &[id]).unwrap();
        assert_eq!(list_media_impl(&conn).unwrap().len(), 1);

        // 再删除并彻底清除
        delete_media_impl(&conn, &[id]).unwrap();
        purge_media_impl(&conn, &[id]).unwrap();
        assert!(list_recycle_impl(&conn).unwrap().is_empty());

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn test_scan_sync_removes_deleted() {
        let conn = mem_db();
        let dir = std::env::temp_dir().join(format!("mm_sync_{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        make_file(&dir, "a.png", b"aaa");
        make_file(&dir, "b.png", b"bbb");

        assert_eq!(scan_folder_impl(&conn, dir.to_str().unwrap(), false).unwrap().added, 2);

        // 删除一个文件后再扫描：记录被清理
        fs::remove_file(dir.join("a.png")).unwrap();
        let sum = scan_folder_impl(&conn, dir.to_str().unwrap(), false).unwrap();
        assert_eq!(sum.removed, 1);
        assert_eq!(sum.added, 0);
        let list = list_media_impl(&conn).unwrap();
        assert_eq!(list.len(), 1);
        assert_eq!(list[0].file_name, "b.png");

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn test_scan_updates_changed() {
        let conn = mem_db();
        let dir = std::env::temp_dir().join(format!("mm_upd_{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        let f = dir.join("a.png");
        fs::write(&f, b"v1").unwrap();
        assert_eq!(scan_folder_impl(&conn, dir.to_str().unwrap(), false).unwrap().added, 1);
        let sha1 = list_media_impl(&conn).unwrap()[0].sha256.clone().unwrap();

        // 内容改变（大小变）后再扫描：记录更新
        fs::write(&f, b"version-2-longer").unwrap();
        let sum = scan_folder_impl(&conn, dir.to_str().unwrap(), false).unwrap();
        assert_eq!(sum.updated, 1);
        assert_eq!(sum.added, 0);
        let sha2 = list_media_impl(&conn).unwrap()[0].sha256.clone().unwrap();
        assert_ne!(sha1, sha2);

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn test_index_dirs_roundtrip() {
        let conn = mem_db();
        assert_eq!(list_index_dirs_impl(&conn).unwrap().len(), 0);
        add_index_dir_impl(&conn, "/a").unwrap();
        add_index_dir_impl(&conn, "/b").unwrap();
        add_index_dir_impl(&conn, "/a").unwrap(); // 去重
        let dirs = list_index_dirs_impl(&conn).unwrap();
        assert_eq!(dirs, vec!["/a".to_string(), "/b".to_string()]);
    }

    #[test]
    fn test_media_order_persist() {
        let conn = mem_db();
        let dir = std::env::temp_dir().join(format!("mm_order_{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        make_file(&dir, "a.png", b"aaa");
        make_file(&dir, "b.png", b"bbb");
        make_file(&dir, "c.png", b"ccc");
        scan_folder_impl(&conn, dir.to_str().unwrap(), false).unwrap();

        let list = list_media_impl(&conn).unwrap();
        assert_eq!(list.len(), 3);
        assert!(list.iter().all(|m| m.sort_order == 0));

        // 反转顺序并持久化
        let reversed: Vec<i64> = list.iter().map(|m| m.id).rev().collect();
        set_media_order_impl(&conn, &reversed).unwrap();

        let list2 = list_media_impl(&conn).unwrap();
        let ids2: Vec<i64> = list2.iter().map(|m| m.id).collect();
        assert_eq!(ids2, reversed);
        assert!(list2.iter().all(|m| m.sort_order > 0));

        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn test_scan_like_escape_sibling_dirs() {
        // 目录名含 LIKE 通配符字符（_、%）时，扫描一个目录不能误删兄弟目录的记录
        let conn = mem_db();
        let base = std::env::temp_dir().join(format!("mm_like_{}", std::process::id()));
        let _ = fs::remove_dir_all(&base);
        let d1 = base.join("my_dir"); // 含 _
        let d2 = base.join("myXdir"); // 与 d1 只差一个字符，未转义时会被 _ 通配命中
        let d3 = base.join("100%"); // 含 %
        let d4 = base.join("100abc"); // 会被 % 通配命中
        for d in [&d1, &d2, &d3, &d4] {
            fs::create_dir_all(d).unwrap();
        }
        make_file(&d1, "a.png", b"a");
        make_file(&d2, "b.png", b"b");
        make_file(&d3, "c.png", b"c");
        make_file(&d4, "d.png", b"d");

        // 全部入库
        for d in [&d1, &d2, &d3, &d4] {
            scan_folder_impl(&conn, d.to_str().unwrap(), false).unwrap();
        }
        assert_eq!(list_media_impl(&conn).unwrap().len(), 4);

        // 再扫描 d1 / d3：兄弟目录（d2、d4）的记录必须原样保留
        scan_folder_impl(&conn, d1.to_str().unwrap(), false).unwrap();
        scan_folder_impl(&conn, d3.to_str().unwrap(), false).unwrap();
        let names: Vec<String> = list_media_impl(&conn)
            .unwrap()
            .into_iter()
            .map(|m| m.file_name)
            .collect();
        assert_eq!(names.len(), 4);
        assert!(names.contains(&"a.png".to_string()));
        assert!(names.contains(&"b.png".to_string()));
        assert!(names.contains(&"c.png".to_string()));
        assert!(names.contains(&"d.png".to_string()));

        let _ = fs::remove_dir_all(&base);
    }

    #[test]
    fn test_settings_roundtrip() {
        let conn = mem_db();
        set_setting_impl(&conn, "defaultStorage", "user").unwrap();
        set_setting_impl(&conn, "gridCols", "6").unwrap();
        let map = get_settings_impl(&conn).unwrap();
        assert_eq!(map.get("defaultStorage").unwrap(), "user");
        // upsert
        set_setting_impl(&conn, "defaultStorage", "custom").unwrap();
        let map = get_settings_impl(&conn).unwrap();
        assert_eq!(map.get("defaultStorage").unwrap(), "custom");
        assert_eq!(map.len(), 2);
    }
}
